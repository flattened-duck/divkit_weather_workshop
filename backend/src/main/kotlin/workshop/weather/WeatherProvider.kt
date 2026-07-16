package workshop.weather

import workshop.l10n.Localizer
import workshop.proto.WeatherDataOuterClass.WeatherData
import workshop.weather.data.CityParam

interface WeatherProvider {
    suspend fun provide(city: CityParam, localizer: Localizer): WeatherData

    companion object {
        fun create(): WeatherProvider =
            if (System.getProperty("weather.source") == "mock") MockWeatherProvider
            else OpenMeteoWeatherProvider(OpenMeteoClient())
    }
}
