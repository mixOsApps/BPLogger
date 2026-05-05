package com.bplogger.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bp_records ADD COLUMN weight REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE bp_records ADD COLUMN spO2 INTEGER DEFAULT NULL")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS medications (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                dosage TEXT NOT NULL,
                frequency TEXT NOT NULL,
                startDate INTEGER NOT NULL,
                endDate INTEGER,
                notes TEXT NOT NULL DEFAULT '',
                isActive INTEGER NOT NULL DEFAULT 1
            )"""
        )
    }
}

@Database(
    entities = [BpRecord::class, Medication::class],
    version = 3,
    exportSchema = false
)
abstract class BpDatabase : RoomDatabase() {
    abstract fun bpDao(): BpDao
    abstract fun medicationDao(): MedicationDao
}
