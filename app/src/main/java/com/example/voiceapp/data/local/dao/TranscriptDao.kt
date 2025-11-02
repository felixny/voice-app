package com.example.voiceapp.data.local.dao

import androidx.room.*
import com.example.voiceapp.data.local.entity.Transcript
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcripts WHERE meetingId = :meetingId")
    suspend fun getTranscriptByMeetingId(meetingId: String): Transcript?
    
    @Query("SELECT * FROM transcripts WHERE meetingId = :meetingId")
    fun getTranscriptByMeetingIdFlow(meetingId: String): Flow<Transcript?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscript(transcript: Transcript)
    
    @Update
    suspend fun updateTranscript(transcript: Transcript)
}

