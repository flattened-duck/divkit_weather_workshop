import XCTest

class BaseUITest: XCTestCase {
    let app = XCUIApplication()
    static let backendURL = "http://localhost:8080"

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    /// Configure launch env then launch fresh. `resetState` false only for the popup-persistence relaunch.
    func launch(baseURL: String = backendURL, resetState: Bool = true) {
        app.launchEnvironment["WDK_BASE_URL"]  = baseURL
        app.launchEnvironment["WDK_UITEST"]    = "1"
        app.launchEnvironment["WDK_RESET_STATE"] = resetState ? "1" : "0"
        app.launch()
    }

    /// Mirror of Android requireBackendUp: fail loudly if the live backend isn't serving /document.
    func requireBackendUp(file: StaticString = #file, line: UInt = #line) {
        let url = URL(string: "\(Self.backendURL)/document?lang=ru")!
        var req = URLRequest(url: url); req.timeoutInterval = 5
        let sem = DispatchSemaphore(value: 0); var ok = false; var detail = ""
        URLSession.shared.dataTask(with: req) { _, resp, err in
            if let http = resp as? HTTPURLResponse, http.statusCode == 200 { ok = true }
            else { detail = err?.localizedDescription ?? "HTTP \((resp as? HTTPURLResponse)?.statusCode ?? -1)" }
            sem.signal()
        }.resume()
        _ = sem.wait(timeout: .now() + 6)
        XCTAssertTrue(ok, "Backend not reachable at \(Self.backendURL)/document — start it (cd backend && ./gradlew run). \(detail)", file: file, line: line)
        if !ok { fatalError("backend down") } // hard stop; continueAfterFailure=false already set
    }

    func fetchLiveDocument(lang: String) -> Data {
        let url = URL(string: "\(Self.backendURL)/document?lang=\(lang)")!
        let sem = DispatchSemaphore(value: 0); var out = Data()
        URLSession.shared.dataTask(with: url) { d, _, _ in out = d ?? Data(); sem.signal() }.resume()
        _ = sem.wait(timeout: .now() + 6)
        XCTAssertFalse(out.isEmpty, "empty /document body for lang=\(lang)")
        return out
    }
}
