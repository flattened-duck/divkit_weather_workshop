import DivKit
import Foundation

final class DocumentLoader: DocumentLoading {
    private let urlSession: URLSession
    init(urlSession: URLSession = .shared) {
        self.urlSession = urlSession
    }

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

        let (data, response) = try await urlSession.data(from: url)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            let status = (response as? HTTPURLResponse)?.statusCode ?? -1
            throw DocumentLoaderError.badStatus(status)
        }

        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw DocumentLoaderError.malformed("response root is not a JSON object")
        }

        let sources = try DocumentEnvelopeAdapter.makeSources(from: root)
        writeCache(lang: lang, data: data)
        return DocumentBundle(sources: sources, rawBody: data)
    }

    func loadCache(lang: String) -> DocumentBundle? {
        guard let data = try? Data(contentsOf: cacheURL(lang)) else { return nil }
        guard let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let sources = try? DocumentEnvelopeAdapter.makeSources(from: root) else { return nil }
        return DocumentBundle(sources: sources, rawBody: data)
    }

    func loadBundledSkeleton() -> DocumentBundle? {
        guard let url = Bundle.main.url(forResource: AppPaths.bundledSkeletonResource, withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let sources = try? DocumentEnvelopeAdapter.makeSources(from: root) else { return nil }
        return DocumentBundle(sources: sources, rawBody: data)
    }

    // MARK: - Disk cache

    private func writeCache(lang: String, data: Data) {
        try? data.write(to: cacheURL(lang), options: .atomic)
    }

    private func cacheURL(_ lang: String) -> URL {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent(AppPaths.cacheFileName(lang: lang))
    }

    func loadCitySearch(query: String, lang: String) async -> DivPatch? {
        do {
            guard var components = URLComponents(string: AppConfig.baseURL + "/city-search") else {
                return nil
            }
            components.queryItems = [
                URLQueryItem(name: "q", value: query),
                URLQueryItem(name: "lang", value: lang),
            ]
            guard let url = components.url else { return nil }

            let (data, response) = try await urlSession.data(from: url)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                return nil
            }

            guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                return nil
            }

            return try CitySearchPatchAdapter.makePatch(from: root)
        } catch {
            Log.warn("DocumentLoader: loadCitySearch failed: \(error)")
            return nil
        }
    }
}
