/// Pure navigation state machine. Mirror of Android navigation/ScreenNavigator.
@MainActor
final class ScreenRouter {
    private let availableScreens: () -> Set<Screen>
    private let render: (Screen) -> Void
    private let onExit: () -> Void
    private(set) var currentScreen: Screen = .main
    private var backStack: [Screen] = []

    init(availableScreens: @escaping () -> Set<Screen>, render: @escaping (Screen) -> Void, onExit: @escaping () -> Void) {
        self.availableScreens = availableScreens; self.render = render; self.onExit = onExit
    }

    func showScreen(_ screen: Screen) {
        let top = backStack.last
        if let top, top != screen { backStack.append(screen) }
        else if backStack.isEmpty { backStack.append(screen) }
        renderScreen(screen)
    }

    func renderScreen(_ screen: Screen) {
        guard availableScreens().contains(screen) else { return }
        currentScreen = screen
        render(screen)
    }

    func renderCurrent() { renderScreen(currentScreen) }

    func goBack() {
        if backStack.count <= 1 { onExit(); return }
        backStack.removeLast()
        guard let previous = backStack.last else { onExit(); return }
        renderScreen(previous)
    }

    func backStackSnapshot() -> [Screen] { backStack }
}
