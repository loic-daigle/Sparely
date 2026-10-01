package com.example.sparely.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Audit log of every change an AI assistant made through AppFunctions, so the user can see it
 * and undo it. Plain column types on purpose: no converters, and the migration stays trivial.
 */
@Entity(tableName = "assistant_actions")
data class AssistantActionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** An AssistantActionType name, e.g. "EXPENSE_RECORDED". */
    val actionType: String,
    /** ID of the row the action created (an expense, or a main-account transaction). */
    val recordId: Long,
    val description: String,
    val amount: Double,
    val createdAtEpochMillis: Long,
    /** When the user undid the action; null while it still stands. */
    val undoneAtEpochMillis: Long? = null
)

@Dao
interface AssistantActionDao {
    @Insert
    suspend fun insert(action: AssistantActionEntity): Long

    @Query("SELECT * FROM assistant_actions WHERE id = :id")
    suspend fun findById(id: Long): AssistantActionEntity?

    @Query("SELECT * FROM assistant_actions WHERE createdAtEpochMillis >= :sinceEpochMillis ORDER BY createdAtEpochMillis DESC")
    suspend fun getSince(sinceEpochMillis: Long): List<AssistantActionEntity>

    @Query("SELECT * FROM assistant_actions ORDER BY createdAtEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AssistantActionEntity>>

    /** Marks an action undone; returns 0 when it was already undone, so undo runs at most once. */
    @Query("UPDATE assistant_actions SET undoneAtEpochMillis = :undoneAtEpochMillis WHERE id = :id AND undoneAtEpochMillis IS NULL")
    suspend fun markUndone(id: Long, undoneAtEpochMillis: Long): Int
}
