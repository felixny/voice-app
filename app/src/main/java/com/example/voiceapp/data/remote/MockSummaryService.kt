package com.example.voiceapp.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockSummaryService @Inject constructor() {
    fun generateSummaryStream(transcript: String): Flow<SummaryResponse> = flow {
        android.util.Log.d("MockSummaryService", "Starting summary generation. Transcript length: ${transcript.length}")
        
        delay(500)
        
        // Use actual transcript text if available, otherwise use mock
        val hasActualTranscript = transcript.isNotBlank() && 
            !transcript.contains("mock transcription", ignoreCase = true) &&
            !transcript.contains("real implementation", ignoreCase = true) &&
            !transcript.equals("No transcript content available. This is a mock summary.", ignoreCase = true)
        
        android.util.Log.d("MockSummaryService", "hasActualTranscript: $hasActualTranscript")
        
        if (hasActualTranscript && transcript.length > 50) {
            // Generate summary based on actual transcript
            val words = transcript.split(" ").take(20) // Take first 20 words for title
            val title = words.joinToString(" ").take(50) + if (words.size >= 20) "..." else ""
            
            // Generate summary from transcript
            val summaryText = if (transcript.length > 200) {
                transcript.take(200) + "..."
            } else {
                transcript
            }
            
            // Simulate streaming response based on actual transcript
            emit(SummaryResponse(
                title = title.take(50),
                summary = summaryText,
                isComplete = false
            ))
            delay(300)
            
            emit(SummaryResponse(
                title = title.take(50),
                summary = if (transcript.length > 400) transcript.take(400) + "..." else transcript,
                isComplete = false
            ))
            delay(300)
            
            // Extract key points from transcript (simple extraction)
            val sentences = transcript.split(Regex("[.!?]")).filter { it.trim().length > 10 }
            val keyPoints = sentences.take(3).map { it.trim().take(100) }
            
            emit(SummaryResponse(
                title = title.take(50),
                summary = transcript,
                actionItems = extractActionItems(transcript),
                keyPoints = keyPoints,
                isComplete = true
            ))
        } else {
            // Fallback to mock data if transcript is empty or mock
            emit(SummaryResponse(title = "Meeting Summary", isComplete = false))
            delay(300)
            
            emit(SummaryResponse(
                title = "Team Sync Meeting",
                summary = "The team discussed project progress and upcoming deadlines.",
                isComplete = false
            ))
            delay(300)
            
            emit(SummaryResponse(
                title = "Team Sync Meeting",
                summary = "The team discussed project progress and upcoming deadlines. Key topics included sprint planning, bug fixes, and feature implementations.",
                actionItems = listOf("Complete bug fixes by Friday", "Review pull requests", "Plan next sprint"),
                isComplete = false
            ))
            delay(300)
            
            emit(SummaryResponse(
                title = "Team Sync Meeting",
                summary = "The team discussed project progress and upcoming deadlines. Key topics included sprint planning, bug fixes, and feature implementations. Everyone agreed on the timeline and responsibilities.",
                actionItems = listOf(
                    "Complete bug fixes by Friday - Assigned to John",
                    "Review pull requests - Assigned to Sarah",
                    "Plan next sprint - Assigned to Mike"
                ),
                keyPoints = listOf(
                    "Project is on track",
                    "New features planned for next release",
                    "Team capacity reviewed"
                ),
                isComplete = true
            ))
        }
        
        android.util.Log.d("MockSummaryService", "Summary flow completed successfully")
    }
    
    private fun extractActionItems(transcript: String): List<String> {
        // Simple extraction: look for action words
        val actionPattern = Regex("(?i)\\b(must|need|should|will|todo|action|task).{0,50}", RegexOption.IGNORE_CASE)
        val matches = actionPattern.findAll(transcript).take(5)
        return matches.map { it.value.trim().take(100) }.toList()
            .ifEmpty { listOf("Review transcript for action items") }
    }
}

