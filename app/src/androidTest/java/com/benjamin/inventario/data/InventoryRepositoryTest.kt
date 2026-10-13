package com.benjamin.inventario.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.benjamin.inventario.data.local.InventoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
            repo.saveProduct("Arroz", "caja", 1, 20)
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
            repo.saveProduct("Arroz", "caja", 1, 20)
            val product = repo.products.first().single()
            repo.receive(product.id, 2)
            repo.addDriver("Nombre incorrecto")
            val driver = repo.allDrivers.first().single()

            repo.updateDriver(driver.id, "Nombre corregido")
            repo.issue(product.id, 1, repo.allDrivers.first().single())
            repo.deleteDriver(driver.id)

            assertTrue(repo.allDrivers.first().isEmpty())
            assertNull(scalarNullableText(db, "SELECT repartidorId FROM salidas WHERE id=1"))
            assertEquals("Nombre corregido", scalarText(db, "SELECT repartidorNombre FROM salidas WHERE id=1"))
        } finally {
            db.close()
        }
    }

    @Test
    fun resetClearsProductsMovementsRoutesAndMetrics() = runBlocking {
        val db = database()
        try {
            val repo = InventoryRepository(db)
            repo.saveProduct("Arroz", "caja", 1, 20)
            val product = repo.products.first().single()
            repo.receive(product.id, 10)
            repo.issue(product.id, 3, null)
            repo.configureRouteDay("2026-01-01", 2)

            repo.resetAllData()

            listOf("productos", "entradas", "lotes", "salidas", "salida_lineas", "salida_lotes", "devoluciones", "repartidores", "trailers", "jornadas_rutas", "rutas_diarias").forEach { table ->
                assertEquals("$table should be empty", 0, scalar(db, "SELECT COUNT(*) FROM `$table`"))
            }
            assertEquals(0L, repo.movements(Period.MES).first())
            assertTrue(repo.ranking(Period.MES).first().isEmpty())
        } finally {
            db.close()
        }
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
