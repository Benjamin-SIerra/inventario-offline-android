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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.RoomDatabase
import com.benjamin.inventario.data.*
import com.benjamin.inventario.data.local.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek

private val Gold=Color(0xFFC8A84C); private val Green=Color(0xFF4CAF7D); private val Amber=Color(0xFFE6AE49); private val Red=Color(0xFFE05B62)

@Composable fun InventoryApp(db:InventoryDatabase){
    val vm:InventoryViewModel=viewModel(factory=InventoryViewModel.factory(InventoryRepository(db)))
    val products by vm.products.collectAsState(); val contacts by vm.contacts.collectAsState(); val drivers by vm.drivers.collectAsState()
    val low by vm.low.collectAsState(); val empty by vm.empty.collectAsState(); val trailerDays by vm.trailerDays.collectAsState(); val trailers by vm.trailers.collectAsState()
    val receipts by vm.receipts.collectAsState(); val issues by vm.issues.collectAsState(); val recentIssues by vm.recentIssues.collectAsState(); val moved by vm.moved.collectAsState(); val rank by vm.ranking.collectAsState(); val period by vm.selectedPeriod.collectAsState(); val suggestions by vm.suggestions.collectAsState()
    var tab by remember { mutableIntStateOf(0) }; var dialog by remember { mutableStateOf("") }; var toast by remember { mutableStateOf("") }; var returnLine by remember { mutableStateOf<SalidaReciente?>(null) }; var returnQty by remember { mutableStateOf("") }
    LaunchedEffect(Unit){vm.message.collect{toast=it}}
    Scaffold(bottomBar={ NavigationBar { listOf("Inicio","Productos","Movimientos","Más").forEachIndexed{i,s->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(listOf("⌂","▤","⇄","•••")[i])},label={Text(s)}) } } }, floatingActionButton={ if(tab==0||tab==2) FloatingActionButton(onClick={tab=2},containerColor=Gold,contentColor=Color.Black){Text("+",fontSize=28.sp)} }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(Color(0xFF101113))) {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp, vertical=16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("INVENTARIO",color=Gold,fontWeight=FontWeight.Bold,letterSpacing=2.sp);Text("Control simple, siempre local",color=Color.LightGray,fontSize=12.sp)};Text("● SIN CONEXIÓN",color=Green,fontSize=10.sp,fontWeight=FontWeight.Bold)}
            when(tab){
                0->Dashboard(products.size,low,empty,trailerDays,receipts,issues,moved,rank,period,vm,suggestions)
                1->ProductScreen(products,onAdd={dialog="producto"})
                2->MovementsScreen(products,contacts,drivers,recentIssues,onReceive={dialog="entrada"},onIssue={dialog="salida"},onReturn={returnLine=it;returnQty=""})
                else->MoreScreen(contacts,drivers, onContact={dialog="contacto"},onDriver={dialog="repartidor"},onTrailers={dialog="trailers"})
            }
        }
        if(dialog.isNotEmpty()) EntryDialog(dialog,products,contacts,drivers,trailers,onClose={dialog=""},onSaveProduct={n,u,m,x->vm.saveProduct(n,u,m,x);dialog=""},onReceive={id,q,c->vm.receive(id,q,c);dialog=""},onIssue={id,q,c,n,d->vm.issue(id,q,c,n,d);dialog=""},onContact={n,r->vm.addContact(n,r);dialog=""},onDriver={n->vm.addDriver(n);dialog=""},onTrailer={d,on->vm.trailer(d,on)})
        if(returnLine!=null) AlertDialog(onDismissRequest={returnLine=null},title={Text("Registrar devolución")},text={Column{Text("${returnLine!!.productoNombre} · ${returnLine!!.destinatarioNombre}");Text("Entregado: ${returnLine!!.cantidad} u");Field("Cantidad que regresa",returnQty,{returnQty=it},true)}},confirmButton={TextButton(onClick={vm.returnUnits(returnLine!!.lineaId,returnQty.toLongOrNull()?:0);returnLine=null}){Text("Guardar devolución")}},dismissButton={TextButton(onClick={returnLine=null}){Text("Cancelar")}})
        if(toast.isNotBlank()) AlertDialog(onDismissRequest={toast=""},title={Text("Inventario")},text={Text(toast)},confirmButton={TextButton(onClick={toast=""}){Text("Entendido")}})
    }
}

