import DivKit
import Foundation

enum DocumentLoaderError: Error {
    case badStatus(Int)
    case malformed(String)
}

final class DocumentLoader: DocumentLoading {
    func load(lang: String, lat: String?, lon: String?, name: String?) async throws -> DocumentBundle {
        guard var components = URLComponents(string: AppConfig.baseURL + "/document") else {
            throw DocumentLoaderError.malformed("bad base URL")
        }
        var queryItems = [URLQueryItem(name: "lang", value: lang)]
        if let lat, !lat.isEmpty { queryItems.append(URLQueryItem(name: "lat", value: lat)) }
        if let lon, !lon.isEmpty { queryItems.append(URLQueryItem(name: "lon", value: lon)) }
        if let name, !name.isEmpty { queryItems.append(URLQueryItem(name: "name", value: name)) }
        components.queryItems = queryItems

        guard let url = components.url else {
            throw DocumentLoaderError.malformed("could not build URL")
        }

        let (data, response) = try await URLSession.shared.data(from: url)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            let status = (response as? HTTPURLResponse)?.statusCode ?? -1
            throw DocumentLoaderError.badStatus(status)
        }

        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw DocumentLoaderError.malformed("response root is not a JSON object")
        }

        let templates = root["templates"] as? [String: Any] ?? [:]
        guard let screens = root["screens"] as? [String: Any] else {
            throw DocumentLoaderError.malformed("missing \"screens\"")
        }
        guard screens["main"] as? [String: Any] != nil else {
            throw DocumentLoaderError.malformed("missing \"screens.main\"")
        }

        var sources: [Screen: DivViewSource] = [:]
        for screen in Screen.allCases {
            guard let card = screens[screen.rawValue] as? [String: Any] else { continue }
            let sourceDict: [String: Any] = ["templates": templates, "card": card]
            let sourceData = try JSONSerialization.data(withJSONObject: sourceDict)
            sources[screen] = DivViewSource(kind: .data(sourceData), cardId: screen.cardId)
        }

        return DocumentBundle(sources: sources, rawBody: data)
    }
}
