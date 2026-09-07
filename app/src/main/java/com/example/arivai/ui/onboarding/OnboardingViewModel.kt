package com.example.arivai.ui.onboarding

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.arivai.data.model.DownloadState
import com.example.arivai.data.model.ModelDownloadManager
import com.example.arivai.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import javax.inject.Inject

data class OnboardingUiState(
    val isDownloading: Boolean = false,
    val progressPercent: Int = 0,
    val downloadedMb: Float = 0f,
    val totalMb: Float = 0f,
    val hasExistingModel: Boolean = false,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val downloadManager: ModelDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null

    init {
        checkExistingModels()
    }

    fun checkExistingModels() {
        val modelReady = downloadManager.isModelDownloaded(Constants.MODEL_FILENAME, expectedMinSizeMb = 10)
        _uiState.value = _uiState.value.copy(hasExistingModel = modelReady)
    }

    fun startDownload() {
        downloadJob?.cancel()
        _uiState.value = _uiState.value.copy(isDownloading = true, errorMessage = null)

        downloadJob = viewModelScope.launch {
            downloadManager.downloadFile(Constants.DEFAULT_MODEL_URL, Constants.MODEL_FILENAME)
                .collect { state ->
                    when (state) {
                        is DownloadState.Progress -> {
                            val downloadedMb = state.bytesDownloaded / (1024f * 1024f)
                            val totalMb = state.totalBytes / (1024f * 1024f)
                            _uiState.value = _uiState.value.copy(
                                isDownloading = true,
                                progressPercent = state.percentage,
                                downloadedMb = downloadedMb,
                                totalMb = totalMb,
                                errorMessage = null
                            )
                        }
                        is DownloadState.Completed -> {
                            _uiState.value = _uiState.value.copy(
                                isDownloading = false,
                                hasExistingModel = true,
                                isCompleted = true,
                                progressPercent = 100,
                                errorMessage = null
                            )
                        }
                        is DownloadState.Error -> {
                            _uiState.value = _uiState.value.copy(
                                isDownloading = false,
                                errorMessage = state.message
                            )
                        }
                    }
                }
        }
    }

    fun loadLocalGgufFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val destFile = downloadManager.getModelFile(Constants.MODEL_FILENAME)
                    val outputStream = FileOutputStream(destFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()

                    _uiState.value = _uiState.value.copy(
                        hasExistingModel = true,
                        isCompleted = true,
                        isDownloading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to copy local GGUF file: ${e.message}"
                )
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _uiState.value = _uiState.value.copy(isDownloading = false, errorMessage = "Download cancelled")
    }

    fun continueToLibrary() {
        _uiState.value = _uiState.value.copy(isCompleted = true)
    }
}
