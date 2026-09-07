package com.example.arivai.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.arivai.data.model.EngineMode
import com.example.arivai.data.model.ModelDownloadManager
import com.example.arivai.data.ocr.OcrHelper
import com.example.arivai.data.stt.SpeechResult
import com.example.arivai.data.stt.SpeechToTextHelper
import com.example.arivai.domain.ChatMessage
import com.example.arivai.domain.Sender
import com.example.arivai.domain.TutorRepository
import com.example.arivai.util.Constants
import com.example.arivai.util.HardwareChecker
import com.example.arivai.util.HardwareReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val inputText: String = "",
    val isListening: Boolean = false,
    val engineMode: EngineMode = EngineMode.NOT_LOADED,
    val engineStatusDetail: String = "",
    val hardwareReport: HardwareReport? = null,
    val showModelStatusDialog: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val tutorRepository: TutorRepository,
    private val speechToTextHelper: SpeechToTextHelper,
    private val ocrHelper: OcrHelper,
    private val downloadManager: ModelDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            checkAndInitModel()
        }
        // Welcome message
        val welcomeMsg = ChatMessage(
            sender = Sender.TUTOR,
            text = "Hello! I am ariv.ai, your Socratic AI tutor. Ask me any problem or topic from your textbook, and I will guide you step-by-step!"
        )
        _uiState.value = _uiState.value.copy(messages = listOf(welcomeMsg))
    }

    fun checkAndInitModel(customPath: String? = null) {
        val status = tutorRepository.ensureModelLoaded(customPath)
        val (mode, detail) = tutorRepository.getEngineStateInfo()
        _uiState.value = _uiState.value.copy(
            engineMode = mode,
            engineStatusDetail = "$status\nDiagnostics: $detail"
        )
    }

    fun loadHardwareReport(context: Context) {
        val report = HardwareChecker.checkDeviceHardware(context)
        _uiState.value = _uiState.value.copy(hardwareReport = report)
    }

    fun toggleModelStatusDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showModelStatusDialog = show)
    }

    fun loadCustomGgufFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = _uiState.value.copy(errorMessage = "Loading selected .gguf file...")
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val destFile = downloadManager.getModelFile(Constants.CUSTOM_MODEL_FILENAME)
                    val outputStream = FileOutputStream(destFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()

                    val status = tutorRepository.reloadCustomModel(destFile.absolutePath)
                    val (mode, detail) = tutorRepository.getEngineStateInfo()
                    _uiState.value = _uiState.value.copy(
                        engineMode = mode,
                        engineStatusDetail = "$status\nDiagnostics: $detail",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to load GGUF file: ${e.message}")
            }
        }
    }

    fun onInputTextChange(newText: String) {
        _uiState.value = _uiState.value.copy(inputText = newText)
    }

    fun sendMessage() {
        val userText = _uiState.value.inputText.trim()
        if (userText.isBlank() || _uiState.value.isGenerating) return

        val userMessage = ChatMessage(sender = Sender.USER, text = userText)
        val currentMessages = _uiState.value.messages + userMessage

        _uiState.value = _uiState.value.copy(
            messages = currentMessages,
            inputText = "",
            isGenerating = true,
            errorMessage = null
        )

        val tutorMessageId = java.util.UUID.randomUUID().toString()
        val initialTutorMsg = ChatMessage(
            id = tutorMessageId,
            sender = Sender.TUTOR,
            text = "Thinking..."
        )

        _uiState.value = _uiState.value.copy(
            messages = currentMessages + initialTutorMsg
        )

        viewModelScope.launch(Dispatchers.IO) {
            tutorRepository.ask(userText, currentMessages)
                .collect { (streamedText, citedChunks) ->
                    val updatedMessages = _uiState.value.messages.map { msg ->
                        if (msg.id == tutorMessageId) {
                            msg.copy(
                                text = streamedText,
                                citedChunks = citedChunks,
                                isRevealedAnswer = streamedText.contains("worked solution", ignoreCase = true) || streamedText.contains("full answer", ignoreCase = true)
                            )
                        } else {
                            msg
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        messages = updatedMessages,
                        isGenerating = true
                    )
                }
            _uiState.value = _uiState.value.copy(
                isGenerating = false
            )
        }
    }

    fun startVoiceInput() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isListening = true)
            speechToTextHelper.startListening().collect { result ->
                when (result) {
                    is SpeechResult.Ready -> {}
                    is SpeechResult.Listening -> {
                        _uiState.value = _uiState.value.copy(isListening = true)
                    }
                    is SpeechResult.Partial -> {
                        _uiState.value = _uiState.value.copy(inputText = result.text)
                    }
                    is SpeechResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            inputText = result.text,
                            isListening = false
                        )
                    }
                    is SpeechResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isListening = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun processImageForInput(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val extractedText = ocrHelper.recognizeText(bitmap)
                if (extractedText.isNotBlank()) {
                    _uiState.value = _uiState.value.copy(inputText = extractedText)
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = "Couldn't read text clearly from image")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "OCR Error: ${e.message}")
            }
        }
    }
}
