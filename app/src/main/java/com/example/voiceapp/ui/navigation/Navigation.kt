package com.example.voiceapp.ui.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    data class Recording(val meetingId: String) : Screen("recording/$meetingId")
    data class Summary(val meetingId: String) : Screen("summary/$meetingId")
}

