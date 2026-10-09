package com.autoflow.core.engine.trigger

import com.autoflow.core.model.TriggerSpec
import com.autoflow.core.model.Weekday
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Computes the next fire time of time-based triggers (DST-safe via [ZonedDateTime]). */
object TimeScheduleCalculator {
    fun nextFireTime(spec: TriggerSpec, after: ZonedDateTime): ZonedDateTime? = when (spec) {
        is TriggerSpec.Time -> nextTime(spec, after)
        is TriggerSpec.Interval -> after.truncatedTo(ChronoUnit.MINUTES).plusMinutes(spec.everyMinutes.toLong())
        is TriggerSpec.SunEvent -> nextSunEvent(spec, after)
        else -> null
    }

    /** Next sunrise/sunset (+offset) after [after] on an allowed day; null if none within a year (polar regions). */
    private fun nextSunEvent(spec: TriggerSpec.SunEvent, after: ZonedDateTime): ZonedDateTime? {
        val start = after.toLocalDate().minusDays(1)
        for (offset in 0L..MAX_SEARCH_DAYS) {
            val instant = SolarCalculator.eventTime(start.plusDays(offset), spec.latitude, spec.longitude, spec.type)
                ?: continue
            val candidate = instant.plusSeconds(spec.offsetMinutes * 60L).atZone(after.zone).truncatedTo(ChronoUnit.MINUTES)
            if (!candidate.isAfter(after)) continue
            if (spec.days.isNotEmpty() && Weekday.of(candidate.dayOfWeek) !in spec.days) continue
            return candidate
        }
        return null
    }

    private const val MAX_SEARCH_DAYS = 370L

    private fun nextTime(spec: TriggerSpec.Time, after: ZonedDateTime): ZonedDateTime {
        val startDate = after.toLocalDate()
        for (offset in 0L..7L) {
            val date = startDate.plusDays(offset)
            if (spec.days.isNotEmpty() && Weekday.of(date.dayOfWeek) !in spec.days) continue
            val candidate = ZonedDateTime.of(date, spec.at.toLocalTime(), after.zone)
            if (candidate.isAfter(after)) return candidate
        }
        // Unreachable for valid input: a week always contains a matching day.
        error("No fire time found for $spec")
    }
}
