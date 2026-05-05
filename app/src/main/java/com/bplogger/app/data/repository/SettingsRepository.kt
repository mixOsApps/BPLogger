package com.bplogger.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "bp_settings")

data class AppSettings(
    val systolicAlertThreshold: Int = 140,
    val diastolicAlertThreshold: Int = 90,
    val alertCheckIntervalHours: Int = 6,
    val autoFillCurrentTime: Boolean = true,
    val dateFormat: String = "DD/MM/YYYY",
    val appsScriptUrl: String = "",
    val googleAccountEmail: String? = null,
    val googleSpreadsheetId: String? = null,
    val lastSyncedAt: Long = 0L,
    val themeMode: String = "system",          // "light", "dark", "system"
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 8,                 // default 8:00 AM
    val reminderMinute: Int = 0,
    val summaryNotificationEnabled: Boolean = false,
    val summaryFrequency: String = "weekly"    // "weekly" or "monthly"
)

class SettingsRepository(private val context: Context) {
    companion object {
        val SYSTOLIC_THRESHOLD = intPreferencesKey("systolic_threshold")
        val DIASTOLIC_THRESHOLD = intPreferencesKey("diastolic_threshold")
        val ALERT_INTERVAL_HOURS = intPreferencesKey("alert_interval_hours")
        val AUTO_FILL_TIME = booleanPreferencesKey("auto_fill_time")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val APPS_SCRIPT_URL = stringPreferencesKey("apps_script_url")
        val GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")
        val GOOGLE_SPREADSHEET_ID = stringPreferencesKey("google_spreadsheet_id")
        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val SUMMARY_ENABLED = booleanPreferencesKey("summary_notification_enabled")
        val SUMMARY_FREQUENCY = stringPreferencesKey("summary_frequency")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            systolicAlertThreshold = prefs[SYSTOLIC_THRESHOLD] ?: 140,
            diastolicAlertThreshold = prefs[DIASTOLIC_THRESHOLD] ?: 90,
            alertCheckIntervalHours = prefs[ALERT_INTERVAL_HOURS] ?: 6,
            autoFillCurrentTime = prefs[AUTO_FILL_TIME] ?: true,
            dateFormat = prefs[DATE_FORMAT] ?: "DD/MM/YYYY",
            appsScriptUrl = prefs[APPS_SCRIPT_URL] ?: "",
            googleAccountEmail = prefs[GOOGLE_ACCOUNT_EMAIL],
            googleSpreadsheetId = prefs[GOOGLE_SPREADSHEET_ID],
            lastSyncedAt = prefs[LAST_SYNCED_AT] ?: 0L,
            themeMode = prefs[THEME_MODE] ?: "system",
            reminderEnabled = prefs[REMINDER_ENABLED] ?: false,
            reminderHour = prefs[REMINDER_HOUR] ?: 8,
            reminderMinute = prefs[REMINDER_MINUTE] ?: 0,
            summaryNotificationEnabled = prefs[SUMMARY_ENABLED] ?: false,
            summaryFrequency = prefs[SUMMARY_FREQUENCY] ?: "weekly"
        )
    }

    /** Blocking read for non-Compose contexts */
    fun getThemeModeSync(): String {
        val prefs = context.dataStore.data
        // Use runBlocking-safe alternative: read from SharedPreferences fallback
        // For splash, we read directly from the underlying file
        return try {
            kotlinx.coroutines.runBlocking {
                context.dataStore.data.first()[THEME_MODE] ?: "system"
            }
        } catch (e: Exception) {
            "system"
        }
    }

    suspend fun updateSystolicThreshold(value: Int) {
        context.dataStore.edit { it[SYSTOLIC_THRESHOLD] = value }
    }

    suspend fun updateDiastolicThreshold(value: Int) {
        context.dataStore.edit { it[DIASTOLIC_THRESHOLD] = value }
    }

    suspend fun updateAlertInterval(hours: Int) {
        context.dataStore.edit { it[ALERT_INTERVAL_HOURS] = hours }
    }

    suspend fun updateAutoFillTime(value: Boolean) {
        context.dataStore.edit { it[AUTO_FILL_TIME] = value }
    }

    suspend fun updateDateFormat(format: String) {
        context.dataStore.edit { it[DATE_FORMAT] = format }
    }

    suspend fun updateAppsScriptUrl(url: String) {
        context.dataStore.edit { it[APPS_SCRIPT_URL] = url }
    }

    suspend fun updateGoogleAccount(email: String?, spreadsheetId: String?) {
        context.dataStore.edit {
            if (email != null) it[GOOGLE_ACCOUNT_EMAIL] = email else it.remove(GOOGLE_ACCOUNT_EMAIL)
            if (spreadsheetId != null) it[GOOGLE_SPREADSHEET_ID] = spreadsheetId else it.remove(GOOGLE_SPREADSHEET_ID)
        }
    }

    suspend fun updateLastSyncedAt(time: Long) {
        context.dataStore.edit { it[LAST_SYNCED_AT] = time }
    }

    suspend fun updateThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun updateReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[REMINDER_ENABLED] = enabled }
    }

    suspend fun updateReminderTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[REMINDER_HOUR] = hour
            it[REMINDER_MINUTE] = minute
        }
    }

    suspend fun updateSummaryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SUMMARY_ENABLED] = enabled }
    }

    suspend fun updateSummaryFrequency(frequency: String) {
        context.dataStore.edit { it[SUMMARY_FREQUENCY] = frequency }
    }
}