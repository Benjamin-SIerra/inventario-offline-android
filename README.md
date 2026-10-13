# Inventario Local — diseño base Android

Base de arquitectura y lógica de inventario offline, en español. Room/SQLite es la fuente de verdad; Compose observa `Flow` y toda operación de stock se realiza en transacciones Room. No se configura INTERNET ni sincronización remota.

## Decisiones de dominio
- Productos almacenan cantidad derivada de lotes disponibles; el valor se recalcula y contrasta para detectar corrupción.
- Cada recepción crea un lote con saldo propio y fecha/hora de recepción. FIFO ordena por `recibidoEn ASC, id ASC` (desempate determinista).
- Cada retiro es un documento con destinatario (cliente/proveedor), repartidor opcional y fecha. Sus líneas enlazan producto y guardan asignaciones por lote (`SalidaLoteEntity`) para trazabilidad y devoluciones.
- No se borran movimientos contabilizados: se corrigen con reversa/devolución auditada. Identificadores de destino guardan ID y snapshot de nombre.
- Cantidades: `Long` en unidades base enteras; artículos fraccionables requieren unidad y escala explícitas.

## Capas
`Compose UI → ViewModel (StateFlow / eventos) → casos de uso → repositorio → DAO Room → SQLite`.
Room/DAO no se llaman desde UI. El caso de uso FIFO y escritura del movimiento están dentro de una única `withTransaction`. Room emite cambios reactivos. Paquetes recomendados: `data/local/{entity,dao,db}`, `data/repository`, `domain/{model,usecase}`, `ui/{dashboard,products,movements,people,analytics,settings}`.

## Modelo Room
- `ProductoEntity`: catálogo y umbrales mínimo/máximo.
- `LoteEntity`: recepciones y saldo actual del lote.
- `EntradaEntity` y `EntradaLineaEntity`: cabecera de recepción y líneas; la recepción crea lotes.
- `SalidaEntity`, `SalidaLineaEntity`, `SalidaLoteEntity`: retiro, líneas y consumo FIFO por lote.
- `ContactoEntity`: proveedor/cliente tipado (el mismo negocio puede tener ambos roles).
- `RepartidorEntity`.
- `DevolucionEntity` + `DevolucionLineaEntity`: vínculo a salida/lote de origen; restaura ese lote (auditoría preservada).
- `TrailerProgramadoEntity`: día semanal, activo y nota; el indicador cuenta días únicos programados (0–7).

Relaciones: producto 1:N lotes/lineas; entrada 1:N líneas; salida 1:N líneas; salida-línea 1:N consumos de lote; contacto/repartidor 1:N salidas; devolución referencia salida y lote original. Índices en todas las FK, producto/fecha y fecha/estado.

## Reglas de negocio
1. Recepción exige cantidad > 0, producto activo y fecha explícita; transacción inserta encabezado/línea/lote y actualiza cantidad de catálogo.
2. Salida valida todas las líneas antes de insertar; si cualquier producto no tiene saldo suficiente, aborta todo. FIFO consume solo lotes con saldo > 0, por fecha de recepción ascendente; se registra cada cantidad consumida.
3. `cantidadActual = SUM(lote.cantidadDisponible)`; máximo deseado es objetivo de reabastecimiento, no una restricción para recibir.
4. Estado: agotado si stock=0; bajo si stock <= mínimo; cerca de mínimo configurable (por defecto cuando stock <= mínimo + max(1, ceil(mínimo*0.2))); normal de otro modo. La interfaz distingue colores e iconos y texto, no solo color.
5. Recomendación: `pedido = max(0, objetivoReposicion - stockActual - entradasAbiertas + demandaPrevistaDurantePlazo)`. Velocidad = salidas unidades/día en ventana configurable, usando días calendario; sin historial, sugerir solo faltante hasta objetivo. La predicción es orientativa y explica su cálculo.
6. Analítica suma cantidades de `SalidaLineaEntity` por producto y periodo; filtros semana/mes utilizan zona local y límites [inicio, inicio siguiente), con índice por fecha.
7. Notificaciones locales opcionales: solicitud de permiso y configuración; nunca dependen de red.

## Esquema conceptual SQL
Ver entities Kotlin. Claves foráneas usan RESTRICT para movimientos auditables; catálogo se archiva con `activo=false`, no se elimina. Migraciones Room versionadas y probadas con MigrationTestHelper; nunca `fallbackToDestructiveMigration` en producción.

## Privacidad y resiliencia
La app funciona íntegramente sin permiso de internet. Backup Android debe ser una decisión explícita: ofrecer exportación local cifrada/CSV con advertencia de datos sensibles; no afirmar que `allowBackup=false` equivale a cifrado. Preferir cifrado SQLCipher solo si se define manejo de clave y recuperación; proteger archivos de exportación. Añadir exportación/restauración verificada antes de uso productivo.

## UI/UX
- Inicio con cuatro tarjetas grandes: Existencias, Bajo/agotado, Entradas hoy, Salidas hoy; botón flotante `+ Movimiento` abre dos acciones claras: Recibir / Entregar.
- Formularios progresivos: producto → cantidad → fecha; para entrega agregar destinatario y repartidor. Búsqueda inmediata, lector de código de barras como mejora futura, no requisito.
- Confirmación previa con resumen (“Retirar 8; FIFO: 5 del lote 12-mar y 3 del lote 18-mar”) y saldo resultante; selección manual de lotes solo con permiso avanzado.
- Deshacer limitado mediante devolución/reversa, nunca editar silenciosamente historial; comprobante y detalle muestran quién, cuándo, destinatario, repartidor y lotes.
- Analítica con barras semanales/mensuales, ranking y unidades legibles; Vico Compose. Accesibilidad: targets ≥48dp, contraste, tipografía escalable, TalkBack, estados vacíos con acción siguiente.
- Verde normal, ámbar cerca/bajo, rojo agotado; icono y etiqueta redundantes para daltonismo. Navegación inferior máximo 4 destinos: Inicio, Productos, Movimientos, Más.

## Build
Este repositorio contiene el diseño y un ejemplo autocontenido de dominio/Room, no es todavía una aplicación completa instalable. Para producción faltan proyecto Gradle/Compose, migraciones, pantallas, pruebas en dispositivo y revisión de devoluciones/concurrencia.