package workshop.mock

import workshop.proto.WeatherDataOuterClass
import workshop.proto.WeatherDataOuterClass.ConditionCode
import workshop.proto.WeatherDataOuterClass.DayForecast
import workshop.proto.WeatherDataOuterClass.WeatherData

object MockWeatherDataProvider {

    fun provide(): WeatherData = WeatherData.newBuilder()
        .setToday(
            DayForecast.newBuilder()
                .setTempC(17)
                .setTempFeels(14)
                .setCondition(ConditionCode.CLOUDY)
                .setCity("Москва")
                .build(),
        )
        .setTomorrow(
            DayForecast.newBuilder()
                .setTempC(20)
                .setTempFeels(18)
                .setCondition(ConditionCode.CLEAR)
                .setCity("Москва")
                .build(),
        )
        .build()
}
