package com.example.voiceapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_chunks")
data class AudioChunk(
    @PrimaryKey
    val id: String,
    val meetingId: String,
    val filePath: String,
    val chunkIndex: Int,
    val startTime: Long,
    val duration: Long,
    val isTranscribed: Boolean = false,
    val transcriptionStatus: TranscriptionStatus = TranscriptionStatus.PENDING
)

enum class TranscriptionStatus {
    PENDING,
    UPLOADING,
    TRANSCRIBING,
    COMPLETED,
    FAILED
}

