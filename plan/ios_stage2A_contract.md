# iOS Stage 2A — `sun_phase` DivCustomBlock — IMPLEMENTATION CONTRACT (kopatel)

You register a DivKit iOS **custom block** for `custom_type: "sun_phase"` that draws a
sunrise→sunset arc with a "now" marker, porting the BEHAVIOR of the Android
`SunPhaseCustomViewAdapter` (`app/src/main/java/com/example/weatherdivkit/divkit/SunPhaseCustomViewAdapter.kt`).
Port BEHAVIOR, not code.

You work in an **isolated git worktree** branched from `ios-client`. You add **exactly two things**:
one new file `ios/WeatherDivKit/Extensions/SunPhaseCustomBlock.swift`, and **one line** in
`WeatherHostViewController.makeComponents()`. Then regenerate `project.pbxproj`. Nothing else.

All DivKit/CoreGraphics facts below were VERIFIED against `/Users/the-leo/divkit_source/divkit` @ tag
`32.57.0` (`R-32.57`). Citations are `path:line` relative to `client/ios/`. Do NOT re-derive; do NOT
edit DivKit sources.

---

## A. MUST-NOT-GET-WRONG (read first)

1. **THE ELLIPSE MARKER.** The arc is an ELLIPSE: horizontal radius `r`, **vertical radius `r/2`**,
   centered at `ellipseCy = cy - r/2`. The marker MUST ride that ellipse:
   `mx = cx - r·cos(θ)`, `my = ellipseCy - (r/2)·sin(θ)`, where `θ = f·π`. Do NOT place it on a circle
   of radius `r` (`my = cy - r·sin θ`) — it drifts off the curve, worst toward sunset. The track, the
   progress arc, AND the marker all use the SAME parametric formula (§B.4) so the marker provably lands
   on the drawn curve.
2. **`custom_props` are RAW JSON literals, NOT expressions.** Read them off `data.data` (a
   `[String: Any]`) with `as? String`. No expression resolver, no `@{...}`. Values arrive already
   resolved as plain strings.
3. **Missing/invalid `sunrise` or `sunset` → draw ONLY the empty track, no crash, log a warning.**
   Never force-unwrap parsed times. `now`, when absent/invalid, comes from the DEVICE CLOCK.
4. **Arc bulges UP.** With UIKit's y-DOWN coordinate system, the semicircle occupies the UPPER half of
   the ellipse (θ: 0→π sampled as `y = ellipseCy - (r/2)·sin θ`, and `sin θ ≥ 0` ⇒ points sit ABOVE
   the center). Verify on-device the arc arches upward like Android.
5. **Dispatch by `data.name`.** iOS has no `isCustomTypeSupported`; the factory is called for EVERY
   custom block and switches on `data.name`. `case "sun_phase"` → your block; `default` → replicate the
   built-in miss (`context.addError(message: "No block factory for DivCustom: \(data.name)")` +
   `EmptyBlock.zeroSized`) so any future unknown custom still surfaces an error.
6. **`contentMode = .redraw` + transparent background** on the `UIView`. Without `.redraw`, UIKit
   stretches the cached bitmap on resize/rotation and the arc geometry goes wrong. Use `bounds` (NOT
   the `rect` argument) for all geometry in `draw(_:)`.
7. **Frozen seam untouched.** Do NOT change any signature in `DivComponentsFactory.swift`,
   `WeatherHostViewController` method signatures, or any Stage-0/1 file body except the single added
   registration line in `makeComponents()`. Do NOT touch scroll_state / images / theme / any other
   stage's files.

---

## B. IMPLEMENTER SPEC

### B.0 The registration seam — ADD ONE LINE

In `ios/WeatherDivKit/WeatherHostViewController.swift`, method `makeComponents()` (currently sets
`reporter` + `urlHandler`), insert this line (anywhere before `return factory.makeComponents()`):

```swift
factory.customBlockFactory = SunPhaseCustomBlockFactory()
```

