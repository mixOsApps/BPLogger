package com.bplogger.app

import android.content.Context
import androidx.room.Room
import com.bplogger.app.data.db.BpDatabase
import com.bplogger.app.data.repository.BpRepository

class AppContainer(context: Context) {

    private val database: BpDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            BpDatabase::class.java,
            "bp-logger-db"
        ).build()
    }

    val bpRepository: BpRepository by lazy {
        BpRepository(database.bpDao())
    }
}
