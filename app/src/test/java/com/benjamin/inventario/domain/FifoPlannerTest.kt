package com.benjamin.inventario.domain

import com.benjamin.inventario.data.local.LoteFIFO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FifoPlannerTest {
    @Test fun consumesOldestAndSplitsAcrossLots(){
        val lots=listOf(LoteFIFO(8,1,300,2),LoteFIFO(3,2,100,4),LoteFIFO(5,3,200,3))
        assertEquals(listOf(AsignacionFifo(3,4),AsignacionFifo(5,1)),planificarFifo(lots,5))
    }
    @Test fun deterministicTieBreakById(){
        val lots=listOf(LoteFIFO(9,1,100,2),LoteFIFO(2,1,100,2))
        assertEquals(listOf(AsignacionFifo(2,1)),planificarFifo(lots,1))
    }
    @Test fun insufficientStockRejectedBeforeAnyWrite(){
        assertThrows(IllegalArgumentException::class.java){planificarFifo(listOf(LoteFIFO(1,1,100,2)),3)}
    }
}
