package com.example.voiceapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.voiceapp.ui.navigation.Screen
import com.example.voiceapp.ui.screen.DashboardScreen
import com.example.voiceapp.ui.screen.RecordingScreen
import com.example.voiceapp.ui.screen.SummaryScreen
import com.example.voiceapp.ui.theme.VoiceAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoiceAppTheme {
                VoiceAppNavigation()
            }
        }
    }
}

@Composable
fun VoiceAppNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToRecording = { meetingId ->
                    navController.navigate(Screen.Recording(meetingId).route)
                },
                onNavigateToSummary = { meetingId ->
                    navController.navigate(Screen.Summary(meetingId).route)
                }
            )
        }
        
        composable("recording/{meetingId}") { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getString("meetingId")
            RecordingScreen(
                meetingId = meetingId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("summary/{meetingId}") { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getString("meetingId")
                ?: return@composable
            
            SummaryScreen(
                meetingId = meetingId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}