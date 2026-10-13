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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

/** What screens need to show on-device AI. Provided once near the root of the app. */
data class OnDeviceAi(
    val manager: LocalAiManager? = null,
    /** The user's "Use on-device AI" setting. */
    val enabled: Boolean = false,
    /** Language name in English, e.g. "French", for the model's replies. */
    val replyLanguage: String = "English"
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
    val language = LocalOnDeviceAi.current.replyLanguage
    val scope = rememberCoroutineScope()
    var result by remember(facts, task) {
        mutableStateOf<LocalAiResult?>(manager.cachedSummary(facts, language, task)?.let { LocalAiResult.Success(it) })
    }
    var running by remember(facts, task) { mutableStateOf(false) }

    fun generate() {
        running = true
        scope.launch {
            result = manager.summarize(facts, replyLanguage = language, task = task)
            running = false
        }
    }

    // Write it once when the numbers change; the cache makes revisits instant.
    LaunchedEffect(facts, task) {
        if (result == null) generate()
    }

    AiCard(title = title, modifier = modifier) {
        when (val r = result) {
            is LocalAiResult.Success -> Text(r.text, style = MaterialTheme.typography.bodyMedium)
            null -> Unit
            else -> AiError(r)
        }
        if (running) {
            Text(
                stringResource(R.string.ai_writing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else if (result != null && result !is LocalAiResult.Success) {
            SparelyTextButton(onClick = ::generate) { Text(stringResource(R.string.ai_try_again)) }
        }
    }
}

/**
 * Lets the user ask a question answered from [facts] only. Needs the full model, so it's hidden on
 * phones that can only summarize.
 */
@Composable
fun OnDeviceAiAskCard(
    facts: List<Fact>,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.ai_ask_placeholder)
) {
    val manager = readyManager() ?: return
    if (!manager.canAnswerQuestions) return
    val language = LocalOnDeviceAi.current.replyLanguage
    val scope = rememberCoroutineScope()
    var question by rememberSaveable { mutableStateOf("") }
    var asked by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<LocalAiResult?>(null) }
    var running by remember { mutableStateOf(false) }

    fun ask() {
        val q = question.trim()
        if (q.isEmpty() || running) return
        asked = q
        running = true
        result = null
        scope.launch {
            result = manager.ask(facts, q, replyLanguage = language)
            running = false
        }
    }

    AiCard(title = stringResource(R.string.ai_ask_title), modifier = modifier) {
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
        asked?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        when (val r = result) {
            is LocalAiResult.Success -> Text(r.text, style = MaterialTheme.typography.bodyMedium)
            null -> Unit
            else -> AiError(r)
        }
    }
}

@Composable
private fun AiCard(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    ExpressiveCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MaterialSymbolIcon(icon = MaterialSymbols.LIGHTBULB, contentDescription = null, size = 20.dp)
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
