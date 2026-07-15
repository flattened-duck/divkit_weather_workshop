# iOS Stage 5 — Parity FIX Contract (6 confirmed divergences)

Status: PLANNER output. Diagnosis complete against real source + live app (sim iPhone 17
`981B1D02-5102-43C8-B827-A20579A528AF`, backend up on :8080, app `com.example.weatherdivkit`).
Every root cause below is CONFIRMED with file:line / live-JSON path / on-device evidence — none inferred.

---

## MUST-NOT-GET-WRONG (read first)

1. There are exactly TWO code touch-points. (a) `WeatherHostViewController.swift` — one line (edge-to-edge frame). (b) `DocumentLoader.swift` — one JSON-normalisation pass over the document before it becomes a `DivViewSource`. NOTHING else changes. **Backend is FROZEN — do not touch `backend/`. Do not modify the vendored DivKit at `/Users/the-leo/divkit_source/divkit`.**
2. Edge-to-edge means `divView.frame = view.bounds` (FULL window). Do NOT keep `.inset(by: view.safeAreaInsets)`. Keep feeding `status_inset`/`nav_inset` exactly as today — they are the ONLY inset mechanism after this change (matches Android).
3. The JSON transform runs inside `makeSources(from:)` on the WHOLE `root` dict (templates + screens) BEFORE any `DivViewSource` is built, so it covers the network, cache, AND bundled-skeleton paths (all three call `makeSources`).
4. Gallery horizontal-padding fix = copy `start`→`left` and `end`→`right` on `type=="gallery"` paddings only. This app is LTR-only (ru/en) so `start==left`, `end==right`. Only add `left`/`right` when absent; never overwrite.
5. Header-transition fix = on any node that has `transition_in` OR `transition_out` but NO `id`, delete BOTH `transition_in` and `transition_out`. Do not touch `transition_change` (it lives on the `state` node, which HAS an id).
6. Do not regress: header collapse/expand cycle, the 14 XCUITests, notch/home-indicator safe areas (content must still clear them — the `status_inset`/`nav_inset` layout paddings do that), and the `scroll_state` observer + PTR coexistence.
7. PTR already FIRES on device (proven). Symptom 5 is a VISIBILITY problem, not a wiring problem. Do NOT rewrite the PTR mechanism.

---

## ROOT-CAUSE FINDINGS (evidence)

### #1 + #4 — Double top/bottom inset (huge empty band above compact header; huge gap above Настройки/О приложении titles)
- `ios/WeatherDivKit/WeatherHostViewController.swift:44`
  `divView.frame = view.bounds.inset(by: view.safeAreaInsets)` pushes the DivView BELOW the status bar/notch AND above the home indicator.
- `WeatherHostViewController.swift:47-53` (`viewSafeAreaInsetsDidChange`) ALSO feeds `status_inset = safeAreaInsets.top`, `nav_inset = safeAreaInsets.bottom` into the globals, and the backend layout adds these as padding:
  - main: `fullHeader` top `@{24 + status_inset}`, `compactHeader` top `@{12 + status_inset}`, `main_scroll` top `@{(compact?76:210)+status_inset}` bottom `@{96+nav_inset}`, `fabRow` bottom `@{20+nav_inset}`.
  - settings/about galleries: top `@{16 + status_inset}`, bottom `@{16 + nav_inset}`.
