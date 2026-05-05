
package com.bplogger.app.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import com.bplogger.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 🔄 App Update Manager
 * Checks GitHub for new versions, downloads APK, and triggers install.
 *
 * Location: util/AppUpdateManager.kt
 */
object AppUpdateManager {

    private const val TAG = "AppUpdateManager"

    // ✅ GitHub raw URL for version.json
    private const val VERSION_CHECK_URL =
    "https://raw.githubusercontent.com/mixOsApps/BPLogger/refs/heads/main/version.json"

    private const val PREF_NAME = "AppUpdatePrefs"
    private const val KEY_SKIP_VERSION = "skipVersionCode"
    private const val KEY_LAST_CHECK_TIME = "lastCheckTime"

    // Minimum interval between auto-checks (1 hour)
    private const val CHECK_INTERVAL_MS = 60 * 60 * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .cache(null) // Disable HTTP cache entirely for update checks
        .build()

    /**
     * Data class for version info from GitHub
     */
    data class VersionInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val releaseNotes: String,
        val forceUpdate: Boolean
    )

    /**
     * 🔍 Check for updates (called from MainActivity on open)
     * Respects check interval to avoid excessive network calls.
     */
    suspend fun checkForUpdate(activity: Activity, isManualCheck: Boolean = false) {
        try {
            // Skip auto-check if checked recently (manual checks always proceed)
            if (!isManualCheck && !shouldAutoCheck(activity)) {
                Log.d(TAG, "⏭️ Skipping auto-check (checked recently)")
                return
            }

            Log.d(TAG, "🔍 Checking for app update... (manual=$isManualCheck)")

            val versionInfo = fetchVersionInfo() ?: run {
                Log.w(TAG, "⚠️ Could not fetch version info")
                if (isManualCheck) {
                    withContext(Dispatchers.Main) {
                        showNoConnectionDialog(activity)
                    }
                }
                return
            }

            // Save check time
            saveLastCheckTime(activity)

            val currentVersionCode = getCurrentVersionCode(activity)
            Log.d(TAG, "📊 Current: $currentVersionCode, Remote: ${versionInfo.versionCode}")

            if (versionInfo.versionCode > currentVersionCode) {
                Log.d(TAG, "🆕 New version available: ${versionInfo.versionName}")
                withContext(Dispatchers.Main) {
                    showUpdateDialog(activity, versionInfo)
                }
            } else {
                Log.d(TAG, "✅ App is up to date")
                if (isManualCheck) {
                    withContext(Dispatchers.Main) {
                        showUpToDateDialog(activity)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Update check failed", e)
            if (isManualCheck) {
                withContext(Dispatchers.Main) {
                    showNoConnectionDialog(activity)
                }
            }
        }
    }

    /**
     * 🌐 Fetch version info from GitHub
     */
    private suspend fun fetchVersionInfo(): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$VERSION_CHECK_URL?_=${System.currentTimeMillis()}")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Version check HTTP error: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            VersionInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = json.getString("apkUrl"),
                releaseNotes = json.optString("releaseNotes", ""),
                forceUpdate = json.optBoolean("forceUpdate", false)
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse version info", e)
            null
        }
    }

    /**
     * 📢 Show forced update dialog
     */
    private fun showUpdateDialog(activity: Activity, versionInfo: VersionInfo) {
        val message = activity.getString(
            R.string.update_dialog_message,
            versionInfo.versionName,
            versionInfo.releaseNotes
        )

        val builder = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_dialog_title))
            .setMessage(message)
            .setCancelable(!versionInfo.forceUpdate)
            .setPositiveButton(activity.getString(R.string.update_now), null)

        if (versionInfo.forceUpdate) {
            builder.setNegativeButton(activity.getString(R.string.update_exit_app)) { _, _ ->
                activity.finishAffinity()
            }
        } else {
            builder.setNegativeButton(activity.getString(R.string.update_later), null)
        }

        val dialog = builder.create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                dialog.dismiss()
                downloadAndInstall(activity, versionInfo)
            }
        }

        dialog.show()
    }

    /**
     * ⬇️ Download APK with progress dialog and install
     */
    @SuppressLint("StringFormatMatches")
    private fun downloadAndInstall(activity: Activity, versionInfo: VersionInfo) {
        // Inflate custom progress layout
        val progressView = LayoutInflater.from(activity).inflate(R.layout.dialog_update_progress, null)
        val progressBar = progressView.findViewById<ProgressBar>(R.id.progressBarUpdate)
        val textProgress = progressView.findViewById<TextView>(R.id.textUpdateProgress)
        val textPercentage = progressView.findViewById<TextView>(R.id.textUpdatePercentage)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_downloading_title))
            .setView(progressView)
            .setCancelable(false)
            .create()

        dialog.show()

        // Download in background thread
        Thread {
            try {
                val request = Request.Builder()
                    .url(versionInfo.apkUrl)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    activity.runOnUiThread {
                        dialog.dismiss()
                        showDownloadFailedDialog(activity, versionInfo)
                    }
                    return@Thread
                }

                val body = response.body ?: run {
                    activity.runOnUiThread {
                        dialog.dismiss()
                        showDownloadFailedDialog(activity, versionInfo)
                    }
                    return@Thread
                }

                val contentLength = body.contentLength()
                val inputStream = body.byteStream()

                // Save to app's external files directory
                val apkFile = File(
                    activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    "app-update-${versionInfo.versionName}.apk"
                )

                // Delete old APK files
                cleanOldApks(activity)

                val outputStream = FileOutputStream(apkFile)
                val buffer = ByteArray(8192)
                var totalBytesRead = 0L
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    if (contentLength > 0) {
                        val progress = ((totalBytesRead * 100) / contentLength).toInt()
                        val downloadedMB = totalBytesRead / (1024.0 * 1024.0)
                        val totalMB = contentLength / (1024.0 * 1024.0)

                        activity.runOnUiThread {
                            progressBar.progress = progress
                            textPercentage.text = "$progress%"
                            textProgress.text = activity.getString(
                                R.string.update_download_progress,
                                downloadedMB,
                                totalMB
                            )
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                Log.d(TAG, "✅ APK downloaded: ${apkFile.absolutePath}")

                activity.runOnUiThread {
                    dialog.dismiss()
                    installApk(activity, apkFile)
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Download failed", e)
                activity.runOnUiThread {
                    dialog.dismiss()
                    showDownloadFailedDialog(activity, versionInfo)
                }
            }
        }.start()
    }

    /**
     * 📦 Trigger APK installation
     */
    private fun installApk(activity: Activity, apkFile: File) {
        try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.provider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            activity.startActivity(intent)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to install APK", e)

            // Fallback: Show dialog with file path
            AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.update_install_failed_title))
                .setMessage(activity.getString(R.string.update_install_failed_message, apkFile.absolutePath))
                .setPositiveButton(activity.getString(R.string.ok), null)
                .show()
        }
    }

    /**
     * 🧹 Clean old downloaded APK files
     */
    private fun cleanOldApks(activity: Activity) {
        try {
            val downloadDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            downloadDir?.listFiles()?.forEach { file ->
                if (file.name.startsWith("app-update-") && file.name.endsWith(".apk")) {
                    file.delete()
                    Log.d(TAG, "🧹 Deleted old APK: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to clean old APKs", e)
        }
    }

    /**
     * ✅ Show "up to date" dialog (manual check only)
     */
    private fun showUpToDateDialog(activity: Activity) {
        val currentVersionName = try {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        } catch (e: Exception) {
            "?"
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_up_to_date_title))
            .setMessage(activity.getString(R.string.update_up_to_date_message, currentVersionName))
            .setPositiveButton(activity.getString(R.string.ok), null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(getAccentColor(activity))
    }

    /**
     * 🔴 Show download failed dialog with retry option
     */
    private fun showDownloadFailedDialog(activity: Activity, versionInfo: VersionInfo) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_download_failed_title))
            .setMessage(activity.getString(R.string.update_download_failed_message))
            .setCancelable(false)
            .setPositiveButton(activity.getString(R.string.update_retry), null)
            .setNegativeButton(activity.getString(R.string.update_exit_app)) { _, _ ->
                activity.finishAffinity()
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
                downloadAndInstall(activity, versionInfo)
            }
        }

        dialog.show()
    }

    /**
     * ⚠️ Show no connection dialog
     */
    private fun showNoConnectionDialog(activity: Activity) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_check_failed_title))
            .setMessage(activity.getString(R.string.update_check_failed_message))
            .setPositiveButton(activity.getString(R.string.ok), null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(getAccentColor(activity))
    }

    /**
     * 📊 Get current app version code
     */
    private fun getCurrentVersionCode(context: Context): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get version code", e)
            0
        }
    }

    /**
     * ⏱️ Check if enough time has passed since last auto-check
     */
    private fun shouldAutoCheck(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0)
        return System.currentTimeMillis() - lastCheck > CHECK_INTERVAL_MS
    }

    /**
     * 💾 Save last check time
     */
    private fun saveLastCheckTime(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
            .apply()
    }

    /**
     * 📄 Get current version name for display
     */
    fun getCurrentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }

    /**
     * 🎨 Get theme-aware accent color (Red for light, Yellow for dark)
     */
    private fun getAccentColor(activity: Activity): Int {
        val nightModeFlags = activity.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            android.graphics.Color.parseColor("#FBC02D") // BPYellow
        } else {
            android.graphics.Color.parseColor("#D32F2F") // BPRed
        }
    }

}
