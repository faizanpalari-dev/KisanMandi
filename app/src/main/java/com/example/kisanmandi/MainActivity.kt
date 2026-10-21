package com.example.kisanmandi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF2E7D32),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFC8E6C9),
                    onPrimaryContainer = Color(0xFF0B3D0E),
                    secondary = Color(0xFF795548)
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }
}

@Composable
fun App(vm: AppViewModel = viewModel()) {
    // Milestone 1: first launch -> registration screen
    if (vm.user == null) {
        OnboardingScreen(vm)
        return
    }

    // Simple screen stack for navigation
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val nav = remember {
        Nav(
            push = { stack.add(it) },
            back = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
        )
    }
    BackHandler(enabled = stack.size > 1) { nav.back() }

    when (val s = stack.last()) {
        Screen.Home -> HomeScreen(vm, nav)
        is Screen.CropDetail -> CropDetailScreen(vm, nav, s.cropId, s.marketId)
        is Screen.ListingDetail -> ListingDetailScreen(vm, nav, s.id)
        is Screen.ListingForm -> ListingFormScreen(vm, nav, s.id)
        Screen.Profit -> ProfitScreen(vm, nav)
    }
}

// ---------- First launch: name, phone, district, role ----------

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf<String?>(null) }
    var role by rememberSaveable { mutableStateOf("Farmer") }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }
    var saving by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "🌾 KisanMandi", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
        )
        Text("Crop prices and direct selling for Sindh farmers")
        Spacer(Modifier.height(8.dp))

        Field(name, { name = it }, "Full name", errors["name"])
        Field(phone, { phone = it }, "Phone (03xxxxxxxxx)", errors["phone"], KeyboardType.Phone)
        Picker("District", SINDH_DISTRICTS.map { it to it }, district, { district = it }, Modifier.fillMaxWidth())
        ErrorText(errors["district"])

        Text("I am a:")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Farmer", "Buyer").forEach { r ->
                FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r) })
            }
        }

        ErrorText(vm.error)

        Button(
            onClick = {
                val e = mutableMapOf<String, String>()
                if (name.trim().length < 2) e["name"] = "Enter your name"
                if (!PHONE_REGEX.matches(phone.trim())) e["phone"] = "Enter phone like 03001234567"
                if (district == null) e["district"] = "Select your district"
                errors = e
                if (e.isEmpty()) {
                    saving = true
                    vm.register(name.trim(), phone.trim(), district!!, role) { saving = false }
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (saving) "Please wait..." else "Continue") }
    }
}

// ---------- Home with role-based tabs ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, nav: Nav) {
    val isFarmer = vm.user?.role == "Farmer"
    val tabs = if (isFarmer) listOf("Prices", "Market", "Alerts", "My Listings") else listOf("Prices", "Market")
    val tabIndex = vm.tab.coerceIn(0, tabs.size - 1)
    val current = tabs[tabIndex]

    fun iconFor(t: String): ImageVector = when (t) {
        "Prices" -> Icons.Default.Home
        "Market" -> Icons.Default.ShoppingCart
        "Alerts" -> Icons.Default.Notifications
        else -> Icons.Default.Person
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("KisanMandi", fontWeight = FontWeight.Bold)
                        Text(
                            "${vm.user?.name} • ${vm.user?.role}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    if (isFarmer) TextButton(onClick = { nav.push(Screen.Profit) }) { Text("Profit") }
                    IconButton(onClick = { vm.logout() }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "Logout")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tabIndex == i,
                        onClick = { vm.tab = i },
                        label = { Text(t) },
                        icon = {
                            // Milestone 3: triggered-alert badge
                            if (t == "Alerts" && vm.triggeredCount > 0) {
                                BadgedBox(badge = { Badge { Text("${vm.triggeredCount}") } }) {
                                    Icon(iconFor(t), t)
                                }
                            } else {
                                Icon(iconFor(t), t)
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (current == "My Listings") {
                FloatingActionButton(onClick = { nav.push(Screen.ListingForm(null)) }) {
                    Icon(Icons.Default.Add, "Add listing")
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            vm.error?.let { msg ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { vm.refresh() }) { Text("Retry") }
                    }
                }
            }
            when (current) {
                "Prices" -> PricesScreen(vm, nav)
                "Market" -> MarketplaceScreen(vm, nav)
                "Alerts" -> AlertsScreen(vm)
                else -> MyListingsScreen(vm, nav)
            }
        }
    }
}
