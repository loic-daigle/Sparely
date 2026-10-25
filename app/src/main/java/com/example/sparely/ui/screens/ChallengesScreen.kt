@file:OptIn(ExperimentalLayoutApi::class)

package com.example.sparely.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import com.example.sparely.ui.components.ExpressiveEmptyState
import com.example.sparely.ui.components.ExpressiveListHeader
import com.example.sparely.ui.components.segmentSpacing
import com.example.sparely.ui.components.segmentedShape
import com.example.sparely.ui.theme.ExpressiveShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.*
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SingleLineText
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.state.SparelyUiState
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import com.sparely.app.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.sparely.ui.components.SparelyBottomSheet
import com.example.sparely.ui.utils.LocalDates
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** How far a challenge should be by [today] to keep its pace: the share of its days gone, 0 to 1. */
internal fun challengePace(challenge: SavingsChallenge, today: LocalDate): Float {
    val total = ChronoUnit.DAYS.between(challenge.startDate, challenge.endDate).coerceAtLeast(1)
    val gone = ChronoUnit.DAYS.between(challenge.startDate, today).coerceIn(0, total)
    return gone.toFloat() / total
}

/** Ahead, on or behind the pace, with a little room either side so a day's rounding doesn't flip it. */
internal enum class ChallengePace { AHEAD, ON, BEHIND }

internal fun challengePaceStatus(progress: Float, pace: Float): ChallengePace = when {
    progress >= pace + 0.03f -> ChallengePace.AHEAD
    progress <= pace - 0.03f -> ChallengePace.BEHIND
    else -> ChallengePace.ON
}

// Challenges started before their names were translated were saved with these English names
private val legacyTitles = mapOf(
    "52-Week Money Challenge" to R.string.challenges_52week_name,
    "30-Day Daily Challenge" to R.string.challenges_daily_name,
    "30-Day Daily Savings" to R.string.challenges_daily_name
)
private val legacyDescriptions = mapOf(
    "Save $1 in week 1, $2 in week 2, etc." to R.string.challenges_52week_detail,
    "Save $5 daily" to R.string.challenges_daily_detail
)

@Composable
private fun SavingsChallenge.shownTitle(): String = legacyTitles[title]?.let { stringResource(it) } ?: title

@Composable
private fun SavingsChallenge.shownDescription(): String = legacyDescriptions[description]?.let { stringResource(it) } ?: description

private fun SavingsChallenge.icon(): Int = when (type) {
    ChallengeType.FIFTY_TWO_WEEK -> MaterialSymbols.CALENDAR_MONTH
    ChallengeType.NO_SPEND_DAYS -> MaterialSymbols.BLOCK
    ChallengeType.DAILY_SAVINGS -> MaterialSymbols.TODAY
    else -> MaterialSymbols.TROPHY
}

