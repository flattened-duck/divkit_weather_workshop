# iOS Stage 2B — `scroll_state` header-collapse + built-in Shimmer — IMPLEMENTATION CONTRACT (kopatel)

You add TWO things to the Stage-0/1 iOS client and NOTHING else:

1. A `ScrollStateExtensionHandler` (`DivExtensionHandler`, id `scroll_state`) that observes the main
   vertical gallery's scroll offset and drives the global `header_state` String var
   (`"full"`/`"collapsed"`) with the Android hysteresis. The backend header is a `div-state` bound via
   `state_id_variable="header_state"`; DivKit re-models the card and switches the header state when the
   var changes (VERIFIED reactive from source — see §D.4).
2. Registration of DivKit's built-in shimmer handler `ShimmerImagePreviewExtension` (id `shimmer`,
   from `DivKitExtensions`) — no custom code.

Behavioral reference = the Android handler `app/src/main/java/com/example/weatherdivkit/divkit/
ScrollStateExtensionHandler.kt`. **Port BEHAVIOR, not code — the iOS mechanism is fundamentally
different** (see §A.1, §D). All DivKit facts below are VERIFIED against
`/Users/the-leo/divkit_source/divkit/client/ios` @ tag `32.57.0` (branch `R-32.57`); citations are
`path:line` relative to `client/ios/`. Do NOT re-derive; do NOT edit the DivKit sources.

You work in an ISOLATED git worktree branched from `ios-client`. Touch ONLY the three things in §C.

---

## A. MUST-NOT-GET-WRONG (read first)

1. **iOS extension handlers do NOT get the live view.** Unlike Android's `bindView(view: RecyclerView)`,
   the iOS `DivExtensionHandler` runs at *modeling* time and receives a LayoutKit `Block` + a
   `DivBlockModelingContext` — never a `UIView`/`UIScrollView` (`DivExtensionHandler.swift:16-59`). You
   CANNOT get the scroll offset from the handler's arguments. The scroll view is reached by walking the
   rendered `DivView` UIView tree (§D.2). Do not try to read offset from the `Block` or `context`.
2. **Observe scroll with block-based KVO on `contentOffset`, NOT by becoming the scroll delegate.** The
   gallery already owns `collectionView.delegate` (`GalleryView.swift:107`, a private compound delegate).
   Overwriting `.delegate` BREAKS the gallery. KVO on `\.contentOffset` is delegate-safe and fires
   continuously during user scroll AND at rest. Do NOT set `.delegate`.
3. **`atTop` must be inset-immune.** Use `distanceFromTop = contentOffset.y + adjustedContentInset.top`
   (the true distance scrolled from the top), NOT raw `contentOffset.y`. This is the iOS analog of
   Android `!canScrollVertically(-1)` / `computeVerticalScrollOffset()` and survives any top-inset shift
   caused by the header collapsing.
4. **Exact hysteresis (§B.2):** collapse only when `distanceFromTop > 24`; once collapsed STAY collapsed
   until `atTop` (`distanceFromTop <= 0.5`); `atTop` ⇒ always `"full"`; `compact==true` FORCES
   `"collapsed"`. No offset-based expand threshold.
5. **Change-only writes.** Only call the storage write when the resolved value differs from the CURRENT
   stored `header_state` value (read it back and compare). This makes writes idempotent across the two
   sources of `header_state` (this handler + Stage-1 `setCompact`) and avoids redundant re-models.
6. **Detach cleanly; never double-observe.** Hold the KVO token as a single `NSKeyValueObservation?`.
   Assigning a new token auto-invalidates the old one (block-based KVO), so re-attaching after a card
   swap does NOT leak or duplicate observers. Do NOT use `addObserver(_:forKeyPath:)` (manual removal is
   crash-prone on view dealloc).
7. **Everything runs on the main thread.** `accept(div:context:)` may be called off-main (async
   `setSource` models off-main). Hop to `DispatchQueue.main` before touching any handler state, the view
   tree, KVO, or the variables storage from the KVO callback (KVO on `contentOffset` already fires on
   main).
8. **Worktree discipline.** Add ONLY `ios/WeatherDivKit/Extensions/ScrollStateExtensionHandler.swift`,
   append TWO lines in `WeatherHostViewController.makeComponents()`, and regenerate
   `project.pbxproj`. Do NOT touch `sun_phase` (2A), offline/zero (Stage 3), `GlobalVariables.swift`,
   the frozen seams, or any other file.

