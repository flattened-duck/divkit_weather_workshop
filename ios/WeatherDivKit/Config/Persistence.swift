import Foundation

/// Typed UserDefaults wrapper mirroring Android `MainActivity`'s SharedPreferences keys/defaults.
enum Persistence {
    private static let defaults = UserDefaults.standard

    private enum Key {
        static let lang = "pref_lang"
        static let themeMode = "pref_theme_mode"
        static let compact = "pref_compact"
        static let lat = "pref_lat"
        static let lon = "pref_lon"
        static let cityName = "pref_city_name"
    }

    static var lang: String {
        get { defaults.string(forKey: Key.lang) ?? "ru" }
        set { defaults.set(newValue, forKey: Key.lang) }
    }

    static var themeMode: String {
        get { defaults.string(forKey: Key.themeMode) ?? ThemeMode.system.rawValue }
        set { defaults.set(newValue, forKey: Key.themeMode) }
    }

    static var compact: Bool {
        get { defaults.bool(forKey: Key.compact) }
        set { defaults.set(newValue, forKey: Key.compact) }
    }

    static var city: (lat: String?, lon: String?, name: String?) {
        get {
            (
                defaults.string(forKey: Key.lat),
                defaults.string(forKey: Key.lon),
                defaults.string(forKey: Key.cityName)
            )
        }
        set {
            defaults.set(newValue.lat, forKey: Key.lat)
            defaults.set(newValue.lon, forKey: Key.lon)
            defaults.set(newValue.name, forKey: Key.cityName)
        }
    }
}
