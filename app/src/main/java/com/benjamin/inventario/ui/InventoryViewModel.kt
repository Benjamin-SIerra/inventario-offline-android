package com.benjamin.inventario.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.benjamin.inventario.data.*
import com.benjamin.inventario.data.local.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class InventoryViewModel(private val repo:InventoryRepository):ViewModel(){
 val products=repo.products.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 val contacts=repo.contacts.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 val drivers=repo.drivers.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 val trailers=repo.trailers.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 val trailerDays=repo.trailerDays.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),0)
 val low=repo.lowStock.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),0);val empty=repo.outOfStock.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),0)
 val receipts=repo.receiptsToday.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),0);val issues=repo.issuesToday.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),0);val recentIssues=repo.recentIssues.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 val routeDate=LocalDate.now().toString()
 val routeDay:StateFlow<JornadaRutasEntity?> =repo.routeDay(routeDate).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
  val dailyRoutes:StateFlow<List<RutaDiariaEntity>> =routeDay.flatMapLatest{day->
  if(day==null) flowOf(emptyList()) else repo.routesForDay(routeDate,day.cantidadRutas)
 }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 private val period=MutableStateFlow(Period.SEMANA);val selectedPeriod=period.asStateFlow()
 val moved=period.flatMapLatest(repo::movements).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),0L)
 val ranking=period.flatMapLatest(repo::ranking).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 private val _suggestions=MutableStateFlow<List<OrderSuggestion>>(emptyList());val suggestions=_suggestions.asStateFlow()
 private val _message=MutableSharedFlow<String>(extraBufferCapacity=1);val message=_message.asSharedFlow()
 fun period(p:Period){period.value=p}
 fun refreshSuggestions(){viewModelScope.launch{runCatching{repo.recommendations()}.onSuccess{_suggestions.value=it}.onFailure{_message.tryEmit(it.message?:"Error")}}}
 fun saveProduct(n:String,u:String,m:Long,x:Long)=launchOp{repo.saveProduct(n,u,m,x)}
 fun addContact(n:String,r:String)=launchOp{repo.addContact(n,r)}
 fun addDriver(n:String)=launchOp{repo.addDriver(n)}
 fun trailer(d:Int,on:Boolean)=launchOp{repo.scheduleTrailer(d,on)}
 fun configureRoutes(count:Int)=launchOp{repo.configureRouteDay(routeDate,count);_message.emit("Jornada configurada con $count rutas")}
 fun setRouteCompleted(number:Int,completed:Boolean)=launchOp{repo.setRouteCompleted(routeDate,number,completed)}
 fun receive(id:Long,q:Long,c:ContactoEntity?)=launchOp{repo.receive(id,q,c)}
 fun returnUnits(lineId:Long,q:Long)=launchOp{repo.returnUnits(lineId,q);_message.emit("Devolución registrada y asignada al lote de origen")}
 fun issue(id:Long,q:Long,d:RepartidorEntity?)=launchOp{val used=repo.issue(id,q,d);_message.emit("Entrega registrada · FIFO ${used.joinToString{ "lote ${it.first}: ${it.second}" }}")}
 private fun launchOp(block:suspend()->Unit){viewModelScope.launch{runCatching{block()}.onFailure{_message.emit(it.message?:"No se pudo guardar")}}}
 companion object{fun factory(repo:InventoryRepository)=object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST") override fun<T:ViewModel>create(modelClass:Class<T>):T=InventoryViewModel(repo) as T}}
}
