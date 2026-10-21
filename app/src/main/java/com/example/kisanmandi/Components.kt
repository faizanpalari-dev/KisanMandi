package com.example.kisanmandi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ---------- Constants & small helpers ----------

val SINDH_DISTRICTS = listOf(
    "Hyderabad", "Sukkur", "Larkana", "Karachi", "Mirpurkhas", "Nawabshah", "Thatta",
    "Badin", "Khairpur", "Dadu", "Jacobabad", "Sanghar", "Umerkot", "Tharparkar", "Ghotki", "Shikarpur"
).sorted()

val PHONE_REGEX = Regex("^03\\d{9}$")

val Green = Color(0xFF2E7D32)
val Red = Color(0xFFC62828)
val Blue = Color(0xFF1565C0)

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

class Nav(val push: (Screen) -> Unit, val back: () -> Unit)

sealed class Screen {
    object Home : Screen()
    data class CropDetail(val cropId: String, val marketId: String) : Screen()
    data class ListingDetail(val id: String) : Screen()
    data class ListingForm(val id: String?) : Screen()
    object Profit : Screen()
}

// ---------- Reusable UI pieces ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }
    )
}

// Simple dropdown: options are (id, label) pairs
@Composable
fun Picker(
    label: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                "$label: " + (options.find { it.first == selected }?.second ?: "Select"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(key); open = false })
            }
        }
    }
}

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
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = modifier
    )
}

@Composable
fun ErrorText(e: String?) {
    if (e != null) Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun Tag(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(
            text, color = color, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun MarketLabel(label: String) {
    if (label.isEmpty()) return
    val c = when {
        label.startsWith("Below") -> Green
        label.startsWith("Above") -> Red
        else -> Blue
    }
    Tag(label, c)
}

@Composable
fun ChangeText(diff: Double, base: Double) {
    val pct = if (base != 0.0) diff / base * 100 else 0.0
    val color = when { diff > 0 -> Green; diff < 0 -> Red; else -> Color.Gray }
    val arrow = when { diff > 0 -> "▲"; diff < 0 -> "▼"; else -> "•" }
    Text(
        "$arrow ${rs(abs(diff))} (${String.format(Locale.US, "%+.1f", pct)}%)",
        color = color, style = MaterialTheme.typography.bodySmall
    )
}

@Composable
fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Bold, color = color)
    }
}

// 14-day line chart drawn with Canvas (no extra library needed)
@Composable
fun LineChart(points: List<Price>) {
    if (points.size < 2) {
        Text("Not enough data for a chart")
        return
    }
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val vals = points.map { it.price }
    val mn = vals.min()
    val mx = vals.max()
    val range = if (mx - mn > 0) mx - mn else 1.0

    Text(rs(mx), style = MaterialTheme.typography.labelSmall)
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(vertical = 8.dp)) {
        val stepX = size.width / (vals.size - 1)
        fun y(v: Double) = size.height * (1f - ((v - mn) / range).toFloat())
        for (k in 0..3) {
            val gy = size.height * k / 3f
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1f)
        }
        val path = Path()
        vals.forEachIndexed { i, v ->
            if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * stepX, y(v))
        }
        drawPath(path, line, style = Stroke(width = 5f))
        vals.forEachIndexed { i, v -> drawCircle(line, 7f, Offset(i * stepX, y(v))) }
    }
    Text(rs(mn), style = MaterialTheme.typography.labelSmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(points.first().date.substring(5), style = MaterialTheme.typography.labelSmall)
        Text(points.last().date.substring(5), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun ListingCard(vm: AppViewModel, l: Listing, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    vm.cropName(l.crop_id), fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)
                )
                Text(rs(l.asking_price), fontWeight = FontWeight.Bold)
            }
            Text(
                "Qty ${fmt(l.quantity)} • Grade ${l.grade} • ${l.district}",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Available from ${l.available_from}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MarketLabel(vm.marketLabel(l))
                if (l.status != "Active") Tag(l.status, Color.Gray)
            }
        }
    }
}
