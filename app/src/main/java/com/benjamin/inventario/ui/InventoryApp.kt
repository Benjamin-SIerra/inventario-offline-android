package com.benjamin.inventario.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benjamin.inventario.data.*
import com.benjamin.inventario.data.local.*
import com.benjamin.inventario.domain.isWholeNumberInput
import com.benjamin.inventario.domain.parseWholeNumber
import com.benjamin.inventario.domain.matchesProductSearch
import com.benjamin.inventario.domain.isLowStock
import com.benjamin.inventario.domain.isValidMultiProductIssue
import java.time.DayOfWeek
import java.time.LocalDate

private val Gold = Color(0xFFC8A84C)
private val Green = Color(0xFF4CAF7D)
private val Amber = Color(0xFFE6AE49)
private val Red = Color(0xFFE05B62)

private data class DeleteMovement(val receipt:Boolean,val id:Long)
private data class BatchLine(val productId:Long,val quantity:String)

@Composable
fun InventoryApp(db: InventoryDatabase) {
    val vm: InventoryViewModel = viewModel(factory = InventoryViewModel.factory(InventoryRepository(db)))
    val products by vm.products.collectAsState()
    val drivers by vm.drivers.collectAsState()
    val allDrivers by vm.allDrivers.collectAsState()
    val routeDay by vm.routeDay.collectAsState()
    val dailyRoutes by vm.dailyRoutes.collectAsState()
    val low by vm.low.collectAsState()
    val empty by vm.empty.collectAsState()
    val trailerDays by vm.trailerDays.collectAsState()
    val trailers by vm.trailers.collectAsState()
    val receipts by vm.receipts.collectAsState()
    val issues by vm.issues.collectAsState()
    val recentIssues by vm.recentIssues.collectAsState()
    val recentReceipts by vm.recentReceipts.collectAsState()
    val moved by vm.moved.collectAsState()
    val rank by vm.ranking.collectAsState()
    val period by vm.selectedPeriod.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val weekStartDay by vm.weekStartDay.collectAsState()
    val weekOffset by vm.selectedWeekOffset.collectAsState()
    val historyRange by vm.historyRange.collectAsState()
    val historyReceipts by vm.historyReceipts.collectAsState()
    val historyIssues by vm.historyIssues.collectAsState()
    val historyAdjustments by vm.historyAdjustments.collectAsState()
    val historyReturns by vm.historyReturns.collectAsState()
    val driverHistory by vm.driverProfile.collectAsState()
    val currentCycle=com.benjamin.inventario.domain.weekCycle(LocalDate.now(),weekStartDay)

    var tab by remember { mutableIntStateOf(0) }
    var showRouteControl by remember { mutableStateOf(false) }
    var showDriverManager by remember { mutableStateOf(false) }
    var showWeeklyHistory by remember { mutableStateOf(false) }
    var selectedDriverProfile by remember { mutableStateOf<RepartidorEntity?>(null) }
    var editingProduct by remember { mutableStateOf<ProductoEntity?>(null) }
    var deletingProduct by remember { mutableStateOf<ProductoEntity?>(null) }
    var dialog by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf("") }
    var returnLine by remember { mutableStateOf<SalidaReciente?>(null) }
    var returnQty by remember { mutableStateOf("") }
    var editingReceipt by remember { mutableStateOf<EntradaReciente?>(null) }
    var editingIssue by remember { mutableStateOf<SalidaReciente?>(null) }
    var deleteTarget by remember { mutableStateOf<DeleteMovement?>(null) }
    var editingDriver by remember { mutableStateOf<RepartidorEntity?>(null) }
    var deletingDriver by remember { mutableStateOf<RepartidorEntity?>(null) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.message.collect { toast = it } }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("Inicio", "Productos", "Movimientos", "Más").forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index; showRouteControl = false; showDriverManager = false },
                        icon = { Text(listOf("⌂", "▤", "⇄", "•••")[index]) },
                        label = { Text(label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 0 || tab == 2) FloatingActionButton(
                onClick = { tab = 2 }, containerColor = Gold, contentColor = Color.Black
            ) { Text("+", fontSize = 28.sp) }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(Color(0xFF101113))) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("INVENTARIO", color = Gold, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Control simple, siempre local", color = Color.LightGray, fontSize = 12.sp)
                }
                Text("● SIN CONEXIÓN", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            when (tab) {
                0 -> Dashboard(products.size, low, empty, trailerDays, receipts, issues, moved, rank, period, vm, suggestions)
                1 -> ProductScreen(products,onAdd={editingProduct=null;dialog="producto"},onEdit={editingProduct=it;dialog="producto"},onDelete={deletingProduct=it})
                2 -> MovementsScreen(
                    products = products,
                    drivers = drivers,
                    receipts = recentReceipts,
                    recent = recentIssues,
                    onReceive = { dialog = "entrada" },
                    onIssue = { dialog = "salida" },
                    onReturn = { returnLine = it; returnQty = "" },
                    onEditReceipt = { editingReceipt = it },
                    onDeleteReceipt = { deleteTarget = DeleteMovement(receipt = true, id = it.loteId) },
                    onEditIssue = { editingIssue = it },
                    onDeleteIssue = { deleteTarget = DeleteMovement(receipt = false, id = it.lineaId) }
                )
                else -> when {
                    showRouteControl -> RouteControlScreen(
                        date = vm.routeDate, day = routeDay, routes = dailyRoutes,
                        onBack = { showRouteControl = false }, onConfigure = vm::configureRoutes,
                        onToggle = vm::setRouteCompleted
                    )
                    showWeeklyHistory -> WeeklyHistoryScreen(
                        startEpoch=historyRange.first,endEpoch=historyRange.second,offset=weekOffset,
                        receipts=historyReceipts,issues=historyIssues,returns=historyReturns,adjustments=historyAdjustments,
                        onBack={showWeeklyHistory=false},onOffset=vm::chooseHistoryWeek
                    )
                    selectedDriverProfile != null -> DriverProfileScreen(
                        driver=selectedDriverProfile!!,issues=driverHistory,onBack={selectedDriverProfile=null;vm.closeDriverProfile()}
                    )
                    showDriverManager -> DriversScreen(
                        drivers=allDrivers,onBack={showDriverManager=false},
                        onAdd={editingDriver=null;dialog="repartidor"},onEdit={editingDriver=it;dialog="repartidor"},
                        onDelete={deletingDriver=it},onProfile={selectedDriverProfile=it;vm.openDriverProfile(it)}
                    )
                    else -> MoreScreen(
                        drivers=allDrivers,weekStart=weekStartDay,cycleStart=currentCycle.start,cycleEnd=currentCycle.lastDay,onDrivers={showDriverManager=true},
                        onTrailers={dialog="trailers"},onRoutes={showRouteControl=true},
                        onHistory={showWeeklyHistory=true;vm.chooseHistoryWeek(1)},onWeekStart=vm::setWeekStart,
                        onReset={showResetConfirmation=true}
                    )
                }
            }
        }

        if (dialog.isNotEmpty()) EntryDialog(
            kind = dialog,
            products = products,
            drivers = drivers,
            trailers = trailers,
            editingDriver=editingDriver,
            editingProduct=editingProduct,
            onClose={dialog="";editingDriver=null;editingProduct=null},
            onSaveProduct={id,name,code,unit,min,max,stock->if(id==null)vm.saveProduct(name,code,unit,min,max,stock) else vm.updateProduct(id,name,code,unit,min,max,stock);dialog="";editingProduct=null},
            onReceive={id,quantity->vm.receive(id,quantity);dialog=""},
            onIssue={lines,driver->vm.issueBatch(lines,driver);dialog=""},
            onSaveDriver = { name -> vm.addDriver(name); dialog = "" },
            onUpdateDriver = { id, name -> vm.updateDriver(id, name); dialog = ""; editingDriver = null },
            onTrailer = vm::trailer
        )

        if (returnLine != null) {
            val line = returnLine!!
            val quantity = parseWholeNumber(returnQty)
            AlertDialog(
                onDismissRequest = { returnLine = null },
                title = { Text("Registrar devolución") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${line.productoNombre} · Repartidor: ${line.repartidorNombre ?: "—"}")
                        Text("Entregado: ${line.cantidad} u · Ya devuelto: ${line.devuelto} u")
                        Text("Pendiente: ${line.pendiente} u", color = Gold, fontWeight = FontWeight.Bold)
                        Field("Cantidad que regresa", returnQty, { returnQty = it }, numeric = true)
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = quantity != null && quantity in 1L..line.pendiente,
                        onClick = { vm.returnUnits(line.lineaId, quantity!!); returnLine = null }
                    ) { Text("Guardar devolución") }
                },
                dismissButton = { TextButton(onClick = { returnLine = null }) { Text("Cancelar") } }
            )
        }

        if (editingReceipt != null || editingIssue != null) EditMovementDialog(
            receipt = editingReceipt,
            issue = editingIssue,
            products = products,
            onClose = { editingReceipt = null; editingIssue = null },
            onSaveReceipt = { id, productId, quantity -> vm.editReceipt(id, productId, quantity); editingReceipt = null },
            onSaveIssue = { id, productId, quantity -> vm.editIssue(id, productId, quantity); editingIssue = null }
        )

        if (deleteTarget != null) {
            val target = deleteTarget!!
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text(if (target.receipt) "Eliminar lote" else "Eliminar entrega") },
                text = {
                    Text(
                        if (target.receipt) "Se quitará del stock lo que aún queda en el lote. Las entregas y devoluciones históricas se conservarán, pero quedarán desvinculadas de este lote."
                        else "Se eliminará la entrega y se devolverán al stock sus unidades."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (target.receipt) vm.deleteReceipt(target.id) else vm.deleteIssue(target.id)
                        deleteTarget = null
                    }) { Text("Eliminar", color = Red) }
                },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } }
            )
        }

        if (deletingDriver != null) {
            val driver = deletingDriver!!
            AlertDialog(
                onDismissRequest = { deletingDriver = null },
                title = { Text("Eliminar repartidor") },
                text = { Text("Se eliminará a ${driver.nombre}. El nombre guardado en entregas históricas se conservará.") },
                confirmButton = {
                    TextButton(onClick = { vm.deleteDriver(driver.id); deletingDriver = null }) { Text("Eliminar", color = Red) }
                },
                dismissButton = { TextButton(onClick = { deletingDriver = null }) { Text("Cancelar") } }
            )
        }

        if (showResetConfirmation) AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Restablecer datos de prueba") },
            text = {
                Text("Esta acción no se puede deshacer. Eliminará productos, stock, entradas, entregas, devoluciones, repartidores, rutas y programación de trailers. El inventario y las métricas quedarán vacíos.")
            },
            confirmButton = {
                TextButton(onClick = { vm.resetAllData(); showResetConfirmation = false }) {
                    Text("Restablecer todo", color = Red)
                }
            },
            dismissButton = { TextButton(onClick = { showResetConfirmation = false }) { Text("Cancelar") } }
        )

        if(deletingProduct!=null){
            val product=deletingProduct!!
            AlertDialog(onDismissRequest={deletingProduct=null},title={Text("Eliminar producto")},
                text={Text("Se archivará ${product.nombre} y se conservarán sus movimientos. Primero debes llevar el stock a cero; no se eliminará si tiene unidades asignadas o en almacén.")},
                confirmButton={TextButton(onClick={vm.deleteProduct(product.id);deletingProduct=null}){Text("Eliminar",color=Red)}},
                dismissButton={TextButton(onClick={deletingProduct=null}){Text("Cancelar")}})
        }

        if (toast.isNotBlank()) AlertDialog(
            onDismissRequest = { toast = "" },
            title = { Text("Inventario") },
            text = { Text(toast) },
            confirmButton = { TextButton(onClick = { toast = "" }) { Text("Entendido") } }
        )
    }
}

