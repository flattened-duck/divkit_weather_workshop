package com.example.weatherdivkit.config

object ThemeResolver {
    /** system → DARK if systemDark else LIGHT; explicit dark/light returned as-is. */
    fun resolveEffective(mode: String, systemDark: Boolean): String =
        if (mode == ThemeMode.SYSTEM) (if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT) else mode
}
