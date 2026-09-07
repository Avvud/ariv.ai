package com.example.arivai.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.arivai.data.embedding.EmbeddingEngine
import com.example.arivai.data.ocr.OcrHelper
import com.example.arivai.data.rag.Chunk
import com.example.arivai.data.rag.PdfChunker
import com.example.arivai.data.rag.VectorStore
import com.example.arivai.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class LibraryUiState(
    val isIndexing: Boolean = false,
    val indexedChunkCount: Int = 0,
    val statusMessage: String = "No PDF loaded",
    val isReadyForChat: Boolean = false,
    val scannedPagesCount: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val pdfChunker: PdfChunker,
    private val embeddingEngine: EmbeddingEngine,
    private val vectorStore: VectorStore,
    private val ocrHelper: OcrHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val scannedPageTexts = mutableListOf<String>()

    fun processPdfUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = _uiState.value.copy(
                    isIndexing = true,
                    statusMessage = "Reading PDF...",
                    errorMessage = null
                )

                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _uiState.value = _uiState.value.copy(
                        isIndexing = false,
                        errorMessage = "Failed to open PDF file"
                    )
                    return@launch
                }

                val unembeddedChunks = pdfChunker.extractTextAndChunk(inputStream)
                inputStream.close()

                _uiState.value = _uiState.value.copy(
                    statusMessage = "Generating embeddings for ${unembeddedChunks.size} chunks..."
                )

                val newChunks = mutableListOf<Chunk>()
                for ((index, uChunk) in unembeddedChunks.withIndex()) {
                    val embedding = embeddingEngine.embed(uChunk.text)
                    val chunk = Chunk(
                        text = uChunk.text,
                        pageNumber = uChunk.pageNumber,
                        embedding = embedding
                    )
                    newChunks.add(chunk)
                    _uiState.value = _uiState.value.copy(
                        indexedChunkCount = index + 1
                    )
                }

                vectorStore.clear()
                vectorStore.addChunks(newChunks)

                // Save index to disk
                val indexPath = File(context.filesDir, Constants.VECTOR_INDEX_FILENAME).absolutePath
                vectorStore.saveToDisk(indexPath)

                _uiState.value = _uiState.value.copy(
                    isIndexing = false,
                    indexedChunkCount = newChunks.size,
                    statusMessage = "PDF Indexed Successfully (${newChunks.size} chunks)",
                    isReadyForChat = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isIndexing = false,
                    errorMessage = "Indexing failed: ${e.message}"
                )
            }
        }
    }

    fun processScannedImage(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = ocrHelper.recognizeText(bitmap)
                if (text.isNotBlank()) {
                    scannedPageTexts.add(text)
                    _uiState.value = _uiState.value.copy(
                        scannedPagesCount = scannedPageTexts.size,
                        statusMessage = "Scanned Page ${scannedPageTexts.size}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "OCR error: ${e.message}"
                )
            }
        }
    }

    fun finalizeScannedPages(context: Context) {
        if (scannedPageTexts.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = _uiState.value.copy(
                    isIndexing = true,
                    statusMessage = "Indexing scanned pages..."
                )

                val fullText = scannedPageTexts.joinToString("\n\n")
                val unembeddedChunks = pdfChunker.chunkText(fullText, pageNumber = 1)

                val newChunks = mutableListOf<Chunk>()
                for (uChunk in unembeddedChunks) {
                    val embedding = embeddingEngine.embed(uChunk.text)
                    newChunks.add(Chunk(text = uChunk.text, pageNumber = uChunk.pageNumber, embedding = embedding))
                }

                vectorStore.clear()
                vectorStore.addChunks(newChunks)

                val indexPath = File(context.filesDir, Constants.VECTOR_INDEX_FILENAME).absolutePath
                vectorStore.saveToDisk(indexPath)

                _uiState.value = _uiState.value.copy(
                    isIndexing = false,
                    indexedChunkCount = newChunks.size,
                    statusMessage = "Scanned Pages Indexed (${newChunks.size} chunks)",
                    isReadyForChat = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isIndexing = false,
                    errorMessage = "Failed to index scanned pages: ${e.message}"
                )
            }
        }
    }
}
