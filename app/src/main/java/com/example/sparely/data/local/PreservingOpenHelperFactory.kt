package com.example.sparely.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * When SQLite finds the database file damaged, Android's default handling deletes the file and
 * starts an empty one: every expense, vault and account gone without a word, and the app looks
 * like it simply can't load the data. This copies the files aside first (`*.corrupt-<time>.bak`
 * next to the database), so they can still be repaired or read, then lets the usual handling run.
 */
class PreservingOpenHelperFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory()
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val callback = configuration.callback
        val preserving = object : SupportSQLiteOpenHelper.Callback(callback.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) = callback.onConfigure(db)
            override fun onCreate(db: SupportSQLiteDatabase) = callback.onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                callback.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                callback.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SupportSQLiteDatabase) = callback.onOpen(db)

            override fun onCorruption(db: SupportSQLiteDatabase) {
                db.path?.let { path ->
                    val suffix = ".corrupt-${System.currentTimeMillis()}.bak"
                    listOf("", "-wal", "-shm", "-journal").forEach { ext ->
                        try {
                            val source = java.io.File(path + ext)
                            if (source.exists()) source.copyTo(java.io.File(path + ext + suffix), overwrite = true)
                        } catch (e: Exception) {
                            android.util.Log.e("SparelyDatabase", "Could not copy $path$ext aside", e)
                        }
                    }
                    android.util.Log.e("SparelyDatabase", "Database reported damaged; copied aside to *$suffix before Android resets it")
                }
                callback.onCorruption(db)
            }
        }
        return delegate.create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(preserving)
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)
                .build()
        )
    }
}
