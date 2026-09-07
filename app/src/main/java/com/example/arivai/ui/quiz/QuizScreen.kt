package com.example.arivai.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    viewModel: QuizViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ariv.ai Quiz Engine") },
                actions = {
                    Text(
                        text = "Score: ${uiState.score}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Generating questions from textbook...")
            } else if (uiState.questions.isNotEmpty()) {
                val currentQ = uiState.questions.getOrNull(uiState.currentQuestionIndex)

                if (currentQ != null) {
                    Text(
                        text = "Question ${uiState.currentQuestionIndex + 1} of ${uiState.questions.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = currentQ.question,
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            currentQ.options.forEachIndexed { index, option ->
                                val isSelected = uiState.selectedOptionIndex == index
                                val isCorrect = index == currentQ.correctIndex

                                val optionColor = when {
                                    uiState.selectedOptionIndex == null -> MaterialTheme.colorScheme.surfaceVariant
                                    isCorrect -> Color(0xFFC8E6C9) // Green
                                    isSelected -> Color(0xFFFFCDD2) // Red
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(optionColor, shape = RoundedCornerShape(8.dp))
                                        .clickable { viewModel.selectOption(index) }
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "${('A' + index)}. $option",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            if (uiState.selectedOptionIndex != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Explanation: ${currentQ.explanation}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (uiState.selectedOptionIndex != null) {
                        if (uiState.currentQuestionIndex < uiState.questions.size - 1) {
                            Button(onClick = { viewModel.nextQuestion() }) {
                                Text("Next Question")
                            }
                        } else {
                            Text(
                                text = "Quiz Completed! Final Score: ${uiState.score}/${uiState.questions.size}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { viewModel.generateQuizFromTextbook() }) {
                                Text("Generate More Questions")
                            }
                        }
                    }
                }
            } else {
                Text("No questions generated.")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { viewModel.generateQuizFromTextbook() }) {
                    Text("Retry")
                }
            }
        }
    }
}
