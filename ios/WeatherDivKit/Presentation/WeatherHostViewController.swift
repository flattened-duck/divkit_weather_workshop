import DivKit
import DivKitExtensions
import UIKit

@MainActor
final class WeatherHostViewController: UIViewController, HostActions {
    private let repository = DocumentRepository(loader: DocumentLoader())
    private let variablesStorage: DivVariablesStorage
    private let globals: GlobalVariables
    private lazy var divKitComponents: DivKitComponents = makeComponents()
    private lazy var divView = DivView(divKitComponents: divKitComponents)
    private lazy var router: ScreenRouter = makeRouter()

    /// Current parsed screens, replaced atomically on refetch. Analog of Android `screens`.
    private var sources: [Screen: DivViewSource] = [:]
    private var themeMode: ThemeMode = .system
    private var effectiveTheme: EffectiveTheme = .light

    init() {
        let storage = DivVariablesStorage()
        self.variablesStorage = storage
        self.globals = GlobalVariables(storage: storage)
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("WeatherHostViewController is instantiated programmatically") }

    override func viewDidLoad() {
        super.viewDidLoad()

        themeMode = ThemeMode(rawValue: Persistence.themeMode) ?? .system
        let compact = Persistence.compact
        effectiveTheme = resolveEffectiveTheme(themeMode)

        view.addSubview(divView)

        globals.seed([
            GlobalVariables.theme: .string(effectiveTheme.rawValue),
            GlobalVariables.themeMode: .string(themeMode.rawValue),
            GlobalVariables.compact: .bool(compact),
            GlobalVariables.headerState: .string(HeaderState.full.rawValue),
            GlobalVariables.statusInset: .integer(0),
            GlobalVariables.navInset: .integer(0),
        ])

        setNeedsStatusBarAppearanceUpdate()

        Task { await coldStart() }
    }

