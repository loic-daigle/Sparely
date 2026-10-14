package com.example.sparely.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
 * The on-device AI block of a screen, laid out like the app's segmented lists: a summary of
 * [facts] on top and, when [askSlot] is set and the phone runs the full model, a row for asking
 * questions underneath, stacked as one rounded group. Shows nothing unless the user turned AI on
 * and the phone's model is ready.
 *
 * Requests run in the manager, not here, so scrolling the section away doesn't stop them, and
 * answers are saved, so the same numbers show the same text without running the model again.
 *
 * @param task what the model should write, from [com.example.sparely.ai.AiTask]
 * @param askSlot identifies this screen's question box (e.g. "insights"); null for no questions
 */
@Composable
fun OnDeviceAiSection(
    title: String,
    facts: List<Fact>,
    task: String,
    modifier: Modifier = Modifier,
    askSlot: String? = null,
    askPlaceholder: String = stringResource(R.string.ai_ask_placeholder)
) {
    val manager = readyManager() ?: return
    if (facts.isEmpty()) return
    val showAsk = askSlot != null && manager.canAnswerQuestions
    val count = if (showAsk) 2 else 1
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
        AiSummarySegment(manager, title, facts, task, segmentedShape(0, count))
        if (showAsk) {
            AiAskSegment(manager, askSlot!!, facts, askPlaceholder, segmentedShape(1, count))
        }
    }
}

@Composable
private fun AiSummarySegment(
    manager: LocalAiManager,
    title: String,
    facts: List<Fact>,
    task: String,
    shape: Shape
) {
    val language = LocalOnDeviceAi.current.language
    // Starts once when the numbers change; the same request comes back when the section is rebuilt.
    var request by remember(facts, task, language) { mutableStateOf(manager.liveSummary(facts, language, task)) }
    val result by request.result.collectAsState()
    val writing = result == null || result is LocalAiResult.Partial

    Surface(modifier = Modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AiBadge()
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                OnDeviceChip()
            }

            when (val r = result) {
                null -> PlaceholderLines()
                is LocalAiResult.Partial -> AiText(r.text)
                is LocalAiResult.Success -> AiText(r.text)
                else -> AiErrorRow(r, onRetry = { request = manager.liveSummary(facts, language, task, restart = true) })
            }

            if (writing) {
                WritingIndicator()
            } else if (result is LocalAiResult.Success) {
                Footnote()
            }
        }
    }
}

