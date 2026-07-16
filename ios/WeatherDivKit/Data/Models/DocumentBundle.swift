import DivKit
import Foundation

struct DocumentBundle {
    /// Renderable source per screen. Stage 0: only `.main` present.
    let sources: [Screen: DivViewSource]
    /// Raw successful response body (for the Stage 3 cache-to-disk seam). Unused in Stage 0.
    let rawBody: Data
}
