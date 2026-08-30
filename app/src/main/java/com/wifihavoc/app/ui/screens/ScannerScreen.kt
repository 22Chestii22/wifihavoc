package com.wifihavoc.app.ui.screens

import android.Manifest
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifihavoc.app.data.AttackConfig
import com.wifihavoc.app.data.AttackEngine
import com.wifihavoc.app.data.AttackType
import com.wifihavoc.app.data.KaliManager
import com.wifihavoc.app.data.WifiNetwork
import com.wifihavoc.app.data.WifiScanner
import kotlinx.coroutines.launch

@Composable
private fun SignalBars(percent: Int) {
    val color = when {
        percent >= 70 -> MaterialTheme.colorScheme.tertiary
        percent >= 40 -> MaterialTheme.colorScheme.inversePrimary
        else -> MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(4) { i ->
            val h = (6 + i * 5).dp
            Box(
                Modifier
                    .width(4.dp)
                    .height(h)
                    .background(
                        color = if (i < percent / 25) color else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    kaliManager: KaliManager
) {
    val context = LocalContext.current
    val networks by WifiScanner.networks.collectAsStateWithLifecycle()
    val scanning by WifiScanner.isScanning.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var selectedNet by remember { mutableStateOf<WifiNetwork?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("WifiHavoc", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text("Выбери сеть для атаки", style = MaterialTheme.typography.bodyMedium)
            }
            FilledTonalIconButton(
                onClick = { scope.launch { WifiScanner.scan(context) } },
                enabled = !scanning
            ) {
                if (scanning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Сканировать")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (!WifiScanner.hasPermission(context)) {
            PermissionBanner()
        } else if (networks.isEmpty()) {
            EmptyState(scanning)
        } else {
            Text(
                "Найдено: ${networks.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(networks, key = { it.bssid }) { net ->
                    NetworkCard(net) { selectedNet = net }
                }
            }
        }
    }

    val net = selectedNet
    if (net != null) {
        AttackSheet(
            net = net,
            kaliManager = kaliManager,
            onDismiss = { selectedNet = null }
        )
    }
}

@Composable
private fun PermissionBanner() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(
            "Нет разрешения на геолокацию / Nearby Wi-Fi. Разреши в настройках, чтобы сканировать сети.",
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun EmptyState(scanning: Boolean) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (scanning) "Сканирование..." else "Сетей не найдено", style = MaterialTheme.typography.titleMedium)
            if (scanning) {
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator()
            } else {
                Text("Нажми ↻ чтобы сканировать", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun NetworkCard(net: WifiNetwork, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = if (net.isOpen) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (net.isOpen) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(net.ssid, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip2(net.security)
                    Chip2("${net.band} · CH ${net.channel}")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    net.bssid,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SignalBars(percent = net.levelPercent)
        }
    }
}

@Composable
private fun Chip2(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttackSheet(
    net: WifiNetwork,
    kaliManager: KaliManager,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val engine = remember { AttackEngine(kaliManager) }
    var busyType by remember { mutableStateOf<AttackType?>(null) }
    var status by remember { mutableStateOf("") }
    val conf = remember(net.bssid) { AttackConfig(net.ssid, net.bssid, net.channel) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(net.ssid, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${net.bssid} · CH ${net.channel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Атаки", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            AttackType.entries.forEach { type ->
                val busy = busyType == type
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = busyType == null) {
                            scope.launch {
                                busyType = type
                                status = launchAttack(engine, type, conf)
                                busyType = null
                            }
                        }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(type.label, style = MaterialTheme.typography.titleSmall)
                            if (type.needsClient) {
                                Text(
                                    "Требует клиент в сети (можно указать позже)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (status.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text(status, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private suspend fun launchAttack(engine: AttackEngine, type: AttackType, conf: AttackConfig): String {
    return when (type) {
        AttackType.DEAUTH -> {
            engine.deauth(conf, 5, null)
        }
        AttackType.HANDSHAKE -> {
            val outDir = "/sdcard/Download/wifihavoc"
            engine.revertMonitor()
            val res = engine.startHandshakeCapture(conf, outDir)
            if (res.startsWith("OK:")) {
                val pid = res.removePrefix("OK:").toIntOrNull()
                "Захват хэндшейка запущен (airodump, pid $pid). Файл: $outDir/capture.cap"
            } else res
        }
        AttackType.PMKID -> {
            val outDir = "/sdcard/Download/wifihavoc"
            val res = engine.startPmkid(conf, outDir)
            if (res.startsWith("OK:")) {
                val pid = res.removePrefix("OK:").toIntOrNull()
                "PMKID-захват запущен (pid $pid). Файл: $outDir/pmkid.cap"
            } else res
        }
    }
}