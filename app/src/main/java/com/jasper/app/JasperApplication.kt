package com.jasper.app

import android.app.Application
import com.jasper.app.data.db.FaceDatabase
import com.jasper.app.data.repository.FaceRepository
import com.jasper.app.data.settings.SettingsRepository

class JasperApplication : Application() {

    val database: FaceDatabase by lazy {
        FaceDatabase.getInstance(applicationContext)
    }

    val repository: FaceRepository by lazy {
        FaceRepository(applicationContext, database)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(applicationContext)
    }

    override fun onTerminate() {
        super.onTerminate()
        // Full shutdown only when the process actually ends
        repository.closeAllResources()
    }
}
