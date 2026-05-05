package com.bplogger.app

import android.app.Application
import androidx.work.Configuration
import com.bplogger.app.worker.BpAlertWorker

class BPLoggerApp : Application() {

    lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        BpAlertWorker.schedule(this, intervalHours = 6)
    }
}
