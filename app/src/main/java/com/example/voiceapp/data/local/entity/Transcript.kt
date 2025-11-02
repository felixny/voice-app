package com.example.voiceapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcripts")
data class Transcript(
    @PrimaryKey
    val id: String,
    val meetingId: String,
    val fullText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isComplete: Boolean = false
)

