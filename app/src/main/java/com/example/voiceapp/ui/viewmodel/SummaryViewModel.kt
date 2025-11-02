package com.example.voiceapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.example.voiceapp.data.local.entity.Summary
import com.example.voiceapp.data.repository.SummaryRepository
import com.example.voiceapp.data.repository.TranscriptionRepository
import com.example.voiceapp.worker.SummaryWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val summaryRepository: SummaryRepository,
    private val transcriptionRepository: TranscriptionRepository,
    private val workManager: WorkManager
) : ViewModel() {
    
    fun getSummary(meetingId: String): StateFlow<Summary?> {
        return summaryRepository.getSummaryByMeetingIdFlow(meetingId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )
    }
    
    
    fun generateSummary(meetingId: String) {
        viewModelScope.launch {
            try {
                // First ensure transcription is complete
                transcriptionRepository.updateTranscriptText(meetingId)
                
                // Trigger summary generation via WorkManager
                val workRequest = SummaryWorker.createWorkRequest(meetingId)
                workManager.enqueue(workRequest)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun parseActionItems(actionItemsJson: String): List<String> {
        return try {
            if (actionItemsJson.isEmpty()) return emptyList()
            // Simple parsing - assuming comma-separated or JSON array
            if (actionItemsJson.startsWith("[")) {
                // JSON array - would need Gson or similar
                actionItemsJson.removePrefix("[").removeSuffix("]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
            } else {
                actionItemsJson.split(",").map { it.trim() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun parseKeyPoints(keyPointsJson: String): List<String> {
        return parseActionItems(keyPointsJson) // Same parsing logic
    }
}

