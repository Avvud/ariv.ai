package com.example.arivai.domain

import com.example.arivai.data.embedding.EmbeddingEngine
import com.example.arivai.data.model.EngineMode
import com.example.arivai.data.model.LlamaEngine
import com.example.arivai.data.model.ModelDownloadManager
import com.example.arivai.data.model.TokenCallback
import com.example.arivai.data.rag.Chunk
import com.example.arivai.data.rag.VectorStore
import com.example.arivai.data.socratic.AnswerLeakDetector
import com.example.arivai.data.socratic.InsistGate
import com.example.arivai.util.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TutorRepository @Inject constructor(
    private val llama: LlamaEngine,
    private val embedder: EmbeddingEngine,
    private val vectorStore: VectorStore,
    private val insistGate: InsistGate,
    private val leakDetector: AnswerLeakDetector,
    private val downloadManager: ModelDownloadManager
) {

    private var activeCustomModelPath: String? = null

    fun reloadCustomModel(customPath: String): String {
        llama.unload()
        activeCustomModelPath = customPath
        val file = File(customPath)
        if (file.exists() && file.length() > 0) {
            val loaded = llama.loadModel(file.absolutePath)
            if (loaded) return "Loaded GGUF model: ${file.name}"
        }
        return "Failed to load model at ${file.name}"
    }

    fun ensureModelLoaded(customPath: String? = null): String {
        if (!customPath.isNullOrBlank()) {
            return reloadCustomModel(customPath)
        }

        if (llama.isNativeLoaded()) {
            return "Native GGUF Model Active (${llama.currentModelPath?.let { File(it).name }})"
        }

        // 1. Try selected_model.gguf in modelsDir
        val customFile = downloadManager.getModelFile(Constants.CUSTOM_MODEL_FILENAME)
        if (customFile.exists() && customFile.length() > 10 * 1024 * 1024) {
            val loaded = llama.loadModel(customFile.absolutePath)
            if (loaded) return "Loaded GGUF model: ${customFile.name}"
        }

        // 2. Try default 1.5B model file in modelsDir
        val defaultFile = downloadManager.getModelFile(Constants.MODEL_FILENAME)
        if (defaultFile.exists() && defaultFile.length() > 10 * 1024 * 1024) {
            val loaded = llama.loadModel(defaultFile.absolutePath)
            if (loaded) return "Loaded GGUF model: ${defaultFile.name}"
        }

        // 3. Scan modelsDir for any .gguf file
        val anyModelDirGguf = downloadManager.modelsDir.listFiles()?.firstOrNull { it.extension.equals("gguf", ignoreCase = true) && it.length() > 10 * 1024 * 1024 }
        if (anyModelDirGguf != null) {
            val loaded = llama.loadModel(anyModelDirGguf.absolutePath)
            if (loaded) return "Loaded GGUF model: ${anyModelDirGguf.name}"
        }

        // 4. Fallback to Socratic Demo Engine if no valid native file found
        llama.enableDemoFallbackMode("No native GGUF model file found on device.")
        return "Socratic Engine running (Demo/Rule-Based Mode)"
    }

    fun getEngineStateInfo(): Pair<EngineMode, String> {
        return Pair(llama.engineMode, llama.statusDetailMessage)
    }

    fun ask(
        question: String,
        conversationHistory: List<ChatMessage>,
        questionTopicId: String = "default",
        customModelPath: String? = null
    ): Flow<Pair<String, List<Chunk>>> = callbackFlow {
        ensureModelLoaded(customModelPath)

        val isInsist = insistGate.isInsistPhrase(question)
        val currentInsistCount = if (isInsist) {
            insistGate.registerInsistAttempt(questionTopicId)
        } else {
            insistGate.getInsistCount(questionTopicId)
        }

        val shouldReveal = insistGate.shouldRevealAnswer(questionTopicId)

        // 1. Vector Search
        val queryEmbedding = embedder.embed(question)
        val relevantChunks = vectorStore.topK(queryEmbedding, k = 4)
        val chunkText = if (relevantChunks.isNotEmpty()) {
            relevantChunks.joinToString("\n\n") { "Page ${it.pageNumber}: ${it.text}" }
        } else {
            "No specific textbook context indexed yet."
        }

        val historyText = conversationHistory.takeLast(6).joinToString("\n") {
            "${it.sender}: ${it.text}"
        }

        // 2. Build Prompt
        val prompt = if (shouldReveal) {
            PromptTemplates.revealAnswerPrompt(chunkText, currentInsistCount, question)
        } else {
            PromptTemplates.socraticPrompt(chunkText, historyText, question)
        }

        // 3. Stream Inference
        val fullTextBuilder = StringBuilder()

        llama.generateStream(prompt, maxTokens = 200, object : TokenCallback {
            override fun onToken(token: String) {
                fullTextBuilder.append(token)
                trySend(Pair(stripThinking(fullTextBuilder.toString()), relevantChunks))
            }

            override fun onComplete() {
                var finalOutput = stripThinking(fullTextBuilder.toString())
                if (!shouldReveal) {
                    finalOutput = leakDetector.sanitizeOrFilterHint(finalOutput, shouldReveal)
                }
                trySend(Pair(finalOutput, relevantChunks))
                close()
            }
        })

        awaitClose { }
    }

    fun getVectorStore(): VectorStore = vectorStore

    // Defensive: strips any <think>...</think> block that leaks into the
    // visible output despite the prompt pre-filling an already-closed one.
    // Also handles an unclosed trailing <think> (mid-generation) by cutting
    // everything from that point on, so partial reasoning never flashes
    // on screen while streaming.
    private fun stripThinking(text: String): String {
        val closed = text.replace(Regex("(?s)<think>.*?</think>"), "")
        val openIdx = closed.indexOf("<think>")
        return if (openIdx >= 0) closed.substring(0, openIdx).trim() else closed.trim()
    }
}