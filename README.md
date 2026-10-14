# Inventario Local (Android)

Aplicación Android nativa en Kotlin + Jetpack Compose/Material 3. El inventario se guarda en Room/SQLite en el dispositivo, sin permiso INTERNET ni sincronización remota.

## Funciones implementadas
- Alta de productos con código, unidad, stock inicial directo, mínimo/máximo y edición de datos; búsqueda por nombre o código.
- Vistas separadas «Stock Normal» y «Stock Bajo»; el mínimo por producto determina y permite configurar el umbral.
- Edición y archivo lógico de productos y repartidores, conservando referencias y movimientos históricos; los productos solo se archivan con existencia cero.
- Recepción sin proveedor/contacto y creación de lote PEPS.
- Entregas directas con repartidor opcional; el descuento FIFO y los lotes asignados se registran en una transacción Room.
- Carga multi-producto: una sola salida transaccional con varias líneas, repartidor, marca de tiempo exacta y total auditado.
- Perfil por repartidor con filtro por nombre/código de producto, cantidades entregadas, devueltas y pendientes.
- Historial semanal consultable por semana, con entradas, entregas, devoluciones y ajustes directos; inicio de semana configurable y fechas visibles.
- Correcciones directas del stock desde la ficha del producto; cada cambio queda como ajuste auditado y las existencias se concilian con los lotes.
- Historial local de entradas y entregas. Los lotes se pueden corregir mientras la cantidad no quede por debajo de unidades fuera del almacén. Al eliminar un lote asociado, se resta del stock su saldo disponible; las entregas/devoluciones quedan en el historial desvinculadas del lote. Las entregas con devoluciones siguen protegidas contra edición/eliminación.
- Restablecimiento completo con confirmación: borra productos, stock, movimientos, ajustes, devoluciones, rutas, repartidores y programación de trailers; conserva la preferencia del día de inicio semanal.
- Calendario semanal de trailers con indicador de días únicos programados.
- Control diario de rutas de surtido: genera rutas numeradas por fecha, permite marcar cada una como surtida y conserva el progreso localmente.
- Dashboard con movimientos de hoy, alertas, comparativa semanal/mensual y gráficas de barras hechas con Compose.
- Recomendación de pedido basada en consumo neto de devoluciones de 30 días y un plazo estimado de 7 días. Consumo diario y pedido sugerido se muestran como unidades enteras con redondeo hacia arriba.
- Los campos numéricos aceptan solo dígitos enteros.

## Arquitectura
`Compose → InventoryViewModel (StateFlow) → InventoryRepository → InventoryDao → Room`. Recepciones, salidas, devoluciones, ajustes y cargas multi-producto se ejecutan en transacciones. La versión 4 migra el código de producto, conserva los registros existentes, añade configuración del inicio semanal, auditoría de totales por salida y ajustes directos.

## Compilar
Requiere JDK 17 y Android SDK 35. Desde la carpeta del proyecto:

```bash
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
gradle testDebugUnitTest
```

Incluye pruebas unitarias para FIFO, devoluciones, cantidades, búsquedas, clasificación de stock y ciclos semanales; pruebas instrumentadas Room para migraciones, lotes, repartidores, stock inicial, ajustes y salidas multi-producto. Verifica el SQL Room 3→4 sin dispositivo con `python scripts/verify_inventory_migration.py`; compila instrumentación con `:app:compileDebugAndroidTestKotlin`.

## Privacidad y límites actuales
No solicita permiso de red; `allowBackup=false`; los datos son locales, pero SQLite no está cifrado. No agregues datos sensibles sin decidir una estrategia de exportación/cifrado. La compilación de depuración no es un release firmado para Play. La fecha de movimientos se registra al confirmar (hora actual); edición de fechas no está incluida. Las recomendaciones asumen 7 días de plazo de reposición (estimación fija del MVP).

## Próximo antes de producción
- Ejecutar las pruebas instrumentadas en un emulador o teléfono y añadir pruebas Compose de UI/accesibilidad.
- Exportación/restauración local segura y elección de clave/cifrado.
- Agregar edición manual de fechas de movimientos si se requiere.
- Ajustar el plazo estimado de reposición y firmar una variante release para Play.
