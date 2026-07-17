# DivKit Weather

Небольшое приложение погоды, целиком построенное на **Server-Driven UI**: сервер описывает
весь интерфейс в виде JSON, а нативные клиенты (**Android** и **iOS**) его рендерят. UI можно
менять на сервере без пересборки и переустановки приложения.

Построено на открытом фреймворке [DivKit](https://github.com/divkit/divkit) (Apache-2.0):
`com.yandex.div` на Android, `DivKit` (Swift Package) на iOS и `kotlin-json-builder` для сборки
вёрстки на сервере. Данные о погоде — из открытого [Open-Meteo](https://open-meteo.com)
(без API-ключа).

---

## Возможности

- **Текущая погода** из Open-Meteo: температура, «ощущается как», состояние, макс/мин.
- **Почасовой прогноз** (24 ч) и **прогноз на 7 дней**.
- **Сворачивающаяся шапка** при скролле.
- **Детальные карточки:** восход/закат (дуга солнца), УФ-индекс, осадки, видимость,
  влажность, давление, ветер.
- **Поиск города** (геокодинг): результаты подгружаются на лету через `DivPatch`.
- **Темы** (Системная / Тёмная / Светлая) с фоновыми фото под погоду (день/ночь по теме).
- **Локализация** (ru / en).
- **Офлайн-режим:** при недоступном сервере клиент рендерит встроенную копию вёрстки.

---

## Структура

| Папка | Что это |
|---|---|
| `android/` | Android-клиент. Рендерит DivKit-вёрстку, обрабатывает навигацию и действия. |
| `ios/` | iOS-клиент (Swift/UIKit). Тот же DivKit-конверт от бэкенда, нативный рендеринг. |
| `backend/` | Kotlin/Ktor-сервис. Ходит в Open-Meteo, собирает DivKit-вёрстку всех экранов. |
| `scripts/` | Утилиты (напр. регенерация встроенного офлайн-скелета из `/zero`). |
| `plan/`, `talk/` | Проектные заметки и материалы доклада (не нужны для запуска). |

---

## Как это работает

- Сервер отдаёт **один JSON-конверт** на запрос: общие `templates` + три экрана
  (`main`, `settings`, `about`). Клиент парсит шаблоны один раз и рендерит каждый экран.
- **Навигация — без повторных запросов:** клиент переключает уже загруженные экраны.
- **Тема и компактный режим** — реактивные переменные на клиенте: меняются мгновенно,
  без обращения к серверу. **Смена языка или города** — перезапрашивает вёрстку с сервера
  (строки и данные готовит сервер).
- Фоновые фото и картинки грузятся по URL (Coil).

```
  Android / iOS (DivKit)  ──►  GET /document?lang=&lat=&lon=&name=  ──►  Ktor backend ──► Open-Meteo
        │                  ◄──  { templates, screens: {main, settings, about} }  ◄──
        └── офлайн-фолбэк: document.json (тот же формат)
```

Оба нативных клиента получают один и тот же JSON-конверт и рендерят его средствами DivKit —
серверная вёрстка не зависит от платформы.

---

## Запуск

**Нужно:** JDK 17, Android SDK, эмулятор (AVD) или устройство.

### Бэкенд

```bash
cd backend
./gradlew run                                   # Ktor на :8080

curl 'http://localhost:8080/ping'               # → pong
curl 'http://localhost:8080/document?lang=ru' | python3 -m json.tool
curl 'http://localhost:8080/city-search?q=Berlin&lang=en'   # → DivPatch со списком городов
```

Эндпоинты: `GET /document?lang=&lat=&lon=&name=` (Москва по умолчанию),
`GET /city-search?q=&lang=`, `GET /ping`.

### Приложение

```bash
cd android
./gradlew installDebug          # собрать и поставить на запущенный эмулятор/устройство
# или открыть папку android/ в Android Studio и запустить
```

- URL сервера по умолчанию — `http://10.0.2.2:8080` (это `localhost` хоста для эмулятора).
  Для физического устройства укажите адрес хоста в `DocumentLoader`.
- Без запущенного сервера приложение работает на встроенной вёрстке (`assets/document.json`).

### iOS-приложение

**Нужно:** macOS с Xcode 26.x, [XcodeGen](https://github.com/yonaskolb/XcodeGen)
(`brew install xcodegen`) и локально выкачанные исходники [DivKit](https://github.com/divkit/divkit).

> ⚠️ DivKit подключён как **локальный** Swift Package по абсолютному пути в `ios/project.yml`
> (`packages.DivKit.path`, сейчас `/Users/the-leo/divkit_source/divkit/client/ios`, тег `R-32.57`).
> Перед сборкой на другой машине поправьте этот путь под свой checkout DivKit.

```bash
cd ios
xcodegen generate          # сгенерировать WeatherDivKit.xcodeproj из project.yml
open WeatherDivKit.xcodeproj   # затем Cmd+R в Xcode
```

Сборка и запуск из командной строки на симуляторе:

```bash
cd ios
xcodegen generate
xcrun simctl boot 'iPhone 17' || true
xcodebuild -project WeatherDivKit.xcodeproj -scheme WeatherDivKit \
  -destination 'platform=iOS Simulator,name=iPhone 17' build

# поставить и запустить собранный .app на загруженный симулятор
APP=$(xcodebuild -project WeatherDivKit.xcodeproj -scheme WeatherDivKit \
  -destination 'platform=iOS Simulator,name=iPhone 17' -showBuildSettings \
  | awk -F' = ' '/ CODESIGNING_FOLDER_PATH/{print $2; exit}')
xcrun simctl install booted "$APP"
xcrun simctl launch booted com.example.weatherdivkit
```

- URL сервера по умолчанию — `http://localhost:8080`: симулятор ходит на хост напрямую
  (ATS `NSAllowsLocalNetworking` включён). Меняется в `WeatherDivKit/Config/AppConfig.swift`.
- Deployment target — **iOS 15.0**, Swift 5.9, bundle id `com.example.weatherdivkit`.

> `android/`, `ios/` и `backend/` — **независимые проекты**, открывайте их по отдельности.
> Корневого `settings.gradle.kts` нет; iOS-проект генерируется XcodeGen'ом.

---

## Стек

| Компонент | Версия |
|---|---|
| DivKit (Android `com.yandex.div`, backend `kotlin-json-builder`, iOS SPM) | `32.57.0` |
| Kotlin | `2.2.10` (android), `2.1.20` (backend) |
| Ktor | `3.1.3` |
| Gradle | `8.13` (android и backend) |
| Android Gradle Plugin (AGP) | `8.11.0` |
| Image loader (Android) | Coil `3.1.0` |
| HTTP-клиент (Android) | OkHttp `4.12.0` |
| Источник погоды | Open-Meteo (без ключа) |
| Android: minSdk / targetSdk / compileSdk | 26 / 36 / 36 |
| Android JDK / jvmTarget | 17 / 11 |
| Backend JDK | 17 |
| iOS: Xcode / deployment target / Swift | 26.x / iOS 15.0 / 5.9 |
| iOS project generator | XcodeGen (`project.yml`) |

---

## Лицензия

Демо в образовательных целях. [DivKit](https://github.com/divkit/divkit) — открытый проект
(Apache-2.0). Данные о погоде — [Open-Meteo](https://open-meteo.com) (CC BY 4.0).