@Composable
private fun Dashboard(
    count: Int, low: Int, empty: Int, trailer: Int, receipts: Int, issues: Int,
    moved: Long, rank: List<ProductoMovido>, period: Period, vm: InventoryViewModel,
    suggestions: List<OrderSuggestion>
) {
    LaunchedEffect(Unit) { vm.refreshSuggestions() }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF202124)), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Resumen de hoy", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Metric("Productos", count.toString(), Color.White, Modifier.weight(1f))
                        Metric("Entradas", receipts.toString(), Green, Modifier.weight(1f))
                        Metric("Salidas", issues.toString(), Gold, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Bajo mínimo", low.toString(), Amber, Modifier.weight(1f))
                Metric("Agotados", empty.toString(), Red, Modifier.weight(1f))
                Metric("Trailers / semana", "$trailer de 7", Gold, Modifier.weight(1f))
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF202124)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Rotación", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Row {
                        Period.entries.forEach { selected ->
                            FilterChip(
                                selected = period == selected,
                                onClick = { vm.period(selected) },
                                label = { Text(if (selected == Period.SEMANA) "Esta semana" else "Este mes") },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                    Text("$moved unidades entregadas", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    SimpleBars(rank.map { it.productoNombre to it.unidades })
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF202124)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Productos con más movimiento", fontWeight = FontWeight.Bold)
                    if (rank.isEmpty()) Text("Aún no hay salidas en este periodo.", color = Color.LightGray, modifier = Modifier.padding(top = 8.dp))
                    rank.take(5).forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.productoNombre)
                            Text("${item.unidades} u", color = Gold)
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sugerencias de pedido", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::refreshSuggestions) { Text("Actualizar") }
            }
        }
        if (suggestions.isEmpty()) item {
            Text("Sin faltantes estimados. Las sugerencias usan salida de 30 días y un plazo de 7 días.", color = Color.LightGray, fontSize = 13.sp)
        }
        items(suggestions) { suggestion ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF202124))) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(suggestion.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Stock ${suggestion.stock} · objetivo ${suggestion.target} · consumo estimado ${suggestion.unitsPerDay}/día",
                            fontSize = 12.sp, color = Color.LightGray
                        )
                    }
                    Text("Pedir ${suggestion.suggested}", color = Amber, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Metric(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF202124)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(13.dp)) {
            Text(title, color = Color.LightGray, fontSize = 11.sp)
            Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun SimpleBars(data: List<Pair<String, Long>>) {
    if (data.isEmpty()) return
    val maximum = (data.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        data.take(5).forEach { (name, amount) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name.take(14), fontSize = 11.sp, modifier = Modifier.width(95.dp), maxLines = 1)
                Box(Modifier.weight(1f).height(12.dp).background(Color(0xFF34363A), RoundedCornerShape(8.dp))) {
                    Box(Modifier.fillMaxWidth((amount.toFloat() / maximum).coerceIn(0.04f, 1f)).fillMaxHeight().background(Gold, RoundedCornerShape(8.dp)))
                }
                Text(" $amount", fontSize = 11.sp, color = Color.LightGray)
            }
        }
    }
}

