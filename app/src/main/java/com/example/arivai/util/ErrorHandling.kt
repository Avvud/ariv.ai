package com.example.arivai.util

sealed class AppError : Exception() {
    data class ModelLoadError(override val message: String) : AppError()
    data class OutOfMemoryError(override val message: String) : AppError()
    data class DownloadError(override val message: String) : AppError()
    data class OcrError(override val message: String) : AppError()
    data class SpeechError(override val message: String) : AppError()
    data class QuizGenerationError(override val message: String) : AppError()
}

object ErrorHandler {
    fun getUserFriendlyMessage(error: Throwable): String {
        return when (error) {
            is AppError.OutOfMemoryError -> "This device doesn't have enough memory for the on-device model."
            is AppError.ModelLoadError -> "Failed to load AI model. Please ensure the file is downloaded correctly."
            is AppError.DownloadError -> "Download failed. Please check your internet connection and try again."
            is AppError.OcrError -> "Couldn't read text clearly from the image. Please try again or type manually."
            is AppError.SpeechError -> "Speech recognition failed. Please try again or use text input."
            is AppError.QuizGenerationError -> "Failed to generate quiz questions. Please try again."
            else -> error.localizedMessage ?: "An unexpected error occurred."
        }
    }
}
