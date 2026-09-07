package com.example.arivai.data.rag

data class Chunk(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val pageNumber: Int,
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Chunk

        if (id != other.id) return false
        if (text != other.text) return false
        if (pageNumber != other.pageNumber) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + pageNumber
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
