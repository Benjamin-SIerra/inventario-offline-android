package com.benjamin.inventario.data.local

import androidx.room.*

@Entity(tableName = "productos", indices = [Index(value = ["nombre"], unique = true)])
data class ProductoEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val nombre: String, val unidad: String = "unidad", val minimo: Long, val maximo: Long, val cantidadActual: Long = 0, val activo: Boolean = true)
@Entity(tableName = "repartidores", indices = [Index("nombre")])
data class RepartidorEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val nombre: String, val activo: Boolean = true)
@Entity(tableName = "entradas", indices = [Index("fechaEpochMillis")])
data class EntradaEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val fechaEpochMillis: Long, val nota: String = "")
@Entity(tableName = "lotes", foreignKeys = [ForeignKey(entity = ProductoEntity::class, parentColumns = ["id"], childColumns = ["productoId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = EntradaEntity::class, parentColumns = ["id"], childColumns = ["entradaId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("productoId", "recibidoEnEpochMillis", "id"), Index("entradaId")])
data class LoteEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val productoId: Long, val entradaId: Long, val recibidoEnEpochMillis: Long, val cantidadInicial: Long, val cantidadDisponible: Long)
@Entity(tableName = "salidas", foreignKeys = [ForeignKey(entity = RepartidorEntity::class, parentColumns = ["id"], childColumns = ["repartidorId"], onDelete = ForeignKey.SET_NULL)], indices = [Index("repartidorId"), Index("fechaEpochMillis")])
data class SalidaEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val repartidorId: Long?, val repartidorNombre: String?, val fechaEpochMillis: Long, val nota: String = "")
@Entity(tableName = "salida_lineas", foreignKeys = [ForeignKey(entity = SalidaEntity::class, parentColumns = ["id"], childColumns = ["salidaId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = ProductoEntity::class, parentColumns = ["id"], childColumns = ["productoId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("salidaId"), Index("productoId", "fechaEpochMillis")])
data class SalidaLineaEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val salidaId: Long, val productoId: Long, val productoNombre: String, val cantidad: Long, val fechaEpochMillis: Long)
@Entity(tableName = "salida_lotes", foreignKeys = [ForeignKey(entity = SalidaLineaEntity::class, parentColumns = ["id"], childColumns = ["salidaLineaId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = LoteEntity::class, parentColumns = ["id"], childColumns = ["loteId"], onDelete = ForeignKey.SET_NULL)], indices = [Index("salidaLineaId"), Index("loteId")])
data class SalidaLoteEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val salidaLineaId: Long, val loteId: Long?, val cantidad: Long)
@Entity(tableName = "devoluciones", foreignKeys = [ForeignKey(entity = SalidaEntity::class, parentColumns = ["id"], childColumns = ["salidaId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = SalidaLineaEntity::class, parentColumns = ["id"], childColumns = ["salidaLineaId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = LoteEntity::class, parentColumns = ["id"], childColumns = ["loteId"], onDelete = ForeignKey.SET_NULL)], indices = [Index("salidaLineaId"), Index("loteId"), Index("salidaId")])
data class DevolucionEntity(@PrimaryKey(autoGenerate = true) val id: Long=0,val salidaId:Long,val salidaLineaId:Long,val loteId:Long?,val cantidad:Long,val fechaEpochMillis:Long,val nota:String="")
@Entity(tableName = "trailers", indices = [Index("diaSemana", "activo")])
data class TrailerProgramadoEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val diaSemana: Int, val nota: String = "", val activo: Boolean = true)
@Entity(tableName = "jornadas_rutas")
data class JornadaRutasEntity(@PrimaryKey val fecha: String, val cantidadRutas: Int)
@Entity(
    tableName = "rutas_diarias",
    foreignKeys = [ForeignKey(entity = JornadaRutasEntity::class, parentColumns = ["fecha"], childColumns = ["fecha"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("fecha"), Index(value = ["fecha", "numeroRuta"], unique = true)]
)
data class RutaDiariaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fecha: String,
    val numeroRuta: Int,
    val completada: Boolean = false
)
data class LoteFIFO(val id: Long, val entradaId: Long, val recibidoEnEpochMillis: Long, val cantidadDisponible: Long)
data class ProductoMovido(val productoId: Long, val productoNombre: String, val unidades: Long)
data class SalidaReciente(val lineaId:Long,val salidaId:Long,val productoId:Long,val productoNombre:String,val cantidad:Long,val repartidorNombre:String?,val fechaEpochMillis:Long,val devuelto:Long) {
    val pendiente: Long get() = (cantidad - devuelto).coerceAtLeast(0)
}
data class EntradaReciente(val entradaId:Long,val loteId:Long,val productoId:Long,val productoNombre:String,val cantidad:Long,val cantidadDisponible:Long,val fechaEpochMillis:Long,val referenciasSalida:Int)
data class LoteEntregado(val loteId:Long?,val cantidad:Long,val recibidoEnEpochMillis:Long)
