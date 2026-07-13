package com.example.weatherdivkit

import android.app.Application
import com.yandex.div.core.DivKit
import com.yandex.div.core.DivKitConfiguration

class WeatherApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        DivKit.configure(DivKitConfiguration.Builder().build())
    }
}
