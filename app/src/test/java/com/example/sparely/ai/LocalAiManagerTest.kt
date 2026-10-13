package com.example.sparely.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private class FakeSummarizer(
        var availability: ModelAvailability = ModelAvailability.AVAILABLE,
        var downloadEvents: Flow<ModelDownloadEvent> = flowOf(ModelDownloadEvent.Completed),
        var reply: () -> String? = { "* Spending is down." }
    ) : LocalSummarizer {
        var downloads = 0
        val inputs = mutableListOf<String>()
        override suspend fun availability() = availability
        override fun download(): Flow<ModelDownloadEvent> {
            downloads++
            return downloadEvents
        }
        override suspend fun summarize(text: String): String? {
            inputs += text
            return reply()
        }
        override fun close() = Unit
    }

    // Unconfined runs launched work right away, so the fake's results are visible on return.
    private fun manager(model: FakeModel, summarizer: FakeSummarizer? = null) =
        LocalAiManager(model, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), summarizer)

    private val facts = listOf(Fact("Dining this month", "$412 of $300 budget"))

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

    @Test
    fun summarizerAloneMakesSummariesReadyButNotQuestions() {
        val summarizer = FakeSummarizer()
        val ai = manager(FakeModel(availability = ModelAvailability.UNAVAILABLE), summarizer)
        ai.refresh()
        assertEquals(LocalAiState.Ready, ai.state.value)
        assertEquals(LocalAiState.Unsupported, ai.promptState.value)
        assertFalse(ai.canAnswerQuestions)
        assertEquals(LocalAiResult.NotReady, runBlocking { ai.generate("Hi") })
        assertEquals(LocalAiResult.Success("• Spending is down."), runBlocking { ai.summarize(facts) })
        assertEquals(listOf("Dining this month: $412 of $300 budget."), summarizer.inputs)
    }

    @Test
    fun fullModelIsPreferredForSummaries() {
        val model = FakeModel(reply = { "Dining is over budget." })
        val summarizer = FakeSummarizer()
        val ai = manager(model, summarizer)
        ai.refresh()
        assertTrue(ai.canAnswerQuestions)
        assertEquals(LocalAiResult.Success("Dining is over budget."), runBlocking { ai.summarize(facts, "French") })
        assertTrue(summarizer.inputs.isEmpty())
        assertTrue(model.prompts.single().contains("- Dining this month: $412 of $300 budget"))
        assertTrue(model.prompts.single().contains("Reply in French."))
    }

    @Test
    fun downloadsOnlyTheSummarizerWhenItIsAllThePhoneOffers() {
        val model = FakeModel(availability = ModelAvailability.UNAVAILABLE)
        val summarizer = FakeSummarizer(availability = ModelAvailability.DOWNLOADABLE)
        val ai = manager(model, summarizer)
        ai.refresh()
        assertEquals(LocalAiState.NeedsDownload, ai.state.value)
        ai.startDownload()
        assertEquals(0, model.downloads)
        assertEquals(1, summarizer.downloads)
        assertEquals(LocalAiState.Ready, ai.state.value)
    }

    @Test
    fun downloadsTheFullModelWhenThePhoneOffersIt() {
        val model = FakeModel(availability = ModelAvailability.DOWNLOADABLE)
        val summarizer = FakeSummarizer(availability = ModelAvailability.DOWNLOADABLE)
        val ai = manager(model, summarizer)
        ai.refresh()
        ai.startDownload()
        assertEquals(1, model.downloads)
        assertEquals(0, summarizer.downloads)
        assertTrue(ai.canAnswerQuestions)
    }

    @Test
    fun unsupportedOnlyWhenNeitherEngineIs() {
        val ai = manager(
            FakeModel(availability = ModelAvailability.UNAVAILABLE),
            FakeSummarizer(availability = ModelAvailability.UNAVAILABLE)
        )
        ai.refresh()
        assertEquals(LocalAiState.Unsupported, ai.state.value)
        assertEquals(LocalAiResult.NotReady, runBlocking { ai.summarize(facts) })
    }

    @Test
    fun combinePicksTheMostUsefulState() {
        val error = LocalAiState.Error("boom")
        assertEquals(LocalAiState.Ready, LocalAiManager.combine(LocalAiState.Unsupported, LocalAiState.Ready))
        assertEquals(LocalAiState.Downloading(30), LocalAiManager.combine(LocalAiState.Downloading(10), LocalAiState.Downloading(20)))
        assertEquals(LocalAiState.NeedsDownload, LocalAiManager.combine(error, LocalAiState.NeedsDownload))
        assertEquals(LocalAiState.Checking, LocalAiManager.combine(LocalAiState.Checking, LocalAiState.Unsupported))
        assertEquals(error, LocalAiManager.combine(LocalAiState.Unsupported, error))
        assertEquals(LocalAiState.Unsupported, LocalAiManager.combine(LocalAiState.Unsupported, LocalAiState.Unsupported))
    }
}