---

## B. IMPLEMENTER SPEC

### B.1 New file `ios/WeatherDivKit/Extensions/ScrollStateExtensionHandler.swift`

Imports: `import DivKit`, `import UIKit`. **Do NOT `import LayoutKit`** — you implement only `id` +
`accept(div:context:)`; every other `DivExtensionHandler` method has a default impl
(`DivExtensionHandler.swift:61-85`), so you never name the LayoutKit `Block` type. This sidesteps the
transitive-import question entirely.

The EXACT mechanism below IS the spec (it is the load-bearing, non-derivable part of this stage). Write
it as given; the only latitude is trivial Swift phrasing. Constructor injection:
`variablesStorage: DivVariablesStorage` (for both reading `compact` + writing `header_state`) and
`hostView: @escaping () -> UIView?` (a lazy provider of the root `DivView`).

```swift
import DivKit
import UIKit

/// Drives the global `header_state` String var ("full"/"collapsed") from the scroll offset of the
/// main vertical gallery carrying `extensions:[{"id":"scroll_state"}]` (div id `main_scroll`).
///
/// iOS DIVERGENCE FROM ANDROID: the extension handler runs at modeling time and never receives the
/// scroll view. We use `accept(div:context:)` only as a "the scroll_state div is being (re)modeled"
/// signal, then walk the rendered DivView tree to the gallery's UICollectionView and observe its
/// `contentOffset` via delegate-safe block KVO. Hysteresis is ported from
/// app/.../divkit/ScrollStateExtensionHandler.kt.
final class ScrollStateExtensionHandler: DivExtensionHandler {
    let id = "scroll_state"

    private let variablesStorage: DivVariablesStorage
    private let hostView: () -> UIView?

    private var cardId = DivCardID(rawValue: "main")
    private var observation: NSKeyValueObservation?
    private weak var observedScrollView: UIScrollView?
    private var lastCollapsed: Bool?
    private var isRetryScheduled = false
    private var retriesLeft = 0

    private let collapsePt: CGFloat = 24       // COLLAPSE_DP in Android (iOS uses points directly)
    private let maxRetries = 25                 // ~2.5s of 0.1s ticks before giving up

    init(variablesStorage: DivVariablesStorage, hostView: @escaping () -> UIView?) {
        self.variablesStorage = variablesStorage
        self.hostView = hostView
    }

    // MARK: DivExtensionHandler (modeling-time signal only)

    func accept(div: DivBase, context: DivBlockModelingContext) {
        let cardId = context.cardId
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.cardId = cardId
            self.retriesLeft = self.maxRetries
            self.attach()
        }
    }

    // MARK: Attach / observe (main thread only)

    private func attach() {
        guard let root = hostView(),
              let scrollView = firstCollectionView(in: root) else {
            scheduleRetry()
            return
        }
        if scrollView === observedScrollView { return }   // already observing this instance
        // Assigning a new token invalidates the previous one → clean detach, no duplicates.
        observation = scrollView.observe(\.contentOffset, options: [.new]) { [weak self] sv, _ in
            self?.evaluate(sv)                              // KVO on contentOffset fires on main
        }
        observedScrollView = scrollView
        lastCollapsed = nil                                // fresh hysteresis for a new view
        evaluate(scrollView)                               // initial push
    }

    private func scheduleRetry() {
        guard !isRetryScheduled, retriesLeft > 0 else { return }
        isRetryScheduled = true
        retriesLeft -= 1
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { [weak self] in
            self?.isRetryScheduled = false
            self?.attach()
        }
    }

    /// BFS: the OUTERMOST UICollectionView in the DivView tree is the main vertical gallery
    /// (`main_scroll`). Nested horizontal galleries (e.g. hourly forecast) are its descendants and are
    /// found later, so BFS-first is correct. (Div id is NOT exposed as accessibilityIdentifier —
    /// verified — so we select by tree position, not id.)
    private func firstCollectionView(in root: UIView) -> UIScrollView? {
        var queue = [root]
        while !queue.isEmpty {
            let v = queue.removeFirst()
            if let cv = v as? UICollectionView { return cv }
            queue.append(contentsOf: v.subviews)
        }
        return nil
    }

    // MARK: Hysteresis (ported from Android updateCollapsed)

    private func evaluate(_ scrollView: UIScrollView) {
        let distanceFromTop = scrollView.contentOffset.y + scrollView.adjustedContentInset.top
        let atTop = distanceFromTop <= 0.5
        let forced = currentCompact()
        let prev = lastCollapsed ?? false
        let collapsed: Bool
        if forced {
            collapsed = true
        } else if atTop {
            collapsed = false
        } else if prev {
            collapsed = true
        } else {
            collapsed = distanceFromTop > collapsePt
        }
        guard lastCollapsed != collapsed else { return }
        lastCollapsed = collapsed
        writeHeaderState(collapsed ? "collapsed" : "full")
    }

    private func currentCompact() -> Bool {
        if case let .bool(value) = variablesStorage.getVariableValue(cardId: cardId, name: GlobalVariables.compact) {
            return value
        }
        return false
    }

    private func writeHeaderState(_ value: String) {
        // Change-only vs the ACTUAL stored value (robust to Stage-1 setCompact also writing it).
        if case let .string(current) = variablesStorage.getVariableValue(cardId: cardId, name: GlobalVariables.headerState),
           current == value {
            return
        }
        variablesStorage.append(variables: [GlobalVariables.headerState: .string(value)], triggerUpdate: true)
    }
}
```

