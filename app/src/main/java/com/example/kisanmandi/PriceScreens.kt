package com.example.kisanmandi

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val categories = listOf("All") + vm.crops.map { it.category }.distinct()
    val shown = vm.crops.filter {
        (category == "All" || it.category == category) &&
            it.name.contains(query.trim(), ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Picker(
            "Market", vm.markets.map { it.id to "${it.name} (${it.district})" }, mId,
            { marketId = it }, Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Search crop") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { c ->
                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
            }
        }
        PullToRefreshBox(
            isRefreshing = vm.loading,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (mId == null) {
                    item { Text("No data yet. Pull down to refresh.") }
                } else {
                    if (shown.isEmpty()) item { Text("No crops match your search.") }
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
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(crop.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(crop.category, style = MaterialTheme.typography.bodySmall)
                today?.let { Text("Date: ${it.date}", style = MaterialTheme.typography.bodySmall) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(today?.let { rs(it.price) } ?: "-", fontWeight = FontWeight.Bold)
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
    val series = vm.series(cropId, marketId).takeLast(14)
    val values = series.map { it.price }
    val compare = vm.markets
        .mapNotNull { m -> vm.latest(cropId, m.id)?.let { m to it } }
        .sortedByDescending { it.second }

    Scaffold(topBar = { BackBar("${vm.cropName(cropId)} trend", nav.back) }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Picker(
                    "Market", vm.markets.map { it.id to it.name }, marketId,
                    { marketId = it }, Modifier.fillMaxWidth()
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Last 14 days", fontWeight = FontWeight.Bold)
                        LineChart(series)
                    }
                }
            }
            if (values.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBox("Min", rs(values.min()), Modifier.weight(1f))
                        StatBox("Max", rs(values.max()), Modifier.weight(1f))
                        StatBox("Average", rs(values.average()), Modifier.weight(1f))
                    }
                }
            }
            item {
                Text("Compare markets (today)", style = MaterialTheme.typography.titleMedium)
            }
            itemsIndexed(compare) { i, (market, price) ->
                val best = i == 0
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (best) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(market.name, fontWeight = FontWeight.Bold)
                            Text(market.district, style = MaterialTheme.typography.bodySmall)
                            if (best) {
                                Text(
                                    "★ Best place to sell today",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(rs(price), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