@Composable
private fun AiAskSegment(
    manager: LocalAiManager,
    slot: String,
    facts: List<Fact>,
    placeholder: String,
    shape: Shape
) {
    val language = LocalOnDeviceAi.current.language
    var question by rememberSaveable(slot) { mutableStateOf("") }
    var request by remember(slot) { mutableStateOf(manager.currentAnswer(slot)) }
    val result = request?.result?.collectAsState()?.value
    val writing = request != null && (result == null || result is LocalAiResult.Partial)
    // Folded into one row until tapped; open already if a question was asked here, so its answer
    // is visible when the user comes back.
    var expanded by rememberSaveable(slot) { mutableStateOf(request != null) }

    fun ask() {
        val q = question.trim()
        if (q.isEmpty() || writing) return
        request = manager.askLive(slot, facts, q, language)
        question = ""
    }

    Surface(modifier = Modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Only the header folds and unfolds, so tapping the answer or the field doesn't.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !writing) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.INFO,
                        contentDescription = null,
                        size = 20.dp,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ai_ask_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!expanded) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                MaterialSymbolIcon(
                    icon = if (expanded) MaterialSymbols.ARROW_DROP_UP else MaterialSymbols.ARROW_DROP_DOWN,
                    contentDescription = stringResource(if (expanded) R.string.ai_ask_close else R.string.ai_ask_open),
                    size = 24.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    request?.question?.let { QuestionBubble(it) }
                    when (result) {
                        null -> if (request != null) PlaceholderLines(lines = 2)
                        is LocalAiResult.Partial -> AiText(result.text)
                        is LocalAiResult.Success -> AiText(result.text)
                        else -> AiErrorRow(result, onRetry = null)
                    }
                    if (writing) WritingIndicator()

                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it.take(MAX_QUESTION_CHARS) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        enabled = !writing,
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { ask() }),
                        trailingIcon = {
                            IconButton(onClick = ::ask, enabled = !writing && question.isNotBlank()) {
                                MaterialSymbolIcon(
                                    icon = MaterialSymbols.ARROW_FORWARD,
                                    contentDescription = stringResource(R.string.ai_ask_button),
                                    size = 22.dp,
                                    tint = if (!writing && question.isNotBlank()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * A one-sentence brief written by the on-device model, meant to sit under a screen's greeting as a
 * subtitle rather than as another card. Tapping it calls [onClick] (e.g. to open Insights).
 *
 * While a new brief is written for changed numbers, the previous one stays, faded, so the line
 * never empties or jumps; only the very first brief shows a placeholder line. A failure hides it
 * instead of putting an error on the screen. Shows nothing when AI is off or not ready.
 *
 * @param slot names this brief so the last one can be shown while the next is written
 */
@Composable
fun OnDeviceAiBrief(
    facts: List<Fact>,
    task: String,
    slot: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val manager = readyManager() ?: return
    if (facts.isEmpty()) return
    val language = LocalOnDeviceAi.current.language
    val request = remember(facts, task, language) {
        manager.liveSummary(facts, language, task, maxAnswerChars = BRIEF_MAX_CHARS, rememberAs = slot)
    }
    val result by request.result.collectAsState()
    val previous = remember(slot, language) { manager.latestSummary(slot, language) }
    val fresh = (result as? LocalAiResult.Success)?.text ?: (result as? LocalAiResult.Partial)?.text
    val writing = result == null || result is LocalAiResult.Partial
    val shown = fresh ?: previous
    if (shown == null && !writing) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = stringResource(R.string.ai_brief_open), onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MaterialSymbolIcon(
            icon = MaterialSymbols.LIGHTBULB,
            contentDescription = null,
            size = 16.dp,
            tint = MaterialTheme.colorScheme.primary
        )
        Box(modifier = Modifier.weight(1f)) {
            if (shown == null) {
                PlaceholderLines(lines = 1)
            } else {
                Text(
                    text = shown,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    // The previous brief, while the new one is written.
                    modifier = Modifier.alpha(if (fresh == null) 0.55f else 1f)
                )
            }
        }
        if (writing && fresh == null && shown != null) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
        } else {
            MaterialSymbolIcon(
                icon = MaterialSymbols.ARROW_FORWARD,
                contentDescription = null,
                size = 16.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One sentence; anything past this is cut at the last full sentence. */
private const val BRIEF_MAX_CHARS = 180

/** The tonal circle that marks AI content, like the badges on the app's list headers. */
@Composable
private fun AiBadge() {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        MaterialSymbolIcon(
            icon = MaterialSymbols.LIGHTBULB,
            contentDescription = null,
            size = 20.dp,
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun OnDeviceChip() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = stringResource(R.string.ai_on_device_chip),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun AiText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** What the user asked, as a bubble on the right, like a message they sent. */
@Composable
private fun QuestionBubble(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

/** Pulsing grey lines where the text will appear, before the model writes its first words. */
@Composable
private fun PlaceholderLines(lines: Int = 3) {
    val transition = rememberInfiniteTransition(label = "ai_placeholder")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
        label = "ai_placeholder_alpha"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.alpha(alpha)) {
        repeat(lines) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (index == lines - 1) 0.6f else 1f)
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
        }
    }
}

@Composable
private fun WritingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        Text(
            stringResource(R.string.ai_writing),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Footnote() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MaterialSymbolIcon(
            icon = MaterialSymbols.INFO,
            contentDescription = null,
            size = 14.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.ai_on_device_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AiErrorRow(result: LocalAiResult, onRetry: (() -> Unit)?) {
    val detail = (result as? LocalAiResult.Failed)?.message
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MaterialSymbolIcon(
            icon = MaterialSymbols.WARNING,
            contentDescription = null,
            size = 20.dp,
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = if (detail.isNullOrBlank()) stringResource(R.string.ai_failed) else stringResource(R.string.ai_failed_details, detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
    if (onRetry != null) {
        SparelyTextButton(
            onClick = onRetry,
            icon = { MaterialSymbolIcon(icon = MaterialSymbols.REFRESH, contentDescription = null, size = 18.dp) }
        ) {
            Text(stringResource(R.string.ai_try_again))
        }
    }
}

private const val MAX_QUESTION_CHARS = 300