Notes for the implementer:
- `GlobalVariables.compact` / `GlobalVariables.headerState` are existing `static let … : DivVariableName`
  constants (`DivKitHost/GlobalVariables.swift:15-16`). You only REFERENCE them — do NOT edit that file.
- `variablesStorage.append(variables:triggerUpdate:)` is the same (deprecation-warned but functional)
  global write path Stage 1 uses; the one localized warning in this file is acceptable (matches the
  `GlobalVariables` rationale). Do NOT switch to `outerStorage`.
- `getVariableValue(cardId:name:) -> DivVariableValue?` is the public untyped read
  (`DivVariablesStorage.swift:84-92`); a global falls through to `globalStorage`, so any card id works —
  `main` is correct here.
- `DivVariableValue` cases are `.string/.bool/.integer/...` (`DivVariableValue.swift:5-13`).

### B.2 The hysteresis (authoritative — matches Android exactly)

| Android (`ScrollStateExtensionHandler.kt`) | iOS equivalent |
|---|---|
| `offset = computeVerticalScrollOffset()` | `distanceFromTop = contentOffset.y + adjustedContentInset.top` |
| `atTop = !canScrollVertically(-1)` | `atTop = distanceFromTop <= 0.5` |
| `collapsePx = 24 * density` | `collapsePt = 24` (points; NO density multiply on iOS) |
| `forced = compact var` | `forced = currentCompact()` |
| `collapsed = forced || (atTop→false; prev→true; else offset>collapsePx)` | identical (§B.1 `evaluate`) |
| write only when `lastCollapsed` changes | change-only, AND idempotent vs stored value |
| `onScrolled` + `onScrollStateChanged(IDLE)` | KVO `contentOffset` (continuous, covers scroll + settle) |
| `unbindView` removes listener | KVO token auto-invalidates on replace / handler dealloc |

### B.3 Registration — the TWO appended lines in `makeComponents()`

In `ios/WeatherDivKit/WeatherHostViewController.swift`, method `makeComponents()` (currently lines
69-75), insert BEFORE `return factory.makeComponents()` and AFTER the existing
`globals = GlobalVariables(...)` line:

```swift
        factory.extensionHandlers.append(
            ScrollStateExtensionHandler(
                variablesStorage: factory.variablesStorage,
                hostView: { [weak self] in self?.divView }
            )
        )
        factory.extensionHandlers.append(ShimmerImagePreviewExtension())
```

- `ShimmerImagePreviewExtension` is `public` with `public init()` (`DivKitExtensions/Shimmers/Shimmer/
  ShimmerImagePreviewExtension.swift:7-12`), id `"shimmer"`. `WeatherHostViewController.swift` already
  `import DivKitExtensions` (line 2) — no new import.
- **The `hostView` closure must NOT be invoked during `makeComponents()`.** It is stored and called
  later (on main, during attach). By then `divView`/`divKitComponents` are fully initialized. Calling
  `self?.divView` eagerly here would recurse into `makeComponents()` — do not do it; a closure defers it.
