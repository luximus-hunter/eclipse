package eu.tlipman.eclipse.system

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

enum class ScheduleMode {
    CUSTOM_TIMES,
    SUNSET_TO_SUNRISE
}

data class ScheduleTimes(val darkStart: LocalTime, val lightStart: LocalTime) {
    fun validate(): String? =
        if (darkStart == lightStart) "Start and end times must be different." else null

    companion object {
        private val formatter = DateTimeFormatter.ofPattern("HH:mm")

        fun parse(value: String): LocalTime? = try {
            LocalTime.parse(value, formatter)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun darkStartText(): String = darkStart.format(formatter)
    fun lightStartText(): String = lightStart.format(formatter)
}
