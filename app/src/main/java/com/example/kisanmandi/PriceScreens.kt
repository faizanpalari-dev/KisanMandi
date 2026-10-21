package com.example.kisanmandi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ---------- Milestone 1: today's prices ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricesScreen(vm: AppViewModel, nav: Nav) {
    var marketId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }

    val mId = marketId ?: vm.markets.firstOrNull()?.id
    val market = vm.markets.find { it.id == mId }
    val categories = listOf("All") + vm.crops.map { it.category }.distinct()
    val shown = vm.crops.filter {
        (category == "All" || it.category == category) &&
                it.name.contains(query.trim(), ignoreCase = true)
    }

    Column(Modifier.fillMaxSize()) {
        OrangeHeader {
            SearchBar(query, { query = it }, "Search crops (wheat, onion...)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderPicker(vm.markets.map { it.id to it.name }, mId, { marketId = it })
                Spacer(Modifier.weight(1f))
                Text(
                    "Today's rates", color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { c -> OrangeChip(c, category == c) { category = c } }
        }

        PullToRefreshBox(
            isRefreshing = vm.loading,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (mId == null) {
                    item { EmptyState("📡", "No data yet. Pull down to refresh.") }
                } else {
                    market?.let { m ->
                        item {
                            Text(
                                "${m.name} • ${m.district}  (pull down to refresh)",
                                style = MaterialTheme.typography.labelMedium, color = TextGray
                            )
                        }
                    }
                    if (shown.isEmpty()) item { EmptyState("🔍", "No crops match your search") }
                    items(shown, key = { it.id }) { crop ->
                        val s = vm.series(crop.id, mId)
                        PriceRow(crop, s.lastOrNull(), s.getOrNull(s.size - 2)) {
                            nav.push(Screen.CropDetail(crop.id, mId))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PriceRow(crop: Crop, today: Price?, yesterday: Price?, onClick: () -> Unit) {
    WhiteCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CropAvatar(crop.name, crop.category, 50)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(crop.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(crop.category, style = MaterialTheme.typography.bodySmall, color = TextGray)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    today?.let { rs(it.price) } ?: "-",
                    color = Orange, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                if (today != null && yesterday != null) {
                    ChangeText(today.price - yesterday.price, yesterday.price)
                }
            }
        }
    }
}

// ---------- Milestone 2: 14-day trend + market comparison ----------

@Composable
fun CropDetailScreen(vm: AppViewModel, nav: Nav, cropId: String, startMarket: String) {
    var marketId by rememberSaveable { mutableStateOf(startMarket) }
    val crop = vm.crops.find { it.id == cropId }
    val name = crop?.name ?: "Crop"
    val full = vm.series(cropId, marketId)
    val series = full.takeLast(14)
    val values = series.map { it.price }
    val today = full.lastOrNull()
    val yesterday = full.getOrNull(full.size - 2)
    val compare = vm.markets
        .mapNotNull { m -> vm.latest(cropId, m.id)?.let { m to it } }
        .sortedByDescending { it.second }

    Scaffold(topBar = { BackBar(name, nav.back) }, containerColor = BgGray) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CropAvatar(name, crop?.category ?: "", 60)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(crop?.category ?: "", color = TextGray)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                today?.let { rs(it.price) } ?: "-",
                                color = Orange, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (today != null && yesterday != null) {
                                ChangeText(today.price - yesterday.price, yesterday.price)
                            }
                        }
                    }
                }
            }
            item {
                Picker("Market", vm.markets.map { it.id to it.name }, marketId, { marketId = it }, Modifier.fillMaxWidth())
            }
            item {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionTitle("14-day price trend")
                        Spacer(Modifier.height(6.dp))
                        LineChart(series)
                    }
                }
            }
            if (values.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBox("Min", rs(values.min()), Red, Modifier.weight(1f))
                        StatBox("Max", rs(values.max()), Green, Modifier.weight(1f))
                        StatBox("Average", rs(values.average()), Orange, Modifier.weight(1f))
                    }
                }
            }
            item { SectionTitle("Where to sell today", Modifier.padding(top = 4.dp)) }
            itemsIndexed(compare) { i, (m, p) ->
                val best = i == 0
                val shape = RoundedCornerShape(14.dp)
                WhiteCard(
                    Modifier
                        .fillMaxWidth()
                        .then(if (best) Modifier.border(2.dp, Orange, shape) else Modifier)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (best) Orange else Color(0xFFEEEEEE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${i + 1}", color = if (best) Color.White else TextGray, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.SemiBold)
                            Text("📍 ${m.district}", style = MaterialTheme.typography.bodySmall, color = TextGray)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                rs(p), fontWeight = FontWeight.Bold,
                                color = if (best) Orange else Color.Unspecified
                            )
                            if (best) Tag("★ BEST PRICE", Green, filled = true)
                        }
                    }
                }
            }
        }
    }
}
