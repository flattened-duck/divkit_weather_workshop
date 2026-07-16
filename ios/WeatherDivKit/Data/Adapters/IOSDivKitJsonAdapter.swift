import Foundation

/// Works around two DivKit iOS gaps vs the backend's Android-first JSON, applied to every
/// dict node in the tree before it becomes a `DivViewSource`:
/// - gallery `resolveHorizontalInsets`/`resolveVerticalInsets` read only left/right/top/bottom,
///   never start/end (unlike the general resolver) — so gallery start/end padding is dropped.
/// - a state-item container with transition_in/out but no id can't be identified across a
///   state change, which DivKit reports as a render error and renders as a janky switch.
enum IOSDivKitJsonAdapter {
    static func normalize(_ node: Any) -> Any {
        if var dict = node as? [String: Any] {
            if dict["type"] as? String == "gallery", var paddings = dict["paddings"] as? [String: Any] {
                if paddings["left"] == nil, let start = paddings["start"] {
                    paddings["left"] = start
                }
                if paddings["right"] == nil, let end = paddings["end"] {
                    paddings["right"] = end
                }
                dict["paddings"] = paddings
            }
            if dict["id"] == nil, dict["transition_in"] != nil || dict["transition_out"] != nil {
                dict.removeValue(forKey: "transition_in")
                dict.removeValue(forKey: "transition_out")
            }
            for (key, value) in dict {
                dict[key] = IOSDivKitJsonAdapter.normalize(value)
            }
            return dict
        }
        if let array = node as? [Any] {
            return array.map { IOSDivKitJsonAdapter.normalize($0) }
        }
        return node
    }
}
