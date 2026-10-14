package com.example.sparely.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
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
        assertEquals(LocalAiResult.Success("Dining is over budget."), runBlocking { ai.summarize(facts, PromptLanguage.FRENCH) })
        assertTrue(summarizer.inputs.isEmpty())
        assertTrue(model.prompts.single().contains("- Dining this month : $412 of $300 budget"))
        assertTrue(model.prompts.single().contains("Réponds uniquement en français"))
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

    @Test
    fun askNeedsTheFullModelAndIncludesTheQuestion() {
        val model = FakeModel(reply = { "Yes, dining is over budget." })
        val ai = manager(model)
        ai.refresh()
        assertEquals(
            LocalAiResult.Success("Yes, dining is over budget."),
            runBlocking { ai.ask(facts, "Am I overspending?", PromptLanguage.FRENCH) }
        )
        assertTrue(model.prompts.single().contains("L'utilisateur demande : Am I overspending?"))

        val summaryOnly = manager(FakeModel(availability = ModelAvailability.UNAVAILABLE), FakeSummarizer())
        summaryOnly.refresh()
        assertEquals(LocalAiResult.NotReady, runBlocking { summaryOnly.ask(facts, "Am I overspending?") })
    }

    @Test
    fun repeatedRequestsAreAnsweredFromTheCache() {
        var calls = 0
        val model = FakeModel(reply = { calls++; "Answer $calls" })
        val ai = manager(model)
        ai.refresh()
        assertEquals(null, ai.cachedSummary(facts, task = "Sum up."))
        val first = runBlocking { ai.summarize(facts, task = "Sum up.") }
        val second = runBlocking { ai.summarize(facts, task = "Sum up.") }
        assertEquals(LocalAiResult.Success("Answer 1"), first)
        assertEquals(first, second)
        assertEquals(1, calls)
        assertEquals("Answer 1", ai.cachedSummary(facts, task = "Sum up."))
        // A different task is a different request.
        assertEquals(LocalAiResult.Success("Answer 2"), runBlocking { ai.summarize(facts, task = "Explain.") })
    }

    @Test
    fun failuresAreNotCached() {
        var fail = true
        val ai = manager(FakeModel(reply = { if (fail) throw IllegalStateException("busy") else "Done." }))
        ai.refresh()
        assertEquals(LocalAiResult.Failed("busy"), runBlocking { ai.generate("Hi") })
        fail = false
        assertEquals(LocalAiResult.Success("Done."), runBlocking { ai.generate("Hi") })
    }

    private class StreamingModel(private val chunks: List<String>) : LocalTextModel by FakeModel() {
        var streams = 0
        override fun generateStream(prompt: String, options: GenerationOptions): Flow<String> {
            streams++
            return flowOf(*chunks.toTypedArray())
        }
    }

    @Test
    fun summarizeStreamShowsTheReplyAsItIsWrittenThenCachesIt() {
        val model = StreamingModel(listOf("Dining is ", "over budget."))
        val ai = LocalAiManager(model, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        ai.refresh()
        val results = runBlocking { ai.summarizeStream(facts, task = "Sum up.").toList() }
        assertEquals(
            listOf(
                LocalAiResult.Partial("Dining is"),
                LocalAiResult.Partial("Dining is over budget."),
                LocalAiResult.Success("Dining is over budget.")
            ),
            results
        )
        // Asking again comes straight from the cache, without running the model.
        assertEquals(
            listOf(LocalAiResult.Success("Dining is over budget.")),
            runBlocking { ai.summarizeStream(facts, task = "Sum up.").toList() }
        )
        assertEquals(1, model.streams)
        assertEquals("Dining is over budget.", ai.cachedSummary(facts, task = "Sum up."))
    }

    @Test
    fun summarizeStreamFallsBackToTheSummarizerInOneStep() {
        val ai = manager(FakeModel(availability = ModelAvailability.UNAVAILABLE), FakeSummarizer())
        ai.refresh()
        assertEquals(
            listOf(LocalAiResult.Success("• Spending is down.")),
            runBlocking { ai.summarizeStream(facts).toList() }
        )
        assertEquals(listOf(LocalAiResult.NotReady), runBlocking { ai.askStream(facts, "Why?").toList() })
    }

    @Test
    fun askStreamReportsFailures() {
        val model = object : LocalTextModel by FakeModel() {
            override fun generateStream(prompt: String, options: GenerationOptions): Flow<String> =
                flow { throw IllegalStateException("AICore error 200") }
        }
        val ai = LocalAiManager(model, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        ai.refresh()
        assertEquals(
            listOf(LocalAiResult.Failed("AICore error 200")),
            runBlocking { ai.askStream(facts, "Why?").toList() }
        )
    }

    @Test
    fun liveSummaryIsSharedSoRebuiltCardsDontRestartIt() {
        val model = StreamingModel(listOf("Dining is over budget."))
        val ai = LocalAiManager(model, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        ai.refresh()
        val first = ai.liveSummary(facts, task = "Sum up.")
        // A card scrolled away and back asks again with the same numbers.
        val again = ai.liveSummary(facts, task = "Sum up.")
        assertTrue(first === again)
        assertEquals(LocalAiResult.Success("Dining is over budget."), again.result.value)
        assertFalse(again.isRunning)
        assertEquals(1, model.streams)
    }

    @Test
    fun failedLiveSummaryOnlyRunsAgainWhenAsked() {
        var fail = true
        val model = object : LocalTextModel by FakeModel() {
            var streams = 0
            override fun generateStream(prompt: String, options: GenerationOptions): Flow<String> {
                streams++
                return if (fail) flow { throw IllegalStateException("AICore error 200") } else flowOf("Done.")
            }
        }
        val ai = LocalAiManager(model, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        ai.refresh()
        val failed = ai.liveSummary(facts, task = "Sum up.")
        assertEquals(LocalAiResult.Failed("AICore error 200"), failed.result.value)
        fail = false
        // Coming back to the card shows the failure; it doesn't retry by itself.
        assertTrue(failed === ai.liveSummary(facts, task = "Sum up."))
        assertEquals(1, model.streams)
        // "Try again" does.
        val retried = ai.liveSummary(facts, task = "Sum up.", restart = true)
        assertEquals(LocalAiResult.Success("Done."), retried.result.value)
        assertEquals(2, model.streams)
    }

    @Test
    fun askLiveKeepsTheQuestionAndAnswerForItsCard() {
        val ai = LocalAiManager(StreamingModel(listOf("Yes.")), CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        ai.refresh()
        assertEquals(null, ai.currentAnswer("insights"))
        ai.askLive("insights", facts, "Am I overspending?")
        val current = ai.currentAnswer("insights")
        assertEquals("Am I overspending?", current?.question)
        assertEquals(LocalAiResult.Success("Yes."), current?.result?.value)
        assertEquals(null, ai.currentAnswer("health"))
    }

    @Test
    fun rememberedSummaryOutlivesItsNumbers() {
        val ai = LocalAiManager(StreamingModel(listOf("You're on track.")), CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        ai.refresh()
        assertEquals(null, ai.latestSummary("dashboard"))
        ai.liveSummary(facts, task = "Brief.", rememberAs = "dashboard")
        assertEquals("You're on track.", ai.latestSummary("dashboard"))
        assertEquals(null, ai.latestSummary("dashboard", PromptLanguage.FRENCH))
        assertEquals(null, ai.latestSummary("insights"))
    }
}
