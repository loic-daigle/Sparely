package com.example.sparely.ai

import kotlinx.coroutines.flow.Flow

/** Whether the phone can run the on-device model right now. */
enum class ModelAvailability {
    /** Downloaded and ready to use. */
    AVAILABLE,

    /** Supported, but the model must be downloaded first. */
    DOWNLOADABLE,

    /** Supported, and a download is already in progress. */
    DOWNLOADING,

    /** Not supported, or the phone hasn't fetched the configuration that enables it yet. */
    UNAVAILABLE
}

sealed interface ModelDownloadEvent {
    data object Started : ModelDownloadEvent
    data class Progress(val bytesDownloaded: Long) : ModelDownloadEvent
    data object Completed : ModelDownloadEvent
    data class Failed(val cause: Throwable?) : ModelDownloadEvent
}

/** Sampling settings. Low values keep answers close to the facts in the prompt. */
data class GenerationOptions(
    val temperature: Float = 0.2f,
    val topK: Int = 10
)

/**
 * A text model that runs entirely on the phone. Kept separate from ML Kit so [LocalAiManager] and
 * anything built on it can be tested without a device.
 */
interface LocalTextModel {
    suspend fun availability(): ModelAvailability
    fun download(): Flow<ModelDownloadEvent>

    /** Loads the model into memory ahead of time so the first answer comes back faster. */
    suspend fun warmup()

    /** Returns the model's raw reply, or null if it produced none. */
    suspend fun generate(prompt: String, options: GenerationOptions): String?
    fun close()
}
