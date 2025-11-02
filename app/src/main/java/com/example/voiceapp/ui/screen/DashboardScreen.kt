package com.example.voiceapp.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.voiceapp.data.local.entity.Meeting
import com.example.voiceapp.ui.navigation.Screen
import com.example.voiceapp.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToRecording: (String) -> Unit,
    onNavigateToSummary: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val meetings by viewModel.meetings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Meetings") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToRecording("new") }
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Recording")
            }
        }
    ) { paddingValues ->
        if (meetings.isEmpty()) {
            EmptyState(
                onStartRecording = { onNavigateToRecording("new") },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(
                    items = meetings,
                    key = { it.id }
                ) { meeting ->
                    MeetingItem(
                        meeting = meeting,
                        onDelete = { viewModel.deleteMeeting(meeting) },
                        onItemClick = {
                            // If meeting is completed/stopped, show summary (even if still generating)
                            // If meeting is still recording/paused, show recording screen
                            when (meeting.status) {
                                com.example.voiceapp.data.local.entity.MeetingStatus.RECORDING,
                                com.example.voiceapp.data.local.entity.MeetingStatus.PAUSED -> {
                                    onNavigateToRecording(meeting.id)
                                }
                                else -> {
                                    // STOPPED, TRANSCRIBING, or COMPLETED -> show summary
                                    onNavigateToSummary(meeting.id)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    onStartRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No meetings yet",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "Tap the + button to start recording",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MeetingItem(
    meeting: Meeting,
    onDelete: () -> Unit,
    onItemClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = meeting.title.ifEmpty { "Meeting ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(meeting.createdAt))}" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDuration(meeting.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(meeting.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
        minutes > 0 -> String.format("%d:%02d", minutes, seconds % 60)
        else -> String.format("0:%02d", seconds)
    }
}

