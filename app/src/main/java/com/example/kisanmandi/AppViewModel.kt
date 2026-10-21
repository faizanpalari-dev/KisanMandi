package com.example.kisanmandi

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("kisanmandi", 0)
    private val api = Net.api

    // Logged-in user (saved on the phone after first launch)
    var user by mutableStateOf<User?>(null); private set

    // Data from the mock API
    var users by mutableStateOf(emptyList<User>()); private set
    var crops by mutableStateOf(emptyList<Crop>()); private set
    var markets by mutableStateOf(emptyList<Market>()); private set
    var prices by mutableStateOf(emptyList<Price>()); private set
    var listings by mutableStateOf(emptyList<Listing>()); private set
    var alerts by mutableStateOf(emptyList<Alert>()); private set

    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var tab by mutableIntStateOf(0)

    init {
        val id = prefs.getString("id", null)
        if (id != null) {
            user = User(
                id = id,
                name = prefs.getString("name", "") ?: "",
                phone = prefs.getString("phone", "") ?: "",
                district = prefs.getString("district", "") ?: "",
                role = prefs.getString("role", "Farmer") ?: "Farmer"
            )
        }
        refresh()
    }

    // Pull-to-refresh calls this. Alerts are re-checked automatically because
    // isTriggered() always uses the newest prices.
    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        loading = true
        try {
            crops = api.crops()
            markets = api.markets()
            prices = api.prices()
            listings = api.listings()
            alerts = api.alerts()
            users = api.users()
            error = null
        } catch (e: Exception) {
            error = "Can't reach the server. Is JSON Server running? (${e.message})"
        } finally {
            loading = false
        }
    }

    private fun act(onDone: () -> Unit = {}, onFail: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                load()
                onDone()
            } catch (e: Exception) {
                error = "Action failed: ${e.message}"
                onFail()
            }
        }
    }

    // ---------- Users ----------

    fun register(name: String, phone: String, district: String, role: String, onFail: () -> Unit) =
        act(onFail = onFail) {
            val u = api.addUser(User(name = name, phone = phone, district = district, role = role))
            prefs.edit()
                .putString("id", u.id)
                .putString("name", u.name)
                .putString("phone", u.phone)
                .putString("district", u.district)
                .putString("role", u.role)
                .apply()
            tab = 0
            user = u
        }

    fun logout() {
        prefs.edit().clear().apply()
        tab = 0
        user = null
    }

    // ---------- Listings (owner-only changes) ----------

    private fun checkOwner(l: Listing) =
        require(l.user_id == user?.id) { "You can only change your own listings" }

    fun saveListing(l: Listing, onDone: () -> Unit, onFail: () -> Unit) = act(onDone, onFail) {
        checkOwner(l)
        if (l.id == null) api.addListing(l) else api.updateListing(l.id, l)
    }

    fun markSold(l: Listing) = act {
        checkOwner(l)
        api.updateListing(l.id!!, l.copy(status = "Sold"))
    }

    fun deleteListing(l: Listing, onDone: () -> Unit) = act(onDone) {
        checkOwner(l)
        api.deleteListing(l.id!!)
    }

    // ---------- Alerts ----------

    fun saveAlert(a: Alert) = act {
        if (a.id == null) api.addAlert(a) else api.updateAlert(a.id, a)
    }

    fun deleteAlert(a: Alert) = act { api.deleteAlert(a.id!!) }

    // ---------- Helpers ----------

    fun cropName(id: String) = crops.find { it.id == id }?.name ?: "Unknown"
    fun marketName(id: String) = markets.find { it.id == id }?.name ?: "Unknown"

    // All prices of one crop in one market, oldest first
    fun series(cropId: String, marketId: String) =
        prices.filter { it.crop_id == cropId && it.market_id == marketId }.sortedBy { it.date }

    fun latest(cropId: String, marketId: String): Double? =
        series(cropId, marketId).lastOrNull()?.price

    // Average of today's price across all markets
    fun avgMarket(cropId: String): Double? =
        markets.mapNotNull { latest(cropId, it.id) }.takeIf { it.isNotEmpty() }?.average()

    // Within ±5% of the average = At market
    fun marketLabel(l: Listing): String {
        val avg = avgMarket(l.crop_id) ?: return ""
        val diff = (l.asking_price - avg) / avg
        return when {
            diff < -0.05 -> "Below market"
            diff > 0.05 -> "Above market"
            else -> "At market"
        }
    }

    // Above: latest >= target, Below: latest <= target
    fun isTriggered(a: Alert): Boolean {
        if (!a.active_status) return false
        val p = latest(a.crop_id, a.market_id) ?: return false
        return if (a.condition == "Above") p >= a.target_price else p <= a.target_price
    }

    val myAlerts: List<Alert> get() = alerts.filter { it.user_id == user?.id }
    val triggeredCount: Int get() = myAlerts.count { isTriggered(it) }
}
