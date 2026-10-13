package com.benjamin.inventario.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReturnPlannerTest {
    @Test fun exactFullReturnIsAcceptedAndLeavesZeroPending() {
        assertEquals(0L, pendienteTrasDevolucion(entregadas = 20, yaDevueltas = 0, solicita = 20))
    }

    @Test fun partialReturnsUseOnlyTheRemainingQuantity() {
        assertEquals(0L, pendienteTrasDevolucion(entregadas = 20, yaDevueltas = 7, solicita = 13))
    }

    @Test fun returnAboveRemainingQuantityIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            pendienteTrasDevolucion(entregadas = 20, yaDevueltas = 7, solicita = 14)
        }
    }
}
