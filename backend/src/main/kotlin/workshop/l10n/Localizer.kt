package workshop.l10n

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.readValue

class Localizer(lang: String) {

    private val strings: Map<String, String>

    init {
        val effectiveLang = if (lang in SUPPORTED_LANGS) lang else DEFAULT_LANG
        val resourcePath = "/strings/strings_$effectiveLang.json"
        val stream = Localizer::class.java.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("String resource not found: $resourcePath")
        strings = JsonMapper.builder().build().readValue(stream)
    }

    fun getOrDefault(key: String, default: String): String =
        strings.getOrDefault(key, default)

    companion object {
        private const val DEFAULT_LANG = "ru"
        private val SUPPORTED_LANGS = setOf("ru", "en")
    }
}
