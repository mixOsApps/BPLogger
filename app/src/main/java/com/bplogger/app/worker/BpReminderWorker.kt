package com.bplogger.app.worker

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bplogger.app.MainActivity
import com.bplogger.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.util.Calendar

class BpReminderWorker : BroadcastReceiver() {

    companion object {
        private const val TAG = "BpReminder"
        const val CHANNEL_ID = "bp_daily_reminder"
        const val NOTIFICATION_ID = 2001
        const val REQUEST_CODE = 2002

        /** Create the notification channel (safe to call multiple times). */
        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily BP Reminder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you to log your blood pressure daily"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        fun schedule(context: Context, hour: Int, minute: Int) {
            // Ensure channel exists before scheduling
            createNotificationChannel(context)

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, BpReminderWorker::class.java).apply {
                action = "com.bplogger.app.ACTION_BP_REMINDER"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            Log.d(TAG, "Scheduling alarm for: ${calendar.time}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    // Fallback to inexact alarm
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, BpReminderWorker::class.java).apply {
                action = "com.bplogger.app.ACTION_BP_REMINDER"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Alarm cancelled")
        }

        /** Check if notification permission is granted (Android 13+). */
        fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Pre-Android 13, permission is granted at install
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "onReceive triggered, action: ${intent?.action}")

        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // Re-schedule alarm after device reboot
                Log.d(TAG, "Boot completed — re-scheduling reminder")
                rescheduleIfEnabled(context)
            }
            else -> {
                // Alarm fired — show notification
                showNotification(context)
                rescheduleForNextDay(context)
            }
        }
    }

    private fun rescheduleIfEnabled(context: Context) {
        try {
            val settingsRepo = SettingsRepository(context.applicationContext)
            val settings = runBlocking {
                settingsRepo.settings.firstOrNull()
            }
            if (settings?.reminderEnabled == true) {
                schedule(context, settings.reminderHour, settings.reminderMinute)
                Log.d(TAG, "Re-scheduled for ${settings.reminderHour}:${settings.reminderMinute}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to re-schedule after boot", e)
        }
    }

    private fun rescheduleForNextDay(context: Context) {
        try {
            val settingsRepo = SettingsRepository(context.applicationContext)
            val settings = runBlocking {
                settingsRepo.settings.firstOrNull()
            }
            if (settings?.reminderEnabled == true) {
                schedule(context, settings.reminderHour, settings.reminderMinute)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reschedule for next day", e)
            schedule(context, 8, 0)
        }
    }

    private fun showNotification(context: Context) {
        // Check permission before posting (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Notification permission not granted — skipping notification")
                return
            }
        }

        createNotificationChannel(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.bplogger.app.ACTION_ADD_RECORD"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🩺 Time to Log Your BP")
            .setContentText("Don't forget to record your blood pressure today!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Notification posted successfully")
    }
}