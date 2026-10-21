package com.example.kisanmandi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
        OrangeHeader {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "🔔 Price Alerts", color = Color.White,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${vm.triggeredCount} triggered • ${mine.size} total",
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                Button(
                    onClick = { editing = null; showForm = true },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Orange)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("New", fontWeight = FontWeight.Bold)
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = vm.loading,
            onRefresh = { vm.refresh() }, // alerts are re-checked on every refresh
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (mine.isEmpty()) item { EmptyState("🔕", "No alerts yet. Tap New to add one.") }
                items(mine, key = { it.id ?: "" }) { a ->
                    AlertCard(
                        vm, a,
                        onEdit = { editing = a; showForm = true },
                        onDelete = { toDelete = a }
                    )
                }
            }
        }
    }

    if (showForm) AlertForm(vm, editing) { showForm = false }

    toDelete?.let { a ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = Color.White,
            title = { Text("Delete alert?") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAlert(a); toDelete = null }) { Text("Delete", color = Red) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AlertCard(vm: AppViewModel, a: Alert, onEdit: () -> Unit, onDelete: () -> Unit) {
    val hit = vm.isTriggered(a)
    val latest = vm.latest(a.crop_id, a.market_id)
    val crop = vm.crops.find { it.id == a.crop_id }
    val shape = RoundedCornerShape(14.dp)

    WhiteCard(
        Modifier
            .fillMaxWidth()
            .then(if (hit) Modifier.border(2.dp, Red, shape) else Modifier)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CropAvatar(crop?.name ?: "", crop?.category ?: "", 44)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(crop?.name ?: "Unknown", fontWeight = FontWeight.Bold)
                    Text("📍 ${vm.marketName(a.market_id)}", style = MaterialTheme.typography.bodySmall, color = TextGray)
                }
                Switch(
                    checked = a.active_status,
                    onCheckedChange = { vm.saveAlert(a.copy(active_status = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Orange)
                )
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(BgGray).padding(10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Target", style = MaterialTheme.typography.labelSmall, color = TextGray)
                    Text("${a.condition} ${rs(a.target_price)}", fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f)) {
                    Text("Latest price", style = MaterialTheme.typography.labelSmall, color = TextGray)
                    Text(latest?.let { rs(it) } ?: "-", fontWeight = FontWeight.SemiBold, color = Orange)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    hit -> Tag("🔔 Target reached!", Red, filled = true)
                    !a.active_status -> Tag("Paused", TextGray)
                    else -> Tag("Watching", Green)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete", color = Red) }
            }
        }
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
        containerColor = Color.White,
        title = { Text(if (existing == null) "New price alert" else "Edit alert", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker("Crop", vm.crops.map { it.id to "${cropEmoji(it.name)}  ${it.name}" }, crop, { crop = it }, Modifier.fillMaxWidth())
                Picker("Market", vm.markets.map { it.id to it.name }, market, { market = it }, Modifier.fillMaxWidth())
                Text("Notify me when price is", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrangeChip("Above ≥", cond == "Above") { cond = "Above" }
                    OrangeChip("Below ≤", cond == "Below") { cond = "Below" }
                }
                Field(target, { target = it }, "Target price (Rs)", err, KeyboardType.Number)
                latest?.let {
                    Text("Latest price: ${rs(it)}", style = MaterialTheme.typography.bodySmall, color = TextGray)
                }
            }
        },
        confirmButton = {
            Button(
                shape = RoundedCornerShape(10.dp),
                onClick = {
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
                }
            ) { Text("Save") }
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

    Scaffold(topBar = { BackBar("Profit calculator", nav.back) }, containerColor = BgGray) { pad ->
        Column(
            Modifier.padding(pad).padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Result card on top
            val hero = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Orange, OrangeSoft)))
                .padding(20.dp)

            if (base != null && q > 0) {
                val price = base * (1 + shift / 100)
                val revenue = price * q
                val total = cpu * q
                val profit = revenue - total
                val margin = if (revenue > 0) profit / revenue * 100 else 0.0

                Column(hero, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (profit >= 0) "Estimated profit 📈" else "Estimated loss 📉",
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Text(
                        (if (profit < 0) "- " else "") + rs(abs(profit)),
                        color = Color.White, style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Margin ${String.format(Locale.US, "%.1f", margin)}%",
                        color = Color.White, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        MiniStat("Revenue", rs(revenue), Modifier.weight(1f))
                        MiniStat("Total cost", rs(total), Modifier.weight(1f))
                        MiniStat("Price/unit", rs(price), Modifier.weight(1f))
                    }
                }
            } else {
                Column(hero) {
                    Text("💰 Profit calculator", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Enter quantity and cost to see your profit", color = Color.White.copy(alpha = 0.9f))
                }
            }

            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle("Your crop")
                    Picker("Crop", vm.crops.map { it.id to "${cropEmoji(it.name)}  ${it.name}" }, crop, { crop = it }, Modifier.fillMaxWidth())
                    Picker("Market", vm.markets.map { it.id to it.name }, market, { market = it }, Modifier.fillMaxWidth())
                    Surface(color = OrangeLight, shape = RoundedCornerShape(10.dp)) {
                        Text(
                            "Today's market price: ${base?.let { rs(it) } ?: "-"} per unit",
                            color = OrangeDark, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().padding(10.dp)
                        )
                    }
                    Field(qty, { qty = it }, "Quantity (units)", if (q <= 0) "Enter quantity greater than 0" else null, KeyboardType.Number)
                    Field(cost, { cost = it }, "Cost of production per unit (Rs)", null, KeyboardType.Number)
                }
            }

            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle("What if the price changes?")
                    val pct = shift.roundToInt()
                    Text(
                        String.format(Locale.US, "%+d%%", pct),
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        color = when { pct > 0 -> Green; pct < 0 -> Red; else -> TextGray }
                    )
                    Slider(
                        value = shift,
                        onValueChange = { shift = it.roundToInt().toFloat() },
                        valueRange = -20f..20f,
                        steps = 39,
                        colors = SliderDefaults.colors(
                            thumbColor = Orange,
                            activeTrackColor = Orange,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("-20%", color = Red)
                        TextButton(onClick = { shift = 0f }) { Text("Reset") }
                        Text("+20%", color = Green)
                    }
                }
            }
        }
    }
}

@Composable
fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}
