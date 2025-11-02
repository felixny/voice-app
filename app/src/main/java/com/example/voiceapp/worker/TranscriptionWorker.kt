package com.example.voiceapp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.voiceapp.data.local.entity.MeetingStatus
import com.example.voiceapp.data.repository.MeetingRepository
import com.example.voiceapp.data.repository.TranscriptionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class TranscriptionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val transcriptionRepository: TranscriptionRepository,
    private val meetingRepository: MeetingRepository
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val meetingId = inputData.getString(KEY_MEETING_ID)
                ?: return@withContext Result.failure()
            
            val pendingChunks = transcriptionRepository.getPendingChunks(meetingId)
            
            if (pendingChunks.isEmpty()) {
                // No chunks to transcribe, but still update transcript if it exists
                val transcript = transcriptionRepository.getOrCreateTranscript(meetingId)
                val meeting = meetingRepository.getMeetingById(meetingId)
                meeting?.let {
                    meetingRepository.updateMeeting(
                        it.copy(
                            transcriptId = transcript.id,
                            status = MeetingStatus.COMPLETED
                        )
                    )
                }
                return@withContext Result.success()
            }
            
            // Transcribe all pending chunks
            for (chunk in pendingChunks) {
                val result = transcriptionRepository.transcribeChunk(chunk)
                
                if (result.isFailure) {
                    // Retry this chunk later
                    return@withContext Result.retry()
                }
            }
            
            // Update transcript text
            transcriptionRepository.updateTranscriptText(meetingId)
            
            // Update meeting with transcriptId
            val transcript = transcriptionRepository.getOrCreateTranscript(meetingId)
            val meeting = meetingRepository.getMeetingById(meetingId)
            meeting?.let {
                meetingRepository.updateMeeting(
                    it.copy(
                        transcriptId = transcript.id,
                        status = MeetingStatus.COMPLETED
                    )
                )
            }
            
            // Trigger summary generation
            val summaryWork = SummaryWorker.createWorkRequest(meetingId)
            WorkManager.getInstance(applicationContext).enqueue(summaryWork)
            
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("TranscriptionWorker", "Error in transcription", e)
            Result.retry()
        }
    }
    
    companion object {
        const val KEY_MEETING_ID = "meeting_id"
        
        fun createWorkRequest(meetingId: String): OneTimeWorkRequest {
            val inputData = Data.Builder()
                .putString(KEY_MEETING_ID, meetingId)
                .build()
            
            return OneTimeWorkRequestBuilder<TranscriptionWorker>()
                .setInputData(inputData)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        }
    }
}