@Composable
private fun ProductScreen(products:List<ProductoEntity>,onAdd:()->Unit,onEdit:(ProductoEntity)->Unit,onDelete:(ProductoEntity)->Unit){
    var query by remember { mutableStateOf("") }
    var lowTab by remember { mutableIntStateOf(0) }
    val matched=products.filter { matchesProductSearch(it.nombre,it.codigo,query) }
    val visible=matched.filter { isLowStock(it.cantidadActual,it.minimo)==(lowTab==1) }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){Text("Productos",fontSize=23.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Button(onClick=onAdd,colors=ButtonDefaults.buttonColors(containerColor=Gold,contentColor=Color.Black)){Text("+ Agregar")}}
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Buscar por nombre o código")},singleLine=true)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("Stock Normal","Stock Bajo").forEachIndexed { index,label->FilterChip(selected=lowTab==index,onClick={lowTab=index},label={Text("$label · ${matched.count{isLowStock(it.cantidadActual,it.minimo)==(index==1)}}")}) }
        }
        if(visible.isEmpty()) EmptyState(if(query.isNotBlank())"Sin coincidencias" else if(lowTab==1)"Sin stock bajo" else "Tu almacén está vacío",if(query.isNotBlank())"Prueba con otro nombre o código." else "Agrega tu primer producto para empezar.")
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(visible,key={it.id}){product->
                val isLow=isLowStock(product.cantidadActual,product.minimo)
                val alert=if(product.cantidadActual==0L)"AGOTADO" else if(isLow)"STOCK BAJO" else "NORMAL"
                val color=if(isLow)Red else Green
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF202124)),shape=RoundedCornerShape(16.dp)){
                    Column(Modifier.padding(14.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(product.nombre,fontSize=17.sp,fontWeight=FontWeight.SemiBold);Text("Código ${product.codigo.ifBlank { "—" }} · Mín. ${product.minimo} · Máx. ${product.maximo} ${product.unidad}",color=Color.LightGray,fontSize=12.sp)};Column(horizontalAlignment=Alignment.End){Text("${product.cantidadActual} ${product.unidad}",fontSize=20.sp,fontWeight=FontWeight.Bold);Text(alert,color=color,fontSize=10.sp,fontWeight=FontWeight.Bold)}}
                        Row {TextButton(onClick={onEdit(product)}){Text("Editar",color=Gold)};TextButton(onClick={onDelete(product)}){Text("Eliminar",color=Red)}}
                    }
                }
            }
        }
    }
}

