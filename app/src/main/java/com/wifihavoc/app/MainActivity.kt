package com.wifihavoc.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wifihavoc.app.data.KaliManager
import com.wifihavoc.app.ui.screens.MonitorScreen
import com.wifihavoc.app.ui.screens.OnboardingScreen
import com.wifihavoc.app.ui.screens.ScannerScreen
import com.wifihavoc.app.ui.screens.TerminalScreen
import com.wifihavoc.app.ui.theme.WifiHavocTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        permissionLauncher.launch(perms.toTypedArray())

        setContent {
            WifiHavocTheme {
                WifiHavocApp()
            }
        }
    }
}

data class NavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

val navItems = listOf(
    NavItem("scanner", "Сканер", Icons.Filled.Radar),
    NavItem("terminal", "Терминал", Icons.Filled.Terminal),
    NavItem("monitor", "Monitor", Icons.Filled.Security),
)

@Composable
fun WifiHavocApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current
    val kaliManager = remember { KaliManager(context) }
    val showOnboarding = remember { mutableStateOf(!kaliManager.isInstalled()) }

    val bg = MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = bg,
        bottomBar = {
            if (!showOnboarding.value) {
                NavigationBar {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo("scanner") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (showOnboarding.value) {
            OnboardingScreen(kaliManager = kaliManager) {
                showOnboarding.value = false
            }
        } else {
            NavHost(
                navController = navController,
                startDestination = "scanner",
                modifier = Modifier.padding(padding)
            ) {
                composable("scanner") { ScannerScreen(kaliManager = kaliManager) }
                composable("terminal") { TerminalScreen(kaliManager = kaliManager) }
                composable("monitor") { MonitorScreen() }
            }
        }
    }
}