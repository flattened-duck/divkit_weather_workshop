package workshop

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplicationSmokeTest {

    @Test
    fun `document returns templates and all three screens`() = testApplication {
        application { module() }
        val resp = client.get("/document?lang=ru")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"templates\""), "Must contain 'templates' key")
        assertTrue(body.contains("\"main\""), "Must contain 'main' screen")
        assertTrue(body.contains("\"settings\""), "Must contain 'settings' screen")
        assertTrue(body.contains("\"about\""), "Must contain 'about' screen")
        assertTrue(body.contains("weather-app://set_theme?mode="), "Must contain reactive set_theme action")
    }

    @Test
    fun `lang=en returns english strings`() = testApplication {
        application { module() }
        val resp = client.get("/document?lang=en")
        val body = resp.bodyAsText()
        assertTrue(body.contains("Today"), "English response must contain 'Today'")
        assertFalse(body.contains("Сегодня"), "English response must not contain Russian 'Сегодня'")
    }

    @Test
    fun `navigate urls use correct format`() = testApplication {
        application { module() }
        val resp = client.get("/document?lang=ru")
        val body = resp.bodyAsText()
        assertTrue(
            body.contains("weather-app://navigate?screen=settings"),
            "Must contain navigate URL for settings",
        )
        assertTrue(
            body.contains("weather-app://navigate?screen=about"),
            "Must contain navigate URL for about",
        )
        assertTrue(
            body.contains("weather-app://navigate?screen=main"),
            "Must contain navigate URL for main",
        )
    }

    @Test
    fun `reactive theme variable expression is present`() = testApplication {
        application { module() }
        val resp = client.get("/document?lang=ru")
        val body = resp.bodyAsText()
        assertTrue(body.contains("@{theme == 'dark'"), "Must contain reactive theme variable expression")
        assertFalse(body.contains("getStoredStringValue"), "Must NOT contain non-reactive getStoredStringValue")
    }

    @Test
    fun `ping returns pong`() = testApplication {
        application { module() }
        val resp = client.get("/ping")
        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals("pong", resp.bodyAsText())
    }

    @Test
    fun `main screen contains stored-values widget popup`() = testApplication {
        application { module() }
        val body = client.get("/document?lang=ru").bodyAsText()
        assertTrue(body.contains("\"overlap\""), "overlap container must be present")
        assertTrue(body.contains("\"set_stored_value\""), "set_stored_value action must be present")
        assertTrue(body.contains("widget_set_up"), "widget_set_up stored value must be present")
        assertTrue(body.contains("widget_popup_delayed"), "widget_popup_delayed stored value must be present")
        assertTrue(body.contains("popup_dismissed"), "popup_dismissed variable must be present")
        assertTrue(body.contains("getStoredBooleanValue('widget_set_up'"), "read expression must be present")
        assertTrue(body.contains("getStoredBooleanValue('widget_popup_delayed'"), "delayed-flag read expression must be present")
        // getTimestamp/nowLocal arithmetic doesn't exist as a builtin in open DivKit 32.6.0;
        // the 3-day delay is a native set_stored_value TTL (seconds) instead.
        assertTrue(body.contains("\"lifetime\":259200"), "3-day (seconds) TTL on the delay action must be present")
        assertFalse(body.contains("getTimestamp"), "getTimestamp is not a real DivKit 32.6.0 builtin, must not be used")
    }
}