- Do NOT change the frozen seam signatures (`DivComponentsFactory`, `HostActions`, etc.). This is the
  only edit to `WeatherHostViewController.swift`.

---

## C. FILES — create / modify / regenerate

Create:
- `ios/WeatherDivKit/Extensions/ScrollStateExtensionHandler.swift` (§B.1)

Modify (bodies only, ≤6 inserted lines):
- `ios/WeatherDivKit/WeatherHostViewController.swift` — `makeComponents()` appends the two handlers (§B.3)

Regenerate (new file must be in the target):
- `cd ios && DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer /opt/homebrew/bin/xcodegen generate`
  (xcodegen auto-includes files under `WeatherDivKit/`; this rewrites `WeatherDivKit.xcodeproj/
  project.pbxproj`).

Do NOT touch: `GlobalVariables.swift`, `DivComponentsFactory.swift`, `HostActions.swift`,
`DocumentLoader.swift`, `WeatherUrlHandler.swift`, `Screen.swift`, `project.yml`, any `Extensions/`
file for sun_phase, anything under `backend/` or `app/`, or the DivKit sources.

---

## D. VERIFIED API REFERENCE (the crux — do NOT re-derive)

### D.1 `DivExtensionHandler` protocol (iOS) — modeling-time, no view
`DivExtensionHandler.swift:16-59`: requires `var id: String`; methods `accept(div:context:)`,
`applyBeforeBaseProperties(to:div:context:) -> Block`, `applyAfterBaseProperties(...)`,
`getPreloadURLs(...)`. Default impls at `:61-85` (accept = no-op; apply* = return block unchanged;
preload = []). **Registering a handler with `id == "scroll_state"` clears the render error** — DivKit
emits `"No DivExtensionHandler for: \(id)"` only when no handler key matches
(`DivBlockModelingContext.swift:185-186`). `accept(div:context:)` IS invoked for every matching handler
during modeling of the div (`DivBaseExtensions.swift:191-193`, called from `setupExtensionsHandlers`
line 19 in the div→block build). The `scroll_state` div is the top-level `main_scroll` gallery item —
always modeled on card render (not a lazy cell), so `accept` always fires.

### D.2 Reaching the scroll view (the resolved unknown)
- iOS DivKit galleries are UICollectionView-backed: `GalleryBlock` → `GalleryView`
  (`GalleryBlock+UIViewRenderableBlock.swift:7-8`) which contains a
  `VisibleBoundsTrackingCollectionView` (`GalleryView.swift:28,94`), a `UICollectionView` subclass
  (`VisibleBoundsTrackingCollectionView.swift:5` → `NoContentTouchDelaysCollectionView` → `UICollectionView`).
- `DivView` is a `UIView` (`DivView.swift:16`; Stage 0/1 already `view.addSubview(divView)` /
  `divView.frame`), so a plain `subviews` BFS reaches the collectionView.
- The main vertical gallery `main_scroll` is the OUTERMOST gallery on the `main` card (verified in the
  live document: `screens.main.states[0].div.items[1]`, `type=gallery`, `id=main_scroll`,
  `extensions=[{id:scroll_state, params:{orientation:vertical}}]`). Nested horizontal galleries are its
  descendants → BFS-first UICollectionView == `main_scroll`.
- Div `id` is NOT surfaced as `accessibilityIdentifier` (only the `accessibility` spec is —
  `DivBaseExtensions.swift` has no id→identifier mapping; `AccessibilityBlock` sets
  `accessibilityIdentifier = model.accessibilityID`, not the div id). Hence select by tree position, not
  by id.
- **Why not the DivKit-native gallery state?** `GalleryView` writes `GalleryViewState` to
  `blockStateStorage` via an observer, but for a **default** (non-paging) gallery it does
  `setState(notifyingObservers: !model.scrollMode.isDefault)` — i.e. it does NOT notify on scroll frames
  (`GalleryView.swift:416-419`), only at scroll-END (`onDidEndScroll`, `:448`). Worse, the change signal
  `DivBlockStateStorage.stateUpdates` is `internal` (`DivBlockStateStorage.swift:78`) — not observable
  from the app module. So the native path gives neither continuous updates nor a public hook. KVO on the
  UICollectionView's `contentOffset` is the correct, continuous, delegate-safe mechanism.

