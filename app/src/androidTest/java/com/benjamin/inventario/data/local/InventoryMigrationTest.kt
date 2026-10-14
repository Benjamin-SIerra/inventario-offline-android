package com.benjamin.inventario.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InventoryMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        InventoryDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrationRemovesContactsPreservesLedgerAndUnlinksDeletedLots() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO productos(id,nombre,unidad,minimo,maximo,cantidadActual,activo) VALUES(1,'Arroz','caja',1,10,4,1)")
            execSQL("INSERT INTO contactos(id,nombre,rol,activo) VALUES(1,'Proveedor legado','PROVEEDOR',1),(2,'Cliente legado','CLIENTE',1)")
            execSQL("INSERT INTO repartidores(id,nombre,activo) VALUES(1,'Repartidor',1)")
            execSQL("INSERT INTO entradas(id,proveedorId,proveedorNombre,fechaEpochMillis,nota) VALUES(1,1,'Proveedor legado',1000,'entrada')")
            execSQL("INSERT INTO lotes(id,productoId,entradaId,recibidoEnEpochMillis,cantidadInicial,cantidadDisponible) VALUES(1,1,1,1000,10,4)")
            execSQL("INSERT INTO salidas(id,destinatarioId,destinatarioNombre,repartidorId,repartidorNombre,fechaEpochMillis,nota) VALUES(1,2,'Cliente legado',1,'Repartidor',2000,'salida')")
            execSQL("INSERT INTO salida_lineas(id,salidaId,productoId,productoNombre,cantidad,fechaEpochMillis) VALUES(1,1,1,'Arroz',6,2000)")
            execSQL("INSERT INTO salida_lotes(id,salidaLineaId,loteId,cantidad) VALUES(1,1,1,6)")
            execSQL("INSERT INTO devoluciones(id,salidaId,salidaLineaId,loteId,cantidad,fechaEpochMillis,nota) VALUES(1,1,1,1,2,3000,'devolución')")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_1_2, MIGRATION_2_3)
        assertEquals(0, migrated.query("SELECT name FROM sqlite_master WHERE type='table' AND name='contactos'").use { it.count })
        assertFalse(columns(migrated, "entradas").any { it == "proveedorId" || it == "proveedorNombre" })
        assertFalse(columns(migrated, "salidas").any { it == "destinatarioId" || it == "destinatarioNombre" })
        assertEquals(1, scalar(migrated, "SELECT COUNT(*) FROM entradas WHERE id=1 AND nota='entrada'"))
        assertEquals(1, scalar(migrated, "SELECT COUNT(*) FROM salida_lineas WHERE id=1 AND cantidad=6"))
        assertEquals(1, scalar(migrated, "SELECT COUNT(*) FROM devoluciones WHERE id=1 AND cantidad=2"))

        migrated.execSQL("DELETE FROM lotes WHERE id=1")
        assertEquals(1, scalar(migrated, "SELECT COUNT(*) FROM salida_lotes WHERE id=1 AND loteId IS NULL"))
        assertEquals(1, scalar(migrated, "SELECT COUNT(*) FROM devoluciones WHERE id=1 AND loteId IS NULL"))
        assertEquals(0, migrated.query("PRAGMA foreign_key_check").use { it.count })
        migrated.close()
    }

    @Test
    fun migration3To4AddsProductCodeWeekSettingAndBatchTotalWithoutLosingRows() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL("INSERT INTO productos(id,nombre,unidad,minimo,maximo,cantidadActual,activo) VALUES(1,'Arroz','caja',1,10,4,1)")
            execSQL("INSERT INTO repartidores(id,nombre,activo) VALUES(1,'Repartidor',1)")
            execSQL("INSERT INTO entradas(id,fechaEpochMillis,nota) VALUES(1,1000,'entrada')")
            execSQL("INSERT INTO lotes(id,productoId,entradaId,recibidoEnEpochMillis,cantidadInicial,cantidadDisponible) VALUES(1,1,1,1000,10,4)")
            execSQL("INSERT INTO salidas(id,repartidorId,repartidorNombre,fechaEpochMillis,nota) VALUES(1,1,'Repartidor',2000,'carga')")
            execSQL("INSERT INTO salida_lineas(id,salidaId,productoId,productoNombre,cantidad,fechaEpochMillis) VALUES(1,1,1,'Arroz',6,2000),(2,1,1,'Arroz',3,2000)")
            close()
        }
        val migrated=helper.runMigrationsAndValidate(TEST_DB,4,true,MIGRATION_3_4)
        assertEquals("",migrated.query("SELECT codigo FROM productos WHERE id=1").use{it.moveToFirst();it.getString(0)})
        assertEquals(1,scalar(migrated,"SELECT COUNT(*) FROM salida_lineas WHERE id=1 AND productoCodigo=''"))
        assertEquals(9,scalar(migrated,"SELECT cantidadTotal FROM salidas WHERE id=1"))
        assertEquals(1,scalar(migrated,"SELECT inicioSemana FROM configuracion_semanal WHERE id=1"))
        assertEquals(1,scalar(migrated,"SELECT COUNT(*) FROM lotes WHERE id=1 AND cantidadDisponible=4"))
        assertEquals(0,migrated.query("PRAGMA foreign_key_check").use{it.count})
        migrated.close()
    }

    private fun columns(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }

    private fun scalar(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    companion object {
        private const val TEST_DB = "inventory-migration-test"
    }
}
