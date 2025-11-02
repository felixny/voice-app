package com.example.voiceapp.data.repository

import com.example.voiceapp.data.local.dao.MeetingDao
import com.example.voiceapp.data.local.entity.Meeting
import com.example.voiceapp.data.local.entity.MeetingStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeetingRepository @Inject constructor(
    private val meetingDao: MeetingDao
) {
    fun getAllMeetings(): Flow<List<Meeting>> = meetingDao.getAllMeetings()
    
    suspend fun getMeetingById(meetingId: String): Meeting? = meetingDao.getMeetingById(meetingId)
    
    fun getMeetingByIdFlow(meetingId: String): Flow<Meeting?> = meetingDao.getMeetingByIdFlow(meetingId)
    
    suspend fun createMeeting(): Meeting {
        val meeting = Meeting(
            id = UUID.randomUUID().toString(),
            status = MeetingStatus.RECORDING
        )
        meetingDao.insertMeeting(meeting)
        return meeting
    }
    
    suspend fun updateMeeting(meeting: Meeting) {
        meetingDao.updateMeeting(meeting)
    }
    
    suspend fun deleteMeeting(meeting: Meeting) {
        meetingDao.deleteMeeting(meeting)
    }
}

