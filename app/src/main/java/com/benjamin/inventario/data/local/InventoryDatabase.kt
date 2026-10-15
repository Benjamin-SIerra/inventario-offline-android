package com.benjamin.inventario.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM productos WHERE activo=1 ORDER BY nombre COLLATE NOCASE")
    fun productos(): Flow<List<ProductoEntity>>

    @Query("SELECT * FROM productos WHERE id=:id")
    suspend fun producto(id: Long): ProductoEntity?

    @Query("UPDATE productos SET nombre=:nombre,codigo=:codigo,unidad=:unidad,minimo=:minimo,maximo=:maximo WHERE id=:id")
    suspend fun actualizarProducto(id:Long,nombre:String,codigo:String,unidad:String,minimo:Long,maximo:Long):Int

    @Query("UPDATE productos SET activo=0 WHERE id=:id")
    suspend fun archivarProducto(id:Long):Int

    @Query("DELETE FROM productos WHERE id=:id")
    suspend fun eliminarProductoSinHistorial(id:Long):Int

    @Insert
    suspend fun crearProducto(p: ProductoEntity): Long

    @Query("UPDATE productos SET cantidadActual=cantidadActual+:delta WHERE id=:id AND cantidadActual+:delta>=0")
    suspend fun ajustarStock(id: Long, delta: Long): Int

    @Query("SELECT * FROM repartidores WHERE activo=1 ORDER BY nombre COLLATE NOCASE")
    fun repartidores(): Flow<List<RepartidorEntity>>

    @Query("SELECT * FROM repartidores ORDER BY activo DESC, nombre COLLATE NOCASE")
    fun todosLosRepartidores(): Flow<List<RepartidorEntity>>

    @Insert
    suspend fun crearRepartidor(r: RepartidorEntity): Long

    @Query("UPDATE repartidores SET nombre=:nombre,activo=1 WHERE id=:id")
    suspend fun actualizarRepartidor(id: Long, nombre: String): Int

    @Query("UPDATE repartidores SET activo=0 WHERE id=:id")
        suspend fun eliminarRepartidor(id: Long): Int

    @Query("SELECT * FROM trailers WHERE activo=1 ORDER BY diaSemana")
    fun trailers(): Flow<List<TrailerProgramadoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarTrailer(t: TrailerProgramadoEntity)

    @Query("SELECT COUNT(DISTINCT diaSemana) FROM trailers WHERE activo=1")
    fun diasTrailer(): Flow<Int>

    @Query("SELECT COUNT(*) FROM productos WHERE activo=1 AND cantidadActual=0")
    fun agotados(): Flow<Int>

    @Query("SELECT COUNT(*) FROM productos WHERE activo=1 AND cantidadActual>0 AND cantidadActual<=minimo")
    fun bajoStock(): Flow<Int>

    @Query("SELECT COALESCE(SUM(cantidad),0) FROM salida_lineas WHERE fechaEpochMillis>=:desde AND fechaEpochMillis<:hasta")
    fun unidadesMovidas(desde: Long, hasta: Long): Flow<Long>

    @Query("SELECT productoId,productoNombre,SUM(cantidad) unidades FROM salida_lineas WHERE fechaEpochMillis>=:desde AND fechaEpochMillis<:hasta GROUP BY productoId ORDER BY unidades DESC LIMIT 10")
    fun ranking(desde: Long, hasta: Long): Flow<List<ProductoMovido>>

    @Query("SELECT id,entradaId,recibidoEnEpochMillis,cantidadDisponible FROM lotes WHERE productoId=:pid AND cantidadDisponible>0 ORDER BY recibidoEnEpochMillis ASC,id ASC")
    suspend fun lotesFifo(pid: Long): List<LoteFIFO>

    @Query("UPDATE lotes SET cantidadDisponible=cantidadDisponible-:n WHERE id=:id AND cantidadDisponible>=:n")
    suspend fun descontarLote(id: Long, n: Long): Int

    @Query("UPDATE lotes SET cantidadDisponible=cantidadDisponible+:n WHERE id=:id")
    suspend fun restaurarLote(id: Long, n: Long): Int

    @Query("SELECT id,entradaId,recibidoEnEpochMillis,cantidadDisponible FROM lotes WHERE productoId=:pid AND cantidadDisponible>0 ORDER BY recibidoEnEpochMillis DESC,id DESC")
    suspend fun lotesDisponiblesParaAjuste(pid:Long):List<LoteFIFO>

    @Query("UPDATE lotes SET cantidadDisponible=cantidadDisponible-:n WHERE id=:id AND cantidadDisponible>=:n")
    suspend fun reducirLoteDisponible(id:Long,n:Long):Int

    @Insert
    suspend fun insertarEntrada(e: EntradaEntity): Long

    @Insert
    suspend fun insertarLote(l: LoteEntity): Long

    @Insert
    suspend fun insertarSalida(s: SalidaEntity): Long

    @Insert
    suspend fun insertarLinea(l: SalidaLineaEntity): Long

    @Insert
    suspend fun insertarAsignacion(a: SalidaLoteEntity)

    @Insert
    suspend fun insertarDevolucion(d: DevolucionEntity)

    @Insert
    suspend fun insertarAjusteInventario(a:AjusteInventarioEntity)

    @Insert
    suspend fun insertarAuditoriaCarga(a: AuditoriaCargaEntity)

    @Query("UPDATE salidas SET cantidadTotal=(SELECT COALESCE(SUM(cantidad),0) FROM salida_lineas WHERE salidaId=:saleId) WHERE id=:saleId")
    suspend fun recalcularTotalSalida(saleId:Long):Int

    @Query("SELECT COALESCE(SUM(cantidad),0) FROM devoluciones WHERE salidaLineaId=:line AND loteId=:lot")
    suspend fun totalDevuelto(line: Long, lot: Long): Long

    @Query("SELECT loteId,cantidad,0 AS recibidoEnEpochMillis FROM salida_lotes WHERE salidaLineaId=:line ORDER BY id")
    suspend fun lotesSalida(line: Long): List<LoteEntregado>

    @Query("SELECT * FROM salida_lineas WHERE id=:id")
    suspend fun lineaSalida(id: Long): SalidaLineaEntity?

    @Query("SELECT COALESCE(SUM(cantidad),0) FROM devoluciones WHERE salidaLineaId=:line")
    suspend fun totalDevueltoLinea(line: Long): Long

    @Query("SELECT COUNT(*) FROM salida_lotes WHERE loteId=:lot")
    suspend fun referenciasSalidaLote(lot: Long): Int

    @Query("SELECT * FROM lotes WHERE id=:id")
    suspend fun lote(id: Long): LoteEntity?

    @Query("SELECT COUNT(*) FROM lotes WHERE entradaId=:entrada")
    suspend fun lotesDeEntrada(entrada: Long): Int

    @Query("SELECT COUNT(*) FROM salida_lineas WHERE productoId=:id")
    suspend fun referenciasProducto(id:Long):Int

    @Query("UPDATE lotes SET productoId=:producto,cantidadInicial=:inicial,cantidadDisponible=:disponible WHERE id=:id")
    suspend fun actualizarLote(id: Long, producto: Long, inicial: Long, disponible: Long): Int

    @Query("DELETE FROM lotes WHERE id=:id")
    suspend fun eliminarLote(id: Long): Int

    @Query("DELETE FROM entradas WHERE id=:id")
    suspend fun eliminarEntrada(id: Long): Int

    @Query("DELETE FROM salida_lotes WHERE salidaLineaId=:line")
    suspend fun eliminarAsignacionesLinea(line: Long): Int

    @Query("UPDATE salida_lineas SET productoId=:producto,productoNombre=:nombre,productoCodigo=:codigo,cantidad=:cantidad WHERE id=:line")
        suspend fun actualizarLineaSalida(line: Long, producto: Long, nombre: String, codigo:String, cantidad: Long): Int

    @Query("DELETE FROM salida_lineas WHERE id=:line")
    suspend fun eliminarLineaSalida(line: Long): Int

    @Query("SELECT COUNT(*) FROM salida_lineas WHERE salidaId=:salida")
    suspend fun lineasDeSalida(salida: Long): Int

    @Query("DELETE FROM salidas WHERE id=:id")
    suspend fun eliminarSalida(id: Long): Int

    @Query("SELECT salida_lineas.id AS lineaId,salidas.id AS salidaId,productoId,productoNombre,productoCodigo,salida_lineas.cantidad,salidas.repartidorId,salidas.repartidorNombre,salida_lineas.fechaEpochMillis,salidas.cantidadTotal,COALESCE(SUM(devoluciones.cantidad),0) AS devuelto FROM salida_lineas JOIN salidas ON salidas.id=salida_lineas.salidaId LEFT JOIN devoluciones ON devoluciones.salidaLineaId=salida_lineas.id GROUP BY salida_lineas.id ORDER BY salida_lineas.fechaEpochMillis DESC LIMIT 30")
    fun salidasRecientes(): Flow<List<SalidaReciente>>

    @Query("SELECT salida_lineas.id AS lineaId,salidas.id AS salidaId,productoId,productoNombre,productoCodigo,salida_lineas.cantidad,salidas.repartidorId,salidas.repartidorNombre,salida_lineas.fechaEpochMillis,salidas.cantidadTotal,COALESCE(SUM(devoluciones.cantidad),0) AS devuelto FROM salida_lineas JOIN salidas ON salidas.id=salida_lineas.salidaId LEFT JOIN devoluciones ON devoluciones.salidaLineaId=salida_lineas.id WHERE (salidas.repartidorId=:driverId OR (salidas.repartidorId IS NULL AND salidas.repartidorNombre=:driverName)) GROUP BY salida_lineas.id ORDER BY salida_lineas.fechaEpochMillis DESC")
    fun salidasPorRepartidor(driverId:Long,driverName:String):Flow<List<SalidaReciente>>

    @Query("SELECT salida_lineas.id AS lineaId,salidas.id AS salidaId,productoId,productoNombre,productoCodigo,salida_lineas.cantidad,salidas.repartidorId,salidas.repartidorNombre,salida_lineas.fechaEpochMillis,salidas.cantidadTotal,COALESCE(SUM(devoluciones.cantidad),0) AS devuelto FROM salida_lineas JOIN salidas ON salidas.id=salida_lineas.salidaId LEFT JOIN devoluciones ON devoluciones.salidaLineaId=salida_lineas.id WHERE salida_lineas.fechaEpochMillis>=:desde AND salida_lineas.fechaEpochMillis<:hasta GROUP BY salida_lineas.id ORDER BY salida_lineas.fechaEpochMillis DESC")
    fun salidasEnRango(desde:Long,hasta:Long):Flow<List<SalidaReciente>>

    @Query("SELECT entradas.id AS entradaId,lotes.id AS loteId,lotes.productoId AS productoId,productos.nombre AS productoNombre,lotes.cantidadInicial AS cantidad,lotes.cantidadDisponible AS cantidadDisponible,entradas.fechaEpochMillis AS fechaEpochMillis,COUNT(salida_lotes.id) AS referenciasSalida FROM entradas JOIN lotes ON lotes.entradaId=entradas.id JOIN productos ON productos.id=lotes.productoId LEFT JOIN salida_lotes ON salida_lotes.loteId=lotes.id GROUP BY lotes.id ORDER BY entradas.fechaEpochMillis DESC,lotes.id DESC LIMIT 30")
    fun entradasRecientes(): Flow<List<EntradaReciente>>

    @Query("SELECT entradas.id AS entradaId,lotes.id AS loteId,lotes.productoId AS productoId,productos.nombre AS productoNombre,lotes.cantidadInicial AS cantidad,lotes.cantidadDisponible AS cantidadDisponible,entradas.fechaEpochMillis AS fechaEpochMillis,COUNT(salida_lotes.id) AS referenciasSalida FROM entradas JOIN lotes ON lotes.entradaId=entradas.id JOIN productos ON productos.id=lotes.productoId LEFT JOIN salida_lotes ON salida_lotes.loteId=lotes.id WHERE entradas.fechaEpochMillis>=:desde AND entradas.fechaEpochMillis<:hasta GROUP BY lotes.id ORDER BY entradas.fechaEpochMillis DESC,lotes.id DESC")
    fun entradasEnRango(desde:Long,hasta:Long):Flow<List<EntradaReciente>>

    @Query("SELECT devoluciones.id,COALESCE(salida_lineas.productoNombre,'Producto eliminado') AS productoNombre,salidas.repartidorNombre,devoluciones.cantidad,devoluciones.fechaEpochMillis FROM devoluciones JOIN salida_lineas ON salida_lineas.id=devoluciones.salidaLineaId JOIN salidas ON salidas.id=devoluciones.salidaId WHERE devoluciones.fechaEpochMillis>=:desde AND devoluciones.fechaEpochMillis<:hasta ORDER BY devoluciones.fechaEpochMillis DESC")
    fun devolucionesEnRango(desde:Long,hasta:Long):Flow<List<DevolucionReciente>>

    @Query("SELECT id,productoNombre,cantidadDelta,fechaEpochMillis,nota FROM ajustes_inventario WHERE fechaEpochMillis>=:desde AND fechaEpochMillis<:hasta ORDER BY fechaEpochMillis DESC")
    fun ajustesEnRango(desde:Long,hasta:Long):Flow<List<AjusteReciente>>

    @Query("SELECT * FROM configuracion_semanal WHERE id=1")
    fun configuracionSemanal():Flow<ConfiguracionSemanalEntity?>

    @Insert(onConflict=OnConflictStrategy.REPLACE)
    suspend fun guardarConfiguracionSemanal(config:ConfiguracionSemanalEntity)

    @Query("SELECT COALESCE(SUM(salida_lineas.cantidad-COALESCE(returned.total,0)),0) FROM salida_lineas LEFT JOIN (SELECT salidaLineaId,SUM(cantidad) AS total FROM devoluciones GROUP BY salidaLineaId) AS returned ON returned.salidaLineaId=salida_lineas.id WHERE productoId=:pid AND fechaEpochMillis>=:desde AND fechaEpochMillis<:hasta")
    suspend fun demanda(pid: Long, desde: Long, hasta: Long): Long

    @Query("SELECT COUNT(*) FROM entradas WHERE fechaEpochMillis>=:desde AND fechaEpochMillis<:hasta")
    fun entradasHoy(desde: Long, hasta: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM salidas WHERE fechaEpochMillis>=:desde AND fechaEpochMillis<:hasta")
    fun salidasHoy(desde: Long, hasta: Long): Flow<Int>

    @Query("SELECT * FROM jornadas_rutas WHERE fecha=:fecha LIMIT 1")
    fun jornadaRutas(fecha: String): Flow<JornadaRutasEntity?>

    @Query("SELECT * FROM rutas_diarias WHERE fecha=:fecha AND numeroRuta<=:cantidad ORDER BY numeroRuta")
    fun rutasDiarias(fecha: String, cantidad: Int): Flow<List<RutaDiariaEntity>>

    @Upsert
    suspend fun guardarJornadaRutas(jornada: JornadaRutasEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarRutasDiarias(rutas: List<RutaDiariaEntity>)

    @Query("UPDATE rutas_diarias SET completada=:completada WHERE fecha=:fecha AND numeroRuta=:numeroRuta")
    suspend fun marcarRuta(fecha: String, numeroRuta: Int, completada: Boolean): Int

    @Query("DELETE FROM ajustes_inventario")
    suspend fun limpiarAjustesInventario()

    @Query("DELETE FROM auditoria_cargas")
    suspend fun limpiarAuditoriaCargas()

    @Query("DELETE FROM devoluciones")
    suspend fun limpiarDevoluciones()

    @Query("DELETE FROM salida_lotes")
    suspend fun limpiarAsignaciones()

    @Query("DELETE FROM salida_lineas")
    suspend fun limpiarLineasSalida()

    @Query("DELETE FROM salidas")
    suspend fun limpiarSalidas()

    @Query("DELETE FROM lotes")
    suspend fun limpiarLotes()

    @Query("DELETE FROM entradas")
    suspend fun limpiarEntradas()

    @Query("DELETE FROM rutas_diarias")
    suspend fun limpiarRutasDiarias()

    @Query("DELETE FROM jornadas_rutas")
    suspend fun limpiarJornadasRutas()

    @Query("DELETE FROM trailers")
    suspend fun limpiarTrailers()

    @Query("DELETE FROM repartidores")
    suspend fun limpiarRepartidores()

    @Query("DELETE FROM productos")
    suspend fun limpiarProductos()
}

@Database(
    entities = [ProductoEntity::class, RepartidorEntity::class, EntradaEntity::class, LoteEntity::class,
        SalidaEntity::class, SalidaLineaEntity::class, SalidaLoteEntity::class, DevolucionEntity::class,
        TrailerProgramadoEntity::class, JornadaRutasEntity::class, RutaDiariaEntity::class, ConfiguracionSemanalEntity::class, AjusteInventarioEntity::class, AuditoriaCargaEntity::class],
    version = 5,
    exportSchema = true
)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun dao(): InventoryDao
}

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `jornadas_rutas` (`fecha` TEXT NOT NULL, `cantidadRutas` INTEGER NOT NULL, PRIMARY KEY(`fecha`))")
        database.execSQL("CREATE TABLE IF NOT EXISTS `rutas_diarias` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fecha` TEXT NOT NULL, `numeroRuta` INTEGER NOT NULL, `completada` INTEGER NOT NULL, FOREIGN KEY(`fecha`) REFERENCES `jornadas_rutas`(`fecha`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_rutas_diarias_fecha` ON `rutas_diarias` (`fecha`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_rutas_diarias_fecha_numeroRuta` ON `rutas_diarias` (`fecha`, `numeroRuta`)")
    }
}

