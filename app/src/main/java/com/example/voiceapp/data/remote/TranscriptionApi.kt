package com.example.voiceapp.data.remote

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface TranscriptionApi {
    @Multipart
    @POST("transcribe")
    suspend fun transcribeAudio(
        @Part audioFile: MultipartBody.Part
    ): Response<TranscriptionResponse>
}

data class TranscriptionResponse(
    val text: String,
    val language: String? = null
)

