package com.example.voiceapp.di

import android.content.Context
import androidx.room.Room
import com.example.voiceapp.data.local.VoiceAppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VoiceAppDatabase {
        return Room.databaseBuilder(
            context,
            VoiceAppDatabase::class.java,
            "voice_app_database"
        ).build()
    }
    
    @Provides
    fun provideMeetingDao(database: VoiceAppDatabase) = database.meetingDao()
    
    @Provides
    fun provideAudioChunkDao(database: VoiceAppDatabase) = database.audioChunkDao()
    
    @Provides
    fun provideTranscriptDao(database: VoiceAppDatabase) = database.transcriptDao()
    
    @Provides
    fun provideTranscriptChunkDao(database: VoiceAppDatabase) = database.transcriptChunkDao()
    
    @Provides
    fun provideSummaryDao(database: VoiceAppDatabase) = database.summaryDao()
    
    @Provides
    fun provideRecordingSessionDao(database: VoiceAppDatabase) = database.recordingSessionDao()
}

