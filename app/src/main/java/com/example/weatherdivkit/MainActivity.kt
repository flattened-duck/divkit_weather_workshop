package com.example.weatherdivkit

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.weatherdivkit.databinding.ActivityMainBinding
import com.example.weatherdivkit.divkit.DocumentLoader
import com.example.weatherdivkit.divkit.ScrollStateExtensionHandler
import com.example.weatherdivkit.divkit.SunPhaseCustomViewAdapter
import com.yandex.div.DivDataTag
import com.yandex.div.coil.CoilDivImageLoader
import com.yandex.div.core.Div2Context
import com.yandex.div.core.DivConfiguration
import com.yandex.div.core.expression.variables.DivVariableController
import com.yandex.div.core.view2.Div2View
import com.yandex.div.data.Variable
import com.yandex.div2.DivData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.Proxy
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var divConfiguration: DivConfiguration
    private lateinit var variableController: DivVariableController
    private lateinit var themeModeVar: Variable.StringVariable
    private lateinit var themeVar: Variable.StringVariable
    private lateinit var compactVar: Variable.BooleanVariable
    private lateinit var headerStateVar: Variable.StringVariable
    private lateinit var statusInsetVar: Variable.IntegerVariable
    private lateinit var navInsetVar: Variable.IntegerVariable

    /** Currently rendered screens map. Replaced atomically on refetch. */
    private var screens: Map<Screen, DivData> = emptyMap()

    /** The screen currently on top (survives language refetch). */
    private var currentScreen: Screen = Screen.MAIN

    /** Back stack: each entry is a screen we can go back to. */
    private val backStack = mutableListOf<Screen>()

    // The Android emulator's DHCP configures an HTTP proxy (Wi-Fi AP config, 10.0.2.2:8888).
    // Coil's network fetcher builds its own OkHttpClient by default and picks that proxy up,
    // breaking image loads from raw.githubusercontent.com. Bypass it the same way
    // DocumentLoader already does for the backend.
    private val imageHttpClient by lazy(LazyThreadSafetyMode.PUBLICATION) {
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeMode = prefs.getString(PREF_THEME_MODE, DEFAULT_THEME_MODE) ?: DEFAULT_THEME_MODE
        val compact = prefs.getBoolean(PREF_COMPACT, false)
        val effective = resolveEffectiveTheme(themeMode)

        themeModeVar = Variable.StringVariable("theme_mode", themeMode)
        themeVar = Variable.StringVariable("theme", effective)
        compactVar = Variable.BooleanVariable("compact", compact)
        headerStateVar = Variable.StringVariable("header_state", "full")
        statusInsetVar = Variable.IntegerVariable("status_inset", 0L)
        navInsetVar = Variable.IntegerVariable("nav_inset", 0L)
        variableController = DivVariableController()
        variableController.declare(
            themeModeVar, themeVar, compactVar, headerStateVar, statusInsetVar, navInsetVar,
        )

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val d = resources.displayMetrics.density
            statusInsetVar.set((bars.top / d).roundToInt().toLong())
            navInsetVar.set((bars.bottom / d).roundToInt().toLong())
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        divConfiguration = DivConfiguration.Builder(CoilDivImageLoader(this, imageHttpClient))
            .actionHandler(WeatherDivActionHandler(
                ::showScreen, ::goBack, ::onSetLang, ::onSetTheme, ::onSetCompact,
                ::onCitySearch, ::onSetCity))
            .divVariableController(variableController)
            .divCustomContainerViewAdapter(SunPhaseCustomViewAdapter())
            .extension(ScrollStateExtensionHandler(variableController))
            .visualErrorsEnabled(true)
            .build()

        applyStatusBarTheme(effective)

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
    // Status bar theming — icon contrast follows the effective theme.
    // -------------------------------------------------------------------------

    private fun applyStatusBarTheme(effectiveTheme: String) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val light = effectiveTheme == "light"
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
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
    // City search / selection
    // (weather-app://city_search?q= and weather-app://set_city?lat=&lon=&name=)
    // -------------------------------------------------------------------------

    /** Off-main fetch of the `/city-search` DivPatch, applied on the main thread to the firing view. */
    private fun onCitySearch(query: String, view: Div2View) {
        val lang = readLangPref()
        lifecycleScope.launch(Dispatchers.IO) {
            val patch = DocumentLoader(this@MainActivity).loadCitySearch(query, lang)
            withContext(Dispatchers.Main) {
                if (patch != null) view.applyPatch(patch)
                else Log.w(TAG, "city_search patch null (q='$query')")
            }
        }
    }

    /** Persist the selected city, then refetch the document like [onSetLang]. */
    private fun onSetCity(lat: String, lon: String, name: String) {
        saveCity(lat, lon, name)
        val lang = readLangPref()
        lifecycleScope.launch(Dispatchers.IO) {
            val rawScreens = loadDocument(lang)
            withContext(Dispatchers.Main) {
                screens = buildScreensMap(rawScreens)
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
        applyStatusBarTheme(resolveEffectiveTheme(mode))
    }

    private fun onSetCompact(value: Boolean) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(PREF_COMPACT, value).apply()
        compactVar.set(value)
        if (value) headerStateVar.set("collapsed")
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
            applyStatusBarTheme(if (dark) "dark" else "light")
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
        val (lat, lon, name) = readCity()
        val loader = DocumentLoader(this)
        val fromNetwork = loader.loadFromNetwork(lang, lat, lon, name)
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

    // -------------------------------------------------------------------------
    // SharedPreferences — city persistence
    // -------------------------------------------------------------------------

    private fun readCity(): Triple<String?, String?, String?> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Triple(
            prefs.getString(PREF_LAT, null),
            prefs.getString(PREF_LON, null),
            prefs.getString(PREF_CITY_NAME, null),
        )
    }

    private fun saveCity(lat: String, lon: String, name: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_LAT, lat)
            .putString(PREF_LON, lon)
            .putString(PREF_CITY_NAME, name)
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
        const val PREF_LAT = "pref_lat"
        const val PREF_LON = "pref_lon"
        const val PREF_CITY_NAME = "pref_city_name"
    }
}
