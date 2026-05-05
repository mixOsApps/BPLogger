package com.bplogger.app.data.sync

import com.bplogger.app.data.db.BpRecord
import com.bplogger.app.data.repository.BpRepository
import com.bplogger.app.data.repository.SettingsRepository
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.first

class SyncStatusManager(
    private val bpRepository: BpRepository,
    private val settingsRepository: SettingsRepository,
    private val googleSheetsManager: GoogleSheetsManager
) {
    private var syncInProgress = false

    fun isSyncInProgress(): Boolean = syncInProgress

    suspend fun startInlineSync(account: GoogleSignInAccount, onLog: (String) -> Unit) {
        if (syncInProgress) return
        syncInProgress = true

        try {
            onLog("Fetching local records...")
            val allRecords: List<BpRecord> = bpRepository.getAllRecordsList()

            val settings = settingsRepository.settings.first()
            var spreadsheetId = settings.googleSpreadsheetId

            if (spreadsheetId.isNullOrEmpty()) {
                onLog("Creating new Google Sheet...")
                spreadsheetId = googleSheetsManager.createSpreadsheet(account)
                settingsRepository.updateGoogleAccount(account.email, spreadsheetId)
                onLog("Sheet created: BPLogger_Data")
            }

            // Step 1: Fetch existing IDs from the Google Sheet
            onLog("Checking Google Sheet for existing records...")
            val existingIds = googleSheetsManager.fetchExistingIds(account, spreadsheetId)

            // Step 2: Find records to push (in app but not on sheet)
            val recordsToSync = allRecords.filter { it.id !in existingIds }

            if (recordsToSync.isEmpty()) {
                // Mark all as synced locally
                if (allRecords.isNotEmpty()) {
                    val allIds = allRecords.map { it.id }
                    bpRepository.markAsSynced(allIds, System.currentTimeMillis())
                }
                settingsRepository.updateLastSyncedAt(System.currentTimeMillis())
                onLog("All ${allRecords.size} records are in sync.")
                return
            }

            // Step 3: Push new records
            if (recordsToSync.isNotEmpty()) {
                onLog("Syncing ${recordsToSync.size} new record(s)...")
                googleSheetsManager.pushRecords(account, spreadsheetId, recordsToSync)
                
                val ids = recordsToSync.map { it.id }
                bpRepository.markAsSynced(ids, System.currentTimeMillis())
                onLog("Pushed ${ids.size} record(s) to Google Sheet.")
            }

            // Note: Deletion of orphaned records is skipped for now to avoid complexity 
            // of row management in direct Sheets API without a script.

            // Mark all remaining local records as synced
            if (allRecords.isNotEmpty()) {
                val allIds = allRecords.map { it.id }
                bpRepository.markAsSynced(allIds, System.currentTimeMillis())
            }
            settingsRepository.updateLastSyncedAt(System.currentTimeMillis())
            onLog("Sync complete! (${recordsToSync.size} added)")

        } catch (e: Exception) {
            onLog("Sync failed: ${e.message}")
            throw e
        } finally {
            syncInProgress = false
        }
    }

    suspend fun restoreFromSheet(account: GoogleSignInAccount, onLog: (String) -> Unit): Pair<Int, Int> {
        val settings = settingsRepository.settings.first()
        val spreadsheetId = settings.googleSpreadsheetId

        if (spreadsheetId.isNullOrEmpty()) {
            onLog("Google Sheet not found. Please sync first.")
            return Pair(0, 0)
        }

        onLog("Connecting to Google Sheet...")

        // Step 1: Fetch all records from sheet
        val remoteRecords = googleSheetsManager.fetchAllRecords(account, spreadsheetId)
        if (remoteRecords.isEmpty()) {
            onLog("No records found on Google Sheet.")
            return Pair(0, 0)
        }

        onLog("Found ${remoteRecords.size} record(s) on Google Sheet.")

        // Step 2: Get local record IDs to skip duplicates
        onLog("Checking local database...")
        val localRecords = bpRepository.getAllRecordsList()
        val localIds = localRecords.map { it.id }.toSet()

        // Step 3: Filter out records that already exist locally
        val newRecords = remoteRecords.filter { it.id !in localIds }
        val skippedCount = remoteRecords.size - newRecords.size

        if (newRecords.isEmpty()) {
            onLog("All ${remoteRecords.size} record(s) already exist locally. Nothing to restore.")
            return Pair(0, skippedCount)
        }

        // Step 4: Insert new records into local database
        onLog("Restoring ${newRecords.size} new record(s)...")
        bpRepository.insertAll(newRecords)

        // Mark restored records as synced
        val newIds = newRecords.map { it.id }
        bpRepository.markAsSynced(newIds, System.currentTimeMillis())

        onLog("Restore complete! ${newRecords.size} new, $skippedCount already existed.")
        return Pair(newRecords.size, skippedCount)
    }
}