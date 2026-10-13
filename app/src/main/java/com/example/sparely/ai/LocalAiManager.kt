package com.example.sparely.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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

    /** The reply so far, while the model is still writing. A stream ends with another result. */
    data class Partial(val text: String) : LocalAiResult
    data object NotReady : LocalAiResult
    data object PromptTooLong : LocalAiResult
    data class Failed(val message: String?) : LocalAiResult
}

/**
 * Detects what the phone's on-device AI can do, handles model downloads and runs requests one at
 * a time. Nothing here touches the network itself: models are downloaded by Google Play services,
 * and prompts never leave the phone.
 *
 * Two engines are tried. [promptModel] takes any prompt, so it handles both summaries and
 * questions. Phones without it may still have [summarizer], which can only summarize, in English.
 */
class LocalAiManager(
    private val promptModel: LocalTextModel,
    private val scope: CoroutineScope,
    private val summarizer: LocalSummarizer? = null
) {
    private val _state = MutableStateFlow<LocalAiState>(LocalAiState.Checking)

    /** The best of both engines. Ready means summaries work; see [canAnswerQuestions] for questions. */
    val state: StateFlow<LocalAiState> = _state.asStateFlow()

    private val prompt = ModelSlot(promptModel, scope, ::updateState)
    private val summary = summarizer?.let { ModelSlot(it, scope, ::updateState) }

    /** State of the engine that answers questions, which needs the full Prompt API. */
    val promptState: StateFlow<LocalAiState> = prompt.state

    val canAnswerQuestions: Boolean get() = prompt.state.value == LocalAiState.Ready

    private val inference = Mutex()

    /**
     * Checks again what the phone supports. Call it when an AI screen opens: a phone that was
     * unsupported can become supported once it fetches a newer configuration.
     */
    fun refresh() {
        prompt.refresh()
        summary?.refresh()
    }

    /**
     * Downloads the most capable engine waiting for it, and only that one: the summarizer isn't
     * needed when the prompt model can be downloaded instead.
     */
    fun startDownload() {
        when {
            prompt.state.value == LocalAiState.NeedsDownload -> prompt.startDownload()
            prompt.state.value != LocalAiState.Ready -> summary?.startDownload()
        }
    }

    /** Loads the prompt model ahead of the first request. Safe to skip; failures are ignored. */
    suspend fun warmup() {
        if (!canAnswerQuestions) return
        try {
            promptModel.warmup()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /**
     * Runs [prompt] on the full model and returns a cleaned-up answer. Requests are queued so only
     * one runs at a time. Must be called while the app is in the foreground: AICore rejects
     * background inference.
     */
    suspend fun generate(
        prompt: String,
        options: GenerationOptions = GenerationOptions(),
        maxAnswerChars: Int = AnswerFormatter.DEFAULT_MAX_CHARS
    ): LocalAiResult {
        if (!canAnswerQuestions) return LocalAiResult.NotReady
        if (prompt.length > MAX_PROMPT_CHARS) return LocalAiResult.PromptTooLong
        return run("prompt:$prompt", maxAnswerChars) { promptModel.generate(prompt, options) }
    }

    /**
     * Summarizes [facts] with whichever engine the phone has. The full model follows [task] and
     * writes in [replyLanguage]; the summarizer ignores both and writes English.
     */
    suspend fun summarize(
        facts: List<Fact>,
        replyLanguage: String = "English",
        task: String = SUMMARY_TASK,
        maxAnswerChars: Int = AnswerFormatter.DEFAULT_MAX_CHARS
    ): LocalAiResult {
        if (canAnswerQuestions) {
            return generate(
                FinancePrompt.build(task, facts, replyLanguage = replyLanguage),
                options = REPLY_OPTIONS,
                maxAnswerChars = maxAnswerChars
            )
        }
        val summarizer = summarizer
        if (summarizer == null || summary?.state?.value != LocalAiState.Ready) return LocalAiResult.NotReady
        val text = FinancePrompt.factsAsText(facts)
        if (text.length > MAX_PROMPT_CHARS) return LocalAiResult.PromptTooLong
        return run("summary:$text", maxAnswerChars) { summarizer.summarize(text) }
    }

    /** Answers the user's [question] from [facts] only. Needs the full model ([canAnswerQuestions]). */
    suspend fun ask(
        facts: List<Fact>,
        question: String,
        replyLanguage: String = "English",
        maxAnswerChars: Int = AnswerFormatter.DEFAULT_MAX_CHARS
    ): LocalAiResult = generate(
        FinancePrompt.build(QUESTION_TASK, facts, question = question, replyLanguage = replyLanguage),
        options = REPLY_OPTIONS,
        maxAnswerChars = maxAnswerChars
    )

    /**
     * Like [summarize], but shows the reply as it's written: [LocalAiResult.Partial] while the
     * model writes, then one final result. On the CPU a reply can take a minute, so this keeps the
     * screen from looking stuck. The summarizer can't stream, so it gives just the final result.
     */
    fun summarizeStream(
        facts: List<Fact>,
        replyLanguage: String = "English",
        task: String = SUMMARY_TASK,
        maxAnswerChars: Int = AnswerFormatter.DEFAULT_MAX_CHARS
    ): Flow<LocalAiResult> =
        if (canAnswerQuestions) {
            stream(FinancePrompt.build(task, facts, replyLanguage = replyLanguage), maxAnswerChars)
        } else {
            flow { emit(summarize(facts, replyLanguage, task, maxAnswerChars)) }
        }

    /** Like [ask], but shows the answer as it's written. */
    fun askStream(
        facts: List<Fact>,
        question: String,
        replyLanguage: String = "English",
        maxAnswerChars: Int = AnswerFormatter.DEFAULT_MAX_CHARS
    ): Flow<LocalAiResult> =
        stream(FinancePrompt.build(QUESTION_TASK, facts, question = question, replyLanguage = replyLanguage), maxAnswerChars)

    private fun stream(prompt: String, maxAnswerChars: Int): Flow<LocalAiResult> {
        if (!canAnswerQuestions) return flowOf(LocalAiResult.NotReady)
        if (prompt.length > MAX_PROMPT_CHARS) return flowOf(LocalAiResult.PromptTooLong)
        val key = "prompt:$prompt"
        return flow {
            synchronized(cache) { cache[key] }?.let {
                emit(LocalAiResult.Success(it))
                return@flow
            }
            inference.withLock {
                val raw = StringBuilder()
                val result = try {
                    promptModel.generateStream(prompt, REPLY_OPTIONS).collect { chunk ->
                        raw.append(chunk)
                        val soFar = AnswerFormatter.clean(raw.toString(), maxAnswerChars)
                        if (soFar.isNotEmpty()) emit(LocalAiResult.Partial(soFar))
                    }
                    val text = AnswerFormatter.clean(raw.toString(), maxAnswerChars)
                    if (text.isEmpty()) {
                        LocalAiResult.Failed(null)
                    } else {
                        synchronized(cache) { cache[key] = text }
                        LocalAiResult.Success(text)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LocalAiResult.Failed(e.message)
                }
                emit(result)
            }
        }
    }

    /** Answers already given for the same request, so reopening a screen doesn't run the model again. */
    private val cache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > CACHE_SIZE
    }

    /** The cached answer for a request [summarize] or [ask] already answered, if any. */
    fun cachedSummary(facts: List<Fact>, replyLanguage: String = "English", task: String = SUMMARY_TASK): String? =
        synchronized(cache) {
            if (canAnswerQuestions) cache["prompt:" + FinancePrompt.build(task, facts, replyLanguage = replyLanguage)]
            else cache["summary:" + FinancePrompt.factsAsText(facts)]
        }

    private suspend fun run(key: String, maxAnswerChars: Int, request: suspend () -> String?): LocalAiResult {
        synchronized(cache) { cache[key] }?.let { return LocalAiResult.Success(it) }
        return inference.withLock {
            try {
                val text = AnswerFormatter.clean(request().orEmpty(), maxAnswerChars)
                if (text.isEmpty()) {
                    LocalAiResult.Failed(null)
                } else {
                    synchronized(cache) { cache[key] = text }
                    LocalAiResult.Success(text)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LocalAiResult.Failed(e.message)
            }
        }
    }

    private fun updateState() {
        _state.value = combine(prompt.state.value, summary?.state?.value ?: LocalAiState.Unsupported)
    }

    fun close() {
        promptModel.close()
        summarizer?.close()
    }

    companion object {
        /**
         * Gemini Nano takes under 4000 input tokens. A token is about 4 characters of English but
         * fewer of French, so this stays well below the limit for both.
         */
        const val MAX_PROMPT_CHARS = 8_000

        const val SUMMARY_TASK = "Summarize how the user's finances are going in 2-3 short sentences."
        private const val QUESTION_TASK =
            "Answer the user's question in 1-3 short sentences, using only the facts."
        private const val CACHE_SIZE = 32

        /** 2-3 sentences need far fewer than the 256-token maximum, and each token costs time on the CPU. */
        private val REPLY_OPTIONS = GenerationOptions(maxOutputTokens = 160)

        /** The most useful of two engine states: a usable engine beats one that's still on its way. */
        internal fun combine(a: LocalAiState, b: LocalAiState): LocalAiState {
            val both = listOf(a, b)
            return when {
                LocalAiState.Ready in both -> LocalAiState.Ready
                both.any { it is LocalAiState.Downloading } -> LocalAiState.Downloading(
                    both.filterIsInstance<LocalAiState.Downloading>().sumOf { it.bytesDownloaded }
                )
                LocalAiState.NeedsDownload in both -> LocalAiState.NeedsDownload
                LocalAiState.Checking in both -> LocalAiState.Checking
                else -> both.firstOrNull { it is LocalAiState.Error } ?: LocalAiState.Unsupported
            }
        }
    }
}

/** Support check and download for one engine. */
private class ModelSlot(
    private val model: DownloadableModel,
    private val scope: CoroutineScope,
    private val onChange: () -> Unit
) {
    private val _state = MutableStateFlow<LocalAiState>(LocalAiState.Checking)
    val state: StateFlow<LocalAiState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    private fun set(value: LocalAiState) {
        _state.value = value
        onChange()
    }

    fun refresh() {
        if (downloadJob?.isActive == true || checkJob?.isActive == true) return
        checkJob = scope.launch {
            // A ready model stays usable while it's checked again.
            if (_state.value != LocalAiState.Ready) set(LocalAiState.Checking)
            val availability = try {
                model.availability()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                set(LocalAiState.Error(e.message))
                return@launch
            }
            when (availability) {
                ModelAvailability.AVAILABLE -> set(LocalAiState.Ready)
                ModelAvailability.DOWNLOADABLE -> set(LocalAiState.NeedsDownload)
                ModelAvailability.UNAVAILABLE -> set(LocalAiState.Unsupported)
                // Started earlier, possibly by another app. Follow it to know when it's done.
                ModelAvailability.DOWNLOADING -> launchDownload()
            }
        }
    }

    /** Only acts in [LocalAiState.NeedsDownload]; after an error, call [refresh]. */
    fun startDownload() {
        if (_state.value != LocalAiState.NeedsDownload || downloadJob?.isActive == true) return
        launchDownload()
    }

    private fun launchDownload() {
        set(LocalAiState.Downloading(0))
        downloadJob = scope.launch {
            try {
                model.download().collect { event ->
                    when (event) {
                        ModelDownloadEvent.Started -> Unit
                        is ModelDownloadEvent.Progress -> set(LocalAiState.Downloading(event.bytesDownloaded))
                        ModelDownloadEvent.Completed -> set(LocalAiState.Ready)
                        is ModelDownloadEvent.Failed -> set(LocalAiState.Error(event.cause?.message))
                    }
                }
                // The flow can end without a final event. Ask the model what happened.
                if (_state.value is LocalAiState.Downloading) {
                    set(
                        when (model.availability()) {
                            ModelAvailability.AVAILABLE -> LocalAiState.Ready
                            else -> LocalAiState.Error(null)
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                set(LocalAiState.Error(e.message))
            }
        }
    }
}
