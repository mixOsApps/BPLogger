package com.bplogger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bplogger.app.data.repository.AppSettings
import com.bplogger.app.ui.home.HomeScreen
import com.bplogger.app.ui.import.ImportScreen
import com.bplogger.app.ui.settings.SettingsScreen
import com.bplogger.app.ui.sync.SyncScreen
import com.bplogger.app.ui.theme.BPLoggerTheme
import com.bplogger.app.ui.theme.BPRed
import com.bplogger.app.ui.theme.BPYellow
import com.bplogger.app.ui.medication.MedicationScreen
import com.bplogger.app.ui.trends.TrendsScreen
import androidx.lifecycle.lifecycleScope
import com.bplogger.app.util.AppUpdateManager
import kotlinx.coroutines.launch


sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Log      : Screen("log",      "Log",      Icons.Default.MonitorHeart)
    object Import   : Screen("import",   "Import",   Icons.Default.FileDownload)
    object Sync     : Screen("sync",     "Sync",     Icons.Default.Sync)
    object Trends   : Screen("trends",   "Trends",   Icons.Default.ShowChart)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Medication : Screen("medication", "Medication", Icons.Default.Medication)
}

val bottomNavItems = listOf(
    Screen.Log, Screen.Import, Screen.Sync, Screen.Trends, Screen.Settings
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val openAddRecord = intent?.action == "com.bplogger.app.ACTION_ADD_RECORD"

        // 🔄 Auto-check for app update on launch (respects 1-hour throttle)
        lifecycleScope.launch {
            AppUpdateManager.checkForUpdate(this@MainActivity, isManualCheck = false)
        }

        setContent {
            val app = applicationContext as BpLoggerApplication
            val settingsFlow = remember { app.settingsRepository.settings }
            val settings by settingsFlow.collectAsState(initial = AppSettings())
            val themeMode = settings.themeMode

            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            // Control system status bar colors to match TopAppBar
            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        // Dark mode: yellow TopAppBar → dark status bar icons on yellow
                        SystemBarStyle.light(
                            android.graphics.Color.parseColor("#FFFBC02D"),
                            android.graphics.Color.parseColor("#FFC49000")
                        )
                    } else {
                        // Light mode: red TopAppBar → white status bar icons on red
                        SystemBarStyle.dark(
                            android.graphics.Color.parseColor("#FFD32F2F")
                        )
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(
                            android.graphics.Color.parseColor("#FF121212")
                        )
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.parseColor("#FFF5F5F5"),
                            android.graphics.Color.parseColor("#FF1A1A1A")
                        )
                    }
                )
            }

            BPLoggerTheme(themeMode = themeMode) {
                BPLoggerApp(openAddRecord = openAddRecord)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BPLoggerApp(openAddRecord: Boolean = false) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val app = (navController.context.applicationContext as BpLoggerApplication)
    val viewModelFactory = ViewModelFactory(
        app,
        app.bpRepository,
        app.medicationRepository,
        app.settingsRepository
    )

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f



    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BP Logger") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) BPYellow else BPRed,
                    titleContentColor = if (isDark)
                        androidx.compose.ui.graphics.Color(0xFF1A1A1A)
                    else
                        androidx.compose.ui.graphics.Color.White,
                    navigationIconContentColor = if (isDark)
                        androidx.compose.ui.graphics.Color(0xFF1A1A1A)
                    else
                        androidx.compose.ui.graphics.Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar {
                val accentColor = if (isDark) BPYellow else BPRed
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = accentColor,
                            selectedTextColor = accentColor,
                            indicatorColor = accentColor.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Log.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Log.route) {
                HomeScreen(
                    factory = viewModelFactory,
                    openAddDialog = openAddRecord
                )
            }
            composable(Screen.Import.route) { ImportScreen(factory = viewModelFactory) }
            composable(Screen.Sync.route)   { SyncScreen(factory = viewModelFactory) }
            composable(Screen.Trends.route) { TrendsScreen(factory = viewModelFactory) }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    factory = viewModelFactory,
                    onNavigateToMedications = {
                        navController.navigate(Screen.Medication.route)
                    }
                )
            }
            composable(Screen.Medication.route) { MedicationScreen(factory = viewModelFactory) }
        }
    }
}