### D.3 Reading / writing the globals
| fact | value | source |
|---|---|---|
| read (untyped) | `variablesStorage.getVariableValue(cardId:name:) -> DivVariableValue?` (global falls through to globalStorage) | `DivVariablesStorage.swift:84-92` |
| write global | `variablesStorage.append(variables:triggerUpdate:true)` → `globalStorage.put(_, notifyObservers:true)` | `DivVariablesStorage.swift:157-161` |
| value cases | `.string/.number/.integer/.bool/.color/.url/.dict/.array` | `DivVariableValue.swift:5-13` |
| name constants | `GlobalVariables.compact`, `GlobalVariables.headerState` (`DivVariableName`) | `DivKitHost/GlobalVariables.swift:15-16` |

### D.4 `state_id_variable` reactivity on iOS — VERIFIED reactive (HIGH confidence)
`DivState.makeBaseBlock` (`DivStateExtensions.swift:30-45`): when `stateIdVariable` is set it does
`context.makeBinding(variableName: stateIdVariable, defaultValue:)` + `stateManager.setState(...)`, and
picks `activeState = states.first { $0.stateId == stateManagerItem.currentStateID.rawValue }`.
`makeBinding` (`DivBlockModelingContext.swift:208-216`) calls
`variableTracker?.onVariableUsed(id: viewId, variable: header_state)` — registering `header_state` as a
tracked dependency of the card. Writing `header_state` via `append(triggerUpdate:true)` fires
`globalStorage.changeEvents` → `DivKitComponents` observes → `variableTracker.getAffectedCards` includes
the main card → the card re-models → the binding reads the NEW value → `activeState` switches to
`collapsed`/`full`, running the state's `transition_in`/`transition_out`. This is the SAME tracking
mechanism `theme` uses (proven reactive on-device in Stage 1). So the collapse WILL switch the header
state and clear the header transition state-change errors. (Runtime confirm this in §F — it is the one
piece that is only fully provable on-device.)

### D.5 Shimmer (`ShimmerImagePreviewExtension`, id `shimmer`)
`ShimmerImagePreviewExtension.swift:7-43`: `applyBeforeBaseProperties` only acts when the block is an
`ImageBlock` and the div a `DivImage`; it builds an image holder with a `.view(ShimmerImagePreviewView)`
PLACEHOLDER (`:30-39`). The shimmer is the image's PLACEHOLDER view; it animates via a
`CAGradientLayer`/`CABasicAnimation` keyed on `effectBeginTime`
(`ShimmerImagePreviewView.swift:9-20,...`) — independent of whether the image ever loads. The zero
skeleton uses `{id:"shimmer"}` on `empty://` image divs (verified via `curl /zero?lang=ru`). With
`empty://`, the remote load never completes, so the shimmer placeholder stays visible and keeps
animating. `empty://` is not a `divkit-asset` (`DivImageHolderFactory.swift:127`), so it becomes a
`RemoteImageHolder` whose load quietly fails — mechanism strongly implies no error badge, matching
Android's sentinel choice. **`empty://` shimmer is NOT exercised by Stage-2B's `/document` flow** (that
serves real image URLs). The zero skeleton is fetched only by Stage 3B. Therefore, for Stage 2B: verify
registration is error-free and real images still render; DEFER the `empty://` shimmer-animation +
no-error check to Stage 3 (state this in your report).

---

## E. PORT MAP (Android → iOS)

| Android | iOS |
|---|---|
| `DivExtensionHandler.bindView(view: RecyclerView)` | NO analog — `accept(div:context:)` signal + DivView-tree walk + KVO |
| `RecyclerView.OnScrollListener.onScrolled` | KVO `\.contentOffset` (continuous) |
| `onScrollStateChanged(IDLE)` | same KVO (fires at rest too) |
| `computeVerticalScrollOffset()` | `contentOffset.y + adjustedContentInset.top` |
| `!canScrollVertically(-1)` | `distanceFromTop <= 0.5` |
| `24 * density` px | `24` pt |
| `variableController.get(compact).getValue()` | `variablesStorage.getVariableValue(cardId:name:compact)` → `.bool` |
| `variableController.get(header_state).set(v)` | `variablesStorage.append([header_state:.string(v)], triggerUpdate:true)` |
| `WeakHashMap<RecyclerView,…>` (multiple RVs) | single `lastCollapsed`/`observedScrollView` (one main gallery) |
| `unbindView` removes listener | KVO token auto-invalidate |
| Android added a shimmer dependency | built-in `ShimmerImagePreviewExtension` (no custom code) |

