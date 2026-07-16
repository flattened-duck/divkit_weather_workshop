package workshop

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Byte-identical shipped-JSON guard for the Stage 3 adapter-extraction refactor. First run
 * against unrefactored code writes the 7 golden fixtures (generation mode); every run after that
 * asserts equality. A red snapshot here means the refactor changed shipped JSON — fix the
 * refactor step, never the golden file.
 */
class GoldenSnapshotTest {

    private fun check(name: String, actual: String) {
        val f = File("src/test/resources/golden/$name")
        if (!f.exists()) {
            f.parentFile.mkdirs()
            f.writeText(actual)
            return
        }
        assertEquals(f.readText(), actual, "Golden snapshot drift for $name — refactor changed shipped JSON")
    }

    @Test
    fun `document ru`() = testApplication {
        application { module() }
        check("document_ru.json", client.get("/document?lang=ru").bodyAsText())
    }

    @Test
    fun `document en`() = testApplication {
        application { module() }
        check("document_en.json", client.get("/document?lang=en").bodyAsText())
    }

    @Test
    fun `zero ru`() = testApplication {
        application { module() }
        check("zero_ru.json", client.get("/zero?lang=ru").bodyAsText())
    }

    @Test
    fun `zero en`() = testApplication {
        application { module() }
        check("zero_en.json", client.get("/zero?lang=en").bodyAsText())
    }

    @Test
    fun `city search mos en`() = testApplication {
        application { module() }
        check("city_search_mos_en.json", client.get("/city-search?q=Mos&lang=en").bodyAsText())
    }

    @Test
    fun `city search mos ru`() = testApplication {
        application { module() }
        check("city_search_mos_ru.json", client.get("/city-search?q=Mos&lang=ru").bodyAsText())
    }

    @Test
    fun `city search empty ru`() = testApplication {
        application { module() }
        check("city_search_empty_ru.json", client.get("/city-search?q=&lang=ru").bodyAsText())
    }
}
