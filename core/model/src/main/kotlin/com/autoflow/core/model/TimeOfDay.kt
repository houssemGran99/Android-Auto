package com.autoflow.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.DayOfWeek
import java.time.LocalTime

/** A wall-clock time without date, serialized as "HH:mm" (e.g. "08:00"). */
@Serializable(with = TimeOfDaySerializer::class)
data class TimeOfDay(val hour: Int, val minute: Int) : Comparable<TimeOfDay> {
    init {
        require(hour in 0..23) { "hour must be in 0..23 but was $hour" }
        require(minute in 0..59) { "minute must be in 0..59 but was $minute" }
    }

    val minuteOfDay: Int get() = hour * 60 + minute

    fun toLocalTime(): LocalTime = LocalTime.of(hour, minute)

    override fun compareTo(other: TimeOfDay): Int = minuteOfDay.compareTo(other.minuteOfDay)

    override fun toString(): String = "%02d:%02d".format(hour, minute)

    companion object {
        fun parse(text: String): TimeOfDay {
            val parts = text.trim().split(":")
            require(parts.size == 2) { "Invalid time '$text', expected HH:mm" }
            return TimeOfDay(parts[0].toInt(), parts[1].toInt())
        }

        fun of(time: LocalTime): TimeOfDay = TimeOfDay(time.hour, time.minute)
    }
}

object TimeOfDaySerializer : KSerializer<TimeOfDay> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.autoflow.TimeOfDay", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TimeOfDay) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): TimeOfDay = TimeOfDay.parse(decoder.decodeString())
}

/** Day of week independent from java.time so it can be serialized with stable names. */
@Serializable
enum class Weekday {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;

    fun toDayOfWeek(): DayOfWeek = DayOfWeek.valueOf(name)

    companion object {
        val WORKDAYS: Set<Weekday> = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)
        val WEEKEND: Set<Weekday> = setOf(SATURDAY, SUNDAY)

        fun of(day: DayOfWeek): Weekday = valueOf(day.name)
    }
}