---

## F. GATE — build / run / drive / verify (all must hold)

Let `DD=DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`, `SIM='iPhone 17'`.

1. Backend up: `curl -s -o /dev/null -w '%{http_code}' 'http://localhost:8080/document?lang=ru'` → `200`
   (else `cd backend && ./gradlew run`; do NOT modify backend).
2. Regenerate + build:
   ```
   cd ios && $DD /opt/homebrew/bin/xcodegen generate
   cd /Users/the-leo/divkit-weather-workshop && $DD xcodebuild \
     -project ios/WeatherDivKit.xcodeproj -scheme WeatherDivKit \
     -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17' build
   ```
3. Install + launch capturing console:
   ```
   $DD xcrun simctl boot 'iPhone 17' 2>/dev/null; open -a Simulator
   APP="$($DD xcodebuild -project ios/WeatherDivKit.xcodeproj -scheme WeatherDivKit \
        -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17' \
        -showBuildSettings 2>/dev/null | awk -F' = ' '/ BUILT_PRODUCTS_DIR /{print $2; exit}')/WeatherDivKit.app"
   $DD xcrun simctl install 'iPhone 17' "$APP"
   $DD xcrun simctl launch --console-pty 'iPhone 17' com.example.weatherdivkit
   ```
   Drive the UI with the `mobile-mcp` tools (screenshot, `mobile_swipe_on_screen`, `mobile_take_screenshot`).

**GATE checks:**
- **Build** succeeds; `git status` shows ONLY the new handler file, the VC edit, and the regenerated
  `project.pbxproj`.
- **`scroll_state` error GONE:** grep the captured console — `No DivExtensionHandler for: scroll_state`
  must NOT appear. (`sun_phase` may still error — that is Stage 2A, out of scope.)
- **Header collapses on scroll & is sticky:** screenshot `main` at top (header expanded/full). Swipe up
  to scroll the main gallery (`mobile_swipe_on_screen` up, on the main list area). Screenshot → the
  header switches to the collapsed state (smaller header) AFTER ~24pt. Keep scrolling / small
  up-and-down within the list → it STAYS collapsed. No `/document` request fires during any of this
  (reactive-only). The header `transition_in/transition_out` state-change errors clear.
- **Expands only at the very top:** swipe/scroll back down until the list is at the very top → header
  returns to full/expanded. Partial scroll-back that does NOT reach the top must keep it collapsed.
- **compact forces collapsed:** in settings, toggle `compact` ON → header is collapsed even at the top;
  scrolling to the very top does NOT expand it while compact is on (the `forced` branch). Toggle compact
  OFF at top → header expands.
- **Shimmer registration error-free:** launch is clean (no crash, no new render error from the shimmer
  handler); real weather images on `main` still render normally. (The `empty://` shimmer-animation check
  is DEFERRED to Stage 3 — state this.)
- **Screenshots to capture:** `main` full (top), `main` collapsed (scrolled), `main` collapsed-sticky
  (mid-scroll), `main` re-expanded (back at top), `main` compact-on (collapsed at top).

Report: build result; the console section proving `scroll_state` is gone (and classifying any residual
errors); the `/document` request count during scroll (must be the single cold-start request);
screenshot paths for each state; `git status --porcelain`.

---

## G. RISKS / DECISION TREE (the two iOS-divergence unknowns)

