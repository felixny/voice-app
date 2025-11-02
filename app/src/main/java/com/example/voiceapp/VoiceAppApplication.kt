package com.example.voiceapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VoiceAppApplication : Application() {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    override fun onCreate() {
        super.onCreate()
        
        // Only initialize if not already initialized
        try {
            val configuration = Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()
            
            WorkManager.initialize(this, configuration)
        } catch (e: IllegalStateException) {
            // WorkManager already initialized, which is fine
            android.util.Log.w("VoiceAppApplication", "WorkManager already initialized", e)
        }
    }
}