    override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        // Edge-to-edge: safe-area insets are fed to the layout as status_inset/nav_inset instead
        // (see viewSafeAreaInsetsDidChange), matching Android's WindowCompat.setDecorFitsSystemWindows(false).
        divView.frame = view.bounds
    }

    override func viewSafeAreaInsetsDidChange() {
        super.viewSafeAreaInsetsDidChange()
        // iOS safeAreaInsets are already points (density-independent) — no density divide needed.
        let top = Int(view.safeAreaInsets.top.rounded())
        let bottom = Int(view.safeAreaInsets.bottom.rounded())
        globals.set([GlobalVariables.statusInset: .integer(top), GlobalVariables.navInset: .integer(bottom)])
    }

    override var preferredStatusBarStyle: UIStatusBarStyle {
        effectiveTheme == .dark ? .lightContent : .darkContent
    }

    override func traitCollectionDidChange(_ previous: UITraitCollection?) {
        super.traitCollectionDidChange(previous)
        guard traitCollection.userInterfaceStyle != previous?.userInterfaceStyle else { return }
        guard themeMode == .system else { return }
        let eff: EffectiveTheme = isSystemDark() ? .dark : .light
        effectiveTheme = eff
        globals.set([GlobalVariables.theme: .string(eff.rawValue)])
        setNeedsStatusBarAppearanceUpdate()
    }

    private func makeComponents() -> DivKitComponents {
        let factory = DivComponentsFactory(variablesStorage: variablesStorage)
        factory.reporter = LoggingDivReporter()
        factory.urlHandler = WeatherUrlHandler(actions: self)
        factory.customBlockFactory = SunPhaseCustomBlockFactory()
        factory.extensionHandlers.append(
            ScrollStateExtensionHandler(
                variablesStorage: variablesStorage,
                hostView: { [weak self] in self?.divView }
            )
        )
        factory.extensionHandlers.append(ShimmerImagePreviewExtension())
        return factory.makeComponents()
    }

    private func makeRouter() -> ScreenRouter {
        ScreenRouter(
            availableScreens: { [weak self] in self.map { Set($0.sources.keys) } ?? [] },
            render: { [weak self] screen in
                guard let self else { return }
                guard let source = self.sources[screen] else {
                    Log.error("WeatherHostViewController: No source for \(screen)")
                    return
                }
                // Default shouldResetPreviousCardData: false — globals in globalStorage persist across swaps.
                Task { await self.divView.setSource(source, debugParams: DebugParams(isDebugInfoEnabled: AppConfig.debugOverlayEnabled)) }
                self.installPullToRefreshIfMain()
            },
            onExit: {
                // iOS DIVERGENCE: Android calls finish(); iOS has no Activity to finish, so no-op at root.
                Log.info("WeatherHostViewController: goBack at root — no-op")
            }
        )
    }

    // MARK: - Cold start / refetch

    /// Two-phase: phase 1 renders local (cache-or-skeleton) instantly so the screen is never blank;
    /// phase 2 fetches network in the background and swaps in on success, else keeps phase 1's layout.
    private func coldStart() async {
        let lang = Persistence.lang
        let (lat, lon, name) = Persistence.city

        if let local = repository.coldStartLocal(lang: lang) {
            sources = local.sources
            router.showScreen(.main)
        }

        if let fresh = await repository.fetch(lang: lang, lat: lat, lon: lon, name: name) {
            sources = fresh.sources
            router.renderCurrent()
        } else {
            Log.warn("WeatherHostViewController: cold start network failed, keeping phase-1 layout")
        }
    }

    // MARK: - HostActions

    func navigate(to screen: Screen) {
        router.showScreen(screen)
    }

    func back() {
        router.goBack()
    }

    /// Network-only with a same-lang cache fallback on failure (never falls through to the skeleton).
    func setLang(_ lang: String) {
        Persistence.lang = lang
        let (lat, lon, name) = Persistence.city
        Task {
            if let bundle = await repository.fetchOrCache(lang: lang, lat: lat, lon: lon, name: name) {
                sources = bundle.sources
                router.renderCurrent()
            } else {
                Log.warn("WeatherHostViewController: setLang offline, no cache for \(lang), keeping current")
            }
        }
    }

    func setTheme(_ theme: String) {
        Persistence.themeMode = theme
        themeMode = ThemeMode(rawValue: theme) ?? .system
        effectiveTheme = resolveEffectiveTheme(themeMode)
        globals.set([
            GlobalVariables.themeMode: .string(themeMode.rawValue),
            GlobalVariables.theme: .string(effectiveTheme.rawValue),
        ])
        setNeedsStatusBarAppearanceUpdate()
    }

    func setCompact(_ compact: Bool) {
        Persistence.compact = compact
        globals.set([GlobalVariables.compact: .bool(compact)])
        if compact {
            globals.set([GlobalVariables.headerState: .string(HeaderState.collapsed.rawValue)])
        }
    }

    func setCity(lat: String, lon: String, name: String) {
        Persistence.city = (lat, lon, name)
        let lang = Persistence.lang
        Task {
            if let bundle = await repository.fetch(lang: lang, lat: lat, lon: lon, name: name) {
                sources = bundle.sources
                router.renderCurrent()
            } else {
                Log.warn("WeatherHostViewController: setCity network failed, keeping current")
            }
        }
    }

    func citySearch(query: String) {
        let lang = Persistence.lang
        // Capture before the await: pins "apply to the firing screen" (== settings here),
        // so a late navigation away while the fetch is in flight becomes a safe no-op.
        let cardId = router.currentScreen.cardId
        Task {
            let patch = await repository.citySearchPatch(query: query, lang: lang)
            guard let patch else {
                Log.warn("WeatherHostViewController: citySearch failed for query \"\(query)\"")
                return
            }
            divView.applyPatch(patch, cardId: cardId)
        }
    }

    // MARK: - Theme resolution

    private func resolveEffectiveTheme(_ mode: ThemeMode) -> EffectiveTheme {
        switch mode {
        case .system: return isSystemDark() ? .dark : .light
        case .dark: return .dark
        case .light: return .light
        }
    }

    private func isSystemDark() -> Bool {
        traitCollection.userInterfaceStyle == .dark
    }

    // MARK: - Pull-to-refresh (main screen only)

    /// The collection view is (re)built asynchronously after `setSource`, so retry briefly.
    private func installPullToRefreshIfMain(retriesLeft: Int = 25) {
        guard router.currentScreen == .main else { return }
        guard let cv = divView.firstCollectionView() else {
            if retriesLeft > 0 {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { [weak self] in
                    self?.installPullToRefreshIfMain(retriesLeft: retriesLeft - 1)
                }
            }
            return
        }
        guard cv.refreshControl == nil else { return }   // don't double-attach to the same CV
        let rc = UIRefreshControl()
        rc.tintColor = .white // visible against the header photo now that the frame is edge-to-edge
        rc.addTarget(self, action: #selector(handlePullToRefresh(_:)), for: .valueChanged)
        cv.refreshControl = rc
    }

    /// Network-only, keep-current on failure; the spinner ends on every path.
    @objc private func handlePullToRefresh(_ sender: UIRefreshControl) {
        let lang = Persistence.lang
        let (lat, lon, name) = Persistence.city
        Task {
            defer { sender.endRefreshing() }
            if let fresh = await repository.fetch(lang: lang, lat: lat, lon: lon, name: name) {
                sources = fresh.sources
                router.renderCurrent()
            } else {
                Log.warn("WeatherHostViewController: pull-to-refresh offline, keeping current layout")
            }
        }
    }
}
