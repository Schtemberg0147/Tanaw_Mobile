package com.tanaw.app.feature.workspace

internal object TrackingSafety {
    fun provider(enabled:(String)->Boolean):String = listOf("gps","network").firstOrNull {
        runCatching {enabled(it)}.getOrDefault(false)
    } ?: error("Location services are disabled or unavailable. Enable location, then Resume location sharing.")

    fun valid(latitude:Double,longitude:Double,accuracy:Float,age:Long):Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            accuracy.isFinite() && accuracy in 0f..100f && age in 0..60000
}
