package com.example.arivai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.arivai.ui.chat.ChatScreen
import com.example.arivai.ui.chat.ChatViewModel
import com.example.arivai.ui.library.LibraryScreen
import com.example.arivai.ui.library.LibraryViewModel
import com.example.arivai.ui.onboarding.OnboardingScreen
import com.example.arivai.ui.onboarding.OnboardingViewModel
import com.example.arivai.ui.quiz.QuizScreen
import com.example.arivai.ui.quiz.QuizViewModel
import com.example.arivai.ui.theme.ArivaiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArivaiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MathMateAppNavHost()
                }
            }
        }
    }
}

@Composable
fun MathMateAppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "onboarding"
    ) {
        composable("onboarding") {
            val viewModel: OnboardingViewModel = hiltViewModel()
            OnboardingScreen(
                viewModel = viewModel,
                onNavigateToLibrary = {
                    navController.navigate("library") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

        composable("library") {
            val viewModel: LibraryViewModel = hiltViewModel()
            LibraryScreen(
                viewModel = viewModel,
                onNavigateToChat = { navController.navigate("chat") },
                onNavigateToQuiz = { navController.navigate("quiz") }
            )
        }

        composable("chat") {
            val viewModel: ChatViewModel = hiltViewModel()
            ChatScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("quiz") {
            val viewModel: QuizViewModel = hiltViewModel()
            QuizScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
