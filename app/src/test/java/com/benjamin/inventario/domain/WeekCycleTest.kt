package com.benjamin.inventario.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeekCycleTest {
    @Test fun defaultMondayCycleIncludesSundayAndEndsBeforeNextMonday() {
        val cycle = weekCycle(LocalDate.parse("2026-10-18"), DayOfWeek.MONDAY)
        assertEquals(LocalDate.parse("2026-10-12"), cycle.start)
        assertEquals(LocalDate.parse("2026-10-18"), cycle.lastDay)
        assertEquals(LocalDate.parse("2026-10-19"), cycle.endExclusive)
    }

    @Test fun configurableStartDayHandlesBoundaryDate() {
        val cycle = weekCycle(LocalDate.parse("2026-10-18"), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.parse("2026-10-18"), cycle.start)
        assertEquals(LocalDate.parse("2026-10-24"), cycle.lastDay)
    }
}
