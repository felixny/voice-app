package com.example.voiceapp.data.local.dao

import androidx.room.*
import com.example.voiceapp.data.local.entity.RecordingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingSessionDao {
    @Query("SELECT * FROM recording_sessions WHERE meetingId = :meetingId")
    suspend fun getSession(meetingId: String): RecordingSession?
    
    @Query("SELECT * FROM recording_sessions WHERE meetingId = :meetingId")
    fun getSessionFlow(meetingId: String): Flow<RecordingSession?>
    
    @Query("SELECT * FROM recording_sessions WHERE isRecording = 1 LIMIT 1")
    suspend fun getActiveSession(): RecordingSession?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RecordingSession)
    
    @Update
    suspend fun updateSession(session: RecordingSession)
    
    @Query("DELETE FROM recording_sessions WHERE meetingId = :meetingId")
    suspend fun deleteSession(meetingId: String)
}