@Composable
private fun MovementsScreen(
    products: List<ProductoEntity>, drivers: List<RepartidorEntity>,
    receipts: List<EntradaReciente>, recent: List<SalidaReciente>,
    onReceive: () -> Unit, onIssue: () -> Unit,
    onReturn: (SalidaReciente) -> Unit, onEditReceipt: (EntradaReciente) -> Unit,
    onDeleteReceipt: (EntradaReciente) -> Unit, onEditIssue: (SalidaReciente) -> Unit,
    onDeleteIssue: (SalidaReciente) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Movimientos", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Las salidas consumen primero el lote más antiguo.", color = Color.LightGray, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onReceive, modifier = Modifier.weight(1f).height(58.dp), colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("↓  Recibir") }
            Button(onClick = onIssue, modifier = Modifier.weight(1f).height(58.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)) { Text("↑  Entregar") }
        }
        Text("${products.size} productos · ${drivers.size} repartidores", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            item { Text("Últimas entradas", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(vertical = 4.dp)) }
            if (receipts.isEmpty()) item { Text("Aún no hay entradas registradas.", color = Color.LightGray, fontSize = 13.sp) }
            items(receipts, key = { "entrada-${it.loteId}" }) { entry ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF202124))) {
                    Column(Modifier.padding(12.dp)) {
                        Text(entry.productoNombre, fontWeight = FontWeight.SemiBold)
                        Text("Recibidas: ${entry.cantidad} u · Disponibles de este lote: ${entry.cantidadDisponible} u", fontSize = 12.sp, color = Color.LightGray)
                        Row {
                            TextButton(onClick = { onEditReceipt(entry) }) { Text("Editar", color = Gold) }
                            TextButton(onClick = { onDeleteReceipt(entry) }) { Text("Eliminar", color = Red) }
                        }
                        if (entry.referenciasSalida > 0) Text(
                            "Hay entregas asociadas. Si eliminas el lote, se conservarán en el historial y se desvincularán.",
                            fontSize = 11.sp, color = Color.LightGray
                        )
                    }
                }
            }
            item { Text("Últimas entregas", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
            if (recent.isEmpty()) item { Text("Aún no hay entregas registradas.", color = Color.LightGray, fontSize = 13.sp) }
            items(recent, key = { "salida-${it.lineaId}" }) { sale ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF202124))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${sale.productoNombre} · ${sale.productoCodigo.ifBlank { "sin código" }}", fontWeight = FontWeight.SemiBold)
                        Text("Salida #${sale.salidaId} · ${formatTimestamp(sale.fechaEpochMillis)} · carga total ${sale.cantidadTotal} u",fontSize=11.sp,color=Gold)
                        Text("Asignado al repartidor: ${sale.cantidad} u · Devuelto: ${sale.devuelto} u · En su poder: ${sale.pendiente} u", fontSize = 12.sp, color = Color.LightGray)
                        Text("Repartidor: ${sale.repartidorNombre ?: "—"}", fontSize = 11.sp, color = Color.LightGray)
                        Row {
                            TextButton(enabled = sale.pendiente > 0L, onClick = { onReturn(sale) }) {
                                Text(if (sale.pendiente > 0L) "Devolver" else "Completada", color = if (sale.pendiente > 0L) Gold else Green)
                            }
                            if (sale.devuelto == 0L) {
                                TextButton(onClick = { onEditIssue(sale) }) { Text("Editar", color = Gold) }
                                TextButton(onClick = { onDeleteIssue(sale) }) { Text("Eliminar", color = Red) }
                            }
                        }
                        if (sale.devuelto > 0L) Text("Edición y eliminación bloqueadas porque ya hay devolución registrada.", fontSize = 11.sp, color = Color.LightGray)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMovementDialog(
    receipt: EntradaReciente?, issue: SalidaReciente?, products: List<ProductoEntity>, onClose: () -> Unit,
    onSaveReceipt: (Long, Long, Long) -> Unit, onSaveIssue: (Long, Long, Long) -> Unit
) {
    val selectedKey = receipt?.loteId ?: issue?.lineaId ?: 0L
    var productId by remember(selectedKey) { mutableStateOf(receipt?.productoId ?: issue?.productoId ?: products.firstOrNull()?.id ?: 0L) }
    var quantity by remember(selectedKey) { mutableStateOf((receipt?.cantidad ?: issue?.cantidad ?: 0L).toString()) }
    var expanded by remember { mutableStateOf(false) }
    val canChangeReceiptProduct = receipt == null || receipt.referenciasSalida == 0
    val netOutside = receipt?.let { (it.cantidad - it.cantidadDisponible).coerceAtLeast(0L) } ?: 0L
    val amount = parseWholeNumber(quantity)
    val productExists = products.any { it.id == productId }
    val valid = amount != null && amount > 0L && productExists && (receipt == null || amount >= netOutside)

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (receipt != null) "Editar entrada" else "Editar entrega") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (canChangeReceiptProduct) expanded = it }) {
                    OutlinedTextField(
                        value = products.firstOrNull { it.id == productId }?.nombre ?: "Producto",
                        onValueChange = {}, readOnly = true, enabled = canChangeReceiptProduct,
                        label = { Text("Producto") }, modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        products.forEach { product ->
                            DropdownMenuItem(text = { Text("${product.nombre} · stock ${product.cantidadActual}") }, onClick = { productId = product.id; expanded = false })
                        }
                    }
                }
                Field("Cantidad", quantity, { quantity = it }, numeric = true)
                if (receipt != null) {
                    Text("Unidades netas aún fuera del almacén: $netOutside. La cantidad nueva no puede ser menor.", fontSize = 12.sp, color = Color.LightGray)
                    if (receipt.referenciasSalida > 0) Text("El producto queda fijo para conservar la trazabilidad; puedes corregir la cantidad o eliminar el lote.", fontSize = 12.sp, color = Amber)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                if (receipt != null) onSaveReceipt(receipt.loteId, productId, amount!!) else issue?.let { onSaveIssue(it.lineaId, productId, amount!!) }
            }) { Text("Guardar cambios") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}

