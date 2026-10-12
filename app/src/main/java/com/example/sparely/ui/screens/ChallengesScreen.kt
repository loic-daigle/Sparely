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
import com.example.sparely.ui.components.SparelyAlertDialog
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelyTextButton
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

@Composable
fun ChallengesScreen(
    uiState: SparelyUiState,
    onStartChallenge: (ChallengeInput) -> Unit,
    onNavigateBack: () -> Unit
) {
    var showChallengeDialog by remember { mutableStateOf(false) }
    
    val runningChallenges = uiState.activeChallenges.filter { it.isActive && !it.isCompleted }
    val completedChallenges = uiState.activeChallenges.filter { it.isCompleted }

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
                item(key = "header_active") {
                    ExpressiveListHeader(
                        title = stringResource(R.string.challenges_active_title),
                        icon = MaterialSymbols.LOCAL_FIRE_DEPARTMENT,
                        trailing = runningChallenges.size.toString(),
                        modifier = Modifier.padding(top = 0.dp)
                    )
                }
                itemsIndexed(runningChallenges, key = { _, challenge -> "active_${challenge.id}" }) { index, challenge ->
                    ChallengeCard(
                        challenge = challenge,
                        shape = segmentedShape(index, runningChallenges.size, outer = 28.dp),
                        modifier = Modifier.segmentSpacing(index)
                    )
                }
            } else {
                // Nothing running (none yet, or all finished): invite starting one
                item(key = "empty") {
                    EmptyChallengesState(onStartChallenge = { showChallengeDialog = true })
                }
                item(key = "overview") {
                    ChallengeOverviewCard()
                }
            }

            if (uiState.achievements.isNotEmpty()) {
                item(key = "achievements") {
                    AchievementsSection(uiState.achievements.take(5))
                }
            }

            if (completedChallenges.isNotEmpty()) {
                item(key = "header_completed") {
                    ExpressiveListHeader(
                        title = stringResource(R.string.challenges_completed_title),
                        icon = MaterialSymbols.CHECK_CIRCLE,
                        trailing = completedChallenges.size.toString(),
                        modifier = Modifier.padding(top = 8.dp)
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
            onClick = { showChallengeDialog = true },
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

    if (showChallengeDialog) {
        ChallengeSelectionDialog(
            onDismiss = { showChallengeDialog = false },
            onSelectChallenge = { input ->
                onStartChallenge(input)
                showChallengeDialog = false
            }
        )
    }
}

@Composable
fun ChallengeCard(
    challenge: SavingsChallenge,
    shape: androidx.compose.ui.graphics.Shape = ExpressiveShapes.large,
    modifier: Modifier = Modifier
) {
    ExpressiveCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        contentPadding = 20.dp
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(ExpressiveShapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialSymbolIcon(
                        icon = when (challenge.type) {
                            ChallengeType.FIFTY_TWO_WEEK -> MaterialSymbols.CALENDAR_MONTH
                            ChallengeType.NO_SPEND_DAYS -> MaterialSymbols.BLOCK
                            ChallengeType.DAILY_SAVINGS -> MaterialSymbols.TODAY
                            else -> MaterialSymbols.TROPHY
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        size = 22.dp
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = challenge.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = challenge.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress
            if (challenge.targetAmount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = challenge.currentAmount.formatCurrency(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.challenges_target_label, challenge.targetAmount.formatCurrency()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { challenge.progressPercent.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.challenges_percent_complete, "${String.format("%.0f", challenge.progressPercent * 100)}%"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Streak
            if (challenge.streakDays > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.LOCAL_FIRE_DEPARTMENT,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp),
                        size = 20.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.challenges_streak_label, challenge.streakDays),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Next milestone
            challenge.nextMilestone?.let { milestone ->
                Spacer(modifier = Modifier.height(12.dp))
                ExpressiveCard(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = ExpressiveShapes.medium,
                    contentPadding = 12.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.challenges_next_milestone_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = milestone.description,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        AssistChip(
                            onClick = {},
                            label = { Text(stringResource(R.string.challenges_points_label, milestone.rewardPoints)) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.challenges_days_remaining, challenge.daysRemaining.coerceAtLeast(0)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.challenges_ends_label, challenge.endDate.format(DateTimeFormatter.ofPattern("MMM d"))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        text = challenge.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.challenges_completed_on, challenge.completedDate?.format(DateTimeFormatter.ofPattern("MMM d, yyyy")) ?: ""),
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

@Composable
fun AchievementsSection(achievements: List<Achievement>) {
    ExpressiveCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = ExpressiveShapes.large,
        contentPadding = 16.dp
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.challenges_recent_achievements),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                MaterialSymbolIcon(icon = MaterialSymbols.TROPHY, contentDescription = null)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            for (achievement in achievements) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = achievement.icon,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = achievement.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = achievement.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                if (achievement != achievements.last()) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
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

@Composable
fun ChallengeSelectionDialog(
    onDismiss: () -> Unit,
    onSelectChallenge: (ChallengeInput) -> Unit
) {
    SparelyAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.challenges_choose_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChallengeOption(
                    title = stringResource(R.string.challenges_52week_title),
                    description = stringResource(R.string.challenges_52week_desc),
                    icon = MaterialSymbols.CALENDAR_MONTH,
                    onClick = {
                        onSelectChallenge(
                            ChallengeInput(
                                type = ChallengeType.FIFTY_TWO_WEEK,
                                title = "52-Week Money Challenge",
                                description = "Save $1 in week 1, $2 in week 2, etc.",
                                targetAmount = 1378.0,
                                endDate = java.time.LocalDate.now().plusWeeks(52)
                            )
                        )
                    }
                )
                
                ChallengeOption(
                    title = stringResource(R.string.challenges_daily_title),
                    description = stringResource(R.string.challenges_daily_desc),
                    icon = MaterialSymbols.TODAY,
                    onClick = {
                        onSelectChallenge(
                            ChallengeInput(
                                type = ChallengeType.DAILY_SAVINGS,
                                title = "30-Day Daily Challenge",
                                description = "Save $5 daily",
                                targetAmount = 150.0,
                                endDate = java.time.LocalDate.now().plusDays(30)
                            )
                        )
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            SparelyTextButton(onClick = onDismiss) {
                SingleLineText(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ChallengeOption(
    title: String,
    description: String,
    @androidx.annotation.DrawableRes icon: Int,
    onClick: () -> Unit
) {
    ExpressiveCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MaterialSymbolIcon(
                icon = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
                size = 32.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

