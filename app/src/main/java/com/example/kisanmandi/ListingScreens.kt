package com.example.kisanmandi

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Locale

// ---------- Milestone 4: Marketplace (active listings) ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(vm: AppViewModel, nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    var crop by rememberSaveable { mutableStateOf("All") }
    var district by rememberSaveable { mutableStateOf("All") }
    var grade by rememberSaveable { mutableStateOf("All") }
    var sort by rememberSaveable { mutableStateOf("newest") }

    val districts = (vm.listings.map { it.district } + vm.markets.map { it.district }).distinct().sorted()
    val shown = vm.listings
        .filter { it.status == "Active" }
        .filter { query.isBlank() || vm.cropName(it.crop_id).contains(query.trim(), ignoreCase = true) }
        .filter { crop == "All" || it.crop_id == crop }
        .filter { district == "All" || it.district == district }
        .filter { grade == "All" || it.grade == grade }
        .let { list ->
            if (sort == "price") list.sortedBy { it.asking_price }
            else list.sortedWith(
                compareByDescending<Listing> { it.created_at ?: "" }
                    .thenByDescending { it.id?.toIntOrNull() ?: 0 }
            )
        }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Search crop") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Picker(
                "Crop", listOf("All" to "All") + vm.crops.map { it.id to it.name }, crop,
                { crop = it }, Modifier.weight(1f)
            )
            Picker(
                "District", listOf("All" to "All") + districts.map { it to it }, district,
                { district = it }, Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Picker(
                "Grade", listOf("All", "A", "B", "C").map { it to it }, grade,
                { grade = it }, Modifier.weight(1f)
            )
            Picker(
                "Sort", listOf("newest" to "Newest", "price" to "Price low-high"), sort,
                { sort = it }, Modifier.weight(1f)
            )
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
                if (shown.isEmpty()) item { Text("No listings match your filters.") }
                items(shown, key = { it.id ?: "" }) { l ->
                    ListingCard(vm, l) { nav.push(Screen.ListingDetail(l.id!!)) }
                }
            }
        }
    }
}

// ---------- My Listings (farmers only) ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListingsScreen(vm: AppViewModel, nav: Nav) {
    val mine = vm.listings
        .filter { it.user_id == vm.user?.id }
        .sortedByDescending { it.created_at ?: "" }

    PullToRefreshBox(
        isRefreshing = vm.loading,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (mine.isEmpty()) item { Text("You have no listings yet. Tap + to add one.") }
            items(mine, key = { it.id ?: "" }) { l ->
                ListingCard(vm, l) { nav.push(Screen.ListingDetail(l.id!!)) }
            }
        }
    }
}

// ---------- Listing details: Call / WhatsApp / owner actions ----------

@Composable
fun ListingDetailScreen(vm: AppViewModel, nav: Nav, id: String) {
    val l = vm.listings.find { it.id == id }
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackBar("Listing details", nav.back) }) { pad ->
        if (l == null) {
            Box(Modifier.padding(pad).padding(16.dp)) { Text("This listing no longer exists.") }
            return@Scaffold
        }
        val seller = vm.users.find { it.id == l.user_id }
        val phone = l.phone ?: seller?.phone ?: ""
        val avg = vm.avgMarket(l.crop_id)
        val isOwner = l.user_id == vm.user?.id

        fun open(uri: String, action: String) {
            try {
                ctx.startActivity(Intent(action, Uri.parse(uri)))
            } catch (e: Exception) {
                Toast.makeText(ctx, "No app found to open this", Toast.LENGTH_SHORT).show()
            }
        }

        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    vm.cropName(l.crop_id), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                Tag(l.status, if (l.status == "Active") Green else Color.Gray)
            }
            MarketLabel(vm.marketLabel(l))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("Asking price", rs(l.asking_price))
                    InfoRow("Avg market price", avg?.let { rs(it) } ?: "-")
                    InfoRow("Quantity", fmt(l.quantity))
                    InfoRow("Grade", l.grade)
                    InfoRow("District", l.district)
                    InfoRow("Available from", l.available_from)
                    InfoRow("Seller", seller?.name ?: "-")
                    InfoRow("Phone", phone)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { open("tel:$phone", Intent.ACTION_DIAL) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Call, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Call")
                }
                OutlinedButton(
                    onClick = {
                        val digits = phone.filter { it.isDigit() }
                        val intl = if (digits.startsWith("0")) "92" + digits.drop(1) else digits
                        val msg = Uri.encode("Assalam o Alaikum, I am interested in your ${vm.cropName(l.crop_id)} on KisanMandi.")
                        open("https://wa.me/$intl?text=$msg", Intent.ACTION_VIEW)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("WhatsApp") }
            }

            // Ownership safeguard: only the owner sees these buttons
            if (isOwner) {
                HorizontalDivider()
                Text("Manage your listing", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nav.push(Screen.ListingForm(l.id)) }) { Text("Edit") }
                    if (l.status == "Active") {
                        Button(onClick = { vm.markSold(l) }) { Text("Mark Sold") }
                    }
                    OutlinedButton(onClick = { confirmDelete = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete listing?") },
                text = { Text("This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        vm.deleteListing(l) { nav.back() }
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
            )
        }
    }
}

