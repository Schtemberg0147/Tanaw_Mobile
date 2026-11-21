package com.tanaw.app.feature.workspace.tracking

internal data class FixCandidate<T>(
    val item: T,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timeMs: Long,
    val isMock: Boolean = false,
)

internal object TrackingSafety {
    private const val LOCATION_DISABLED_ERROR =
        "Location services are disabled or unavailable. Enable location, then Resume location sharing."

    fun provider(enabled: (String) -> Boolean): String = listOf("gps", "network").firstOrNull {
        runCatching { enabled(it) }.getOrDefault(defaultValue = false)
    } ?: error(LOCATION_DISABLED_ERROR)

    fun providers(enabled: (String) -> Boolean): List<String> = listOf("fused", "gps", "network").filter {
        runCatching { enabled(it) }.getOrDefault(defaultValue = false)
    }.ifEmpty { error(LOCATION_DISABLED_ERROR) }

    fun valid(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        age: Long,
        isMock: Boolean = false,
        allowMock: Boolean = false,
    ): Boolean =
        (!isMock || allowMock) &&
            latitude.isFinite() && longitude.isFinite() && (latitude in -90.0..90.0) && (longitude in -180.0..180.0) &&
            accuracy.isFinite() && (accuracy in 0f..100f) && (age in 0..60000)

    fun <T> chooseBestFix(
        candidates: Iterable<FixCandidate<T>>,
        nowMs: Long,
        maxAgeMs: Long = 60_000L,
        allowMock: Boolean = false,
    ): T? =
        candidates
            .filter { (_, latitude, longitude, accuracy, timeMs, isMock) ->
                val age = nowMs - timeMs
                age in 0..maxAgeMs && valid(latitude, longitude, accuracy, age, isMock = isMock, allowMock = allowMock)
            }
            .minWithOrNull(compareBy<FixCandidate<T>> { it.accuracy }.thenBy { nowMs - it.timeMs })
            ?.item
}
