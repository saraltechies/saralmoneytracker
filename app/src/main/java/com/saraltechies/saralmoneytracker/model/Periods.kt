package com.saraltechies.saralmoneytracker.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class PeriodKind { DAY, WEEK, MONTH, YEAR, CUSTOM }

/** An inclusive date range. */
data class Period(val kind: PeriodKind, val start: LocalDate, val end: LocalDate) {
    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

/**
 * How weeks and months are cut. [monthStartDay] lets a "month" follow a salary cycle: with 25, October's
 * month runs 25 Oct to 24 Nov. Kept to 1..28 so every month has that day.
 */
data class PeriodPrefs(val weekStart: DayOfWeek = DayOfWeek.MONDAY, val monthStartDay: Int = 1) {
    init {
        require(monthStartDay in 1..28)
    }
}

object Periods {

    fun containing(date: LocalDate, kind: PeriodKind, prefs: PeriodPrefs): Period = when (kind) {
        PeriodKind.DAY, PeriodKind.CUSTOM -> Period(PeriodKind.DAY, date, date)
        PeriodKind.WEEK -> {
            val start = date.with(TemporalAdjusters.previousOrSame(prefs.weekStart))
            Period(kind, start, start.plusDays(6))
        }
        PeriodKind.MONTH -> {
            val s = prefs.monthStartDay
            val start = if (date.dayOfMonth >= s) date.withDayOfMonth(s) else date.minusMonths(1).withDayOfMonth(s)
            Period(kind, start, start.plusMonths(1).minusDays(1))
        }
        PeriodKind.YEAR -> Period(kind, date.withDayOfYear(1), date.withDayOfYear(date.lengthOfYear()))
    }

    fun shift(period: Period, steps: Long, prefs: PeriodPrefs): Period = when (period.kind) {
        PeriodKind.DAY -> containing(period.start.plusDays(steps), PeriodKind.DAY, prefs)
        PeriodKind.WEEK -> containing(period.start.plusWeeks(steps), PeriodKind.WEEK, prefs)
        PeriodKind.MONTH -> containing(period.start.plusMonths(steps), PeriodKind.MONTH, prefs)
        PeriodKind.YEAR -> containing(period.start.plusYears(steps), PeriodKind.YEAR, prefs)
        PeriodKind.CUSTOM -> {
            val length = period.days.toLong()
            Period(PeriodKind.CUSTOM, period.start.plusDays(steps * length), period.end.plusDays(steps * length))
        }
    }

    fun custom(start: LocalDate, end: LocalDate): Period =
        if (end.isBefore(start)) Period(PeriodKind.CUSTOM, end, start) else Period(PeriodKind.CUSTOM, start, end)
}
