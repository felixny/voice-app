package com.example.voiceapp.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming

interface SummaryApi {
    @POST("summarize")
    @Streaming
    suspend fun generateSummary(
        @Body request: SummaryRequest
    ): Response<SummaryResponse>
}

data class SummaryRequest(
    val transcript: String,
    val meetingId: String
)

data class SummaryResponse(
    val title: String? = null,
    val summary: String? = null,
    val actionItems: List<String>? = null,
    val keyPoints: List<String>? = null,
    val isComplete: Boolean = false
)

