package com.autoflow.core.engine.trigger

import com.autoflow.core.model.SunEventType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise / sunset times using the NOAA general solar position equations (accurate to about a
 * minute away from the poles). Works fully offline.
 */
object SolarCalculator {
    /** Official sunrise/sunset zenith including refraction and the sun's radius. */
    private const val ZENITH_DEGREES = 90.833

    /**
     * Time of [type] on the UTC calendar day [date] at the given coordinates, or null when the
     * sun does not rise or set that day (polar day / polar night).
     */
    fun eventTime(date: LocalDate, latitude: Double, longitude: Double, type: SunEventType): Instant? {
        val gamma = 2 * Math.PI / date.lengthOfYear() * (date.dayOfYear - 1)
        val equationOfTime = 229.18 * (
            0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma)
            )
        val declination = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val latRad = Math.toRadians(latitude)
        val cosHourAngle = cos(Math.toRadians(ZENITH_DEGREES)) / (cos(latRad) * cos(declination)) -
            tan(latRad) * tan(declination)
        if (cosHourAngle < -1.0 || cosHourAngle > 1.0) return null
        val hourAngle = Math.toDegrees(acos(cosHourAngle))
        val minutesUtc = when (type) {
            SunEventType.SUNRISE -> 720 - 4 * (longitude + hourAngle) - equationOfTime
            SunEventType.SUNSET -> 720 - 4 * (longitude - hourAngle) - equationOfTime
        }
        val midnight = date.atStartOfDay().toInstant(ZoneOffset.UTC)
        return midnight.plusMillis((minutesUtc * 60_000).toLong())
    }
}
