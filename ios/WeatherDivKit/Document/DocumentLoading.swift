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
}

struct DocumentBundle {
    /// Renderable source per screen. Stage 0: only `.main` present.
    let sources: [Screen: DivViewSource]
    /// Raw successful response body (for the Stage 3 cache-to-disk seam). Unused in Stage 0.
    let rawBody: Data
}