@Composable private fun Dashboard(count:Int,low:Int,empty:Int,trailer:Int,receipts:Int,issues:Int,moved:Long,rank:List<ProductoMovido>,period:Period,vm:InventoryViewModel,suggestions:List<OrderSuggestion>){
    LaunchedEffect(Unit){vm.refreshSuggestions()}
    LazyColumn(contentPadding=PaddingValues(horizontal=16.dp, vertical=6.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item { Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF202124)),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(18.dp)){Text("Resumen de hoy",color=Color.White,fontSize=21.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(14.dp));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Metric("Productos",count.toString(),Color.White,Modifier.weight(1f));Metric("Entradas",receipts.toString(),Green,Modifier.weight(1f));Metric("Salidas",issues.toString(),Gold,Modifier.weight(1f))}}} }
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Metric("Bajo mínimo",low.toString(),Amber,Modifier.weight(1f));Metric("Agotados",empty.toString(),Red,Modifier.weight(1f));Metric("Trailers / semana", "$trailer de 7",Gold,Modifier.weight(1f))} }
        item { Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF202124)),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(16.dp)){Text("Rotación",fontWeight=FontWeight.Bold,fontSize=18.sp);Row{Period.entries.forEach{p->FilterChip(selected=period==p,onClick={vm.period(p)},label={Text(if(p==Period.SEMANA)"Esta semana" else "Este mes")},modifier=Modifier.padding(end=8.dp))}};Text("$moved unidades entregadas",color=Gold,fontSize=20.sp,fontWeight=FontWeight.Bold);SimpleBars(rank.map{it.productoNombre to it.unidades})}} }
        item { Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF202124)),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(16.dp)){Text("Productos con más movimiento",fontWeight=FontWeight.Bold);if(rank.isEmpty())Text("Aún no hay salidas en este periodo.",color=Color.LightGray,modifier=Modifier.padding(top=8.dp));rank.take(5).forEach{Row(Modifier.fillMaxWidth().padding(vertical=5.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(it.productoNombre);Text("${it.unidades} u",color=Gold)}}}} }
        item { Row(verticalAlignment=Alignment.CenterVertically){Text("Sugerencias de pedido",fontWeight=FontWeight.Bold,fontSize=18.sp,modifier=Modifier.weight(1f));TextButton(onClick=vm::refreshSuggestions){Text("Actualizar")}} }
        if(suggestions.isEmpty()) item {Text("Sin faltantes estimados. Las sugerencias usan salida de 30 días y 7 días de plazo estimado.",color=Color.LightGray,fontSize=13.sp)}
        items(suggestions){s->Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF202124))){Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(s.name,fontWeight=FontWeight.SemiBold);Text("Stock ${s.stock} · objetivo ${s.target} · consumo estimado ${"%.1f".format(s.unitsPerDay)}/día",fontSize=12.sp,color=Color.LightGray)};Text("Pedir ${s.suggested}",color=Amber,fontWeight=FontWeight.Bold)}}}
    }
}
@Composable private fun Metric(title:String,value:String,color:Color,modifier:Modifier=Modifier){Card(modifier,colors=CardDefaults.cardColors(containerColor=Color(0xFF202124)),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(13.dp)){Text(title,color=Color.LightGray,fontSize=11.sp);Text(value,color=color,fontSize=20.sp,fontWeight=FontWeight.Bold,maxLines=1)}}}
@Composable private fun SimpleBars(data:List<Pair<String,Long>>){
    if(data.isEmpty()) return
    val maximum=(data.maxOfOrNull{it.second} ?: 1L).coerceAtLeast(1L)
    Column(Modifier.fillMaxWidth().padding(top=10.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        data.take(5).forEach { (name, amount) ->
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(name.take(14), fontSize=11.sp, modifier=Modifier.width(95.dp), maxLines=1)
                Box(Modifier.weight(1f).height(12.dp).background(Color(0xFF34363A), RoundedCornerShape(8.dp))) {
                    Box(Modifier.fillMaxWidth((amount.toFloat()/maximum).coerceIn(0.04f,1f)).fillMaxHeight().background(Gold, RoundedCornerShape(8.dp)))
                }
                Text(" $amount", fontSize=11.sp, color=Color.LightGray)
            }
        }
    }
}
@Composable private fun ProductScreen(products:List<ProductoEntity>,onAdd:()->Unit){Column(Modifier.fillMaxSize().padding(horizontal=16.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("Productos",fontSize=23.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Button(onClick=onAdd,colors=ButtonDefaults.buttonColors(containerColor=Gold,contentColor=Color.Black)){Text("+ Agregar")}};if(products.isEmpty()) EmptyState("Tu almacén está vacío","Agrega tu primer producto para empezar.");LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(products,key={it.id}){p->val alert=when{p.cantidadActual==0L->"AGOTADO";p.cantidadActual<=p.minimo->"BAJO";p.cantidadActual<=p.minimo+maxOf(1L,(p.minimo+4)/5)->"CERCA DEL MÍNIMO";else->"NORMAL"};val c=when(alert){"AGOTADO"->Red;"BAJO"->Red;"CERCA DEL MÍNIMO"->Amber;else->Green};Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF202124)),shape=RoundedCornerShape(16.dp)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.nombre,fontSize=17.sp,fontWeight=FontWeight.SemiBold);Text("Mín. ${p.minimo} · Máx. ${p.maximo} ${p.unidad}",color=Color.LightGray,fontSize=12.sp)};Column(horizontalAlignment=Alignment.End){Text("${p.cantidadActual} ${p.unidad}",fontSize=20.sp,fontWeight=FontWeight.Bold);Text(alert,color=c,fontSize=10.sp,fontWeight=FontWeight.Bold)}}}}}}}
@Composable private fun MovementsScreen(products:List<ProductoEntity>,contacts:List<ContactoEntity>,drivers:List<RepartidorEntity>,recent:List<SalidaReciente>,onReceive:()->Unit,onIssue:()->Unit,onReturn:(SalidaReciente)->Unit){
 Column(Modifier.fillMaxSize().padding(16.dp)){
  Text("Movimientos",fontSize=24.sp,fontWeight=FontWeight.Bold);Text("Las salidas consumen primero el lote más antiguo.",color=Color.LightGray,fontSize=13.sp);Spacer(Modifier.height(16.dp))
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Button(onClick=onReceive,modifier=Modifier.weight(1f).height(58.dp),colors=ButtonDefaults.buttonColors(containerColor=Green)){Text("↓  Recibir")};Button(onClick=onIssue,modifier=Modifier.weight(1f).height(58.dp),colors=ButtonDefaults.buttonColors(containerColor=Gold,contentColor=Color.Black)){Text("↑  Entregar")}}
  Text("${products.size} productos · ${contacts.size} contactos · ${drivers.size} repartidores",color=Color.LightGray,fontSize=12.sp,modifier=Modifier.padding(vertical=12.dp));Text("Últimas entregas · devolución trazable",fontWeight=FontWeight.Bold,fontSize=17.sp)
  LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.weight(1f)){items(recent,key={it.lineaId}){s->Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFF202124))){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(s.productoNombre,fontWeight=FontWeight.SemiBold);Text("${s.cantidad} u · ${s.destinatarioNombre}",fontSize=12.sp,color=Color.LightGray);Text("Repartidor: ${s.repartidorNombre?:"—"}",fontSize=11.sp,color=Color.LightGray)};TextButton(onClick={onReturn(s)}){Text("Devolver",color=Gold)}}}}}
 }
}
@Composable private fun MoreScreen(contacts:List<ContactoEntity>,drivers:List<RepartidorEntity>,onContact:()->Unit,onDriver:()->Unit,onTrailers:()->Unit){Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Configuración",fontSize=24.sp,fontWeight=FontWeight.Bold);ActionCard("Proveedores y clientes","${contacts.size} contactos registrados",onContact);ActionCard("Repartidores","${drivers.size} repartidores registrados",onDriver);ActionCard("Días de trailer","Programa llegadas semanales",onTrailers);Text("Tus datos permanecen en este dispositivo. No requiere internet.",fontSize=13.sp,color=Green)} }
@Composable private fun ActionCard(title:String,subtitle:String,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick = onClick),colors=CardDefaults.cardColors(containerColor=Color(0xFF202124)),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(18.dp)){Text(title,fontWeight=FontWeight.Bold,fontSize=17.sp);Text(subtitle,color=Color.LightGray,fontSize=13.sp)}}}
@Composable private fun EmptyState(title:String,sub:String){Column(Modifier.fillMaxWidth().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("📦",fontSize=34.sp);Text(title,fontWeight=FontWeight.Bold);Text(sub,color=Color.LightGray,fontSize=13.sp)}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EntryDialog(kind:String,products:List<ProductoEntity>,contacts:List<ContactoEntity>,drivers:List<RepartidorEntity>,trailers:List<TrailerProgramadoEntity>,onClose:()->Unit,onSaveProduct:(String,String,Long,Long)->Unit,onReceive:(Long,Long,ContactoEntity?)->Unit,onIssue:(Long,Long,ContactoEntity?,String,RepartidorEntity?)->Unit,onContact:(String,String)->Unit,onDriver:(String)->Unit,onTrailer:(Int,Boolean)->Unit){
    var name by remember{mutableStateOf("")};var unit by remember{mutableStateOf("unidad")};var min by remember{mutableStateOf("5")};var max by remember{mutableStateOf("20")};var qty by remember{mutableStateOf("")};var selected by remember{mutableStateOf(products.firstOrNull()?.id?:0L)};var recipient by remember{mutableStateOf("")};var contactIx by remember{mutableIntStateOf(0)};var driverIx by remember{mutableIntStateOf(0)};var role by remember{mutableStateOf("PROVEEDOR")};var openProduct by remember{mutableStateOf(false)};var openContact by remember{mutableStateOf(false)};var openDriver by remember{mutableStateOf(false)}
    val title=when(kind){"producto"->"Nuevo producto";"entrada"->"Recibir producto";"salida"->"Registrar entrega";"movimiento"->"Nuevo movimiento";"contacto"->"Nuevo contacto";"repartidor"->"Nuevo repartidor";else->"Llegadas de trailers"}
    if(kind=="movimiento"){AlertDialog(onDismissRequest=onClose,title={Text(title)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={/* dismiss by calling receive dialog state is handled in root via callback below */}){Text("Elige Entradas/Salidas en Movimientos")};Text("Cierra y usa los botones grandes de la pestaña Movimientos.")}},confirmButton={TextButton(onClick=onClose){Text("Cerrar")}});return}
    AlertDialog(onDismissRequest=onClose,title={Text(title)},text={Column(Modifier.heightIn(max=490.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        when(kind){
            "producto"->{Field("Nombre del producto",name,{name=it});Field("Unidad (pza, caja, kg…)",unit,{unit=it});Field("Stock mínimo",min,{min=it},true);Field("Stock máximo deseado",max,{max=it},true)}
            "entrada","salida"->{if(products.isEmpty())Text("Primero registra un producto.",color=Red) else {ExposedDropdownMenuBox(expanded=openProduct,onExpandedChange={openProduct=it}){OutlinedTextField(products.firstOrNull{it.id==selected}?.nombre?:"Producto",{},readOnly=true,label={Text("Producto")},modifier=Modifier.menuAnchor().fillMaxWidth());ExposedDropdownMenu(expanded=openProduct,onDismissRequest={openProduct=false}){products.forEach{p->DropdownMenuItem(text={Text("${p.nombre} · stock ${p.cantidadActual}")},onClick={selected=p.id;openProduct=false})}}};Field("Cantidad",qty,{qty=it},true)}
                if(kind=="entrada"){val suppliers=contacts.filter{it.rol!="CLIENTE"};if(suppliers.isNotEmpty()) ExposedDropdownMenuBox(expanded=openContact,onExpandedChange={openContact=it}){OutlinedTextField(suppliers.getOrNull(contactIx)?.nombre?:"Sin proveedor",{},readOnly=true,label={Text("Proveedor (opcional)")},modifier=Modifier.menuAnchor().fillMaxWidth());ExposedDropdownMenu(expanded=openContact,onDismissRequest={openContact=false}){DropdownMenuItem(text={Text("Sin proveedor")},onClick={contactIx= -1;openContact=false});suppliers.forEachIndexed{i,c->DropdownMenuItem(text={Text(c.nombre)},onClick={contactIx=i;openContact=false})}}}}
                else {Field("Cliente / destinatario",recipient,{recipient=it});val customers=contacts.filter{it.rol!="PROVEEDOR"};if(customers.isNotEmpty())ExposedDropdownMenuBox(expanded=openContact,onExpandedChange={openContact=it}){OutlinedTextField(customers.getOrNull(contactIx)?.nombre?:"Seleccionar cliente (opcional)",{},readOnly=true,label={Text("Cliente guardado")},modifier=Modifier.menuAnchor().fillMaxWidth());ExposedDropdownMenu(expanded=openContact,onDismissRequest={openContact=false}){customers.forEachIndexed{i,c->DropdownMenuItem(text={Text(c.nombre)},onClick={contactIx=i;recipient=c.nombre;openContact=false})}}};if(drivers.isNotEmpty())ExposedDropdownMenuBox(expanded=openDriver,onExpandedChange={openDriver=it}){OutlinedTextField(drivers.getOrNull(driverIx)?.nombre?:"Sin repartidor",{},readOnly=true,label={Text("Repartidor (opcional)")},modifier=Modifier.menuAnchor().fillMaxWidth());ExposedDropdownMenu(expanded=openDriver,onDismissRequest={openDriver=false}){DropdownMenuItem(text={Text("Sin repartidor")},onClick={driverIx= -1;openDriver=false});drivers.forEachIndexed{i,d->DropdownMenuItem(text={Text(d.nombre)},onClick={driverIx=i;openDriver=false})}}};Text("El descuento se asigna automáticamente a los lotes más antiguos (PEPS).",color=Color.LightGray,fontSize=12.sp)}
            }
            "contacto"->{Field("Nombre",name,{name=it});Row{listOf("PROVEEDOR","CLIENTE","AMBOS").forEach{FilterChip(selected=role==it,onClick={role=it},label={Text(it)})}}}
            "repartidor"->Field("Nombre del repartidor",name,{name=it})
            "trailers"->{Text("Marca los días habituales de llegada. Se cuentan días únicos por semana.");(1..7).forEach{d->val on=trailers.any{it.diaSemana==d&&it.activo};Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=on,onCheckedChange={onTrailer(d,it)});Text(DayOfWeek.of(d).name.lowercase().replaceFirstChar(Char::uppercase))}}}
        }
    }},confirmButton={ when(kind){"producto"->TextButton(onClick={onSaveProduct(name,unit,min.toLongOrNull()?:0,max.toLongOrNull()?:0)}){Text("Guardar")};"entrada"->TextButton(onClick={onReceive(selected,qty.toLongOrNull()?:0,contacts.filter{it.rol!="CLIENTE"}.getOrNull(contactIx))}){Text("Confirmar recepción")};"salida"->TextButton(onClick={onIssue(selected,qty.toLongOrNull()?:0,contacts.filter{it.rol!="PROVEEDOR"}.getOrNull(contactIx),recipient,drivers.getOrNull(driverIx))}){Text("Confirmar salida PEPS")};"contacto"->TextButton(onClick={onContact(name,role)}){Text("Guardar")};"repartidor"->TextButton(onClick={onDriver(name)}){Text("Guardar")};else->TextButton(onClick=onClose){Text("Listo")} }},dismissButton={TextButton(onClick=onClose){Text("Cancelar")}})
}
@Composable private fun Field(label:String,value:String,onChange:(String)->Unit,numeric:Boolean=false){OutlinedTextField(value,onChange,label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Number else KeyboardType.Text),modifier=Modifier.fillMaxWidth())}
