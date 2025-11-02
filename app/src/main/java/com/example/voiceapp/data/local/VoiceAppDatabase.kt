package com.example.voiceapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.voiceapp.data.local.dao.*
import com.example.voiceapp.data.local.entity.*

@Database(
    entities = [
        Meeting::class,
        AudioChunk::class,
        Transcript::class,
        TranscriptChunk::class,
        Summary::class,
        RecordingSession::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VoiceAppDatabase : RoomDatabase() {
    abstract fun meetingDao(): MeetingDao
    abstract fun audioChunkDao(): AudioChunkDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun transcriptChunkDao(): TranscriptChunkDao
    abstract fun summaryDao(): SummaryDao
    abstract fun recordingSessionDao(): RecordingSessionDao
}

