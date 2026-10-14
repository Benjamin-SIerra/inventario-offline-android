package com.benjamin.inventario.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.benjamin.inventario.data.InventoryRepository
import com.benjamin.inventario.data.OrderSuggestion
import com.benjamin.inventario.data.Period
import com.benjamin.inventario.data.local.JornadaRutasEntity
import com.benjamin.inventario.data.local.RepartidorEntity
import com.benjamin.inventario.data.local.RutaDiariaEntity
import com.benjamin.inventario.data.local.TrailerProgramadoEntity
import com.benjamin.inventario.data.local.EntradaReciente
import com.benjamin.inventario.data.local.SalidaReciente
import com.benjamin.inventario.data.local.ProductoEntity
import com.benjamin.inventario.data.local.AjusteReciente
import com.benjamin.inventario.data.local.DevolucionReciente
import com.benjamin.inventario.domain.weekCycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.ZoneId

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class InventoryViewModel(private val repo: InventoryRepository) : ViewModel() {
    val products = repo.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val drivers = repo.drivers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allDrivers = repo.allDrivers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trailers = repo.trailers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trailerDays = repo.trailerDays.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val low = repo.lowStock.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val empty = repo.outOfStock.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val receipts = repo.receiptsToday.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val issues = repo.issuesToday.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val recentIssues = repo.recentIssues.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<SalidaReciente>())
    val recentReceipts = repo.recentReceipts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<EntradaReciente>())

    val routeDate = LocalDate.now().toString()
    val routeDay = repo.routeDay(routeDate).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null as JornadaRutasEntity?)
    val dailyRoutes = routeDay.flatMapLatest { day ->
        if (day == null) flowOf(emptyList()) else repo.routesForDay(routeDate, day.cantidadRutas)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<RutaDiariaEntity>())

    private val period = MutableStateFlow(Period.SEMANA)
    val selectedPeriod = period.asStateFlow()
    val weekStartDay = repo.weekStartDay.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),DayOfWeek.MONDAY)
    val moved = combine(period,weekStartDay){p,d->p to d}.flatMapLatest{(p,d)->repo.movements(p,d)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),0L)
    val ranking = combine(period,weekStartDay){p,d->p to d}.flatMapLatest{(p,d)->repo.ranking(p,d)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val weekOffset=MutableStateFlow(1)
    val selectedWeekOffset=weekOffset.asStateFlow()
    val historyRange=combine(weekStartDay,weekOffset){day,offset->
        val start=weekCycle(LocalDate.now(),day).start.minusWeeks(offset.toLong())
        val zone=ZoneId.systemDefault()
        start.atStartOfDay(zone).toInstant().toEpochMilli() to start.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),0L to 0L)
    val historyReceipts=historyRange.flatMapLatest{(a,b)->repo.receiptsBetween(a,b)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList<EntradaReciente>())
    val historyIssues=historyRange.flatMapLatest{(a,b)->repo.issuesBetween(a,b)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList<SalidaReciente>())
    val historyReturns=historyRange.flatMapLatest{(a,b)->repo.returnsBetween(a,b)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList<DevolucionReciente>())
    val historyAdjustments=historyRange.flatMapLatest{(a,b)->repo.adjustmentsBetween(a,b)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList<AjusteReciente>())
    private val selectedDriver=MutableStateFlow<RepartidorEntity?>(null)
    val driverProfile=selectedDriver.flatMapLatest{d->if(d==null) flowOf(emptyList()) else repo.issuesForDriver(d)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList<SalidaReciente>())
    val driverProfileName=selectedDriver.map{it?.nombre}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)

    private val _suggestions = MutableStateFlow<List<OrderSuggestion>>(emptyList())
    val suggestions = _suggestions.asStateFlow()
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message = _message.asSharedFlow()

    fun period(value: Period) { period.value = value }
    fun chooseHistoryWeek(offset:Int){weekOffset.value=offset.coerceAtLeast(1)}
    fun openDriverProfile(driver:RepartidorEntity){selectedDriver.value=driver}
    fun closeDriverProfile(){selectedDriver.value=null}
    fun setWeekStart(day:DayOfWeek)=launchOp{repo.saveWeekStart(day)}

    fun refreshSuggestions() {
        viewModelScope.launch {
            runCatching { repo.recommendations() }
                .onSuccess { _suggestions.value = it }
                .onFailure { _message.tryEmit(it.message ?: "Error") }
        }
    }

    fun saveProduct(name:String,code:String,unit:String,min:Long,max:Long,stock:Long)=launchOp{repo.saveProduct(name,code,unit,min,max,stock)}
    fun updateProduct(id:Long,name:String,code:String,unit:String,min:Long,max:Long,stock:Long)=launchOp{repo.updateProduct(id,name,code,unit,min,max,stock)}
    fun deleteProduct(id:Long)=launchOp{repo.deleteProduct(id);_message.emit("Producto eliminado del almacén; su historial se conserva")}
    fun issueBatch(lines:List<Pair<Long,Long>>,driver:RepartidorEntity?)=launchOp{repo.issueBatch(lines,driver);_message.emit("Carga registrada: ${lines.size} productos · ${lines.sumOf { it.second }} unidades · ${driver?.nombre ?: "Sin repartidor"}")}
    fun addDriver(name: String) = launchOp { repo.addDriver(name) }
    fun updateDriver(id: Long, name: String) = launchOp { repo.updateDriver(id, name) }
    fun deleteDriver(id: Long) = launchOp { repo.deleteDriver(id) }
    fun trailer(day: Int, enabled: Boolean) = launchOp { repo.scheduleTrailer(day, enabled) }
    fun configureRoutes(count: Int) = launchOp {
        repo.configureRouteDay(routeDate, count)
        _message.emit("Jornada configurada con $count rutas")
    }
    fun setRouteCompleted(number: Int, completed: Boolean) = launchOp { repo.setRouteCompleted(routeDate, number, completed) }
    fun receive(id: Long, quantity: Long) = launchOp { repo.receive(id, quantity) }
    fun returnUnits(lineId: Long, quantity: Long) = launchOp {
        repo.returnUnits(lineId, quantity)
        _message.emit("Devolución registrada")
    }
    fun issue(id: Long, quantity: Long, driver: RepartidorEntity?) = launchOp {
        val used = repo.issue(id, quantity, driver)
        _message.emit("Entrega registrada · FIFO ${used.joinToString { "lote ${it.first}: ${it.second}" }}")
    }
    fun editReceipt(lotId: Long, productId: Long, quantity: Long) = launchOp {
        repo.editReceipt(lotId, productId, quantity)
        _message.emit("Entrada actualizada; stock ajustado")
    }
    fun deleteReceipt(lotId: Long) = launchOp {
        repo.deleteReceipt(lotId)
        _message.emit("Lote eliminado; sus entregas históricas quedaron desvinculadas")
    }
    fun editIssue(lineId: Long, productId: Long, quantity: Long) = launchOp {
        repo.editIssue(lineId, productId, quantity)
        _message.emit("Entrega actualizada; stock ajustado")
    }
    fun deleteIssue(lineId: Long) = launchOp {
        repo.deleteIssue(lineId)
        _message.emit("Entrega eliminada; stock revertido")
    }
    fun resetAllData() = launchOp {
        repo.resetAllData()
        _suggestions.value = emptyList()
        _message.emit("Datos restablecidos. El inventario y las métricas están en cero.")
    }

    private fun launchOp(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { _message.emit(it.message ?: "No se pudo guardar") }
        }
    }

    companion object {
        fun factory(repo: InventoryRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = InventoryViewModel(repo) as T
        }
    }
}
