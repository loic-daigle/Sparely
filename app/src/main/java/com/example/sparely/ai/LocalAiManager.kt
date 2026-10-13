package com.example.sparely.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the UI should show for the on-device assistant. */
sealed interface LocalAiState {
    data object Checking : LocalAiState

    /** The phone can't run the model. AI features stay hidden. */
    data object Unsupported : LocalAiState

    /** Supported but not downloaded. Ask the user before calling [LocalAiManager.startDownload]. */
    data object NeedsDownload : LocalAiState
    data class Downloading(val bytesDownloaded: Long) : LocalAiState
    data object Ready : LocalAiState

    /** Checking or downloading failed. [LocalAiManager.refresh] checks again from the start. */
    data class Error(val message: String?) : LocalAiState
}

sealed interface LocalAiResult {
    data class Success(val text: String) : LocalAiResult
    data object NotReady : LocalAiResult
    data object PromptTooLong : LocalAiResult
    data class Failed(val message: String?) : LocalAiResult
}

/**
 * Detects whether the phone supports the on-device model, handles its download and runs prompts
 * one at a time. Nothing here touches the network itself: the model download is done by Google
 * Play services, and prompts never leave the phone.
 */
class LocalAiManager(
    private val model: LocalTextModel,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<LocalAiState>(LocalAiState.Checking)
    val state: StateFlow<LocalAiState> = _state.asStateFlow()

    private val inference = Mutex()
    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    /**
     * Checks again whether the model is usable. Call it when an AI screen opens: a phone that was
     * unsupported can become supported once it fetches a newer configuration.
     */
    fun refresh() {
        if (downloadJob?.isActive == true || checkJob?.isActive == true) return
        checkJob = scope.launch {
            // A ready model stays usable while it's checked again.
            if (_state.value != LocalAiState.Ready) _state.value = LocalAiState.Checking
            val availability = try {
                model.availability()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = LocalAiState.Error(e.message)
                return@launch
            }
            when (availability) {
                ModelAvailability.AVAILABLE -> _state.value = LocalAiState.Ready
                ModelAvailability.DOWNLOADABLE -> _state.value = LocalAiState.NeedsDownload
                ModelAvailability.UNAVAILABLE -> _state.value = LocalAiState.Unsupported
                // Started earlier, possibly by another app. Follow it to know when it's done.
                ModelAvailability.DOWNLOADING -> launchDownload()
            }
        }
    }

    /** Starts the model download. Only acts in [LocalAiState.NeedsDownload]; after an error, call [refresh]. */
    fun startDownload() {
        if (_state.value != LocalAiState.NeedsDownload || downloadJob?.isActive == true) return
        launchDownload()
    }

    private fun launchDownload() {
        _state.value = LocalAiState.Downloading(0)
        downloadJob = scope.launch {
            try {
                model.download().collect { event ->
                    when (event) {
                        ModelDownloadEvent.Started -> Unit
                        is ModelDownloadEvent.Progress ->
                            _state.value = LocalAiState.Downloading(event.bytesDownloaded)
                        ModelDownloadEvent.Completed -> _state.value = LocalAiState.Ready
                        is ModelDownloadEvent.Failed ->
                            _state.value = LocalAiState.Error(event.cause?.message)
                    }
                }
                // The flow can end without a final event. Ask the model what happened.
                if (_state.value is LocalAiState.Downloading) {
                    _state.value = when (model.availability()) {
                        ModelAvailability.AVAILABLE -> LocalAiState.Ready
                        else -> LocalAiState.Error(null)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = LocalAiState.Error(e.message)
            }
        }
    }

    /** Loads the model ahead of the first prompt. Safe to skip; failures are ignored. */
    suspend fun warmup() {
        if (_state.value != LocalAiState.Ready) return
        try {
            model.warmup()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /**
     * Runs [prompt] and returns a cleaned-up answer. Prompts are queued so only one runs at a time.
     * Must be called while the app is in the foreground: AICore rejects background inference.
     */
    suspend fun generate(
        prompt: String,
        options: GenerationOptions = GenerationOptions(),
        maxAnswerChars: Int = AnswerFormatter.DEFAULT_MAX_CHARS
    ): LocalAiResult {
        if (_state.value != LocalAiState.Ready) return LocalAiResult.NotReady
        if (prompt.length > MAX_PROMPT_CHARS) return LocalAiResult.PromptTooLong
        return inference.withLock {
            try {
                val text = AnswerFormatter.clean(model.generate(prompt, options).orEmpty(), maxAnswerChars)
                if (text.isEmpty()) LocalAiResult.Failed(null) else LocalAiResult.Success(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LocalAiResult.Failed(e.message)
            }
        }
    }

    fun close() {
        model.close()
    }

    companion object {
        /**
         * Gemini Nano takes under 4000 input tokens. A token is about 4 characters of English but
         * fewer of French, so this stays well below the limit for both.
         */
        const val MAX_PROMPT_CHARS = 8_000
    }
}
