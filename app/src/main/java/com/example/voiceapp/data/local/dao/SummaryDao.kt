package com.example.voiceapp.data.local.dao

import androidx.room.*
import com.example.voiceapp.data.local.entity.Summary
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {
    @Query("SELECT * FROM summaries WHERE meetingId = :meetingId")
    suspend fun getSummaryByMeetingId(meetingId: String): Summary?
    
    @Query("SELECT * FROM summaries WHERE meetingId = :meetingId")
    fun getSummaryByMeetingIdFlow(meetingId: String): Flow<Summary?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: Summary)
    
    @Update
    suspend fun updateSummary(summary: Summary)
}

