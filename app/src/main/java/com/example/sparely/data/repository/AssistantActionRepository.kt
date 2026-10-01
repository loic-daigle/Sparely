package com.example.sparely.data.repository

import com.example.sparely.data.local.AssistantActionDao
import com.example.sparely.data.local.AssistantActionEntity
import com.example.sparely.domain.model.AssistantAction
import com.example.sparely.domain.model.AssistantActionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Audit log of changes AI assistants made through AppFunctions. */
class AssistantActionRepository(private val dao: AssistantActionDao) {

    suspend fun log(type: AssistantActionType, recordId: Long, description: String, amount: Double): AssistantAction {
        val entity = AssistantActionEntity(
            actionType = type.name,
            recordId = recordId,
            description = description,
            amount = amount,
            createdAtEpochMillis = System.currentTimeMillis()
        )
        return entity.copy(id = dao.insert(entity)).toDomain()!!
    }

    suspend fun findById(id: Long): AssistantAction? = dao.findById(id)?.toDomain()

    suspend fun actionsSince(epochMillis: Long): List<AssistantAction> =
        dao.getSince(epochMillis).mapNotNull { it.toDomain() }

    fun observeRecent(limit: Int = 20): Flow<List<AssistantAction>> =
        dao.observeRecent(limit).map { rows -> rows.mapNotNull { it.toDomain() } }

    /** Returns false when the action was already undone. */
    suspend fun markUndone(id: Long): Boolean = dao.markUndone(id, System.currentTimeMillis()) > 0

    // Rows written by a newer app version may carry a type this version doesn't know: skip them.
    private fun AssistantActionEntity.toDomain(): AssistantAction? {
        val type = AssistantActionType.entries.firstOrNull { it.name == actionType } ?: return null
        return AssistantAction(
            id = id,
            type = type,
            recordId = recordId,
            description = description,
            amount = amount,
            createdAtEpochMillis = createdAtEpochMillis,
            undoneAtEpochMillis = undoneAtEpochMillis
        )
    }
}
