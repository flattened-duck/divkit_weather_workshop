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
