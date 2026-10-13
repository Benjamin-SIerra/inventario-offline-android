package com.benjamin.inventario.domain

import com.benjamin.inventario.data.local.LoteFIFO

data class AsignacionFifo(val loteId:Long,val cantidad:Long)
fun planificarFifo(lotes:List<LoteFIFO>,cantidad:Long):List<AsignacionFifo>{
 require(cantidad>0){"La cantidad debe ser mayor que cero"}
 val ordenados=lotes.filter{it.cantidadDisponible>0}.sortedWith(compareBy<LoteFIFO>{it.recibidoEnEpochMillis}.thenBy{it.id})
 val stock=ordenados.sumOf{it.cantidadDisponible};require(stock>=cantidad){"Stock insuficiente: disponible $stock, solicitado $cantidad"}
 var faltante=cantidad
 return buildList { for(lote in ordenados){if(faltante==0L)break;val toma=minOf(faltante,lote.cantidadDisponible);add(AsignacionFifo(lote.id,toma));faltante-=toma} }
}
