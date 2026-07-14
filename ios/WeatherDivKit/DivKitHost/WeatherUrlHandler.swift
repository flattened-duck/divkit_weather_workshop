import DivKit
import Foundation

final class WeatherUrlHandler: DivUrlHandler {
    weak var actions: HostActions?
    init(actions: HostActions?) { self.actions = actions }

    func handle(_ url: URL, info: DivActionInfo, sender: AnyObject?) {
        // Stage 0 STUB: log only. Full weather-app:// routing → HostActions is Stage 1.
        print("WeatherUrlHandler: \(url)")
    }
}
