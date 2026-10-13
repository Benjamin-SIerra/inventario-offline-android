package com.benjamin.inventario.data

import androidx.room.withTransaction
import com.benjamin.inventario.data.local.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class InventoryRepository(private val db: InventoryDatabase) {
    private val dao=db.dao()
    val products=dao.productos(); val contacts=dao.contactos(); val drivers=dao.repartidores(); val trailers=dao.trailers(); val trailerDays=dao.diasTrailer(); val lowStock=dao.bajoStock(); val outOfStock=dao.agotados()
    val receiptsToday=dao.entradasHoy(todayStart(),todayEnd()); val issuesToday=dao.salidasHoy(todayStart(),todayEnd()); val recentIssues=dao.salidasRecientes()
    suspend fun returnUnits(lineId:Long, quantity:Long){db.withTransaction{
        require(quantity>0){"La cantidad debe ser mayor que cero"};val line=dao.lineaSalida(lineId)?:error("Entrega no encontrada")
        val already=dao.lotesSalida(lineId).sumOf{dao.totalDevuelto(lineId,it.loteId)};require(already+quantity<=line.cantidad){"La devolución supera la cantidad entregada"}
        var remain=quantity
        for(a in dao.lotesSalida(lineId)){if(remain==0L)break;val capacity=a.cantidad-dao.totalDevuelto(lineId,a.loteId);if(capacity<=0)continue;val n=minOf(remain,capacity);check(dao.restaurarLote(a.loteId,n)==1);dao.insertarDevolucion(DevolucionEntity(salidaId=line.salidaId,salidaLineaId=lineId,loteId=a.loteId,cantidad=n,fechaEpochMillis=System.currentTimeMillis()));remain-=n}
        check(remain==0L);check(dao.ajustarStock(line.productoId,quantity)==1)
    }}
    fun movements(period:Period)=period.range().let{dao.unidadesMovidas(it.first,it.second)}
    fun ranking(period:Period)=period.range().let{dao.ranking(it.first,it.second)}
    suspend fun saveProduct(name:String,unit:String,min:Long,max:Long){require(name.isNotBlank()&&min>=0&&max>=min);dao.crearProducto(ProductoEntity(nombre=name.trim(),unidad=unit.trim().ifBlank{"unidad"},minimo=min,maximo=max))}
    suspend fun addContact(name:String,role:String){require(name.isNotBlank());dao.crearContacto(ContactoEntity(nombre=name.trim(),rol=role))}
    suspend fun addDriver(name:String){require(name.isNotBlank());dao.crearRepartidor(RepartidorEntity(nombre=name.trim()))}
    suspend fun scheduleTrailer(day:Int,enabled:Boolean)=db.withTransaction{require(day in 1..7);dao.guardarTrailer(TrailerProgramadoEntity(id=day.toLong(),diaSemana=day,activo=enabled))}
    suspend fun receive(productId:Long,quantity:Long,supplier:ContactoEntity?,date:Long=System.currentTimeMillis())=db.withTransaction{require(quantity>0);val p=dao.producto(productId)?:error("Producto no encontrado");val id=dao.insertarEntrada(EntradaEntity(proveedorId=supplier?.id,proveedorNombre=supplier?.nombre,fechaEpochMillis=date));dao.insertarLote(LoteEntity(productoId=p.id,entradaId=id,recibidoEnEpochMillis=date,cantidadInicial=quantity,cantidadDisponible=quantity));check(dao.ajustarStock(productId,quantity)==1)}
    suspend fun issue(productId:Long,quantity:Long,recipient:ContactoEntity?,recipientName:String,driver:RepartidorEntity?,date:Long=System.currentTimeMillis()):List<Pair<Long,Long>> = db.withTransaction {
        require(quantity>0&&recipientName.isNotBlank());val p=dao.producto(productId)?:error("Producto no encontrado");val lots=dao.lotesFifo(productId);val total=lots.sumOf{it.cantidadDisponible};require(total>=quantity){"Stock insuficiente: hay $total ${p.unidad}"}
        val allocations=com.benjamin.inventario.domain.planificarFifo(lots,quantity).map{it.loteId to it.cantidad}
        val sid=dao.insertarSalida(SalidaEntity(destinatarioId=recipient?.id,destinatarioNombre=recipientName.trim(),repartidorId=driver?.id,repartidorNombre=driver?.nombre,fechaEpochMillis=date));val lid=dao.insertarLinea(SalidaLineaEntity(salidaId=sid,productoId=p.id,productoNombre=p.nombre,cantidad=quantity,fechaEpochMillis=date))
        allocations.forEach{(id,n)->check(dao.descontarLote(id,n)==1){"El lote cambió; inténtalo de nuevo"};dao.insertarAsignacion(SalidaLoteEntity(salidaLineaId=lid,loteId=id,cantidad=n))};check(dao.ajustarStock(productId,-quantity)==1);allocations
    }
    suspend fun recommendations():List<OrderSuggestion>{val now=System.currentTimeMillis();val from=now-30L*86400000;return products.first().mapNotNull{p->val velocity=dao.demanda(p.id,from,now).toDouble()/30;val forecast=kotlin.math.ceil(velocity*7).toLong();val target=if(p.maximo>0)p.maximo else p.minimo;val order=(target-p.cantidadActual+forecast).coerceAtLeast(0);if(order>0)OrderSuggestion(p.nombre,p.cantidadActual,target,forecast,order,velocity)else null}.sortedByDescending{it.suggested}}
}
enum class Period{SEMANA,MES;fun range():Pair<Long,Long>{val d=LocalDate.now();val a=if(this==SEMANA)d.with(java.time.DayOfWeek.MONDAY)else d.withDayOfMonth(1);val b=if(this==SEMANA)a.plusWeeks(1)else a.plusMonths(1);val z=ZoneId.systemDefault();return a.atStartOfDay(z).toInstant().toEpochMilli() to b.atStartOfDay(z).toInstant().toEpochMilli()}}
data class OrderSuggestion(val name:String,val stock:Long,val target:Long,val forecast:Long,val suggested:Long,val unitsPerDay:Double)
private fun todayStart()=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
private fun todayEnd()=LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