// ---------- Add / Edit listing form with validation ----------

@Composable
fun ListingFormScreen(vm: AppViewModel, nav: Nav, id: String?) {
    val existing = id?.let { i -> vm.listings.find { it.id == i } }
    val ctx = LocalContext.current

    var cropId by rememberSaveable { mutableStateOf(existing?.crop_id) }
    var qty by rememberSaveable { mutableStateOf(existing?.quantity?.let { fmt(it) } ?: "") }
    var price by rememberSaveable { mutableStateOf(existing?.asking_price?.let { fmt(it) } ?: "") }
    var district by rememberSaveable { mutableStateOf(existing?.district ?: vm.user?.district) }
    var grade by rememberSaveable { mutableStateOf(existing?.grade ?: "A") }
    var date by rememberSaveable { mutableStateOf(existing?.available_from ?: today()) }
    var phone by rememberSaveable { mutableStateOf(existing?.phone ?: vm.user?.phone ?: "") }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }
    var saving by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackBar(if (existing == null) "New listing" else "Edit listing", nav.back) }) { pad ->
        if (existing != null && existing.user_id != vm.user?.id) {
            Box(Modifier.padding(pad).padding(16.dp)) { Text("You can only edit your own listings.") }
            return@Scaffold
        }

        fun validate(): Map<String, String> {
            val e = mutableMapOf<String, String>()
            if (cropId == null) e["crop"] = "Select a crop"
            if ((qty.toDoubleOrNull() ?: 0.0) <= 0) e["qty"] = "Quantity must be greater than 0"
            if ((price.toDoubleOrNull() ?: 0.0) <= 0) e["price"] = "Price must be greater than 0"
            if (district.isNullOrBlank()) e["district"] = "Select a district"
            if (grade !in listOf("A", "B", "C")) e["grade"] = "Select grade A, B or C"
            if (!isValidDate(date)) e["date"] = "Use format YYYY-MM-DD"
            else if (date < today()) e["date"] = "Date cannot be in the past"
            if (!PHONE_REGEX.matches(phone.trim())) e["phone"] = "Enter phone like 03001234567"
            return e
        }

        fun pickDate() {
            val c = Calendar.getInstance()
            DatePickerDialog(
                ctx,
                { _, y, m, d -> date = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d) },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
            ).apply { datePicker.minDate = c.timeInMillis - 1000 }.show()
        }

        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Picker("Crop", vm.crops.map { it.id to it.name }, cropId, { cropId = it }, Modifier.fillMaxWidth())
            ErrorText(errors["crop"])

            Field(qty, { qty = it }, "Quantity", errors["qty"], KeyboardType.Number)
            Field(price, { price = it }, "Asking price (Rs)", errors["price"], KeyboardType.Number)
            cropId?.let { c ->
                vm.avgMarket(c)?.let {
                    Text("Avg market price today: ${rs(it)}", style = MaterialTheme.typography.bodySmall)
                }
            }

            Picker("District", SINDH_DISTRICTS.map { it to it }, district, { district = it }, Modifier.fillMaxWidth())
            ErrorText(errors["district"])

            Text("Grade")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("A", "B", "C").forEach { g ->
                    FilterChip(selected = grade == g, onClick = { grade = g }, label = { Text("Grade $g") })
                }
            }
            ErrorText(errors["grade"])

            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(date, { date = it }, "Available from (YYYY-MM-DD)", errors["date"], modifier = Modifier.weight(1f))
                IconButton(onClick = { pickDate() }) { Icon(Icons.Default.DateRange, "Pick date") }
            }

            Field(phone, { phone = it }, "Contact phone", errors["phone"], KeyboardType.Phone)

            Button(
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    errors = validate()
                    if (errors.isEmpty()) {
                        saving = true
                        vm.saveListing(
                            Listing(
                                id = existing?.id,
                                user_id = vm.user!!.id!!,
                                crop_id = cropId!!,
                                quantity = qty.toDouble(),
                                asking_price = price.toDouble(),
                                district = district!!,
                                grade = grade,
                                available_from = date,
                                status = existing?.status ?: "Active",
                                phone = phone.trim(),
                                created_at = existing?.created_at ?: today()
                            ),
                            onDone = { nav.back() },
                            onFail = { saving = false }
                        )
                    }
                }
            ) { Text(if (saving) "Saving..." else "Save listing") }
            ErrorText(vm.error)
        }
    }
}
