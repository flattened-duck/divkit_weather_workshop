import DivKit

final class LoggingDivReporter: DivReporter {
    func reportError(cardId: DivCardID, error: DivError) {
        print("DIVKIT_RENDER_ERROR [\(cardId.rawValue)] \(error)")
    }
}
