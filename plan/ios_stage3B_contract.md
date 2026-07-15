# iOS Stage 3B — Offline: cache + two-phase cold start + bundled zero skeleton + graceful lang/city/PTR + empty:// shimmer

Implementer (kopatel) contract. You have NO prior context. Work in an **isolated git worktree branched from `ios-client`**, build/run on **iOS Simulator "iPhone 16e"** (a parallel Stage-3A track uses iPhone 17 and also edits `DocumentLoader.swift`/`WeatherHostViewController.swift` — keep your edits scoped; the orchestrator resolves the merge). Do **NOT** commit.

Behavioral source of truth = the Android client. All rules below are ports of it; `path:line` citations are given so you can re-verify, not re-derive.

---

## 0) MUST-NOT-GET-WRONG (load-bearing invariants — read first)

1. **KEEP-CURRENT vs FALLBACK is the whole point.** The bundle/cache fallback chain (`cache(lang) → skeleton`) is used **ONLY on cold start** (`coldStart`). A **refresh** (`setLang`, `setCity`, pull-to-refresh) is **network-ONLY**: on network failure you KEEP whatever is on screen. Never let a refresh failure fall through to the bundled skeleton or (except the one exception below) the cache — that fall-through is THE layout-wipe bug.
2. **`setLang` exception:** on network failure it MAY try `loadCache(lang)` before giving up (Android does — the cache may show a stale city, that's accepted). If cache also misses → keep current, never skeleton.
3. **`setCity` NEVER reads the cache.** The cache is lang-keyed, not city-keyed → a cache hit would show the PREVIOUS city under a "success". Network-only, keep-current. (Do NOT touch `citySearch(query:)` — that's Stage-3A.)
4. **Pull-to-refresh: MAIN screen only.** Attach `UIRefreshControl` to the main gallery's `UICollectionView` only. The spinner (`endRefreshing()`) must end on EVERY path (success, keep-current failure, thrown error).
5. **Two-phase start never blanks.** Phase 1 renders local (cache-or-skeleton) INSTANTLY; phase 2 fetches network in the background and swaps on success, else leaves phase-1's layout on screen.
6. **Cache is written ONLY on a successful network parse.** Write is **atomic** (`Data.write(options: .atomic)`). A write failure must NOT fail the network path that just succeeded (`try?`, swallow).
7. **Do NOT add `NWPathMonitor`/connectivity auto-refetch.** Refresh is manual only.
8. **Frozen:** do not change the existing `DocumentLoading.load(...)` signature. Add new methods additively.

---

## 1) Files you touch

| File | Change |
|---|---|
| `ios/WeatherDivKit/Resources/zero_ru.json` | **NEW** bundled resource — generate via curl (§2). |
| `ios/WeatherDivKit/Document/DocumentLoading.swift` | Add 2 method requirements to the protocol (additive). |
| `ios/WeatherDivKit/Document/DocumentLoader.swift` | Add cache write (in `load`), `loadCache`, `loadBundledSkeleton`, factor `makeSources`. |
| `ios/WeatherDivKit/WeatherHostViewController.swift` | Rewrite `coldStart` (two-phase); rewrite `setLang` (cache fallback); add PTR (`UIRefreshControl` + handler + BFS collection-view finder); call PTR-install from `renderScreen`. |
| `ios/project.yml` | Verify `zero_ru.json` gets bundled (§6). |

Do **NOT** edit `citySearch(query:)`, `setCity` (keep as-is), `ScrollStateExtensionHandler.swift`, backend, or Android code.

---

## 2) Generate the bundled zero skeleton (do this FIRST — backend must be running on :8080)

```bash
mkdir -p <worktree>/ios/WeatherDivKit/Resources
curl -s "http://localhost:8080/zero?lang=ru" -o <worktree>/ios/WeatherDivKit/Resources/zero_ru.json
# sanity:
python3 -c "import json;d=json.load(open('<worktree>/ios/WeatherDivKit/Resources/zero_ru.json'));assert set(d)>={'templates','screens'};assert set(d['screens'])>={'main','settings','about'};import json as j;s=j.dumps(d);assert s.count('empty://')==24 and s.count('\"shimmer\"')==24;print('OK',len(s),'bytes')"
```
VERIFIED shape (curl output): top keys `templates`,`screens`; screens `main`/`settings`/`about`; **same envelope as `/document`**. It contains the `main_scroll` vertical gallery with `extensions:[{"id":"scroll_state"}]` and 24 `image` divs with `image_url:"empty://"` + `extensions:[{"id":"shimmer"}]`. No real data (dashes + shimmer). If the file is empty/curl fails, STOP — the backend isn't up.

---

## 3) DocumentLoading protocol — additive requirements

Add to `protocol DocumentLoading` (leave `load(...)` untouched):

```swift
/// Reads the lang-keyed disk cache (written by a prior successful load). nil if missing/corrupt.
func loadCache(lang: String) -> DocumentBundle?
/// Reads the app-bundled zero skeleton (dashes + empty:// shimmer). nil if missing/unparseable.
func loadBundledSkeleton() -> DocumentBundle?
```
Both are **synchronous, non-throwing, optional-returning** (local reads must degrade to nil, never crash). `DocumentBundle` (in the same file) is unchanged: `{ sources: [Screen: DivViewSource], rawBody: Data }`.

---

## 4) DocumentLoader.swift — rules (write the bodies yourself)

Current `load(...)` (network) reshapes `{templates, screens}` → per-screen `DivViewSource` inline at `DocumentLoader.swift:42-50`. Factor + extend:

**4a. `private func makeSources(from root: [String: Any]) throws -> [Screen: DivViewSource]`**
- Exact reshape, unchanged from the current inline code: `templates = root["templates"] as? [String:Any] ?? [:]`; require `root["screens"] as? [String:Any]` (throw `DocumentLoaderError.malformed("missing \"screens\"")` if absent); require `screens["main"]` present (throw `malformed("missing \"screens.main\"")`).
- For each `screen in Screen.allCases` with a `screens[screen.rawValue] as? [String:Any]` card: build `sourceDict = ["templates": templates, "card": card]`, `JSONSerialization.data(withJSONObject:)`, `DivViewSource(kind: .data(sourceData), cardId: screen.cardId)`.
- `load(...)` now calls this (replacing its inline loop). Its network fetch/status/root-parse logic stays.

**4b. Cache write inside `load(...)`** — after `makeSources` succeeds (parse OK ⇒ this is the "network parse SUCCESS" point), before `return DocumentBundle(sources:..., rawBody: data)`, call `writeCache(lang: lang, data: data)`. This runs on the URLSession background context (fine). Writing here means EVERY successful network load caches under `lang` (cold-start phase-2, setLang, setCity, PTR) — mirrors Android `loadFromNetwork` (`DocumentLoader.kt:84`).

**4c. `private func writeCache(lang: String, data: Data)`**
- `try? data.write(to: cacheURL(lang), options: .atomic)`. The `.atomic` option writes a temp file + renames = the iOS atomic idiom. `try?` swallows failure (invariant #6). Do not throw.

**4d. `private func cacheURL(_ lang: String) -> URL`**
- `FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("doc_cache_\(lang).json")`. Use `.cachesDirectory` (the gate checks `Library/Caches/doc_cache_ru.json`).

**4e. `func loadCache(lang: String) -> DocumentBundle?`**
- `guard let data = try? Data(contentsOf: cacheURL(lang))` else nil (missing).
- `guard let root = (try? JSONSerialization.jsonObject(with: data)) as? [String:Any], let sources = try? makeSources(from: root)` else nil (corrupt ⇒ degrade, never crash — Android `loadFromCache` `DocumentLoader.kt:100-109`).
- `return DocumentBundle(sources: sources, rawBody: data)`.

**4f. `func loadBundledSkeleton() -> DocumentBundle?`**
- `guard let url = Bundle.main.url(forResource: "zero_ru", withExtension: "json"), let data = try? Data(contentsOf: url), let root = (try? JSONSerialization.jsonObject(with: data)) as? [String:Any], let sources = try? makeSources(from: root)` else nil.
- `return DocumentBundle(sources: sources, rawBody: data)`.

---

## 5) WeatherHostViewController.swift — rules

Context: `loader: DocumentLoading` (property, line 7); `sources: [Screen: DivViewSource]` swapped atomically; `divView` is the DivKit host UIView added as a subview; `renderScreen(_:)` at :124-132 does `Task { await divView.setSource(source, ...) }`; `refetchAndRender(...)` at :95-110 is keep-current (no fallback) and is KEPT for `setCity`.

**5a. Rewrite `coldStart()` — two-phase (port of `MainActivity.kt:115-136`):**
- Read `lang = Persistence.lang`, `(lat,lon,name) = Persistence.city`.
- **Phase 1 (instant, main thread):** `if let local = loader.loadCache(lang: lang) ?? loader.loadBundledSkeleton() { sources = local.sources; showScreen(.main) }`. (Priority = cache(lang) then skeleton. `showScreen(.main)` seeds the back stack + renders — same as today's `initial` path.)
- **Phase 2 (network, background):** `do { let fresh = try await loader.load(...); sources = fresh.sources; renderScreen(currentScreen) } catch { /* keep phase-1 layout */ print(...) }`.
- `coldStart` is already `@MainActor async` called from `viewDidLoad`'s `Task`. Phase-1 local read (~24 KB JSON reshape, no DivData parse) is cheap enough on main = "instant". The `sources` swap + render are already main-actor confined, so there is no race (mirrors Android's atomic replace).
- `viewDidLoad` is otherwise UNCHANGED (it already seeds globals then `Task { await coldStart() }`).

**5b. Rewrite `setLang(_:)` — network-only + same-lang cache fallback (port of `MainActivity.kt:159-179`):**
- `Persistence.lang = lang`; read city.
- `Task { do { let fresh = try await loader.load(...); sources = fresh.sources; renderScreen(currentScreen) } catch { if let cached = loader.loadCache(lang: lang) { sources = cached.sources; renderScreen(currentScreen) } else { print("setLang offline, no cache for \(lang), keeping current") } } }`.
- Do NOT route this through `refetchAndRender` (it swallows the error, so you couldn't trigger the cache fallback).

**5c. `setCity(...)` — LEAVE AS-IS.** It calls `refetchAndRender(initial:false)` = network-only, keep-current, NO cache read. Correct per invariant #3. Do not add a cache fallback here.

**5d. Pull-to-refresh.**
- Add a private BFS finder (replicate `ScrollStateExtensionHandler.firstCollectionView` `ScrollStateExtensionHandler.swift:84-92` — the OUTERMOST `UICollectionView` in the tree is the `main_scroll` vertical gallery):
  ```swift
  private func firstCollectionView(in root: UIView) -> UICollectionView? {
      var queue = [root]
      while !queue.isEmpty {
          let v = queue.removeFirst()
          if let cv = v as? UICollectionView { return cv }
          queue.append(contentsOf: v.subviews)
      }
      return nil
  }
  ```
- Install method, MAIN-gated, with retry (the collection view appears asynchronously after `setSource`; mirror the scroll-handler's 0.1s retry, cap ~25):
  ```swift
  private func installPullToRefreshIfMain(retriesLeft: Int = 25) {
      guard currentScreen == .main else { return }
      guard let cv = firstCollectionView(in: divView) else {
          if retriesLeft > 0 {
              DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { [weak self] in
                  self?.installPullToRefreshIfMain(retriesLeft: retriesLeft - 1)
              }
          }
          return
      }
      guard cv.refreshControl == nil else { return }   // don't double-attach to the same CV
      let rc = UIRefreshControl()
      rc.addTarget(self, action: #selector(handlePullToRefresh(_:)), for: .valueChanged)
      cv.refreshControl = rc
  }
  ```
- Handler — network-only, keep-current, spinner ALWAYS ends (port of `MainActivity.kt:192-214`):
  ```swift
  @objc private func handlePullToRefresh(_ sender: UIRefreshControl) {
      let lang = Persistence.lang
      let (lat, lon, name) = Persistence.city
      Task {
          defer { sender.endRefreshing() }             // ends on every path (MainActor)
          do {
              let fresh = try await loader.load(lang: lang, lat: lat, lon: lon, name: name)
              sources = fresh.sources
              renderScreen(currentScreen)
          } catch {
              print("pull-to-refresh offline, keeping current layout: \(error)")
          }
      }
  }
  ```
- **Wire-up:** at the END of `renderScreen(_:)` (after the `Task { await divView.setSource(...) }` line), add `installPullToRefreshIfMain()`. It self-gates to `.main`, so settings/about get no PTR, and every main render (phase-1 skeleton, phase-2 swap, PTR-success swap, navigate-back-to-main) re-attaches to the freshly-built collection view. `currentScreen` is set earlier in `renderScreen`, so the gate reads correctly.

**5e. `UIKit` is already imported.** `UIRefreshControl`/`UICollectionView` need no new import.

---

## 6) project.yml — bundling the resource

`sources: - path: WeatherDivKit` is a **recursive glob**, so `WeatherDivKit/Resources/zero_ru.json` is auto-included, and xcodegen infers `.json` into the **resources** build phase (Copy Bundle Resources). So after adding the file, just `xcodegen generate`. **VERIFY** it actually bundled (do not trust inference blindly):

```bash
cd <worktree>/ios && xcodegen generate
/usr/libexec/PlistBuddy -c 'Print' WeatherDivKit.xcodeproj/project.pbxproj 2>/dev/null | grep -c zero_ru.json || \
  grep -c "zero_ru.json" WeatherDivKit.xcodeproj/project.pbxproj
```
If the count is 0 (not in a Resources phase), add an explicit entry to the target's `sources:` list:
```yaml
    sources:
      - path: WeatherDivKit
        excludes:
          - "Resources/zero_ru.json"
      - path: WeatherDivKit/Resources/zero_ru.json
        buildPhase: resources
```
and regenerate. Runtime is the real arbiter: gate #3 fails if `Bundle.main.url(forResource:"zero_ru", withExtension:"json")` is nil.

---

## 7) Build / run / test commands (Simulator iPhone 16e, already booted)

Worktree + build:
```bash
git worktree add /Users/the-leo/wt-ios-3b -b ios-stage3b ios-client   # from the repo root
cd /Users/the-leo/wt-ios-3b/ios && xcodegen generate
xcodebuild -project WeatherDivKit.xcodeproj -scheme WeatherDivKit \
  -destination 'platform=iOS Simulator,name=iPhone 16e' \
  -derivedDataPath /tmp/wdk3b build
APP=/tmp/wdk3b/Build/Products/Debug-iphonesimulator/WeatherDivKit.app
BID=com.example.weatherdivkit
```
Install / launch / screenshot / logs:
```bash
xcrun simctl install booted "$APP"
xcrun simctl launch --console-pty booted "$BID"       # or: launch booted "$BID"
xcrun simctl io booted screenshot /tmp/shot.png
xcrun simctl spawn booted log stream --predicate 'process=="WeatherDivKit"' &   # optional
```
Inspect the on-disk cache (proves the write):
```bash
ls -l "$(xcrun simctl get_app_container booted "$BID" data)/Library/Caches/"
```

**Offline simulation (revertible, no shared-backend kill):** temporarily point the base URL at a dead port so `URLSession` fails fast (connection refused):
- Edit `ios/WeatherDivKit/Config/AppConfig.swift`: `baseURL = "http://localhost:9999"`, rebuild+reinstall. **REVERT to `http://localhost:8080` and rebuild before finishing.**

Fresh-install (no cache) = `xcrun simctl uninstall booted "$BID"` then reinstall.

---

## 8) GATE — prove each (screenshot + note the log/file evidence)

Backend on :8080 for online gates. `BID`/`APP` as above.

1. **Cold start ONLINE → real content + cache written.** baseURL=8080, fresh install, launch. Screenshot shows real weather (not dashes). Then `ls .../Library/Caches/` shows **`doc_cache_ru.json`** (non-empty). ✅ = content rendered AND file present.
2. **Cold start OFFLINE, warm cache → instant CACHED content.** After gate 1 (cache exists), set baseURL=9999, rebuild+reinstall (does NOT wipe the Caches container — reinstall preserves the data container; if unsure, do NOT uninstall, just reinstall over). Launch. Screenshot shows the previously-cached real content **instantly**, not the skeleton, not blank. ✅
3. **Cold start OFFLINE, NO cache → bundled skeleton with ANIMATING shimmer, no error.** `xcrun simctl uninstall booted "$BID"` (clears cache), keep baseURL=9999, reinstall, launch. Screenshot shows the zero skeleton (grey dashes/placeholder blocks). Take a short screen recording (`xcrun simctl io booted recordVideo /tmp/shimmer.mp4`, Ctrl-C after ~3s) to confirm the shimmer is **animating**. Confirm no DivKit render-error overlay/badge. ✅ (Mechanism verified in source — see §9 — but the device is the arbiter.)
4. **PTR on main.** baseURL=8080, online. Pull down on the main screen → refetches, content updates, spinner ends. Then set baseURL=9999 (rebuild+reinstall), pull again → spinner ends, current layout is KEPT (no blank, no skeleton). ✅
5. **`set_lang` offline → keep current / cache, never skeleton.** With a warm cache and content on screen, baseURL=9999, tap the language toggle in settings. Screen keeps the current (or same-lang-cache) layout — never blanks to the skeleton. ✅

**After all gates: revert `AppConfig.baseURL` to `http://localhost:8080`, rebuild once, confirm the online cold start still works.** Report the final `git diff --stat` (must be limited to the 5 files in §1).

---

## 9) VERIFIED APIs (path:line / idiom)

- **Built-in shimmer keeps animating on `empty://`, no error** — `DivKitExtensions/Shimmers/Shimmer/ShimmerImagePreviewExtension.swift:14-40` (`id = "shimmer"` at :43). It calls `context.imageHolderFactory.make(div.resolveImageUrl(...), .view(shimmerViewProvider))` — the shimmer view is the image's **placeholder**. For `image_url:"empty://"`, `DefaultImageHolderFactory.make(url, placeholder)` (`DivKit/Images/DivImageHolderFactory.swift:81-93`) builds a `RemoteImageHolder(url:"empty://", placeholder: shimmerView)`; the `empty://` scheme never resolves to image data → the placeholder (shimmer) stays visible/animating and no error is raised. Already registered: `WeatherHostViewController.makeComponents` appends `ShimmerImagePreviewExtension()` (line 81). The 24 skeleton images carry `extensions:[{"id":"shimmer"}]` (verified). Runtime = gate #3.
- **`DivView.setSource(_:debugParams:shouldResetPreviousCardData:)` is `async`** — `DivKit/Views/DivView.swift:148`. Default `shouldResetPreviousCardData:false` keeps globals across swaps (already relied on).
- **BFS collection-view finder** — `ScrollStateExtensionHandler.swift:84-92`. Outermost `UICollectionView` = `main_scroll`. Replicated in §5d (do not import from the handler; keep files decoupled).
- **`UIRefreshControl` on a DivKit `UICollectionView` is safe** — `refreshControl` is a `UIScrollView` property (iOS 10+); setting it does NOT touch the collection view's `delegate`/`dataSource`, so it does not fight the gallery. It coexists with the scroll_state KVO `contentOffset` observer (`ScrollStateExtensionHandler.swift:62`): a pull-down drives `contentOffset.y` negative ⇒ `distanceFromTop <= 0` ⇒ `atTop` ⇒ header stays "full" (no spurious collapse).
- **Caches dir + atomic write** — `FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]`; `Data.write(to:options:.atomic)` = write-temp-then-rename (the Darwin atomic idiom; equivalent to Android's `tmp.renameTo`).
- **Bundle load** — `Bundle.main.url(forResource:"zero_ru", withExtension:"json")` → `Data(contentsOf:)` → same `makeSources` reshape the network/cache paths use (envelope shape confirmed identical to `/document`).
- **xcodegen** 2.45.4 present; scheme `WeatherDivKit`. DivKit SPM package is an absolute local path (`project.yml:9`) → works from any worktree.

---

## 10) RISKS / open questions (apply the default; flag only if a gate contradicts it)

- **Shimmer-on-`empty://`:** mechanism confirmed in source (§9). Only risk is a runtime quirk (e.g. a visible broken-image glyph or a `DivErrorsStorage` badge). If gate #3 shows an error/badge instead of a clean animating shimmer, STOP and report — do not paper over it.
- **`/zero` vs `/document` shape:** confirmed identical (`templates`,`screens{main,settings,about}`), so `makeSources` handles both. If a future `/zero` diverges, `makeSources` will throw ⇒ `loadBundledSkeleton` returns nil ⇒ cold start blanks — that's a signal, report it.
- **PTR ↔ scroll_state on the same collection view:** both attach to the same `UICollectionView` (refreshControl property vs contentOffset KVO). Verified non-conflicting (§9). Watch during gate #4 that pulling to refresh does not wrongly collapse the header; if it does, report (do not disable scroll_state).
- **`.cachesDirectory` can be purged by the OS under storage pressure.** Accepted: a purge simply degrades gate #2 to gate #3 (skeleton). This matches the "instant offline is best-effort" intent. Do NOT switch to `.documentDirectory` without asking.
- **Reinstall vs data container:** `simctl install` over an existing install preserves the data (Caches) container; `uninstall` clears it. Gate #2 must NOT uninstall between the online and offline runs, or the cache is gone (that's gate #3, not #2).

## 11) OUT OF SCOPE (do not touch)

- City search + DivPatch (`citySearch(query:)`, `patchProvider`) — parallel Stage-3A track.
- Any test harness / unit tests — Stage 4.
- `ScrollStateExtensionHandler`, `sun_phase`, theme/compact/insets logic, backend, Android sources.
- Committing (orchestrator owns commits + the DocumentLoader/WeatherHostViewController merge + pbxproj regeneration).
