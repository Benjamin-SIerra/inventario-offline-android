package com.benjamin.inventario.data

import androidx.room.withTransaction
import com.benjamin.inventario.data.local.*
import com.benjamin.inventario.domain.generarNumerosDeRuta
import com.benjamin.inventario.domain.pendienteTrasDevolucion
import com.benjamin.inventario.domain.planificarFifo
import com.benjamin.inventario.domain.roundedDailyConsumption
import com.benjamin.inventario.domain.roundedDemandForecast
import com.benjamin.inventario.domain.suggestedOrderQuantity
import com.benjamin.inventario.domain.weekCycle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class InventoryRepository(private val db: InventoryDatabase) {
    private val dao = db.dao()

    val products = dao.productos()
    val drivers = dao.repartidores()
    val allDrivers = dao.todosLosRepartidores()
    val trailers = dao.trailers()
    val trailerDays = dao.diasTrailer()
    val lowStock = dao.bajoStock()
    val outOfStock = dao.agotados()
    val receiptsToday = dao.entradasHoy(todayStart(), todayEnd())
    val issuesToday = dao.salidasHoy(todayStart(), todayEnd())
    val recentIssues = dao.salidasRecientes()
    val recentReceipts = dao.entradasRecientes()
    val weekStartDay:Flow<DayOfWeek> = dao.configuracionSemanal().map { DayOfWeek.of((it?.inicioSemana ?: 1).coerceIn(1,7)) }
    suspend fun saveWeekStart(day:DayOfWeek) = dao.guardarConfiguracionSemanal(ConfiguracionSemanalEntity(inicioSemana=day.value))
    fun issuesForDriver(driver:RepartidorEntity) = dao.salidasPorRepartidor(driver.id,driver.nombre)
    fun receiptsBetween(start:Long,end:Long) = dao.entradasEnRango(start,end)
    fun issuesBetween(start:Long,end:Long) = dao.salidasEnRango(start,end)
    fun returnsBetween(start:Long,end:Long) = dao.devolucionesEnRango(start,end)
    fun adjustmentsBetween(start:Long,end:Long) = dao.ajustesEnRango(start,end)

    suspend fun returnUnits(lineId: Long, quantity: Long) = db.withTransaction {
        val line = dao.lineaSalida(lineId) ?: error("Entrega no encontrada")
        pendienteTrasDevolucion(line.cantidad, dao.totalDevueltoLinea(lineId), quantity)
        val allocations = dao.lotesSalida(lineId)
        var remaining = quantity

        for (allocation in allocations.filter { it.loteId != null }) {
            if (remaining == 0L) break
            val lotId = allocation.loteId ?: continue
            val capacity = allocation.cantidad - dao.totalDevuelto(lineId, lotId)
            if (capacity <= 0L) continue
            val returned = minOf(remaining, capacity)
            check(dao.restaurarLote(lotId, returned) == 1)
            dao.insertarDevolucion(DevolucionEntity(
                salidaId = line.salidaId,
                salidaLineaId = lineId,
                loteId = lotId,
                cantidad = returned,
                fechaEpochMillis = System.currentTimeMillis()
            ))
            remaining -= returned
        }

        if (remaining > 0L) {
            val returnedOnLinkedLots = allocations.mapNotNull { it.loteId }
                .distinct().sumOf { dao.totalDevuelto(lineId, it) }
            val alreadyReturnedUnlinked = (dao.totalDevueltoLinea(lineId) - returnedOnLinkedLots).coerceAtLeast(0L)
            val detachedCapacity = allocations.filter { it.loteId == null }.sumOf { it.cantidad } - alreadyReturnedUnlinked
            check(remaining <= detachedCapacity) { "La entrega no tiene saldo de lote suficiente para asignar la devolución" }
            val correctionLotId = createCorrectionLot(line.productoId, remaining, "Devolución sin lote original")
            dao.insertarDevolucion(DevolucionEntity(
                salidaId = line.salidaId,
                salidaLineaId = lineId,
                loteId = correctionLotId,
                cantidad = remaining,
                fechaEpochMillis = System.currentTimeMillis(),
                nota = "Devolución registrada después de eliminar el lote original"
            ))
            remaining = 0L
        }

        check(remaining == 0L) { "La entrega no tiene saldo de lote suficiente para asignar la devolución" }
        check(dao.ajustarStock(line.productoId, quantity) == 1)
    }

    fun movements(period: Period, weekStart:DayOfWeek=DayOfWeek.MONDAY) = period.range(weekStart).let { dao.unidadesMovidas(it.first,it.second) }
    fun ranking(period: Period, weekStart:DayOfWeek=DayOfWeek.MONDAY) = period.range(weekStart).let { dao.ranking(it.first,it.second) }
    fun routeDay(date: String) = dao.jornadaRutas(date)
    fun routesForDay(date: String, count: Int) = dao.rutasDiarias(date, count)

    suspend fun configureRouteDay(date: String, count: Int) = db.withTransaction {
        LocalDate.parse(date)
        val numbers = generarNumerosDeRuta(count)
        dao.guardarJornadaRutas(JornadaRutasEntity(fecha = date, cantidadRutas = count))
        dao.insertarRutasDiarias(numbers.map { RutaDiariaEntity(fecha = date, numeroRuta = it) })
    }

    suspend fun setRouteCompleted(date: String, number: Int, completed: Boolean) {
        require(number in 1..1000)
        check(dao.marcarRuta(date, number, completed) == 1) { "La ruta ya no existe en esta jornada" }
    }

    suspend fun saveProduct(name:String,code:String,unit:String,min:Long,max:Long,initialStock:Long=0L):Long = db.withTransaction {
        require(name.isNotBlank() && min >= 0L && max >= min && initialStock >= 0L)
        val id=dao.crearProducto(ProductoEntity(nombre=name.trim(),codigo=code.trim(),unidad=unit.trim().ifBlank { "unidad" },minimo=min,maximo=max))
        if(initialStock>0L){ createCorrectionLot(id,initialStock,"Inventario inicial desde ficha de producto"); check(dao.ajustarStock(id,initialStock)==1); dao.insertarAjusteInventario(AjusteInventarioEntity(productoId=id,productoNombre=name.trim(),cantidadDelta=initialStock,fechaEpochMillis=System.currentTimeMillis(),nota="Inventario inicial desde ficha de producto")) }
        id
    }

    suspend fun updateProduct(id:Long,name:String,code:String,unit:String,min:Long,max:Long,stock:Long) = db.withTransaction {
        require(name.isNotBlank() && min>=0L && max>=min && stock>=0L)
        val product=dao.producto(id)?:error("Producto no encontrado")
        val delta=stock-product.cantidadActual
        if(delta>0L){createCorrectionLot(id,delta,"Ajuste directo desde ficha de producto");check(dao.ajustarStock(id,delta)==1)}
        else if(delta<0L){
            var remaining=-delta
            val lots=dao.lotesDisponiblesParaAjuste(id)
            check(lots.sumOf { it.cantidadDisponible }>=remaining){"No se puede reducir el stock por debajo de las unidades asignadas a repartidores"}
            for(lot in lots){if(remaining==0L)break;val amount=minOf(remaining,lot.cantidadDisponible);check(dao.reducirLoteDisponible(lot.id,amount)==1);remaining-=amount}
            check(remaining==0L&&dao.ajustarStock(id,delta)==1)
        }
        if(delta!=0L)dao.insertarAjusteInventario(AjusteInventarioEntity(productoId=id,productoNombre=product.nombre,cantidadDelta=delta,fechaEpochMillis=System.currentTimeMillis(),nota="Ajuste directo desde ficha de producto"))
        check(dao.actualizarProducto(id,name.trim(),code.trim(),unit.trim().ifBlank { "unidad" },min,max)==1)
    }

    suspend fun deleteProduct(id:Long) = db.withTransaction {
        val product=dao.producto(id)?:error("Producto no encontrado")
        check(product.cantidadActual==0L){"Ajusta primero el stock a cero antes de eliminar el producto"}
        // Keep referenced catalog rows as inactive so historic movements and lot foreign keys remain valid.
        if(dao.referenciasProducto(id)>0) check(dao.archivarProducto(id)==1)
        else check(dao.archivarProducto(id)==1)
    }

    suspend fun addDriver(name: String) {
        require(name.isNotBlank())
        dao.crearRepartidor(RepartidorEntity(nombre = name.trim()))
    }

    suspend fun updateDriver(id: Long, name: String) {
        require(name.isNotBlank())
        check(dao.actualizarRepartidor(id, name.trim()) == 1) { "El repartidor ya no existe" }
    }

    suspend fun deleteDriver(id: Long) {
        check(dao.eliminarRepartidor(id) == 1) { "El repartidor ya no existe" }
    }

    suspend fun scheduleTrailer(day: Int, enabled: Boolean) = db.withTransaction {
        require(day in 1..7)
        dao.guardarTrailer(TrailerProgramadoEntity(id = day.toLong(), diaSemana = day, activo = enabled))
    }

    suspend fun receive(productId: Long, quantity: Long, date: Long = System.currentTimeMillis()) = db.withTransaction {
        require(quantity > 0L)
        val product = dao.producto(productId) ?: error("Producto no encontrado")
        val entryId = dao.insertarEntrada(EntradaEntity(fechaEpochMillis = date))
        dao.insertarLote(LoteEntity(
            productoId = product.id,
            entradaId = entryId,
            recibidoEnEpochMillis = date,
            cantidadInicial = quantity,
            cantidadDisponible = quantity
        ))
        check(dao.ajustarStock(productId, quantity) == 1)
    }

    suspend fun issue(productId: Long, quantity: Long, driver: RepartidorEntity?, date: Long = System.currentTimeMillis()): List<Pair<Long, Long>> =
        issueBatch(listOf(productId to quantity),driver,date).flatMap { it.second }

    suspend fun issueBatch(lines:List<Pair<Long,Long>>,driver:RepartidorEntity?,date:Long=System.currentTimeMillis()):List<Pair<Long,List<Pair<Long,Long>>>> = db.withTransaction {
        require(lines.isNotEmpty()) { "Selecciona al menos un producto" }
        require(lines.all { it.second>0L }) { "Todas las cantidades deben ser mayores que cero" }
        require(lines.map { it.first }.distinct().size==lines.size) { "No repitas productos; agrega toda la cantidad en una sola línea" }
        val prepared=lines.map { (productId,quantity)->
            val product=dao.producto(productId)?:error("Producto no encontrado")
            check(product.activo){"${product.nombre} está inactivo"}
            val lots=dao.lotesFifo(productId)
            val total=lots.sumOf { it.cantidadDisponible }
            require(total>=quantity){"Stock insuficiente para ${product.nombre}: hay $total ${product.unidad}"}
            Triple(product,quantity,planificarFifo(lots,quantity).map { it.loteId to it.cantidad })
        }
        val total=prepared.fold(0L){acc,item->Math.addExact(acc,item.second)}
        val saleId=dao.insertarSalida(SalidaEntity(repartidorId=driver?.id,repartidorNombre=driver?.nombre,fechaEpochMillis=date,cantidadTotal=total))
        prepared.map { (product,quantity,allocations)->
            val lineId=dao.insertarLinea(SalidaLineaEntity(salidaId=saleId,productoId=product.id,productoNombre=product.nombre,productoCodigo=product.codigo,cantidad=quantity,fechaEpochMillis=date))
            allocations.forEach { (lotId,amount)->
                check(dao.descontarLote(lotId,amount)==1){"El lote cambió; inténtalo de nuevo"}
                dao.insertarAsignacion(SalidaLoteEntity(salidaLineaId=lineId,loteId=lotId,cantidad=amount))
            }
            check(dao.ajustarStock(product.id,-quantity)==1)
            product.id to allocations
        }
    }

    suspend fun editIssue(lineId: Long, productId: Long, quantity: Long) = db.withTransaction {
        require(quantity > 0L) { "La cantidad debe ser mayor que cero" }
        val line = dao.lineaSalida(lineId) ?: error("Entrega no encontrada")
        check(dao.totalDevueltoLinea(lineId) == 0L) { "No se puede editar una entrega que ya tiene devoluciones registradas" }
        val oldAllocations = dao.lotesSalida(lineId)
        restoreIssuedStock(line, oldAllocations)
        check(dao.eliminarAsignacionesLinea(lineId) == oldAllocations.size)
        val target = dao.producto(productId) ?: error("Producto no encontrado")
        check(target.activo) { "El producto seleccionado está inactivo" }
        val allocations = planificarFifo(dao.lotesFifo(productId), quantity)
        allocations.forEach { allocation ->
            check(dao.descontarLote(allocation.loteId, allocation.cantidad) == 1) { "El lote cambió; inténtalo de nuevo" }
            dao.insertarAsignacion(SalidaLoteEntity(salidaLineaId = lineId, loteId = allocation.loteId, cantidad = allocation.cantidad))
        }
        check(dao.ajustarStock(productId, -quantity) == 1)
        check(dao.actualizarLineaSalida(lineId, productId, target.nombre, target.codigo, quantity) == 1)
        check(dao.recalcularTotalSalida(line.salidaId)==1)
        val changedProduct = line.productoId != productId
        val changedQuantity = line.cantidad != quantity
        if (changedProduct || changedQuantity) {
            dao.insertarAuditoriaCarga(AuditoriaCargaEntity(
                salidaId = line.salidaId,
                salidaLineaId = lineId,
                tipo = if (changedProduct) "CAMBIO_PRODUCTO" else "CORRECCION_CANTIDAD",
                productoAnteriorId = line.productoId,
                productoAnteriorNombre = line.productoNombre,
                productoNuevoId = productId,
                productoNuevoNombre = target.nombre,
                cantidadAnterior = line.cantidad,
                cantidadNueva = quantity,
                fechaEpochMillis = System.currentTimeMillis(),
                nota = if (changedProduct) "Intercambio de producto; devolución al inventario y nueva salida PEPS" else "Corrección de cantidad asignada"
            ))
        }
    }

    suspend fun deleteIssue(lineId: Long) = db.withTransaction {
        val line = dao.lineaSalida(lineId) ?: error("Entrega no encontrada")
        check(dao.totalDevueltoLinea(lineId) == 0L) { "No se puede eliminar una entrega que ya tiene devoluciones registradas" }
        val allocations = dao.lotesSalida(lineId)
        restoreIssuedStock(line, allocations)
        check(dao.eliminarAsignacionesLinea(lineId) == allocations.size)
        check(dao.eliminarLineaSalida(lineId) == 1)
        if (dao.lineasDeSalida(line.salidaId) == 0) check(dao.eliminarSalida(line.salidaId) == 1)
        else check(dao.recalcularTotalSalida(line.salidaId)==1)
    }

    suspend fun editReceipt(lotId: Long, productId: Long, quantity: Long) = db.withTransaction {
        require(quantity > 0L) { "La cantidad debe ser mayor que cero" }
        val lot = dao.lote(lotId) ?: error("Entrada no encontrada")
        val target = dao.producto(productId) ?: error("Producto no encontrado")
        check(target.activo) { "El producto seleccionado está inactivo" }
        check(lot.cantidadDisponible in 0..lot.cantidadInicial) { "El saldo del lote es inválido" }
        val references = dao.referenciasSalidaLote(lotId)
        val netConsumed = lot.cantidadInicial - lot.cantidadDisponible
        check(quantity >= netConsumed) {
            "La cantidad no puede ser menor que las unidades que siguen fuera del almacén ($netConsumed)"
        }
        if (productId != lot.productoId) {
            check(references == 0 && netConsumed == 0L) {
                "No se puede cambiar el producto de una entrada que ya fue asignada a entregas"
            }
            check(dao.ajustarStock(lot.productoId, -lot.cantidadDisponible) == 1)
            check(dao.ajustarStock(productId, quantity) == 1)
            check(dao.actualizarLote(lotId, productId, quantity, quantity) == 1)
        } else {
            val delta = quantity - lot.cantidadInicial
            check(dao.ajustarStock(productId, delta) == 1) { "El ajuste dejaría el stock en un valor inválido" }
            check(dao.actualizarLote(lotId, productId, quantity, lot.cantidadDisponible + delta) == 1)
        }
    }

    suspend fun deleteReceipt(lotId: Long) = db.withTransaction {
        val lot = dao.lote(lotId) ?: error("Entrada no encontrada")
        check(dao.ajustarStock(lot.productoId, -lot.cantidadDisponible) == 1)
        check(dao.eliminarLote(lotId) == 1) { "No se pudo eliminar el lote" }
        if (dao.lotesDeEntrada(lot.entradaId) == 0) check(dao.eliminarEntrada(lot.entradaId) == 1)
    }

    suspend fun resetAllData() = db.withTransaction {
        dao.limpiarDevoluciones()
        dao.limpiarAjustesInventario()
        dao.limpiarAuditoriaCargas()
        dao.limpiarAsignaciones()
        dao.limpiarLineasSalida()
        dao.limpiarSalidas()
        dao.limpiarLotes()
        dao.limpiarEntradas()
        dao.limpiarRutasDiarias()
        dao.limpiarJornadasRutas()
        dao.limpiarTrailers()
        dao.limpiarRepartidores()
        dao.limpiarProductos()
    }

    suspend fun recommendations(): List<OrderSuggestion> {
        val now = System.currentTimeMillis()
        val from = now - 30L * 86_400_000L
        return products.first().mapNotNull { product ->
            val demand = dao.demanda(product.id, from, now)
            val daily = roundedDailyConsumption(demand, 30)
            val forecast = roundedDemandForecast(demand, 30, 7)
            val target = if (product.maximo > 0L) product.maximo else product.minimo
            val order = suggestedOrderQuantity(product.cantidadActual, target, forecast)
            if (order > 0L) OrderSuggestion(product.nombre, product.cantidadActual, target, forecast, order, daily) else null
        }.sortedByDescending { it.suggested }
    }

    private suspend fun restoreIssuedStock(line: SalidaLineaEntity, allocations: List<LoteEntregado>) {
        check(allocations.sumOf { it.cantidad } == line.cantidad) { "No se pudo verificar el stock original de la entrega" }
        allocations.filter { it.loteId != null }.forEach { allocation ->
            check(dao.restaurarLote(allocation.loteId!!, allocation.cantidad) == 1)
        }
        val unlinkedQuantity = allocations.filter { it.loteId == null }.sumOf { it.cantidad }
        if (unlinkedQuantity > 0L) createCorrectionLot(line.productoId, unlinkedQuantity, "Reingreso por corrección de entrega")
        check(dao.ajustarStock(line.productoId, line.cantidad) == 1)
    }

    private suspend fun createCorrectionLot(productId: Long, quantity: Long, note: String): Long {
        val now = System.currentTimeMillis()
        val entryId = dao.insertarEntrada(EntradaEntity(fechaEpochMillis = now, nota = note))
        return dao.insertarLote(LoteEntity(
            productoId = productId,
            entradaId = entryId,
            recibidoEnEpochMillis = now,
            cantidadInicial = quantity,
            cantidadDisponible = quantity
        ))
    }
}

enum class Period {
    SEMANA, MES;

    fun range(weekStart:DayOfWeek=DayOfWeek.MONDAY):Pair<Long,Long>{
        val today=LocalDate.now()
        val cycle=weekCycle(today,weekStart)
        val start=if(this==SEMANA) cycle.start else today.withDayOfMonth(1)
        val end=if(this==SEMANA) cycle.endExclusive else start.plusMonths(1)
        val zone=ZoneId.systemDefault()
        return start.atStartOfDay(zone).toInstant().toEpochMilli() to end.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

data class OrderSuggestion(
    val name: String,
    val stock: Long,
    val target: Long,
    val forecast: Long,
    val suggested: Long,
    val unitsPerDay: Long
)

private fun todayStart() = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
private fun todayEnd() = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
