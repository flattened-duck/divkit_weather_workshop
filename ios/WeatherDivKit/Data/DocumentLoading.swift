import DivKit
import Foundation

/// Fetches + parses the /document envelope into per-screen renderable sources.
/// Stage 0 concrete impl populates only `.main`; Stage 1+ populate all three and add caching.
protocol DocumentLoading {
    func load(
        lang: String,
        lat: String?,
        lon: String?,
        name: String?
    ) async throws -> DocumentBundle

    /// Reads the lang-keyed disk cache (written by a prior successful load). nil if missing/corrupt.
    func loadCache(lang: String) -> DocumentBundle?
    /// Reads the app-bundled zero skeleton (dashes + empty:// shimmer). nil if missing/unparseable.
    func loadBundledSkeleton() -> DocumentBundle?
    /// Fetches a city-search DivPatch to apply to the currently rendered card. nil on any failure.
    func loadCitySearch(query: String, lang: String) async -> DivPatch?
}