val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Keep all ledger values and IDs while rebuilding tables that formerly referenced contacts.
        database.execSQL("CREATE TABLE `_migration_entradas` AS SELECT id,fechaEpochMillis,nota FROM entradas")
        database.execSQL("CREATE TABLE `_migration_lotes` AS SELECT id,productoId,entradaId,recibidoEnEpochMillis,cantidadInicial,cantidadDisponible FROM lotes")
        database.execSQL("CREATE TABLE `_migration_salidas` AS SELECT id,repartidorId,repartidorNombre,fechaEpochMillis,nota FROM salidas")
        database.execSQL("CREATE TABLE `_migration_lineas` AS SELECT id,salidaId,productoId,productoNombre,cantidad,fechaEpochMillis FROM salida_lineas")
        database.execSQL("CREATE TABLE `_migration_asignaciones` AS SELECT id,salidaLineaId,loteId,cantidad FROM salida_lotes")
        database.execSQL("CREATE TABLE `_migration_devoluciones` AS SELECT id,salidaId,salidaLineaId,loteId,cantidad,fechaEpochMillis,nota FROM devoluciones")

        database.execSQL("DROP TABLE devoluciones")
        database.execSQL("DROP TABLE salida_lotes")
        database.execSQL("DROP TABLE salida_lineas")
        database.execSQL("DROP TABLE lotes")
        database.execSQL("DROP TABLE salidas")
        database.execSQL("DROP TABLE entradas")
        database.execSQL("DROP TABLE contactos")

        database.execSQL("CREATE TABLE `entradas` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fechaEpochMillis` INTEGER NOT NULL, `nota` TEXT NOT NULL)")
        database.execSQL("CREATE INDEX `index_entradas_fechaEpochMillis` ON `entradas` (`fechaEpochMillis`)")
        database.execSQL("CREATE TABLE `lotes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productoId` INTEGER NOT NULL, `entradaId` INTEGER NOT NULL, `recibidoEnEpochMillis` INTEGER NOT NULL, `cantidadInicial` INTEGER NOT NULL, `cantidadDisponible` INTEGER NOT NULL, FOREIGN KEY(`productoId`) REFERENCES `productos`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`entradaId`) REFERENCES `entradas`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        database.execSQL("CREATE INDEX `index_lotes_productoId_recibidoEnEpochMillis_id` ON `lotes` (`productoId`,`recibidoEnEpochMillis`,`id`)")
        database.execSQL("CREATE INDEX `index_lotes_entradaId` ON `lotes` (`entradaId`)")
        database.execSQL("CREATE TABLE `salidas` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `repartidorId` INTEGER, `repartidorNombre` TEXT, `fechaEpochMillis` INTEGER NOT NULL, `nota` TEXT NOT NULL, FOREIGN KEY(`repartidorId`) REFERENCES `repartidores`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
        database.execSQL("CREATE INDEX `index_salidas_repartidorId` ON `salidas` (`repartidorId`)")
        database.execSQL("CREATE INDEX `index_salidas_fechaEpochMillis` ON `salidas` (`fechaEpochMillis`)")
        database.execSQL("CREATE TABLE `salida_lineas` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `salidaId` INTEGER NOT NULL, `productoId` INTEGER NOT NULL, `productoNombre` TEXT NOT NULL, `cantidad` INTEGER NOT NULL, `fechaEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`salidaId`) REFERENCES `salidas`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`productoId`) REFERENCES `productos`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        database.execSQL("CREATE INDEX `index_salida_lineas_salidaId` ON `salida_lineas` (`salidaId`)")
        database.execSQL("CREATE INDEX `index_salida_lineas_productoId_fechaEpochMillis` ON `salida_lineas` (`productoId`,`fechaEpochMillis`)")
        database.execSQL("CREATE TABLE `salida_lotes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `salidaLineaId` INTEGER NOT NULL, `loteId` INTEGER, `cantidad` INTEGER NOT NULL, FOREIGN KEY(`salidaLineaId`) REFERENCES `salida_lineas`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`loteId`) REFERENCES `lotes`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
        database.execSQL("CREATE INDEX `index_salida_lotes_salidaLineaId` ON `salida_lotes` (`salidaLineaId`)")
        database.execSQL("CREATE INDEX `index_salida_lotes_loteId` ON `salida_lotes` (`loteId`)")
        database.execSQL("CREATE TABLE `devoluciones` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `salidaId` INTEGER NOT NULL, `salidaLineaId` INTEGER NOT NULL, `loteId` INTEGER, `cantidad` INTEGER NOT NULL, `fechaEpochMillis` INTEGER NOT NULL, `nota` TEXT NOT NULL, FOREIGN KEY(`salidaId`) REFERENCES `salidas`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`salidaLineaId`) REFERENCES `salida_lineas`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`loteId`) REFERENCES `lotes`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
        database.execSQL("CREATE INDEX `index_devoluciones_salidaLineaId` ON `devoluciones` (`salidaLineaId`)")
        database.execSQL("CREATE INDEX `index_devoluciones_loteId` ON `devoluciones` (`loteId`)")
        database.execSQL("CREATE INDEX `index_devoluciones_salidaId` ON `devoluciones` (`salidaId`)")

        database.execSQL("INSERT INTO entradas SELECT * FROM `_migration_entradas`")
        database.execSQL("INSERT INTO lotes SELECT * FROM `_migration_lotes`")
        database.execSQL("INSERT INTO salidas SELECT * FROM `_migration_salidas`")
        database.execSQL("INSERT INTO salida_lineas SELECT * FROM `_migration_lineas`")
        database.execSQL("INSERT INTO salida_lotes SELECT * FROM `_migration_asignaciones`")
        database.execSQL("INSERT INTO devoluciones SELECT * FROM `_migration_devoluciones`")

        database.execSQL("DROP TABLE `_migration_devoluciones`")
        database.execSQL("DROP TABLE `_migration_asignaciones`")
        database.execSQL("DROP TABLE `_migration_lineas`")
        database.execSQL("DROP TABLE `_migration_salidas`")
        database.execSQL("DROP TABLE `_migration_lotes`")
        database.execSQL("DROP TABLE `_migration_entradas`")
    }
}

val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE productos ADD COLUMN codigo TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE salida_lineas ADD COLUMN productoCodigo TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE salidas ADD COLUMN cantidadTotal INTEGER NOT NULL DEFAULT 0")
        database.execSQL("UPDATE salidas SET cantidadTotal=(SELECT COALESCE(SUM(cantidad),0) FROM salida_lineas WHERE salidaId=salidas.id)")
        database.execSQL("CREATE TABLE IF NOT EXISTS configuracion_semanal (id INTEGER NOT NULL PRIMARY KEY, inicioSemana INTEGER NOT NULL)")
        database.execSQL("INSERT OR IGNORE INTO configuracion_semanal (id,inicioSemana) VALUES (1,1)")
        database.execSQL("CREATE TABLE IF NOT EXISTS ajustes_inventario (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, productoId INTEGER, productoNombre TEXT NOT NULL, cantidadDelta INTEGER NOT NULL, fechaEpochMillis INTEGER NOT NULL, nota TEXT NOT NULL, FOREIGN KEY(productoId) REFERENCES productos(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_ajustes_inventario_productoId ON ajustes_inventario(productoId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_ajustes_inventario_fechaEpochMillis ON ajustes_inventario(fechaEpochMillis)")
    }
}


val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `auditoria_cargas` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `salidaId` INTEGER NOT NULL, `salidaLineaId` INTEGER NOT NULL, `tipo` TEXT NOT NULL, `productoAnteriorId` INTEGER NOT NULL, `productoAnteriorNombre` TEXT NOT NULL, `productoNuevoId` INTEGER NOT NULL, `productoNuevoNombre` TEXT NOT NULL, `cantidadAnterior` INTEGER NOT NULL, `cantidadNueva` INTEGER NOT NULL, `fechaEpochMillis` INTEGER NOT NULL, `nota` TEXT NOT NULL)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_auditoria_cargas_salidaLineaId` ON `auditoria_cargas` (`salidaLineaId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_auditoria_cargas_salidaId` ON `auditoria_cargas` (`salidaId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_auditoria_cargas_fechaEpochMillis` ON `auditoria_cargas` (`fechaEpochMillis`)")
    }
}
