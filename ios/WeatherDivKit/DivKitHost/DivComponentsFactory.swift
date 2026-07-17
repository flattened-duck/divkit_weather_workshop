import DivKit

/// The SINGLE assembly point for DivKitComponents. Configure the stored properties, then call
/// makeComponents() exactly once. Registration is append-only so parallel tracks don't conflict.
final class DivComponentsFactory {
    var extensionHandlers: [DivExtensionHandler] = []      // S2B scroll_state, built-in Shimmers append here
    var customBlockFactory: DivCustomBlockFactory?         // S2A sun_phase sets this
    var patchProvider: DivPatchProvider?                   // S3A city-search may set this
    var urlHandler: DivUrlHandler?                          // S1 sets the real WeatherUrlHandler
    let variablesStorage: DivVariablesStorage               // S1 seeds global vars here
    var reporter: DivReporter?                             // Stage 0 sets LoggingDivReporter

    init(variablesStorage: DivVariablesStorage = DivVariablesStorage()) {
        self.variablesStorage = variablesStorage
    }

    func makeComponents() -> DivKitComponents {
        DivKitComponents(
            divCustomBlockFactory: customBlockFactory,
            extensionHandlers: extensionHandlers,
            patchProvider: patchProvider,
            reporter: reporter,
            urlHandler: urlHandler ?? DivUrlHandlerDelegate { _ in },
            variablesStorage: variablesStorage
        )
    }
}
