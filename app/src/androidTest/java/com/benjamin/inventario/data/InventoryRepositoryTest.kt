package com.benjamin.inventario.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.benjamin.inventario.data.local.InventoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InventoryRepositoryTest {
    @Test
    fun deletingLotUnlinksItsDeliveryAndKeepsReturnHistoryUsable() = runBlocking {
        val db = database()
        try {
            val repo = InventoryRepository(db)
            repo.saveProduct("Arroz", "", "caja", 1, 20)
            val product = repo.products.first().single()
            repo.receive(product.id, 10)
            repo.issue(product.id, 4, null)
            val lot = repo.recentReceipts.first().single()
            val issue = repo.recentIssues.first().single()

            repo.deleteReceipt(lot.loteId)

            assertEquals(0L, repo.products.first().single().cantidadActual)
            assertEquals(4L, repo.recentIssues.first().single().cantidad)
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM salida_lotes WHERE salidaLineaId=${issue.lineaId} AND loteId IS NULL"))

            repo.returnUnits(issue.lineaId, 1)

            assertEquals(1L, repo.products.first().single().cantidadActual)
            assertEquals(1L, repo.recentIssues.first().single().devuelto)
            assertTrue(scalar(db, "SELECT COUNT(*) FROM lotes WHERE cantidadDisponible=1") >= 1)
        } finally {
            db.close()
        }
    }

    @Test
    fun driversCanBeEditedAndDeletedWithoutErasingHistoricalName() = runBlocking {
        val db = database()
        try {
            val repo = InventoryRepository(db)
            repo.saveProduct("Arroz", "", "caja", 1, 20)
            val product = repo.products.first().single()
            repo.receive(product.id, 2)
            repo.addDriver("Nombre incorrecto")
            val driver = repo.allDrivers.first().single()

            repo.updateDriver(driver.id, "Nombre corregido")
            repo.issue(product.id, 1, repo.allDrivers.first().single())
            repo.deleteDriver(driver.id)

            assertFalse(repo.allDrivers.first().single().activo)
            assertEquals(1, scalar(db, "SELECT repartidorId FROM salidas WHERE id=1"))
            assertEquals("Nombre corregido", scalarText(db, "SELECT repartidorNombre FROM salidas WHERE id=1"))
        } finally {
            db.close()
        }
    }

    @Test
    fun multiProductDispatchCreatesOneAuditedHeaderAndSnapshotsProductCodes() = runBlocking {
        val db=database()
        try {
            val repo=InventoryRepository(db)
            repo.saveProduct("Arroz","AR-1","caja",1,20,8)
            repo.saveProduct("Frijol","FR-2","bolsa",1,15,10)
            val products=repo.products.first().associateBy{it.nombre}
            repo.addDriver("Repartidor")
            val driver=repo.allDrivers.first().single()
            repo.issueBatch(listOf(products.getValue("Arroz").id to 2L,products.getValue("Frijol").id to 3L),driver,123456789L)

            assertEquals(1,scalar(db,"SELECT COUNT(*) FROM salidas"))
            assertEquals(5,scalar(db,"SELECT cantidadTotal FROM salidas WHERE id=1"))
            assertEquals(123456789,scalar(db,"SELECT fechaEpochMillis FROM salidas WHERE id=1"))
            assertEquals(2,scalar(db,"SELECT COUNT(*) FROM salida_lineas WHERE salidaId=1 AND productoCodigo IN ('AR-1','FR-2')"))
            assertEquals(1,scalar(db,"SELECT COUNT(*) FROM salidas WHERE repartidorId=1"))
            assertEquals(2,repo.issuesForDriver(driver).first().size)
            assertEquals(5L,repo.issuesForDriver(driver).first().sumOf{it.cantidad})
        } finally { db.close() }
    }

    @Test
    fun insufficientLineRollsBackEntireMultiProductDispatch() = runBlocking {
        val db=database()
        try {
            val repo=InventoryRepository(db)
            repo.saveProduct("Arroz","AR-1","caja",1,20,8)
            repo.saveProduct("Frijol","FR-2","bolsa",1,15,2)
            val products=repo.products.first().associateBy{it.nombre}
            val failed=runCatching { repo.issueBatch(listOf(products.getValue("Arroz").id to 2L,products.getValue("Frijol").id to 3L),null) }
            assertTrue(failed.isFailure)
            assertEquals(0,scalar(db,"SELECT COUNT(*) FROM salidas"))
            assertEquals(0,scalar(db,"SELECT COUNT(*) FROM salida_lineas"))
            assertEquals(10L,repo.products.first().sumOf{it.cantidadActual})
        } finally { db.close() }
    }

    @Test
    fun directProductStockAdjustmentKeepsLotsAndAuditInSync() = runBlocking {
        val db=database()
        try {
            val repo=InventoryRepository(db)
            val productId=repo.saveProduct("Arroz","AR-1","caja",1,20,10)
            repo.updateProduct(productId,"Arroz","AR-2","caja",2,25,7)
            assertEquals(7L,repo.products.first().single().cantidadActual)
            assertEquals(7,scalar(db,"SELECT SUM(cantidadDisponible) FROM lotes WHERE productoId=$productId"))
            assertEquals(2,scalar(db,"SELECT COUNT(*) FROM ajustes_inventario WHERE productoId=$productId"))
            assertEquals(-3,scalar(db,"SELECT cantidadDelta FROM ajustes_inventario WHERE cantidadDelta<0"))
            assertEquals("AR-2",repo.products.first().single().codigo)
        } finally { db.close() }
    }

    @Test
    fun resetClearsProductsMovementsRoutesAndMetrics() = runBlocking {
        val db = database()
        try {
            val repo = InventoryRepository(db)
            repo.saveProduct("Arroz", "", "caja", 1, 20)
            val product = repo.products.first().single()
            repo.receive(product.id, 10)
            repo.issue(product.id, 3, null)
            repo.configureRouteDay("2026-01-01", 2)

            repo.resetAllData()

            listOf("productos", "entradas", "lotes", "salidas", "salida_lineas", "salida_lotes", "devoluciones", "repartidores", "trailers", "jornadas_rutas", "rutas_diarias", "ajustes_inventario").forEach { table ->
                assertEquals("$table should be empty", 0, scalar(db, "SELECT COUNT(*) FROM `$table`"))
            }
            assertEquals(0L, repo.movements(Period.MES).first())
            assertTrue(repo.ranking(Period.MES).first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun changingAssignedProductRestoresOriginalLotsUsesFifoAndWritesAudit() = runBlocking {
        val db = database()
        try {
            val repo = InventoryRepository(db)
            val fromId = repo.saveProduct("Papa Fuego", "PF", "bolsa", 1, 20, 5)
            val toId = repo.saveProduct("Papa Queso", "PQ", "bolsa", 1, 20, 6)
            repo.issue(fromId, 3, null)
            val line = repo.recentIssues.first().single()

            repo.editIssue(line.lineaId, toId, 2)

            assertEquals(5L, repo.products.first().first { it.id == fromId }.cantidadActual)
            assertEquals(4L, repo.products.first().first { it.id == toId }.cantidadActual)
            assertEquals("Papa Queso", repo.recentIssues.first().single().productoNombre)
            assertEquals(2L, repo.recentIssues.first().single().cantidad)
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM auditoria_cargas WHERE salidaLineaId=${line.lineaId} AND tipo='CAMBIO_PRODUCTO'"))
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM salida_lotes WHERE salidaLineaId=${line.lineaId}"))
        } finally { db.close() }
    }

    @Test
    fun correctingAssignedQuantityWritesAuditAndKeepsDriverBalance() = runBlocking {
        val db = database()
        try {
            val repo = InventoryRepository(db)
            val productId = repo.saveProduct("Papa Fuego", "PF", "bolsa", 1, 20, 8)
            repo.issue(productId, 5, null)
            val line = repo.recentIssues.first().single()

            repo.editIssue(line.lineaId, productId, 3)

            assertEquals(5L, repo.products.first().single().cantidadActual)
            assertEquals(3L, repo.recentIssues.first().single().pendiente)
            assertEquals(1, scalar(db, "SELECT COUNT(*) FROM auditoria_cargas WHERE salidaLineaId=${line.lineaId} AND tipo='CORRECCION_CANTIDAD'"))
        } finally { db.close() }
    }

    private fun database(): InventoryDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        InventoryDatabase::class.java
    ).allowMainThreadQueries().build()

    private fun scalar(db: InventoryDatabase, sql: String): Int =
        db.openHelper.writableDatabase.query(sql).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private fun scalarText(db: InventoryDatabase, sql: String): String =
        db.openHelper.writableDatabase.query(sql).use { cursor -> cursor.moveToFirst(); cursor.getString(0) }

    private fun scalarNullableText(db: InventoryDatabase, sql: String): String? =
        db.openHelper.writableDatabase.query(sql).use { cursor -> cursor.moveToFirst(); if (cursor.isNull(0)) null else cursor.getString(0) }
}
