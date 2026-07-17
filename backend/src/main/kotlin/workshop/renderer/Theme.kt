package workshop.renderer

/** Reactive theme-expression strings shared by every screen; deduplicated from five near-identical
 *  copies (main/zero/settings/about/servant). Values are frozen — do not normalize/reformat. */
object Theme {
    const val PRIMARY_TEXT   = "@{theme == 'dark' ? '#FFFFFFFF' : '#FF1C1C1E'}"
    const val SECONDARY_TEXT = "@{theme == 'dark' ? '#FF9E9EA3' : '#FF6E6E73'}"
    const val CARD_BG        = "@{theme == 'dark' ? '#CC1C1C1E' : '#CCFFFFFF'}"
    const val FAB_BG         = "@{theme == 'dark' ? '#E6D8D8DD' : '#E63A3A3C'}"
    const val FAB_ICON       = "@{theme == 'dark' ? '#FF1C1C1E' : '#FFFFFFFF'}"
    const val HEADER_SCRIM   = "@{theme == 'dark' ? '#99000000' : '#99FFFFFF'}"
    const val SCREEN_BG      = "@{theme == 'dark' ? '#FF1C1C1E' : '#FFF2F2F7'}"
    const val SURFACE        = "@{theme == 'dark' ? '#FF2C2C2E' : '#FFFFFFFF'}"
    const val INPUT_FIELD    = "@{theme == 'dark' ? '#FF3A3A3C' : '#FFF2F2F7'}"
}