1. **Scroll observation reachability (RESOLVED, one runtime assumption).** The native gallery-state path
   is a dead end (§D.2). The chosen path — DivView-tree BFS to the outermost `UICollectionView` + KVO on
   `contentOffset` — is fully grounded in source. The single runtime assumption is TIMING: at cold start
   `accept` fires during modeling before the collectionView is laid out, so `firstCollectionView` may
   return nil on the first attempt — hence the bounded retry (`scheduleRetry`, ~2.5s). **Decision tree
   for the implementer at §F run time:**
   - If the header collapses correctly on scroll → the KVO path works; done.
   - If it NEVER collapses (grep shows the handler attached but no writes): the collectionView was not
     found (retry exhausted) or is not the outermost. Log inside `attach()` whether
     `firstCollectionView` returned a view and its class; confirm the outermost UICollectionView is the
     vertical `main_scroll` (it should be). If a nested horizontal gallery is somehow found first,
     tighten selection: among BFS-found collection views pick the one whose bounds ≈ the DivView bounds
     (the full-width/height one). Do NOT fall back to becoming the delegate.
   - If it collapses but never expands at top: your `atTop` is using raw `contentOffset.y` — switch to
     `contentOffset.y + adjustedContentInset.top` (§A.3).
2. **`state_id_variable` reactivity (VERIFIED reactive in source — §D.4).** HIGH confidence the header
   state switches when `header_state` changes, via the same tracker `theme` uses. **If** on-device the
   var changes (confirm by logging the write) but the header does NOT visually switch, the fault is the
   binding/tracking, not this handler — STOP and report to the orchestrator with the evidence (the write
   fired, the card did not re-model). Do NOT hack around it by mutating state some other way. Fallback in
   that (unexpected) case would be a macro-plan §5 escalation, not a Stage-2B change.
3. **Deprecated `append(variables:triggerUpdate:)`** — the only public global-write path on the frozen
   `DivVariablesStorage()`; functional in 32.57 (Stage 1 precedent). One localized warning is fine.
4. **Off-main `accept`** — mitigated by dispatching to main (§A.7). KVO `contentOffset` callbacks are
   already main.
5. **Swift concurrency warnings** — the `hostView` closure captures `[weak self]` (the VC) and is stored
   in a nonisolated handler; under Swift 5 language mode (`SWIFT_VERSION 5.9`) these are at most
   warnings, not errors. Do not add `@MainActor` to the handler (it would fight the nonisolated protocol
   requirements); keep all state access on main via dispatch.

---

## H. OUT OF SCOPE (do NOT implement)
- `sun_phase` DivCustomBlock (2A).
- Zero-skeleton asset, offline cache, two-phase cold start, PTR, the `empty://` shimmer-animation
  runtime check (Stage 3 — only register the shimmer handler here; defer that check).
- Images/theme/status-bar/insets beyond what Stage 1/2C already do.
- Any UI/unit tests (Stage 4).
- Do NOT edit `GlobalVariables.swift`, the frozen seams, or any file outside the two in §C.

---

## ORCHESTRATOR NOTES (appendix — implementer applies the defaults; skip on first read)

- **The core divergence** from Android: iOS `DivExtensionHandler` is a modeling-time block transformer,
  not a view binder. There is no supported way to observe a default gallery's continuous scroll through
  DivKit's own state pipeline (native state notifies only at scroll-end for default galleries and the
  signal is `internal`). The only robust, delegate-safe, continuous mechanism is KVO on the underlying
  `UICollectionView.contentOffset`, reached by walking the `DivView` tree. This is why the handler is
  "abused" as a mere `accept` signal + view-walk, rather than doing the observation through DivKit APIs.
- **Why BFS-outermost, not by id:** verified div `id` does NOT map to `accessibilityIdentifier` on iOS
  (unlike Android's view-tag). The main gallery is the single outermost vertical UICollectionView, so
  tree position is a stable selector for THIS layout. Stage 3B/Stage 4 will need a real div-id→view
  lookup (macro §7 Q4) for `main_scroll` PTR + tests — that is NOT solved here; flagged for those stages.
- **state_id_variable reactivity is verified from source** (D.4), reusing the exact tracker `theme` uses.
  I did not need a device probe to establish the mechanism, but the FINAL VERIFIER must still confirm the
  header visually switches on-device (the one runtime-only link).
- **Shimmer is thin** — one registration line; its real payoff (`empty://` skeleton) is a Stage-3
  concern. Registering it now clears the future `shimmer` handler-missing error and costs nothing.
- **Thin-glue check:** the shimmer half is pure glue, but the `scroll_state` half is NOT — the
  view-reachability mechanism, KVO lifecycle, inset-immune `atTop`, and the compact/hysteresis interplay
  each have correctness traps. The full planner→implement→review→verify ceremony is warranted for this
  track (it is the hardest Stage-2 track).
</content>
</invoke>
