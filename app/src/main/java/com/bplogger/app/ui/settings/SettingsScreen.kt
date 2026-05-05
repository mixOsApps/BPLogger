package com.bplogger.app.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bplogger.app.ViewModelFactory
import com.bplogger.app.ui.theme.BPRed
import com.bplogger.app.ui.theme.BPYellow
import com.bplogger.app.ui.theme.accentColor
import com.bplogger.app.ui.theme.greenAccent
import com.bplogger.app.util.AppUpdateManager
import com.bplogger.app.worker.BpReminderWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    factory: ViewModelFactory,
    onNavigateToMedications: () -> Unit = {}
) {
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val accent = MaterialTheme.accentColor
    val green = MaterialTheme.greenAccent

    // ── Notification permission handling ─────────────────────
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted — enable the reminder
            viewModel.updateReminderEnabled(context, true)
        } else {
            // Permission denied — show explanation dialog
            showPermissionDeniedDialog = true
        }
    }

    var systolicInput by remember(settings?.systolicAlertThreshold) {
        mutableStateOf(settings?.systolicAlertThreshold?.toString() ?: "140")
    }
    var diastolicInput by remember(settings?.diastolicAlertThreshold) {
        mutableStateOf(settings?.diastolicAlertThreshold?.toString() ?: "90")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = accent
        )

        // ── Alert Settings ──────────────────────────────────────
        SettingsSection(
            icon = Icons.Default.NotificationsActive,
            title = "Alert Thresholds",
            tint = accent
        ) {
            Text(
                "Notifications trigger when readings exceed these values.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = systolicInput,
                    onValueChange = { systolicInput = it },
                    label = { Text("Systolic (mmHg)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        IconButton(onClick = {
                            systolicInput.toIntOrNull()?.let { viewModel.updateSystolicThreshold(it) }
                        }) { Icon(Icons.Default.Check, null, tint = accent) }
                    }
                )
                OutlinedTextField(
                    value = diastolicInput,
                    onValueChange = { diastolicInput = it },
                    label = { Text("Diastolic (mmHg)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        IconButton(onClick = {
                            diastolicInput.toIntOrNull()?.let { viewModel.updateDiastolicThreshold(it) }
                        }) { Icon(Icons.Default.Check, null, tint = accent) }
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Alert Check Frequency",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "1h", 6 to "6h", 12 to "12h", 24 to "24h").forEach { (h, label) ->
                    FilterChip(
                        selected = settings?.alertCheckIntervalHours == h,
                        onClick = { viewModel.updateAlertInterval(h) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.15f),
                            selectedLabelColor = accent
                        )
                    )
                }
            }
        }

        // ── Entry Preferences ───────────────────────────────────
        SettingsSection(
            icon = Icons.Default.Edit,
            title = "Entry Preferences",
            tint = accent
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-fill Current Time", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Pre-fill time field when adding a record",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = settings?.autoFillCurrentTime ?: true,
                    onCheckedChange = viewModel::updateAutoFillTime,
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(checkedTrackColor = accent)
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Date Format",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DD/MM/YYYY", "MM/DD/YYYY", "YYYY-MM-DD").forEach { fmt ->
                    FilterChip(
                        selected = settings?.dateFormat == fmt,
                        onClick = { viewModel.updateDateFormat(fmt) },
                        label = { Text(fmt, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.15f),
                            selectedLabelColor = accent
                        )
                    )
                }
            }
        }

        // ── Medications ───────────────────────────────────────────
        SettingsSection(
            icon = Icons.Default.Medication,
            title = "Medications",
            tint = accent
        ) {
            Text(
                "Track your medications to see them alongside BP readings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onNavigateToMedications,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Icon(Icons.Default.Medication, null)
                Spacer(Modifier.width(8.dp))
                Text("Manage Medications")
            }
        }

        // ── Daily Reminders ─────────────────────────────────────
        val reminderEnabled = settings?.reminderEnabled ?: false
        val reminderHour = settings?.reminderHour ?: 8
        val reminderMinute = settings?.reminderMinute ?: 0
        var showTimePicker by remember { mutableStateOf(false) }

        SettingsSection(
            icon = Icons.Default.Alarm,
            title = "Daily Reminders",
            tint = accent
        ) {
            Text(
                "Get a daily notification reminding you to log your blood pressure.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enable Reminder", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            // Check notification permission first (Android 13+)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !BpReminderWorker.hasNotificationPermission(context)
                            ) {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            } else {
                                viewModel.updateReminderEnabled(context, true)
                            }
                        } else {
                            viewModel.updateReminderEnabled(context, false)
                        }
                    },
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(checkedTrackColor = accent)
                )
            }

            if (reminderEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Reminder Time", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            formatTime(reminderHour, reminderMinute),
                            style = MaterialTheme.typography.bodySmall,
                            color = accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(Icons.Default.AccessTime, null, tint = accent)
                }
            }
        }

        // Time Picker Dialog
        if (showTimePicker) {
            val timePickerState = rememberTimePickerState(
                initialHour = reminderHour,
                initialMinute = reminderMinute,
                is24Hour = false
            )

            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                title = { Text("Set Reminder Time", color = accent) },
                text = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        TimePicker(
                            state = timePickerState,
                            colors = TimePickerDefaults.colors(
                                clockDialColor = accent.copy(alpha = 0.08f),
                                selectorColor = accent,
                                containerColor = MaterialTheme.colorScheme.surface,
                                periodSelectorSelectedContainerColor = accent.copy(alpha = 0.15f),
                                periodSelectorSelectedContentColor = accent,
                                timeSelectorSelectedContainerColor = accent.copy(alpha = 0.15f),
                                timeSelectorSelectedContentColor = accent
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showTimePicker = false
                            viewModel.updateReminderTime(
                                context,
                                timePickerState.hour,
                                timePickerState.minute
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Text("Set")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Permission Denied Dialog
        if (showPermissionDeniedDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDeniedDialog = false },
                title = { Text("Notification Permission Required", color = accent) },
                text = {
                    Text(
                        "To receive daily reminders, you need to allow notifications for BP Logger.\n\n" +
                                "Please enable notifications in the app settings."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPermissionDeniedDialog = false
                            // Open app notification settings
                            val intent = Intent().apply {
                                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDeniedDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // ── Summary Notifications ─────────────────────────────
        val summaryEnabled = settings?.summaryNotificationEnabled ?: false
        val summaryFrequency = settings?.summaryFrequency ?: "weekly"

        SettingsSection(
            icon = Icons.Default.Summarize,
            title = "Summary Notifications",
            tint = accent
        ) {
            Text(
                "Receive periodic summaries of your blood pressure readings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enable Summary", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = summaryEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.updateSummaryEnabled(context, enabled)
                    },
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(checkedTrackColor = accent)
                )
            }

            if (summaryEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Frequency",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("weekly" to "Weekly", "monthly" to "Monthly").forEach { (freq, label) ->
                        FilterChip(
                            selected = summaryFrequency == freq,
                            onClick = { viewModel.updateSummaryFrequency(context, freq) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.15f),
                                selectedLabelColor = accent
                            )
                        )
                    }
                }
            }
        }

        // ── Theme ───────────────────────────────────────────────
        val currentTheme = settings?.themeMode ?: "system"

        SettingsSection(
            icon = Icons.Default.Palette,
            title = "Theme",
            tint = accent
        ) {
            Text(
                "Choose your preferred appearance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("light" to "Light", "dark" to "Dark", "system" to "System").forEach { (mode, label) ->
                    FilterChip(
                        selected = currentTheme == mode,
                        onClick = { viewModel.updateThemeMode(mode) },
                        label = { Text(label) },
                        leadingIcon = {
                            when (mode) {
                                "light" -> Icon(Icons.Default.LightMode, null, modifier = Modifier.size(16.dp))
                                "dark" -> Icon(Icons.Default.DarkMode, null, modifier = Modifier.size(16.dp))
                                else -> Icon(Icons.Default.SettingsBrightness, null, modifier = Modifier.size(16.dp))
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.15f),
                            selectedLabelColor = accent,
                            selectedLeadingIconColor = accent
                        )
                    )
                }
            }
        }

        // ── Backup & Restore (Local) ─────────────────────────────
        val backupStatus by viewModel.backupStatus.collectAsState()

        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            uri?.let { viewModel.exportBackup(context, it) }
        }

        val importLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let { viewModel.importBackup(context, it) }
        }

        SettingsSection(
            icon = Icons.Default.SaveAlt,
            title = "Backup & Restore",
            tint = accent
        ) {
            Text(
                "Export all data to a JSON file or restore from a backup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { exportLauncher.launch("bp_logger_backup.json") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import")
                }
            }

            when (val status = backupStatus) {
                is BackupStatus.InProgress -> {
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = BPYellow.copy(alpha = 0.1f))) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = BPYellow, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(status.message, style = MaterialTheme.typography.bodySmall, color = BPYellow)
                        }
                    }
                }
                is BackupStatus.Success -> {
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = green.copy(alpha = 0.1f))) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = green, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(status.message, style = MaterialTheme.typography.bodySmall, color = green)
                        }
                    }
                }
                is BackupStatus.Error -> {
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = BPRed.copy(alpha = 0.1f))) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = BPRed, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(status.message, style = MaterialTheme.typography.bodySmall, color = BPRed)
                        }
                    }
                }
                else -> {}
            }
        }

        // ── Export Data ───────────────────────────────────────────
        val csvExportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/csv")
        ) { uri -> uri?.let { viewModel.exportCsv(context, it) } }

        val pdfExportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf")
        ) { uri -> uri?.let { viewModel.exportPdf(context, it) } }

        SettingsSection(
            icon = Icons.Default.Description,
            title = "Export Data",
            tint = accent
        ) {
            Text(
                "Export your BP records as CSV or PDF for sharing with your doctor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { csvExportLauncher.launch("bp_records.csv") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("Export CSV")
                }
                OutlinedButton(
                    onClick = { pdfExportLauncher.launch("bp_records.pdf") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export PDF")
                }
            }
        }

        // ── Doctor Report ────────────────────────────────────────
        var showDoctorReportDialog by remember { mutableStateOf(false) }

        SettingsSection(
            icon = Icons.Default.LocalHospital,
            title = "Doctor Report",
            tint = accent
        ) {
            Text(
                "Generate a comprehensive report for your doctor with averages, trends, and classification breakdown.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showDoctorReportDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Icon(Icons.Default.LocalHospital, null)
                Spacer(Modifier.width(8.dp))
                Text("Generate Doctor Report")
            }
        }

        // Doctor Report Dialog
        if (showDoctorReportDialog) {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            var patientName by remember { mutableStateOf("") }
            var patientDob by remember { mutableStateOf("") }
            var startDateStr by remember {
                mutableStateOf(dateFormat.format(Date(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)))
            }
            var endDateStr by remember {
                mutableStateOf(dateFormat.format(Date()))
            }

            val doctorReportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/pdf")
            ) { uri ->
                uri?.let {
                    val startMs = try { dateFormat.parse(startDateStr)?.time ?: 0L } catch (_: Exception) { 0L }
                    val endMs = try { dateFormat.parse(endDateStr)?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
                    viewModel.exportDoctorReport(context, it, startMs, endMs, patientName, patientDob)
                    showDoctorReportDialog = false
                }
            }

            AlertDialog(
                onDismissRequest = { showDoctorReportDialog = false },
                title = { Text("Doctor Report", color = accent) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = patientName,
                            onValueChange = { patientName = it },
                            label = { Text("Patient Name (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = patientDob,
                            onValueChange = { patientDob = it },
                            label = { Text("Date of Birth (optional)") },
                            placeholder = { Text("dd/MM/yyyy") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = startDateStr,
                                onValueChange = { startDateStr = it },
                                label = { Text("From") },
                                placeholder = { Text("dd/MM/yyyy") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = endDateStr,
                                onValueChange = { endDateStr = it },
                                label = { Text("To") },
                                placeholder = { Text("dd/MM/yyyy") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { doctorReportLauncher.launch("bp_doctor_report.pdf") },
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Text("Generate")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDoctorReportDialog = false }) { Text("Cancel") }
                }
            )
        }

        // ── Restore from Google Sheet ───────────────────────────
        val restoreStatus by viewModel.restoreStatus.collectAsState()
        var showRestoreConfirmDialog by remember { mutableStateOf(false) }

        SettingsSection(
            icon = Icons.Default.CloudDownload,
            title = "Restore from Google Sheet",
            tint = accent
        ) {
            Text(
                "Pull records from your synced Google Sheet back into the app. " +
                        "Existing records will be skipped.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { showRestoreConfirmDialog = true },
                enabled = restoreStatus !is RestoreStatus.Restoring && !settings?.googleAccountEmail.isNullOrEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                if (restoreStatus is RestoreStatus.Restoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.CloudDownload, null)
                }
                Spacer(Modifier.width(8.dp))
                Text("Restore Now")
            }

            if (settings?.googleAccountEmail.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sign in with Google in the Sync tab first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BPRed.copy(alpha = 0.7f)
                )
            }

            // Inline status messages
            when (val status = restoreStatus) {
                is RestoreStatus.Restoring -> {
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = BPYellow.copy(alpha = 0.1f))) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = BPYellow,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(status.message, style = MaterialTheme.typography.bodySmall, color = BPYellow)
                        }
                    }
                }
                is RestoreStatus.Success -> {
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = green.copy(alpha = 0.1f))) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = green, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(status.message, style = MaterialTheme.typography.bodySmall, color = green)
                        }
                    }
                }
                is RestoreStatus.Error -> {
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = BPRed.copy(alpha = 0.1f))) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, null, tint = BPRed, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(status.message, style = MaterialTheme.typography.bodySmall, color = BPRed)
                        }
                    }
                }
                else -> {}
            }
        }

        // Confirmation Dialog
        if (showRestoreConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirmDialog = false },
                title = {
                    Text("Restore Records", color = accent)
                },
                text = {
                    Text(
                        "This will pull all records from your Google Sheet and add any that don't already exist in the app.\n\n" +
                                "Existing records will be skipped (no duplicates).\n\n" +
                                "Continue?"
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRestoreConfirmDialog = false
                            viewModel.clearRestoreStatus()
                            viewModel.startRestore(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Text("Restore")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // ── App Update ──────────────────────────────────────────
        SettingsSection(
            icon = Icons.Default.SystemUpdate,
            title = "App Update",
            tint = MaterialTheme.colorScheme.primary
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { AppUpdateManager.checkForUpdate(context as Activity, true) } },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Check for Updates", style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.Default.ChevronRight, null)
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            text = "BP Logger v${AppUpdateManager.getCurrentVersionName(context)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

/** Format hour/minute to readable time string */
private fun formatTime(hour: Int, minute: Int): String {
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "%d:%02d %s".format(displayHour, minute, amPm)
}

@Composable
fun SettingsSection(
    icon: ImageVector,
    title: String,
    tint: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = tint)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            content()
        }
    }
}