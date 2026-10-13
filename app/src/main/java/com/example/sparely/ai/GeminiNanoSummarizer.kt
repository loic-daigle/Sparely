package com.example.sparely.ai

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.SummarizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/**
 * Gemini Nano through the ML Kit GenAI Summarization API. It can only summarize, but it runs on
 * phones whose smaller Gemini Nano doesn't offer the Prompt API, like the Pixel 9a. Its languages
 * are English, Japanese and Korean, so Sparely asks for English.
 */
class GeminiNanoSummarizer(context: Context) : LocalSummarizer {

    private val appContext = context.applicationContext

    private val client by lazy {
        Summarization.getClient(
            SummarizerOptions.builder(appContext)
                .setInputType(SummarizerOptions.InputType.ARTICLE)
                .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
                .setLanguage(SummarizerOptions.Language.ENGLISH)
                .build()
        )
    }

    override suspend fun availability(): ModelAvailability {
        val status = try {
            client.checkFeatureStatus().await()
        } catch (e: GenAiException) {
            Log.w(TAG, "checkFeatureStatus failed: ${describe(e)}", e)
            if (isNotAvailable(e)) return ModelAvailability.UNAVAILABLE
            throw IllegalStateException(describe(e), e)
        }
        return when (status) {
            FeatureStatus.AVAILABLE -> ModelAvailability.AVAILABLE
            FeatureStatus.DOWNLOADABLE -> ModelAvailability.DOWNLOADABLE
            FeatureStatus.DOWNLOADING -> ModelAvailability.DOWNLOADING
            else -> ModelAvailability.UNAVAILABLE
        }
    }

    override fun download(): Flow<ModelDownloadEvent> = callbackFlow {
        val future = client.downloadFeature(object : DownloadCallback {
            override fun onDownloadStarted(bytesToDownload: Long) {
                trySend(ModelDownloadEvent.Started)
            }

            override fun onDownloadProgress(totalBytesDownloaded: Long) {
                trySend(ModelDownloadEvent.Progress(totalBytesDownloaded))
            }

            override fun onDownloadCompleted() {
                trySend(ModelDownloadEvent.Completed)
                this@callbackFlow.close()
            }

            override fun onDownloadFailed(e: GenAiException) {
                Log.w(TAG, "download failed: ${describe(e)}", e)
                trySend(ModelDownloadEvent.Failed(IllegalStateException(describe(e), e)))
                this@callbackFlow.close()
            }
        })
        // The future also fails if the download can't start, sometimes without a callback.
        launch {
            try {
                future.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "downloadFeature failed", e)
                val message = (e as? GenAiException)?.let(::describe) ?: e.message
                trySend(ModelDownloadEvent.Failed(IllegalStateException(message, e)))
            }
            this@callbackFlow.close()
        }
        awaitClose()
    }

    override suspend fun summarize(text: String): String? =
        client.runInference(SummarizationRequest.builder(text).build()).await().summary

    override fun close() {
        client.close()
    }

    private companion object {
        const val TAG = "GeminiNanoSummarizer"
    }
}
