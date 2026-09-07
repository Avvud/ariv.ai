package com.example.arivai.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.arivai.data.quiz.QuizGenerator
import com.example.arivai.data.quiz.QuizQuestion
import com.example.arivai.data.rag.VectorStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuizUiState(
    val isLoading: Boolean = false,
    val questions: List<QuizQuestion> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val selectedOptionIndex: Int? = null,
    val score: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val quizGenerator: QuizGenerator,
    private val vectorStore: VectorStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    init {
        generateQuizFromTextbook()
    }

    fun generateQuizFromTextbook() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = QuizUiState(isLoading = true)

            val chunks = vectorStore.getChunks()
            if (chunks.isEmpty()) {
                // Fallback sample questions if vector store is empty
                val fallbackQuestions = listOf(
                    QuizQuestion(
                        question = "What is the order of operations in basic mathematics?",
                        options = listOf("PEMDAS / BODMAS", "Left to Right always", "Addition first", "Exponent last"),
                        correctIndex = 0,
                        explanation = "PEMDAS / BODMAS specifies Parentheses, Exponents, Multiplication, Division, Addition, Subtraction."
                    ),
                    QuizQuestion(
                        question = "What is the derivative of x² with respect to x?",
                        options = listOf("x", "2x", "x²", "2"),
                        correctIndex = 1,
                        explanation = "Using the power rule: d/dx(x^n) = n*x^(n-1), so d/dx(x²) = 2x."
                    )
                )
                _uiState.value = QuizUiState(
                    isLoading = false,
                    questions = fallbackQuestions
                )
                return@launch
            }

            val contextExcerpt = chunks.take(3).joinToString("\n\n") { it.text }
            val quizResult = quizGenerator.generateQuiz(contextExcerpt)

            if (quizResult.questions.isNotEmpty()) {
                _uiState.value = QuizUiState(
                    isLoading = false,
                    questions = quizResult.questions
                )
            } else {
                _uiState.value = QuizUiState(
                    isLoading = false,
                    errorMessage = "Could not parse generated quiz. Try again."
                )
            }
        }
    }

    fun selectOption(optionIndex: Int) {
        val currentState = _uiState.value
        if (currentState.selectedOptionIndex != null) return // Already answered this question

        val currentQ = currentState.questions.getOrNull(currentState.currentQuestionIndex) ?: return
        val isCorrect = optionIndex == currentQ.correctIndex
        val newScore = if (isCorrect) currentState.score + 1 else currentState.score

        _uiState.value = currentState.copy(
            selectedOptionIndex = optionIndex,
            score = newScore
        )
    }

    fun nextQuestion() {
        val currentState = _uiState.value
        val nextIdx = currentState.currentQuestionIndex + 1

        if (nextIdx < currentState.questions.size) {
            _uiState.value = currentState.copy(
                currentQuestionIndex = nextIdx,
                selectedOptionIndex = null
            )
        }
    }
}
