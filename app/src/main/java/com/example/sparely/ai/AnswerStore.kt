package com.example.sparely.ai

/**
 * Answers the model already gave, keyed by the exact request. The same prompt means the same
 * numbers, so its answer is reused instead of running the model again.
 */
interface AnswerStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun clear()
}

/** Keeps the [maxEntries] most recently used answers in memory. */
class InMemoryAnswerStore(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) : AnswerStore {

    private val entries = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > maxEntries
    }

    @Synchronized
    override fun get(key: String): String? = entries[key]

    @Synchronized
    override fun put(key: String, value: String) {
        entries[key] = value
    }

    @Synchronized
    override fun clear() = entries.clear()

    /** Least recently used first, so loading them back in order keeps the same eviction order. */
    @Synchronized
    fun snapshot(): List<Pair<String, String>> = entries.map { it.key to it.value }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 64
    }
}
