package com.wifihavoc.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wifihavoc.app.data.RadioState
import com.wifihavoc.app.data.RootManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen() {
    val scope = rememberCoroutineScope()
    var rootOk by remember { mutableStateOf<Boolean?>(null) }
    var state by remember { mutableStateOf<RadioState?>(null) }
    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        rootOk = RootManager.isRootAvailable
        state = RootManager.radioState()
        log = "root: ${if (rootOk == true) "OK (Magisk)" else "нет root"}" +
            "\nРеальное состояние интерфейса: ${if (state?.reported == true) "MONITOR" else "MANAGED"}\n" +
            "con_mode: ${state?.conMode} · iface: ${if (state?.ifaceUp == true) "UP" else "DOWN"} · " +
            (if (state?.ifaceMonitor == true) "radiotap" else "managed")
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Monitor Mode") })
        }
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RootStatusCard(rootOk)

            val monOn = state?.reported == true
            StatusPill(monOn)

            val live = state?.reported == true
            val tip = if (live) {
                "Телефон сейчас в монитор-режиме и отключён от Wi-Fi-сети. " +
                    "Для захвата/атак вернись в Сканер и выбери сеть."
            } else {
                "Wi-Fi работает штатно. Включи monitor mode, чтобы атаковать сети." +
                    "\n\nВнимание: при включении телефон выйдет из Wi-Fi — по окончании выключи, иначе останешься без сети."
            }
            AssistChip(
                onClick = {},
                label = { Text(tip, style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) }
            )

            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        if (state?.reported == true) {
                            val ok = RootManager.disableMonitorMode()
                            log += "\n→ disable: ${if (ok) "OK" else "FAIL"}"
                        } else {
                            val ok = RootManager.enableMonitorMode()
                            log += "\n→ enable: ${if (ok) "OK (Wi-Fi отключен)" else "FAIL"}"
                        }
                        state = RootManager.radioState()
                        busy = false
                    }
                },
                enabled = rootOk == true && !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state?.reported == true)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state?.reported == true) "Выключить (вернуть Wi-Fi)" else "Включить Monitor Mode")
            }

            LogCard(log)
        }
    }
}

@Composable
private fun RootStatusCard(rootOk: Boolean?) {
    val color: Color
    val label: String
    val icon: ImageVector
    when (rootOk) {
        true -> {
            color = MaterialTheme.colorScheme.tertiary
            label = "Root: OK (Magisk)"
            icon = Icons.Filled.Security
        }
        false -> {
            color = MaterialTheme.colorScheme.error
            label = "Root: НЕТ"
            icon = Icons.Filled.SignalWifiOff
        }
        else -> {
            color = MaterialTheme.colorScheme.onSurfaceVariant
            label = "Проверка root..."
            icon = Icons.Filled.Security
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun StatusPill(on: Boolean) {
    val container = if (on) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val content = if (on) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = RoundedCornerShape(14.dp), color = container) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                if (on) Icons.Filled.SignalWifiOff else Icons.Filled.WifiTethering,
                contentDescription = null,
                tint = content
            )
            Column {
                Text(
                    if (on) "MONITOR MODE" else "MANAGED",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = content
                )
                Text(
                    if (on) "Wi-Fi отключён от сети" else "Wi-Fi работает",
                    style = MaterialTheme.typography.bodySmall,
                    color = content
                )
            }
        }
    }
}

@Composable
private fun LogCard(log: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp)) {
            Text("Лог", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                log.ifEmpty { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
