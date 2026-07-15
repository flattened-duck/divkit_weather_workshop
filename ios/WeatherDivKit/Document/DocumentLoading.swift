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
}

struct DocumentBundle {
    /// Renderable source per screen. Stage 0: only `.main` present.
    let sources: [Screen: DivViewSource]
    /// Raw successful response body (for the Stage 3 cache-to-disk seam). Unused in Stage 0.
    let rawBody: Data
}
