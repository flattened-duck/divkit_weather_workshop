package com.example.weatherdivkit.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenTest {

    @Test
    fun fromWireId_resolvesKnownIds() {
        assertEquals(Screen.MAIN, Screen.fromWireId("main"))
        assertEquals(Screen.SETTINGS, Screen.fromWireId("settings"))
        assertEquals(Screen.ABOUT, Screen.fromWireId("about"))
    }

    @Test
    fun fromWireId_returnsNullForUnknownOrEmpty() {
        assertNull(Screen.fromWireId("unknown"))
        assertNull(Screen.fromWireId(""))
    }

    @Test
    fun wireId_valuesAreExpected() {
        assertEquals("main", Screen.MAIN.wireId)
        assertEquals("settings", Screen.SETTINGS.wireId)
        assertEquals("about", Screen.ABOUT.wireId)
    }

    @Test
    fun fromWireId_roundTripsForEveryEntry() {
        Screen.entries.forEach { screen ->
            assertEquals(screen, Screen.fromWireId(screen.wireId))
        }
    }

    @Test
    fun fromWireId_equivalentToLowercasedName() {
        Screen.entries.forEach { screen ->
            assertEquals(screen, Screen.fromWireId(screen.name.lowercase()))
        }
    }
}
