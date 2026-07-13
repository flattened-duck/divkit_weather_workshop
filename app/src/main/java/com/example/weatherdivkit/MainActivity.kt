package com.example.weatherdivkit

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.weatherdivkit.databinding.ActivityMainBinding
import com.example.weatherdivkit.divkit.DocumentLoader
import com.yandex.div.DivDataTag
import com.yandex.div.core.Div2Context
import com.yandex.div.core.DivConfiguration
import com.yandex.div.core.expression.variables.DivVariableController
import com.yandex.div.core.view2.Div2View
import com.yandex.div.data.Variable
import com.yandex.div.picasso.PicassoDivImageLoader
import com.yandex.div2.DivData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var divConfiguration: DivConfiguration
    private lateinit var variableController: DivVariableController
    private lateinit var themeModeVar: Variable.StringVariable
    private lateinit var themeVar: Variable.StringVariable
    private lateinit var compactVar: Variable.BooleanVariable

    /** Currently rendered screens map. Replaced atomically on refetch. */
    private var screens: Map<Screen, DivData> = emptyMap()

    /** The screen currently on top (survives language refetch). */
    private var currentScreen: Screen = Screen.MAIN

    /** Back stack: each entry is a screen we can go back to. */
    private val backStack = mutableListOf<Screen>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeMode = prefs.getString(PREF_THEME_MODE, DEFAULT_THEME_MODE) ?: DEFAULT_THEME_MODE
        val compact = prefs.getBoolean(PREF_COMPACT, false)
        val effective = resolveEffectiveTheme(themeMode)

        themeModeVar = Variable.StringVariable("theme_mode", themeMode)
        themeVar = Variable.StringVariable("theme", effective)
        compactVar = Variable.BooleanVariable("compact", compact)
        variableController = DivVariableController()
        variableController.declare(themeModeVar, themeVar, compactVar)

        divConfiguration = DivConfiguration.Builder(PicassoDivImageLoader(this))
            .actionHandler(WeatherDivActionHandler(::showScreen, ::goBack, ::onSetLang, ::onSetTheme, ::onSetCompact))
            .divVariableController(variableController)
            .visualErrorsEnabled(true)
            .build()

        // Kick off async document loading: network-first, assets as offline fallback.
        val lang = readLangPref()
        lifecycleScope.launch(Dispatchers.IO) {
            val rawScreens = loadDocument(lang)
            withContext(Dispatchers.Main) {
                screens = buildScreensMap(rawScreens)
                showScreen(Screen.MAIN)
            }
        }

        // Modern back handling — works with predictive back (targetSdk 33+/36).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
    }

    // -------------------------------------------------------------------------
    // Language refetch (triggered by weather-app://set_lang?value=ru|en)
    // -------------------------------------------------------------------------

    private fun onSetLang(lang: String) {
        Log.i(TAG, "Language changed to '$lang', refetching document…")
        saveLangPref(lang)
        lifecycleScope.launch(Dispatchers.IO) {
            val rawScreens = loadDocument(lang)
            withContext(Dispatchers.Main) {
                screens = buildScreensMap(rawScreens)
                // Re-render the current screen in the new language.
                renderScreen(currentScreen)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Theme / compact — reactive div variables (triggered by weather-app://set_theme, set_compact)
    // -------------------------------------------------------------------------

    private fun onSetTheme(mode: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(PREF_THEME_MODE, mode).apply()
        themeModeVar.set(mode)
        themeVar.set(resolveEffectiveTheme(mode))
    }

    private fun onSetCompact(value: Boolean) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(PREF_COMPACT, value).apply()
        compactVar.set(value)
    }

    /** system → read OS night mode; otherwise the explicit user choice. */
    private fun resolveEffectiveTheme(mode: String): String =
        if (mode == "system") (if (isSystemDark()) "dark" else "light") else mode

    private fun isSystemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if ((themeModeVar.getValue() as String) == "system") {
            val dark = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            themeVar.set(if (dark) "dark" else "light")
        }
    }

    // -------------------------------------------------------------------------
    // Document loading helpers
    // -------------------------------------------------------------------------

    /**
     * Tries network first; falls back to the bundled asset on failure.
     * Must be called off the main thread.
     */
    private fun loadDocument(lang: String): Map<String, DivData> {
        val loader = DocumentLoader(this)
        val fromNetwork = loader.loadFromNetwork(lang)
        if (fromNetwork != null) {
            Log.i(TAG, "Using network document (lang=$lang)")
            return fromNetwork
        }
        Log.i(TAG, "Network unavailable — loading bundled asset (lang=$lang)")
        return try {
            loader.loadFromAssets()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load document from assets", e)
            emptyMap()
        }
    }

    private fun buildScreensMap(rawScreens: Map<String, DivData>): Map<Screen, DivData> =
        buildMap {
            rawScreens["main"]?.let { put(Screen.MAIN, it) }
            rawScreens["settings"]?.let { put(Screen.SETTINGS, it) }
            rawScreens["about"]?.let { put(Screen.ABOUT, it) }
        }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    private fun showScreen(screen: Screen) {
        val current = backStack.lastOrNull()
        if (current != null && current != screen) {
            backStack.add(screen)
        } else if (backStack.isEmpty()) {
            backStack.add(screen)
        }
        renderScreen(screen)
    }

    private fun renderScreen(screen: Screen) {
        val data = screens[screen] ?: run {
            Log.e(TAG, "No DivData for screen: $screen")
            return
        }

        currentScreen = screen

        val divContext = Div2Context(
            baseContext = this,
            configuration = divConfiguration,
            lifecycleOwner = this,
        )

        val divView = Div2View(divContext).apply {
            setData(data, DivDataTag(screen.name.lowercase()))
        }

        binding.divContainer.removeAllViews()
        binding.divContainer.addView(divView)
    }

    private fun goBack() {
        if (backStack.size <= 1) {
            finish()
            return
        }
        backStack.removeAt(backStack.lastIndex)
        val previous = backStack.lastOrNull() ?: run { finish(); return }
        renderScreen(previous)
    }

    // -------------------------------------------------------------------------
    // SharedPreferences — language persistence
    // -------------------------------------------------------------------------

    private fun readLangPref(): String =
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_LANG, DEFAULT_LANG) ?: DEFAULT_LANG

    private fun saveLangPref(lang: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_LANG, lang)
            .apply()
    }

    private companion object {
        const val TAG = "MainActivity"
        const val PREFS_NAME = "weather_prefs"
        const val PREF_LANG = "pref_lang"
        const val DEFAULT_LANG = "ru"
        const val PREF_THEME_MODE = "pref_theme_mode"
        const val PREF_COMPACT = "pref_compact"
        const val DEFAULT_THEME_MODE = "system"
    }
}
