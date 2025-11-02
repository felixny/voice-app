package com.example.voiceapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "meetings")
data class Meeting(
    @PrimaryKey
    val id: String,
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val duration: Long = 0L,
    val status: MeetingStatus = MeetingStatus.RECORDING,
    val transcriptId: String? = null,
    val summaryId: String? = null
)

enum class MeetingStatus {
    RECORDING,
    PAUSED,
    STOPPED,
    TRANSCRIBING,
    COMPLETED
}

