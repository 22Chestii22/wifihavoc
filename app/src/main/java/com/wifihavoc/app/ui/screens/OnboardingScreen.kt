package com.wifihavoc.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifihavoc.app.data.KaliManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    kaliManager: KaliManager,
    onFinish: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentStep = remember { mutableStateOf(0) }
    val progress = remember { mutableStateOf(0f) }
    val status = remember { mutableStateOf("") }
    val animatedProgress by animateFloatAsState(progress.value / 100f, animationSpec = spring(dampingRatio = 1f))
    val showTerminalTip = remember { mutableStateOf(false) }
    val installing = remember { mutableStateOf(false) }

    val steps = listOf(
        OnboardingStep("Kali Linux", "Полноценное окружение для атак", androidx.compose.material.icons.Icons.Filled.Terminal, MaterialTheme.colorScheme.primary),
        OnboardingStep("Aircrack-ng", "Deauth, Handshake, PMKID", androidx.compose.material.icons.Icons.Filled.Security, MaterialTheme.colorScheme.tertiary),
        OnboardingStep("John & Hashcat", "Крек хэндшейков и PMKID", androidx.compose.material.icons.Icons.Filled.CheckCircle, MaterialTheme.colorScheme.secondary),
        OnboardingStep("Monitor Mode", "Авто включение/выключение", androidx.compose.material.icons.Icons.Filled.WifiTethering, MaterialTheme.colorScheme.primary),
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress indicator
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Установка Kali Linux", style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = animatedProgress, modifier = Modifier.fillMaxWidth().height(8.dp))
            Spacer(Modifier.height(4.dp))
            Text("${progress.value.toInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(status.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }

        // Feature cards
        Column(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            steps.forEachIndexed { index, stepData ->
                val isActive = index == currentStep.value
                val isDone = index < currentStep.value
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDone) MaterialTheme.colorScheme.tertiaryContainer
                        else if (isActive) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    elevation = if (isActive) CardDefaults.cardElevation(defaultElevation = 8.dp) else CardDefaults.cardElevation()
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(stepData.icon, contentDescription = null, tint = if (isDone) MaterialTheme.colorScheme.onTertiaryContainer else if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(stepData.title, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = if (isDone) MaterialTheme.colorScheme.onTertiaryContainer else if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                if (isDone) Icon(androidx.compose.material.icons.Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                else if (isActive) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                            Text(stepData.desc, style = MaterialTheme.typography.bodySmall, color = if (isDone) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f) else if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Action button
        Button(
            onClick = {
                if (currentStep.value < steps.size - 1) {
                    // Simulate step progress
                    scope.launch {
                        val s = currentStep.value
                        var p = (s * 25)
                        status.value = "Подготовка ${steps[s].title.toLowerCase()}..."
                        while (p < (s + 1) * 25) {
                            p += 5
                            progress.value = p.toFloat()
                            delay(100)
                        }
                        currentStep.value = s + 1
                    }
                } else if (currentStep.value == steps.size - 1) {
                    // Final: actually install Kali
                    installing.value = true
                    scope.launch {
                        status.value = "Скачивание и установка Kali..."
                        val success = kaliManager.install { pct, msg ->
                            progress.value = pct.toFloat()
                            status.value = msg
                        }
                        installing.value = false
                        if (success) {
                            progress.value = 100f
                            status.value = "Готово! Kali установлен."
                            delay(1000)
                            onFinish()
                        } else {
                            status.value = "Ошибка установки. Попробуйте снова."
                        }
                    }
                }
            },
            enabled = !installing.value,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (installing.value) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text(when {
                currentStep.value < steps.size - 1 -> "Далее: ${steps[currentStep.value].title}"
                currentStep.value == steps.size - 1 -> "Установить Kali"
                else -> "Начать"
            })
        }

        if (showTerminalTip.value) {
            Text(
                "Совет: после установки зайдите в раздел «Терминал» — там полноценный Kali Linux с aircrack-ng, john, hashcat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

data class OnboardingStep(
    val title: String,
    val desc: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: androidx.compose.ui.graphics.Color
)
