package com.benjamin.inventario.domain

/** Returns the amount still outstanding after validating one additional return. */
fun pendienteTrasDevolucion(entregadas: Long, yaDevueltas: Long, solicita: Long): Long {
    require(entregadas > 0) { "La cantidad entregada debe ser mayor que cero" }
    require(yaDevueltas in 0..entregadas) { "La cantidad ya devuelta es inválida" }
    require(solicita > 0) { "La cantidad debe ser mayor que cero" }
    val pendiente = entregadas - yaDevueltas
    require(solicita <= pendiente) { "La devolución supera la cantidad pendiente (${pendiente} u)" }
    return pendiente - solicita
}
