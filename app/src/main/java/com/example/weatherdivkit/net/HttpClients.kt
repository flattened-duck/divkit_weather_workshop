package com.example.weatherdivkit.net

import okhttp3.OkHttpClient
import java.net.Proxy

object HttpClients {
    // Emulator DHCP forces an HTTP proxy (10.0.2.2:8888) — bypass it, else backend
    // requests and Coil image loads from raw.githubusercontent.com break.
    val noProxy: OkHttpClient by lazy(LazyThreadSafetyMode.PUBLICATION) {
        OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
    }
}
