package workshop.renderer.data

import kotlin.math.abs
import workshop.l10n.Localizer
import workshop.proto.WeatherDataOuterClass.ConditionCode
import workshop.proto.WeatherDataOuterClass.DailyPoint
import workshop.proto.WeatherDataOuterClass.HourlyPoint
import workshop.proto.WeatherDataOuterClass.WeatherData
import workshop.weather.bgBase

/** Maps raw [WeatherData] + localized strings into a [WeatherMainViewModel] the renderer can walk
 *  without touching proto or [Localizer] itself. Moved verbatim from [workshop.renderer.WeatherMainRenderer]. */
class WeatherMainAdapter(private val localizer: Localizer) {

    private fun loc(key: String, fallback: String): String = localizer.getOrDefault(key, fallback)

    fun adapt(data: WeatherData): WeatherMainViewModel {
        val current = data.current
        val daily = data.dailyList
        val hourly = data.hourlyList
        val daily0 = daily[0]

        val weekMin = daily.minOf { it.tempMin }
        val weekMax = daily.maxOf { it.tempMax }
        val span = maxOf(1, weekMax - weekMin)

        val uvFraction = current.uvIndex.coerceIn(0, 11) / 11.0
        val pressureFraction = (current.pressure - PRESS_MIN).toDouble() / (PRESS_MAX - PRESS_MIN)

        val feelsDelta = current.feelsC - current.tempC
        val feelsSubtitle = when {
            abs(feelsDelta) <= 1 -> loc("feels.similar", "Similar to the actual temperature")
            feelsDelta > 1 -> loc("feels.warmer", "Feels warmer than it actually is")
            else -> loc("feels.cooler", "Feels cooler than it actually is")
        }
        val visSubtitle = when {
            current.visibility >= 20000 -> loc("vis.perfect", "Perfectly clear")
            current.visibility >= 10000 -> loc("vis.good", "Good visibility")
            else -> loc("vis.reduced", "Reduced visibility")
        }

        val conditionLabel = loc("condition.${current.condition.name}", current.condition.name)

        return WeatherMainViewModel(
            popupTitle = loc("popup.widget.title", "Add the weather widget to your home screen"),
            popupInstall = loc("popup.widget.install", "Install"),
            bgBaseName = bgBase(current.condition),
            city = current.city,
            currentTempLabel = "${current.tempC}°",
            conditionLabel = conditionLabel,
            todayMaxArrowLabel = "↑ ${daily0.tempMax}°",
            todayMinArrowLabel = "  ↓ ${daily0.tempMin}°",
            compactSummaryLabel = "${current.tempC}°  |  " + conditionLabel,
            hourly = hourly.map { h ->
                HourCellVm(
                    time = h.time,
                    emoji = conditionEmoji(h.condition),
                    tempLabel = "${h.tempC}°"
                )
            },
            daily = daily.map { d -> dailyRow(d, weekMin, span) },
            sunsetTitle = loc("weather.sunset", "Sunset"),
            sunrise = current.sunrise,
            sunset = current.sunset,
            sunsetSubtitle = loc("card.sunrise_at", "Sunrise at") + " " + current.sunrise,
            uvTitle = loc("weather.uv", "UV index"),
            uvIndexLabel = "${current.uvIndex}",
            uvBandLabel = uvBand(current.uvIndex),
            uvFraction = uvFraction,
            precipTitle = loc("weather.precipitation", "Precipitation"),
            todayPrecipLabel = "${daily0.precipProb}%",
            precipSubtitle = loc("precip.subtitle", "Chance today"),
            humidityTitle = loc("weather.humidity", "Humidity"),
            humidityLabel = "${current.humidity}%",
            windTitle = loc("weather.wind", "Wind"),
            windLabel = "${current.wind} " + loc("unit.wind", "km/h"),
            feelsTitle = loc("feels_like", "feels like"),
            feelsLabel = "${current.feelsC}°",
            feelsSubtitle = feelsSubtitle,
            visTitle = loc("weather.visibility", "Visibility"),
            visLabel = "${current.visibility / 1000} " + loc("unit.visibility", "km"),
            visSubtitle = visSubtitle,
            pressureTitle = loc("weather.pressure", "Pressure"),
            pressureLabel = "${current.pressure} " + loc("unit.pressure", "hPa"),
            pressureFraction = pressureFraction,
        )
    }

    private fun dailyRow(d: DailyPoint, weekMin: Int, span: Int): DayRowVm {
        var offsetPx = ((d.tempMin - weekMin) * 100) / span
        val fillPx = maxOf(6, ((d.tempMax - d.tempMin) * 100) / span)
        if (offsetPx + fillPx > 100) offsetPx = 100 - fillPx

        return DayRowVm(
            weekday = d.weekday,
            emoji = conditionEmoji(d.condition),
            minLabel = "${d.tempMin}°",
            maxLabel = "${d.tempMax}°",
            offsetPx = offsetPx,
            fillPx = fillPx,
            precipLabel = if (d.precipProb > 0) "💧${d.precipProb}%" else null,
        )
    }

    private fun uvBand(uv: Int): String = when {
        uv <= 2 -> loc("weather.uv.low", "Low")
        uv <= 5 -> loc("weather.uv.moderate", "Moderate")
        uv <= 7 -> loc("weather.uv.high", "High")
        uv <= 10 -> loc("weather.uv.very_high", "Very high")
        else -> loc("weather.uv.extreme", "Extreme")
    }

    private companion object {
        const val PRESS_MIN = 980
        const val PRESS_MAX = 1040

        fun conditionEmoji(condition: ConditionCode): String = when (condition) {
            ConditionCode.CLEAR -> "☀️"
            ConditionCode.CLOUDY -> "☁️"
            ConditionCode.RAIN -> "🌧️"
            ConditionCode.SNOW -> "❄️"
            ConditionCode.THUNDER -> "⛈️"
            ConditionCode.FOG -> "🌫️"
            else -> "🌡️" // ConditionCode.UNRECOGNIZED / future proto values
        }
    }
}
