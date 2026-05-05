package com.bplogger.app.ui.sync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bplogger.app.data.repository.BpRepository
import com.bplogger.app.data.repository.SettingsRepository
import com.bplogger.app.data.sync.GoogleSheetsManager
import com.bplogger.app.data.sync.SyncStatusManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class SyncStatus {
    object Idle : SyncStatus()
    data class Syncing(val message: String) : SyncStatus()
    data class Success(val message: String) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

class SyncViewModel(
    private val bpRepository: BpRepository,
    private val settingsRepository: SettingsRepository,
    private val googleSheetsManager: GoogleSheetsManager
) : ViewModel() {

    val settings = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus

    private val syncStatusManager = SyncStatusManager(
        bpRepository, settingsRepository, googleSheetsManager
    )

    private val _googleAccount = MutableStateFlow<GoogleSignInAccount?>(null)
    val googleAccount: StateFlow<GoogleSignInAccount?> = _googleAccount

    fun checkSignedInAccount(context: Context) {
        _googleAccount.value = GoogleSignIn.getLastSignedInAccount(context)
    }

    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SheetsScopes.SPREADSHEETS))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun onSignInSuccess(account: GoogleSignInAccount) {
        _googleAccount.value = account
        viewModelScope.launch {
            settingsRepository.updateGoogleAccount(account.email, settings.value?.googleSpreadsheetId)
        }
    }

    fun onSignInError(message: String) {
        _syncStatus.value = SyncStatus.Error(message)
    }

    fun signOut(context: Context) {
        getGoogleSignInClient(context).signOut().addOnCompleteListener {
            _googleAccount.value = null
            viewModelScope.launch {
                settingsRepository.updateGoogleAccount(null, null)
            }
        }
    }

    fun manualSync() {
        val account = _googleAccount.value
        if (account == null) {
            _syncStatus.value = SyncStatus.Error("Please sign in with Google first")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (syncStatusManager.isSyncInProgress()) {
                _syncStatus.value = SyncStatus.Syncing("Sync already in progress...")
                return@launch
            }

            _syncStatus.value = SyncStatus.Syncing("Starting sync...")

            try {
                syncStatusManager.startInlineSync(account) { message ->
                    _syncStatus.value = SyncStatus.Syncing(message)
                }
                _syncStatus.value = SyncStatus.Success("Sync completed successfully!")
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error("Sync failed: ${e.message}")
            }
        }
    }

    fun restoreFromSheet() {
        val account = _googleAccount.value
        if (account == null) {
            _syncStatus.value = SyncStatus.Error("Please sign in with Google first")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _syncStatus.value = SyncStatus.Syncing("Restoring data...")
            try {
                val (newCount, skippedCount) = syncStatusManager.restoreFromSheet(account) { message ->
                    _syncStatus.value = SyncStatus.Syncing(message)
                }
                _syncStatus.value = SyncStatus.Success("Restored $newCount records ($skippedCount already existed)")
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error("Restore failed: ${e.message}")
            }
        }
    }

    fun clearStatus() {
        _syncStatus.value = SyncStatus.Idle
    }
}