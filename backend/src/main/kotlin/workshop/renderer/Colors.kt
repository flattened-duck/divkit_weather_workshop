package workshop.renderer

/**
 * Central palette of static ARGB color literals used across the renderers and [Theme].
 * Names describe the color/role; theme-reactive light/dark selection lives in [Theme],
 * which references these same constants.
 */
object Colors {
    // Neutrals
    const val WHITE = "#FFFFFFFF"
    const val NEAR_BLACK = "#FF1C1C1E"        // primary label / dark screen bg
    const val DARK_SURFACE = "#FF2C2C2E"
    const val CONTROL_INACTIVE = "#FF3A3A3C"  // unselected segment bg / dark input field
    const val GRAY = "#FF8E8E93"              // placeholder / tertiary text
    const val GRAY_LIGHT = "#FF9E9EA3"        // secondary text (dark theme) / arc track
    const val GRAY_DARK = "#FF6E6E73"         // secondary text (light theme)
    const val LIGHT_SURFACE = "#FFF2F2F7"     // light screen bg / light input field

    // Accents (iOS system palette)
    const val BLUE = "#FF007AFF"
    const val GREEN = "#FF34C759"
    const val ORANGE = "#FFFF9500"
    const val YELLOW = "#FFFFCC00"
    const val RED = "#FFFF3B30"
    const val PURPLE = "#FFAF52DE"
    const val PINK = "#FFFF2D55"
    const val INDIGO = "#FF5856D6"
    const val SKY_BLUE = "#FF5AC8FA"

    // Cards / FAB / overlays / tracks (semi-transparent)
    const val CARD_DARK = "#CC1C1C1E"
    const val CARD_LIGHT = "#CCFFFFFF"
    const val FAB_DARK = "#E6D8D8DD"
    const val FAB_LIGHT = "#E63A3A3C"
    const val SCRIM_BLACK = "#99000000"
    const val SCRIM_WHITE = "#99FFFFFF"
    const val TRACK_WHITE = "#33FFFFFF"
}
