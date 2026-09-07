package com.example.arivai.data.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.nio.LongBuffer
import kotlin.math.sqrt

class EmbeddingEngine(private val context: Context) {
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    fun loadModel(modelFile: File): Boolean {
        return try {
            ortEnv = OrtEnvironment.getEnvironment()
            ortSession = ortEnv?.createSession(modelFile.absolutePath)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isLoaded(): Boolean = ortSession != null

    fun embed(text: String): FloatArray {
        if (ortSession == null || ortEnv == null) {
            return generateSimpleEmbedding(text)
        }

        return try {
            val tokens = simpleTokenize(text)
            val shape = longArrayOf(1, tokens.size.toLong())
            val inputBuffer = LongBuffer.wrap(tokens.map { it.toLong() }.toLongArray())
            val maskBuffer = LongBuffer.wrap(LongArray(tokens.size) { 1L })

            val inputTensor = OnnxTensor.createTensor(ortEnv, inputBuffer, shape)
            val maskTensor = OnnxTensor.createTensor(ortEnv, maskBuffer, shape)

            val inputs = mapOf(
                "input_ids" to inputTensor,
                "attention_mask" to maskTensor
            )

            val results = ortSession!!.run(inputs)
            val outputTensor = results[0].value as Array<Array<FloatArray>>
            val embedding = FloatArray(384)

            // Mean pooling
            val numTokens = tokens.size
            for (i in 0 until numTokens) {
                for (j in 0 until 384) {
                    embedding[j] += outputTensor[0][i][j]
                }
            }
            for (j in 0 until 384) {
                embedding[j] /= numTokens.toFloat()
            }

            inputTensor.close()
            maskTensor.close()
            results.close()

            normalize(embedding)
        } catch (e: Exception) {
            generateSimpleEmbedding(text)
        }
    }

    private fun simpleTokenize(text: String): List<Int> {
        val words = text.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val ids = mutableListOf(101) // [CLS]
        for (word in words) {
            val hash = (word.hashCode() and 0x7FFFFFFF) % 30000 + 1000
            ids.add(hash)
            if (ids.size >= 126) break
        }
        ids.add(102) // [SEP]
        return ids
    }

    private fun generateSimpleEmbedding(text: String): FloatArray {
        val vector = FloatArray(384)
        val words = text.lowercase().split(Regex("\\s+"))
        for (i in words.indices) {
            val wordHash = words[i].hashCode()
            val index = Math.abs(wordHash) % 384
            vector[index] += 1.0f
        }
        return normalize(vector)
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var sumSquare = 0.0f
        for (v in vector) sumSquare += v * v
        val norm = sqrt(sumSquare.toDouble()).toFloat()
        if (norm > 0.0f) {
            for (i in vector.indices) vector[i] /= norm
        }
        return vector
    }
}
