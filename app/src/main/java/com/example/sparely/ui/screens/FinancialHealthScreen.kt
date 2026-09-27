package com.example.sparely.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.*
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.components.ExpressiveCard
import androidx.compose.ui.res.stringResource
import com.sparely.app.R
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import com.example.sparely.ui.state.SparelyUiState
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.ExpressiveMotionTokens
import com.example.sparely.ui.theme.critical
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.theme.warning

@Composable
fun FinancialHealthScreen(
    uiState: SparelyUiState,
    onNavigateBack: () -> Unit
) {
    val healthScore = uiState.financialHealthScore
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        if (healthScore != null) {
            item {
                HealthScoreCard(healthScore)
            }

            item {
                Text(
                    text = stringResource(R.string.health_score_breakdown),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                ScoreBreakdownCard(healthScore)
            }

            if (healthScore.topStrengths.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.health_strengths),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(healthScore.topStrengths) { strength ->
                    StrengthCard(strength)
                }
            }

            if (healthScore.improvementAreas.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.health_improve),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(healthScore.improvementAreas) { tip ->
                    ImprovementTipCard(tip)
                }
            }
        } else {
            item {
                Text(stringResource(R.string.health_loading))
            }
        }
    }
}

@Composable
fun HealthScoreCard(healthScore: FinancialHealthScore) {
    // Determine gradient colors based on health level
    val (startColor, endColor) = when (healthScore.healthLevel) {
        HealthLevel.EXCELLENT -> MaterialTheme.colorScheme.success to MaterialTheme.colorScheme.success.copy(alpha = 0.72f)
        HealthLevel.GOOD -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
        HealthLevel.FAIR -> MaterialTheme.colorScheme.warning to MaterialTheme.colorScheme.warning.copy(alpha = 0.72f)
        HealthLevel.NEEDS_WORK -> MaterialTheme.colorScheme.warning to MaterialTheme.colorScheme.warning.copy(alpha = 0.55f)
        HealthLevel.CRITICAL -> MaterialTheme.colorScheme.critical to MaterialTheme.colorScheme.critical.copy(alpha = 0.72f)
    }

    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        contentPadding = 32.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.dashboard_financial_health),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            EnhancedAnimatedScoreCircle(
                score = healthScore.overallScore,
                size = 240.dp,
                strokeWidth = 24.dp,
                primaryColor = startColor,
                secondaryColor = endColor
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = healthScore.healthLevel.displayName(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = startColor
            )
        }
    }
}

@Composable
fun EnhancedAnimatedScoreCircle(
    score: Int,
    size: Dp = 200.dp,
    strokeWidth: Dp = 20.dp,
    primaryColor: Color,
    secondaryColor: Color
) {
    var animatedScore by remember { mutableStateOf(0f) }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    
    LaunchedEffect(score) {
        animate(
            initialValue = animatedScore,
            targetValue = score.toFloat(),
            animationSpec = tween(
                durationMillis = ExpressiveMotionTokens.SlowDurationMillis,
                easing = ExpressiveMotionTokens.EmphasizedEasing
            )
        ) { value, _ ->
            animatedScore = value
        }
    }

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = this.size.minDimension
            val strokeWidthPx = strokeWidth.toPx()
             val radiusWidth = diameter - strokeWidthPx
            val topLeftOffset = Offset(strokeWidthPx / 2, strokeWidthPx / 2)
            
            // Background Track with Ticks style or solid
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(strokeWidthPx, cap = StrokeCap.Round),
                size = Size(radiusWidth, radiusWidth),
                topLeft = topLeftOffset
            )
            
            // Gradient Score Arc
            val sweepAngle = (animatedScore / 100f) * 270f
            
            if (sweepAngle > 0) {
                 drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to secondaryColor,
                         0.5f to primaryColor,
                         1.0f to primaryColor,
                         center = center
                    ),
                    startAngle = 135f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(strokeWidthPx, cap = StrokeCap.Round),
                    size = Size(radiusWidth, radiusWidth),
                    topLeft = topLeftOffset
                )
            }
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
             Text(
                text = "${animatedScore.toInt()}",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(64f, androidx.compose.ui.unit.TextUnitType.Sp)),
                fontWeight = FontWeight.Bold,
                 color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "/100",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ScoreBreakdownCard(healthScore: FinancialHealthScore) {
    // Grid Layout for Breakdown items
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val items = healthScore.scoreBreakdown.entries.toList()
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for ((category, score) in rowItems) {
                    BreakdownTile(
                        category = category,
                        score = score,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun BreakdownTile(category: String, score: Int, modifier: Modifier = Modifier) {
    ExpressiveCard(
        modifier = modifier.height(110.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        contentPadding = 16.dp
    ) {
        Column(
            modifier = Modifier,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = getCategoryDisplayName(category),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                 Text(
                    text = "$score",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        score >= 80 -> MaterialTheme.colorScheme.primary
                        score >= 60 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                // Mini indicator
                CircularProgressIndicator(
                    progress = { score / 100f },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                    color = when {
                        score >= 80 -> MaterialTheme.colorScheme.primary
                        score >= 60 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ScoreBreakdownRow(category: String, score: Int) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = getCategoryDisplayName(category),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "$score",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    score >= 80 -> MaterialTheme.colorScheme.primary
                    score >= 60 -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.error
                }
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (score / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = when {
                score >= 80 -> MaterialTheme.colorScheme.primary
                score >= 60 -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.error
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )

    }
}

@Composable
fun StrengthCard(strength: String) {
    ExpressiveCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MaterialSymbolIcon(
                icon = MaterialSymbols.CHECK_CIRCLE,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = strength,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun ImprovementTipCard(tip: ImprovementTip) {
    ExpressiveCard(
        containerColor = when (tip.priority) {
            Priority.HIGH -> MaterialTheme.colorScheme.errorContainer
            Priority.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
            Priority.LOW -> MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(12.dp),
        contentPadding = 16.dp
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tip.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                AssistChip(
                    onClick = {},
                    label = { Text("+${tip.potentialScoreGain} pts") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        labelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tip.description,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.LIGHTBULB,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = tip.actionable,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
@Composable
fun HealthLevel.displayName(): String = when (this) {
    HealthLevel.EXCELLENT -> stringResource(R.string.health_level_excellent)
    HealthLevel.GOOD -> stringResource(R.string.health_level_good)
    HealthLevel.FAIR -> stringResource(R.string.health_level_fair)
    HealthLevel.NEEDS_WORK -> stringResource(R.string.health_level_needs_work)
    HealthLevel.CRITICAL -> stringResource(R.string.health_level_critical)
}

@Composable
fun getCategoryDisplayName(category: String): String = when (category) {
    "Savings Rate" -> stringResource(R.string.health_cat_savings_rate)
    "Emergency Fund" -> stringResource(R.string.health_cat_emergency_fund)
    "Budget Adherence" -> stringResource(R.string.health_cat_budget_adherence)
    "Goal Progress" -> stringResource(R.string.health_cat_goal_progress)
    "Debt Management" -> stringResource(R.string.health_cat_debt_management)
    else -> category
}
