package com.example.weatherdivkit.divkithost

import android.view.Window
import androidx.core.view.WindowCompat
import com.example.weatherdivkit.config.ThemeMode

object StatusBarTheming {
    /** Light icons in LIGHT theme, dark icons otherwise — exact port of applyStatusBarTheme. */
    fun apply(window: Window, effectiveTheme: String) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val light = effectiveTheme == ThemeMode.LIGHT
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
    }
}
