package com.benjamin.inventario.domain

import org.junit.Assert.*
import org.junit.Test

class InventoryFilterTest {
    @Test fun productSearchMatchesNameOrCodeWithoutCaseSensitivity() {
        assertTrue(matchesProductSearch("Leche entera","LEC-01","lec-01"))
        assertTrue(matchesProductSearch("Leche entera","LEC-01","LECHE"))
        assertFalse(matchesProductSearch("Leche entera","LEC-01","arroz"))
    }

    @Test fun lowStockIncludesTheConfiguredThresholdButNotOneAbove() {
        assertTrue(isLowStock(5,5))
        assertTrue(isLowStock(0,5))
        assertFalse(isLowStock(6,5))
    }

    @Test fun multiProductDispatchRequiresUniqueProductsPositiveAmountsAndEnoughStock() {
        val stock=mapOf(1L to 8L,2L to 5L)
        assertTrue(isValidMultiProductIssue(listOf(1L to 8L,2L to 3L),stock))
        assertFalse(isValidMultiProductIssue(listOf(1L to 4L,1L to 2L),stock))
        assertFalse(isValidMultiProductIssue(listOf(1L to 0L),stock))
        assertFalse(isValidMultiProductIssue(listOf(2L to 6L),stock))
    }
}
