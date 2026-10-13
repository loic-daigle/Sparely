package com.example.sparely.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AnswerStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun inMemoryDropsTheLeastRecentlyUsedAnswer() {
        val store = InMemoryAnswerStore(maxEntries = 2)
        store.put("a", "1")
        store.put("b", "2")
        store.get("a") // "a" is now the most recently used
        store.put("c", "3")
        assertEquals("1", store.get("a"))
        assertNull(store.get("b"))
        assertEquals("3", store.get("c"))
    }

    @Test
    fun fileStoreKeepsAnswersAcrossRestarts() {
        val file = folder.root.resolve("ai_answers.json")
        FileAnswerStore(file).put("prompt: same numbers", "Dining is over budget.")

        val reopened = FileAnswerStore(file)
        assertEquals("Dining is over budget.", reopened.get("prompt: same numbers"))
        assertNull(reopened.get("prompt: different numbers"))
        // Only hashes of the prompts are written, never the prompts themselves.
        assertFalse(file.readText().contains("same numbers"))
    }

    @Test
    fun fileStoreClearDeletesEverything() {
        val file = folder.root.resolve("ai_answers.json")
        val store = FileAnswerStore(file)
        store.put("k", "v")
        store.clear()
        assertFalse(file.exists())
        assertNull(FileAnswerStore(file).get("k"))
    }

    @Test
    fun damagedFileStartsOver() {
        val file = folder.root.resolve("ai_answers.json")
        file.writeText("{not json")
        val store = FileAnswerStore(file)
        assertNull(store.get("k"))
        store.put("k", "v")
        assertEquals("v", FileAnswerStore(file).get("k"))
    }

    @Test
    fun managerReusesASavedAnswerWithoutRunningTheModel() {
        val file = folder.root.resolve("ai_answers.json")
        val prompt = "Same prompt"
        FileAnswerStore(file).put("prompt:$prompt", "Saved answer.")

        var calls = 0
        val model = object : LocalTextModel {
            override suspend fun availability() = ModelAvailability.AVAILABLE
            override fun download() = kotlinx.coroutines.flow.emptyFlow<ModelDownloadEvent>()
            override suspend fun warmup() = Unit
            override suspend fun generate(prompt: String, options: GenerationOptions): String? {
                calls++
                return "New answer."
            }
            override fun close() = Unit
        }
        val ai = LocalAiManager(
            model,
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            answers = FileAnswerStore(file)
        )
        ai.refresh()
        assertEquals(LocalAiResult.Success("Saved answer."), kotlinx.coroutines.runBlocking { ai.generate(prompt) })
        assertEquals(0, calls)
    }
}
