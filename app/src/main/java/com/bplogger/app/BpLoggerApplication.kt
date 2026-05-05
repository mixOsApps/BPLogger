package com.bplogger.app

import android.app.Application
import androidx.room.Room
import androidx.work.Configuration
import com.bplogger.app.data.db.BpDatabase
import com.bplogger.app.data.db.MIGRATION_1_2
import com.bplogger.app.data.db.MIGRATION_2_3
import com.bplogger.app.data.repository.BpRepository
import com.bplogger.app.data.repository.MedicationRepository
import com.bplogger.app.data.repository.SettingsRepository
import com.bplogger.app.worker.BpReminderWorker
import com.bplogger.app.worker.BpSummaryWorker

class BpLoggerApplication : Application(), Configuration.Provider {

    lateinit var bpRepository: BpRepository
    lateinit var medicationRepository: MedicationRepository
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()

        val database = Room.databaseBuilder(
            applicationContext,
            BpDatabase::class.java, "bp-database"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        bpRepository = BpRepository(database.bpDao())
        medicationRepository = MedicationRepository(database.medicationDao())
        settingsRepository = SettingsRepository(applicationContext)

        // Create notification channels early so the system knows about them
        BpReminderWorker.createNotificationChannel(this)
        BpSummaryWorker.createNotificationChannel(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}