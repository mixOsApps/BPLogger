package com.bplogger.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bplogger.app.data.repository.BpRepository
import com.bplogger.app.data.repository.MedicationRepository
import com.bplogger.app.data.repository.SettingsRepository
import com.bplogger.app.data.sync.GoogleSheetsManager
import com.bplogger.app.data.sync.SyncStatusManager
import com.bplogger.app.ui.home.HomeViewModel
import com.bplogger.app.ui.import.ImportViewModel
import com.bplogger.app.ui.medication.MedicationViewModel
import com.bplogger.app.ui.settings.SettingsViewModel
import com.bplogger.app.ui.sync.SyncViewModel
import com.bplogger.app.ui.trends.TrendsViewModel

class ViewModelFactory(
    private val context: Context,
    private val bpRepository: BpRepository,
    private val medicationRepository: MedicationRepository,
    private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(bpRepository) as T
        }
        if (modelClass.isAssignableFrom(ImportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ImportViewModel(bpRepository) as T
        }
        if (modelClass.isAssignableFrom(MedicationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MedicationViewModel(medicationRepository) as T
        }
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsRepository, bpRepository, medicationRepository) as T
        }
        if (modelClass.isAssignableFrom(SyncViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SyncViewModel(bpRepository, settingsRepository, GoogleSheetsManager(context)) as T
        }
        if (modelClass.isAssignableFrom(TrendsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TrendsViewModel(bpRepository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
