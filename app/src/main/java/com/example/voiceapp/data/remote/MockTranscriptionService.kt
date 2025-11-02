package com.example.voiceapp.data.remote

import kotlinx.coroutines.delay
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockTranscriptionService @Inject constructor() {
    suspend fun transcribeAudio(file: File): Result<String> {
        // Simulate API call delay
        delay(1000)
        
        // Verify file exists and has content
        if (!file.exists()) {
            return Result.failure(Exception("Audio file does not exist: ${file.absolutePath}"))
        }
        
        val fileSize = file.length()
        if (fileSize == 0L) {
            return Result.failure(Exception("Audio file is empty: ${file.absolutePath}"))
        }
        
        // Mock transcription - in real app, this would:
        // 1. Upload audio file to OpenAI Whisper API or Google Speech-to-Text
        // 2. Get back the actual transcribed text
        // 3. Return the real transcription
        
        // For now, generate mock text based on file size and name to make it seem more realistic
        val fileSizeKB = fileSize / 1024
        val mockTranscription = when {
            fileSizeKB < 100 -> "Brief recording session. Quick notes and discussion."
            fileSizeKB < 500 -> "Meeting discussion covering multiple topics. Participants shared updates and ideas."
            else -> "Extended meeting recording. Detailed discussion with multiple speakers covering various agenda items, decisions, and action items."
        }
        
        android.util.Log.d("MockTranscriptionService", 
            "Transcribing file: ${file.name}, size: ${fileSizeKB}KB. " +
            "In production, this would call OpenAI Whisper or Google Speech-to-Text API."
        )
        
        return Result.success(mockTranscription)
    }
}

