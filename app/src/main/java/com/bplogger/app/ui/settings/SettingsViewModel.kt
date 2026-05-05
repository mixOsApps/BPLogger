package com.bplogger.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bplogger.app.data.backup.BackupManager
import com.bplogger.app.data.export.CsvExporter
import com.bplogger.app.data.export.PdfExporter
import com.bplogger.app.data.repository.BpRepository
import com.bplogger.app.data.repository.MedicationRepository
import com.bplogger.app.data.repository.SettingsRepository
import com.bplogger.app.data.sync.GoogleSheetsManager
import com.bplogger.app.data.sync.SyncStatusManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.bplogger.app.worker.BpReminderWorker
import com.bplogger.app.worker.BpSummaryWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class RestoreStatus {
    object Idle : RestoreStatus()
    data class Restoring(val message: String) : RestoreStatus()
    data class Success(val message: String) : RestoreStatus()
    data class Error(val message: String) : RestoreStatus()
}

sealed class BackupStatus {
    object Idle : BackupStatus()
    data class InProgress(val message: String) : BackupStatus()
    data class Success(val message: String) : BackupStatus()
    data class Error(val message: String) : BackupStatus()
}

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val bpRepository: BpRepository,
    private val medicationRepository: MedicationRepository? = null
) : ViewModel() {

    val settings = repo.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    private val _restoreStatus = MutableStateFlow<RestoreStatus>(RestoreStatus.Idle)
    val restoreStatus: StateFlow<RestoreStatus> = _restoreStatus

    private val _backupStatus = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val backupStatus: StateFlow<BackupStatus> = _backupStatus

    fun updateSystolicThreshold(v: Int) = viewModelScope.launch { repo.updateSystolicThreshold(v) }
    fun updateDiastolicThreshold(v: Int) = viewModelScope.launch { repo.updateDiastolicThreshold(v) }
    fun updateAlertInterval(h: Int) = viewModelScope.launch { repo.updateAlertInterval(h) }
    fun updateAutoFillTime(v: Boolean) = viewModelScope.launch { repo.updateAutoFillTime(v) }
    fun updateDateFormat(f: String) = viewModelScope.launch { repo.updateDateFormat(f) }

    fun updateThemeMode(mode: String) = viewModelScope.launch { repo.updateThemeMode(mode) }

    fun updateReminderEnabled(context: Context, enabled: Boolean) = viewModelScope.launch {
        repo.updateReminderEnabled(enabled)
        if (enabled) {
            val s = settings.value
            BpReminderWorker.schedule(context, s?.reminderHour ?: 8, s?.reminderMinute ?: 0)
        } else {
            BpReminderWorker.cancel(context)
        }
    }

    fun updateReminderTime(context: Context, hour: Int, minute: Int) = viewModelScope.launch {
        repo.updateReminderTime(hour, minute)
        if (settings.value?.reminderEnabled == true) {
            BpReminderWorker.schedule(context, hour, minute)
        }
    }

    fun updateSummaryEnabled(context: Context, enabled: Boolean) = viewModelScope.launch {
        repo.updateSummaryEnabled(enabled)
        if (enabled) {
            val freq = settings.value?.summaryFrequency ?: "weekly"
            BpSummaryWorker.schedule(context, freq)
        } else {
            BpSummaryWorker.cancel(context)
        }
    }

    fun updateSummaryFrequency(context: Context, frequency: String) = viewModelScope.launch {
        repo.updateSummaryFrequency(frequency)
        if (settings.value?.summaryNotificationEnabled == true) {
            BpSummaryWorker.schedule(context, frequency)
        }
    }

    fun startRestore(context: Context) {
        viewModelScope.launch {
            _restoreStatus.value = RestoreStatus.Restoring("Starting restore...")
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account == null) {
                _restoreStatus.value = RestoreStatus.Error("Please sign in with Google in the Sync tab first.")
                return@launch
            }
            try {
                val syncStatusManager = SyncStatusManager(
                    bpRepository, repo, GoogleSheetsManager(context)
                )

                val (newCount, skippedCount) = withContext(Dispatchers.IO) {
                    syncStatusManager.restoreFromSheet(account) { message ->
                        _restoreStatus.value = RestoreStatus.Restoring(message)
                    }
                }

                if (newCount == 0 && skippedCount == 0) {
                    val currentMsg = (_restoreStatus.value as? RestoreStatus.Restoring)?.message ?: "No records to restore."
                    _restoreStatus.value = RestoreStatus.Error(currentMsg)
                } else {
                    _restoreStatus.value = RestoreStatus.Success(
                        "Restore complete! $newCount new record(s) added, $skippedCount already existed."
                    )
                }
            } catch (e: Exception) {
                _restoreStatus.value = RestoreStatus.Error("Restore failed: ${e.message}")
            }
        }
    }

    fun clearRestoreStatus() {
        _restoreStatus.value = RestoreStatus.Idle
    }

    fun exportBackup(context: Context, uri: Uri) {
        val medRepo = medicationRepository ?: return
        viewModelScope.launch {
            _backupStatus.value = BackupStatus.InProgress("Exporting...")
            try {
                val result = withContext(Dispatchers.IO) {
                    BackupManager(bpRepository, medRepo).exportBackup(context, uri)
                }
                _backupStatus.value = BackupStatus.Success(
                    "Backup complete! ${result.recordCount} records, ${result.medicationCount} medications exported."
                )
            } catch (e: Exception) {
                _backupStatus.value = BackupStatus.Error("Backup failed: ${e.message}")
            }
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        val medRepo = medicationRepository ?: return
        viewModelScope.launch {
            _backupStatus.value = BackupStatus.InProgress("Importing...")
            try {
                val result = withContext(Dispatchers.IO) {
                    BackupManager(bpRepository, medRepo).importBackup(context, uri)
                }
                _backupStatus.value = BackupStatus.Success(
                    "Import complete! ${result.newRecords} records, ${result.newMedications} medications added. ${result.skipped} skipped."
                )
            } catch (e: Exception) {
                _backupStatus.value = BackupStatus.Error("Import failed: ${e.message}")
            }
        }
    }

    fun clearBackupStatus() {
        _backupStatus.value = BackupStatus.Idle
    }

    fun exportCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            _backupStatus.value = BackupStatus.InProgress("Exporting CSV...")
            try {
                val records = withContext(Dispatchers.IO) { bpRepository.getAllRecordsList() }
                val csv = CsvExporter.export(records)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
                _backupStatus.value = BackupStatus.Success("CSV exported: ${records.size} records.")
            } catch (e: Exception) {
                _backupStatus.value = BackupStatus.Error("CSV export failed: ${e.message}")
            }
        }
    }

    fun exportPdf(context: Context, uri: Uri) {
        viewModelScope.launch {
            _backupStatus.value = BackupStatus.InProgress("Generating PDF...")
            try {
                val records = withContext(Dispatchers.IO) { bpRepository.getAllRecordsList() }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { PdfExporter.exportRecords(records, it) }
                }
                _backupStatus.value = BackupStatus.Success("PDF exported: ${records.size} records.")
            } catch (e: Exception) {
                _backupStatus.value = BackupStatus.Error("PDF export failed: ${e.message}")
            }
        }
    }

    fun exportDoctorReport(
        context: Context,
        uri: Uri,
        startDate: Long,
        endDate: Long,
        patientName: String,
        patientDob: String
    ) {
        val medRepo = medicationRepository ?: return
        viewModelScope.launch {
            _backupStatus.value = BackupStatus.InProgress("Generating doctor report...")
            try {
                val records = withContext(Dispatchers.IO) { bpRepository.getAllRecordsList() }
                val medications = withContext(Dispatchers.IO) { medRepo.getAllList() }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use {
                        PdfExporter.exportDoctorReport(records, medications, it, startDate, endDate, patientName, patientDob)
                    }
                }
                _backupStatus.value = BackupStatus.Success("Doctor report generated successfully.")
            } catch (e: Exception) {
                _backupStatus.value = BackupStatus.Error("Report generation failed: ${e.message}")
            }
        }
    }
}