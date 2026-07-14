/// Callback surface the url handler invokes. Stage 0: the host conforms with STUB (log-only) methods;
/// full routing is wired in Stage 1.
protocol HostActions: AnyObject {
    func navigate(to screen: Screen)
    func back()
    func setLang(_ lang: String)
    func setTheme(_ theme: String)
    func setCompact(_ compact: Bool)
    func setCity(lat: String, lon: String, name: String)
    func citySearch(query: String)
}
