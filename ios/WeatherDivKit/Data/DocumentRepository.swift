import DivKit

/// Named load policies over DocumentLoading. Port of Android document/DocumentRepository.
final class DocumentRepository {
    private let loader: DocumentLoading
    init(loader: DocumentLoading) { self.loader = loader }

    func coldStartLocal(lang: String) -> DocumentBundle? {
        loader.loadCache(lang: lang) ?? loader.loadBundledSkeleton()
    }

    func fetch(lang: String, lat: String?, lon: String?, name: String?) async -> DocumentBundle? {
        try? await loader.load(lang: lang, lat: lat, lon: lon, name: name)
    }

    func fetchOrCache(lang: String, lat: String?, lon: String?, name: String?) async -> DocumentBundle? {
        if let fresh = try? await loader.load(lang: lang, lat: lat, lon: lon, name: name) { return fresh }
        return loader.loadCache(lang: lang)
    }

    func citySearchPatch(query: String, lang: String) async -> DivPatch? {
        await loader.loadCitySearch(query: query, lang: lang)
    }
}
