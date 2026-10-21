package com.example.kisanmandi

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------- Milestone 3: Price alerts ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(vm: AppViewModel) {
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Alert?>(null) }
    var toDelete by remember { mutableStateOf<Alert?>(null) }
    val mine = vm.myAlerts

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${vm.triggeredCount} triggered • ${mine.size} total", Modifier.weight(1f))
            Button(onClick = { editing = null; showForm = true }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("New alert")
            }
        }
        PullToRefreshBox(
            isRefreshing = vm.loading,
            onRefresh = { vm.refresh() }, // alerts are re-checked on every refresh
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (mine.isEmpty()) item { Text("No alerts yet. Tap New alert to add one.") }
                items(mine, key = { it.id ?: "" }) { a ->
                    val hit = vm.isTriggered(a)
                    val latest = vm.latest(a.crop_id, a.market_id)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (hit) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${vm.cropName(a.crop_id)} @ ${vm.marketName(a.market_id)}",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "When price is ${a.condition.lowercase()} ${rs(a.target_price)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Latest price: ${latest?.let { rs(it) } ?: "-"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (hit) {
                                        Text(
                                            "🔔 Target reached!",
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Switch(
                                    checked = a.active_status,
                                    onCheckedChange = { vm.saveAlert(a.copy(active_status = it)) }
                                )
                            }
                            Row {
                                TextButton(onClick = { editing = a; showForm = true }) { Text("Edit") }
                                TextButton(onClick = { toDelete = a }) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForm) AlertForm(vm, editing) { showForm = false }

    toDelete?.let { a ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete alert?") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAlert(a); toDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AlertForm(vm: AppViewModel, existing: Alert?, onClose: () -> Unit) {
    var crop by remember { mutableStateOf(existing?.crop_id ?: vm.crops.firstOrNull()?.id) }
    var market by remember { mutableStateOf(existing?.market_id ?: vm.markets.firstOrNull()?.id) }
    var cond by remember { mutableStateOf(existing?.condition ?: "Above") }
    var target by remember { mutableStateOf(existing?.target_price?.let { fmt(it) } ?: "") }
    var err by remember { mutableStateOf<String?>(null) }

    val c = crop
    val m = market
    val latest = if (c != null && m != null) vm.latest(c, m) else null

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (existing == null) "New price alert" else "Edit alert") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker("Crop", vm.crops.map { it.id to it.name }, crop, { crop = it }, Modifier.fillMaxWidth())
                Picker("Market", vm.markets.map { it.id to it.name }, market, { market = it }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Above", "Below").forEach { x ->
                        FilterChip(selected = cond == x, onClick = { cond = x }, label = { Text(x) })
                    }
                }
                Field(target, { target = it }, "Target price (Rs)", err, KeyboardType.Number)
                latest?.let { Text("Latest price: ${rs(it)}", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = target.toDoubleOrNull()
                when {
                    c == null || m == null -> err = "Select crop and market"
                    t == null || t <= 0 -> err = "Enter a price greater than 0"
                    else -> {
                        vm.saveAlert(
                            Alert(
                                id = existing?.id,
                                user_id = vm.user!!.id!!,
                                crop_id = c,
                                market_id = m,
                                condition = cond,
                                target_price = t,
                                active_status = existing?.active_status ?: true
                            )
                        )
                        onClose()
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

// ---------- Milestone 5: Profit calculator ----------

@Composable
fun ProfitScreen(vm: AppViewModel, nav: Nav) {
    var crop by rememberSaveable { mutableStateOf(vm.crops.firstOrNull()?.id) }
    var market by rememberSaveable { mutableStateOf(vm.markets.firstOrNull()?.id) }
    var qty by rememberSaveable { mutableStateOf("100") }
    var cost by rememberSaveable { mutableStateOf("") }
    var shift by rememberSaveable { mutableFloatStateOf(0f) }

    val c = crop
    val m = market
    val base = if (c != null && m != null) vm.latest(c, m) else null
    val q = qty.toDoubleOrNull() ?: 0.0
    val cpu = cost.toDoubleOrNull() ?: 0.0

    Scaffold(topBar = { BackBar("Profit calculator", nav.back) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Picker("Crop", vm.crops.map { it.id to it.name }, crop, { crop = it }, Modifier.fillMaxWidth())
            Picker("Market", vm.markets.map { it.id to it.name }, market, { market = it }, Modifier.fillMaxWidth())
            Text("Today's market price: ${base?.let { rs(it) } ?: "-"} per unit")

            Field(qty, { qty = it }, "Quantity (units)", if (q <= 0) "Enter quantity greater than 0" else null, KeyboardType.Number)
            Field(cost, { cost = it }, "Cost of production per unit (Rs)", null, KeyboardType.Number)

            Text(
                "What-if price change: ${String.format(Locale.US, "%+d", shift.roundToInt())}%",
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = shift,
                onValueChange = { shift = it.roundToInt().toFloat() },
                valueRange = -20f..20f,
                steps = 39
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("-20%")
                TextButton(onClick = { shift = 0f }) { Text("Reset") }
                Text("+20%")
            }

            if (base != null && q > 0) {
                val price = base * (1 + shift / 100)
                val revenue = price * q
                val total = cpu * q
                val profit = revenue - total
                val margin = if (revenue > 0) profit / revenue * 100 else 0.0
                val color = if (profit >= 0) Green else Red

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoRow("Selling price / unit", rs(price))
                        InfoRow("Revenue", rs(revenue))
                        InfoRow("Total cost", rs(total))
                        HorizontalDivider()
                        InfoRow(if (profit >= 0) "Profit" else "Loss", rs(abs(profit)), color)
                        InfoRow("Margin", String.format(Locale.US, "%.1f%%", margin), color)
                    }
                }
            }
        }
    }
}
