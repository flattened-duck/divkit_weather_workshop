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
        assertTrue(body.contains("Weather"), "English response must contain 'Weather'")
        assertFalse(body.contains("Погода"), "English response must not contain Russian 'Погода'")
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

    @Test
    fun `weather-json exposes current, hourly, daily, bg_key`() = testApplication {
        application { module() }
        val resp = client.get("/weather-json?lang=ru")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"current\""), "Must contain 'current' field")
        assertTrue(body.contains("\"hourly\""), "Must contain 'hourly' field")
        assertTrue(body.contains("\"daily\""), "Must contain 'daily' field")
        assertTrue(body.contains("\"bg_key\""), "Must contain 'bg_key' field")
        assertTrue(body.contains("cloudy_day"), "Mock bg_key must be 'cloudy_day'")
    }

    @Test
    fun `city-search returns a divpatch`() = testApplication {
        application { module() }
        val resp = client.get("/city-search?q=Mos&lang=en")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"changes\""), "Must contain 'changes' key")
        assertTrue(body.contains("city_search_results"), "Must target city_search_results")
        assertTrue(body.contains("weather-app://set_city?"), "Must contain set_city action URL")
        assertTrue(body.contains("Moscow"), "Must contain matching city name")
    }

    @Test
    fun `city-search empty query returns empty-state patch`() = testApplication {
        application { module() }
        val resp = client.get("/city-search?q=&lang=ru")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"changes\""), "Must contain 'changes' key")
        assertTrue(body.contains("city_search_results"), "Must target city_search_results")
    }

    @Test
    fun `document renders new weather main card`() = testApplication {
        application { module() }
        val resp = client.get("/document?lang=ru")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("sun_phase"), "Must contain the sun_phase custom element")
        assertTrue(body.contains("main_scroll"), "Must contain the main_scroll gallery id")
        assertTrue(body.contains("background_cloudy_day.png"), "Must contain the mock bg image")
        // Cross-worktree glue with the client (Worktree C): the scroll extension + custom_props keys.
        assertTrue(body.contains("scroll_state"), "Must carry the scroll_state extension for header collapse")
        assertTrue(body.contains("\"sunrise\""), "sun_phase custom_props must carry the sunrise key")
        assertTrue(body.contains("\"sunset\""), "sun_phase custom_props must carry the sunset key")
        // Read-global guard: header_collapsed is READ in an expression but NOT declared as a local
        // variable here (the client declares it globally). A local declaration would shadow the
        // global and silently break the header collapse — lock that decision in.
        assertTrue(body.contains("@{header_collapsed"), "header_collapsed must be read in the state expression")
        assertFalse(
            body.contains("\"name\":\"header_collapsed\""),
            "header_collapsed must NOT be declared as a local variable (client owns it globally)",
        )
    }

    @Test
    fun `settings screen wires the city-search input`() = testApplication {
        application { module() }
        val resp = client.get("/document?lang=ru")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        // Cross-worktree glue with the client (Worktree C): input variable, change trigger, action,
        // and the initially-empty DivPatch target container.
        assertTrue(body.contains("city_query"), "Settings input must bind the city_query variable")
        assertTrue(body.contains("\"on_variable\""), "Must fire the search on city_query change (on_variable trigger)")
        assertTrue(
            body.contains("weather-app://city_search?q=@{city_query}"),
            "Must carry the city_search action with the substituted query",
        )
        assertTrue(body.contains("city_search_results"), "Must contain the DivPatch target container id")
    }
}
