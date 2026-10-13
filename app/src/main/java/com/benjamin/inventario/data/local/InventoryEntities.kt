package com.benjamin.inventario.data.local

import androidx.room.*

@Entity(tableName = "productos", indices = [Index(value = ["nombre"], unique = true)])
data class ProductoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val unidad: String = "unidad",
    val minimo: Long,
    val maximo: Long,
    val cantidadActual: Long = 0,
    val activo: Boolean = true
)

@Entity(tableName = "contactos", indices = [Index("nombre")])
data class ContactoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    /** CLIENTE, PROVEEDOR o AMBOS */ val rol: String,
    val activo: Boolean = true
)

@Entity(tableName = "repartidores", indices = [Index("nombre")])
data class RepartidorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val activo: Boolean = true
)

@Entity(
    tableName = "entradas",
    foreignKeys = [ForeignKey(entity = ContactoEntity::class, parentColumns = ["id"], childColumns = ["proveedorId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("proveedorId"), Index("fechaEpochMillis")]
)
data class EntradaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val proveedorId: Long?,
    val proveedorNombre: String?,
    val fechaEpochMillis: Long,
    val nota: String = ""
)

@Entity(
    tableName = "lotes",
    foreignKeys = [
        ForeignKey(entity = ProductoEntity::class, parentColumns = ["id"], childColumns = ["productoId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = EntradaEntity::class, parentColumns = ["id"], childColumns = ["entradaId"], onDelete = ForeignKey.RESTRICT)
    ], indices = [Index("productoId", "recibidoEnEpochMillis", "id"), Index("entradaId")]
)
data class LoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productoId: Long,
    val entradaId: Long,
    val recibidoEnEpochMillis: Long,
    val cantidadInicial: Long,
    val cantidadDisponible: Long
)

@Entity(
    tableName = "salidas",
    foreignKeys = [
        ForeignKey(entity = ContactoEntity::class, parentColumns = ["id"], childColumns = ["destinatarioId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = RepartidorEntity::class, parentColumns = ["id"], childColumns = ["repartidorId"], onDelete = ForeignKey.RESTRICT)
    ], indices = [Index("destinatarioId"), Index("repartidorId"), Index("fechaEpochMillis")]
)
data class SalidaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val destinatarioId: Long?,
    val destinatarioNombre: String,
    val repartidorId: Long?,
    val repartidorNombre: String?,
    val fechaEpochMillis: Long,
    val nota: String = ""
)

@Entity(
    tableName = "salida_lineas",
    foreignKeys = [
        ForeignKey(entity = SalidaEntity::class, parentColumns = ["id"], childColumns = ["salidaId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ProductoEntity::class, parentColumns = ["id"], childColumns = ["productoId"], onDelete = ForeignKey.RESTRICT)
    ], indices = [Index("salidaId"), Index("productoId", "salidaId")]
)
data class SalidaLineaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val salidaId: Long,
    val productoId: Long,
    val productoNombre: String,
    val cantidad: Long
)

@Entity(
    tableName = "salida_lotes",
    foreignKeys = [
        ForeignKey(entity = SalidaLineaEntity::class, parentColumns = ["id"], childColumns = ["salidaLineaId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = LoteEntity::class, parentColumns = ["id"], childColumns = ["loteId"], onDelete = ForeignKey.RESTRICT)
    ], indices = [Index("salidaLineaId"), Index("loteId")]
)
data class SalidaLoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val salidaLineaId: Long,
    val loteId: Long,
    val cantidad: Long
)

@Entity(tableName = "trailers", indices = [Index("diaSemana", "activo")])
data class TrailerProgramadoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 1=lunes ... 7=domingo (ISO-8601) */ val diaSemana: Int,
    val nota: String = "",
    val activo: Boolean = true
)

/** Cabecera y asignación por lote quedan ligadas para auditoría de devoluciones. */
data class LoteFIFO(val id: Long, val recibidoEnEpochMillis: Long, val cantidadDisponible: Long)

data class SalidaConRelaciones(
    @Embedded val salida: SalidaEntity,
    @Relation(parentColumn = "id", entityColumn = "salidaId") val lineas: List<SalidaLineaEntity>
)
