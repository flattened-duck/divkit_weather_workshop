import Network
import Foundation

final class RecordingBackend {
    private let listener: NWListener
    private let ruBody: Data
    private let enBody: Data
    private let queue = DispatchQueue(label: "recording.backend")
    private let lock = NSLock()
    private var _paths: [String] = []

    var port: UInt16 { listener.port?.rawValue ?? 0 }
    var requestCount: Int { lock.lock(); defer { lock.unlock() }; return _paths.count }
    func lastPath() -> String { lock.lock(); defer { lock.unlock() }; return _paths.last ?? "" }

    init(ruBody: Data, enBody: Data) throws {
        self.ruBody = ruBody; self.enBody = enBody
        let params = NWParameters.tcp
        params.allowLocalEndpointReuse = true
        listener = try NWListener(using: params, on: .any)   // OS-assigned port
        listener.newConnectionHandler = { [weak self] c in self?.accept(c) }
    }

    func start() {
        let sem = DispatchSemaphore(value: 0)
        listener.stateUpdateHandler = { if case .ready = $0 { sem.signal() } }
        listener.start(queue: queue)
        _ = sem.wait(timeout: .now() + 5)
    }
    func stop() { listener.cancel() }

    private func accept(_ c: NWConnection) { c.start(queue: queue); receive(c, buffer: Data()) }

    private func receive(_ c: NWConnection, buffer: Data) {
        c.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, done, err in
            guard let self else { return }
            var buf = buffer; if let data { buf.append(data) }
            if let end = buf.range(of: Data("\r\n\r\n".utf8)) {
                let header = String(decoding: buf[buf.startIndex..<end.lowerBound], as: UTF8.self)
                let reqLine = header.split(separator: "\r\n", maxSplits: 1).first.map(String.init) ?? ""
                let path = reqLine.split(separator: " ").dropFirst().first.map(String.init) ?? ""
                self.respond(c, path: path)
            } else if done || err != nil { c.cancel() }
            else { self.receive(c, buffer: buf) }
        }
    }

    private func respond(_ c: NWConnection, path: String) {
        let body: Data
        if path.hasPrefix("/document") {
            lock.lock(); _paths.append(path); lock.unlock()
            body = path.contains("lang=en") ? enBody : ruBody
        } else {
            body = Data("{}".utf8)   // /city-search etc. — parse fails harmlessly, no-op
        }
        var head = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
        head += "Content-Length: \(body.count)\r\nConnection: close\r\n\r\n"
        var out = Data(head.utf8); out.append(body)
        c.send(content: out, completion: .contentProcessed { _ in c.cancel() })
    }
}
