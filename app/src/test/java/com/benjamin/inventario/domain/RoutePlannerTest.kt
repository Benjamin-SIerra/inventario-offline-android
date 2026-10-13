package com.benjamin.inventario.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutePlannerTest {
    @Test
    fun generaNumeracionSecuencialParaLaJornada() {
        assertEquals((1..20).toList(), generarNumerosDeRuta(20))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rechazaCantidadDeRutasCero() {
        generarNumerosDeRuta(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rechazaCantidadMayorAlLimitePermitido() {
        generarNumerosDeRuta(1001)
    }
}
