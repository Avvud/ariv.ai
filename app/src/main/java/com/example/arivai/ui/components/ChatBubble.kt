package com.example.arivai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.arivai.data.rag.Chunk
import com.example.arivai.domain.ChatMessage
import com.example.arivai.domain.Sender

@Composable
fun ChatBubble(
    message: ChatMessage,
    onChunkClick: (Chunk) -> Unit = {}
) {
    val isUser = message.sender == Sender.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start

    val backgroundColor = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        message.isRevealedAnswer -> Color(0xFFFFEBEE) // Mild red tint for revealed answers
        else -> MaterialTheme.colorScheme.secondaryContainer // Default hint tint
    }

    val textColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        message.isRevealedAnswer -> Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(backgroundColor)
                .padding(12.dp)
        ) {
            Column {
                if (!isUser && message.isRevealedAnswer) {
                    Text(
                        text = "Full Answer Revealed",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB71C1C)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )

                if (!isUser && message.citedChunks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sources:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    message.citedChunks.forEach { chunk ->
                        SourceCitationChip(chunk = chunk, onClick = { onChunkClick(chunk) })
                    }
                }
            }
        }
    }
}
