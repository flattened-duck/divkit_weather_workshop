import DivKit

enum Screen: String, CaseIterable {
    case main
    case settings
    case about

    var cardId: DivCardID { DivCardID(rawValue: rawValue) }
}
