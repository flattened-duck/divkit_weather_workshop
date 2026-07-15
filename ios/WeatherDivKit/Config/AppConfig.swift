enum AppConfig {
    static var baseURL: String = "http://localhost:8080"
    /// Release-like default: error-counter overlay OFF. Force ON with launch env WDK_DEBUG_OVERLAY=1.
    static var debugOverlayEnabled: Bool = false
}
