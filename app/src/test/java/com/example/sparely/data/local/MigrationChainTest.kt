package com.example.sparely.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards against a forgotten migration: every schema version users may still have installed
 * must be able to reach the current version through the registered migrations.
 */
class MigrationChainTest {

    private val migrations = SparelyDatabase.ALL_MIGRATIONS.toList()

    @Test
    fun everyOldVersionHasAPathToTheCurrentVersion() {
        val target = SparelyDatabase.DATABASE_VERSION
        val byStart = migrations.groupBy { it.startVersion }
        for (version in 1 until target) {
            val reachable = mutableSetOf(version)
            val queue = ArrayDeque(listOf(version))
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                byStart[current].orEmpty().forEach { migration ->
                    if (reachable.add(migration.endVersion)) queue.addLast(migration.endVersion)
                }
            }
            assertTrue("No migration path from v$version to v$target", target in reachable)
        }
    }

    @Test
    fun migrationsOnlyMoveForwardAndAreUnique() {
        migrations.forEach { assertTrue(it.endVersion > it.startVersion) }
        val pairs = migrations.map { it.startVersion to it.endVersion }
        assertEquals("Duplicate migration registered", pairs.size, pairs.toSet().size)
        assertTrue(migrations.all { it.endVersion <= SparelyDatabase.DATABASE_VERSION })
    }
}
