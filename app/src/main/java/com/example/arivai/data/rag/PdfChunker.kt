package com.example.arivai.data.rag

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

class PdfChunker(private val context: Context) {

    init {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun extractTextAndChunk(inputStream: InputStream, chunkSizeWords: Int = 350, overlapWords: Int = 40): List<UnembeddedChunk> {
        val resultChunks = mutableListOf<UnembeddedChunk>()
        try {
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val totalPages = document.numberOfPages

            for (page in 1..totalPages) {
                stripper.startPage = page
                stripper.endPage = page
                val pageText = stripper.getText(document).trim()

                if (pageText.isNotBlank()) {
                    val pageChunks = chunkText(pageText, page, chunkSizeWords, overlapWords)
                    resultChunks.addAll(pageChunks)
                }
            }
            document.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return resultChunks
    }

    fun chunkText(text: String, pageNumber: Int, chunkSizeWords: Int = 350, overlapWords: Int = 40): List<UnembeddedChunk> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        val chunks = mutableListOf<UnembeddedChunk>()
        var step = chunkSizeWords - overlapWords
        if (step <= 0) step = chunkSizeWords

        var i = 0
        while (i < words.size) {
            val end = minOf(i + chunkSizeWords, words.size)
            val chunkWords = words.subList(i, end)
            val chunkString = chunkWords.joinToString(" ")
            chunks.add(UnembeddedChunk(text = chunkString, pageNumber = pageNumber))

            if (end == words.size) break
            i += step
        }
        return chunks
    }

    data class UnembeddedChunk(
        val text: String,
        val pageNumber: Int
    )
}
