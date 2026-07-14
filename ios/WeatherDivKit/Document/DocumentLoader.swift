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
        guard let mainCard = screens["main"] as? [String: Any] else {
            throw DocumentLoaderError.malformed("missing \"screens.main\"")
        }

        let sourceDict: [String: Any] = ["templates": templates, "card": mainCard]
        let sourceData = try JSONSerialization.data(withJSONObject: sourceDict)
        let source = DivViewSource(kind: .data(sourceData), cardId: Screen.main.cardId)

        return DocumentBundle(sources: [.main: source], rawBody: data)
    }
}
