package com.example.arivai.di

import android.content.Context
import com.example.arivai.data.embedding.EmbeddingEngine
import com.example.arivai.data.model.LlamaEngine
import com.example.arivai.data.model.ModelDownloadManager
import com.example.arivai.data.ocr.OcrHelper
import com.example.arivai.data.quiz.QuizGenerator
import com.example.arivai.data.rag.PdfChunker
import com.example.arivai.data.rag.VectorStore
import com.example.arivai.data.socratic.AnswerLeakDetector
import com.example.arivai.data.socratic.InsistGate
import com.example.arivai.data.stt.SpeechToTextHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideModelDownloadManager(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): ModelDownloadManager {
        return ModelDownloadManager(context, okHttpClient)
    }

    @Provides
    @Singleton
    fun provideLlamaEngine(): LlamaEngine {
        return LlamaEngine()
    }

    @Provides
    @Singleton
    fun provideEmbeddingEngine(@ApplicationContext context: Context): EmbeddingEngine {
        return EmbeddingEngine(context)
    }

    @Provides
    @Singleton
    fun provideVectorStore(): VectorStore {
        return VectorStore()
    }

    @Provides
    @Singleton
    fun provideInsistGate(): InsistGate {
        return InsistGate()
    }

    @Provides
    @Singleton
    fun provideAnswerLeakDetector(): AnswerLeakDetector {
        return AnswerLeakDetector()
    }

    @Provides
    @Singleton
    fun providePdfChunker(@ApplicationContext context: Context): PdfChunker {
        return PdfChunker(context)
    }

    @Provides
    @Singleton
    fun provideOcrHelper(): OcrHelper {
        return OcrHelper()
    }

    @Provides
    @Singleton
    fun provideSpeechToTextHelper(@ApplicationContext context: Context): SpeechToTextHelper {
        return SpeechToTextHelper(context)
    }

    @Provides
    @Singleton
    fun provideQuizGenerator(llamaEngine: LlamaEngine): QuizGenerator {
        return QuizGenerator(llamaEngine)
    }
}