@Composable
fun ChallengesScreen(
    uiState: SparelyUiState,
    onStartChallenge: (ChallengeInput) -> Unit,
    onNavigateBack: () -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    val runningChallenges = uiState.activeChallenges.filter { it.isActive && !it.isCompleted }
    val completedChallenges = uiState.activeChallenges.filter { it.isCompleted }
    val today = remember { LocalDate.now() }

    val listState = rememberLazyListState()
    // The FAB shows its label at the top of the list and shrinks to an icon once scrolling starts
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Content padding (not Modifier.padding) avoids clipping; bottom room for the FAB
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
        ) {
            // Running challenges first: they're what people come here to check
            if (runningChallenges.isNotEmpty()) {
                item(key = "hero") {
                    ChallengesHero(runningChallenges, today)
                }
                item(key = "header_active") {
                    ExpressiveListHeader(
                        title = stringResource(R.string.challenges_active_title),
                        icon = MaterialSymbols.LOCAL_FIRE_DEPARTMENT,
                        trailing = runningChallenges.size.toString(),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                itemsIndexed(runningChallenges, key = { _, challenge -> "active_${challenge.id}" }) { index, challenge ->
                    ChallengeRow(
                        challenge = challenge,
                        today = today,
                        shape = segmentedShape(index, runningChallenges.size),
                        modifier = Modifier.segmentSpacing(index)
                    )
                }
            } else {
                // Nothing running (none yet, or all finished): invite starting one
                item(key = "empty") {
                    EmptyChallengesState(onStartChallenge = { showPicker = true })
                }
                item(key = "overview") {
                    ChallengeOverviewCard()
                }
            }

            if (uiState.achievements.isNotEmpty()) {
                val achievements = uiState.achievements.take(5)
                item(key = "header_achievements") {
                    ExpressiveListHeader(
                        title = stringResource(R.string.challenges_recent_achievements),
                        icon = MaterialSymbols.TROPHY,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                itemsIndexed(achievements, key = { index, _ -> "achievement_$index" }) { index, achievement ->
                    AchievementRow(
                        achievement = achievement,
                        shape = segmentedShape(index, achievements.size),
                        modifier = Modifier.segmentSpacing(index)
                    )
                }
            }

            if (completedChallenges.isNotEmpty()) {
                item(key = "header_completed") {
                    ExpressiveListHeader(
                        title = stringResource(R.string.challenges_completed_title),
                        icon = MaterialSymbols.CHECK_CIRCLE,
                        trailing = completedChallenges.size.toString(),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                itemsIndexed(completedChallenges, key = { _, challenge -> "completed_${challenge.id}" }) { index, challenge ->
                    CompletedChallengeCard(
                        challenge = challenge,
                        shape = segmentedShape(index, completedChallenges.size),
                        modifier = Modifier.segmentSpacing(index)
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showPicker = true },
            expanded = fabExpanded,
            icon = {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.ADD,
                    contentDescription = if (fabExpanded) null else stringResource(R.string.challenges_new_title)
                )
            },
            text = { Text(stringResource(R.string.challenges_new_title)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        )
    }

    ChallengePickerSheet(
        isOpen = showPicker,
        onDismiss = { showPicker = false },
        onSelectChallenge = { input ->
            onStartChallenge(input)
            showPicker = false
        }
    )
}

/**
 * The page's headline (docs/DESIGN.md §3): what's saved across the running challenges against their
 * targets, a bar with the mark where they should be by now, and pills for the pace, the best streak
 * and the next one to end.
 */
@Composable
private fun ChallengesHero(running: List<SavingsChallenge>, today: LocalDate) {
    val colors = MaterialTheme.colorScheme
    val ink = colors.onPrimaryContainer
    val saved = running.sumOf { it.currentAmount }
    val target = running.sumOf { it.targetAmount }
    val progress = if (target > 0) (saved / target).toFloat().coerceIn(0f, 1f) else 0f
    val mark = if (target > 0) {
        (running.sumOf { it.targetAmount * challengePace(it, today) } / target).toFloat()
    } else null
    val bestStreak = running.maxOf { it.streakDays }
    val nextEnd = running.minByOrNull { it.endDate }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        color = colors.primaryContainer,
        contentColor = ink
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text(
                    stringResource(R.string.chal_hero_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = ink.copy(alpha = 0.8f)
                )
                SingleLineText(
                    text = saved.formatCurrency(),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = ink
                )
                if (target > 0) {
                    Text(
                        stringResource(R.string.challenges_target_label, target.formatCurrency()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ink.copy(alpha = 0.8f)
                    )
                }
            }
            if (target > 0) {
                MarkedBar(progress = progress, mark = mark, color = ink, height = 10.dp, markColor = ink)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mark != null) {
                    val (icon, text) = when (challengePaceStatus(progress, mark)) {
                        ChallengePace.AHEAD -> MaterialSymbols.TRENDING_UP to stringResource(R.string.chal_pace_ahead)
                        ChallengePace.ON -> MaterialSymbols.CHECK_CIRCLE to stringResource(R.string.chal_pace_on)
                        ChallengePace.BEHIND -> MaterialSymbols.TRENDING_DOWN to stringResource(R.string.chal_pace_behind)
                    }
                    HeroPill(icon, text)
                }
                if (bestStreak > 0) {
                    HeroPill(MaterialSymbols.LOCAL_FIRE_DEPARTMENT, pluralStringResource(R.plurals.chal_streak, bestStreak, bestStreak))
                }
                if (nextEnd != null) {
                    HeroPill(MaterialSymbols.SCHEDULE, stringResource(R.string.challenges_ends_label, nextEnd.endDate.format(LocalDates.dayMonth)))
                }
            }
        }
    }
}

/** One running challenge: icon, name, what's saved against its target and the days left, a bar with its pace mark. */
@Composable
private fun ChallengeRow(
    challenge: SavingsChallenge,
    today: LocalDate,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val progress = challenge.progressPercent.toFloat()
    val pace = challengePace(challenge, today)
    val behind = challenge.targetAmount > 0 && challengePaceStatus(progress, pace) == ChallengePace.BEHIND
    Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = colors.surfaceContainer) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialSymbolIcon(icon = challenge.icon(), contentDescription = null, size = 22.dp, tint = colors.primary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        challenge.shownTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(
                            challenge.shownDescription().takeIf { it.isNotBlank() },
                            stringResource(R.string.challenges_days_remaining, challenge.daysRemaining.coerceAtLeast(0))
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (challenge.streakDays > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        MaterialSymbolIcon(icon = MaterialSymbols.LOCAL_FIRE_DEPARTMENT, contentDescription = null, size = 18.dp, tint = colors.secondary)
                        Text(challenge.streakDays.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (challenge.targetAmount > 0) {
                MarkedBar(progress = progress, mark = pace, color = if (behind) colors.tertiary else colors.primary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.chal_row_progress, challenge.currentAmount.formatCurrency(), challenge.targetAmount.formatCurrency()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${(progress * 100).roundToInt()} %",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            challenge.nextMilestone?.let { milestone ->
                Text(
                    stringResource(R.string.chal_next_milestone, milestone.description, milestone.rewardPoints),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChallengeOverviewCard() {
    ExpressiveCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = ExpressiveShapes.large,
        contentPadding = 20.dp
    ) {
        Column {
            Text(
                text = stringResource(R.string.challenges_help_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.challenges_help_desc_1),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.challenges_help_desc_2),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CompletedChallengeCard(
    challenge: SavingsChallenge,
    shape: androidx.compose.ui.graphics.Shape = ExpressiveShapes.large,
    modifier: Modifier = Modifier
) {
    ExpressiveCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.success.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.CHECK_CIRCLE,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.success,
                        size = 22.dp
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = challenge.shownTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.challenges_completed_on, challenge.completedDate?.format(com.example.sparely.ui.utils.LocalDates.medium) ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = challenge.currentAmount.formatCurrency(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** One achievement in its group: its emoji, name and what it was for. */
@Composable
private fun AchievementRow(achievement: Achievement, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = colors.surfaceContainer) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(achievement.icon, style = MaterialTheme.typography.titleLarge)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(achievement.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(achievement.description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun EmptyChallengesState(onStartChallenge: () -> Unit) {
    ExpressiveEmptyState(
        icon = MaterialSymbols.TROPHY,
        title = stringResource(R.string.challenges_no_active_title),
        description = stringResource(R.string.challenges_no_active_desc),
        action = {
            SparelyButton(
                onClick = onStartChallenge,
                icon = { MaterialSymbolIcon(icon = MaterialSymbols.PLAY_ARROW, contentDescription = null, size = 20.dp) }
            ) {
                SingleLineText(stringResource(R.string.challenges_start_button))
            }
        }
    )
}

/** Picking a new challenge (docs/DESIGN.md §5: a quick task, so a sheet): one row per kind, with its length and target. */
@Composable
private fun ChallengePickerSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onSelectChallenge: (ChallengeInput) -> Unit
) {
    // Saved with the names in the phone's language, so the list shows them that way
    val weeklyName = stringResource(R.string.challenges_52week_name)
    val weeklyDetail = stringResource(R.string.challenges_52week_detail)
    val dailyName = stringResource(R.string.challenges_daily_name)
    val dailyDetail = stringResource(R.string.challenges_daily_detail)
    SparelyBottomSheet(isOpen = isOpen, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                stringResource(R.string.challenges_choose_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
            )
            ChallengeOption(
                title = stringResource(R.string.challenges_52week_title),
                description = stringResource(R.string.challenges_52week_desc),
                meta = stringResource(R.string.chal_option_52week, 1378.0.formatCurrency(decimals = 0)),
                icon = MaterialSymbols.CALENDAR_MONTH,
                shape = segmentedShape(0, 2),
                onClick = {
                    onSelectChallenge(
                        ChallengeInput(
                            type = ChallengeType.FIFTY_TWO_WEEK,
                            title = weeklyName,
                            description = weeklyDetail,
                            targetAmount = 1378.0,
                            endDate = LocalDate.now().plusWeeks(52)
                        )
                    )
                }
            )
            ChallengeOption(
                title = stringResource(R.string.challenges_daily_title),
                description = stringResource(R.string.challenges_daily_desc),
                meta = stringResource(R.string.chal_option_daily, 150.0.formatCurrency(decimals = 0)),
                icon = MaterialSymbols.TODAY,
                shape = segmentedShape(1, 2),
                modifier = Modifier.segmentSpacing(1),
                onClick = {
                    onSelectChallenge(
                        ChallengeInput(
                            type = ChallengeType.DAILY_SAVINGS,
                            title = dailyName,
                            description = dailyDetail,
                            targetAmount = 150.0,
                            endDate = LocalDate.now().plusDays(30)
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun ChallengeOption(
    title: String,
    description: String,
    meta: String,
    @androidx.annotation.DrawableRes icon: Int,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = colors.surfaceContainerHigh) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                MaterialSymbolIcon(icon = icon, contentDescription = null, size = 22.dp, tint = colors.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Text(meta, style = MaterialTheme.typography.labelMedium, color = colors.primary, modifier = Modifier.padding(top = 2.dp))
            }
            MaterialSymbolIcon(icon = MaterialSymbols.ARROW_FORWARD, contentDescription = null, size = 20.dp, tint = colors.onSurfaceVariant)
        }
    }
}
