package com.benjamin.inventario.domain

const val MAX_RUTAS_POR_JORNADA = 1000

fun generarNumerosDeRuta(cantidad: Int): List<Int> {
    require(cantidad in 1..MAX_RUTAS_POR_JORNADA) {
        "La cantidad de rutas debe estar entre 1 y $MAX_RUTAS_POR_JORNADA"
    }
    return (1..cantidad).toList()
}
