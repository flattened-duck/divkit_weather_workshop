# DivKit Weather

**English** · [Русский](README.ru.md)

A small weather app built entirely on **server-driven UI**: the server describes the whole interface as JSON, and native **Android** and **iOS** clients render it. The UI can change on the server without rebuilding or reinstalling the app.

Built on the open-source [DivKit](https://github.com/divkit/divkit) framework (Apache-2.0): `com.yandex.div` on Android, the `DivKit` Swift package on iOS, and `kotlin-json-builder` (DivKit's Kotlin DSL) for building layouts on the server. Weather data comes from the open [Open-Meteo](https://open-meteo.com) API (no API key needed).

Originally made as material for a DivKit workshop for students.

---

## Features

- **Current weather** from Open-Meteo: temperature, "feels like", conditions, daily high/low.
- **Hourly forecast** (24 h) and a **7-day forecast**.
- **Collapsing header** on scroll.
- **Detail cards:** sunrise/sunset (sun arc), UV index, precipitation, visibility, humidity, pressure, wind.
- **City search** (geocoding): results load on the fly via `DivPatch`.
- **Themes** (System / Dark / Light) with weather-matched background photos (day/night follows the theme).
- **Localization** (ru / en).
- **Offline mode:** when the server is unreachable, the client renders a bundled copy of the layout.

---

## Structure

| Folder | What it is |
|---|---|
| `android/` | Android client. Renders the DivKit layout, handles navigation and actions. |
| `ios/` | iOS client (Swift/UIKit). Same DivKit envelope from the backend, native rendering. |
| `backend/` | Kotlin/Ktor service. Calls Open-Meteo and builds the DivKit layout for every screen. |
| `scripts/` | Utilities (e.g. regenerating the bundled offline skeleton from `/zero`). |

---

## How it works

- The server returns **one JSON envelope** per request: shared `templates` plus three screens (`main`, `settings`, `about`). The client parses the templates once and renders each screen.
- **Navigation needs no extra requests:** the client switches between screens it has already loaded.
- **Theme and compact mode** are reactive variables on the client: they change instantly, without a server call. **Changing the language or city** refetches the layout from the server (the server prepares strings and data).
- Background photos and images load by URL (Coil).

```
  Android / iOS (DivKit)  ──►  GET /document?lang=&lat=&lon=&name=  ──►  Ktor backend ──► Open-Meteo
        │                  ◄──  { templates, screens: {main, settings, about} }  ◄──
        └── offline fallback: document.json (same format)
```

Both native clients receive the same JSON envelope and render it with DivKit, so the server-side layout is platform-independent.

---

## Tests

- **Backend:** golden-snapshot tests of the generated layouts, smoke tests of the Ktor application, and unit tests for the Open-Meteo provider, weather-code mapping and screen adapters.
- **Android:** UI tests that drive the app through DivKit element IDs (offline mode, refetching, the zero-state skeleton), plus unit tests for navigation, preferences, theming and the document repository.
- **iOS:** an XCUITest suite mirroring the Android tests, plus unit tests for adapters, routing and the document repository.

```bash
cd backend && ./gradlew test
```

---

## Running

**You need:** JDK 17, the Android SDK, and an emulator (AVD) or a device.

### Backend

```bash
cd backend
./gradlew run                                   # Ktor on :8080

curl 'http://localhost:8080/ping'               # → pong
curl 'http://localhost:8080/document?lang=en' | python3 -m json.tool
curl 'http://localhost:8080/city-search?q=Berlin&lang=en'   # → DivPatch with a list of cities
```

Endpoints: `GET /document?lang=&lat=&lon=&name=` (Moscow by default), `GET /city-search?q=&lang=`, `GET /ping`.

### Android app

```bash
cd android
./gradlew installDebug          # build and install on a running emulator/device
# or open the android/ folder in Android Studio and run it
```

- The default server URL is `http://10.0.2.2:8080` (the host's `localhost` as seen from the emulator). For a physical device, set the host address in `DocumentLoader`.
- Without a running server, the app works from the bundled layout (`assets/document.json`).

### iOS app

**You need:** macOS with Xcode 26.x, [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`) and a local checkout of the [DivKit](https://github.com/divkit/divkit) sources.

> ⚠️ DivKit is linked as a **local** Swift package by an absolute path in `ios/project.yml` (`packages.DivKit.path`, currently `/Users/the-leo/divkit_source/divkit/client/ios`, tag `R-32.57`). Before building on another machine, point this path to your own DivKit checkout.

```bash
cd ios
xcodegen generate          # generate WeatherDivKit.xcodeproj from project.yml
open WeatherDivKit.xcodeproj   # then Cmd+R in Xcode
```

Building and running on a simulator from the command line:

```bash
cd ios
xcodegen generate
xcrun simctl boot 'iPhone 17' || true
xcodebuild -project WeatherDivKit.xcodeproj -scheme WeatherDivKit \
  -destination 'platform=iOS Simulator,name=iPhone 17' build

# install and launch the built .app on the booted simulator
APP=$(xcodebuild -project WeatherDivKit.xcodeproj -scheme WeatherDivKit \
  -destination 'platform=iOS Simulator,name=iPhone 17' -showBuildSettings \
  | awk -F' = ' '/ CODESIGNING_FOLDER_PATH/{print $2; exit}')
xcrun simctl install booted "$APP"
xcrun simctl launch booted com.example.weatherdivkit
```

- The default server URL is `http://localhost:8080`: the simulator reaches the host directly (ATS `NSAllowsLocalNetworking` is enabled). Change it in `WeatherDivKit/Config/AppConfig.swift`.
- Deployment target is **iOS 15.0**, Swift 5.9, bundle ID `com.example.weatherdivkit`.

> `android/`, `ios/` and `backend/` are **independent projects**: open them separately. There is no root `settings.gradle.kts`; the iOS project is generated by XcodeGen.

---

## Stack

| Component | Version |
|---|---|
| DivKit (Android `com.yandex.div`, backend `kotlin-json-builder`, iOS SPM) | `32.57.0` |
| Kotlin | `2.2.10` (android), `2.1.20` (backend) |
| Ktor | `3.1.3` |
| Gradle | `8.13` (android and backend) |
| Android Gradle Plugin (AGP) | `8.11.0` |
| Image loader (Android) | Coil `3.1.0` |
| HTTP client (Android) | OkHttp `4.12.0` |
| Weather source | Open-Meteo (no key) |
| Android: minSdk / targetSdk / compileSdk | 26 / 36 / 36 |
| Android JDK / jvmTarget | 17 / 11 |
| Backend JDK | 17 |
| iOS: Xcode / deployment target / Swift | 26.x / iOS 15.0 / 5.9 |
| iOS project generator | XcodeGen (`project.yml`) |

---

## License

An educational demo. [DivKit](https://github.com/divkit/divkit) is an open-source project (Apache-2.0). Weather data: [Open-Meteo](https://open-meteo.com) (CC BY 4.0).
