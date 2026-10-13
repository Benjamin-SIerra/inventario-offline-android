# Inventario Local (Android)

Aplicación Android nativa en Kotlin + Jetpack Compose/Material 3. El inventario se guarda en Room/SQLite en el dispositivo, sin permiso INTERNET ni sincronización remota.

## Funciones implementadas
- Alta y lista de productos, unidad, existencia, mínimo/máximo; alertas visuales normal / cerca / bajo / agotado.
- Recepción con proveedor opcional, fecha/hora del registro y lote PEPS.
- Entregas directas a repartidores (repartidor opcional); el formulario no solicita cliente/destinatario. Prevalidación del stock, descuento FIFO con lote más antiguo primero y registro de lotes asignados, todo en transacción Room.
- Historial local de entradas y entregas. Se pueden editar o eliminar con ajuste de stock atómico: las entregas con devoluciones no se editan/eliminan; el lote de una entrada asociada a entregas no se elimina y no permite cambiar el producto.
- Contactos de tipo proveedor/cliente/ambos y catálogo de repartidores.
- Calendario semanal de trailers con indicador de días únicos programados.
- Control diario de rutas de surtido: genera rutas numeradas por fecha, permite marcar cada una como surtida y conserva el progreso localmente.
- Dashboard con movimientos de hoy, alertas, comparativa semanal/mensual y gráficas de barras hechas con Compose.
- Recomendación de pedido basada en salidas de 30 días y estimación de plazo de 7 días; la UI explica que es una estimación.

## Arquitectura
`Compose → InventoryViewModel (StateFlow) → InventoryRepository → InventoryDao → Room`. Recepciones, salidas, devoluciones y correcciones de movimientos se ejecutan en transacciones; los decrementos FIFO están protegidos por guardas de saldo. Las devoluciones se validan contra el pendiente acumulado y el historial muestra cuando queda en cero. La versión 2 añade jornadas y checklist de rutas mediante migración Room 1→2 sin borrar los datos existentes.

## Compilar
Requiere JDK 17 y Android SDK 35. Desde la carpeta del proyecto:

```bash
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
gradle testDebugUnitTest
```

El repositorio contiene pruebas unitarias del planificador FIFO y de los límites de devolución (devolución total exacta, parcial y exceso). Aún se requieren pruebas instrumentadas Room para verificar todas las transacciones de edición/eliminación y probar migraciones en dispositivo.

## Privacidad y límites actuales
No solicita permiso de red; `allowBackup=false`; los datos son locales, pero SQLite no está cifrado. No agregues datos sensibles sin decidir una estrategia de exportación/cifrado. La compilación de depuración no es un release firmado para Play. La fecha de movimientos se registra al confirmar (hora actual); edición de fechas y unidades fraccionarias no están incluidas. Las recomendaciones asumen 7 días de plazo de reposición (estimación fija del MVP).

## Próximo antes de producción
- Pruebas instrumentadas Room para recepción/salida/devolución y actualización de esquema, más pruebas de UI y corrida en teléfono.
- Exportación/restauración local segura y elección de clave/cifrado.
- Editar/archivar catálogo y fecha manual de movimiento.
- Ajustes configurables del plazo de proveedor y unidades fraccionarias.
- Firmar variante release y revisión de accesibilidad con TalkBack/tamaño de fuente.
