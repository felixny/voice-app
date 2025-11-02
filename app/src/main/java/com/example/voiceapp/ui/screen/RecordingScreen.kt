package com.example.voiceapp.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.voiceapp.service.RecordingState
import com.example.voiceapp.ui.util.Permissions
import com.example.voiceapp.ui.viewmodel.RecordingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    meetingId: String?,
    onNavigateBack: () -> Unit,
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val currentMeetingId by viewModel.currentMeetingId.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    
    var hasPermissions by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    
    // Check if permissions are granted
    LaunchedEffect(Unit) {
        val requiredPermissions = Permissions.getEssentialPermissions()
        hasPermissions = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        hasPermissions = allGranted
        permissionDenied = !allGranted
        
        if (allGranted && (meetingId == "new" || meetingId == null)) {
            viewModel.startRecording()
        }
    }
    
    // Request permissions and start recording
    LaunchedEffect(meetingId) {
        if (meetingId == "new" || meetingId == null) {
            val requiredPermissions = Permissions.getEssentialPermissions()
            val missingPermissions = requiredPermissions.filter { permission ->
                ContextCompat.checkSelfPermission(context, permission) != 
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            
            if (missingPermissions.isEmpty()) {
                hasPermissions = true
                viewModel.startRecording()
            } else {
                permissionLauncher.launch(missingPermissions.toTypedArray())
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recording") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Stop, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            permissionDenied -> {
                PermissionDeniedScreen(
                    onRequestAgain = {
                        permissionDenied = false
                        permissionLauncher.launch(Permissions.getEssentialPermissions())
                    },
                    onNavigateBack = onNavigateBack,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp)
                )
            }
            !hasPermissions -> {
                // Show loading while requesting permissions
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
            // Timer Display - Show elapsed time for Recording and Paused states
            val elapsedTime = when (val state = recordingState) {
                is RecordingState.Recording -> state.elapsedTime
                is RecordingState.Paused -> state.elapsedTime
                else -> 0L
            }
            Text(
                text = formatTime(elapsedTime),
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(vertical = 32.dp)
            )
            
            // Status Indicator
            StatusIndicator(
                state = recordingState,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // Show error if there is one
            if (recordingState is RecordingState.Error) {
                Text(
                    text = (recordingState as RecordingState.Error).message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
            
            // Debug: Show current state (can be removed in production)
            if (recordingState !is RecordingState.Recording && recordingState !is RecordingState.Error) {
                Text(
                    text = "State: ${recordingState.javaClass.simpleName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            // Control Buttons
            Row(
                modifier = Modifier.padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                when (recordingState) {
                    is RecordingState.Recording -> {
                        IconButton(
                            onClick = { viewModel.pauseRecording() },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = "Pause",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is RecordingState.Paused -> {
                        IconButton(
                            onClick = { viewModel.resumeRecording() },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Resume",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    else -> {}
                }
                
                IconButton(
                    onClick = {
                        viewModel.stopRecording()
                        onNavigateBack()
                    },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
                }
            }
        }
    }
}

@Composable
fun PermissionDeniedScreen(
    onRequestAgain: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "This app needs microphone and notification permissions to record audio.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onRequestAgain) {
            Text("Grant Permissions")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        TextButton(onClick = onNavigateBack) {
            Text("Cancel")
        }
    }
}

@Composable
fun StatusIndicator(
    state: RecordingState,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (state) {
        is RecordingState.Recording -> "Recording..." to MaterialTheme.colorScheme.primary
        is RecordingState.Paused -> {
            val reason = (state as RecordingState.Paused).reason
            "Paused - $reason" to MaterialTheme.colorScheme.error
        }
        is RecordingState.Stopped -> "Stopped" to MaterialTheme.colorScheme.onSurfaceVariant
        is RecordingState.Warning -> {
            val message = (state as RecordingState.Warning).message
            message to MaterialTheme.colorScheme.error
        }
        is RecordingState.Error -> {
            val message = (state as RecordingState.Error).message
            "Error: $message" to MaterialTheme.colorScheme.error
        }
        else -> "Ready" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}


fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

