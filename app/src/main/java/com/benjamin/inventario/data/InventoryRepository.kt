package com.benjamin.inventario.data

import androidx.room.withTransaction
import com.benjamin.inventario.data.local.*
import com.benjamin.inventario.domain.generarNumerosDeRuta
import com.benjamin.inventario.domain.pendienteTrasDevolucion
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class InventoryRepository(private val db: InventoryDatabase) {
    private val dao=db.dao()
    val products=dao.productos(); val contacts=dao.contactos(); val drivers=dao.repartidores(); val trailers=dao.trailers(); val trailerDays=dao.diasTrailer(); val lowStock=dao.bajoStock(); val outOfStock=dao.agotados()
    val receiptsToday=dao.entradasHoy(todayStart(),todayEnd()); val issuesToday=dao.salidasHoy(todayStart(),todayEnd()); val recentIssues=dao.salidasRecientes(); val recentReceipts=dao.entradasRecientes()
    suspend fun returnUnits(lineId:Long, quantity:Long){db.withTransaction{
        val line=dao.lineaSalida(lineId)?:error("Entrega no encontrada")
        pendienteTrasDevolucion(line.cantidad,dao.totalDevueltoLinea(lineId),quantity)
        var remain=quantity
        for(a in dao.lotesSalida(lineId)){
            if(remain==0L)break
            val capacity=a.cantidad-dao.totalDevuelto(lineId,a.loteId)
            if(capacity<=0)continue
            val n=minOf(remain,capacity)
            check(dao.restaurarLote(a.loteId,n)==1)
            dao.insertarDevolucion(DevolucionEntity(salidaId=line.salidaId,salidaLineaId=lineId,loteId=a.loteId,cantidad=n,fechaEpochMillis=System.currentTimeMillis()))
            remain-=n
        }
        check(remain==0L){"La entrega no tiene saldo de lote suficiente para asignar la devolución"}
        check(dao.ajustarStock(line.productoId,quantity)==1)
    }}
    fun movements(period:Period)=period.range().let{dao.unidadesMovidas(it.first,it.second)}
    fun ranking(period:Period)=period.range().let{dao.ranking(it.first,it.second)}
    fun routeDay(date:String)=dao.jornadaRutas(date)
    fun routesForDay(date:String,count:Int)=dao.rutasDiarias(date,count)
    suspend fun configureRouteDay(date:String,count:Int)=db.withTransaction{
        LocalDate.parse(date)
        val numbers=generarNumerosDeRuta(count)
        dao.guardarJornadaRutas(JornadaRutasEntity(fecha=date,cantidadRutas=count))
        dao.insertarRutasDiarias(numbers.map{RutaDiariaEntity(fecha=date,numeroRuta=it)})
    }
    suspend fun setRouteCompleted(date:String,number:Int,completed:Boolean){
        require(number in 1..1000)
        check(dao.marcarRuta(date,number,completed)==1){"La ruta ya no existe en esta jornada"}
    }
    suspend fun saveProduct(name:String,unit:String,min:Long,max:Long){require(name.isNotBlank()&&min>=0&&max>=min);dao.crearProducto(ProductoEntity(nombre=name.trim(),unidad=unit.trim().ifBlank{"unidad"},minimo=min,maximo=max))}
    suspend fun addContact(name:String,role:String){require(name.isNotBlank());dao.crearContacto(ContactoEntity(nombre=name.trim(),rol=role))}
    suspend fun addDriver(name:String){require(name.isNotBlank());dao.crearRepartidor(RepartidorEntity(nombre=name.trim()))}
    suspend fun scheduleTrailer(day:Int,enabled:Boolean)=db.withTransaction{require(day in 1..7);dao.guardarTrailer(TrailerProgramadoEntity(id=day.toLong(),diaSemana=day,activo=enabled))}
    suspend fun receive(productId:Long,quantity:Long,supplier:ContactoEntity?,date:Long=System.currentTimeMillis())=db.withTransaction{require(quantity>0);val p=dao.producto(productId)?:error("Producto no encontrado");val id=dao.insertarEntrada(EntradaEntity(proveedorId=supplier?.id,proveedorNombre=supplier?.nombre,fechaEpochMillis=date));dao.insertarLote(LoteEntity(productoId=p.id,entradaId=id,recibidoEnEpochMillis=date,cantidadInicial=quantity,cantidadDisponible=quantity));check(dao.ajustarStock(productId,quantity)==1)}
    suspend fun issue(productId:Long,quantity:Long,driver:RepartidorEntity?,date:Long=System.currentTimeMillis()):List<Pair<Long,Long>> = db.withTransaction {
        require(quantity>0);val p=dao.producto(productId)?:error("Producto no encontrado");val lots=dao.lotesFifo(productId);val total=lots.sumOf{it.cantidadDisponible};require(total>=quantity){"Stock insuficiente: hay $total ${p.unidad}"}
        val allocations=com.benjamin.inventario.domain.planificarFifo(lots,quantity).map{it.loteId to it.cantidad}
        // Las columnas de destinatario se conservan en Room para no perder compatibilidad
        // con la base de datos existente; las nuevas entregas ya no guardan destinatario.
        val sid=dao.insertarSalida(SalidaEntity(repartidorId=driver?.id,repartidorNombre=driver?.nombre,fechaEpochMillis=date));val lid=dao.insertarLinea(SalidaLineaEntity(salidaId=sid,productoId=p.id,productoNombre=p.nombre,cantidad=quantity,fechaEpochMillis=date))
        allocations.forEach{(id,n)->check(dao.descontarLote(id,n)==1){"El lote cambió; inténtalo de nuevo"};dao.insertarAsignacion(SalidaLoteEntity(salidaLineaId=lid,loteId=id,cantidad=n))};check(dao.ajustarStock(productId,-quantity)==1);allocations
    }
    suspend fun editIssue(lineId:Long,productId:Long,quantity:Long)=db.withTransaction{
        require(quantity>0){"La cantidad debe ser mayor que cero"}
        val line=dao.lineaSalida(lineId)?:error("Entrega no encontrada")
        check(dao.totalDevueltoLinea(lineId)==0L){"No se puede editar una entrega que ya tiene devoluciones registradas"}
        val oldAllocations=dao.lotesSalida(lineId)
        check(oldAllocations.sumOf{it.cantidad}==line.cantidad){"No se pudo verificar el stock original de la entrega"}
        val target=dao.producto(productId)?:error("Producto no encontrado")
        check(target.activo){"El producto seleccionado está inactivo"}
        oldAllocations.forEach{check(dao.restaurarLote(it.loteId,it.cantidad)==1)}
        check(dao.ajustarStock(line.productoId,line.cantidad)==1)
        check(dao.eliminarAsignacionesLinea(lineId)==oldAllocations.size)
        val allocations=com.benjamin.inventario.domain.planificarFifo(dao.lotesFifo(productId),quantity)
        allocations.forEach{a->
            check(dao.descontarLote(a.loteId,a.cantidad)==1){"El lote cambió; inténtalo de nuevo"}
            dao.insertarAsignacion(SalidaLoteEntity(salidaLineaId=lineId,loteId=a.loteId,cantidad=a.cantidad))
        }
        check(dao.ajustarStock(productId,-quantity)==1)
        check(dao.actualizarLineaSalida(lineId,productId,target.nombre,quantity)==1)
    }
    suspend fun deleteIssue(lineId:Long)=db.withTransaction{
        val line=dao.lineaSalida(lineId)?:error("Entrega no encontrada")
        check(dao.totalDevueltoLinea(lineId)==0L){"No se puede eliminar una entrega que ya tiene devoluciones registradas"}
        val allocations=dao.lotesSalida(lineId)
        check(allocations.sumOf{it.cantidad}==line.cantidad){"No se pudo verificar el stock original de la entrega"}
        allocations.forEach{check(dao.restaurarLote(it.loteId,it.cantidad)==1)}
        check(dao.ajustarStock(line.productoId,line.cantidad)==1)
        check(dao.eliminarAsignacionesLinea(lineId)==allocations.size)
        check(dao.eliminarLineaSalida(lineId)==1)
        if(dao.lineasDeSalida(line.salidaId)==0)check(dao.eliminarSalida(line.salidaId)==1)
    }
    suspend fun editReceipt(lotId:Long,productId:Long,quantity:Long)=db.withTransaction{
        require(quantity>0){"La cantidad debe ser mayor que cero"}
        val lot=dao.lote(lotId)?:error("Entrada no encontrada")
        val target=dao.producto(productId)?:error("Producto no encontrado")
        check(target.activo){"El producto seleccionado está inactivo"}
        check(lot.cantidadDisponible in 0..lot.cantidadInicial){"El saldo del lote es inválido"}
        val referencias=dao.referenciasSalidaLote(lotId)
        val netoConsumido=lot.cantidadInicial-lot.cantidadDisponible
        check(quantity>=netoConsumido){"La cantidad no puede ser menor que las unidades que siguen fuera del almacén ($netoConsumido)"}
        if(productId!=lot.productoId){
            check(referencias==0&&netoConsumido==0L){"No se puede cambiar el producto de una entrada que ya fue asignada a entregas"}
            check(dao.ajustarStock(lot.productoId,-lot.cantidadDisponible)==1)
            check(dao.ajustarStock(productId,quantity)==1)
            check(dao.actualizarLote(lotId,productId,quantity,quantity)==1)
        }else{
            val delta=quantity-lot.cantidadInicial
            check(dao.ajustarStock(productId,delta)==1){"El ajuste dejaría el stock en un valor inválido"}
            check(dao.actualizarLote(lotId,productId,quantity,lot.cantidadDisponible+delta)==1)
        }
    }
    suspend fun deleteReceipt(lotId:Long)=db.withTransaction{
        val lot=dao.lote(lotId)?:error("Entrada no encontrada")
        check(dao.referenciasSalidaLote(lotId)==0){"No se puede eliminar un lote que ya está asociado a entregas"}
        check(dao.ajustarStock(lot.productoId,-lot.cantidadDisponible)==1)
        check(dao.eliminarLote(lotId)==1)
        if(dao.lotesDeEntrada(lot.entradaId)==0)check(dao.eliminarEntrada(lot.entradaId)==1)
    }
    suspend fun recommendations():List<OrderSuggestion>{val now=System.currentTimeMillis();val from=now-30L*86400000;return products.first().mapNotNull{p->val velocity=dao.demanda(p.id,from,now).toDouble()/30;val forecast=kotlin.math.ceil(velocity*7).toLong();val target=if(p.maximo>0)p.maximo else p.minimo;val order=(target-p.cantidadActual+forecast).coerceAtLeast(0);if(order>0)OrderSuggestion(p.nombre,p.cantidadActual,target,forecast,order,velocity)else null}.sortedByDescending{it.suggested}}
}
enum class Period{SEMANA,MES;fun range():Pair<Long,Long>{val d=LocalDate.now();val a=if(this==SEMANA)d.with(java.time.DayOfWeek.MONDAY)else d.withDayOfMonth(1);val b=if(this==SEMANA)a.plusWeeks(1)else a.plusMonths(1);val z=ZoneId.systemDefault();return a.atStartOfDay(z).toInstant().toEpochMilli() to b.atStartOfDay(z).toInstant().toEpochMilli()}}
data class OrderSuggestion(val name:String,val stock:Long,val target:Long,val forecast:Long,val suggested:Long,val unitsPerDay:Double)
private fun todayStart()=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
private fun todayEnd()=LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
