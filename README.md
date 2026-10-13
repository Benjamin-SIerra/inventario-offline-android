# Inventario Local (Android)

Aplicación Android nativa en Kotlin + Jetpack Compose/Material 3. El inventario se guarda en Room/SQLite en el dispositivo, sin permiso INTERNET ni sincronización remota.

## Funciones implementadas
- Alta y lista de productos, unidad, existencia, mínimo/máximo; alertas visuales normal / cerca / bajo / agotado.
- Recepción sin proveedor/contacto y creación de lote PEPS.
- Entregas directas con repartidor opcional; la pantalla no solicita cliente/destinatario. El descuento FIFO y los lotes asignados se registran en una transacción Room.
- Configuración de repartidores con lista, edición y eliminación. Al borrar uno, su nombre histórico se conserva en las entregas.
- Historial local de entradas y entregas. Los lotes se pueden corregir mientras la cantidad no quede por debajo de unidades fuera del almacén. Al eliminar un lote asociado, se resta del stock su saldo disponible; las entregas/devoluciones quedan en el historial desvinculadas del lote. Las entregas con devoluciones siguen protegidas contra edición/eliminación.
- Restablecimiento completo con confirmación: borra productos, stock, movimientos, devoluciones, rutas, repartidores y programación de trailers.
- Calendario semanal de trailers con indicador de días únicos programados.
- Control diario de rutas de surtido: genera rutas numeradas por fecha, permite marcar cada una como surtida y conserva el progreso localmente.
- Dashboard con movimientos de hoy, alertas, comparativa semanal/mensual y gráficas de barras hechas con Compose.
- Recomendación de pedido basada en consumo neto de devoluciones de 30 días y un plazo estimado de 7 días. Consumo diario y pedido sugerido se muestran como unidades enteras con redondeo hacia arriba.
- Los campos numéricos aceptan solo dígitos enteros.

## Arquitectura
`Compose → InventoryViewModel (StateFlow) → InventoryRepository → InventoryDao → Room`. Recepciones, salidas, devoluciones y correcciones se ejecutan en transacciones. La versión 2 añade jornadas/rutas con migración 1→2; la versión 3 elimina la tabla de contactos y columnas de proveedor/destinatario, conserva el resto del historial y permite desvincular referencias de lotes y repartidores mediante `SET NULL`.

## Compilar
Requiere JDK 17 y Android SDK 35. Desde la carpeta del proyecto:

```bash
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
gradle testDebugUnitTest
```

Incluye pruebas unitarias para FIFO, devoluciones, validación de cantidades enteras y redondeo; también pruebas instrumentadas Room para la migración 2→3, el borrado de lotes vinculados, la gestión de repartidores y el reinicio de datos.

## Privacidad y límites actuales
No solicita permiso de red; `allowBackup=false`; los datos son locales, pero SQLite no está cifrado. No agregues datos sensibles sin decidir una estrategia de exportación/cifrado. La compilación de depuración no es un release firmado para Play. La fecha de movimientos se registra al confirmar (hora actual); edición de fechas no está incluida. Las recomendaciones asumen 7 días de plazo de reposición (estimación fija del MVP).

## Próximo antes de producción
- Ejecutar las pruebas instrumentadas en un emulador o teléfono y añadir pruebas Compose de UI/accesibilidad.
- Exportación/restauración local segura y elección de clave/cifrado.
- Editar/archivar catálogo y fecha manual de movimiento.
- Ajustar el plazo estimado de reposición y firmar una variante release para Play.
