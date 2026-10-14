#!/usr/bin/env python3
"""Exercise the checked-in Room 3→4 migration SQL against schema 3."""
from __future__ import annotations

import json
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = ROOT / "app/schemas/com.benjamin.inventario.data.local.InventoryDatabase/3.json"
DATABASE = ROOT / "app/src/main/java/com/benjamin/inventario/data/local/InventoryDatabase.kt"

schema = json.loads(SCHEMA.read_text(encoding="utf-8"))["database"]
source = DATABASE.read_text(encoding="utf-8")
block = source.split("val MIGRATION_3_4", 1)[1]
block = block.split("\n}", 1)[0]
statements = re.findall(r'database\.execSQL\("([^"]+)"\)', block)
if len(statements) != 9:
    raise SystemExit(f"FAIL: expected 9 migration statements, found {len(statements)}")

connection = sqlite3.connect(":memory:")
connection.execute("PRAGMA foreign_keys=ON")
for entity in schema["entities"]:
    connection.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
for entity in schema["entities"]:
    for index in entity.get("indices", []):
        connection.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))

connection.execute("INSERT INTO productos(id,nombre,unidad,minimo,maximo,cantidadActual,activo) VALUES(1,'Arroz','caja',2,20,6,1)")
connection.execute("INSERT INTO repartidores(id,nombre,activo) VALUES(1,'Repartidor',1)")
connection.execute("INSERT INTO entradas(id,fechaEpochMillis,nota) VALUES(1,1000,'carga')")
connection.execute("INSERT INTO lotes(id,productoId,entradaId,recibidoEnEpochMillis,cantidadInicial,cantidadDisponible) VALUES(1,1,1,1000,6,0)")
connection.execute("INSERT INTO salidas(id,repartidorId,repartidorNombre,fechaEpochMillis,nota) VALUES(1,1,'Repartidor',2000,'')")
connection.execute("INSERT INTO salida_lineas(id,salidaId,productoId,productoNombre,cantidad,fechaEpochMillis) VALUES(1,1,1,'Arroz',6,2000)")

for statement in statements:
    connection.execute(statement)

assert connection.execute("SELECT codigo FROM productos WHERE id=1").fetchone() == ("",)
assert connection.execute("SELECT productoCodigo FROM salida_lineas WHERE id=1").fetchone() == ("",)
assert connection.execute("SELECT cantidadTotal FROM salidas WHERE id=1").fetchone() == (6,)
assert connection.execute("SELECT inicioSemana FROM configuracion_semanal WHERE id=1").fetchone() == (1,)
assert connection.execute("SELECT COUNT(*) FROM ajustes_inventario").fetchone() == (0,)
assert connection.execute("PRAGMA foreign_key_check").fetchall() == []
print("PASS: Room 3→4 migration preserves rows and adds code, weekly configuration, adjustment ledger, and batch totals")
