package com.benjamin.inventario.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benjamin.inventario.data.local.JornadaRutasEntity
import com.benjamin.inventario.data.local.RutaDiariaEntity
import com.benjamin.inventario.domain.MAX_RUTAS_POR_JORNADA
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RouteGold = Color(0xFFC8A84C)
private val RouteGreen = Color(0xFF4CAF7D)
private val RouteSurface = Color(0xFF202124)
private val RouteMuted = Color(0xFFB7B7B7)

@Composable
fun RouteControlScreen(
    date: String,
    day: JornadaRutasEntity?,
    routes: List<RutaDiariaEntity>,
    onBack: () -> Unit,
    onConfigure: (Int) -> Unit,
    onToggle: (Int, Boolean) -> Unit
) {
    var countInput by rememberSaveable(date) { mutableStateOf("") }
    LaunchedEffect(day?.cantidadRutas) {
        day?.let { countInput = it.cantidadRutas.toString() }
    }

    val parsedCount = countInput.toIntOrNull()
    val validCount = parsedCount != null && parsedCount in 1..MAX_RUTAS_POR_JORNADA
    val completedCount = routes.count { it.completada }
    val totalCount = day?.cantidadRutas ?: 0
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
    val locale = Locale("es", "MX")
    val dateLabel = rememberSaveable(date) {
        LocalDate.parse(date)
            .format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", locale))
            .replaceFirstChar { it.titlecase(locale) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF101113)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "route_title") {
            Column {
                TextButton(onClick = onBack) { Text("← Más", color = RouteGold) }
                Text("Control diario de rutas", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(dateLabel, color = RouteMuted, fontSize = 13.sp)
            }
        }

        item(key = "route_setup") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RouteSurface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Rutas para surtir", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = countInput,
                        onValueChange = { countInput = it.filter(Char::isDigit).take(4) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Cantidad de rutas") },
                        placeholder = { Text("Ej. 20") },
                        singleLine = true,
                        isError = countInput.isNotBlank() && !validCount,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            Text(
                                if (countInput.isNotBlank() && !validCount)
                                    "Ingresa un valor entre 1 y $MAX_RUTAS_POR_JORNADA."
                                else "Al ajustar el total, se conserva el avance de las rutas existentes."
                            )
                        }
                    )
                    Button(
                        onClick = { parsedCount?.let(onConfigure) },
                        enabled = validCount,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RouteGold,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(if (day == null) "Generar rutas" else "Guardar cantidad")
                    }
                }
            }
        }

        if (day == null || totalCount == 0) {
            item(key = "route_empty") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = RouteSurface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        "Configura cuántas rutas se surtirán hoy para crear la lista de pendientes.",
                        modifier = Modifier.padding(16.dp),
                        color = RouteMuted
                    )
                }
            }
        } else {
            item(key = "route_progress") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = RouteSurface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Progreso de la jornada", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text("$completedCount / $totalCount", color = RouteGold, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth().height(9.dp)
                                .clip(RoundedCornerShape(50)).background(Color(0xFF393B3F))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(9.dp)
                                    .clip(RoundedCornerShape(50)).background(RouteGreen)
                            )
                        }
                        Text("Rutas completadas: $completedCount / $totalCount", color = RouteMuted, fontSize = 13.sp)
                    }
                }
            }

            items(routes, key = { it.numeroRuta }) { route ->
                RouteChecklistItem(route = route, onToggle = { checked -> onToggle(route.numeroRuta, checked) })
            }
        }
    }
}

@Composable
private fun RouteChecklistItem(route: RutaDiariaEntity, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = RouteSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = route.completada,
                onCheckedChange = onToggle,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Ruta ${route.numeroRuta}" },
                colors = CheckboxDefaults.colors(
                    checkedColor = RouteGreen,
                    checkmarkColor = Color.Black
                )
            )
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    text = "Ruta ${route.numeroRuta}",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (route.completada) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (route.completada) RouteGreen else Color.White
                )
                Text(
                    text = if (route.completada) "Surtida" else "Pendiente",
                    color = if (route.completada) RouteGreen else RouteMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}
