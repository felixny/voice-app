package com.example.voiceapp.ui.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voiceapp.data.repository.MeetingRepository
import com.example.voiceapp.service.RecordingService
import com.example.voiceapp.service.RecordingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    application: Application,
    private val meetingRepository: MeetingRepository
) : AndroidViewModel(application) {
    
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()
    
    private val _currentMeetingId = MutableStateFlow<String?>(null)
    val currentMeetingId: StateFlow<String?> = _currentMeetingId.asStateFlow()
    
    private var isReceiverRegistered = false
    
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                RecordingService.ACTION_RECORDING_STATE_CHANGED -> {
                    val state = intent.getStringExtra(RecordingService.EXTRA_STATE) ?: "idle"
                    val meetingId = intent.getStringExtra(RecordingService.EXTRA_MEETING_ID)
                    val elapsedTime = intent.getLongExtra(RecordingService.EXTRA_ELAPSED_TIME, 0L)
                    
                    android.util.Log.d("RecordingViewModel", "Received state: $state, elapsedTime: $elapsedTime")
                    
                    meetingId?.let { _currentMeetingId.value = it }
                    
                    _recordingState.value = when (state) {
                        "recording" -> RecordingState.Recording(elapsedTime)
                        "paused" -> {
                            val reason = intent.getStringExtra(RecordingService.EXTRA_PAUSE_REASON) ?: "Unknown"
                            RecordingState.Paused(reason, elapsedTime)
                        }
                        "stopped" -> RecordingState.Stopped
                        "error" -> {
                            val message = intent.getStringExtra(RecordingService.EXTRA_ERROR_MESSAGE) ?: "Error"
                            RecordingState.Error(message)
                        }
                        "warning" -> {
                            val message = intent.getStringExtra(RecordingService.EXTRA_WARNING_MESSAGE) ?: "Warning"
                            RecordingState.Warning(message)
                        }
                        else -> RecordingState.Idle
                    }
                }
            }
        }
    }
    
    init {
        observeRecordingService()
    }
    
    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(stateReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }
    
    fun startRecording() {
        viewModelScope.launch {
            val meeting = meetingRepository.createMeeting()
            _currentMeetingId.value = meeting.id
            
            val intent = Intent(getApplication(), RecordingService::class.java).apply {
                action = RecordingService.ACTION_START_RECORDING
                putExtra(RecordingService.EXTRA_MEETING_ID, meeting.id)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplication<Application>().startForegroundService(intent)
            } else {
                getApplication<Application>().startService(intent)
            }
        }
    }
    
    fun stopRecording() {
        val intent = Intent(getApplication(), RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP_RECORDING
        }
        getApplication<Application>().startForegroundService(intent)
    }
    
    fun pauseRecording() {
        val intent = Intent(getApplication(), RecordingService::class.java).apply {
            action = RecordingService.ACTION_PAUSE_RECORDING
        }
        getApplication<Application>().startForegroundService(intent)
    }
    
    fun resumeRecording() {
        val intent = Intent(getApplication(), RecordingService::class.java).apply {
            action = RecordingService.ACTION_RESUME_RECORDING
        }
        getApplication<Application>().startForegroundService(intent)
    }
    
    private fun observeRecordingService() {
        if (isReceiverRegistered) return
        
        val filter = IntentFilter().apply {
            addAction(RecordingService.ACTION_RECORDING_STATE_CHANGED)
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getApplication<Application>().registerReceiver(
                    stateReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                getApplication<Application>().registerReceiver(stateReceiver, filter)
            }
            isReceiverRegistered = true
        } catch (e: Exception) {
            // Receiver might already be registered
            android.util.Log.e("RecordingViewModel", "Error registering receiver", e)
        }
    }
}

