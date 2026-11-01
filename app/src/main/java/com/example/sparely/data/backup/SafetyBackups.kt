package com.example.sparely.data.backup

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The backups that keep the user's data safe, in one place:
 *
 * - Every backup lands in the app's backup folder. [prune] keeps the newest [KEEP_RECENT] plus the
 *   newest of each day for [KEEP_DAYS] days, so a bad stretch (an empty database backed up a few
 *   times) can't push every good copy out.
 * - Once a day, a copy also goes to Downloads/Sparely (Android 10+), which survives uninstalling
 *   the app and clearing its data, and can be imported from the file picker.
 * - [recoveryCandidate] finds the newest backup that holds data, for the app to offer it back when
 *   it opens on an empty database.
 */
object SafetyBackups {
    private const val TAG = "SafetyBackups"
    const val DIR_NAME = "backups"
    private const val PREFIX = "sparely_backup_"
    private const val KEEP_RECENT = 10
    private const val KEEP_DAYS = 30L
    private const val PUBLIC_KEEP = 7
    private val stampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    /** What a backup holds, read from its file without restoring it. */
    data class Summary(val file: File, val savedAt: LocalDateTime, val expenses: Int, val vaults: Int, val recurring: Int, val transactions: Int) {
        val hasData: Boolean get() = expenses + vaults + recurring + transactions > 0
    }

    fun directory(context: Context): File =
        File(context.getExternalFilesDir(null), DIR_NAME).apply { if (!exists()) mkdirs() }

    fun files(context: Context): List<File> = directory(context)
        .listFiles { f -> f.isFile && f.name.startsWith(PREFIX) && f.name.endsWith(".json") }
        .orEmpty()
        .sortedByDescending { it.lastModified() }

    /** Saves [json] as a new backup ([reason] goes in the name, e.g. "before-import"), then prunes. */
    fun write(context: Context, json: String, reason: String? = null): File {
        val stamp = LocalDateTime.now().format(stampFormat)
        val name = PREFIX + stamp + (reason?.let { "_$it" } ?: "") + ".json"
        val file = File(directory(context), name)
        file.writeText(json)
        prune(context)
        copyToDownloads(context, json)
        return file
    }

    /** Keeps the newest [KEEP_RECENT] backups and the newest one of each of the last [KEEP_DAYS] days. */
    fun prune(context: Context) {
        val all = files(context)
        val keep = selectKept(all.map { it.name to dayOf(it) }).toSet()
        all.filter { it.name !in keep }.forEach { file ->
            Log.d(TAG, "Deleting old backup: ${file.name}")
            file.delete()
        }
    }

    /** Names to keep from (name, day) pairs sorted newest first. Pure, for tests. */
    fun selectKept(newestFirst: List<Pair<String, LocalDate>>, today: LocalDate = LocalDate.now()): List<String> {
        val recent = newestFirst.take(KEEP_RECENT).map { it.first }
        val daily = newestFirst
            .filter { !it.second.isBefore(today.minusDays(KEEP_DAYS)) }
            .distinctBy { it.second }
            .map { it.first }
        return (recent + daily).distinct()
    }

    private fun dayOf(file: File): LocalDate =
        Instant.ofEpochMilli(file.lastModified()).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Counts what a backup file holds; null when it can't be read as a backup. */
    fun summarize(file: File): Summary? = try {
        val root = JsonParser.parseString(file.readText()).asJsonObject
        fun count(key: String) = root.get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0
        val savedAt = root.get("timestamp")?.takeIf { it.isJsonPrimitive }?.asLong
            ?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
            ?: LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault())
        Summary(file, savedAt, count("expenses"), count("vaults"), count("recurringExpenses"), count("transactions"))
    } catch (e: Exception) {
        Log.w(TAG, "Unreadable backup ${file.name}", e)
        null
    }

    /** The newest backup that holds data, to offer back when the database is empty. */
    fun recoveryCandidate(context: Context): Summary? =
        files(context).asSequence().mapNotNull { summarize(it) }.firstOrNull { it.hasData }

    /**
     * Once a day, a copy in Downloads/Sparely: outside the app's own folders, so it's still there
     * after the app is uninstalled or its data cleared. Older copies the app made are pruned to
     * [PUBLIC_KEEP]. Android 10+ only (no storage permission needed there).
     */
    private fun copyToDownloads(context: Context, json: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val folder = "Download/Sparely/"
            val name = "sparely_backup_${LocalDate.now()}.json"
            // The day's copy is replaced by the latest of the day
            resolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
                "${MediaStore.Downloads.RELATIVE_PATH} = ?",
                arrayOf(folder),
                "${MediaStore.Downloads.DATE_ADDED} DESC"
            )?.use { cursor ->
                var index = 0
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val existing = cursor.getString(1)
                    val uri = android.content.ContentUris.withAppendedId(collection, id)
                    if (existing == name || index >= PUBLIC_KEEP - 1) resolver.delete(uri, null, null) else index++
                }
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, folder)
            }
            resolver.insert(collection, values)?.let { uri ->
                resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy the backup to Downloads", e)
        }
    }

    /** True when the database holds the user's data (not a fresh or wiped one). */
    suspend fun databaseHasData(container: com.example.sparely.AppContainer): Boolean {
        val repo = container.savingsRepository
        return repo.observeExpenses().first().isNotEmpty() ||
            repo.observeAllSmartVaults().first().isNotEmpty() ||
            repo.observeMainAccountTransactions().first().isNotEmpty() ||
            repo.observeRecurringExpenses().first().isNotEmpty()
    }

    /** A copy of the data before something replaces or deletes it (an import, a reset). */
    suspend fun saveBefore(container: com.example.sparely.AppContainer, reason: String) {
        try {
            if (databaseHasData(container)) write(container.context, container.backupRepository.exportData(), reason)
        } catch (e: Exception) {
            Log.w(TAG, "Could not save a copy before $reason", e)
        }
    }
}
