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
        else -> null
    }

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
