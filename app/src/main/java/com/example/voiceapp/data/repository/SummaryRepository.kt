package com.example.voiceapp.data.repository

import com.example.voiceapp.data.local.dao.SummaryDao
import com.example.voiceapp.data.local.dao.TranscriptDao
import com.example.voiceapp.data.local.entity.Summary
import com.example.voiceapp.data.local.entity.SummaryStatus
import com.example.voiceapp.data.remote.MockSummaryService
import com.example.voiceapp.data.remote.SummaryResponse
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SummaryRepository @Inject constructor(
    private val summaryDao: SummaryDao,
    private val transcriptDao: TranscriptDao,
    private val mockSummaryService: MockSummaryService,
    private val gson: Gson
) {
    suspend fun getOrCreateSummary(meetingId: String): Summary {
        var summary = summaryDao.getSummaryByMeetingId(meetingId)
        if (summary == null) {
            val transcript = transcriptDao.getTranscriptByMeetingId(meetingId)
                ?: throw IllegalStateException("Transcript not found for meeting $meetingId")
            
            summary = Summary(
                id = UUID.randomUUID().toString(),
                meetingId = meetingId,
                transcriptId = transcript.id,
                status = SummaryStatus.GENERATING
            )
            summaryDao.insertSummary(summary)
        }
        return summary
    }
    
    suspend fun generateSummary(meetingId: String): Flow<SummaryResponse> {
        val transcript = transcriptDao.getTranscriptByMeetingId(meetingId)
            ?: throw IllegalStateException("Transcript not found for meeting $meetingId")
        
        val summary = getOrCreateSummary(meetingId)
        
        // Only generate if summary is not already completed
        if (summary.status == SummaryStatus.COMPLETED) {
            android.util.Log.d("SummaryRepository", "Summary already completed for meeting $meetingId")
            // Return empty flow if already completed
            return kotlinx.coroutines.flow.emptyFlow()
        }
        
        // Check if transcript has content
        val transcriptText = transcript.fullText?.takeIf { it.isNotBlank() } 
            ?: "No transcript content available. This is a mock summary."
        
        android.util.Log.d("SummaryRepository", 
            "Generating summary for meeting $meetingId. Transcript length: ${transcriptText.length}")
        
        // Update status to GENERATING
        summaryDao.updateSummary(
            summary.copy(status = SummaryStatus.GENERATING)
        )
        
        return mockSummaryService.generateSummaryStream(transcriptText)
    }
    
    suspend fun updateSummaryFromResponse(meetingId: String, response: SummaryResponse) {
        val summary = summaryDao.getSummaryByMeetingId(meetingId)
            ?: throw IllegalStateException("Summary not found for meeting $meetingId")
        
        val updatedSummary = summary.copy(
            title = response.title ?: summary.title,
            summary = response.summary ?: summary.summary,
            actionItems = response.actionItems?.let { gson.toJson(it) } ?: summary.actionItems,
            keyPoints = response.keyPoints?.let { gson.toJson(it) } ?: summary.keyPoints,
            status = if (response.isComplete) SummaryStatus.COMPLETED else SummaryStatus.GENERATING,
            updatedAt = System.currentTimeMillis()
        )
        
        summaryDao.updateSummary(updatedSummary)
    }
    
    suspend fun getSummaryByMeetingId(meetingId: String): Summary? {
        return summaryDao.getSummaryByMeetingId(meetingId)
    }
    
    fun getSummaryByMeetingIdFlow(meetingId: String): Flow<Summary?> {
        return summaryDao.getSummaryByMeetingIdFlow(meetingId)
    }
    
}

