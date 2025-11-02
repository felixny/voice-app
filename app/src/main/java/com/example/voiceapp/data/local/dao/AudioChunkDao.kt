package com.example.voiceapp.data.local.dao

import androidx.room.*
import com.example.voiceapp.data.local.entity.AudioChunk
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioChunkDao {
    @Query("SELECT * FROM audio_chunks WHERE meetingId = :meetingId ORDER BY chunkIndex ASC")
    fun getChunksByMeetingId(meetingId: String): Flow<List<AudioChunk>>
    
    @Query("SELECT * FROM audio_chunks WHERE meetingId = :meetingId AND isTranscribed = 0 ORDER BY chunkIndex ASC")
    suspend fun getPendingChunks(meetingId: String): List<AudioChunk>
    
    @Query("SELECT * FROM audio_chunks WHERE id = :chunkId")
    suspend fun getChunkById(chunkId: String): AudioChunk?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: AudioChunk)
    
    @Update
    suspend fun updateChunk(chunk: AudioChunk)
    
    @Query("DELETE FROM audio_chunks WHERE meetingId = :meetingId")
    suspend fun deleteChunksByMeetingId(meetingId: String)
}

