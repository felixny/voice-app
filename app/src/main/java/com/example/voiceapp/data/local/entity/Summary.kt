package com.example.voiceapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "summaries")
data class Summary(
    @PrimaryKey
    val id: String,
    val meetingId: String,
    val transcriptId: String,
    val title: String = "",
    val summary: String = "",
    val actionItems: String = "", // JSON or comma-separated
    val keyPoints: String = "", // JSON or comma-separated
    val status: SummaryStatus = SummaryStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class SummaryStatus {
    PENDING,
    GENERATING,
    COMPLETED,
    FAILED
}

