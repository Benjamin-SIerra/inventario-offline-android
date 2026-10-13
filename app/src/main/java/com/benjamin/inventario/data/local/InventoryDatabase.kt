package com.benjamin.inventario.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao interface InventoryDao {
 @Query("SELECT * FROM productos WHERE activo=1 ORDER BY nombre COLLATE NOCASE") fun productos():Flow<List<ProductoEntity>>
 @Query("SELECT * FROM productos WHERE id=:id") suspend fun producto(id:Long):ProductoEntity?
 @Insert suspend fun crearProducto(p:ProductoEntity):Long
 @Query("UPDATE productos SET cantidadActual=cantidadActual+:delta WHERE id=:id AND cantidadActual+:delta>=0") suspend fun ajustarStock(id:Long,delta:Long):Int
 @Query("SELECT * FROM contactos WHERE activo=1 ORDER BY nombre") fun contactos():Flow<List<ContactoEntity>>
 @Insert suspend fun crearContacto(c:ContactoEntity):Long
 @Query("SELECT * FROM repartidores WHERE activo=1 ORDER BY nombre") fun repartidores():Flow<List<RepartidorEntity>>
 @Insert suspend fun crearRepartidor(r:RepartidorEntity):Long
 @Query("SELECT * FROM trailers WHERE activo=1 ORDER BY diaSemana") fun trailers():Flow<List<TrailerProgramadoEntity>>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun guardarTrailer(t:TrailerProgramadoEntity)
 @Query("SELECT COUNT(DISTINCT diaSemana) FROM trailers WHERE activo=1") fun diasTrailer():Flow<Int>
 @Query("SELECT COUNT(*) FROM productos WHERE activo=1 AND cantidadActual=0") fun agotados():Flow<Int>
 @Query("SELECT COUNT(*) FROM productos WHERE activo=1 AND cantidadActual>0 AND cantidadActual<=minimo") fun bajoStock():Flow<Int>
 @Query("SELECT COALESCE(SUM(cantidad),0) FROM salida_lineas WHERE fechaEpochMillis>=:desde AND fechaEpochMillis<:hasta") fun unidadesMovidas(desde:Long,hasta:Long):Flow<Long>
 @Query("SELECT productoId,productoNombre,SUM(cantidad) unidades FROM salida_lineas WHERE fechaEpochMillis>=:desde AND fechaEpochMillis<:hasta GROUP BY productoId ORDER BY unidades DESC LIMIT 10") fun ranking(desde:Long,hasta:Long):Flow<List<ProductoMovido>>
 @Query("SELECT id,entradaId,recibidoEnEpochMillis,cantidadDisponible FROM lotes WHERE productoId=:pid AND cantidadDisponible>0 ORDER BY recibidoEnEpochMillis ASC,id ASC") suspend fun lotesFifo(pid:Long):List<LoteFIFO>
 @Query("UPDATE lotes SET cantidadDisponible=cantidadDisponible-:n WHERE id=:id AND cantidadDisponible>=:n") suspend fun descontarLote(id:Long,n:Long):Int
 @Query("UPDATE lotes SET cantidadDisponible=cantidadDisponible+:n WHERE id=:id") suspend fun restaurarLote(id:Long,n:Long):Int
 @Insert suspend fun insertarEntrada(e:EntradaEntity):Long
 @Insert suspend fun insertarLote(l:LoteEntity):Long
 @Insert suspend fun insertarSalida(s:SalidaEntity):Long
 @Insert suspend fun insertarLinea(l:SalidaLineaEntity):Long
 @Insert suspend fun insertarAsignacion(a:SalidaLoteEntity)
 @Insert suspend fun insertarDevolucion(d:DevolucionEntity)
 @Query("SELECT COALESCE(SUM(cantidad),0) FROM devoluciones WHERE salidaLineaId=:line AND loteId=:lot") suspend fun totalDevuelto(line:Long,lot:Long):Long
 @Query("SELECT loteId,cantidad,0 AS recibidoEnEpochMillis FROM salida_lotes WHERE salidaLineaId=:line ORDER BY id") suspend fun lotesSalida(line:Long):List<LoteEntregado>
 @Query("SELECT * FROM salida_lineas WHERE id=:id") suspend fun lineaSalida(id:Long):SalidaLineaEntity?
 @Query("SELECT salida_lineas.id AS lineaId,salidas.id AS salidaId,productoId,productoNombre,salida_lineas.cantidad,salidas.repartidorNombre,salida_lineas.fechaEpochMillis FROM salida_lineas JOIN salidas ON salidas.id=salida_lineas.salidaId ORDER BY salida_lineas.fechaEpochMillis DESC LIMIT 30") fun salidasRecientes():Flow<List<SalidaReciente>>
 @Query("SELECT COALESCE(SUM(cantidad),0) FROM salida_lineas WHERE productoId=:pid AND fechaEpochMillis>=:desde AND fechaEpochMillis<:hasta") suspend fun demanda(pid:Long,desde:Long,hasta:Long):Long
 @Query("SELECT COUNT(*) FROM entradas WHERE fechaEpochMillis>=:desde AND fechaEpochMillis<:hasta") fun entradasHoy(desde:Long,hasta:Long):Flow<Int>
 @Query("SELECT COUNT(*) FROM salidas WHERE fechaEpochMillis>=:desde AND fechaEpochMillis<:hasta") fun salidasHoy(desde:Long,hasta:Long):Flow<Int>
 @Query("SELECT * FROM jornadas_rutas WHERE fecha=:fecha LIMIT 1") fun jornadaRutas(fecha:String):Flow<JornadaRutasEntity?>
 @Query("SELECT * FROM rutas_diarias WHERE fecha=:fecha AND numeroRuta<=:cantidad ORDER BY numeroRuta") fun rutasDiarias(fecha:String,cantidad:Int):Flow<List<RutaDiariaEntity>>
 @Upsert suspend fun guardarJornadaRutas(jornada:JornadaRutasEntity)
 @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertarRutasDiarias(rutas:List<RutaDiariaEntity>)
 @Query("UPDATE rutas_diarias SET completada=:completada WHERE fecha=:fecha AND numeroRuta=:numeroRuta") suspend fun marcarRuta(fecha:String,numeroRuta:Int,completada:Boolean):Int
}
@Database(entities=[ProductoEntity::class,ContactoEntity::class,RepartidorEntity::class,EntradaEntity::class,LoteEntity::class,SalidaEntity::class,SalidaLineaEntity::class,SalidaLoteEntity::class,DevolucionEntity::class,TrailerProgramadoEntity::class,JornadaRutasEntity::class,RutaDiariaEntity::class],version=2,exportSchema=true)
abstract class InventoryDatabase:RoomDatabase(){abstract fun dao():InventoryDao}

val MIGRATION_1_2: Migration = object : Migration(1,2) {
 override fun migrate(database:SupportSQLiteDatabase) {
  database.execSQL("CREATE TABLE IF NOT EXISTS `jornadas_rutas` (`fecha` TEXT NOT NULL, `cantidadRutas` INTEGER NOT NULL, PRIMARY KEY(`fecha`))")
  database.execSQL("CREATE TABLE IF NOT EXISTS `rutas_diarias` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fecha` TEXT NOT NULL, `numeroRuta` INTEGER NOT NULL, `completada` INTEGER NOT NULL, FOREIGN KEY(`fecha`) REFERENCES `jornadas_rutas`(`fecha`) ON UPDATE NO ACTION ON DELETE CASCADE )")
  database.execSQL("CREATE INDEX IF NOT EXISTS `index_rutas_diarias_fecha` ON `rutas_diarias` (`fecha`)")
  database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_rutas_diarias_fecha_numeroRuta` ON `rutas_diarias` (`fecha`, `numeroRuta`)")
 }
}
