package com.example.kisanmandi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Orange,
                    onPrimary = Color.White,
                    primaryContainer = OrangeLight,
                    onPrimaryContainer = OrangeDark,
                    secondary = Color(0xFF1A9CB7),
                    background = BgGray,
                    surface = Color.White,
                    surfaceVariant = Color(0xFFF0F0F0)
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = BgGray) {
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
            .background(BgGray)
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Brush.verticalGradient(listOf(Orange, OrangeSoft)))
                .statusBarsPadding()
                .padding(28.dp)
        ) {
            Text("🌾", fontSize = 48.sp)
            Text(
                "KisanMandi", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = Color.White
            )
            Text("Mandi rates & direct selling for Sindh farmers", color = Color.White.copy(alpha = 0.9f))
        }

        WhiteCard(Modifier.padding(16.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Create your account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Field(name, { name = it }, "Full name", errors["name"])
                Field(phone, { phone = it }, "Phone (03xxxxxxxxx)", errors["phone"], KeyboardType.Phone)
                Picker("District", SINDH_DISTRICTS.map { it to it }, district, { district = it }, Modifier.fillMaxWidth())
                ErrorText(errors["district"])

                Text("I am a", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoleCard("🧑‍🌾", "Farmer", "Sell my crops", role == "Farmer", Modifier.weight(1f)) { role = "Farmer" }
                    RoleCard("🛒", "Buyer", "Buy produce", role == "Buyer", Modifier.weight(1f)) { role = "Buyer" }
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
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp)
                ) { Text(if (saving) "Please wait..." else "Get Started", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun RoleCard(
    emoji: String,
    title: String,
    sub: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .border(2.dp, if (selected) Orange else Color(0xFFE0E0E0), shape)
            .background(if (selected) OrangeLight else Color.White)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 30.sp)
        Text(title, fontWeight = FontWeight.Bold, color = if (selected) OrangeDark else Color.Unspecified)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = TextGray)
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
    val firstName = vm.user?.name?.split(" ")?.firstOrNull() ?: ""

    fun iconFor(t: String): ImageVector = when (t) {
        "Prices" -> Icons.Default.Home
        "Market" -> Icons.Default.ShoppingCart
        "Alerts" -> Icons.Default.Notifications
        else -> Icons.Default.Person
    }

    Scaffold(
        containerColor = BgGray,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("KisanMandi", fontWeight = FontWeight.Bold)
                        Text(
                            "Assalam o Alaikum, $firstName 👋",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                },
                actions = {
                    if (isFarmer) {
                        TextButton(
                            onClick = { nav.push(Screen.Profit) },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) { Text("💰 Profit", fontWeight = FontWeight.SemiBold) }
                    }
                    IconButton(onClick = { vm.logout() }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Orange,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 4.dp) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tabIndex == i,
                        onClick = { vm.tab = i },
                        label = { Text(t) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Orange,
                            selectedTextColor = Orange,
                            indicatorColor = OrangeLight,
                            unselectedIconColor = TextGray,
                            unselectedTextColor = TextGray
                        ),
                        icon = {
                            // Milestone 3: triggered-alert badge
                            if (t == "Alerts" && vm.triggeredCount > 0) {
                                BadgedBox(badge = {
                                    Badge(containerColor = Red, contentColor = Color.White) {
                                        Text("${vm.triggeredCount}")
                                    }
                                }) { Icon(iconFor(t), t) }
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
                ExtendedFloatingActionButton(
                    onClick = { nav.push(Screen.ListingForm(null)) },
                    containerColor = Orange,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Sell crop") }
                )
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
