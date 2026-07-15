import DivKit
import Foundation
import UIKit

final class WeatherUrlHandler: DivUrlHandler {
    weak var actions: HostActions?
    init(actions: HostActions?) { self.actions = actions }

    func handle(_ url: URL, sender: AnyObject?) {
        // weather-app:// dispatch is already on the main thread (verified: DivActionHandler.swift:297-424);
        // assumeIsolated avoids introducing an async hop for a call that is synchronously on main.
        MainActor.assumeIsolated { _ = route(url) }
    }

    func handle(_ url: URL, info: DivActionInfo, sender: AnyObject?) {
        MainActor.assumeIsolated { _ = route(url) }
    }

    @MainActor
    private func route(_ url: URL) -> Bool {
        guard url.scheme == "weather-app" else {
            if url.scheme == "http" || url.scheme == "https" {
                UIApplication.shared.open(url)
                return true
            }
            return false
        }

        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        var params: [String: String] = [:]
        for item in components?.queryItems ?? [] {
            params[item.name] = item.value
        }

        switch url.host {
        case "navigate":
            guard let screenParam = params["screen"], let screen = Screen(rawValue: screenParam.lowercased()) else {
                return false
            }
            actions?.navigate(to: screen)
            return true

        case "back":
            actions?.back()
            return true

        case "set_lang":
            guard let value = params["value"], !value.isEmpty else { return false }
            actions?.setLang(value)
            return true

        case "set_theme":
            guard let mode = params["mode"], ["system", "dark", "light"].contains(mode) else { return false }
            actions?.setTheme(mode)
            return true

        case "set_compact":
            guard let raw = params["value"], let value = Bool(raw) else { return false }
            actions?.setCompact(value)
            return true

        case "set_city":
            guard let lat = params["lat"], let lon = params["lon"] else { return false }
            actions?.setCity(lat: lat, lon: lon, name: params["name"] ?? "")
            return true

        case "city_search":
            actions?.citySearch(query: params["q"] ?? "")
            return true

        default:
            return false
        }
    }
}
