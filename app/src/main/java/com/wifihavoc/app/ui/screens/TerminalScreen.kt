package com.wifihavoc.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifihavoc.app.data.KaliManager
import com.wifihavoc.app.data.RootManager
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    kaliManager: KaliManager
) {
    val scope = rememberCoroutineScope()
    val text = remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<String>() }
    val inputFocus = remember { mutableStateOf(true) }
    val kaliReady = remember { mutableStateOf<Boolean?>(null) }
    val installing = remember { mutableStateOf(false) }
    val installProgress = remember { mutableStateOf<Pair<Int, String>>(0 to "") }

    LaunchedEffect(Unit) {
        kaliReady.value = kaliManager.isInstalled()
        if (!kaliReady.value!!) {
            // Авто-установка при первом заходе
            installing.value = true
            val success = kaliManager.install { pct, msg ->
                installProgress.value = pct to msg
            }
            installing.value = false
            kaliReady.value = success
        }
    }

    val session = remember { TerminalSession(kaliManager) }

    fun sendCommand() {
        val cmd = text.value.trim()
        if (cmd.isEmpty()) return
        text.value = ""
        history.add("$ $cmd")
        scope.launch {
            if (cmd == "exit" || cmd == "quit") {
                session.stop()
                history.add("[Session ended]")
            } else {
                session.run(cmd) { line ->
                    history.add(line)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kali Terminal") },
                actions = {
                    if (installing.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(16.dp).size(24.dp),
                            progress = installProgress.value.first / 100f
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (installing.value) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(installProgress.value.second, style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = installProgress.value.first / 100f)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(12.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status bar
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Kali Linux", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    if (kaliReady.value == true) {
                        Badge { Text("READY", style = MaterialTheme.typography.labelSmall) }
                    } else if (kaliReady.value == false) {
                        Badge { Text("ERROR", style = MaterialTheme.typography.labelSmall) }
                    } else {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
                if (session.isRunning) {
                    Text("PID: ${session.currentPid ?: "?"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Terminal output area
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Top
                    ) {
                        history.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        // Current input line
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$ ", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = Color(0xFF00FF00)))
                            TextField(
                                value = text.value,
                                onValueChange = { text.value = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = Color.White),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    cursorColor = Color(0xFF00FF00)
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { sendCommand() }
                                )
                            )
                        }
                    }
                }
            }

            // Quick command bar
            ScrollableTabRow(
                selectedTabIndex = 0,
                modifier = Modifier.fillMaxWidth(),
                divider = {}
            ) {
                listOf(
                    "ls -la" to "List",
                    "iw dev" to "WiFi",
                    "aircrack-ng --help" to "Aircrack",
                    "john --help" to "John",
                    "hashcat --help" to "Hashcat"
                ).forEach { (cmd, label) ->
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .clickable { text.value = cmd; sendCommand() }
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

class TerminalSession(private val kaliManager: KaliManager) {
    var isRunning = false
    var currentPid: Int? = null
    private var job: Job? = null

    fun run(cmd: String, onLine: (String) -> Unit) {
        if (isRunning) return
        isRunning = true
        job = CoroutineScope(Dispatchers.IO).launch {
            kaliManager.execInKaliStream("sh", "-c", cmd) { line ->
                onLine(line)
            }
        }
    }

    fun stop() {
        job?.cancel()
        isRunning = false
        currentPid = null
    }
}