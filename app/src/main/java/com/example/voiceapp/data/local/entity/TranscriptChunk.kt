package com.example.voiceapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcript_chunks")
data class TranscriptChunk(
    @PrimaryKey
    val id: String,
    val audioChunkId: String,
    val meetingId: String,
    val text: String,
    val chunkIndex: Int,
    val createdAt: Long = System.currentTimeMillis()
)

