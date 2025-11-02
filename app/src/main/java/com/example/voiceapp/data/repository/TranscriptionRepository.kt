package com.example.voiceapp.data.repository

import com.example.voiceapp.data.local.dao.AudioChunkDao
import com.example.voiceapp.data.local.dao.TranscriptChunkDao
import com.example.voiceapp.data.local.dao.TranscriptDao
import com.example.voiceapp.data.local.entity.AudioChunk
import com.example.voiceapp.data.local.entity.Transcript
import com.example.voiceapp.data.local.entity.TranscriptChunk
import com.example.voiceapp.data.local.entity.TranscriptionStatus
import com.example.voiceapp.data.remote.MockTranscriptionService
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionRepository @Inject constructor(
    private val audioChunkDao: AudioChunkDao,
    private val transcriptDao: TranscriptDao,
    private val transcriptChunkDao: TranscriptChunkDao,
    private val mockTranscriptionService: MockTranscriptionService
) {
    suspend fun getPendingChunks(meetingId: String): List<AudioChunk> {
        return audioChunkDao.getPendingChunks(meetingId)
    }
    
    suspend fun transcribeChunk(chunk: AudioChunk): Result<String> {
        try {
            // Update status to TRANSCRIBING
            audioChunkDao.updateChunk(
                chunk.copy(transcriptionStatus = TranscriptionStatus.TRANSCRIBING)
            )
            
            val file = File(chunk.filePath)
            if (!file.exists()) {
                return Result.failure(Exception("Audio file not found: ${chunk.filePath}"))
            }
            
            // Call transcription service
            val result = mockTranscriptionService.transcribeAudio(file)
            
            result.onSuccess { transcriptText ->
                // Save transcript chunk
                val transcriptChunk = TranscriptChunk(
                    id = UUID.randomUUID().toString(),
                    audioChunkId = chunk.id,
                    meetingId = chunk.meetingId,
                    text = transcriptText,
                    chunkIndex = chunk.chunkIndex
                )
                transcriptChunkDao.insertChunk(transcriptChunk)
                
                // Update chunk as transcribed
                audioChunkDao.updateChunk(
                    chunk.copy(
                        isTranscribed = true,
                        transcriptionStatus = TranscriptionStatus.COMPLETED
                    )
                )
            }.onFailure {
                audioChunkDao.updateChunk(
                    chunk.copy(transcriptionStatus = TranscriptionStatus.FAILED)
                )
            }
            
            return result
        } catch (e: Exception) {
            audioChunkDao.updateChunk(
                chunk.copy(transcriptionStatus = TranscriptionStatus.FAILED)
            )
            return Result.failure(e)
        }
    }
    
    suspend fun getOrCreateTranscript(meetingId: String): Transcript {
        var transcript = transcriptDao.getTranscriptByMeetingId(meetingId)
        if (transcript == null) {
            transcript = Transcript(
                id = UUID.randomUUID().toString(),
                meetingId = meetingId
            )
            transcriptDao.insertTranscript(transcript)
        }
        return transcript
    }
    
    suspend fun updateTranscriptText(meetingId: String) {
        val chunks = transcriptChunkDao.getTranscriptTextByMeetingId(meetingId)
        val fullText = chunks.joinToString(" ")
        
        val transcript = getOrCreateTranscript(meetingId)
        transcriptDao.updateTranscript(
            transcript.copy(
                fullText = fullText,
                isComplete = true
            )
        )
    }
    
    fun getTranscriptByMeetingIdFlow(meetingId: String): Flow<Transcript?> {
        return transcriptDao.getTranscriptByMeetingIdFlow(meetingId)
    }
}

