package com.srrotas.app

object AppSignals {
    const val ACTION_CAPTURE_UPDATED = "com.srrotas.app.CAPTURE_UPDATED"

    const val UBER_PACKAGE = "com.ubercab.driver"
    const val NINETY_NINE_PACKAGE = "com.app99.driver"

    fun inferredPackage(platform: String): String =
        when (platform.lowercase()) {
            "uber" -> UBER_PACKAGE
            "99" -> NINETY_NINE_PACKAGE
            "indrive" -> "inferred:indrive-driver"
            "maxim" -> "inferred:maxim-driver"
            else -> "inferred:driver-app"
        }
}
