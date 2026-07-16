import DivKit
import Foundation

enum CitySearchPatchAdapter {
    /// Backend returns bare {"changes":[...]} at root; parseDivPatch wants {"patch": {...}}.
    static func makePatch(from root: [String: Any]) throws -> DivPatch {
        var wrapper: [String: Any] = ["patch": root]
        if let templates = root["templates"] {
            wrapper["templates"] = templates
        }
        let data = try JSONSerialization.data(withJSONObject: wrapper)
        return try parseDivPatch(data)
    }
}
