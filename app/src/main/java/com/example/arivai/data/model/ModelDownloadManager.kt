package com.example.arivai.data.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

sealed class DownloadState {
    data class Progress(val bytesDownloaded: Long, val totalBytes: Long, val percentage: Int) : DownloadState()
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}

@Singleton
class ModelDownloadManager @Inject constructor(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    val modelsDir: File
        get() = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    fun getModelFile(filename: String): File = File(modelsDir, filename)

    fun isModelDownloaded(filename: String, expectedMinSizeMb: Long = 10): Boolean {
        val file = getModelFile(filename)
        return file.exists() && (file.length() >= expectedMinSizeMb * 1024 * 1024)
    }

    fun downloadFile(url: String, targetFilename: String): Flow<DownloadState> = flow {
        val targetFile = getModelFile(targetFilename)
        var existingLength = if (targetFile.exists()) targetFile.length() else 0L

        val requestBuilder = Request.Builder().url(url)
        if (existingLength > 0) {
            requestBuilder.header("Range", "bytes=$existingLength-")
        }

        try {
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                emit(DownloadState.Error("Server returned code ${response.code}"))
                return@flow
            }

            val body = response.body
            if (body == null) {
                emit(DownloadState.Error("Response body is empty"))
                return@flow
            }

            val contentLength = body.contentLength()
            val totalBytes = if (response.code == 206) existingLength + contentLength else contentLength

            val input = body.byteStream()
            val output = if (response.code == 206) {
                RandomAccessFile(targetFile, "rw").apply { seek(existingLength) }
            } else {
                FileOutputStream(targetFile)
            }

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var bytesDownloaded = if (response.code == 206) existingLength else 0L

            if (output is RandomAccessFile) {
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    bytesDownloaded += bytesRead
                    val percent = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
                    emit(DownloadState.Progress(bytesDownloaded, totalBytes, percent))
                }
                output.close()
            } else if (output is FileOutputStream) {
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    bytesDownloaded += bytesRead
                    val percent = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
                    emit(DownloadState.Progress(bytesDownloaded, totalBytes, percent))
                }
                output.close()
            }

            input.close()
            emit(DownloadState.Completed)
        } catch (e: Exception) {
            emit(DownloadState.Error("Download failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
}
