package com.example.weatherdivkit

import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.example.weatherdivkit.config.ThemeMode
import com.example.weatherdivkit.document.Screen
import com.yandex.div.core.DivActionHandler
import com.yandex.div.core.DivViewFacade
import com.yandex.div.core.view2.Div2View
import com.yandex.div.json.expressions.ExpressionResolver
import com.yandex.div2.DivAction

class WeatherDivActionHandler(
    private val onNavigate: (Screen) -> Unit,
    private val onBack: () -> Unit,
    private val onSetLang: (String) -> Unit,
    private val onSetTheme: (String) -> Unit,
    private val onSetCompact: (Boolean) -> Unit,
    private val onCitySearch: (String, Div2View) -> Unit,
    private val onSetCity: (lat: String, lon: String, name: String) -> Unit,
) : DivActionHandler() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun handleAction(
        action: DivAction,
        view: DivViewFacade,
        resolver: ExpressionResolver,
    ): Boolean {
        val url = action.url?.evaluate(resolver)
            ?: return super.handleAction(action, view, resolver)

        return if (url.scheme == SCHEME) {
            handleWeatherAction(url, view)
        } else {
            super.handleAction(action, view, resolver)
        }
    }

    private fun handleWeatherAction(url: Uri, view: DivViewFacade): Boolean {
        return when (url.host) {
            "navigate" -> {
                val screenParam = url.getQueryParameter("screen")
                    ?: return false
                val screen = Screen.fromWireId(screenParam.lowercase()) ?: return false

                mainHandler.post { onNavigate(screen) }
                true
            }
            "back" -> {
                mainHandler.post { onBack() }
                true
            }
            "set_lang" -> {
                val lang = url.getQueryParameter("value") ?: return false
                mainHandler.post { onSetLang(lang) }
                true
            }
            "set_theme" -> {
                val mode = url.getQueryParameter("mode")
                if (mode == null || mode !in setOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT)) return false
                mainHandler.post { onSetTheme(mode) }
                true
            }
            "set_compact" -> {
                val raw = url.getQueryParameter("value")
                val value = raw?.toBooleanStrictOrNull() ?: return false
                mainHandler.post { onSetCompact(value) }
                true
            }
            "city_search" -> {
                val q = url.getQueryParameter("q") ?: ""
                val div2View = view as? Div2View ?: return false
                mainHandler.post { onCitySearch(q, div2View) }
                true
            }
            "set_city" -> {
                val lat = url.getQueryParameter("lat") ?: return false
                val lon = url.getQueryParameter("lon") ?: return false
                val name = url.getQueryParameter("name") ?: ""
                mainHandler.post { onSetCity(lat, lon, name) }
                true
            }
            else -> false
        }
    }

    companion object {
        private const val SCHEME = "weather-app"
    }
}