@Composable
private fun MoreScreen(drivers:List<RepartidorEntity>,weekStart:DayOfWeek,cycleStart:LocalDate,cycleEnd:LocalDate,onDrivers:()->Unit,onTrailers:()->Unit,onRoutes:()->Unit,onHistory:()->Unit,onWeekStart:(DayOfWeek)->Unit,onReset:()->Unit){
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Configuración",fontSize=24.sp,fontWeight=FontWeight.Bold)
        ActionCard("Repartidores","${drivers.size} registrados · perfiles, edición y búsqueda",onDrivers)
        ActionCard("Historial semanal","Consulta entradas, entregas y devoluciones de semanas archivadas",onHistory)
        ActionCard("Control diario de rutas","Configura y marca rutas surtidas",onRoutes)
        ActionCard("Días de trailer","Programa llegadas semanales",onTrailers)
        Text("Semana actual: $cycleStart — $cycleEnd",color=Gold,fontSize=13.sp)
        Text("Inicio del ciclo semanal",fontWeight=FontWeight.SemiBold)
        ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={expanded=it}){
            OutlinedTextField(value=weekStart.getDisplayName(java.time.format.TextStyle.FULL,java.util.Locale("es","MX")).replaceFirstChar(Char::uppercase),onValueChange={},readOnly=true,label={Text("El ciclo termina el día anterior")},modifier=Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){DayOfWeek.values().forEach{day->DropdownMenuItem(text={Text(day.getDisplayName(java.time.format.TextStyle.FULL,java.util.Locale("es","MX")).replaceFirstChar(Char::uppercase))},onClick={onWeekStart(day);expanded=false})}}
        }
        ActionCard("Restablecer datos de prueba","Borra el inventario y todas las métricas locales",onReset)
        Text("Tus datos permanecen en este dispositivo. No requiere internet.",fontSize=13.sp,color=Green)
    }
}

