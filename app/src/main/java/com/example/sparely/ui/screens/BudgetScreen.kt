package com.example.sparely.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.sparely.domain.model.*
import com.example.sparely.domain.logic.BudgetEngine
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.components.SingleLineText
import com.example.sparely.ui.components.SparelyBottomSheet
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.state.SparelyUiState
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.critical
import com.example.sparely.ui.theme.criticalContainer
import com.example.sparely.ui.components.SparelyTextField
import com.example.sparely.ui.components.SparelyTonalButton
import com.example.sparely.ui.theme.spacing
import com.example.sparely.ui.theme.getCategoryColor
import com.example.sparely.ui.theme.getCategoryIcon
import com.example.sparely.ui.theme.onCriticalContainer
import com.example.sparely.ui.theme.onSuccessContainer
import com.example.sparely.ui.theme.onWarningContainer
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.theme.successContainer
import com.example.sparely.ui.theme.warning
import com.example.sparely.ui.theme.warningContainer
import com.example.sparely.ui.components.SparelyExpressiveDropdown
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import com.example.sparely.ui.utils.toSafeDouble
import com.example.sparely.ui.utils.filterCurrencyInput
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.utils.displayName
import com.sparely.app.R
@Composable
fun BudgetScreen(
    uiState: SparelyUiState,
    onAddBudget: (BudgetInput) -> Unit,
    onUpdateBudget: (CategoryBudget) -> Unit,
    onDeleteBudget: (Long) -> Unit,
    onNavigateBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var budgetToEdit by remember { mutableStateOf<CategoryBudget?>(null) }
    var budgetToDelete by remember { mutableStateOf<CategoryBudget?>(null) }
    val budgetLookup = remember(uiState.budgets) {
        uiState.budgets.associateBy { it.category to it.yearMonth }
    }
    val suggestions = uiState.budgetSuggestions
    val summary = uiState.budgetSummary
    val currentMonth = YearMonth.now()
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Add budget button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SparelyButton(
                    onClick = { showAddDialog = true },
                    icon = {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.ADD,
                            contentDescription = stringResource(R.string.budget_add_budget),
                            size = 18.dp
                        )
                    }
                ) {
                    Text(text = stringResource(R.string.budget_add_budget))
                }
            }
        }

        summary?.let {
            item {
                BudgetSummaryCard(it)
            }
        }
        
        // Warning if total budgets exceed monthly income
        val totalBudgets = uiState.budgets.filter { it.isActive && it.yearMonth == currentMonth }.sumOf { it.monthlyLimit }
        val monthlyIncome = uiState.settings.monthlyIncome
        if (totalBudgets > monthlyIncome && monthlyIncome > 0.0) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.WARNING,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            size = 24.dp
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.budget_exceeds_income_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = stringResource(
                                    R.string.budget_exceeds_income_desc,
                                    totalBudgets.formatCurrency(),
                                    monthlyIncome.formatCurrency()
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        if (uiState.preemptiveWarnings.isNotEmpty()) {
            items(uiState.preemptiveWarnings, key = { "warning_${it.category.name}_${it.daysUntilIssue}" }) { warning ->
                PreemptiveWarningBanner(warning)
            }
        }

        if (uiState.budgetForecasts.isNotEmpty()) {
            item {
                BudgetSectionHeader(
                    title = stringResource(R.string.budget_forecast_section_title),
                    subtitle = stringResource(R.string.budget_forecast_section_desc),
                    icon = MaterialSymbols.TRENDING_UP
                )
            }

            items(uiState.budgetForecasts, key = { "forecast_${it.category.name}" }) { forecast ->
                BudgetForecastCard(forecast)
            }
        }

        // Filter suggestions to only show those with meaningful differences
        val meaningfulSuggestions = suggestions.filter { suggestion ->
            val existing = budgetLookup[suggestion.category to currentMonth]
            if (existing == null) {
                // Show if no budget exists
                true
            } else {
                // Only show if difference is significant (more than $1)
                abs(suggestion.suggestedLimit - existing.monthlyLimit) >= 1.0
            }
        }

        if (meaningfulSuggestions.isNotEmpty()) {
            item {
                BudgetSectionHeader(
                    title = stringResource(R.string.budget_smart_suggestions_title),
                    subtitle = stringResource(R.string.budget_smart_suggestions_desc),
                    icon = MaterialSymbols.PIE_CHART
                )
            }

            items(meaningfulSuggestions, key = { it.category.name }) { suggestion ->
                val existing = budgetLookup[suggestion.category to currentMonth]
                BudgetSuggestionCard(
                    suggestion = suggestion,
                    currentBudget = existing,
                    onApply = { amount ->
                        val sanitized = amount.coerceAtLeast(0.0)
                        if (existing != null) {
                            onUpdateBudget(existing.copy(monthlyLimit = sanitized))
                        } else {
                            onAddBudget(BudgetInput(suggestion.category, sanitized))
                        }
                    }
                )
            }
        }

        if (summary?.categoryStatuses?.isNotEmpty() == true) {
            item {
                BudgetSectionHeader(
                    title = stringResource(R.string.budget_category_budgets_title),
                    subtitle = stringResource(R.string.budget_active_budgets_count, summary.categoryStatuses.size),
                    icon = MaterialSymbols.ACCOUNT_BALANCE_WALLET
                )
            }

            items(summary.categoryStatuses) { status ->
                val budget = budgetLookup[status.category to status.yearMonth]
                CategoryBudgetCard(
                    status = status,
                    onEdit = {
                        budget?.let { budgetToEdit = it }
                    },
                    onDelete = {
                        budget?.let { budgetToDelete = it }
                    }
                )
            }
        }

        if (uiState.budgets.isEmpty()) {
            item {
                EmptyBudgetState(onAddBudget = { showAddDialog = true })
            }
        }
    }

    if (showAddDialog) {
        AddBudgetDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { budgetInput ->
                onAddBudget(budgetInput)
                showAddDialog = false
            }
        )
    }

    budgetToEdit?.let { editable ->
        EditBudgetDialog(
            budget = editable,
            onDismiss = { budgetToEdit = null },
            onConfirm = { amount ->
                onUpdateBudget(editable.copy(monthlyLimit = amount))
                budgetToEdit = null
            },
            onDelete = {
                onDeleteBudget(editable.id)
                budgetToEdit = null
            }
        )
    }

    budgetToDelete?.let { budget ->
        DeleteBudgetConfirmationDialog(
            budget = budget,
            onConfirm = {
                onDeleteBudget(budget.id)
                budgetToDelete = null
            },
            onDismiss = { budgetToDelete = null }
        )
    }
}

