package workshop.l10n

/** Single source of truth for supported UI languages and language normalization. */
object LanguageSupport {
    const val DEFAULT = "ru"
    val SUPPORTED = setOf("ru", "en")

    /** Returns [lang] if supported, otherwise [DEFAULT]. Null-safe. */
    fun normalize(lang: String?): String = lang?.takeIf { it in SUPPORTED } ?: DEFAULT

    /** True iff [lang] normalizes to English. */
    fun isEnglish(lang: String?): Boolean = normalize(lang) == "en"
}
