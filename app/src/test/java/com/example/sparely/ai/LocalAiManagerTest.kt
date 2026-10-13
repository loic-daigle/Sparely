package com.example.sparely.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAiManagerTest {

    private class FakeModel(
        var availability: ModelAvailability = ModelAvailability.AVAILABLE,
        var downloadEvents: Flow<ModelDownloadEvent> = flowOf(ModelDownloadEvent.Completed),
        var reply: () -> String? = { "Hello" }
    ) : LocalTextModel {
        var downloads = 0
        var prompts = mutableListOf<String>()
        override suspend fun availability() = availability
        override fun download(): Flow<ModelDownloadEvent> {
            downloads++
            return downloadEvents
        }
        override suspend fun warmup() = Unit
        override suspend fun generate(prompt: String, options: GenerationOptions): String? {
            prompts += prompt
            return reply()
        }
        override fun close() = Unit
    }

    // Unconfined runs launched work right away, so the fake's results are visible on return.
    private fun manager(model: FakeModel) =
        LocalAiManager(model, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

    @Test
    fun startsCheckingUntilRefreshed() {
        assertEquals(LocalAiState.Checking, manager(FakeModel()).state.value)
    }

    @Test
    fun mapsAvailabilityToState() {
        val expected = mapOf(
            ModelAvailability.AVAILABLE to LocalAiState.Ready,
            ModelAvailability.DOWNLOADABLE to LocalAiState.NeedsDownload,
            ModelAvailability.UNAVAILABLE to LocalAiState.Unsupported
        )
        expected.forEach { (availability, state) ->
            val ai = manager(FakeModel(availability = availability))
            ai.refresh()
            assertEquals(state, ai.state.value)
        }
    }

    @Test
    fun checkFailureBecomesError() {
        val model = object : LocalTextModel by FakeModel() {
            override suspend fun availability(): ModelAvailability = throw IllegalStateException("AICore missing")
        }
        val ai = LocalAiManager(model, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        ai.refresh()
        assertEquals(LocalAiState.Error("AICore missing"), ai.state.value)
    }

    @Test
    fun downloadReportsProgressThenReady() {
        val seen = mutableListOf<LocalAiState>()
        val model = FakeModel(availability = ModelAvailability.DOWNLOADABLE)
        val ai = manager(model)
        model.downloadEvents = flow {
            emit(ModelDownloadEvent.Started)
            emit(ModelDownloadEvent.Progress(500))
            seen += ai.state.value
            emit(ModelDownloadEvent.Completed)
        }
        ai.refresh()
        ai.startDownload()
        assertEquals(listOf(LocalAiState.Downloading(500)), seen)
        assertEquals(LocalAiState.Ready, ai.state.value)
    }

    @Test
    fun downloadOnlyStartsWhenNeeded() {
        val model = FakeModel(availability = ModelAvailability.UNAVAILABLE)
        val ai = manager(model)
        ai.refresh()
        ai.startDownload()
        assertEquals(0, model.downloads)
        assertEquals(LocalAiState.Unsupported, ai.state.value)
    }

    @Test
    fun failedDownloadBecomesError() {
        val model = FakeModel(
            availability = ModelAvailability.DOWNLOADABLE,
            downloadEvents = flowOf(ModelDownloadEvent.Failed(RuntimeException("No space")))
        )
        val ai = manager(model)
        ai.refresh()
        ai.startDownload()
        assertEquals(LocalAiState.Error("No space"), ai.state.value)
    }

    @Test
    fun downloadEndingWithoutEventAsksModelAgain() {
        val model = FakeModel(availability = ModelAvailability.DOWNLOADABLE)
        model.downloadEvents = flow {
            emit(ModelDownloadEvent.Started)
            model.availability = ModelAvailability.AVAILABLE
        }
        val ai = manager(model)
        ai.refresh()
        ai.startDownload()
        assertEquals(LocalAiState.Ready, ai.state.value)
    }

    @Test
    fun followsDownloadStartedElsewhere() {
        val model = FakeModel(availability = ModelAvailability.DOWNLOADING)
        val ai = manager(model)
        ai.refresh()
        assertEquals(1, model.downloads)
        assertEquals(LocalAiState.Ready, ai.state.value)
    }

    @Test
    fun generateBeforeReadyIsRejected() {
        val model = FakeModel(availability = ModelAvailability.DOWNLOADABLE)
        val ai = manager(model)
        ai.refresh()
        assertEquals(LocalAiResult.NotReady, runBlocking { ai.generate("Hi") })
        assertTrue(model.prompts.isEmpty())
    }

    @Test
    fun generateCleansTheAnswer() {
        val ai = manager(FakeModel(reply = { "**Answer:** You spent less." }))
        ai.refresh()
        assertEquals(LocalAiResult.Success("You spent less."), runBlocking { ai.generate("Hi") })
    }

    @Test
    fun overlongPromptIsRejected() {
        val model = FakeModel()
        val ai = manager(model)
        ai.refresh()
        val prompt = "a".repeat(LocalAiManager.MAX_PROMPT_CHARS + 1)
        assertEquals(LocalAiResult.PromptTooLong, runBlocking { ai.generate(prompt) })
        assertTrue(model.prompts.isEmpty())
    }

    @Test
    fun emptyOrFailingReplyIsAFailure() {
        val model = FakeModel(reply = { "   " })
        val ai = manager(model)
        ai.refresh()
        assertEquals(LocalAiResult.Failed(null), runBlocking { ai.generate("Hi") })
        model.reply = { throw IllegalStateException("Quota exceeded") }
        assertEquals(LocalAiResult.Failed("Quota exceeded"), runBlocking { ai.generate("Hi") })
    }
}
