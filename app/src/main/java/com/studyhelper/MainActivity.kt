package com.studyhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.studyhelper.ui.AskAiScreen
import com.studyhelper.ui.HistoryScreen
import com.studyhelper.ui.HomeScreen
import com.studyhelper.ui.QuizScreen
import com.studyhelper.ui.SummarizeScreen
import com.studyhelper.viewmodel.StudyViewModel
import com.studyhelper.viewmodel.StudyViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val app = application as StudyApp
                val viewModel: StudyViewModel = viewModel(
                    factory = StudyViewModelFactory(app.database.historyDao())
                )
                val snackbarHostState = remember { SnackbarHostState() }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize()
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(padding)
                    ) {
                        composable("home") { HomeScreen(navController) }
                        composable("ask") { AskAiScreen(viewModel, snackbarHostState) }
                        composable("summarize") { SummarizeScreen(viewModel, snackbarHostState) }
                        composable("quiz") { QuizScreen(viewModel, snackbarHostState) }
                        composable("history") { HistoryScreen(viewModel) }
                    }
                }
            }
        }
    }
}
