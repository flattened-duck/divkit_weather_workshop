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

        let sources = try makeSources(from: root)
        writeCache(lang: lang, data: data)
        return DocumentBundle(sources: sources, rawBody: data)
    }

    func loadCache(lang: String) -> DocumentBundle? {
        guard let data = try? Data(contentsOf: cacheURL(lang)) else { return nil }
        guard let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let sources = try? makeSources(from: root) else { return nil }
        return DocumentBundle(sources: sources, rawBody: data)
    }

    func loadBundledSkeleton() -> DocumentBundle? {
        guard let url = Bundle.main.url(forResource: "zero_ru", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let sources = try? makeSources(from: root) else { return nil }
        return DocumentBundle(sources: sources, rawBody: data)
    }

    // MARK: - Shared reshape

    private func makeSources(from root: [String: Any]) throws -> [Screen: DivViewSource] {
        let root = normalizeForIOSDivKit(root) as? [String: Any] ?? root
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
        return sources
    }

    // MARK: - DivKit iOS workarounds

    /// Works around two DivKit iOS gaps vs the backend's Android-first JSON, applied to every
    /// dict node in the tree before it becomes a `DivViewSource`:
    /// - gallery `resolveHorizontalInsets`/`resolveVerticalInsets` read only left/right/top/bottom,
    ///   never start/end (unlike the general resolver) — so gallery start/end padding is dropped.
    /// - a state-item container with transition_in/out but no id can't be identified across a
    ///   state change, which DivKit reports as a render error and renders as a janky switch.
    private func normalizeForIOSDivKit(_ node: Any) -> Any {
        if var dict = node as? [String: Any] {
            if dict["type"] as? String == "gallery", var paddings = dict["paddings"] as? [String: Any] {
                if paddings["left"] == nil, let start = paddings["start"] {
                    paddings["left"] = start
                }
                if paddings["right"] == nil, let end = paddings["end"] {
                    paddings["right"] = end
                }
                dict["paddings"] = paddings
            }
            if dict["id"] == nil, dict["transition_in"] != nil || dict["transition_out"] != nil {
                dict.removeValue(forKey: "transition_in")
                dict.removeValue(forKey: "transition_out")
            }
            for (key, value) in dict {
                dict[key] = normalizeForIOSDivKit(value)
            }
            return dict
        }
        if let array = node as? [Any] {
            return array.map { normalizeForIOSDivKit($0) }
        }
        return node
    }

    // MARK: - Disk cache

    private func writeCache(lang: String, data: Data) {
        try? data.write(to: cacheURL(lang), options: .atomic)
    }

    private func cacheURL(_ lang: String) -> URL {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("doc_cache_\(lang).json")
    }

    /// Fresh instance per call (Android-faithful: `onCitySearch` builds `DocumentLoader(this)`).
    /// Off-protocol on purpose — keeps this edit isolated from the shared `loader` property.
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

            let (data, response) = try await URLSession.shared.data(from: url)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                return nil
            }

            guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                return nil
            }

            // Backend returns bare {"changes":[...]} at root; parseDivPatch wants {"patch": {...}}.
            var wrapper: [String: Any] = ["patch": root]
            if let templates = root["templates"] {
                wrapper["templates"] = templates
            }
            let wrapperData = try JSONSerialization.data(withJSONObject: wrapper)
            return try parseDivPatch(wrapperData)
        } catch {
            print("DocumentLoader: loadCitySearch failed: \(error)")
            return nil
        }
    }
}