@Composable
private fun DriversScreen(
    drivers:List<RepartidorEntity>,onBack:()->Unit,onAdd:()->Unit,
    onEdit:(RepartidorEntity)->Unit,onDelete:(RepartidorEntity)->Unit,onProfile:(RepartidorEntity)->Unit
) {
    var query by remember { mutableStateOf("") }
    val shown=drivers.filter{it.nombre.contains(query,true)}
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Repartidores", fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Volver") }
        }
        Button(onClick=onAdd,colors=ButtonDefaults.buttonColors(containerColor=Gold,contentColor=Color.Black)){Text("+ Agregar repartidor")}
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Buscar repartidor")},singleLine=true)
        if(shown.isEmpty()) EmptyState("Sin repartidores", "Agrega aquí a las personas que reciben entregas.")
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(shown,key={it.id}){driver->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF202124)), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(driver.nombre, fontWeight = FontWeight.SemiBold)
                            if (!driver.activo) Text("Inactivo · editar para reactivarlo", color = Amber, fontSize = 11.sp)
                        }
                        TextButton(onClick={onProfile(driver)}){Text("Perfil",color=Green)}
                        TextButton(onClick={onEdit(driver)}) { Text("Editar", color = Gold) }
                        TextButton(onClick = { onDelete(driver) }) { Text("Eliminar", color = Red) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyHistoryScreen(startEpoch:Long,endEpoch:Long,offset:Int,receipts:List<EntradaReciente>,issues:List<SalidaReciente>,returns:List<DevolucionReciente>,adjustments:List<AjusteReciente>,onBack:()->Unit,onOffset:(Int)->Unit){
    val zone=java.time.ZoneId.systemDefault()
    val first=java.time.Instant.ofEpochMilli(startEpoch).atZone(zone).toLocalDate()
    val last=java.time.Instant.ofEpochMilli(endEpoch).atZone(zone).toLocalDate().minusDays(1)
    val formatter=java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically){Text("Historial semanal",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));TextButton(onClick=onBack){Text("Volver")}}}
        item{Text("Semana ${offset} · ${first} al ${last}",color=Gold,fontWeight=FontWeight.SemiBold)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton(onClick={onOffset(offset+1)}){Text("← Semana anterior")};TextButton(enabled=offset>1,onClick={onOffset(offset-1)}){Text("Semana más reciente →")}}}
        item{Text("Entradas (${receipts.size})",fontSize=18.sp,fontWeight=FontWeight.Bold)}
        if(receipts.isEmpty()) item{Text("Sin entradas en esta semana",color=Color.LightGray)}
        items(receipts,key={"h-receipt-${it.loteId}"}){e->HistoryCard("Entrada · ${e.productoNombre}","+${e.cantidad} u · ${java.time.Instant.ofEpochMilli(e.fechaEpochMillis).atZone(zone).format(formatter)}")}
        item{Text("Entregas (${issues.size})",fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=8.dp))}
        if(issues.isEmpty()) item{Text("Sin entregas en esta semana",color=Color.LightGray)}
        items(issues,key={"h-issue-${it.lineaId}"}){i->HistoryCard("Entrega · ${i.productoNombre}","${i.cantidad} u · carga total ${i.cantidadTotal} u · ${i.repartidorNombre ?: "Sin repartidor"} · ${formatTimestamp(i.fechaEpochMillis)}")}
        item{Text("Devoluciones (${returns.size})",fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=8.dp))}
        if(returns.isEmpty()) item{Text("Sin devoluciones en esta semana",color=Color.LightGray)}
        items(returns,key={"h-return-${it.id}"}){r->HistoryCard("Devolución · ${r.productoNombre}","+${r.cantidad} u · ${r.repartidorNombre ?: "Sin repartidor"} · ${java.time.Instant.ofEpochMilli(r.fechaEpochMillis).atZone(zone).format(formatter)}")}
        item{Text("Ajustes directos de stock (${adjustments.size})",fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=8.dp))}
        if(adjustments.isEmpty())item{Text("Sin ajustes directos en esta semana",color=Color.LightGray)}
        items(adjustments,key={"h-adjustment-${it.id}"}){a->HistoryCard("Ajuste · ${a.productoNombre}","${if(a.cantidadDelta>0) "+" else ""}${a.cantidadDelta} u · ${a.nota} · ${formatTimestamp(a.fechaEpochMillis)}")}
    }
}

