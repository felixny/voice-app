package com.example.voiceapp.ui.util

import android.Manifest
import android.os.Build

object Permissions {
    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        
        // Always required for recording
        permissions.add(Manifest.permission.RECORD_AUDIO)
        
        // Required for Android 13+ notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Optional: Phone state for phone call detection
        permissions.add(Manifest.permission.READ_PHONE_STATE)
        
        return permissions.toTypedArray()
    }
    
    fun getEssentialPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        permissions.add(Manifest.permission.RECORD_AUDIO)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        return permissions.toTypedArray()
    }
}

