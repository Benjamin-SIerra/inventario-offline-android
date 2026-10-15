package com.benjamin.inventario

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.room.Room
import com.benjamin.inventario.data.local.InventoryDatabase
import com.benjamin.inventario.data.local.MIGRATION_1_2
import com.benjamin.inventario.data.local.MIGRATION_2_3
import com.benjamin.inventario.data.local.MIGRATION_3_4
import com.benjamin.inventario.data.local.MIGRATION_4_5
import com.benjamin.inventario.ui.InventoryApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, InventoryDatabase::class.java, "inventario-local.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFC8A84C), secondary = Color(0xFF4CAF7D),
                background = Color(0xFF101113), surface = Color(0xFF1B1C1F),
                error = Color(0xFFCF6679)
            )) { InventoryApp(db) }
        }
    }
}
