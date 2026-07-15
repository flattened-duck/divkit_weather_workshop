import DivKit
import Foundation
import LayoutKit
import UIKit

/// Registers `custom_type: "sun_phase"`. Port of the BEHAVIOR of the Android
/// `SunPhaseCustomViewAdapter` (draws a sunrise->sunset ellipse arc with a "now" marker).
struct SunPhaseCustomBlockFactory: DivCustomBlockFactory {
    func makeBlock(data: DivCustomData, context: DivBlockModelingContext) -> Block {
        switch data.name {
        case "sun_phase":
            return makeSunPhaseBlock(data: data, context: context)
        default:
            context.addError(message: "No block factory for DivCustom: \(data.name)")
            return EmptyBlock.zeroSized
        }
    }

    private func makeSunPhaseBlock(data: DivCustomData, context: DivBlockModelingContext) -> Block {
        let props = data.data
        let sunriseMin = Self.parseHhMm(props["sunrise"] as? String)
        let sunsetMin = Self.parseHhMm(props["sunset"] as? String)
        let nowMin = Self.parseHhMm(props["now"] as? String) ?? Self.nowFromDeviceClock()
        let arcColor = Self.parseColor(props["arc_color"] as? String) ?? Self.defaultArcColor
        let trackColor = Self.parseColor(props["track_color"] as? String) ?? Self.defaultTrackColor
        let markerColor = Self.parseColor(props["marker_color"] as? String) ?? Self.defaultMarkerColor

        if sunriseMin == nil || sunsetMin == nil {
            context.addWarning(
                message: "sun_phase: missing/unparseable sunrise or sunset — rendering empty arc"
            )
        }

        let view = SunPhaseView(
            sunriseMin: sunriseMin,
            sunsetMin: sunsetMin,
            nowMin: nowMin,
            arcColor: arcColor,
            trackColor: trackColor,
            markerColor: markerColor
        )

        let heightTrait: GenericViewBlock.Trait
        if case let .fixed(v) = data.heightTrait { heightTrait = .fixed(v) } else { heightTrait = .fixed(96) }
        let widthTrait: GenericViewBlock.Trait
        if case let .fixed(v) = data.widthTrait { widthTrait = .fixed(v) } else { widthTrait = .resizable }

        return GenericViewBlock(content: .view(view), width: widthTrait, height: heightTrait)
    }

    // MARK: - Golden defaults (single source of truth: parsed off the canonical ARGB hex)

    static let defaultArcColor = SunPhaseCustomBlockFactory.parseColor("#FFFFB74D")!
    static let defaultTrackColor = SunPhaseCustomBlockFactory.parseColor("#66FFFFFF")!
    static let defaultMarkerColor = SunPhaseCustomBlockFactory.parseColor("#FFFFFFFF")!

    // MARK: - Helpers

    static func parseHhMm(_ s: String?) -> Int? {
        guard let s, !s.isEmpty else { return nil }
        let parts = s.split(separator: ":", omittingEmptySubsequences: false)
        guard parts.count == 2 else { return nil }
        guard let hour = Int(parts[0]), (0...23).contains(hour) else { return nil }
        guard let minute = Int(parts[1]), (0...59).contains(minute) else { return nil }
        return hour * 60 + minute
    }

    static func nowFromDeviceClock() -> Int {
        let c = Calendar.current
        let now = Date()
        let h = c.component(.hour, from: now)
        let m = c.component(.minute, from: now)
        return h * 60 + m
    }

    static func parseColor(_ s: String?) -> UIColor? {
        guard var hex = s, hex.hasPrefix("#") else { return nil }
        hex.removeFirst()
        guard let v = UInt64(hex, radix: 16) else { return nil }
        let a: UInt64
        let r: UInt64
        let g: UInt64
        let b: UInt64
        switch hex.count {
        case 6:
            a = 255
            r = (v >> 16) & 0xFF
            g = (v >> 8) & 0xFF
            b = v & 0xFF
        case 8:
            a = (v >> 24) & 0xFF
            r = (v >> 16) & 0xFF
            g = (v >> 8) & 0xFF
            b = v & 0xFF
        default:
            return nil
        }
        return UIColor(red: CGFloat(r) / 255, green: CGFloat(g) / 255, blue: CGFloat(b) / 255, alpha: CGFloat(a) / 255)
    }
}