That is the ONLY edit outside the new file. The property already exists:
`var customBlockFactory: DivCustomBlockFactory?` (`DivComponentsFactory.swift:7`), forwarded as
`divCustomBlockFactory: customBlockFactory` into `DivKitComponents(...)` (`DivComponentsFactory.swift:15`).

### B.1 New file — `ios/WeatherDivKit/Extensions/SunPhaseCustomBlock.swift`

Imports: `import DivKit`, `import LayoutKit`, `import UIKit`, `import Foundation`.
(`Block`/`EmptyBlock`/`GenericViewBlock` are LayoutKit; `DivCustomBlockFactory`/`DivCustomData`/
`DivBlockModelingContext` are DivKit; `CoreGraphics` types come with UIKit.)

Two types: `struct SunPhaseCustomBlockFactory: DivCustomBlockFactory` and
`final class SunPhaseView: UIView`.

### B.2 `SunPhaseCustomBlockFactory.makeBlock` — signature + rules

```swift
func makeBlock(data: DivCustomData, context: DivBlockModelingContext) -> Block
```
(Exact protocol method — `DivCustomBlockFactory.swift:22`. `DivCustomData` exposes
`name: String` and `data: [String: Any]` — `DivCustomData.swift:4-5`. The engine already fed
`name = customType`, `data = customProps ?? [:]` — `DivCustomExtensions.swift:24-30`.)

Rules:
1. `switch data.name`:
   - `case "sun_phase":` build and return the block (rules 2–4).
   - `default:` `context.addError(message: "No block factory for DivCustom: \(data.name)")`;
     `return EmptyBlock.zeroSized`.
2. Read props off `data.data` (all optional, `as? String`):
   - `sunrise`, `sunset` → `parseHhMm(...)` → `Int?` (minutes-since-midnight).
   - `now` → `parseHhMm(...)`; if `nil`, `nowFromDeviceClock()`.
   - `arc_color`, `track_color`, `marker_color` → `parseColor(...)`; fall back to the three defaults
     (§D golden block).
   - If `sunrise` OR `sunset` is `nil` after parsing → `context.addWarning(message: "sun_phase:
     missing/unparseable sunrise or sunset — rendering empty arc")` (`addWarning` —
     `DivBlockModelingContext.swift:196`). Still build the view (it renders just the track).
3. Construct `SunPhaseView(sunriseMin:sunsetMin:nowMin:arcColor:trackColor:markerColor:)` with the parsed
   values (see §B.3).
4. Wrap it in a `GenericViewBlock` and return:
   ```swift
   return GenericViewBlock(content: .view(view), width: widthTrait, height: heightTrait)
   ```
   (`GenericViewBlock(content:width:height:)` convenience init — `GenericViewBlock.swift:69-83`;
   `Content.view(ViewType)` — `:32-35`; `Trait` — `:20-24`.) Derive the traits from
   `data.widthTrait`/`data.heightTrait` (both `LayoutTrait` — `DivCustomData.swift:7-8`,
   `LayoutTrait` enum `LayoutTrait.swift:5-14`) with this mapping:
   ```swift
   let heightTrait: GenericViewBlock.Trait
   if case let .fixed(v) = data.heightTrait { heightTrait = .fixed(v) } else { heightTrait = .fixed(96) }
   let widthTrait: GenericViewBlock.Trait
   if case let .fixed(v) = data.widthTrait { widthTrait = .fixed(v) } else { widthTrait = .resizable }
   ```
   Rationale (see notes): the real card pins `height = fixed(120)`, `width = match_parent`
   (`WeatherMainRenderer.kt:359-360`) → this yields `height .fixed(120)`, `width .resizable`. The
   `else` branches reproduce Android's "default 96pt when unconstrained" / "fill width".

### B.3 `SunPhaseView: UIView`

Stored properties set once at init: `sunriseMin: Int?`, `sunsetMin: Int?`, `nowMin: Int`,
`arcColor: UIColor`, `trackColor: UIColor`, `markerColor: UIColor`.

