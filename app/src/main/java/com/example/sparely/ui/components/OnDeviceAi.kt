package com.example.sparely.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.sparely.ai.Fact
import com.example.sparely.ai.LocalAiManager
import com.example.sparely.ai.LocalAiResult
import com.example.sparely.ai.LocalAiState
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.sparely.app.R

/** What screens need to show on-device AI. Provided once near the root of the app. */
data class OnDeviceAi(
    val manager: LocalAiManager? = null,
    /** The user's "Use on-device AI" setting. */
    val enabled: Boolean = false,
    /** The language prompts are written in, and so the language of the replies. */
    val language: com.example.sparely.ai.PromptLanguage = com.example.sparely.ai.PromptLanguage.ENGLISH
)

val LocalOnDeviceAi = compositionLocalOf { OnDeviceAi() }

/** The manager when the user turned AI on and the phone's model is ready, otherwise null. */
@Composable
private fun readyManager(): LocalAiManager? {
    val ai = LocalOnDeviceAi.current
    val manager = ai.manager ?: return null
    val state by manager.state.collectAsState()
    return manager.takeIf { ai.enabled && state == LocalAiState.Ready }
}

/**
 * A short summary of [facts] written by the on-device model. Shows nothing unless the user turned
 * AI on and the phone supports it. Answers are cached by the manager, so returning to a screen
 * with the same numbers shows the same text without running the model again.
 *
 * @param task what the model should write, e.g. "Explain this verdict in 2 sentences."
 */
@Composable
fun OnDeviceAiSummaryCard(
    title: String,
    facts: List<Fact>,
    task: String,
    modifier: Modifier = Modifier
) {
    val manager = readyManager() ?: return
    if (facts.isEmpty()) return
    val language = LocalOnDeviceAi.current.language
    // The request lives in the manager, so scrolling this card away doesn't stop it, and coming
    // back shows it where it is. It starts once when the numbers change.
    var request by remember(facts, task, language) { mutableStateOf(manager.liveSummary(facts, language, task)) }
    val result by request.result.collectAsState()
    val running = result == null || result is LocalAiResult.Partial

    AiCard(title = title, modifier = modifier) {
        when (val r = result) {
            is LocalAiResult.Success -> Text(r.text, style = MaterialTheme.typography.bodyMedium)
            is LocalAiResult.Partial -> Text(r.text, style = MaterialTheme.typography.bodyMedium)
            null -> Unit
            else -> AiError(r)
        }
        if (running && result !is LocalAiResult.Partial) {
            Text(
                stringResource(R.string.ai_writing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else if (!running && result !is LocalAiResult.Success) {
            SparelyTextButton(onClick = { request = manager.liveSummary(facts, language, task, restart = true) }) {
                Text(stringResource(R.string.ai_try_again))
            }
        }
    }
}

/**
 * Lets the user ask a question answered from [facts] only. Needs the full model, so it's hidden on
 * phones that can only summarize.
 *
 * @param slot identifies this card (e.g. "insights") so its question and answer survive the card
 * being scrolled away and rebuilt
 */
@Composable
fun OnDeviceAiAskCard(
    slot: String,
    facts: List<Fact>,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.ai_ask_placeholder)
) {
    val manager = readyManager() ?: return
    if (!manager.canAnswerQuestions) return
    val language = LocalOnDeviceAi.current.language
    var question by rememberSaveable { mutableStateOf("") }
    var request by remember(slot) { mutableStateOf(manager.currentAnswer(slot)) }
    // Folded into one small row by default; open already if a question was asked here, so its
    // answer is visible when the user comes back.
    var expanded by rememberSaveable(slot) { mutableStateOf(request != null) }
    val result = request?.result?.collectAsState()?.value
    val running = request != null && (result == null || result is LocalAiResult.Partial)

    fun ask() {
        val q = question.trim()
        if (q.isEmpty() || running) return
        request = manager.askLive(slot, facts, q, language)
    }

    if (!expanded) {
        ExpressiveCard(
            modifier = modifier.fillMaxWidth(),
            onClick = { expanded = true },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentPadding = 14.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MaterialSymbolIcon(icon = MaterialSymbols.LIGHTBULB, contentDescription = null, size = 18.dp)
                Text(
                    stringResource(R.string.ai_ask_title),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                MaterialSymbolIcon(icon = MaterialSymbols.ARROW_DROP_DOWN, contentDescription = stringResource(R.string.ai_ask_open), size = 24.dp)
            }
        }
        return
    }

    AiCard(
        title = stringResource(R.string.ai_ask_title),
        modifier = modifier,
        onCollapse = if (running) null else ({ expanded = false })
    ) {
        OutlinedTextField(
            value = question,
            onValueChange = { question = it.take(MAX_QUESTION_CHARS) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            singleLine = true,
            enabled = !running,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { ask() })
        )
        SparelyButton(
            onClick = ::ask,
            enabled = !running && question.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.ai_ask_button))
        }
        request?.question?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (running && result !is LocalAiResult.Partial) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        when (result) {
            is LocalAiResult.Success -> Text(result.text, style = MaterialTheme.typography.bodyMedium)
            is LocalAiResult.Partial -> Text(result.text, style = MaterialTheme.typography.bodyMedium)
            null -> Unit
            else -> AiError(result)
        }
    }
}

@Composable
private fun AiCard(
    title: String,
    modifier: Modifier,
    /** Shows a fold-up arrow in the header when set. */
    onCollapse: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    ExpressiveCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MaterialSymbolIcon(icon = MaterialSymbols.LIGHTBULB, contentDescription = null, size = 20.dp)
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (onCollapse != null) {
                    androidx.compose.material3.IconButton(onClick = onCollapse) {
                        MaterialSymbolIcon(icon = MaterialSymbols.ARROW_DROP_UP, contentDescription = stringResource(R.string.ai_ask_close), size = 24.dp)
                    }
                }
            }
            content()
            Text(
                stringResource(R.string.ai_on_device_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AiError(result: LocalAiResult) {
    val detail = (result as? LocalAiResult.Failed)?.message
    Text(
        text = if (detail.isNullOrBlank()) stringResource(R.string.ai_failed) else stringResource(R.string.ai_failed_details, detail),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

private const val MAX_QUESTION_CHARS = 300
