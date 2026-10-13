package com.benjamin.inventario.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantityLogicTest {
    @Test
    fun acceptsOnlyWholeNumberCharactersInQuantityInputs() {
        assertTrue(isWholeNumberInput(""))
        assertTrue(isWholeNumberInput("007"))
        assertFalse(isWholeNumberInput("7.5"))
        assertFalse(isWholeNumberInput("-7"))
        assertFalse(isWholeNumberInput("7x"))
    }

    @Test
    fun parsesNonNegativeQuantitiesAndRejectsDecimalsAndOverflow() {
        assertEquals(0L, parseWholeNumber("0"))
        assertEquals(42L, parseWholeNumber("42"))
        assertNull(parseWholeNumber("4.2"))
        assertNull(parseWholeNumber("9223372036854775808"))
    }

    @Test
    fun roundsDailyConsumptionUpToWholeUnits() {
        assertEquals(0L, roundedDailyConsumption(0L, 30))
        assertEquals(1L, roundedDailyConsumption(1L, 30))
        assertEquals(2L, roundedDailyConsumption(31L, 30))
    }

    @Test
    fun roundsLeadTimeForecastUpWithoutRoundingDailyRateFirst() {
        assertEquals(1L, roundedDemandForecast(1L, 30, 7))
        assertEquals(8L, roundedDemandForecast(31L, 30, 7))
        assertEquals(0L, roundedDemandForecast(0L, 30, 7))
    }

    @Test
    fun suggestedOrderIsAnIntegerAndDoesNotGoBelowZero() {
        assertEquals(12L, suggestedOrderQuantity(stock = 16L, target = 20L, forecast = 8L))
        assertEquals(0L, suggestedOrderQuantity(stock = 30L, target = 20L, forecast = 8L))
    }
}
