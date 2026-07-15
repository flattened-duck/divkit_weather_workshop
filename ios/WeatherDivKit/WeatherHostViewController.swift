import DivKit
import DivKitExtensions
import UIKit

@MainActor
final class WeatherHostViewController: UIViewController, HostActions {
    private let loader: DocumentLoading = DocumentLoader()
    private lazy var divKitComponents: DivKitComponents = makeComponents()
    private lazy var divView = DivView(divKitComponents: divKitComponents)

    /// Current parsed screens, replaced atomically on refetch. Analog of Android `screens`.
    private var sources: [Screen: DivViewSource] = [:]
    private var currentScreen: Screen = .main
    private var backStack: [Screen] = []
    /// Set as a side effect the first time `divKitComponents` is accessed (see `makeComponents`).
    private var globals: GlobalVariables!
    private var themeMode = "system"
    private var effectiveTheme = "light"

    override func viewDidLoad() {
        super.viewDidLoad()
        view.addSubview(divView) // forces divKitComponents init -> globals set

        themeMode = Persistence.themeMode
        let compact = Persistence.compact
        effectiveTheme = resolveEffectiveTheme(themeMode)

        globals.seed([
            GlobalVariables.theme: .string(effectiveTheme),
            GlobalVariables.themeMode: .string(themeMode),
            GlobalVariables.compact: .bool(compact),
            GlobalVariables.headerState: .string("full"),
            GlobalVariables.statusInset: .integer(0),
            GlobalVariables.navInset: .integer(0),
        ])

        setNeedsStatusBarAppearanceUpdate()

        Task { await coldStart() }
    }

    override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        divView.frame = view.bounds.inset(by: view.safeAreaInsets)
    }

    override func viewSafeAreaInsetsDidChange() {
        super.viewSafeAreaInsetsDidChange()
        // iOS safeAreaInsets are already points (density-independent) — no density divide needed.
        let top = Int(view.safeAreaInsets.top.rounded())
        let bottom = Int(view.safeAreaInsets.bottom.rounded())
        globals.set([GlobalVariables.statusInset: .integer(top), GlobalVariables.navInset: .integer(bottom)])
    }

    override var preferredStatusBarStyle: UIStatusBarStyle {
        effectiveTheme == "dark" ? .lightContent : .darkContent
    }

    override func traitCollectionDidChange(_ previous: UITraitCollection?) {
        super.traitCollectionDidChange(previous)
        guard traitCollection.userInterfaceStyle != previous?.userInterfaceStyle else { return }
        guard themeMode == "system" else { return }
        let eff = isSystemDark() ? "dark" : "light"
        effectiveTheme = eff
        globals.set([GlobalVariables.theme: .string(eff)])
        setNeedsStatusBarAppearanceUpdate()
    }

    private func makeComponents() -> DivKitComponents {
        let factory = DivComponentsFactory()
        factory.reporter = LoggingDivReporter()
        factory.urlHandler = WeatherUrlHandler(actions: self)
        factory.customBlockFactory = SunPhaseCustomBlockFactory()
        globals = GlobalVariables(storage: factory.variablesStorage)
        factory.extensionHandlers.append(
            ScrollStateExtensionHandler(
                variablesStorage: factory.variablesStorage,
                hostView: { [weak self] in self?.divView }
            )
        )
        factory.extensionHandlers.append(ShimmerImagePreviewExtension())
        return factory.makeComponents()
    }

    // MARK: - Cold start / refetch

    private func coldStart() async {
        let lang = Persistence.lang
        let (lat, lon, name) = Persistence.city
        await refetchAndRender(lang: lang, lat: lat, lon: lon, name: name, initial: true)
    }

    /// Single network+render path used by cold start, setLang, setCity.
    /// Keep-current on failure: does nothing to the screen (no cache/bundle fallback — Stage 3).
    private func refetchAndRender(lang: String, lat: String?, lon: String?, name: String?, initial: Bool) async {
        let bundle: DocumentBundle
        do {
            bundle = try await loader.load(lang: lang, lat: lat, lon: lon, name: name)
        } catch {
            print("WeatherHostViewController: load failed: \(error)")
            if initial { print("WeatherHostViewController: cold start failed") }
            return
        }
        sources = bundle.sources
        if initial {
            showScreen(.main)
        } else {
            renderScreen(currentScreen)
        }
    }

    // MARK: - Navigation

    private func showScreen(_ screen: Screen) {
        let top = backStack.last
        if let top, top != screen {
            backStack.append(screen)
        } else if backStack.isEmpty {
            backStack.append(screen)
        }
        renderScreen(screen)
    }

    private func renderScreen(_ screen: Screen) {
        guard let source = sources[screen] else {
            print("WeatherHostViewController: No source for \(screen)")
            return
        }
        currentScreen = screen
        // Default shouldResetPreviousCardData: false — globals in globalStorage persist across swaps.
        Task { await divView.setSource(source, debugParams: DebugParams(isDebugInfoEnabled: true)) }
    }

    private func goBack() {
        if backStack.count <= 1 {
            // iOS DIVERGENCE: Android calls finish(); iOS has no Activity to finish, so no-op at root.
            print("WeatherHostViewController: goBack at root — no-op")
            return
        }
        backStack.removeLast()
        guard let previous = backStack.last else { return }
        renderScreen(previous)
    }

    // MARK: - HostActions

    func navigate(to screen: Screen) {
        showScreen(screen)
    }

    func back() {
        goBack()
    }

    func setLang(_ lang: String) {
        Persistence.lang = lang
        let (lat, lon, name) = Persistence.city
        Task { await refetchAndRender(lang: lang, lat: lat, lon: lon, name: name, initial: false) }
    }

    func setTheme(_ theme: String) {
        Persistence.themeMode = theme
        themeMode = theme
        effectiveTheme = resolveEffectiveTheme(theme)
        globals.set([GlobalVariables.themeMode: .string(theme), GlobalVariables.theme: .string(effectiveTheme)])
        setNeedsStatusBarAppearanceUpdate()
    }

    func setCompact(_ compact: Bool) {
        Persistence.compact = compact
        globals.set([GlobalVariables.compact: .bool(compact)])
        if compact {
            globals.set([GlobalVariables.headerState: .string("collapsed")])
        }
    }

    func setCity(lat: String, lon: String, name: String) {
        Persistence.city = (lat, lon, name)
        let lang = Persistence.lang
        Task { await refetchAndRender(lang: lang, lat: lat, lon: lon, name: name, initial: false) }
    }

    func citySearch(query: String) {
        // Stage 0 stub kept: full DivPatch wiring is Stage 3A.
        print("HostActions.citySearch(query: \(query))")
    }

    // MARK: - Theme resolution

    private func resolveEffectiveTheme(_ mode: String) -> String {
        mode == "system" ? (isSystemDark() ? "dark" : "light") : mode
    }

    private func isSystemDark() -> Bool {
        traitCollection.userInterfaceStyle == .dark
    }
}
