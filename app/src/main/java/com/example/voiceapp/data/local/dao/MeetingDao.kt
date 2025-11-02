package com.example.voiceapp.data.local.dao

import androidx.room.*
import com.example.voiceapp.data.local.entity.Meeting
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Query("SELECT * FROM meetings ORDER BY createdAt DESC")
    fun getAllMeetings(): Flow<List<Meeting>>
    
    @Query("SELECT * FROM meetings WHERE id = :meetingId")
    suspend fun getMeetingById(meetingId: String): Meeting?
    
    @Query("SELECT * FROM meetings WHERE id = :meetingId")
    fun getMeetingByIdFlow(meetingId: String): Flow<Meeting?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeeting(meeting: Meeting)
    
    @Update
    suspend fun updateMeeting(meeting: Meeting)
    
    @Delete
    suspend fun deleteMeeting(meeting: Meeting)
}

