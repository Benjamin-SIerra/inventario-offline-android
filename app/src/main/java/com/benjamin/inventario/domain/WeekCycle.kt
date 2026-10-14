package com.benjamin.inventario.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class WeekCycle(val start: LocalDate, val endExclusive: LocalDate) {
    val lastDay: LocalDate get() = endExclusive.minusDays(1)
}

/** Returns a Monday..Sunday (or user-selected weekday) cycle containing [date]. */
fun weekCycle(date: LocalDate, startDay: DayOfWeek): WeekCycle {
    val start = date.with(TemporalAdjusters.previousOrSame(startDay))
    return WeekCycle(start, start.plusWeeks(1))
}
