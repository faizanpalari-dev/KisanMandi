package com.example.kisanmandi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ---------- Colors ----------

val Orange = Color(0xFFF7631B)
val OrangeDark = Color(0xFFD9480F)
val OrangeSoft = Color(0xFFFF8A3D)
val OrangeLight = Color(0xFFFFEDE3)
val BgGray = Color(0xFFF4F4F6)
val TextGray = Color(0xFF7A7A7A)
val Green = Color(0xFF2E7D32)
val Red = Color(0xFFD32F2F)
val Blue = Color(0xFF1565C0)
val WhatsAppGreen = Color(0xFF25D366)

// ---------- Constants & helpers ----------

val SINDH_DISTRICTS = listOf(
    "Hyderabad", "Sukkur", "Larkana", "Karachi", "Mirpurkhas", "Nawabshah", "Thatta",
    "Badin", "Khairpur", "Dadu", "Jacobabad", "Sanghar", "Umerkot", "Tharparkar", "Ghotki", "Shikarpur"
).sorted()

val PHONE_REGEX = Regex("^03\\d{9}$")

fun rs(v: Double) = "Rs " + String.format(Locale.US, "%,.0f", v)

fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

fun isValidDate(s: String): Boolean {
    if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(s)) return false
    return try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(s)
        true
    } catch (e: Exception) {
        false
    }
}

fun cropEmoji(name: String) = when (name.lowercase()) {
    "wheat" -> "🌾"
    "rice" -> "🍚"
    "tomato" -> "🍅"
    "onion" -> "🧅"
    "mango" -> "🥭"
    "banana" -> "🍌"
    "cotton" -> "🌿"
    "sugarcane" -> "🎋"
    "potato" -> "🥔"
    else -> "🌱"
}

fun categoryColor(category: String) = when (category) {
    "Grains" -> Color(0xFFFFF3D6)
    "Vegetables" -> Color(0xFFE3F5E1)
    "Fruits" -> Color(0xFFFFE6EC)
    "Cash Crops" -> Color(0xFFE4EEFF)
    else -> OrangeLight
}

class Nav(val push: (Screen) -> Unit, val back: () -> Unit)

sealed class Screen {
    object Home : Screen()
    data class CropDetail(val cropId: String, val marketId: String) : Screen()
    data class ListingDetail(val id: String) : Screen()
    data class ListingForm(val id: String?) : Screen()
    object Profit : Screen()
}

// ---------- Basic building blocks ----------

@Composable
fun WhiteCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val colors = CardDefaults.cardColors(containerColor = Color.White)
    val elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, elevation = elevation, content = content)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, elevation = elevation, content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Orange,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}

// Orange area under the top bar (like a shopping app search header)
@Composable
fun OrangeHeader(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
            .background(Brush.verticalGradient(listOf(Orange, OrangeSoft)))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
fun SearchBar(query: String, onChange: (String) -> Unit, hint: String) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(hint, color = TextGray, maxLines = 1) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = Orange) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Clear, "Clear") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(50),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Orange
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
fun EmptyState(emoji: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 48.sp)
        Spacer(Modifier.height(8.dp))
        Text(text, color = TextGray)
    }
}

// ---------- Dropdowns ----------

@Composable
fun MenuPicker(
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    anchor: @Composable (label: String, open: () -> Unit) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val label = options.find { it.first == selected }?.second ?: "Select"
    Box(modifier) {
        anchor(label) { open = true }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(Color.White)
        ) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text,
                            fontWeight = if (key == selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (key == selected) Orange else Color.Unspecified
                        )
                    },
                    onClick = { onSelect(key); open = false }
                )
            }
        }
    }
}

// Form-style dropdown
@Composable
fun Picker(
    label: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    MenuPicker(options, selected, onSelect, modifier) { value, open ->
        OutlinedButton(
            onClick = open,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = TextGray)
                Text(
                    value, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(Icons.Default.ArrowDropDown, null, tint = Orange)
        }
    }
}

// Filter chip with dropdown (for marketplace filters)
@Composable
fun ChipPicker(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    allKey: String = "All"
) {
    MenuPicker(options, selected, onSelect) { value, open ->
        val active = selected != allKey
        FilterChip(
            selected = active,
            onClick = open,
            label = { Text(if (active) value else label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp)) },
            shape = RoundedCornerShape(50),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = Color.White,
                selectedContainerColor = OrangeLight,
                selectedLabelColor = OrangeDark,
                selectedTrailingIconColor = OrangeDark
            )
        )
    }
}

// White text dropdown that sits on the orange header
@Composable
fun HeaderPicker(
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
    prefix: String = "📍 "
) {
    MenuPicker(options, selected, onSelect) { value, open ->
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.2f))
                .clickable { open() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                prefix + value, color = Color.White, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.ArrowDropDown, null, tint = Color.White)
        }
    }
}

