package com.example.arivai.data.rag

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

class VectorStore {
    private val chunks = mutableListOf<Chunk>()

    fun addChunk(chunk: Chunk) {
        chunks.add(chunk)
    }

    fun addChunks(newChunks: List<Chunk>) {
        chunks.addAll(newChunks)
    }

    fun clear() {
        chunks.clear()
    }

    fun getChunks(): List<Chunk> = chunks.toList()

    fun topK(queryEmbedding: FloatArray, k: Int = 4): List<Chunk> {
        if (chunks.isEmpty()) return emptyList()

        return chunks
            .map { chunk -> Pair(chunk, cosineSimilarity(queryEmbedding, chunk.embedding)) }
            .sortedByDescending { it.second }
            .take(k)
            .map { it.first }
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0.0f

        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt((normA * normB).toDouble()).toFloat()
        return if (denominator > 0) dotProduct / denominator else 0.0f
    }

    fun saveToDisk(path: String) {
        try {
            val file = File(path)
            file.parentFile?.mkdirs()
            val jsonArray = JSONArray()
            for (chunk in chunks) {
                val obj = JSONObject()
                obj.put("id", chunk.id)
                obj.put("text", chunk.text)
                obj.put("pageNumber", chunk.pageNumber)
                val embArray = JSONArray()
                for (v in chunk.embedding) {
                    embArray.put(v.toDouble())
                }
                obj.put("embedding", embArray)
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadFromDisk(path: String) {
        try {
            val file = File(path)
            if (!file.exists()) return
            val jsonText = file.readText()
            val jsonArray = JSONArray(jsonText)
            chunks.clear()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id")
                val text = obj.optString("text")
                val pageNumber = obj.optInt("pageNumber")
                val embArray = obj.getJSONArray("embedding")
                val embedding = FloatArray(embArray.length())
                for (j in 0 until embArray.length()) {
                    embedding[j] = embArray.getDouble(j).toFloat()
                }
                chunks.add(Chunk(id = id, text = text, pageNumber = pageNumber, embedding = embedding))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
