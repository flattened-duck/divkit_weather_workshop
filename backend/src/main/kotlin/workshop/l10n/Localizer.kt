package workshop.l10n

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.readValue

class Localizer(lang: String) {

    private val strings: Map<String, String>

    init {
        val effectiveLang = LanguageSupport.normalize(lang)
        val resourcePath = "/strings/strings_$effectiveLang.json"
        val stream = Localizer::class.java.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("String resource not found: $resourcePath")
        strings = JsonMapper.builder().build().readValue(stream)
    }

    fun getOrDefault(key: String, default: String): String =
        strings.getOrDefault(key, default)
}
