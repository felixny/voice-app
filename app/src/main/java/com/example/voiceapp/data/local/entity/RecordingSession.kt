package com.example.voiceapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recording_sessions")
data class RecordingSession(
    @PrimaryKey
    val meetingId: String,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val pauseReason: String? = null,
    val startTime: Long = 0L,
    val elapsedTime: Long = 0L,
    val audioFocusLost: Boolean = false,
    val phoneCallActive: Boolean = false,
    val lastChunkIndex: Int = -1,
    val currentChunkPath: String? = null
)

