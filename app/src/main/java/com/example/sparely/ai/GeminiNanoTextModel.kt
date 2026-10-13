package com.example.sparely.ai

import com.google.mlkit.genai.common.DownloadStatus
import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * Gemini Nano through the ML Kit GenAI Prompt API. AICore picks the model variant that fits the
 * phone, so this class doesn't need to know which one it gets. Everything runs on the device.
 */
class GeminiNanoTextModel : LocalTextModel {

    private val client by lazy { Generation.getClient() }

    override suspend fun availability(): ModelAvailability {
        val status = try {
            client.checkStatus()
        } catch (e: GenAiException) {
            Log.w(TAG, "checkStatus failed: ${describe(e)}", e)
            // Phones with Gemini Nano but without this API (like the Pixel 9a, whose smaller model only
            // serves some ML Kit features) throw here instead of returning UNAVAILABLE.
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

    override fun download(): Flow<ModelDownloadEvent> = client.download().mapNotNull { status ->
        when (status) {
            is DownloadStatus.DownloadStarted -> ModelDownloadEvent.Started
            is DownloadStatus.DownloadProgress -> ModelDownloadEvent.Progress(status.totalBytesDownloaded)
            is DownloadStatus.DownloadCompleted -> ModelDownloadEvent.Completed
            is DownloadStatus.DownloadFailed -> {
                Log.w(TAG, "download failed: ${describe(status.e)}", status.e)
                ModelDownloadEvent.Failed(IllegalStateException(describe(status.e), status.e))
            }
            else -> null
        }
    }

    override suspend fun warmup() {
        client.warmup()
    }

    override suspend fun generate(prompt: String, options: GenerationOptions): String? {
        val request = generateContentRequest(TextPart(prompt)) {
            temperature = options.temperature
            topK = options.topK
            maxOutputTokens = options.maxOutputTokens
        }
        return client.generateContent(request).candidates.firstOrNull()?.text
    }

    override fun close() {
        client.close()
    }

    private companion object {
        const val TAG = "GeminiNano"
    }
}
