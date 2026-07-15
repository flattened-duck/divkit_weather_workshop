import DivKit

/// Single choke point for the SIX host-provided reactive global div-variables
/// (theme, theme_mode, compact, header_state, status_inset, nav_inset).
/// Wraps the deprecated `DivVariablesStorage.append(variables:triggerUpdate:)` — the only public
/// seed/mutate path on the frozen `factory.variablesStorage` — so the deprecation warning stays
/// localized here.
final class GlobalVariables {
    // Typed as DivVariableName (not String): a bare String does not implicitly convert to a
    // type that is merely ExpressibleByStringLiteral — only literal tokens get that conversion.
    // Dictionary-literal usage below (`[GlobalVariables.theme: .string(...)]`) requires the key
    // constants themselves to already be DivVariableName.
    static let theme: DivVariableName = "theme"
    static let themeMode: DivVariableName = "theme_mode"
    static let compact: DivVariableName = "compact"
    static let headerState: DivVariableName = "header_state"
    static let statusInset: DivVariableName = "status_inset"
    static let navInset: DivVariableName = "nav_inset"

    private let storage: DivVariablesStorage
    init(storage: DivVariablesStorage) { self.storage = storage }

    /// Initial declaration of ALL globals. Call ONCE before first render. No re-render.
    func seed(_ variables: DivVariables) {
        storage.append(variables: variables, triggerUpdate: false)
    }

    /// Mutate one or more globals at runtime. Triggers reactive re-render of affected cards.
    func set(_ variables: DivVariables) {
        storage.append(variables: variables, triggerUpdate: true)
    }
}
