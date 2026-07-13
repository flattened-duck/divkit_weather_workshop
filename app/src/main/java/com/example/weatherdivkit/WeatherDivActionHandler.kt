package com.example.weatherdivkit

import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.yandex.div.core.DivActionHandler
import com.yandex.div.core.DivViewFacade
import com.yandex.div.json.expressions.ExpressionResolver
import com.yandex.div2.DivAction

class WeatherDivActionHandler(
    private val onNavigate: (Screen) -> Unit,
    private val onBack: () -> Unit,
    private val onSetLang: (String) -> Unit,
    private val onSetTheme: (String) -> Unit,
    private val onSetCompact: (Boolean) -> Unit,
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
            handleWeatherAction(url)
        } else {
            super.handleAction(action, view, resolver)
        }
    }

    private fun handleWeatherAction(url: Uri): Boolean {
        return when (url.host) {
            "navigate" -> {
                val screenParam = url.getQueryParameter("screen")
                    ?: return false
                val screen = runCatching {
                    Screen.valueOf(screenParam.uppercase())
                }.getOrNull() ?: return false

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
                if (mode == null || mode !in setOf("system", "dark", "light")) return false
                mainHandler.post { onSetTheme(mode) }
                true
            }
            "set_compact" -> {
                val raw = url.getQueryParameter("value")
                val value = raw?.toBooleanStrictOrNull() ?: return false
                mainHandler.post { onSetCompact(value) }
                true
            }
            else -> false
        }
    }

    companion object {
        private const val SCHEME = "weather-app"
    }
}
