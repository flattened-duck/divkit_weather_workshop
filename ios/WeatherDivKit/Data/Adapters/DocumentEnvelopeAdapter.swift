import DivKit
import Foundation

enum DocumentEnvelopeAdapter {
    static func makeSources(from root: [String: Any]) throws -> [Screen: DivViewSource] {
        let root = IOSDivKitJsonAdapter.normalize(root) as? [String: Any] ?? root
        let templates = root["templates"] as? [String: Any] ?? [:]
        guard let screens = root["screens"] as? [String: Any] else {
            throw DocumentLoaderError.malformed("missing \"screens\"")
        }
        guard screens["main"] as? [String: Any] != nil else {
            throw DocumentLoaderError.malformed("missing \"screens.main\"")
        }

        var sources: [Screen: DivViewSource] = [:]
        for screen in Screen.allCases {
            guard let card = screens[screen.rawValue] as? [String: Any] else { continue }
            let sourceDict: [String: Any] = ["templates": templates, "card": card]
            let sourceData = try JSONSerialization.data(withJSONObject: sourceDict)
            sources[screen] = DivViewSource(kind: .data(sourceData), cardId: screen.cardId)
        }
        return sources
    }
}
