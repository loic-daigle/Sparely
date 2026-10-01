package com.example.sparely.domain.model

/** Kinds of change an AI assistant can make through AppFunctions. */
enum class AssistantActionType {
    EXPENSE_RECORDED,
    INCOME_RECORDED
}

/** One change an AI assistant made, kept so the user can review and undo it. */
data class AssistantAction(
    val id: Long,
    val type: AssistantActionType,
    /** The expense id, or the main-account transaction id for income. */
    val recordId: Long,
    val description: String,
    val amount: Double,
    val createdAtEpochMillis: Long,
    val undoneAtEpochMillis: Long? = null
) {
    val isUndone: Boolean get() = undoneAtEpochMillis != null
}
