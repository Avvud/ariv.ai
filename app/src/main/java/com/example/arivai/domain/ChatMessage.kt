package com.example.arivai.domain

import com.example.arivai.data.rag.Chunk

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: Sender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRevealedAnswer: Boolean = false,
    val citedChunks: List<Chunk> = emptyList()
)

enum class Sender {
    USER,
    TUTOR
}
