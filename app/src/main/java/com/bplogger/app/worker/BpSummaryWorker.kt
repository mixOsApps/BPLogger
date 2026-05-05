package com.bplogger.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.bplogger.app.BpLoggerApplication
import com.bplogger.app.data.repository.BpRepository
import com.bplogger.app.data.repository.SettingsRepository
import com.bplogger.app.domain.model.BpClassifier
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit

class BpSummaryWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val bpRepository: BpRepository
    private val settingsRepository: SettingsRepository

    init {
        val app = context.applicationContext as BpLoggerApplication
        bpRepository = app.bpRepository
        settingsRepository = app.settingsRepository
    }

    companion object {
        const val WORK_NAME_WEEKLY = "bp_summary_weekly"
        const val WORK_NAME_MONTHLY = "bp_summary_monthly"
        const val CHANNEL_ID = "bp_summary"
        const val NOTIFICATION_ID = 3001

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BP Summary Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Weekly or monthly blood pressure summary"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        fun schedule(context: Context, frequency: String) {
            cancel(context)

            val intervalDays = if (frequency == "monthly") 30L else 7L

            val request = PeriodicWorkRequestBuilder<BpSummaryWorker>(
                intervalDays, TimeUnit.DAYS
            )
                .setInitialDelay(intervalDays, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            val workName = if (frequency == "monthly") WORK_NAME_MONTHLY else WORK_NAME_WEEKLY

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_WEEKLY)
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_MONTHLY)
        }
    }

    override suspend fun doWork(): Result {
        val settings = settingsRepository.settings.firstOrNull() ?: return Result.success()
        if (!settings.summaryNotificationEnabled) return Result.success()

        val days = if (settings.summaryFrequency == "monthly") 30 else 7
        val periodLabel = if (settings.summaryFrequency == "monthly") "Monthly" else "Weekly"
        val cutoff = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)

        val allRecords = bpRepository.getAllRecordsList()
        val records = allRecords.filter { it.timestamp >= cutoff }

        if (records.isEmpty()) {
            sendNotification(
                "$periodLabel BP Summary",
                "No readings recorded in the past $days days. Don't forget to log your blood pressure!"
            )
            return Result.success()
        }

        val avgSys = records.map { it.systolic }.average().toInt()
        val avgDia = records.map { it.diastolic }.average().toInt()
        val avgHr = records.map { it.heartRate }.average().toInt()
        val highAlerts = records.count { BpClassifier.isHighAlert(it.systolic, it.diastolic) }

        val message = buildString {
            append("Avg BP: $avgSys/$avgDia mmHg | HR: $avgHr bpm")
            append(" | ${records.size} readings")
            if (highAlerts > 0) append(" | $highAlerts high alerts")
        }

        sendNotification("$periodLabel BP Summary", message)
        return Result.success()
    }

    private fun sendNotification(title: String, message: String) {
        createNotificationChannel(applicationContext)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
