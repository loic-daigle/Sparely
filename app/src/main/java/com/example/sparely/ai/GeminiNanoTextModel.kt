package com.example.sparely.ai

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gemini Nano through the ML Kit GenAI Prompt API. Everything runs on the device.
 *
 * AICore offers Gemini Nano in variants, and which ones a phone has depends on its hardware. The
 * default (stable, full) isn't on phones with the smaller model, like the Pixel 9a, which throw
 * "Feature 636 is not available" for it but can offer the fast variant. So [availability] tries
 * each variant in [VARIANTS] and keeps the first the phone offers.
 */
class GeminiNanoTextModel : LocalTextModel {

    private data class Variant(val name: String, val releaseStage: Int, val preference: Int)

    private val lock = Mutex()
    private var client: GenerativeModel? = null
    private var variant: Variant? = null

    override suspend fun availability(): ModelAvailability = lock.withLock {
        // Keep the variant already in use while it still works, so a recheck never closes the
        // client a request is running on.
        val current = client
        val currentVariant = variant
        if (current != null && currentVariant != null) {
            val status = check(current, currentVariant)
            if (status is Checked.Status && status.value != FeatureStatus.UNAVAILABLE) return status.value.toAvailability()
            current.close()
            client = null
            variant = null
            plainRequestsOnly = false
        }

        var failure: Exception? = null
        for (candidate in VARIANTS) {
            val model = Generation.getClient(
                generationConfig {
                    modelConfig = modelConfig {
                        releaseStage = candidate.releaseStage
                        preference = candidate.preference
                    }
                }
            )
            when (val result = check(model, candidate)) {
                is Checked.Status -> if (result.value != FeatureStatus.UNAVAILABLE) {
                    Log.i(TAG, "Using Gemini Nano ${candidate.name} (status ${result.value})")
                    client = model
                    variant = candidate
                    return result.value.toAvailability()
                }
                is Checked.NotOffered -> Unit
                is Checked.Failed -> failure = failure ?: result.error
            }
            model.close()
        }
        // Report a real error over "not supported", so it shows in Settings.
        failure?.let { throw it }
        ModelAvailability.UNAVAILABLE
    }

    private sealed interface Checked {
        data class Status(val value: Int) : Checked
        data object NotOffered : Checked
        data class Failed(val error: Exception) : Checked
    }

    private suspend fun check(model: GenerativeModel, candidate: Variant): Checked = try {
        Checked.Status(model.checkStatus())
    } catch (e: GenAiException) {
        Log.w(TAG, "checkStatus failed for ${candidate.name}: ${describe(e)}")
        if (isNotAvailable(e)) Checked.NotOffered else Checked.Failed(IllegalStateException(describe(e), e))
    }

    private fun Int.toAvailability(): ModelAvailability = when (this) {
        FeatureStatus.AVAILABLE -> ModelAvailability.AVAILABLE
        FeatureStatus.DOWNLOADABLE -> ModelAvailability.DOWNLOADABLE
        FeatureStatus.DOWNLOADING -> ModelAvailability.DOWNLOADING
        else -> ModelAvailability.UNAVAILABLE
    }

    private fun requireClient(): GenerativeModel =
        checkNotNull(client) { "Call availability() before using the model" }

    override fun download(): Flow<ModelDownloadEvent> = requireClient().download().mapNotNull { status ->
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
        requireClient().warmup()
    }

    /** Set once a plain request worked where a tuned one failed, so later requests skip the options. */
    @Volatile
    private var plainRequestsOnly = false

    override suspend fun generate(prompt: String, options: GenerationOptions): String? {
        val client = requireClient()
        if (!plainRequestsOnly) {
            try {
                val request = generateContentRequest(TextPart(prompt)) {
                    temperature = options.temperature
                    topK = options.topK
                    maxOutputTokens = options.maxOutputTokens.coerceIn(1, MAX_OUTPUT_TOKENS)
                }
                return client.generateContent(request).candidates.firstOrNull()?.text
            } catch (e: GenAiException) {
                // Some preview models (like Gemini Nano 4 Fast on the CPU) fail requests that set
                // sampling options. Retry the way AICore's own test screen does: just the prompt.
                Log.w(TAG, "generateContent with options failed on ${variant?.name}: ${describe(e)}", e)
            }
        }
        val text = try {
            client.generateContent(prompt).candidates.firstOrNull()?.text
        } catch (e: GenAiException) {
            Log.w(TAG, "generateContent failed on ${variant?.name}: ${describe(e)}", e)
            throw IllegalStateException(describe(e), e)
        }
        if (!plainRequestsOnly) {
            Log.i(TAG, "Plain requests work on ${variant?.name}; skipping sampling options from now on")
            plainRequestsOnly = true
        }
        return text
    }

    override fun close() {
        client?.close()
        client = null
        variant = null
    }

    private companion object {
        const val TAG = "GeminiNano"

        /**
         * Most capable first. Preview + Full is left out: it can report AVAILABLE and then fail every
         * request (googlesamples/mlkit#1078). Preview variants need the phone to be enrolled in the
         * AICore Developer Preview; without it they're simply not offered.
         */
        val VARIANTS = listOf(
            Variant("stable/full", ModelReleaseStage.STABLE, ModelPreference.FULL),
            Variant("stable/fast", ModelReleaseStage.STABLE, ModelPreference.FAST),
            Variant("preview/fast", ModelReleaseStage.PREVIEW, ModelPreference.FAST)
        )
    }
}
