package com.example.arivai.data.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

interface TokenCallback {
    fun onToken(token: String)
    fun onComplete()
}

enum class EngineMode {
    NOT_LOADED,
    NATIVE_GGUF,
    DEMO_FALLBACK
}

class LlamaEngine {
    private var modelHandle: Long = 0L
    var engineMode: EngineMode = EngineMode.NOT_LOADED
        private set

    var currentModelPath: String? = null
        private set

    var statusDetailMessage: String = "Engine initialized. Model not loaded."
        private set

    external fun nativeLoadModel(path: String, nThreads: Int): Long
    external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int): String
    external fun nativeGenerateStream(handle: Long, prompt: String, maxTokens: Int, callback: TokenCallback)
    external fun nativeUnload(handle: Long)

    fun isLoaded(): Boolean = engineMode == EngineMode.NATIVE_GGUF || engineMode == EngineMode.DEMO_FALLBACK

    fun isNativeLoaded(): Boolean = engineMode == EngineMode.NATIVE_GGUF

    fun loadModel(path: String, nThreads: Int = 4): Boolean {
        val file = File(path)
        if (!file.exists()) {
            statusDetailMessage = "File does not exist at path: $path"
            enableDemoFallbackMode(statusDetailMessage)
            return false
        }

        if (file.length() == 0L) {
            statusDetailMessage = "File at path is empty (0 bytes): $path"
            enableDemoFallbackMode(statusDetailMessage)
            return false
        }

        return try {
            val handle = nativeLoadModel(path, nThreads)
            if (handle != 0L) {
                modelHandle = handle
                engineMode = EngineMode.NATIVE_GGUF
                currentModelPath = path
                statusDetailMessage = "Native GGUF model loaded successfully (${file.name}, ${file.length() / (1024 * 1024)} MB)"
                true
            } else {
                statusDetailMessage = "Native nativeLoadModel returned 0 handle for $path"
                enableDemoFallbackMode(statusDetailMessage)
                false
            }
        } catch (e: UnsatisfiedLinkError) {
            statusDetailMessage = "Native library libllama_android.so link issue: ${e.message}"
            enableDemoFallbackMode(statusDetailMessage)
            false
        } catch (e: Exception) {
            statusDetailMessage = "Error loading model: ${e.message}"
            enableDemoFallbackMode(statusDetailMessage)
            false
        }
    }

    fun enableDemoFallbackMode(reason: String = "Demo mode active") {
        engineMode = EngineMode.DEMO_FALLBACK
        statusDetailMessage = "Demo Mode: $reason"
    }

    fun generate(prompt: String, maxTokens: Int = 512): String {
        return when (engineMode) {
            EngineMode.NATIVE_GGUF -> {
                try {
                    nativeGenerate(modelHandle, prompt, maxTokens)
                } catch (e: Exception) {
                    generateSocraticDemoResponse(prompt)
                }
            }
            EngineMode.DEMO_FALLBACK -> generateSocraticDemoResponse(prompt)
            EngineMode.NOT_LOADED -> generateSocraticDemoResponse(prompt)
        }
    }

    fun generateStream(prompt: String, maxTokens: Int = 512, callback: TokenCallback) {
        when (engineMode) {
            EngineMode.NATIVE_GGUF -> {
                try {
                    nativeGenerateStream(modelHandle, prompt, maxTokens, callback)
                } catch (e: Exception) {
                    streamDemoResponse(prompt, callback)
                }
            }
            EngineMode.DEMO_FALLBACK, EngineMode.NOT_LOADED -> {
                streamDemoResponse(prompt, callback)
            }
        }
    }

    private fun streamDemoResponse(prompt: String, callback: TokenCallback) {
        CoroutineScope(Dispatchers.Default).launch {
            val responseText = generateSocraticDemoResponse(prompt)
            val words = responseText.split(" ")
            for (word in words) {
                callback.onToken("$word ")
                delay(40)
            }
            callback.onComplete()
        }
    }

    private fun generateSocraticDemoResponse(prompt: String): String {
        val promptLower = prompt.lowercase()

        val isReveal = promptLower.contains("explicitly asked for the answer") || promptLower.contains("reveal")
        if (isReveal) {
            return "Here is the full worked solution based on the textbook context:\n1. Identify the given values and equations.\n2. Substitute the given values into the formula.\n3. Solve step-by-step to arrive at the final result."
        }

        if (promptLower.contains("quiz")) {
            return """
{
  "questions": [
    {
      "question": "What is the primary formula discussed in this textbook section?",
      "options": ["Standard Formula A", "Alternative Formula B", "Theorem C", "None of the above"],
      "correctIndex": 0,
      "explanation": "Standard Formula A is directly introduced in the textbook excerpt."
    },
    {
      "question": "Which variable represents the main rate of change?",
      "options": ["Velocity (v)", "Acceleration (a)", "Time (t)", "Distance (d)"],
      "correctIndex": 0,
      "explanation": "Velocity represents the rate of change of distance over time."
    }
  ]
}
            """.trimIndent()
        }

        return "Good question! Let's examine the textbook context. What is the first step or given formula for this problem?"
    }

    fun unload() {
        if (modelHandle != 0L) {
            try {
                nativeUnload(modelHandle)
            } catch (e: Exception) {
                // Ignore
            }
            modelHandle = 0L
        }
        engineMode = EngineMode.NOT_LOADED
        currentModelPath = null
        statusDetailMessage = "Model unloaded."
    }

    companion object {
        init {
            try {
                System.loadLibrary("llama_android")
            } catch (e: UnsatisfiedLinkError) {
                // Native library will be loaded when available
            }
        }
    }
}
