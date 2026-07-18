package com.example.weatherdivkit.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeResolverTest {

    @Test
    fun system_followsSystemDarkFlag() {
        assertEquals(
            ThemeMode.DARK,
            ThemeResolver.resolveEffective(ThemeMode.SYSTEM, systemDark = true)
        )
        assertEquals(
            ThemeMode.LIGHT,
            ThemeResolver.resolveEffective(ThemeMode.SYSTEM, systemDark = false)
        )
    }

    @Test
    fun explicitDark_ignoresSystemFlag() {
        assertEquals(
            ThemeMode.DARK,
            ThemeResolver.resolveEffective(ThemeMode.DARK, systemDark = false)
        )
    }

    @Test
    fun explicitLight_ignoresSystemFlag() {
        assertEquals(
            ThemeMode.LIGHT,
            ThemeResolver.resolveEffective(ThemeMode.LIGHT, systemDark = true)
        )
    }
}
