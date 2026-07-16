import UIKit

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
