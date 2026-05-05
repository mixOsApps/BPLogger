package com.bplogger.app.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.bplogger.app.BpLoggerApplication
import com.bplogger.app.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncBackgroundWorker(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "SyncChannel"
    }

    override suspend fun doWork(): Result {
        val app = context.applicationContext as BpLoggerApplication
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return Result.failure()
        
        val syncStatusManager = SyncStatusManager(
            app.bpRepository, 
            app.settingsRepository, 
            GoogleSheetsManager(context)
        )

        val notification = createNotification("Preparing to sync...")
        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
        setForeground(foregroundInfo)

        if (!isNetworkAvailable()) {
            return Result.retry()
        }

        if (syncStatusManager.isSyncInProgress()) {
            return Result.success()
        }

        return withContext(Dispatchers.IO) {
            try {
                syncStatusManager.startInlineSync(account) { message ->
                    val progressNotification = createNotification(message)
                    notificationManager.notify(NOTIFICATION_ID, progressNotification)
                }
                Result.success()
            } catch (e: Exception) {
                val errorNotification = createNotification("Sync failed. Will retry later.")
                notificationManager.notify(NOTIFICATION_ID, errorNotification)
                Result.retry()
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sync",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle("Syncing Data")
        .setContentText(contentText)
        .setSmallIcon(R.drawable.ic_sync)
        .setOngoing(true)
        .build()
}