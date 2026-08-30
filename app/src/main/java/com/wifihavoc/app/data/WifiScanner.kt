package com.wifihavoc.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val capabilities: String,
    val security: String,
) {
    val levelPercent: Int
        get() = WifiManager.calculateSignalLevel(rssi, 101)

    val isOpen: Boolean get() = capabilities.contains("OEN") && !isSecured
    val isSecured: Boolean
        get() = capabilities.contains("WPA") || capabilities.contains("WEP")

    val band: String get() = if (frequency >= 4900) "5GHz" else "2.4GHz"

    /** Канал из частоты. */
    val channel: Int
        get() = when {
            frequency in 2412..2484 -> (frequency - 2407) / 5
            frequency in 5170..5825 -> (frequency - 5000) / 5
            else -> 0
        }
}

object WifiScanner {
    val networks: StateFlow<List<WifiNetwork>> = MutableStateFlow(emptyList())
    val isScanning: StateFlow<Boolean> = MutableStateFlow(false)

    fun hasPermission(context: Context): Boolean {
        val loc = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= 33) {
            return loc && ContextCompat.checkSelfPermission(
                context, Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        }
        return loc
    }

    suspend fun scan(context: Context) {
        withContext(Dispatchers.IO) {
            if (!hasPermission(context)) return@withContext
            (isScanning as MutableStateFlow).value = true
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.startScan()
            delay(1200)
            val results: List<ScanResult> = wm.scanResults
            val mapped = results.mapNotNull { r ->
                val name: String = r.SSID ?: return@mapNotNull null
                if (name.isBlank()) return@mapNotNull null
                WifiNetwork(
                    ssid = name,
                    bssid = r.BSSID ?: "",
                    rssi = r.level,
                    frequency = r.frequency,
                    capabilities = r.capabilities ?: "",
                    security = describeSecurity(r.capabilities ?: "")
                )
            }
                .distinctBy { it.bssid }
                .sortedByDescending { it.rssi }
            (networks as MutableStateFlow).value = mapped
            (isScanning as MutableStateFlow).value = false
        }
    }

    private fun describeSecurity(caps: String): String {
        return when {
            caps.contains("WPA3") && caps.contains("WPA2") -> "WPA3-WPA2"
            caps.contains("WPA3") -> "WPA3"
            caps.contains("WPA2") -> "WPA2"
            caps.contains("WPA") -> "WPA"
            caps.contains("WEP") -> "WEP"
            else -> "Open"
        }
    }
}
