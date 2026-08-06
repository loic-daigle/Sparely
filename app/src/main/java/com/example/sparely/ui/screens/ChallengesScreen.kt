package com.example.sparely.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { showChallengeDialog = true }) {
                    MaterialSymbolIcon(icon = MaterialSymbols.ADD, stringResource(R.string.challenges_new_title))
                }
            }
        }

        item {
            ChallengeOverviewCard()
        }

        if (uiState.achievements.isNotEmpty()) {
            item {
                AchievementsSection(uiState.achievements.take(5))
            }
        }

        if (uiState.activeChallenges.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.challenges_active_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            items(uiState.activeChallenges.filter { it.isActive && !it.isCompleted }) { challenge ->
                ChallengeCard(challenge)
            }
        }

        val completedChallenges = uiState.activeChallenges.filter { it.isCompleted }
        if (completedChallenges.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.challenges_completed_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            items(completedChallenges) { challenge ->
                CompletedChallengeCard(challenge)
            }
        }

        if (uiState.activeChallenges.isEmpty()) {
            item {
                EmptyChallengesState(onStartChallenge = { showChallengeDialog = true })
            }
        }
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
fun ChallengeCard(challenge: SavingsChallenge) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        contentPadding = 20.dp
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = challenge.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = challenge.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                
                MaterialSymbolIcon(
                    icon = when (challenge.type) {
                        ChallengeType.FIFTY_TWO_WEEK -> MaterialSymbols.CALENDAR_MONTH
                        ChallengeType.NO_SPEND_DAYS -> MaterialSymbols.BLOCK
                        ChallengeType.DAILY_SAVINGS -> MaterialSymbols.TODAY
                        else -> MaterialSymbols.TROPHY
                    },
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                    size = 40.dp
                )
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
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.challenges_target_label, challenge.targetAmount.formatCurrency()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                progress = { challenge.progressPercent.toFloat() },
                modifier = Modifier
                                        .fillMaxWidth()
                                        .height(12.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.challenges_percent_complete, "${String.format("%.0f", challenge.progressPercent * 100)}%"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
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
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // Next milestone
            challenge.nextMilestone?.let { milestone ->
                Spacer(modifier = Modifier.height(12.dp))
                ExpressiveCard(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp),
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
                    text = stringResource(R.string.challenges_days_remaining, challenge.daysRemaining),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Text(
                    text = stringResource(R.string.challenges_ends_label, challenge.endDate.format(DateTimeFormatter.ofPattern("MMM d"))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ChallengeOverviewCard() {
    ExpressiveCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(20.dp),
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
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
fun CompletedChallengeCard(challenge: SavingsChallenge) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.CHECK_CIRCLE,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                    size = 32.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = challenge.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
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
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
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
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
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
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        contentPadding = 32.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MaterialSymbolIcon(
                icon = MaterialSymbols.TROPHY,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.challenges_no_active_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.challenges_no_active_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            SparelyButton(onClick = onStartChallenge) {
                MaterialSymbolIcon(icon = MaterialSymbols.PLAY_ARROW, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                SingleLineText(stringResource(R.string.challenges_start_button))
            }
        }
    }
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

