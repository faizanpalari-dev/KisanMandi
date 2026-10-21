package com.example.kisanmandi

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale

// ---------- Milestone 4: Marketplace (active listings, product grid) ----------

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

    Column(Modifier.fillMaxSize()) {
        OrangeHeader {
            SearchBar(query, { query = it }, "Search wheat, mango, onion...")
            Text(
                "🧺 ${shown.size} fresh listings from Sindh farmers",
                color = Color.White, style = MaterialTheme.typography.labelMedium
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                ChipPicker(
                    "Crop", listOf("All" to "All crops") + vm.crops.map { it.id to it.name },
                    crop, { crop = it }
                )
            }
            item {
                ChipPicker(
                    "District", listOf("All" to "All districts") + districts.map { it to it },
                    district, { district = it }
                )
            }
            item {
                ChipPicker(
                    "Grade",
                    listOf("All" to "All grades", "A" to "Grade A", "B" to "Grade B", "C" to "Grade C"),
                    grade, { grade = it }
                )
            }
            item {
                ChipPicker(
                    "Sort: Newest",
                    listOf("newest" to "Newest first", "price" to "Price: Low to High"),
                    sort, { sort = it }, allKey = "newest"
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = vm.loading,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (shown.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("🧺", "No listings match your filters") }
                }
                items(shown, key = { it.id ?: "" }) { l ->
                    ProductCard(vm, l) { nav.push(Screen.ListingDetail(l.id!!)) }
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
    val active = mine.count { it.status == "Active" }
    val sold = mine.size - active

    PullToRefreshBox(
        isRefreshing = vm.loading,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("Total", "${mine.size}", Orange, Modifier.weight(1f))
                    StatBox("Active", "$active", Green, Modifier.weight(1f))
                    StatBox("Sold", "$sold", TextGray, Modifier.weight(1f))
                }
            }
            if (mine.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("🌱", "No listings yet. Tap Sell crop to add one.")
                }
            }
            items(mine, key = { it.id ?: "" }) { l ->
                ProductCard(vm, l) { nav.push(Screen.ListingDetail(l.id!!)) }
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

    if (l == null) {
        Scaffold(topBar = { BackBar("Product details", nav.back) }, containerColor = BgGray) { pad ->
            Box(Modifier.padding(pad).padding(16.dp)) { EmptyState("🗑️", "This listing no longer exists.") }
        }
        return
    }

    val crop = vm.crops.find { it.id == l.crop_id }
    val name = crop?.name ?: "Unknown"
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

    fun whatsapp() {
        val digits = phone.filter { it.isDigit() }
        val intl = if (digits.startsWith("0")) "92" + digits.drop(1) else digits
        val msg = Uri.encode("Assalam o Alaikum, I am interested in your $name on KisanMandi.")
        open("https://wa.me/$intl?text=$msg", Intent.ACTION_VIEW)
    }

    Scaffold(
        topBar = { BackBar("Product details", nav.back) },
        containerColor = BgGray,
        bottomBar = {
            Surface(color = Color.White, shadowElevation = 8.dp) {
                Row(
                    Modifier.navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { open("tel:$phone", Intent.ACTION_DIAL) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, Orange),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange)
                    ) {
                        Icon(Icons.Default.Call, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Call", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { whatsapp() },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White)
                    ) { Text("💬 WhatsApp", fontWeight = FontWeight.Bold) }
                }
            }
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Big "product image"
            Box(
                Modifier.fillMaxWidth().height(220.dp).background(categoryColor(crop?.category ?: "")),
                contentAlignment = Alignment.Center
            ) {
                Text(cropEmoji(name), fontSize = 110.sp)
                if (l.status != "Active") {
                    Box(
                        Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) { Text("SOLD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 32.sp) }
                }
            }

            WhiteCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        rs(l.asking_price), color = Orange, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tag("Grade ${l.grade}", Orange, filled = true)
                        MarketLabel(vm.marketLabel(l))
                        Tag(l.status, if (l.status == "Active") Green else TextGray)
                    }
                    avg?.let {
                        Text(
                            "Average mandi price today: ${rs(it)}",
                            style = MaterialTheme.typography.bodySmall, color = TextGray
                        )
                    }
                }
            }

            WhiteCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle("Product details")
                    Spacer(Modifier.height(6.dp))
                    InfoRow("Quantity", fmt(l.quantity))
                    InfoRow("Grade", l.grade)
                    InfoRow("District", l.district)
                    InfoRow("Available from", l.available_from)
                    InfoRow("Category", crop?.category ?: "-")
                }
            }

            WhiteCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(Orange),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (seller?.name ?: "?").take(1).uppercase(),
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(seller?.name ?: "Seller", fontWeight = FontWeight.Bold)
                        Text("📞 $phone", style = MaterialTheme.typography.bodySmall, color = TextGray)
                        Text("📍 ${seller?.district ?: l.district}", style = MaterialTheme.typography.bodySmall, color = TextGray)
                    }
                    Tag("Seller", Blue)
                }
            }

            // Ownership safeguard: only the owner sees these buttons
            if (isOwner) {
                WhiteCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("Manage your listing")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { nav.push(Screen.ListingForm(l.id)) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("Edit") }
                            if (l.status == "Active") {
                                Button(
                                    onClick = { vm.markSold(l) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Mark Sold") }
                            }
                            OutlinedButton(
                                onClick = { confirmDelete = true },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Red),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                                modifier = Modifier.weight(1f)
                            ) { Text("Delete") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                containerColor = Color.White,
                title = { Text("Delete listing?") },
                text = { Text("This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        vm.deleteListing(l) { nav.back() }
                    }) { Text("Delete", color = Red) }
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

    Scaffold(
        topBar = { BackBar(if (existing == null) "Sell your crop" else "Edit listing", nav.back) },
        containerColor = BgGray
    ) { pad ->
        if (existing != null && existing.user_id != vm.user?.id) {
            Box(Modifier.padding(pad).padding(16.dp)) { EmptyState("🔒", "You can only edit your own listings.") }
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
            Modifier.padding(pad).padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("🌾 What are you selling?")
                    Picker("Crop", vm.crops.map { it.id to "${cropEmoji(it.name)}  ${it.name}" }, cropId, { cropId = it }, Modifier.fillMaxWidth())
                    ErrorText(errors["crop"])
                    Text("Quality grade", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("A", "B", "C").forEach { g -> OrangeChip("Grade $g", grade == g) { grade = g } }
                    }
                    ErrorText(errors["grade"])
                }
            }

            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionTitle("💰 Quantity & price")
                    Field(qty, { qty = it }, "Quantity", errors["qty"], KeyboardType.Number)
                    Field(price, { price = it }, "Asking price (Rs)", errors["price"], KeyboardType.Number)
                    cropId?.let { c ->
                        vm.avgMarket(c)?.let {
                            Surface(color = OrangeLight, shape = RoundedCornerShape(10.dp)) {
                                Text(
                                    "💡 Average mandi price today: ${rs(it)}",
                                    color = OrangeDark, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionTitle("📍 Location & availability")
                    Picker("District", SINDH_DISTRICTS.map { it to it }, district, { district = it }, Modifier.fillMaxWidth())
                    ErrorText(errors["district"])
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(date, { date = it }, "Available from (YYYY-MM-DD)", errors["date"], modifier = Modifier.weight(1f))
                        IconButton(onClick = { pickDate() }) { Icon(Icons.Default.DateRange, "Pick date", tint = Orange) }
                    }
                    Field(phone, { phone = it }, "Contact phone", errors["phone"], KeyboardType.Phone)
                }
            }

            ErrorText(vm.error)

            Button(
                enabled = !saving,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
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
            ) {
                Text(
                    if (saving) "Saving..." else if (existing == null) "Post listing" else "Save changes",
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
