package com.example.voiceapp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.example.voiceapp.data.repository.MeetingRepository
import com.example.voiceapp.data.repository.SummaryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

@HiltWorker
class SummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val summaryRepository: SummaryRepository,
    private val meetingRepository: MeetingRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val meetingId = inputData.getString(KEY_MEETING_ID)
                ?: return@withContext Result.failure()

            // Check if summary already exists and is completed
            val existingSummary = summaryRepository.getSummaryByMeetingId(meetingId)
            if (existingSummary != null && existingSummary.status == com.example.voiceapp.data.local.entity.SummaryStatus.COMPLETED) {
                // Summary already completed, just update meeting with summaryId if needed
                val meeting = meetingRepository.getMeetingById(meetingId)
                meeting?.let {
                    if (it.summaryId != existingSummary.id) {
                        meetingRepository.updateMeeting(
                            it.copy(summaryId = existingSummary.id)
                        )
                    }
                }
                android.util.Log.d("SummaryWorker", "Summary already completed for meeting $meetingId")
                return@withContext Result.success()
            }

            // Generate summary stream
            val summaryFlow = summaryRepository.generateSummary(meetingId)

            // Process streaming response
            var isComplete = false
            var itemCount = 0
            try {
                android.util.Log.d("SummaryWorker", "Starting to collect summary flow for meeting $meetingId")
                summaryFlow.collect { response ->
                    itemCount++
                    android.util.Log.d("SummaryWorker", 
                        "Received summary response #$itemCount, isComplete: ${response.isComplete}")
                    
                    summaryRepository.updateSummaryFromResponse(meetingId, response)

                    if (response.isComplete) {
                        isComplete = true
                        android.util.Log.d("SummaryWorker", "Summary marked as complete")
                    }
                }
                android.util.Log.d("SummaryWorker", "Flow collection finished. isComplete: $isComplete, items: $itemCount")
            } catch (e: Exception) {
                android.util.Log.e("SummaryWorker", "Error collecting summary flow", e)
                throw e
            }
            
            // Handle empty flow case (when summary was already completed)
            if (itemCount == 0) {
                android.util.Log.d("SummaryWorker", "Received empty flow - summary may already be completed")
                val existingSummary = summaryRepository.getSummaryByMeetingId(meetingId)
                if (existingSummary?.status == com.example.voiceapp.data.local.entity.SummaryStatus.COMPLETED) {
                    // Summary is already completed, update meeting and return success
                    existingSummary.let {
                        val meeting = meetingRepository.getMeetingById(meetingId)
                        meeting?.let { m ->
                            if (m.summaryId != it.id) {
                                meetingRepository.updateMeeting(
                                    m.copy(summaryId = it.id)
                                )
                            }
                        }
                    }
                    return@withContext Result.success()
                }
            }

            if (isComplete) {
                // Update meeting with summaryId
                val summary = summaryRepository.getSummaryByMeetingId(meetingId)
                summary?.let {
                    val meeting = meetingRepository.getMeetingById(meetingId)
                    meeting?.let { m ->
                        meetingRepository.updateMeeting(
                            m.copy(summaryId = summary.id)
                        )
                    }
                }
                
                android.util.Log.d("SummaryWorker", "Summary generation completed for meeting $meetingId")
                Result.success()
            } else {
                android.util.Log.w("SummaryWorker", "Summary generation not completed for meeting $meetingId")
                Result.retry()
            }
        } catch (e: Exception) {
            android.util.Log.e("SummaryWorker", "Error in summary generation", e)
            // Only retry if it's a transient error, otherwise fail
            if (e is IllegalStateException) {
                // Permanent failure (e.g., transcript not found)
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        const val KEY_MEETING_ID = "meeting_id"

        fun createWorkRequest(meetingId: String): OneTimeWorkRequest {
            val inputData = Data.Builder()
                .putString(KEY_MEETING_ID, meetingId)
                .build()

            return OneTimeWorkRequestBuilder<SummaryWorker>()
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

