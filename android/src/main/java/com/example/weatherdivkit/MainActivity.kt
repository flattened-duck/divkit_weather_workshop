package com.example.weatherdivkit

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.weatherdivkit.config.AppPreferences
import com.example.weatherdivkit.config.SharedPreferencesStore
import com.example.weatherdivkit.config.ThemeMode
import com.example.weatherdivkit.config.ThemeResolver
import com.example.weatherdivkit.databinding.ActivityMainBinding
import com.example.weatherdivkit.divkit.ScrollStateExtensionHandler
import com.example.weatherdivkit.divkit.SunPhaseCustomViewAdapter
import com.example.weatherdivkit.divkithost.GlobalVariables
import com.example.weatherdivkit.divkithost.StatusBarTheming
import com.example.weatherdivkit.divkithost.WeatherDivActionHandler
import com.example.weatherdivkit.document.DocumentLoader
import com.example.weatherdivkit.document.DocumentRepository
import com.example.weatherdivkit.document.DocumentSource
import com.example.weatherdivkit.document.Screen
import com.example.weatherdivkit.navigation.ScreenNavigator
import com.example.weatherdivkit.net.HttpClients
import com.yandex.div.DivDataTag
import com.yandex.div.coil.CoilDivImageLoader
import com.yandex.div.core.Div2Context
import com.yandex.div.core.DivConfiguration
import com.yandex.div.core.view2.Div2View
import com.yandex.div.shimmer.DivShimmerExtensionHandler
import com.yandex.div2.DivData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var divConfiguration: DivConfiguration
    private lateinit var globals: GlobalVariables
    private lateinit var navigator: ScreenNavigator

    /** Currently rendered screens map. Replaced atomically on refetch. */
    private var screens: Map<Screen, DivData> = emptyMap()

    private lateinit var documentSource: DocumentSource
    private lateinit var repository: DocumentRepository
    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding.swipeRefresh.setOnRefreshListener { onPullToRefresh() }
        // The direct child (divContainer) never scrolls itself, so the default
        // canChildScrollUp() would let PTR fire mid-list. Delegate to the DivKit
        // gallery ("main_scroll") that actually owns the scroll position.
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ -> mainScrollCanScrollUp() }

        prefs = AppPreferences(SharedPreferencesStore(this))
        val themeMode = prefs.themeMode
        val compact = prefs.compact
        val effective = ThemeResolver.resolveEffective(themeMode, isSystemDark())

        globals = GlobalVariables(themeMode = themeMode, effectiveTheme = effective, compact = compact)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val d = resources.displayMetrics.density
            globals.setInsets((bars.top / d).roundToInt().toLong(), (bars.bottom / d).roundToInt().toLong())
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        navigator = ScreenNavigator(availableScreens = { screens.keys }, render = ::renderDiv, onExit = ::finish)

        divConfiguration = DivConfiguration.Builder(CoilDivImageLoader(this, HttpClients.noProxy))
            .actionHandler(WeatherDivActionHandler(
                navigator::showScreen, navigator::goBack, ::onSetLang, ::onSetTheme, ::onSetCompact,
                ::onCitySearch, ::onSetCity))
            .divVariableController(globals.controller)
            .divCustomContainerViewAdapter(SunPhaseCustomViewAdapter())
            .extension(ScrollStateExtensionHandler(globals.controller))
            .extension(DivShimmerExtensionHandler())
            .visualErrorsEnabled(true)
            .build()

        StatusBarTheming.apply(window, effective)

        // Two-phase cold start: phase 1 renders instantly from local data (cache, else the
        // bundled zero skeleton) so there's never a blank screen; phase 2 swaps in fresh network
        // data in the background once it arrives (or leaves phase 1's layout on screen if it
        // doesn't — see onSetLang/onSetCity/onPullToRefresh for the same keep-current contract).
        documentSource = DocumentLoader(this)
        repository = DocumentRepository(documentSource)

        val lang = prefs.lang
        val (lat, lon, name) = prefs.readCity()
        lifecycleScope.launch(Dispatchers.IO) {
            val initial = repository.coldStartLocal(lang)
            withContext(Dispatchers.Main) {
                screens = initial
                navigator.showScreen(Screen.MAIN)
            }

            val fresh = repository.fetch(lang, lat, lon, name)
            if (fresh != null) {
                withContext(Dispatchers.Main) {
                    screens = fresh
                    navigator.renderCurrent()
                }
            }
        }

        // Modern back handling — works with predictive back (targetSdk 33+/36).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigator.goBack()
        })
    }

    // -------------------------------------------------------------------------
    // Language refetch (triggered by weather-app://set_lang?value=ru|en)
    // -------------------------------------------------------------------------

    private fun onSetLang(lang: String) {
        Log.i(TAG, "Language changed to '$lang', refetching document…")
        prefs.lang = lang
        val (lat, lon, name) = prefs.readCity()
        lifecycleScope.launch(Dispatchers.IO) {
            // Network-only, never wipes the layout: on failure try the same-language cache
            // (may reflect a stale city, see contract notes); if that also misses, keep
            // whatever is currently on screen rather than falling back to the zero asset.
            val rawScreens = repository.fetchOrCache(lang, lat, lon, name)
            withContext(Dispatchers.Main) {
                if (rawScreens != null) {
                    screens = rawScreens
                    navigator.renderCurrent()
                } else {
                    Log.i(TAG, "Offline and no cache for lang=$lang, keeping current layout")
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pull-to-refresh (main screen only — see renderDiv's isEnabled toggle)
    // -------------------------------------------------------------------------

    /**
     * DivKit tags each div's Android view with its `id` (DivBaseBinder.applyId), so the
     * main vertical gallery ("main_scroll") can be located and queried for scroll position.
     */
    private fun mainScrollCanScrollUp(): Boolean =
        binding.divContainer.findViewWithTag<View>("main_scroll")?.canScrollVertically(-1) ?: false

    private fun onPullToRefresh() {
        Log.i(TAG, "Pull-to-refresh: refetching…")
        val lang = prefs.lang
        val (lat, lon, name) = prefs.readCity()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Network-only, keep-current on failure — never blank the screen mid-refresh.
                val fresh = repository.fetch(lang, lat, lon, name)
                if (fresh != null) {
                    withContext(Dispatchers.Main) {
                        screens = fresh
                        navigator.renderCurrent()
                    }
                } else {
                    Log.i(TAG, "Pull-to-refresh offline, keeping current layout")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // City search / selection
    // (weather-app://city_search?q= and weather-app://set_city?lat=&lon=&name=)
    // -------------------------------------------------------------------------

    /** Off-main fetch of the `/city-search` DivPatch, applied on the main thread to the firing view. */
    private fun onCitySearch(query: String, view: Div2View) {
        val lang = prefs.lang
        lifecycleScope.launch(Dispatchers.IO) {
            val patch = repository.citySearchPatch(query, lang)
            withContext(Dispatchers.Main) {
                if (patch != null) view.applyPatch(patch)
                else Log.w(TAG, "city_search patch null (q='$query')")
            }
        }
    }

    /**
     * Persist the selected city, then refetch — network only, keep-current on failure. Never
     * reads the cache here: the cache is lang-keyed, not city-keyed, so it would show the
     * PREVIOUS city under a "successful" cache hit. Never falls back to the bundled asset either.
     */
    private fun onSetCity(lat: String, lon: String, name: String) {
        prefs.saveCity(lat, lon, name)
        val lang = prefs.lang
        lifecycleScope.launch(Dispatchers.IO) {
            val fresh = repository.fetch(lang, lat, lon, name)
            if (fresh != null) {
                withContext(Dispatchers.Main) {
                    screens = fresh
                    navigator.renderCurrent()
                }
            } else {
                Log.i(TAG, "Offline, keeping current layout (city change not applied)")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Theme / compact — reactive div variables (triggered by weather-app://set_theme, set_compact)
    // -------------------------------------------------------------------------

    private fun onSetTheme(mode: String) {
        prefs.themeMode = mode
        globals.setThemeMode(mode)
        val effective = ThemeResolver.resolveEffective(mode, isSystemDark())
        globals.setTheme(effective)
        StatusBarTheming.apply(window, effective)
    }

    private fun onSetCompact(value: Boolean) {
        prefs.compact = value
        globals.setCompact(value)
    }

    private fun isSystemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (globals.currentThemeMode() == ThemeMode.SYSTEM) {
            val dark = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            globals.setTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
            StatusBarTheming.apply(window, if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        }
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    private fun renderDiv(screen: Screen) {
        val data = screens[screen] ?: run {
            Log.e(TAG, "No DivData for screen: $screen")
            return
        }

        binding.swipeRefresh.isEnabled = (screen == Screen.MAIN)

        val divContext = Div2Context(
            baseContext = this,
            configuration = divConfiguration,
            lifecycleOwner = this,
        )

        val divView = Div2View(divContext).apply {
            setData(data, DivDataTag(screen.wireId))
        }

        binding.divContainer.removeAllViews()
        binding.divContainer.addView(divView)
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
