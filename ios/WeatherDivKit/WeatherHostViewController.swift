import DivKit
import DivKitExtensions
import UIKit

final class WeatherHostViewController: UIViewController, HostActions {
    private let loader: DocumentLoading = DocumentLoader()
    private lazy var divKitComponents: DivKitComponents = makeComponents()
    private lazy var divView = DivView(divKitComponents: divKitComponents)

    override func viewDidLoad() {
        super.viewDidLoad()
        view.addSubview(divView)
        Task { await loadAndRender() }
    }

    override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        divView.frame = view.bounds.inset(by: view.safeAreaInsets)
    }

    private func makeComponents() -> DivKitComponents {
        let factory = DivComponentsFactory()
        factory.reporter = LoggingDivReporter()
        factory.urlHandler = WeatherUrlHandler(actions: self)
        return factory.makeComponents()
    }

    private func loadAndRender() async {
        let bundle: DocumentBundle
        do {
            bundle = try await loader.load(lang: "ru", lat: nil, lon: nil, name: nil)
        } catch {
            print("WeatherHostViewController: load failed: \(error)")
            return
        }
        guard let source = bundle.sources[.main] else {
            print("WeatherHostViewController: no main source in bundle")
            return
        }
        await divView.setSource(source, debugParams: DebugParams(isDebugInfoEnabled: true))
    }

    // MARK: - HostActions (Stage 0: log-only stubs; real routing is Stage 1)

    func navigate(to screen: Screen) {
        print("HostActions.navigate(to: \(screen))")
    }

    func back() {
        print("HostActions.back()")
    }

    func setLang(_ lang: String) {
        print("HostActions.setLang(\(lang))")
    }

    func setTheme(_ theme: String) {
        print("HostActions.setTheme(\(theme))")
    }

    func setCompact(_ compact: Bool) {
        print("HostActions.setCompact(\(compact))")
    }

    func setCity(lat: String, lon: String, name: String) {
        print("HostActions.setCity(lat: \(lat), lon: \(lon), name: \(name))")
    }

    func citySearch(query: String) {
        print("HostActions.citySearch(query: \(query))")
    }
}