@Composable
private fun DriverProfileScreen(driver:RepartidorEntity,issues:List<SalidaReciente>,onBack:()->Unit){
    var query by remember(driver.id){mutableStateOf("")}
    val filtered=issues.filter{matchesProductSearch(it.productoNombre,it.productoCodigo,query)}
    val zone=java.time.ZoneId.systemDefault()
    val formatter=java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Text(driver.nombre,fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));TextButton(onClick=onBack){Text("Volver")}}
        Text("Productos cargados, entregados y pendientes de devolución",color=Color.LightGray,fontSize=12.sp)
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Buscar producto en sus entregas")},singleLine=true)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(filtered.isEmpty()) item{EmptyState("Sin entregas",if(query.isBlank())"Este perfil aún no tiene movimientos." else "No hay coincidencias.")}
            items(filtered,key={it.lineaId}){i->HistoryCard("${i.productoNombre} · ${i.cantidad} u","Carga #${i.salidaId} · total ${i.cantidadTotal} u · ${formatTimestamp(i.fechaEpochMillis)}\n${i.repartidorNombre ?: driver.nombre} · entregado ${i.cantidad} · devuelto ${i.devuelto} · en su poder ${i.pendiente}")}
        }
    }
}

private fun formatTimestamp(millis:Long):String=java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))

