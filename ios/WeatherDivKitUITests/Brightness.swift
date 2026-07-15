import XCTest
import UIKit

/// Average brightness (0…1) of a middle horizontal band of the current screen. Theme flips it:
/// light theme is bright, dark theme is dark, regardless of exact content.
func screenBrightness(_ app: XCUIApplication) -> CGFloat {
    guard let cg = app.screenshot().image.cgImage else { return 1 }
    let W = cg.width, H = cg.height
    let crop = CGRect(x: 0, y: Int(Double(H) * 0.30), width: W, height: Int(Double(H) * 0.30))
    guard let region = cg.cropping(to: crop) else { return 1 }
    var px = [UInt8](repeating: 0, count: 4)
    let ctx = CGContext(data: &px, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
                        space: CGColorSpaceCreateDeviceRGB(),
                        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    ctx.interpolationQuality = .medium
    ctx.draw(region, in: CGRect(x: 0, y: 0, width: 1, height: 1))  // scale whole band → 1px average
    return (CGFloat(px[0]) + CGFloat(px[1]) + CGFloat(px[2])) / 3 / 255
}

/// Polls brightness up to ~3s until it stabilises past `previous` (the theme re-render is async).
func pollBrightnessChanged(_ app: XCUIApplication, from previous: CGFloat?, timeout: TimeInterval = 3) -> CGFloat {
    let end = Date().addingTimeInterval(timeout)
    var last = screenBrightness(app)
    while Date() < end {
        let next = screenBrightness(app)
        if let previous, abs(next - previous) < 0.01 {
            usleep(150_000)
            last = next
            continue
        }
        last = next
        break
    }
    return last
}
