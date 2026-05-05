package com.bplogger.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.bplogger.app.BpLoggerApplication
import com.bplogger.app.data.repository.BpRepository
import com.bplogger.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit

class BpAlertWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val bpRepository: BpRepository
    private val settingsRepository: SettingsRepository

    init {
        val app = context.applicationContext as BpLoggerApplication
        bpRepository = app.bpRepository
        settingsRepository = app.settingsRepository
    }

    companion object {
        const val WORK_NAME = "bp_alert_check"
        const val CHANNEL_ID = "bp_alerts"
        const val NOTIFICATION_ID = 1001

        fun schedule(context: Context, intervalHours: Int) {
            val request = PeriodicWorkRequestBuilder<BpAlertWorker>(
                intervalHours.toLong(), TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val settings = settingsRepository.settings.firstOrNull() ?: return Result.success()
        val recent = bpRepository.getLatestRecords(5)

        val hasHighBp = recent.any { record ->
            record.systolic >= settings.systolicAlertThreshold ||
                    record.diastolic >= settings.diastolicAlertThreshold
        }

        if (hasHighBp && recent.isNotEmpty()) {
            val latest = recent.first()
            sendNotification(
                systolic = latest.systolic,
                diastolic = latest.diastolic,
                systolicThreshold = settings.systolicAlertThreshold,
                diastolicThreshold = settings.diastolicAlertThreshold
            )
        }

        return Result.success()
    }

    private fun sendNotification(
        systolic: Int,
        diastolic: Int,
        systolicThreshold: Int,
        diastolicThreshold: Int
    ) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Blood Pressure Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts for high blood pressure readings"
        }
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("\u26A0\uFE0F High Blood Pressure Alert")
            .setContentText("Recent reading: $systolic/$diastolic mmHg exceeds your threshold ($systolicThreshold/$diastolicThreshold)")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Your recent blood pressure reading of $systolic/$diastolic mmHg " +
                                "exceeds your alert threshold of $systolicThreshold/$diastolicThreshold mmHg. " +
                                "Please consult your healthcare provider."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}