/// Draws a sunrise->sunset ellipse arc with a "now" marker. Stateless: iOS re-invokes
/// `makeBlock` on every re-model, producing a freshly configured view — that IS the rebind.
final class SunPhaseView: UIView {
    private let sunriseMin: Int?
    private let sunsetMin: Int?
    private let nowMin: Int
    private let arcColor: UIColor
    private let trackColor: UIColor
    private let markerColor: UIColor

    private let strokeWidth: CGFloat = 6
    private let markerRadius: CGFloat = 6

    init(
        sunriseMin: Int?,
        sunsetMin: Int?,
        nowMin: Int,
        arcColor: UIColor,
        trackColor: UIColor,
        markerColor: UIColor
    ) {
        self.sunriseMin = sunriseMin
        self.sunsetMin = sunsetMin
        self.nowMin = nowMin
        self.arcColor = arcColor
        self.trackColor = trackColor
        self.markerColor = markerColor
        super.init(frame: .zero)
        backgroundColor = .clear
        isOpaque = false
        contentMode = .redraw
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not used")
    }

    /// Shared parametric equation for the track, the progress arc, and the marker — this is what
    /// guarantees the marker is structurally ON the drawn ellipse curve (never drifts off it).
    private func point(_ t: CGFloat, cx: CGFloat, ellipseCy: CGFloat, r: CGFloat, ry: CGFloat) -> CGPoint {
        CGPoint(x: cx - r * cos(t), y: ellipseCy - ry * sin(t))
    }

    override func draw(_ rect: CGRect) {
        let w = bounds.width
        let h = bounds.height
        let pad = markerRadius + strokeWidth
        let cx = w / 2
        let cy = h - pad
        let r = min(cx, cy) - pad
        guard r > 0 else { return }
        let ry = r / 2
        let ellipseCy = cy - ry

        // 1. Track — always, sampled t in [0, pi].
        let trackN = 64
        let trackPath = UIBezierPath()
        trackPath.move(to: point(0, cx: cx, ellipseCy: ellipseCy, r: r, ry: ry))
        for i in 1...trackN {
            let t = CGFloat.pi * CGFloat(i) / CGFloat(trackN)
            trackPath.addLine(to: point(t, cx: cx, ellipseCy: ellipseCy, r: r, ry: ry))
        }
        trackPath.lineWidth = strokeWidth
        trackPath.lineCapStyle = .round
        trackPath.lineJoinStyle = .round
        trackColor.setStroke()
        trackPath.stroke()

        // 2. Progress arc + marker — only when sunrise/sunset are valid.
        guard let sunrise = sunriseMin, let sunset = sunsetMin, sunset > sunrise else { return }
        let f = min(max(CGFloat(nowMin - sunrise) / CGFloat(sunset - sunrise), 0), 1)

        if f > 0 {
            let arcEnd = f * CGFloat.pi
            let arcN = max(2, Int((CGFloat(trackN) * f).rounded()))
            let arcPath = UIBezierPath()
            arcPath.move(to: point(0, cx: cx, ellipseCy: ellipseCy, r: r, ry: ry))
            for i in 1...arcN {
                let t = arcEnd * CGFloat(i) / CGFloat(arcN)
                arcPath.addLine(to: point(t, cx: cx, ellipseCy: ellipseCy, r: r, ry: ry))
            }
            arcPath.lineWidth = strokeWidth
            arcPath.lineCapStyle = .round
            arcPath.lineJoinStyle = .round
            arcColor.setStroke()
            arcPath.stroke()
        }

        let m = point(f * CGFloat.pi, cx: cx, ellipseCy: ellipseCy, r: r, ry: ry)
        let markerPath = UIBezierPath(ovalIn: CGRect(
            x: m.x - markerRadius,
            y: m.y - markerRadius,
            width: 2 * markerRadius,
            height: 2 * markerRadius
        ))
        markerColor.setFill()
        markerPath.fill()
    }
}
