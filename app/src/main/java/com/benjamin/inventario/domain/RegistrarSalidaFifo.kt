package com.benjamin.inventario.domain

import androidx.room.withTransaction
import com.benjamin.inventario.data.local.*

/** DAO surface used by the use case. Queries must run inside the DB transaction. */
interface FifoStockStore {
    suspend fun obtenerLotesFIFO(productoId: Long): List<LoteFIFO>
    suspend fun descontarLote(loteId: Long, cantidad: Long): Int // guarded UPDATE ... WHERE disponible >= cantidad
    suspend fun insertarSalida(salida: SalidaEntity): Long
    suspend fun insertarLinea(linea: SalidaLineaEntity): Long
    suspend fun insertarAsignacionLote(asignacion: SalidaLoteEntity)
    suspend fun actualizarCantidadProducto(productoId: Long, delta: Long): Int // guarded stock >= -delta
}

/** Wrapper implemented by the RoomDatabase: db.withTransaction { block() }. */
interface TransactionRunner {
    suspend fun <T> runAtomically(block: suspend () -> T): T
}

sealed interface ResultadoSalida {
    data class Registrada(val salidaId: Long, val desglose: List<ConsumoLote>) : ResultadoSalida
    data class Rechazada(val motivo: String) : ResultadoSalida
}
data class ConsumoLote(val loteId: Long, val cantidad: Long, val recibidoEnEpochMillis: Long)
data class ItemRetiro(val productoId: Long, val nombreSnapshot: String, val cantidad: Long)

class RegistrarSalidaFifo(
    private val store: FifoStockStore,
    private val tx: TransactionRunner
) {
    suspend operator fun invoke(
        destinatarioId: Long?, destinatarioNombre: String,
        repartidorId: Long?, repartidorNombre: String?,
        fechaEpochMillis: Long, items: List<ItemRetiro>, nota: String = ""
    ): ResultadoSalida {
        if (destinatarioNombre.isBlank()) return ResultadoSalida.Rechazada("Indica el cliente o destino")
        if (items.isEmpty() || items.any { it.productoId <= 0 || it.cantidad <= 0 })
            return ResultadoSalida.Rechazada("Agrega productos con cantidades mayores que cero")
        if (items.groupBy { it.productoId }.any { (_, lineas) -> lineas.size > 1 })
            return ResultadoSalida.Rechazada("Combina las cantidades repetidas del mismo producto")

        return try {
            tx.runAtomically {
                // Preflight completo antes de mutar para rechazar retiros parciales.
                val planes = items.map { item ->
                    val lotes = store.obtenerLotesFIFO(item.productoId)
                        .filter { it.cantidadDisponible > 0 }
                        .sortedWith(compareBy<LoteFIFO> { it.recibidoEnEpochMillis }.thenBy { it.id })
                    val disponible = lotes.sumOf { it.cantidadDisponible }
                    require(disponible >= item.cantidad) {
                        "Stock insuficiente para ${item.nombreSnapshot}: disponible $disponible, solicitado ${item.cantidad}"
                    }
                    var restante = item.cantidad
                    val asignaciones = mutableListOf<ConsumoLote>()
                    for (lote in lotes) {
                        if (restante == 0L) break
                        val tomar = minOf(restante, lote.cantidadDisponible)
                        asignaciones += ConsumoLote(lote.id, tomar, lote.recibidoEnEpochMillis)
                        restante -= tomar
                    }
                    check(restante == 0L)
                    item to asignaciones
                }

                val salidaId = store.insertarSalida(
                    SalidaEntity(
                        destinatarioId = destinatarioId,
                        destinatarioNombre = destinatarioNombre.trim(),
                        repartidorId = repartidorId,
                        repartidorNombre = repartidorNombre?.trim()?.takeIf(String::isNotEmpty),
                        fechaEpochMillis = fechaEpochMillis,
                        nota = nota.trim()
                    )
                )
                val resumen = mutableListOf<ConsumoLote>()
                for ((item, asignaciones) in planes) {
                    val lineaId = store.insertarLinea(
                        SalidaLineaEntity(
                            salidaId = salidaId,
                            productoId = item.productoId,
                            productoNombre = item.nombreSnapshot,
                            cantidad = item.cantidad
                        )
                    )
                    for (a in asignaciones) {
                        check(store.descontarLote(a.loteId, a.cantidad) == 1) {
                            "El lote cambió durante el retiro; inténtalo de nuevo"
                        }
                        store.insertarAsignacionLote(
                            SalidaLoteEntity(salidaLineaId = lineaId, loteId = a.loteId, cantidad = a.cantidad)
                        )
                        resumen += a
                    }
                    check(store.actualizarCantidadProducto(item.productoId, -item.cantidad) == 1) {
                        "No se pudo actualizar el inventario de ${item.nombreSnapshot}"
                    }
                }
                ResultadoSalida.Registrada(salidaId, resumen)
            }
        } catch (e: IllegalArgumentException) {
            ResultadoSalida.Rechazada(e.message ?: "Datos inválidos")
        } catch (e: IllegalStateException) {
            ResultadoSalida.Rechazada(e.message ?: "No se pudo confirmar el movimiento")
        }
    }
}

/* Integración recomendada:
class InventoryDatabase : RoomDatabase(), TransactionRunner {
  override suspend fun <T> runAtomically(block: suspend () -> T): T = withTransaction { block() }
}

DAO de lote debe proteger carrera:
@Query("UPDATE lotes SET cantidadDisponible = cantidadDisponible - :n WHERE id = :id AND cantidadDisponible >= :n")
suspend fun descontarLote(id: Long, n: Long): Int
*/