`init(sunriseMin:sunsetMin:nowMin:arcColor:trackColor:markerColor:)`:
- `super.init(frame: .zero)`; store the six values.
- `backgroundColor = .clear`; `isOpaque = false`; `contentMode = .redraw`.
- Add `required init?(coder:)` = `fatalError` (not used).

Constants (points — do NOT multiply by any density; UIKit points are already density-independent):
`strokeWidth: CGFloat = 6`, `markerRadius: CGFloat = 6`.

`override func draw(_ rect: CGRect)` — geometry off `bounds`, per §B.4.

No separate "bind" step: iOS re-invokes `makeBlock` on every re-model/refetch, producing a freshly
configured view — that IS the rebind. Do not add observers or setters.

### B.4 `draw(_:)` — the CoreGraphics port (EXACT math)

All in points, y-DOWN (same orientation as Android's Canvas). Use `UIBezierPath`.

```
let w = bounds.width, h = bounds.height
let pad = markerRadius + strokeWidth            // = 12
let cx = w / 2
let cy = h - pad
let r  = min(cx, cy) - pad
guard r > 0 else { return }                     // too small: draw nothing (matches Android onDraw:159)
let ry = r / 2
let ellipseCy = cy - ry
```

Parametric point at parameter `t ∈ [0, π]` (this is the shared ellipse equation — track, arc, marker
all use it):
```
point(t) = CGPoint(x: cx - r*cos(t), y: ellipseCy - ry*sin(t))
```
Sanity: `t=0`→`(cx-r, ellipseCy)` left; `t=π/2`→`(cx, ellipseCy-ry)=(cx, cy-r)` top; `t=π`→`(cx+r,
ellipseCy)` right. Upper half of the ellipse.

**1. Track** (always, after the `guard`): polyline sampling `t` from `0` to `π` in `N = 64` segments
(`t_i = π·i/N`, `i = 0…N`), `move(to:)` the first point then `addLine(to:)` the rest. Set
`lineWidth = strokeWidth`, `lineCapStyle = .round`, `lineJoinStyle = .round`. `trackColor.setStroke()`,
`path.stroke()`.

**2. Progress arc + marker** (only when valid):
```
guard let sunrise = sunriseMin, let sunset = sunsetMin, sunset > sunrise else { return }
let f = min(max(CGFloat(nowMin - sunrise) / CGFloat(sunset - sunrise), 0), 1)
```
- Progress arc — only if `f > 0`: polyline sampling `t` from `0` to `f·π` in
  `M = max(2, Int((64·f).rounded()))` segments; same stroke style; `arcColor.setStroke()`.
- Marker (draw whenever times are valid, even at `f == 0`): `let m = point(f * .pi)`; fill a circle
  `UIBezierPath(ovalIn: CGRect(x: m.x - markerRadius, y: m.y - markerRadius, width: 2*markerRadius,
  height: 2*markerRadius))`; `markerColor.setFill()`, `fill()`.

(This mirrors Android exactly: `onDraw` returns before drawing the arc/marker when times are invalid or
`sunset <= sunrise` — `SunPhaseCustomViewAdapter.kt:166`; the marker formula is `.kt:174-178`.)

### B.5 Helpers (file-scope or static on the factory)

- `parseHhMm(_ s: String?) -> Int?` — port `.kt:64-72`: blank→nil; split on `":"`; require exactly 2
  parts; `hour = Int(parts[0])`, `minute = Int(parts[1])`; require `hour` non-nil in `0...23` and
  `minute` non-nil in `0...59`; return `hour*60 + minute`, else nil.
- `nowFromDeviceClock() -> Int` — `let c = Calendar.current; let now = Date(); let h =
  c.component(.hour, from: now); let m = c.component(.minute, from: now); return h*60 + m`. (Runtime
  device clock — the APP is allowed to call it; this is not a backend script.)
- `parseColor(_ s: String?) -> UIColor?` — port `parseColorOrNull` (`.kt:79-86`), accepting Android
  `Color.parseColor` hex forms `#RRGGBB` and `#AARRGGBB` (case-insensitive):
  ```
  guard var hex = s, hex.hasPrefix("#") else { return nil }
  hex.removeFirst()
  guard let v = UInt64(hex, radix: 16) else { return nil }
  let a, r, g, b: UInt64
  switch hex.count {
  case 6: a = 255;            r = (v >> 16) & 0xFF; g = (v >> 8) & 0xFF; b = v & 0xFF
  case 8: a = (v >> 24) & 0xFF; r = (v >> 16) & 0xFF; g = (v >> 8) & 0xFF; b = v & 0xFF
  default: return nil
  }
  return UIColor(red: CGFloat(r)/255, green: CGFloat(g)/255, blue: CGFloat(b)/255, alpha: CGFloat(a)/255)
  ```

---

## C. FILES

Create: `ios/WeatherDivKit/Extensions/SunPhaseCustomBlock.swift` (the `Extensions/` dir does not exist
yet — create it).

Modify (one line only): `ios/WeatherDivKit/WeatherHostViewController.swift` (§B.0).

Regenerate the Xcode project so it picks up the new file:
```
cd /Users/the-leo/divkit-weather-workshop/ios && xcodegen generate
```
(If `xcodegen` isn't on PATH, use `/opt/homebrew/bin/xcodegen generate`.) This rewrites
`ios/WeatherDivKit.xcodeproj/project.pbxproj` — that regenerated file IS an expected part of your diff.

Do NOT touch: `DivComponentsFactory.swift`, `Screen.swift`, `DocumentLoader.swift`,
`WeatherUrlHandler.swift`, `GlobalVariables.swift`, `Persistence.swift`, `project.yml`, or anything
outside `ios/`. Do NOT create any scroll_state / image / theme files.

---

## D. GOLDEN VALUES (pin exactly)

Defaults — define them via `parseColor` on the canonical ARGB hex so they equal what parsing produces
(single source of truth). Port of `.kt:182-184`:

```swift
static let defaultArcColor    = SunPhaseCustomBlockFactory.parseColor("#FFFFB74D")!  // 0xFFFFB74D
static let defaultTrackColor  = SunPhaseCustomBlockFactory.parseColor("#66FFFFFF")!  // 0x66FFFFFF
static let defaultMarkerColor = SunPhaseCustomBlockFactory.parseColor("#FFFFFFFF")!  // 0xFFFFFFFF
```

Numeric equivalents (for a unit assertion if you add one):
| default | ARGB | R,G,B,A (float, ≈) |
|---|---|---|
| arc | `0xFFFFB74D` | `1.0, 0.71765, 0.30196, 1.0` |
| track | `0x66FFFFFF` | `1.0, 1.0, 1.0, 0.40000` |
| marker | `0xFFFFFFFF` | `1.0, 1.0, 1.0, 1.0` |

Marker-position golden (given `cx, cy, r`, `ry = r/2`, `ellipseCy = cy - r/2`):
| f | θ=f·π | mx | my |
|---|---|---|---|
| 0.0 | 0 | `cx - r` | `ellipseCy` (= `cy - r/2`) |
| 0.5 | π/2 | `cx` | `ellipseCy - r/2` (= `cy - r`, the apex) |
| 1.0 | π | `cx + r` | `ellipseCy` |

Real card props (from `WeatherMainRenderer.kt:351-361`, verify with
`curl 'http://localhost:8080/document?lang=ru' | python3 -m json.tool | grep -A6 sun_phase`):
`custom_props = {sunrise:"HH:mm", sunset:"HH:mm", track_color:"#FF9E9EA3"}` (no `now`, no `arc_color`,
no `marker_color`), `width = match_parent`, `height = fixed(120)`. ⇒ arc uses the ORANGE default, marker
uses the WHITE default, track uses the grey `#FF9E9EA3`, `now` comes from the device clock.

---

## E. VERIFIED API REFERENCE (do NOT re-derive)

| fact | value | source |
|---|---|---|
| factory protocol | `protocol DivCustomBlockFactory { func makeBlock(data: DivCustomData, context: DivBlockModelingContext) -> Block }` | `DivKit/Extensions/DivCustom/DivCustomBlockFactory.swift:14,22` |
| props access | `DivCustomData.name: String`, `.data: [String: Any]` (= `custom_type`, raw `custom_props`) | `DivCustom/DivCustomData.swift:4-5` |
| engine wiring | `DivCustomData(name: customType, data: customProps ?? [:], … widthTrait, heightTrait)`; result wrapped in a `ContainerBlock` with the DivCustom's width/height traits | `DivCustom/DivCustomExtensions.swift:24-38` |
| unknown-custom error text | `"No block factory for DivCustom: \(data.name)"` | `DivCustom/DivCustomBlockFactory.swift:27` |
| host a UIView | `GenericViewBlock(content: .view(view), width: Trait, height: Trait)`; `Trait = .resizable/.fixed(CGFloat)/.intrinsic` | `LayoutKit/LayoutKit/GenericViewBlock.swift:19-83,20-24,32-35` |
| add warning | `context.addWarning(message:)` (non-fatal, surfaces in errors storage) | `DivKit/DivBlockModelingContext.swift:196-198` |
| add error | `context.addError(message:)` | `DivBlockModelingContext.swift:192-194` |
| `LayoutTrait` cases | `.fixed(CGFloat)`, `.intrinsic(...)`, `.weighted(Weight)` | `.../LayoutTrait.swift:5-14` |
| seam property | `var customBlockFactory: DivCustomBlockFactory?` → `divCustomBlockFactory:` | `ios/.../DivComponentsFactory.swift:7,15` |
| the canonical example to mirror | `switch data.name { case …: GenericViewBlock(content:.view(SampleView), width:.resizable, height:.fixed(200)); default: EmptyBlock() }` | `Samples/UIKitIntegration/DivKitSample/SampleDivCustomBlockFactory.swift:7-24` |

Coordinate/angle note: UIKit `UIView.draw(_:)` has origin top-left, **y-down** — same as Android
Canvas — so the ported formulas need NO axis flip. We deliberately draw the arc as a sampled polyline
(not `UIBezierPath(arcCenter:…)`, which only draws CIRCULAR arcs) so the ellipse and the marker share
one equation; this sidesteps CG's circular-arc-vs-ellipse mismatch entirely.

---

## F. GATE / HOW TO VERIFY (run these)

Bare tools (no `DEVELOPER_DIR` prefix). `SIM='iPhone 17'`. Backend on `:8080`.

1. Backend up: `curl -s -o /dev/null -w '%{http_code}' 'http://localhost:8080/document?lang=ru'` → `200`
   (else `cd backend && ./gradlew run`; do NOT modify backend).
2. Regenerate + build:
   ```
   cd /Users/the-leo/divkit-weather-workshop/ios && xcodegen generate
   cd /Users/the-leo/divkit-weather-workshop && xcodebuild \
     -project ios/WeatherDivKit.xcodeproj -scheme WeatherDivKit \
     -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17' build
   ```
3. Boot + install + launch (capture console):
   ```
   xcrun simctl boot 'iPhone 17' 2>/dev/null; open -a Simulator
   APP="$(xcodebuild -project ios/WeatherDivKit.xcodeproj -scheme WeatherDivKit \
        -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17' \
        -showBuildSettings 2>/dev/null | awk -F' = ' '/ BUILT_PRODUCTS_DIR /{print $2; exit}')/WeatherDivKit.app"
   xcrun simctl install 'iPhone 17' "$APP"
   xcrun simctl launch --console-pty 'iPhone 17' com.example.weatherdivkit
   ```
4. Drive with `mobile-mcp` tools: `mobile_take_screenshot`, `mobile_swipe_on_screen` (scroll the main
   screen DOWN to the "Sunset" card that hosts `sun_phase`), `mobile_save_screenshot`.

GATE checks (all must hold):
- **Build** succeeds; only the two intended changes + regenerated `project.pbxproj` in `git status`.
- **The `No block factory for DivCustom: sun_phase` render error is GONE** — grep the captured console;
  it must not appear. The on-screen red error-count badge drops by (at least) one vs before. The only
  permitted residual custom/extension error is `scroll_state` (Stage 2B).
- **The arc renders on the Sunset card:** an orange progress arc over a grey (`#FF9E9EA3`) track,
  arching UP, with a WHITE filled marker dot sitting **exactly ON the curve** (not floating above/below,
  not drifting toward the sunset end). Screenshot it. Compare against
  `talk/screens/main_full.png` / the Android sun_phase behavior for parity.
- **Marker position sanity:** for the live sunrise/sunset and current sim time, the dot should be near
  the apex around solar noon and near the right end near sunset; it must never leave the arc line.

Report: build result; the console line(s) proving the sun_phase error is gone (and classifying any
residual); screenshot path(s) of the Sunset card showing the marker on the ellipse;
`git status --porcelain` (only `Extensions/SunPhaseCustomBlock.swift`, the one-line
`WeatherHostViewController.swift` edit, and `WeatherDivKit.xcodeproj/project.pbxproj`).

---

## G. RISKS / OPEN QUESTIONS

1. **Height/width trait fill.** The GenericViewBlock is wrapped by the engine in a `ContainerBlock`
   carrying the DivCustom's own width/height traits (`DivCustomExtensions.swift:31-38`). The §B.2
   mapping gives the child `height .fixed(120)` = the container's fixed height, so no ambiguity about
   cross-axis resizing. If on-device the arc does NOT fill the 120pt card (clipped/short/zero-height),
   flip the child height to `.resizable` and re-check; report which worked. This is the one layout
   unknown; the on-device screenshot is the arbiter.
2. **Polyline vs true arc.** We sample 64 segments with round joins — visually a clean arc, and it
   GUARANTEES the marker lies on the drawn curve. Do not "improve" it to `UIBezierPath(arcCenter:…)`
   (that draws a circle, reintroducing the exact ellipse-drift bug this stage exists to avoid).
3. **`Calendar.current` timezone.** Uses the simulator's local time; the backend's `sunrise`/`sunset`
   are local strings too, so parity holds. No TZ math needed.

---

## H. OUT OF SCOPE (do NOT implement)

- `scroll_state` extension / header collapse / Shimmer (2B).
- Day/night background images, image holder, theme background, status bar, insets (2C / done).
- City search / DivPatch (3A); offline / cache / PTR (3B); tests (4).
- Any change to the frozen seam signatures, the spine's other methods, or any file outside the two
  listed in §C. This track ONLY adds the sun_phase custom block + its single registration line.

---

## ORCHESTRATOR NOTES (appendix — implementer applies defaults, may skip)

- **iOS has no create/bind split.** Android's `DivCustomContainerViewAdapter` separates
  `createView`/`bindView`/`isCustomTypeSupported`/`release`; iOS collapses all of it into one
  `makeBlock(data:context:)` that returns a fully-configured `GenericViewBlock` each modeling pass.
  Rebind-on-props-change is automatic (re-model → new view). `isCustomTypeSupported` becomes the
  `switch data.name` (item A.5). No lifecycle/`release` hook needed for a stateless drawing view.
- **Why the shared parametric equation (not CG arcs).** The whole point of Stage 2A is the ellipse
  gotcha (`app` commit `f854538` fixed the identical Android drift). CG's `addArc` draws circular arcs;
  matching an ellipse needs a CGAffineTransform on the path, which then has to agree pixel-for-pixel
  with the marker's separate parametric formula — two error surfaces that already bit Android. Driving
  track+arc+marker off ONE equation makes "marker on the curve" structurally true. Confidence HIGH.
- **Trait mapping confidence: MEDIUM** — the fixed(120) child inside the fixed(120) container is the
  predictable path, but LayoutKit container cross-axis behavior is the one thing not exhaustively
  traced here; G.1 gives the on-device fallback. Everything else (API shape, props access, error text,
  colors, math) is source-verified HIGH.
- **Thin-glue check:** NOT thin glue — the ellipse math + the custom-block API are genuine correctness
  traps; full planner→implement→review→verify ceremony is warranted.
