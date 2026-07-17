package com.example.weatherdivkit.document

enum class Screen(val wireId: String) {
    MAIN("main"),
    SETTINGS("settings"),
    ABOUT("about"),
    ;

    companion object {
        fun fromWireId(id: String): Screen? = entries.firstOrNull { it.wireId == id }
    }
}
