package com.example.voiceapp.service

import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.example.voiceapp.MainActivity
import com.example.voiceapp.data.local.dao.AudioChunkDao
import com.example.voiceapp.data.local.dao.RecordingSessionDao
import com.example.voiceapp.data.local.entity.AudioChunk
import com.example.voiceapp.data.local.entity.MeetingStatus
import com.example.voiceapp.data.local.entity.RecordingSession
import com.example.voiceapp.data.repository.MeetingRepository
import com.example.voiceapp.worker.TranscriptionWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : Service() {
    
    @Inject lateinit var meetingRepository: MeetingRepository
    @Inject lateinit var audioChunkDao: AudioChunkDao
    @Inject lateinit var recordingSessionDao: RecordingSessionDao
    @Inject lateinit var workManager: WorkManager
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val audioManager: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    
    private var mediaRecorder: MediaRecorder? = null
    private var currentMeetingId: String? = null
    private var recordingSession: RecordingSession? = null
    private var chunkIndex = 0
    private var startTime = 0L
    private var elapsedTime = 0L
    private var timerJob: Job? = null
    private var silenceDetectionJob: Job? = null
    private var lastAudioLevel = 0
    private var silenceStartTime = 0L
    private var isRecording = false
    private var isPaused = false
    private var pauseReason: String? = null
    
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState
    
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                pauseRecording("Audio focus lost")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isPaused && pauseReason == "Audio focus lost") {
                    resumeRecording()
                }
            }
        }
    }
    
    @Suppress("DEPRECATION")
    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK,
                TelephonyManager.CALL_STATE_RINGING -> {
                    pauseRecording("Phone call")
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    if (isPaused && pauseReason == "Phone call") {
                        resumeRecording()
                    }
                }
            }
        }
    }
    
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_STOP_RECORDING -> stopRecording()
                ACTION_RESUME_RECORDING -> resumeRecording()
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    pauseRecording("Audio becoming noisy")
                }
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        // This is a started service, not a bound service
        return null
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        val filter = IntentFilter().apply {
            addAction(ACTION_STOP_RECORDING)
            addAction(ACTION_RESUME_RECORDING)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
        
        // Register phone state listener if permission is granted
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+ uses TelephonyCallback
                    try {
                        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                            override fun onCallStateChanged(state: Int) {
                                when (state) {
                                    TelephonyManager.CALL_STATE_OFFHOOK,
                                    TelephonyManager.CALL_STATE_RINGING -> {
                                        pauseRecording("Phone call")
                                    }
                                    TelephonyManager.CALL_STATE_IDLE -> {
                                        if (isPaused && pauseReason == "Phone call") {
                                            resumeRecording()
                                        }
                                    }
                                }
                            }
                        }
                        telephonyManager.registerTelephonyCallback(mainExecutor, callback)
                    } catch (e: Exception) {
                        // Fallback to deprecated API if new API fails
                        @Suppress("DEPRECATION")
                        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
                    }
                } else {
                    // Older versions use PhoneStateListener
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
                }
            } catch (e: Exception) {
                // Permission might have been revoked or not available
                // Continue without phone call detection
            }
        }
        
        // Recover from process death
        serviceScope.launch {
            recoverFromProcessDeath()
        }
        
        // Register audio device callback for microphone source changes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        }
    }
    
    private val audioDeviceCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (!isRecording) return
                
                for (device in addedDevices) {
                    val deviceType = device.type
                    val isMicrophoneDevice = when (deviceType) {
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_USB_HEADSET -> true
                        else -> false
                    }
                    
                    if (isMicrophoneDevice) {
                        val deviceName = when (deviceType) {
                            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth headset"
                            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
                            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
                            else -> "Audio device"
                        }
                        
                        android.util.Log.d("RecordingService", "Microphone source changed: $deviceName connected")
                        showDeviceChangeNotification("$deviceName connected - Recording continues")
                        
                        // Continue recording with new device - MediaRecorder automatically adapts
                        // No need to restart, Android handles the switch
                    }
                }
            }
            
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                if (!isRecording) return
                
                for (device in removedDevices) {
                    val deviceType = device.type
                    val isMicrophoneDevice = when (deviceType) {
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_USB_HEADSET -> true
                        else -> false
                    }
                    
                    if (isMicrophoneDevice) {
                        val deviceName = when (deviceType) {
                            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth headset"
                            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
                            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
                            else -> "Audio device"
                        }
                        
                        android.util.Log.d("RecordingService", "Microphone source changed: $deviceName disconnected")
                        showDeviceChangeNotification("$deviceName disconnected - Using device microphone")
                        
                        // Recording continues with device microphone
                    }
                }
            }
        }
    } else {
        null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val meetingId = intent.getStringExtra(EXTRA_MEETING_ID)
                if (meetingId != null) {
                    // Start foreground immediately with placeholder notification
                    // This must be called within 5 seconds of startForegroundService()
                    startForeground(NOTIFICATION_ID, createNotification(isPaused = false))
                    
                    // Broadcast starting state immediately
                    currentMeetingId = meetingId
                    _recordingState.value = RecordingState.Recording(0L)
                    broadcastState(RecordingState.Recording(0L), meetingId, 0L)
                    android.util.Log.d("RecordingService", "Broadcasting Recording state with meetingId: $meetingId")
                    
                    // Now start recording asynchronously
                    startRecording(meetingId)
                }
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
            }
            ACTION_RESUME_RECORDING -> {
                resumeRecording()
            }
            ACTION_PAUSE_RECORDING -> {
                pauseRecording("Manual pause")
            }
        }
        
        return START_STICKY
    }
    
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun recoverFromProcessDeath() {
        val activeSession = recordingSessionDao.getActiveSession()
        if (activeSession != null && activeSession.isRecording) {
            currentMeetingId = activeSession.meetingId
            recordingSession = activeSession
            chunkIndex = activeSession.lastChunkIndex + 1
            
            if (activeSession.isPaused) {
                isPaused = true
                pauseReason = activeSession.pauseReason
                elapsedTime = activeSession.elapsedTime
                _recordingState.value = RecordingState.Paused(pauseReason ?: "Unknown", elapsedTime)
                // Start foreground if service was restarted
                startForeground(NOTIFICATION_ID, createNotification(isPaused = true, pauseReason = pauseReason))
            } else {
                // Resume recording - start foreground immediately
                startForeground(NOTIFICATION_ID, createNotification(isPaused = false))
                // Now start recording asynchronously
                startRecording(activeSession.meetingId)
            }
        }
    }
    
    @SuppressLint("ForegroundServiceType")
    private fun startRecording(meetingId: String) {
        if (isRecording) return
        
        serviceScope.launch {
            try {
                // Check storage
                if (!checkStorage()) {
                    _recordingState.value = RecordingState.Error("Low storage")
                    broadcastState(RecordingState.Error("Low storage"), meetingId, 0L)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }
                
                // Request audio focus
                val result = audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                
                if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    _recordingState.value = RecordingState.Error("Audio focus not granted")
                    broadcastState(RecordingState.Error("Audio focus not granted"), meetingId, 0L)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }
                
                // Set recording state BEFORE starting async operations
                currentMeetingId = meetingId
                startTime = System.currentTimeMillis()
                elapsedTime = 0L
                chunkIndex = 0
                isRecording = true
                isPaused = false
                pauseReason = null
                lastAudioLevel = 0
                silenceStartTime = 0L
                
                android.util.Log.d("RecordingService", "isRecording set to true, starting operations...")
                
                // Create or update session
                recordingSession = RecordingSession(
                    meetingId = meetingId,
                    isRecording = true,
                    isPaused = false,
                    startTime = startTime,
                    lastChunkIndex = -1
                )
                recordingSessionDao.insertSession(recordingSession!!)
                
                // Update notification (foreground was already started in onStartCommand)
                updateNotification(isPaused = false)
                
                android.util.Log.d("RecordingService", "Starting chunk recording and timer. isRecording: $isRecording")
                startChunkRecording()
                startTimer()
                startSilenceDetection()
                
                _recordingState.value = RecordingState.Recording(0L)
                broadcastState(RecordingState.Recording(0L), meetingId, 0L)
                android.util.Log.d("RecordingService", "Recording started successfully")
            } catch (e: Exception) {
                val errorMsg = "Recording failed: ${e.message ?: e.javaClass.simpleName}"
                _recordingState.value = RecordingState.Error(errorMsg)
                broadcastState(RecordingState.Error(errorMsg), meetingId, 0L)
                android.util.Log.e("RecordingService", "Error in startRecording", e)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }
    
    private fun pauseRecording(reason: String) {
        if (!isRecording || isPaused) return
        
        serviceScope.launch {
            isPaused = true
            pauseReason = reason
            
            // Stop timer and silence detection before stopping media recorder
            timerJob?.cancel()
            silenceDetectionJob?.cancel()
            
            stopCurrentChunk()
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            
            recordingSession?.let { session ->
                recordingSessionDao.updateSession(
                    session.copy(
                        isPaused = true,
                        pauseReason = reason,
                        elapsedTime = elapsedTime
                    )
                )
            }
            
            _recordingState.value = RecordingState.Paused(reason, elapsedTime)
            updateNotification(isPaused = true, pauseReason = reason)
            broadcastState(RecordingState.Paused(reason, elapsedTime), currentMeetingId, elapsedTime)
            android.util.Log.d("RecordingService", "Broadcasting paused state: $reason, elapsedTime: $elapsedTime")
        }
    }
    
    private fun resumeRecording() {
        if (!isRecording || !isPaused) return
        
        serviceScope.launch {
            try {
                // Check storage
                if (!checkStorage()) {
                    _recordingState.value = RecordingState.Error("Low storage")
                    stopRecording()
                    return@launch
                }
                
                // Request audio focus
                val result = audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                
                if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    _recordingState.value = RecordingState.Error("Audio focus not granted")
                    return@launch
                }
                
                isPaused = false
                pauseReason = null
                
                recordingSession?.let { session ->
                    recordingSessionDao.updateSession(
                        session.copy(
                            isPaused = false,
                            pauseReason = null
                        )
                    )
                }
                
                startChunkRecording()
                startTimer()
                startSilenceDetection()
                
                _recordingState.value = RecordingState.Recording(elapsedTime)
                updateNotification(isPaused = false)
                broadcastState(RecordingState.Recording(elapsedTime), currentMeetingId, elapsedTime)
            } catch (e: Exception) {
                _recordingState.value = RecordingState.Error(e.message ?: "Resume failed")
            }
        }
    }
    
    private fun stopRecording() {
        if (!isRecording) return
        
        serviceScope.launch {
            isRecording = false
            isPaused = false
            
            timerJob?.cancel()
            silenceDetectionJob?.cancel()
            
            stopCurrentChunk()
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            
            audioManager.abandonAudioFocus(audioFocusChangeListener)
            
            recordingSession?.let { session ->
                val meeting = meetingRepository.getMeetingById(session.meetingId)
                meeting?.let {
                    // Update meeting status to TRANSCRIBING
                    meetingRepository.updateMeeting(
                        it.copy(
                            status = MeetingStatus.TRANSCRIBING,
                            duration = elapsedTime
                        )
                    )
                    
                    // Trigger transcription worker
                    val transcriptionWork = TranscriptionWorker.createWorkRequest(session.meetingId)
                    workManager.enqueue(transcriptionWork)
                    android.util.Log.d("RecordingService", "Enqueued transcription worker for meeting ${session.meetingId}")
                }
                
                recordingSessionDao.updateSession(
                    session.copy(
                        isRecording = false,
                        isPaused = false,
                        elapsedTime = elapsedTime
                    )
                )
            }
            
            _recordingState.value = RecordingState.Stopped
            broadcastState(RecordingState.Stopped, currentMeetingId, elapsedTime)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    
    private fun startChunkRecording() {
        val meetingId = currentMeetingId ?: return
        val chunkStartTime = System.currentTimeMillis()
        
        serviceScope.launch {
            try {
                // Check if still recording before starting
                if (!isRecording || isPaused) {
                    android.util.Log.d("RecordingService", "Not recording or paused, skipping chunk start")
                    return@launch
                }
                
                mediaRecorder?.release()
                
                val externalDir = getExternalFilesDir(null)
                val internalDir = filesDir
                val baseDir = externalDir ?: internalDir
                val outputDir = File(baseDir, "recordings")
                
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }
                
                val chunkFile = File(outputDir, "${meetingId}_chunk_${chunkIndex}.m4a")
                android.util.Log.d("RecordingService", "Chunk file path: ${chunkFile.absolutePath}")
                
                // Check again before initializing MediaRecorder
                if (!isRecording || isPaused) {
                    android.util.Log.d("RecordingService", "Not recording or paused, aborting chunk initialization")
                    return@launch
                }
                
                android.util.Log.d("RecordingService", "Initializing MediaRecorder")
                mediaRecorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(chunkFile.absolutePath)
                    setMaxDuration(30000) // 30 seconds
                    setOnInfoListener { _, what, _ ->
                        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED && isRecording && !isPaused) {
                            serviceScope.launch {
                                try {
                                    finalizeChunk(chunkFile, chunkStartTime)
                                    chunkIndex++
                                    startChunkRecording() // Start next chunk with overlap
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    // Ignore cancellation during chunk transition
                                    android.util.Log.d("RecordingService", "Chunk transition cancelled")
                                }
                            }
                        }
                    }
                    
                    prepare()
                    android.util.Log.d("RecordingService", "MediaRecorder prepared, starting...")
                    
                    // Final check before starting
                    if (!isRecording || isPaused) {
                        release()
                        return@launch
                    }
                    
                    start()
                    android.util.Log.d("RecordingService", "MediaRecorder started successfully")
                }
                
                // Schedule chunk finalization after 28 seconds (with 2-second overlap)
                try {
                    delay(28000)
                    if (isRecording && !isPaused) {
                        finalizeChunk(chunkFile, chunkStartTime)
                        chunkIndex++
                        
                        // Start next chunk with 2-second overlap
                        delay(2000)
                        if (isRecording && !isPaused) {
                            startChunkRecording()
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Expected when stopping recording - just cleanup
                    android.util.Log.d("RecordingService", "Chunk recording cancelled (expected on stop)")
                    mediaRecorder?.apply {
                        try {
                            stop()
                        } catch (e: Exception) {
                            // Ignore errors when stopping
                        }
                        release()
                    }
                    mediaRecorder = null
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Expected cancellation when stopping - don't treat as error
                android.util.Log.d("RecordingService", "Chunk recording cancelled (expected)")
                mediaRecorder?.apply {
                    try {
                        stop()
                    } catch (ex: Exception) {
                        // Ignore
                    }
                    try {
                        release()
                    } catch (ex: Exception) {
                        // Ignore
                    }
                }
                mediaRecorder = null
            } catch (e: Exception) {
                // Only log real errors, not cancellations
                if (isRecording && !isPaused) {
                    val errorMsg = "Recording failed: ${e.message ?: e.javaClass.simpleName}"
                    android.util.Log.e("RecordingService", "Error in startChunkRecording: ${e.message}", e)
                    _recordingState.value = RecordingState.Error(errorMsg)
                    broadcastState(RecordingState.Error(errorMsg), currentMeetingId, elapsedTime)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }
    
    private suspend fun finalizeChunk(chunkFile: File, chunkStartTime: Long) {
        val meetingId = currentMeetingId ?: return
        
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    // Ignore
                }
            }
            
            val chunkDuration = System.currentTimeMillis() - chunkStartTime
            
            val audioChunk = AudioChunk(
                id = UUID.randomUUID().toString(),
                meetingId = meetingId,
                filePath = chunkFile.absolutePath,
                chunkIndex = chunkIndex,
                startTime = chunkStartTime - startTime,
                duration = chunkDuration
            )
            
            audioChunkDao.insertChunk(audioChunk)
            
            recordingSession?.let { session ->
                recordingSessionDao.updateSession(
                    session.copy(
                        lastChunkIndex = chunkIndex,
                        currentChunkPath = chunkFile.absolutePath
                    )
                )
            }
            
            // Trigger transcription for this chunk
            triggerTranscription(audioChunk)
            
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    private suspend fun stopCurrentChunk() {
        mediaRecorder?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                // Ignore
            }
        }
        mediaRecorder = null
    }
    
    private fun startTimer() {
        timerJob?.cancel()
        android.util.Log.d("RecordingService", "Starting timer. isRecording: $isRecording, isPaused: $isPaused")
        timerJob = serviceScope.launch {
            android.util.Log.d("RecordingService", "Timer coroutine started")
            while (isRecording && !isPaused) {
                delay(1000)
                if (isRecording && !isPaused) { // Double-check after delay
                    elapsedTime += 1000
                    android.util.Log.d("RecordingService", "Timer update: elapsedTime = $elapsedTime")
                    _recordingState.value = RecordingState.Recording(elapsedTime)
                    updateNotificationWithTimer() // Update notification with timer every second
                    // Broadcast state every second for real-time UI updates
                    broadcastState(RecordingState.Recording(elapsedTime), currentMeetingId, elapsedTime)
                }
            }
            android.util.Log.d("RecordingService", "Timer loop exited. isRecording: $isRecording, isPaused: $isPaused")
        }
    }
    
    private fun startSilenceDetection() {
        silenceDetectionJob?.cancel()
        silenceDetectionJob = serviceScope.launch {
            while (isRecording && !isPaused) {
                delay(1000)
                
                if (mediaRecorder != null) {
                    val maxAmplitude = try {
                        mediaRecorder?.maxAmplitude ?: 0
                    } catch (e: Exception) {
                        0
                    }
                    
                    if (maxAmplitude < SILENCE_THRESHOLD) {
                        if (silenceStartTime == 0L) {
                            silenceStartTime = System.currentTimeMillis()
                        } else if (System.currentTimeMillis() - silenceStartTime > 10000) {
                            // 10 seconds of silence
                            val warningState = RecordingState.Warning("No audio detected - Check microphone")
                            _recordingState.value = warningState
                            broadcastState(warningState, currentMeetingId, elapsedTime)
                            showWarningNotification()
                            silenceStartTime = 0L // Reset to avoid repeated warnings
                        }
                    } else {
                        silenceStartTime = 0L
                    }
                }
            }
        }
    }
    
    private fun checkStorage(): Boolean {
        val externalDir = getExternalFilesDir(null)
        val internalDir = filesDir
        
        // Try external storage first, fallback to internal
        val baseDir = externalDir ?: internalDir
        val outputDir = File(baseDir, "recordings")
        
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        
        // Check available space on the partition containing the directory
        val stat = android.os.StatFs(outputDir.absolutePath)
        val freeSpace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            stat.availableBytes
        } else {
            @Suppress("DEPRECATION")
            stat.availableBlocks * stat.blockSize.toLong()
        }
        
        val minRequiredSpace = 1 * 1024 * 1024 // 1 MB (reduced for testing/emulator)
        
        android.util.Log.d("RecordingService", "Storage check: freeSpace = ${freeSpace / (1024 * 1024)} MB, required = ${minRequiredSpace / (1024 * 1024)} MB, path = ${outputDir.absolutePath}")
        
        if (freeSpace <= minRequiredSpace) {
            android.util.Log.w("RecordingService", "Low storage detected: $freeSpace bytes available, need $minRequiredSpace bytes")
            return false
        }
        
        return true
    }
    
    private fun triggerTranscription(chunk: AudioChunk) {
        // Chunks are transcribed individually when they're saved
        // The main transcription worker is triggered when recording stops
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for voice recording"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(isPaused: Boolean, pauseReason: String? = null): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(ACTION_STOP_RECORDING).apply {
            setPackage(packageName)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Format elapsed time for display
        val totalSeconds = elapsedTime / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val timeString = if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
        
        val statusText = if (isPaused) {
            "Paused - $pauseReason"
        } else {
            "Recording..."
        }
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording")
            .setContentText("$statusText | $timeString")
            .setSubText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false) // Don't show timestamp
            .setUsesChronometer(!isPaused && elapsedTime > 0) // Show chronometer when recording
            .setWhen(System.currentTimeMillis() - elapsedTime) // Set start time for chronometer
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
        
        if (isPaused && pauseReason == "Audio focus lost") {
            val resumeIntent = Intent(ACTION_RESUME_RECORDING).apply {
                setPackage(packageName)
            }
            val resumePendingIntent = PendingIntent.getBroadcast(
                this, 0, resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
        }
        
        return builder.build()
    }
    
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun updateNotification(isPaused: Boolean = this.isPaused, pauseReason: String? = this.pauseReason) {
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(NOTIFICATION_ID, createNotification(isPaused, pauseReason))
    }
    
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun updateNotificationWithTimer(isPaused: Boolean = this.isPaused, pauseReason: String? = this.pauseReason) {
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(NOTIFICATION_ID, createNotification(isPaused, pauseReason))
    }
    
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showWarningNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording Warning")
            .setContentText("No audio detected - Check microphone")
            .setSmallIcon(R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        NotificationManagerCompat.from(this).notify(WARNING_NOTIFICATION_ID, notification)
    }
    
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showDeviceChangeNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(this).notify(DEVICE_CHANGE_NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        silenceDetectionJob?.cancel()
        mediaRecorder?.release()
        audioManager.abandonAudioFocus(audioFocusChangeListener)
        
        // Unregister audio device callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback!!)
        }
        
        unregisterReceiver(receiver)
        serviceScope.cancel()
    }
    
    private fun broadcastState(state: RecordingState, meetingId: String? = null, elapsedTime: Long = 0L) {
        val intent = Intent(ACTION_RECORDING_STATE_CHANGED).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATE, when (state) {
                is RecordingState.Recording -> "recording"
                is RecordingState.Paused -> "paused"
                is RecordingState.Stopped -> "stopped"
                is RecordingState.Error -> "error"
                is RecordingState.Warning -> "warning"
                else -> "idle"
            })
            meetingId?.let { putExtra(EXTRA_MEETING_ID, it) }
            if (state is RecordingState.Recording) {
                putExtra(EXTRA_ELAPSED_TIME, elapsedTime)
            } else if (state is RecordingState.Paused) {
                putExtra(EXTRA_ELAPSED_TIME, elapsedTime) // Include elapsed time for paused state
                putExtra(EXTRA_PAUSE_REASON, (state as RecordingState.Paused).reason)
            } else if (state is RecordingState.Error) {
                putExtra(EXTRA_ERROR_MESSAGE, (state as RecordingState.Error).message)
            } else if (state is RecordingState.Warning) {
                putExtra(EXTRA_WARNING_MESSAGE, (state as RecordingState.Warning).message)
            }
        }
        sendBroadcast(intent)
    }
    
    companion object {
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1
        private const val WARNING_NOTIFICATION_ID = 2
        private const val DEVICE_CHANGE_NOTIFICATION_ID = 3
        private const val SILENCE_THRESHOLD = 1000
        
        const val ACTION_START_RECORDING = "com.example.voiceapp.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.voiceapp.STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "com.example.voiceapp.PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "com.example.voiceapp.RESUME_RECORDING"
        const val ACTION_RECORDING_STATE_CHANGED = "com.example.voiceapp.RECORDING_STATE_CHANGED"
        const val EXTRA_MEETING_ID = "meeting_id"
        const val EXTRA_STATE = "state"
        const val EXTRA_ELAPSED_TIME = "elapsed_time"
        const val EXTRA_PAUSE_REASON = "pause_reason"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        const val EXTRA_WARNING_MESSAGE = "warning_message"
    }
}

sealed class RecordingState {
    object Idle : RecordingState()
    data class Recording(val elapsedTime: Long) : RecordingState()
    data class Paused(val reason: String, val elapsedTime: Long = 0L) : RecordingState()
    object Stopped : RecordingState()
    data class Warning(val message: String) : RecordingState()
    data class Error(val message: String) : RecordingState()
}

