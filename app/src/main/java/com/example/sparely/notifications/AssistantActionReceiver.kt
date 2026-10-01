package com.example.sparely.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.sparely.SparelyApplication
import com.example.sparely.domain.usecase.UndoAssistantActionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the Undo button on an "assistant added an entry" notification. */
class AssistantActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_UNDO) return
        val actionId = intent.getLongExtra(EXTRA_ACTION_ID, -1L)
        if (actionId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as SparelyApplication).container
                val undo = UndoAssistantActionUseCase(
                    container.savingsRepository,
                    container.preferencesRepository,
                    container.assistantActionRepository
                )
                when (undo(actionId)) {
                    UndoAssistantActionUseCase.Outcome.UNDONE ->
                        container.assistantActionRepository.findById(actionId)?.let {
                            AssistantActionNotifier.showUndone(context, it)
                        }
                    else -> AssistantActionNotifier.cancel(context, actionId)
                }
            } catch (e: Exception) {
                // An uncaught exception here would crash the whole app process in the background.
                android.util.Log.e("AssistantActionReceiver", "Failed to undo assistant action $actionId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_UNDO = "com.example.sparely.ASSISTANT_ACTION_UNDO"
        const val EXTRA_ACTION_ID = "assistant_action_id"
    }
}