@Composable
private fun HistoryCard(title:String,subtitle:String){
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF202124)),shape=RoundedCornerShape(14.dp)){
        Column(Modifier.padding(12.dp)){Text(title,fontWeight=FontWeight.SemiBold);Text(subtitle,color=Color.LightGray,fontSize=12.sp)}
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color(0xFF202124)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(subtitle, color = Color.LightGray, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("📦", fontSize = 34.sp)
        Text(title, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color.LightGray, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDialog(
    kind:String,products:List<ProductoEntity>,drivers:List<RepartidorEntity>,trailers:List<TrailerProgramadoEntity>,
    editingDriver:RepartidorEntity?,editingProduct:ProductoEntity?,onClose:()->Unit,
    onSaveProduct:(Long?,String,String,String,Long,Long,Long)->Unit,
    onReceive:(Long,Long)->Unit,onIssue:(List<Pair<Long,Long>>,RepartidorEntity?)->Unit,
    onSaveDriver:(String)->Unit,onUpdateDriver:(Long,String)->Unit,onTrailer:(Int,Boolean)->Unit
){
    var name by remember(kind,editingProduct?.id,editingDriver?.id){mutableStateOf(editingProduct?.nombre?:editingDriver?.nombre.orEmpty())}
    var code by remember(kind,editingProduct?.id){mutableStateOf(editingProduct?.codigo.orEmpty())}
    var unit by remember(kind,editingProduct?.id){mutableStateOf(editingProduct?.unidad?:"unidad")}
    var min by remember(kind,editingProduct?.id){mutableStateOf((editingProduct?.minimo?:5L).toString())}
    var max by remember(kind,editingProduct?.id){mutableStateOf((editingProduct?.maximo?:20L).toString())}
    var quantity by remember(kind,editingProduct?.id){mutableStateOf((editingProduct?.cantidadActual?:0L).toString())}
    var selectedProduct by remember(products,editingProduct?.id){mutableLongStateOf(editingProduct?.id?:products.firstOrNull()?.id?:0L)}
    val issueLines=remember(kind,products){mutableStateListOf(BatchLine(products.firstOrNull()?.id?:0L,""))}
    var driverIndex by remember { mutableIntStateOf(-1) }
    var openProduct by remember { mutableStateOf(false) }
    var openDriver by remember { mutableStateOf(false) }
    var openLineIndex by remember { mutableIntStateOf(-1) }
    val minimum=parseWholeNumber(min);val maximum=parseWholeNumber(max);val amount=parseWholeNumber(quantity)
    val title=when(kind){"producto"->if(editingProduct==null)"Nuevo producto" else "Editar producto";"entrada"->"Recibir producto";"salida"->"Carga multi-producto";"repartidor"->if(editingDriver==null)"Nuevo repartidor" else "Editar repartidor";else->"Llegadas de trailers"}

    AlertDialog(onDismissRequest=onClose,title={Text(title)},text={
        Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            when(kind){
                "producto"->{
                    Field("Nombre del producto",name,{name=it})
                    Field("Código (opcional)",code,{code=it})
                    Field("Unidad (pza, caja, kg…)",unit,{unit=it})
                    Field("Stock mínimo (alerta configurable)",min,{min=it},numeric=true)
                    Field("Stock máximo deseado",max,{max=it},numeric=true)
                    Field(if(editingProduct==null)"Inventario inicial (opcional)" else "Stock actual (ajuste directo)",quantity,{quantity=it},numeric=true)
                    Text("El ajuste directo queda como movimiento de inventario inicial/ajuste, sin usar ‘Recibir lote’. No permite reducir por debajo de lo ya asignado.",fontSize=12.sp,color=Color.LightGray)
                }
                "entrada"->{
                    ProductDropdown(products,selectedProduct,openProduct,{openProduct=it}){selectedProduct=it;openProduct=false}
                    Field("Cantidad",quantity,{quantity=it},numeric=true)
                }
                "salida"->{
                    if(products.isEmpty()) Text("Primero registra un producto.",color=Red) else {
                        issueLines.forEachIndexed{index,line->
                            Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF202124))){Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                                Row(verticalAlignment=Alignment.CenterVertically){Text("Producto ${index+1}",modifier=Modifier.weight(1f),fontWeight=FontWeight.SemiBold);if(issueLines.size>1)TextButton(onClick={issueLines.removeAt(index)}){Text("Quitar",color=Red)}}
                                ExposedDropdownMenuBox(expanded=openLineIndex==index,onExpandedChange={openLineIndex=if(openLineIndex==index)-1 else index}){
                                    OutlinedTextField(value=products.firstOrNull{it.id==line.productId}?.let{"${it.nombre} · stock ${it.cantidadActual}"}?:"Producto",onValueChange={},readOnly=true,label={Text("Producto")},modifier=Modifier.menuAnchor().fillMaxWidth())
                                    ExposedDropdownMenu(expanded=openLineIndex==index,onDismissRequest={openLineIndex=-1}){products.forEach{product->DropdownMenuItem(text={Text("${product.nombre} · stock ${product.cantidadActual}")},onClick={issueLines[index]=line.copy(productId=product.id);openLineIndex=-1})}}
                                }
                                Field("Cantidad",line.quantity,{issueLines[index]=line.copy(quantity=it)},numeric=true)
                            }}
                        }
                        TextButton(onClick={issueLines.add(BatchLine(products.first().id,""))}){Text("+ Agregar otro producto",color=Gold)}
                        ExposedDropdownMenuBox(expanded=openDriver,onExpandedChange={openDriver=it}){
                            OutlinedTextField(value=drivers.getOrNull(driverIndex)?.nombre?:"Sin repartidor",onValueChange={},readOnly=true,label={Text("Repartidor asignado")},modifier=Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(expanded=openDriver,onDismissRequest={openDriver=false}) {
                                DropdownMenuItem(text={Text("Sin repartidor")},onClick={driverIndex=-1;openDriver=false})
                                drivers.forEachIndexed { index, driver -> DropdownMenuItem(text={Text(driver.nombre)},onClick={driverIndex=index;openDriver=false}) }
                            }
                        }
                        Text("Se guarda una sola salida auditada con fecha/hora, repartidor y total de unidades; cada producto conserva su detalle PEPS.",fontSize=12.sp,color=Color.LightGray)
                    }
                }
                "repartidor"->Field("Nombre del repartidor",name,{name=it})
                "trailers"->{Text("Marca los días habituales de llegada. Se cuentan días únicos por semana.");(1..7).forEach{day->val enabled=trailers.any{it.diaSemana==day&&it.activo};Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=enabled,onCheckedChange={onTrailer(day,it)});Text(DayOfWeek.of(day).name.lowercase().replaceFirstChar(Char::uppercase))}}}
            }
        }
    },confirmButton={
        when(kind){
            "producto"->TextButton(enabled=name.isNotBlank()&&minimum!=null&&maximum!=null&&maximum>=minimum&&amount!=null&&amount>=0L,onClick={onSaveProduct(editingProduct?.id,name,code,unit,minimum!!,maximum!!,amount!!)} ){Text("Guardar")}
            "entrada"->TextButton(enabled=products.any{it.id==selectedProduct}&&amount!=null&&amount>0L,onClick={onReceive(selectedProduct,amount!!)} ){Text("Confirmar recepción")}
            "salida"->{val quantities=issueLines.map{parseWholeNumber(it.quantity)};val dispatch=issueLines.mapIndexedNotNull{idx,line->quantities[idx]?.let{line.productId to it}};val available=products.associate{it.id to it.cantidadActual};val valid=dispatch.size==issueLines.size&&isValidMultiProductIssue(dispatch,available);TextButton(enabled=valid,onClick={onIssue(dispatch,drivers.getOrNull(driverIndex))}){Text("Confirmar carga")}}
            "repartidor"->TextButton(enabled=name.isNotBlank(),onClick={if(editingDriver==null)onSaveDriver(name)else onUpdateDriver(editingDriver.id,name)}){Text("Guardar")}
            else->TextButton(onClick=onClose){Text("Listo")}
        }
    },dismissButton={TextButton(onClick=onClose){Text("Cancelar")}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductDropdown(products:List<ProductoEntity>,selected:Long,expanded:Boolean,onExpanded:(Boolean)->Unit,onSelected:(Long)->Unit){
    ExposedDropdownMenuBox(expanded=expanded,onExpandedChange=onExpanded){
        OutlinedTextField(value=products.firstOrNull{it.id==selected}?.let{"${it.nombre} · stock ${it.cantidadActual}"}?:"Producto",onValueChange={},readOnly=true,label={Text("Producto")},modifier=Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded=expanded,onDismissRequest={onExpanded(false)}){products.forEach{p->DropdownMenuItem(text={Text("${p.nombre} · stock ${p.cantidadActual}")},onClick={onSelected(p.id)})}}
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, numeric: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue -> if (!numeric || isWholeNumberInput(newValue)) onChange(newValue) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth()
    )
}
