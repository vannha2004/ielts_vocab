package com.example.domain

import java.util.Calendar
import java.util.TimeZone

/**
 * Controllable clock abstraction for deterministic spaced repetition scheduling
 * and automated testing without waiting for real calendar days to pass.
 */
interface AppClock {
    fun nowMillis(): Long
    fun currentTimeZone(): TimeZone

    /**
     * Returns a normalized local calendar day number (YYYYMMDD as Int or days since epoch in local TZ)
     * so due-review comparisons work across timezone shifts and DST transitions.
     */
    fun currentLocalEpochDay(): Long {
        return millisToLocalEpochDay(nowMillis(), currentTimeZone())
    }

    fun addLocalDays(baseMillis: Long, daysToAdd: Int): Long {
        val cal = Calendar.getInstance(currentTimeZone()).apply {
            timeInMillis = baseMillis
            add(Calendar.DAY_OF_YEAR, daysToAdd)
            set(Calendar.HOUR_OF_DAY, 6) // Due at start of study day (06:00 local time)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun formatLocalDate(millis: Long): String {
        val cal = Calendar.getInstance(currentTimeZone()).apply {
            timeInMillis = millis
        }
        val d = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        val m = (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
        val y = cal.get(Calendar.YEAR)
        return "$d/$m/$y"
    }

    companion object {
        fun millisToLocalEpochDay(millis: Long, timeZone: TimeZone): Long {
            val cal = Calendar.getInstance(timeZone).apply {
                timeInMillis = millis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val offsetMillis = timeZone.getOffset(cal.timeInMillis)
            return (cal.timeInMillis + offsetMillis) / 86_400_000L
        }
    }
}

class SystemAppClock(
    private var dayOffset: Int = 0
) : AppClock {
    fun setDayOffset(days: Int) {
        dayOffset = days
    }

    fun getDayOffset(): Int = dayOffset

    override fun nowMillis(): Long {
        return System.currentTimeMillis() + (dayOffset * 86_400_000L)
    }

    override fun currentTimeZone(): TimeZone = TimeZone.getDefault()
}

class ControllableAppClock(
    private var currentMillis: Long = 1_761_380_000_000L, // Fixed test timestamp
    private var timeZone: TimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
) : AppClock {
    override fun nowMillis(): Long = currentMillis
    override fun currentTimeZone(): TimeZone = timeZone

    fun setNowMillis(millis: Long) {
        currentMillis = millis
    }

    fun advanceDays(days: Int) {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = currentMillis
            add(Calendar.DAY_OF_YEAR, days)
        }
        currentMillis = cal.timeInMillis
    }

    fun advanceHours(hours: Int) {
        currentMillis += hours * 3_600_000L
    }

    fun setTimeZone(newTimeZone: TimeZone) {
        timeZone = newTimeZone
    }
}
