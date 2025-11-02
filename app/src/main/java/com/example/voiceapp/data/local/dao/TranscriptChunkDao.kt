package com.example.voiceapp.data.local.dao

import androidx.room.*
import com.example.voiceapp.data.local.entity.TranscriptChunk
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptChunkDao {
    @Query("SELECT * FROM transcript_chunks WHERE meetingId = :meetingId ORDER BY chunkIndex ASC")
    fun getChunksByMeetingId(meetingId: String): Flow<List<TranscriptChunk>>
    
    @Query("SELECT text FROM transcript_chunks WHERE meetingId = :meetingId ORDER BY chunkIndex ASC")
    suspend fun getTranscriptTextByMeetingId(meetingId: String): List<String>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: TranscriptChunk)
    
    @Query("DELETE FROM transcript_chunks WHERE meetingId = :meetingId")
    suspend fun deleteChunksByMeetingId(meetingId: String)
}

