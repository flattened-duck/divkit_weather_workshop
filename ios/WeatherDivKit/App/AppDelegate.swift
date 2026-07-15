import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_: UIApplication,
                     didFinishLaunchingWithOptions _: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        Self.ensureApplicationSupportDirectoryExists()
        Self.applyTestEnvironmentIfPresent()
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = WeatherHostViewController()
        window.makeKeyAndVisible()
        self.window = window
        return true
    }

    /// Bug fix uncovered by Stage 4's popup-persistence UI tests (not test-only): a fresh app
    /// container has no Library/Application Support directory. DivKit's `DivPersistentValuesStorage`
    /// writes `divkit.values_storage` there via `Data.write(to:)`, which does NOT create missing
    /// intermediate directories — the write silently fails (logged, not thrown/crashed), so
    /// `set_stored_value` (the popup's permanent dismissal) never reaches disk on a first-ever
    /// launch. Runs unconditionally, before any persistence is touched, in prod and under test.
    private static func ensureApplicationSupportDirectoryExists() {
        guard let sup = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else { return }
        try? FileManager.default.createDirectory(at: sup, withIntermediateDirectories: true)
    }

    /// XCUITest is a separate process; launchEnvironment is the only channel into the app at
    /// launch. No-op in production (env vars are absent) — must run before the host VC touches
    /// Persistence/DivKitComponents/coldStart.
    private static func applyTestEnvironmentIfPresent() {
        let env = ProcessInfo.processInfo.environment
        if let base = env["WDK_BASE_URL"], !base.isEmpty { AppConfig.baseURL = base }
        if env["WDK_UITEST"] == "1" { AppConfig.debugOverlayEnabled = false }
        if env["WDK_DEBUG_OVERLAY"] == "1" { AppConfig.debugOverlayEnabled = true }
        if env["WDK_RESET_STATE"] == "1" { wipePersistentState() }
    }

    private static func wipePersistentState() {
        if let id = Bundle.main.bundleIdentifier {
            UserDefaults.standard.removePersistentDomain(forName: id)   // Persistence prefs
        }
        let fm = FileManager.default
        if let sup = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
            try? fm.removeItem(at: sup.appendingPathComponent("divkit.values_storage"))  // stored values (popup)
        }
        if let caches = fm.urls(for: .cachesDirectory, in: .userDomainMask).first,
           let files = try? fm.contentsOfDirectory(at: caches, includingPropertiesForKeys: nil) {
            for f in files where f.lastPathComponent.hasPrefix("doc_cache_") { try? fm.removeItem(at: f) }
        }
    }
}