@Composable
fun OrangeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White,
            selectedContainerColor = Orange,
            selectedLabelColor = Color.White
        )
    )
}

// ---------- Text input ----------

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String? = null,
    type: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = { if (error != null) Text(error) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = type),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Orange,
            focusedLabelColor = Orange,
            cursorColor = Orange
        ),
        modifier = modifier
    )
}

@Composable
fun ErrorText(e: String?) {
    if (e != null) Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

// ---------- Small visual pieces ----------

@Composable
fun Tag(text: String, color: Color, filled: Boolean = false) {
    Surface(
        color = if (filled) color else color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            color = if (filled) Color.White else color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun MarketLabel(label: String) {
    if (label.isEmpty()) return
    when {
        label.startsWith("Below") -> Tag("↓ $label", Green)
        label.startsWith("Above") -> Tag("↑ $label", Red)
        else -> Tag("= $label", Blue)
    }
}

@Composable
fun ChangeText(diff: Double, base: Double) {
    val pct = if (base != 0.0) diff / base * 100 else 0.0
    val color = when { diff > 0 -> Green; diff < 0 -> Red; else -> TextGray }
    val arrow = when { diff > 0 -> "▲"; diff < 0 -> "▼"; else -> "•" }
    Tag("$arrow ${rs(abs(diff))} (${String.format(Locale.US, "%+.1f", pct)}%)", color)
}

@Composable
fun CropAvatar(name: String, category: String, size: Int = 48) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(categoryColor(category)),
        contentAlignment = Alignment.Center
    ) {
        Text(cropEmoji(name), fontSize = (size / 2).sp)
    }
}

@Composable
fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    WhiteCard(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextGray)
            Text(value, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, color: Color = Color.Unspecified) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextGray)
        Text(value, fontWeight = FontWeight.SemiBold, color = color)
    }
}

// ---------- 14-day chart (Canvas, no library) ----------

@Composable
fun LineChart(points: List<Price>, color: Color = Orange) {
    if (points.size < 2) {
        Text("Not enough data for a chart", color = TextGray)
        return
    }
    val vals = points.map { it.price }
    val mn = vals.min()
    val mx = vals.max()
    val range = if (mx - mn > 0) mx - mn else 1.0

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("High ${rs(mx)}", style = MaterialTheme.typography.labelSmall, color = Green)
            Text("Low ${rs(mn)}", style = MaterialTheme.typography.labelSmall, color = Red)
        }
        Canvas(Modifier.fillMaxWidth().height(180.dp).padding(vertical = 10.dp)) {
            val stepX = size.width / (vals.size - 1)
            fun y(v: Double) = size.height * (1f - ((v - mn) / range).toFloat())
            for (k in 0..3) {
                val gy = size.height * k / 3f
                drawLine(Color(0xFFEEEEEE), Offset(0f, gy), Offset(size.width, gy), 2f)
            }
            val line = Path()
            val fill = Path()
            vals.forEachIndexed { i, v ->
                val x = i * stepX
                if (i == 0) {
                    line.moveTo(x, y(v))
                    fill.moveTo(x, size.height)
                    fill.lineTo(x, y(v))
                } else {
                    line.lineTo(x, y(v))
                    fill.lineTo(x, y(v))
                }
            }
            fill.lineTo(size.width, size.height)
            fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(line, color, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            vals.forEachIndexed { i, v ->
                drawCircle(Color.White, 8f, Offset(i * stepX, y(v)))
                drawCircle(color, 5f, Offset(i * stepX, y(v)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().date.substring(5), style = MaterialTheme.typography.labelSmall, color = TextGray)
            Text(points.last().date.substring(5), style = MaterialTheme.typography.labelSmall, color = TextGray)
        }
    }
}

// ---------- Product card (grid item, shopping-app style) ----------

@Composable
fun ProductCard(vm: AppViewModel, l: Listing, onClick: () -> Unit) {
    val crop = vm.crops.find { it.id == l.crop_id }
    val name = crop?.name ?: "Unknown"
    WhiteCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Box(
            Modifier.fillMaxWidth().height(110.dp).background(categoryColor(crop?.category ?: "")),
            contentAlignment = Alignment.Center
        ) {
            Text(cropEmoji(name), fontSize = 52.sp)
            Box(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Tag("Grade ${l.grade}", Orange, filled = true)
            }
            if (l.status != "Active") {
                Box(
                    Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("SOLD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                rs(l.asking_price), color = Orange, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Qty ${fmt(l.quantity)} • ${l.district}",
                style = MaterialTheme.typography.bodySmall, color = TextGray,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            MarketLabel(vm.marketLabel(l))
        }
    }
}
