package com.saraltechies.saralmoneytracker.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object Recurrence {

    /** Safety cap so a rule with a very old start date can't add thousands of entries at once. */
    const val MAX_CATCH_UP = 400

    /**
     * The occurrence after [after]. Counted from [start] each time, so a rule started on 31 January lands on
     * 28/29 February and goes back to 31 March, instead of drifting to the 28th forever.
     */
    fun next(start: LocalDate, frequency: Frequency, after: LocalDate): LocalDate {
        if (after.isBefore(start)) return start
        var k = when (frequency) {
            Frequency.DAILY -> ChronoUnit.DAYS.between(start, after)
            Frequency.WEEKLY -> ChronoUnit.WEEKS.between(start, after)
            Frequency.MONTHLY -> ChronoUnit.MONTHS.between(start, after)
            Frequency.YEARLY -> ChronoUnit.YEARS.between(start, after)
        }
        while (true) {
            val candidate = occurrence(start, frequency, k)
            if (candidate.isAfter(after)) return candidate
            k++
        }
    }

    fun occurrence(start: LocalDate, frequency: Frequency, index: Long): LocalDate = when (frequency) {
        Frequency.DAILY -> start.plusDays(index)
        Frequency.WEEKLY -> start.plusWeeks(index)
        Frequency.MONTHLY -> start.plusMonths(index)
        Frequency.YEARLY -> start.plusYears(index)
    }

    /** Dates from the rule's next date up to [today] that should be added now. */
    fun due(rule: Recurring, today: LocalDate): List<LocalDate> {
        if (!rule.active) return emptyList()
        val dates = mutableListOf<LocalDate>()
        var date = rule.nextDate
        while (!date.isAfter(today) && (rule.endDate == null || !date.isAfter(rule.endDate)) && dates.size < MAX_CATCH_UP) {
            dates += date
            date = next(rule.startDate, rule.frequency, date)
        }
        return dates
    }

    /** Upcoming dates within [days] after [today], for the "coming up" list. */
    fun upcoming(rule: Recurring, today: LocalDate, days: Long): List<LocalDate> {
        if (!rule.active) return emptyList()
        val until = today.plusDays(days)
        val dates = mutableListOf<LocalDate>()
        var date = rule.nextDate
        while (!date.isAfter(until) && (rule.endDate == null || !date.isAfter(rule.endDate)) && dates.size < 31) {
            if (date.isAfter(today)) dates += date
            date = next(rule.startDate, rule.frequency, date)
        }
        return dates
    }
}