- ⇒ the top safe-area (~59pt on iPhone 17) and the bottom safe-area are counted TWICE: once by the inset frame, once by the layout padding.
- On-device evidence: `/tmp/s_main.png`, `/tmp/s_compact2.png`, `/tmp/s_settings.png` all show a BLACK band across the status-bar strip (the window background — DivView doesn't reach it) plus, in compact mode, ~190px of empty space above "Москва", and on Settings ~a full status-bar-height + 16 gap above "Настройки". Android is edge-to-edge (`MainActivity.kt:69 WindowCompat.setDecorFitsSystemWindows(window,false)`) and single-counts via `systemBars` insets (`MainActivity.kt:93-99`).

### #2 + #3 — Gallery horizontal (`start`/`end`) padding dropped on iOS (first hourly card flush-left; Settings/About content touches edges)
- DivKit iOS gallery inset bug. `DivGalleryProtocol.makeMetrics` (`/Users/the-leo/divkit_source/divkit/client/ios/DivKit/Extensions/DivGallery/DivGalleryProtocol.swift:109-110`) derives the gallery's axial+cross insets from `paddings.resolveHorizontalInsets(...)` / `resolveVerticalInsets(...)`.
- Those two resolvers (`/Users/the-leo/divkit_source/divkit/client/ios/DivKit/Extensions/DivEdgeInsetsExtensions.swift:46-60`) read ONLY `left`/`right`/`top`/`bottom` — they DO NOT read `start`/`end` (unlike the general `resolve(_:)` at lines 20-44, which handles start/end with RTL). So gallery `start`/`end` paddings resolve to 0.
- Backend emits `start`/`end` (live JSON `/tmp/doc_ru.json`):
  - `hourly_gallery` (horizontal): `{"start":16,"end":16}` → axial leading inset = 0 → first item flush at x≈0 (on-device element list: `Сейчас` container left edge x≈0). = **#2**.
  - `settings_scroll` / `about_scroll` (vertical): `{"start":16,"end":16,"top":"@{16+status_inset}","bottom":"@{16+nav_inset}"}` → cross (horizontal) insets = 0 → content flush to both edges (`/tmp/s_settings.png` title + cards touch edges). Their `top`/`bottom` DO apply (read via `resolveVerticalInsets`→top/bottom), which is why #4's vertical gap still exists. = **#3**.
- Why MAIN has side padding but Settings/About don't: `main_scroll` paddings = `{top,bottom}` only (NO start/end); every main card carries its own `margins:{start:16,...}` which are applied through the general container-margin path (honours start/end). Settings/About have NO per-card horizontal margins — they rely solely on the gallery's dropped `start/end`.

### #5 — Pull-to-refresh perceived MISSING (it actually FIRES)
- PROVEN functional on device: with the app settled at the top / full header (no state change possible, because `ScrollStateExtensionHandler.evaluate` keeps `header_state=full` while `atTop`), a clean pull-down produced exactly +2 `DIVKIT_RENDER_ERROR [main]` lines (`/tmp/app_console.log`, count 14→16). Those errors are emitted only by `setSource` (card re-modeling); `setSource` on a pull is reached only via `handlePullToRefresh → loader.load(success) → renderScreen → setSource`. A failed load would produce +0. So the `UIRefreshControl` triggers, the refetch succeeds, and the screen re-renders. (Also corroborated: after the pull the hourly row changed from `Сейчас 24° / 17:00…` to fresh time-relative `Сейчас 19° / 01:00…`.)
- Why the user sees "missing": the spinner is INVISIBLE and the refetched data is visually identical.
  - The `UIRefreshControl` is attached (`installPullToRefreshIfMain`, `WeatherHostViewController.swift:253-267`) to the BFS-first `UICollectionView` = `main_scroll`'s collection view. DivKit draws that collection view at overlap index 1, and draws the header overlay (`headerState`) at index 2 — ABOVE it (root overlap order in `WeatherMainRenderer.kt:488`). The refresh spinner renders at the collection's top edge (screen y≈0), i.e. UNDER the header overlay AND under the black status-bar band from the #1 double-inset. So no spinner is ever seen.
  - Secondary: the transparent-but-interactive full-header overlay covers the top ~200pt, so pulls STARTED in the top region are swallowed by the header and never reach the scroll view (the proven trigger required starting the pull at y≈540, below the header).
- ⇒ #5 is dominated by #1 (black band hides the spinner). Fixing #1 (edge-to-edge) lets the spinner show over the background photo in full/expanded mode.

### #6 — Janky header expand animation ("кривая анимация появления")
- CONSOLE-CONFIRMED root cause (`/tmp/app_console.log`): repeated
  `DIVKIT_RENDER_ERROR [main] [main/0/container/2/header/full/container]: The component id with the "transition_in" (and "transition_out") property for state change is missing. Either specify the id, or specify the "transition_trigger" property without "state_change" value.`
- Both header state-item container roots (`fullHeader`, `compactHeader`, `WeatherMainRenderer.kt:215-260, 262-284`) carry `transition_in`/`transition_out` (fade) but NO `id`. Live JSON confirms: state `id="header"`, both `states[].div` are `type=container`, `id=null`, `transition_in`/`transition_out` present. Without an id, DivKit's state-change transition cannot identify the component across the switch → the fade/appearance is broken.
- Amplifiers (not the root cause): the #1 double-inset makes the header's frame jump further on switch; and each header switch re-models both state items (observed +4 render-errors per collapse), so any misbehaviour is doubled. The state's own `transition_change`=changeBounds is fine (the `state` node HAS an id).

---

## IMPLEMENTER SPEC (do exactly this)

### Change 1 — Edge-to-edge frame (fixes #1, #4; primary remedy for #5)
File: `ios/WeatherDivKit/WeatherHostViewController.swift`, method `viewWillLayoutSubviews` (line 42-45).
- Replace the body so the DivView fills the whole window:
  `divView.frame = view.bounds`
- Do NOT change `viewSafeAreaInsetsDidChange` — it must keep writing `status_inset = round(safeAreaInsets.top)` and `nav_inset = round(safeAreaInsets.bottom)`. These remain the sole inset mechanism (identical to Android).
- In `installPullToRefreshIfMain`, after creating the `UIRefreshControl`, set a visible tint so the now-unhidden spinner reads against the photo: `rc.tintColor = .white`. (Cosmetic aid for #5; keep everything else in that method unchanged.)

### Change 2 — Document JSON normalisation (fixes #2, #3, #6)
File: `ios/WeatherDivKit/Document/DocumentLoader.swift`.
- Add a private pure function that recursively rewrites the parsed JSON, and call it once at the TOP of `makeSources(from root:)` — i.e. normalise `root` before reading `templates`/`screens`. Because all load paths (`load`, `loadCache`, `loadBundledSkeleton`) funnel through `makeSources`, this single call covers every path.
- Signature (write the body yourself; it is a plain recursive walk over `Any` produced by `JSONSerialization`, returning a transformed copy — dictionaries are `[String: Any]`, arrays `[Any]`):
  `private func normalizeForIOSDivKit(_ node: Any) -> Any`
- Apply BOTH rules to every dictionary node encountered (recurse into all dict values and array elements):
  - RULE A (gallery horizontal padding — #2/#3): if `dict["type"] as? String == "gallery"`, and `dict["paddings"]` is a `[String: Any]` `p`, then in `p`: if `p["left"] == nil` and `p["start"] != nil` set `p["left"] = p["start"]`; if `p["right"] == nil` and `p["end"] != nil` set `p["right"] = p["end"]`. Leave `start`/`end`/`top`/`bottom` untouched. Write `p` back into the node. (Values may be Int or String-expression; copy them verbatim — do not parse.)
  - RULE B (orphan state-change transitions — #6): if the dict has key `transition_in` OR key `transition_out`, AND has NO `id` key, then REMOVE both `transition_in` and `transition_out` from the dict. (Do not add an id. Do not touch `transition_change`.)
- Order does not matter; a node is never both a gallery-with-paddings and a transition-carrier here, but applying both guards to every node is fine.
- The function must be total: pass through scalars and untouched keys unchanged; preserve all other content byte-for-byte.

Nothing else changes. No new files. No backend edits. No DivKit edits.

---

## GOLDEN / REFERENCE VALUES (pin; verify against these)

Live document (`GET /document?lang=ru`) gallery paddings BEFORE transform (what the transform receives):
```
main_scroll     : {"bottom":"@{96 + nav_inset}","top":"@{(compact ? 76 : 210) + status_inset}"}      → RULE A no-op (no start/end)
hourly_gallery  : {"end":16,"start":16}                                                                → becomes {"end":16,"start":16,"left":16,"right":16}
settings_scroll : {"bottom":"@{16 + nav_inset}","end":16,"start":16,"top":"@{16 + status_inset}"}      → adds "left":16,"right":16
about_scroll    : {"bottom":"@{16 + nav_inset}","end":16,"start":16,"top":"@{16 + status_inset}"}      → adds "left":16,"right":16
```
Header state (screens.main) BEFORE transform:
```
state id="header", state_id_variable="header_state", transition_change present (KEEP).
  states[0] state_id="full"      div: type=container, id=null, transition_in✓ transition_out✓  → RULE B strips transition_in/out
  states[1] state_id="collapsed" div: type=container, id=null, transition_in✓ transition_out✓  → RULE B strips transition_in/out
```
Expected counts after transform on the ru document: RULE A rewrites exactly 3 gallery paddings (hourly_gallery, settings_scroll, about_scroll); RULE B strips transitions from exactly 2 nodes (header full/collapsed). `templates` is empty; every gallery/state lives under `screens`.

Expected on-device outcome numbers:
- status_inset ≈ 59 (iPhone 17). After edge-to-edge: "Настройки" title top ≈ `0 + 16 + 59` = 75pt below the window top (right under the status bar); compact "Москва" ≈ `0 + 12 + 59` = 71pt. No black bands.
- Hourly first card leading = 16pt (was 0). Settings/About side padding = 16pt both sides (was 0).

---

## VERIFICATION / ACCEPTANCE (prove each on iPhone 17)

Build+install: build the `WeatherDivKit` scheme for the sim, `xcrun simctl install booted <app>`, launch `com.example.weatherdivkit`.

Per-symptom (before/after screenshots at the named screen):
1. Main, compact header: scroll up ~450pt to collapse. PASS = no black band + no large empty band above "Москва"; compact header sits directly under the status bar. (Before: `/tmp/s_compact2.png`.)
2. Main, hourly row: PASS = first card ("Сейчас") has ~16pt leading gap (not flush at x≈0). Re-check the element list: `Сейчас` container left edge ≈ 16, not ≈0/8.
3. Settings AND About: PASS = ~16pt padding on BOTH side edges (title + cards no longer touch the edges). (Before: `/tmp/s_settings.png`.)
4. Settings AND About title: PASS = "Настройки"/"О приложении" sit just under the status bar (~75pt), no huge top gap, no black band.
5. PTR: at the top of main, pull down from the content area → a spinner is now VISIBLE over the photo and the list refetches. Regression-proof it still fires by watching `DIVKIT_RENDER_ERROR` count +2 on a clean pull (relaunch with `xcrun simctl launch --console-pty booted com.example.weatherdivkit`). NOTE (on-device gate): if the spinner is still occluded by the compact-header scrim when initiated mid-list, that is acceptable as long as a pull from the true top (full/transparent header) shows it; if it is STILL fully invisible after edge-to-edge, escalate (see ORCHESTRATOR NOTES → #5 fallback) rather than rewriting.
6. Header expand: collapse (scroll up) then scroll to top to expand; the appearance is clean (no janky fade), and the console shows the `transition_in/out ... missing id` errors are GONE (the red layout-error badge count drops). Toggle compact off in Settings and return to main — expand still clean.

Non-regression (must all still pass):
- Header collapse↔expand cycle repeated ≥3×: smooth, no flicker/oscillation, end states correct.
- The 14 XCUITests (Stage 4 suite) — re-run green.
- Notch/home-indicator: no content clipped under the notch or home bar on any screen (top/bottom layout paddings still reserve them).
- PTR coexists with `scroll_state` (both observe the same collection view — KVO vs `refreshControl` don't conflict; unchanged).
- `git diff` touches ONLY `WeatherHostViewController.swift` and `DocumentLoader.swift`.

---

## RISKS / EDGES

- Edge-to-edge + status bar legibility: `preferredStatusBarStyle` already flips with theme (`WeatherHostViewController.swift:55-57`); background photo now extends under the bar — confirm text stays readable in both themes (it did in the pre-fix full-header screenshots where the photo already showed at the top).
- RULE A must run on gallery `paddings` whose values may be Int OR expression strings (top/bottom are `@{…}`) — copy verbatim, never coerce; only left/right (which are the plain `16` Ints) get copied. Do not add left/right when the source `start`/`end` are absent (e.g. `main_scroll`), else you'd inject 0 and could suppress future intent — the guard already handles this (only copy when `start`/`end` present).
- RULE B is guarded by "has transition_in/out AND no id" — in this document that is EXACTLY the 2 header containers (console shows only `header/full` + `header/collapsed`). If a future layout puts a transition on an id-less node elsewhere, RULE B would strip it too; acceptable and correct (DivKit itself rejects that config).
- #5 residual: if after edge-to-edge the spinner is still hidden by the header overlay for mid-list pulls, that is a z-order limitation of "UIRefreshControl on the inner collection view" vs Android's SwipeRefreshLayout-wraps-everything. Do not attempt a host-level PTR rewrite in this stage without sign-off.
- Two independent XCUITest emulators/sim images exist (API 36/37 per memory) — only the iPhone 17 sim is in scope here.

---

## BOUNDARIES / WHAT DEFERS

- Backend stays frozen. The "proper" fixes for #2/#3 (galleries use `left`/`right` instead of `start`/`end`) and #6 (add `id` to the header containers) are BACKEND one-liners; they are intentionally NOT done here and are captured for a future backend-unfreeze (see ORCHESTRATOR NOTES). The client-side transform is the shippable fix now.
- No DivKit fork. The `resolveHorizontalInsets`/`resolveVerticalInsets` start/end omission is a genuine upstream DivKit iOS bug; we work around it in the document rather than patching the vendored lib.
- #5 host-level PTR re-architecture (spinner guaranteed above all overlays, à la Android SwipeRefreshLayout) is out of scope unless the edge-to-edge remedy proves insufficient on device.

---

## ORCHESTRATOR NOTES (implementer may skip)

- Fork justification #2/#3: three viable fixes — (A) client JSON transform [CHOSEN: no backend change, no DivKit fork, LTR-safe, survives DivKit R-bumps]; (B) backend `edgeInsets(left=16,right=16)` on the 3 galleries [cleanest, and Android is UNAFFECTED because Android honours both start/end and left/right — but backend is frozen]; (C) patch vendored DivKit `resolveHorizontal/VerticalInsets` to also read start/end [most upstream-correct, fixes RTL too, but forks the reference lib and future R-bumps clobber it]. If/when backend unfreezes, prefer (B) and drop RULE A.
- Fork justification #6: chosen fix STRIPS `transition_in/out` (deterministic; kills the error + the janky fade; the `state`'s `transition_change`=changeBounds still animates the switch). Faithful alternative = inject a stable unique `id` on each header state-item container to KEEP the designed crossfade; not chosen because DivKit iOS state-transition-with-id behaviour wasn't verified clean on device and stripping is lower-risk. Backend-equivalent = add `id="header_full"/"header_collapsed"` (or drop the fade) in `WeatherMainRenderer.kt`; Android unaffected (ids are inert there).
- Confidence: #1/#4 root cause — certain (frame line + double-count math + black-band screenshots). #2/#3 — certain (resolver source ignores start/end + live JSON + on-device flush). #6 — certain (verbatim console error naming the node + property). #5 — mechanism certain that PTR FIRES (render-count proof); the "why invisible" is high-confidence (z-order + black band) but the exact post-fix spinner visibility needs the on-device gate in step 5.
- This stage is small and mechanical; if the orchestrator prefers, Changes 1+2 could be applied directly by the orchestrator + reviewer without the full planner→kopatel ceremony. The load-bearing risk is entirely in the on-device re-verification of #5 and #6, not in the code volume.
