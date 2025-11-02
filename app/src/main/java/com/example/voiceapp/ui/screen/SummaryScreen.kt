package com.example.voiceapp.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.voiceapp.data.local.entity.Summary
import com.example.voiceapp.data.local.entity.SummaryStatus
import com.example.voiceapp.ui.viewmodel.SummaryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    meetingId: String,
    onNavigateBack: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel()
) {
    val summary by viewModel.getSummary(meetingId).collectAsState()

    LaunchedEffect(meetingId) {
        if (summary == null || summary?.status == SummaryStatus.PENDING) {
            viewModel.generateSummary(meetingId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meeting Summary") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (summary?.status == SummaryStatus.FAILED) {
                        IconButton(onClick = { viewModel.generateSummary(meetingId) }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Retry"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            summary == null || summary?.status == SummaryStatus.PENDING -> {
                LoadingState(
                    text = "Initializing...",
                    modifier = Modifier.padding(paddingValues)
                )
            }

            summary?.status == SummaryStatus.GENERATING -> {
                // Show streaming summary as it's being generated
                StreamingSummaryContent(
                    summary = summary!!,
                    viewModel = viewModel,
                    modifier = Modifier.padding(paddingValues)
                )
            }

            summary?.status == SummaryStatus.FAILED -> {
                ErrorState(
                    onRetry = { viewModel.generateSummary(meetingId) },
                    modifier = Modifier.padding(paddingValues)
                )
            }

            summary?.status == SummaryStatus.COMPLETED -> {
                SummaryContent(
                    summary = summary!!,
                    viewModel = viewModel,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
fun LoadingState(
    text: String = "Generating summary...",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun StreamingSummaryContent(
    summary: Summary,
    viewModel: SummaryViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Show "Generating..." indicator at top
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Generating summary...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Divider()
        
        // Show summary content as it streams in (updates in real-time from database)
        // Title Section
        if (summary.title.isNotEmpty()) {
            SummarySection(
                title = "Title",
                content = summary.title
            )
        }

        // Summary Section - shows partial content as it streams
        if (summary.summary.isNotEmpty()) {
            SummarySection(
                title = "Summary",
                content = summary.summary
            )
        } else {
            SummarySection(
                title = "Summary",
                content = "Generating summary text..."
            )
        }

        // Action Items - shows as they're extracted
        val actionItems = viewModel.parseActionItems(summary.actionItems)
        if (actionItems.isNotEmpty()) {
            SummarySection(
                title = "Action Items",
                items = actionItems
            )
        }

        // Key Points - shows as they're extracted
        val keyPoints = viewModel.parseKeyPoints(summary.keyPoints)
        if (keyPoints.isNotEmpty()) {
            SummarySection(
                title = "Key Points",
                items = keyPoints
            )
        }
    }
}

@Composable
fun ErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Failed to generate summary",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
fun SummaryContent(
    summary: Summary,
    viewModel: SummaryViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Title Section
        if (summary.title.isNotEmpty()) {
            SummarySection(
                title = "Title",
                content = summary.title
            )
        }

        // Summary Section
        if (summary.summary.isNotEmpty()) {
            SummarySection(
                title = "Summary",
                content = summary.summary
            )
        }

        // Action Items
        val actionItems = viewModel.parseActionItems(summary.actionItems)
        if (actionItems.isNotEmpty()) {
            SummarySection(
                title = "Action Items",
                items = actionItems
            )
        }

        // Key Points
        val keyPoints = viewModel.parseKeyPoints(summary.keyPoints)
        if (keyPoints.isNotEmpty()) {
            SummarySection(
                title = "Key Points",
                items = keyPoints
            )
        }
    }
}

@Composable
fun SummarySection(
    title: String,
    content: String = "",
    items: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (content.isNotEmpty()) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        if (items.isNotEmpty()) {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "${index + 1}. ",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