@Composable
fun BudgetSummaryCard(summary: BudgetSummary) {
    val spacing = MaterialTheme.spacing
    
    // Modern Flat Card with Donut Chart
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween, // Title Left, Status Right
                verticalAlignment = Alignment.CenterVertically
            ) {
                 Column {
                    val currentMonthName = YearMonth.now().month.name.lowercase().replaceFirstChar { it.uppercase() }
                    Text(
                        text = stringResource(R.string.budget_month_budget_title, currentMonthName),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                         text = stringResource(R.string.budget_remaining_and_days, summary.totalRemaining.formatCurrency(), YearMonth.now().lengthOfMonth() - LocalDate.now().dayOfMonth),
                         style = MaterialTheme.typography.titleMedium,
                         color = MaterialTheme.colorScheme.primary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when (summary.overallHealth) {
                                BudgetHealthStatus.HEALTHY -> MaterialTheme.colorScheme.primaryContainer
                                BudgetHealthStatus.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.errorContainer
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = summary.overallHealth.displayName(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (summary.overallHealth) {
                            BudgetHealthStatus.HEALTHY -> MaterialTheme.colorScheme.onPrimaryContainer
                            BudgetHealthStatus.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Donut Chart
            Box(contentAlignment = Alignment.Center) {
                BudgetDonutChart(
                    summary = summary,
                    modifier = Modifier.size(220.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.budget_spent_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = summary.totalSpent.formatCurrency(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.budget_spent_of, summary.totalBudget.formatCurrency()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Legend (Top 3 + Others)
            val sortedCategories = summary.categoryStatuses.sortedByDescending { it.percentageUsed }
            val topCategories = sortedCategories.take(3)
            
            if (topCategories.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    for (catStatus in topCategories) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(getCategoryColor(catStatus.category), CircleShape)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = catStatus.category.displayName(),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                             Text(
                                text = catStatus.percentageUsed.formatPercent(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetDonutChart(
    summary: BudgetSummary,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    // Sort logic should match legend
    val sortedCategories = remember(summary) { summary.categoryStatuses.sortedByDescending { it.percentageUsed } }

    Canvas(modifier = modifier) {
        val strokeWidth = 24.dp.toPx()
        val radius = size.minDimension / 2 - strokeWidth / 2
        val center = Offset(size.width / 2, size.height / 2)
        
        // Background Ring
        drawCircle(
            color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        var startAngle = -90f
        val total = summary.totalBudget.coerceAtLeast(1.0)
        
        for (status in sortedCategories) {
            val sweepAngle = ((status.spent / total) * 360f).toFloat()
            if (sweepAngle > 0) {
                drawArc(
                    color = getCategoryColor(status.category, colorScheme),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
                startAngle += sweepAngle
            }
        }
    }
}

// Category icons and colors are now sourced from com.example.sparely.ui.theme.CategoryUtils

@Composable
fun CategoryBudgetCard(
    status: BudgetStatus,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val categoryColor = getCategoryColor(status.category)
    val categoryIcon = getCategoryIcon(status.category)
    
    val statusColor = when (status.status) {
        BudgetHealthStatus.HEALTHY -> colorScheme.success
        BudgetHealthStatus.WARNING -> colorScheme.warning
        BudgetHealthStatus.CRITICAL -> colorScheme.critical
        BudgetHealthStatus.OVER_BUDGET -> colorScheme.critical
    }
    
    val progressValue = status.percentageUsed.toFloat().coerceIn(0f, 1f)

    ExpressiveCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = colorScheme.surfaceContainerHigh,
        contentPadding = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle Gradient Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                categoryColor.copy(alpha = 0.08f),
                                colorScheme.surface.copy(alpha = 0.5f)
                            )
                        )
                    )
            )

            // Watermark Icon
            MaterialSymbolIcon(
                icon = categoryIcon,
                contentDescription = null,
                size = 140.dp,
                tint = categoryColor.copy(alpha = 0.06f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 30.dp, y = 10.dp)
            )

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon Circle
                        Surface(
                            shape = CircleShape,
                            color = categoryColor.copy(alpha = 0.15f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                MaterialSymbolIcon(
                                    icon = categoryIcon,
                                    contentDescription = null,
                                    size = 24.dp,
                                    tint = categoryColor
                                )
                            }
                        }

                        Column {
                            Text(
                                text = status.category.displayName(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusBadge(status.status)
                            }
                        }
                    }

                    // Action Buttons

                }

                // Balance Display
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.budget_spent_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = status.spent.formatCurrency(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = statusColor
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(R.string.budget_category_budgets_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = status.limit.formatCurrency(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                        }
                    }
                }

                // Progress Bar
                LinearProgressIndicator(
                    progress = { progressValue },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = statusColor,
                    trackColor = colorScheme.surfaceVariant
                )

                // Footer Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (status.isOverBudget) {
                            stringResource(R.string.budget_over_by, (status.spent - status.limit).formatCurrency())
                        } else {
                            stringResource(R.string.budget_remaining_and_days, status.remaining.formatCurrency(), status.daysRemainingInMonth)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status.isOverBudget) colorScheme.error else colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = status.percentageUsed.formatPercent(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SparelyButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        containerColor = colorScheme.errorContainer,
                        contentColor = colorScheme.error,
                        icon = {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.DELETE,
                                contentDescription = null,
                                size = 18.dp
                            )
                        }
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                    SparelyButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                        icon = {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.EDIT,
                                contentDescription = null,
                                size = 18.dp
                            )
                        }
                    ) {
                        Text(stringResource(R.string.edit))
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetSuggestionCard(
    suggestion: BudgetSuggestion,
    currentBudget: CategoryBudget?,
    onApply: (Double) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val categoryColor = getCategoryColor(suggestion.category)
    val categoryIcon = getCategoryIcon(suggestion.category)
    
    val difference = currentBudget?.let { suggestion.suggestedLimit - it.monthlyLimit }
    val differenceLabel = when {
        difference == null && currentBudget == null -> stringResource(R.string.budget_suggestion_new)
        difference == null -> stringResource(R.string.budget_suggestion_matches)
        abs(difference) < 1.0 -> stringResource(R.string.budget_suggestion_similar)
        difference > 0 -> stringResource(R.string.budget_suggestion_increase, abs(difference).formatCurrency())
        else -> stringResource(R.string.budget_suggestion_reduce, abs(difference).formatCurrency())
    }
    val differenceColor = when {
        difference == null -> colorScheme.tertiary
        abs(difference) < 1.0 -> colorScheme.onSurfaceVariant
        difference > 0 -> colorScheme.error
        else -> colorScheme.primary
    }
    
    val confidenceColor = when (suggestion.confidence) {
        SuggestionConfidence.HIGH -> colorScheme.primary
        SuggestionConfidence.MEDIUM -> colorScheme.secondary
        SuggestionConfidence.LOW -> colorScheme.onSurfaceVariant
    }

    ExpressiveCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = colorScheme.surfaceContainerHigh,
        contentPadding = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle Gradient Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                categoryColor.copy(alpha = 0.06f),
                                colorScheme.surface.copy(alpha = 0.5f)
                            )
                        )
                    )
            )

            // Watermark Icon
            MaterialSymbolIcon(
                icon = MaterialSymbols.PIE_CHART,
                contentDescription = null,
                size = 120.dp,
                tint = colorScheme.primary.copy(alpha = 0.05f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 20.dp, y = (-10).dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon Circle
                        Surface(
                            shape = CircleShape,
                            color = categoryColor.copy(alpha = 0.15f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                MaterialSymbolIcon(
                                    icon = categoryIcon,
                                    contentDescription = null,
                                    size = 24.dp,
                                    tint = categoryColor
                                )
                            }
                        }

                        Column {
                            Text(
                                text = suggestion.category.displayName(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MaterialSymbolIcon(
                                    icon = MaterialSymbols.PIE_CHART,
                                    contentDescription = null,
                                    size = 14.dp,
                                    tint = confidenceColor
                                )
                                Text(
                                    text = stringResource(R.string.budget_suggestion_confidence_label, suggestion.confidence.displayName()),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = confidenceColor
                                )
                            }
                        }
                    }
                }

                // Suggested Amount
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.budget_label_suggested),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = suggestion.suggestedLimit.formatCurrency(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.primary
                    )
                }

                // Comparison Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.budget_label_current),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentBudget?.monthlyLimit?.formatCurrency() ?: stringResource(R.string.budget_label_not_set),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.budget_label_historic_avg),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = suggestion.historicalAverage.formatCurrency(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.budget_label_profile_target),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = suggestion.profileTarget.formatCurrency(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Difference Indicator
                Surface(
                    color = differenceColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MaterialSymbolIcon(
                            icon = when {
                                difference == null -> MaterialSymbols.ADD
                                difference > 0 -> MaterialSymbols.TRENDING_UP
                                difference < 0 -> MaterialSymbols.TRENDING_DOWN
                                else -> MaterialSymbols.CHECK
                            },
                            contentDescription = null,
                            size = 16.dp,
                            tint = differenceColor
                        )
                        Text(
                            text = differenceLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = differenceColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Rationale
                Text(
                    text = suggestion.rationale,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )

                // Apply Button
                SparelyButton(
                    onClick = { onApply(suggestion.suggestedLimit) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.CHECK,
                            contentDescription = null,
                            size = 18.dp
                        )
                    }
                ) {
                    Text(
                        text = if (currentBudget == null) stringResource(R.string.budget_button_create) else stringResource(R.string.budget_button_apply)
                    )
                }
            }
        }
    }
}

@Composable
fun BudgetForecastCard(forecast: BudgetEngine.BudgetForecast) {
    val colorScheme = MaterialTheme.colorScheme
    val categoryColor = getCategoryColor(forecast.category)
    val categoryIcon = getCategoryIcon(forecast.category)
    val isOverBudget = forecast.projectedOverspend > 0
    val statusColor = if (isOverBudget) colorScheme.error else colorScheme.success
    val confidenceColor = when (forecast.confidenceLevel) {
        SuggestionConfidence.HIGH -> colorScheme.primary
        SuggestionConfidence.MEDIUM -> colorScheme.secondary
        SuggestionConfidence.LOW -> colorScheme.onSurfaceVariant
    }

    ExpressiveCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = categoryColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            MaterialSymbolIcon(
                                icon = categoryIcon,
                                contentDescription = null,
                                size = 20.dp,
                                tint = categoryColor
                            )
                        }
                    }
                    Column {
                        Text(
                            text = forecast.category.displayName(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.budget_suggestion_confidence_label, forecast.confidenceLevel.displayName()),
                            style = MaterialTheme.typography.labelSmall,
                            color = confidenceColor
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.budget_forecast_predicted_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = forecast.predictedMonthEndSpending.formatCurrency(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Text(
                text = if (isOverBudget) {
                    stringResource(R.string.budget_forecast_projected_overspend, forecast.projectedOverspend.formatCurrency())
                } else {
                    stringResource(R.string.budget_forecast_on_pace)
                },
                style = MaterialTheme.typography.bodySmall,
                color = statusColor
            )

            forecast.daysUntilBudgetExhausted?.let { days ->
                Text(
                    text = stringResource(R.string.budget_forecast_days_until_exhausted, days),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PreemptiveWarningBanner(warning: BudgetEngine.PreemptiveBudgetWarning) {
    val colorScheme = MaterialTheme.colorScheme
    val (containerColor, contentColor, icon) = when (warning.severity) {
        AlertType.WARNING -> Triple(colorScheme.errorContainer, colorScheme.onErrorContainer, MaterialSymbols.WARNING)
        AlertType.SUCCESS -> Triple(colorScheme.successContainer, colorScheme.onSuccessContainer, MaterialSymbols.CHECK)
        AlertType.INFO -> Triple(colorScheme.surfaceContainerHigh, colorScheme.onSurface, MaterialSymbols.INFO)
    }

    Surface(
        color = containerColor,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MaterialSymbolIcon(
                icon = icon,
                contentDescription = null,
                tint = contentColor,
                size = 20.dp
            )
            Text(
                text = warning.message,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )
        }
    }
}

@Composable
private fun BudgetSectionHeader(
    title: String,
    subtitle: String,
    icon: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                MaterialSymbolIcon(
                    icon = icon,
                    contentDescription = null,
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatusBadge(status: BudgetHealthStatus) {
    val containerColor = when (status) {
        BudgetHealthStatus.HEALTHY -> MaterialTheme.colorScheme.successContainer
        BudgetHealthStatus.WARNING -> MaterialTheme.colorScheme.warningContainer
        BudgetHealthStatus.CRITICAL -> MaterialTheme.colorScheme.criticalContainer
        BudgetHealthStatus.OVER_BUDGET -> MaterialTheme.colorScheme.criticalContainer
    }
    val contentColor = when (status) {
        BudgetHealthStatus.HEALTHY -> MaterialTheme.colorScheme.onSuccessContainer
        BudgetHealthStatus.WARNING -> MaterialTheme.colorScheme.onWarningContainer
        BudgetHealthStatus.CRITICAL -> MaterialTheme.colorScheme.onCriticalContainer
        BudgetHealthStatus.OVER_BUDGET -> MaterialTheme.colorScheme.onCriticalContainer
    }
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = status.displayName(),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun EmptyBudgetState(onAddBudget: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerHigh
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Gradient Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colorScheme.primary.copy(alpha = 0.05f),
                                colorScheme.surface.copy(alpha = 0.5f)
                            )
                        )
                    )
            )

            // Watermark Icon
            MaterialSymbolIcon(
                icon = MaterialSymbols.ACCOUNT_BALANCE_WALLET,
                contentDescription = null,
                size = 180.dp,
                tint = colorScheme.primary.copy(alpha = 0.05f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 40.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.ACCOUNT_BALANCE_WALLET,
                            contentDescription = null,
                            size = 40.dp,
                            tint = colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.budget_create_first_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.budget_create_first_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                SparelyButton(
                    onClick = onAddBudget,
                    icon = {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.ADD,
                            contentDescription = null,
                            size = 18.dp
                        )
                    }
                ) {
                    Text(text = stringResource(R.string.budget_create_first_button))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBudgetDialog(
    onDismiss: () -> Unit,
    onConfirm: (BudgetInput) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(ExpenseCategory.GROCERIES) }
    var amount by remember { mutableStateOf("") }

    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(R.string.budget_set_budget_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

                SparelyExpressiveDropdown(
                    modifier = Modifier.fillMaxWidth(),
                    selectedOption = selectedCategory,
                    label = stringResource(R.string.onboarding_financial_category_label),
                    options = ExpenseCategory.entries,
                    onOptionSelected = { category ->
                        selectedCategory = category
                    },
                    optionLabel = { category ->
                        category.displayName()
                    },
                    optionIcon = { category ->
                        getCategoryIcon(category)
                    }
                )

            SparelyTextField(
                value = amount,
                onValueChange = { amount = it.filterCurrencyInput() },
                label = { Text(stringResource(R.string.budget_monthly_limit_label)) },
                prefix = { Text("$") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SparelyTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                SparelyButton(
                    onClick = {
                        amount.toSafeDouble()?.let { limitAmount ->
                            if (limitAmount > 0) {
                                onConfirm(BudgetInput(selectedCategory, limitAmount))
                            }
                        }
                    },
                    enabled = amount.toSafeDouble()?.let { it > 0 } == true,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBudgetDialog(
    budget: CategoryBudget,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
    onDelete: () -> Unit
) {
    var amount by remember { mutableStateOf(budget.monthlyLimit.toString()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismiss
    ) {
        if (showDeleteConfirmation) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.WARNING,
                        contentDescription = null,
                        size = 32.dp,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.budget_delete_confirmation_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = stringResource(R.string.budget_delete_confirmation_desc, budget.category.displayName()),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = stringResource(R.string.budget_delete_undone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SparelyTonalButton(
                        onClick = { showDeleteConfirmation = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    SparelyButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.budget_edit_budget_title, budget.category.displayName()),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                SparelyTextField(
                    value = amount,
                    onValueChange = { amount = it.filterCurrencyInput() },
                    label = { Text(stringResource(R.string.budget_monthly_limit_label)) },
                    prefix = { Text("$") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SparelyTonalButton(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                    SparelyTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }

                SparelyButton(
                    onClick = {
                        amount.toSafeDouble()?.let { value ->
                            if (value > 0) {
                                onConfirm(value)
                            }
                        }
                    },
                    enabled = amount.toSafeDouble()?.let { it > 0 } == true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}


@Composable
private fun BudgetHealthStatus.displayName(): String = when (this) {
    BudgetHealthStatus.HEALTHY -> stringResource(R.string.budget_status_on_track)
    BudgetHealthStatus.WARNING -> stringResource(R.string.budget_status_warning)
    BudgetHealthStatus.CRITICAL -> stringResource(R.string.budget_status_critical)
    BudgetHealthStatus.OVER_BUDGET -> stringResource(R.string.budget_status_over)
}




// Extension function for ExpenseCategory.displayName() - using the one from RecurringScreen.kt
// (It's defined as a public function there, so we can use it directly)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteBudgetConfirmationDialog(
    budget: CategoryBudget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val categoryColor = getCategoryColor(budget.category)
    val categoryIcon = getCategoryIcon(budget.category)
    
    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.WARNING,
                    contentDescription = null,
                    size = 32.dp,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.budget_delete_confirmation_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = stringResource(R.string.budget_delete_warning_desc, budget.category.displayName()),
                style = MaterialTheme.typography.bodyLarge
            )

            Surface(
                color = categoryColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MaterialSymbolIcon(
                        icon = categoryIcon,
                        contentDescription = null,
                        size = 24.dp,
                        tint = categoryColor
                    )
                    Column {
                        Text(
                            text = budget.category.displayName(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.budget_monthly_limit_stat, budget.monthlyLimit.formatCurrency()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.budget_delete_undone),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SparelyTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }

                SparelyButton(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}
