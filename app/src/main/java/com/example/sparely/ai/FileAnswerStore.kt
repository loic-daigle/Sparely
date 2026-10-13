package com.example.sparely.ai

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.security.MessageDigest

/**
 * [AnswerStore] that survives app restarts, in a small JSON file. Keys are stored as SHA-256
 * hashes so the file never holds the prompts, only the answers.
 *
 * Put [file] in `Context.noBackupFilesDir`: answers mention the user's finances and must not be
 * copied to cloud backups.
 */
class FileAnswerStore(
    private val file: File,
    maxEntries: Int = InMemoryAnswerStore.DEFAULT_MAX_ENTRIES
) : AnswerStore {

    private val memory = InMemoryAnswerStore(maxEntries)
    private val gson = Gson()
    private var loaded = false

    @Synchronized
    override fun get(key: String): String? {
        load()
        return memory.get(hash(key))
    }

    @Synchronized
    override fun put(key: String, value: String) {
        load()
        memory.put(hash(key), value)
        save()
    }

    @Synchronized
    override fun clear() {
        memory.clear()
        loaded = true
        file.delete()
    }

    private fun load() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return
        try {
            val type = object : TypeToken<List<List<String>>>() {}.type
            val pairs: List<List<String>> = gson.fromJson(file.readText(), type) ?: return
            pairs.filter { it.size == 2 }.forEach { (key, value) -> memory.put(key, value) }
        } catch (_: Exception) {
            // A damaged file only costs the saved answers; start over.
            file.delete()
        }
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(gson.toJson(memory.snapshot().map { listOf(it.first, it.second) }))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (_: Exception) {
            // Not saving only means the model runs again next time.
        }
    }

    private fun hash(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
}
