package com.example.sparely.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.logic.CashflowEngine
import com.example.sparely.domain.logic.SmartInsightEngine
import com.example.sparely.domain.logic.SpendingPatternEngine
import com.example.sparely.domain.model.displayName
import com.example.sparely.domain.model.Expense
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.utils.DateUtils
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.components.SparelyButton
import com.sparely.app.R
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    cashflowForecast: CashflowEngine.CashflowForecast?,
    spendingPatterns: SpendingPatternEngine.SpendingPatternResult?,
    // New smart insight parameters
    recurringPatterns: List<SmartInsightEngine.RecurringPatternInsight> = emptyList(),
    seasonalInsights: List<SmartInsightEngine.SeasonalInsight> = emptyList(),
    idleMoneyInsight: SmartInsightEngine.IdleMoneyInsight? = null,
    uniqueExpenses: List<SmartInsightEngine.UniqueExpenseInsight> = emptyList(),
    onTransferToSavings: (amount: Double) -> Unit = {},
    onNavigateBack: () -> Unit,
    onAnomalyClick: (Expense) -> Unit = {}
) {
    // Note: This screen no longer includes its own TopAppBar to avoid duplicate headers
    // The parent navigation scaffold should provide the app bar
    
    if (cashflowForecast == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 900.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // 1. Idle Money Suggestion (if available) - Top priority
            if (idleMoneyInsight != null) {
                item {
                    IdleMoneyCard(
                        insight = idleMoneyInsight,
                        onTransfer = { onTransferToSavings(idleMoneyInsight.suggestedTransferAmount) }
                    )
                }
            }

            // 2. Detected Recurring Patterns
            if (recurringPatterns.isNotEmpty()) {
                item {
                    RecurringPatternsCard(patterns = recurringPatterns)
                }
            }

            // 3. Seasonal Insights
            if (seasonalInsights.isNotEmpty()) {
                item {
                    SeasonalInsightsCard(insights = seasonalInsights)
                }
            }

            // 4. Unique/One-time Expenses
            if (uniqueExpenses.isNotEmpty()) {
                item {
                    UniqueExpensesCard(expenses = uniqueExpenses)
                }
            }

            // 5. Cashflow Forecast Chart
            item {
                CashflowForecastCard(cashflowForecast)
            }

            // 6. Key Metrics Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MetricCard(
                        title = stringResource(R.string.insights_projected_balance),
                        value = cashflowForecast.projectedBalance30Days.formatCurrency(),
                        modifier = Modifier.weight(1f),
                        isPositive = cashflowForecast.projectedBalance30Days > 0
                    )
                    MetricCard(
                        title = stringResource(R.string.insights_burn_rate),
                        value = "${cashflowForecast.dailyBurnRate.formatCurrency()}${stringResource(R.string.insights_per_day, stringResource(R.string.insights_day))}",
                        modifier = Modifier.weight(1f),
                        isPositive = false
                    )
                }
            }
            
            // 7. Runway & Safe to Spend Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MetricCard(
                        title = stringResource(R.string.insights_safe_to_spend),
                        value = cashflowForecast.safeToSpend.formatCurrency(),
                        modifier = Modifier.weight(1f),
                        isPositive = true
                    )
                     MetricCard(
                        title = stringResource(R.string.insights_runway),
                        value = if (cashflowForecast.runwayDays > 365) stringResource(R.string.insights_runway_over_year) else "${cashflowForecast.runwayDays} ${stringResource(R.string.insights_days)}",
                        modifier = Modifier.weight(1f),
                        isPositive = cashflowForecast.runwayDays > 30
                    )
                }
            }

            // 8. Spending Anomalies
            item {
                Text(
                    text = stringResource(R.string.insights_anomalies_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (spendingPatterns?.anomalies.isNullOrEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.insights_no_anomalies),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(spendingPatterns!!.anomalies.size) { index ->
                    val anomaly = spendingPatterns.anomalies[index]
                    AnomalyItem(anomaly, onClick = { onAnomalyClick(anomaly.expense) })
                    if (index < spendingPatterns.anomalies.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
            
            // 9. Category Velocity (Top Growers)
             item {
                Text(
                    text = stringResource(R.string.insights_velocity_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }
            
            if (spendingPatterns != null && spendingPatterns.categoryVelocity.isNotEmpty()) {
                 // Sort by daily rate descending to show highest spenders
                 val sortedVelocity = spendingPatterns.categoryVelocity.entries
                    .sortedByDescending { it.value.dailyRate }
                    .take(3)
                    
                 items(sortedVelocity.size) { index ->
                    val entry = sortedVelocity[index]
                    CategoryVelocityItem(entry.value)
                 }
            }
            }
        }
    }
}

// === New Insight Cards ===

@Composable
private fun IdleMoneyCard(
    insight: SmartInsightEngine.IdleMoneyInsight,
    onTransfer: () -> Unit
) {
    ExpressiveCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 16.dp
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.SAVINGS,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.insights_idle_money_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(
                            R.string.insights_idle_money_desc,
                            insight.excessAmount.formatCurrency(),
                            insight.estimatedMonthlyInterest.formatCurrency()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.insights_idle_money_suggested),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = insight.suggestedTransferAmount.formatCurrency(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                SparelyButton(
                    onClick = onTransfer
                ) {
                    Text(stringResource(R.string.insights_idle_money_transfer))
                }
            }
            
            // Growth Projections
            if (insight.growthProjections.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.insights_idle_money_growth_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for ((months, amount) in insight.growthProjections.toList().sortedBy { it.first }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when(months) {
                                    6 -> "6 Mo"
                                    12 -> "1 Yr"
                                    24 -> "2 Yr"
                                    else -> "$months Mo"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = amount.formatCurrency(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Projected earnings info
            if (insight.estimatedAnnualInterest > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.TRENDING_UP,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            R.string.insights_idle_money_projected,
                            insight.estimatedAnnualInterest.formatCurrency(),
                            insight.suggestedVault?.effectiveApy?.formatPercent() ?: "4.5%"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecurringPatternsCard(patterns: List<SmartInsightEngine.RecurringPatternInsight>) {
    ExpressiveCard(
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 16.dp
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.SYNC,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.insights_recurring_patterns),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            patterns.take(5).forEachIndexed { index, pattern ->
                RecurringPatternItem(pattern)
                if (index < patterns.size - 1 && index < 4) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RecurringPatternItem(pattern: SmartInsightEngine.RecurringPatternInsight) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pattern.description,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pattern.frequency.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "•",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.insights_recurring_each, pattern.averageAmount.formatCurrency()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = pattern.totalMonthlyImpact.formatCurrency(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.insights_recurring_per_month),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SeasonalInsightsCard(insights: List<SmartInsightEngine.SeasonalInsight>) {
    ExpressiveCard(
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 16.dp
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.CALENDAR_MONTH,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.insights_seasonal_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            insights.take(4).forEach { insight ->
                SeasonalInsightItem(insight)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SeasonalInsightItem(insight: SmartInsightEngine.SeasonalInsight) {
    val isHigher = insight.expectedChangePercent > 0
    val changeColor = if (isHigher) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.success
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = insight.category.displayName(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            val monthResId = when(insight.month) {
                1 -> R.string.month_1
                2 -> R.string.month_2
                3 -> R.string.month_3
                4 -> R.string.month_4
                5 -> R.string.month_5
                6 -> R.string.month_6
                7 -> R.string.month_7
                8 -> R.string.month_8
                9 -> R.string.month_9
                10 -> R.string.month_10
                11 -> R.string.month_11
                12 -> R.string.month_12
                else -> R.string.month_1
            }
            val localizedMonth = stringResource(monthResId)
            Text(
                text = if (isHigher) {
                    stringResource(R.string.insights_seasonal_higher, abs(insight.expectedChangePercent).toInt(), localizedMonth)
                } else {
                    stringResource(R.string.insights_seasonal_lower, abs(insight.expectedChangePercent).toInt(), localizedMonth)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbolIcon(
                icon = if (isHigher) MaterialSymbols.TRENDING_UP else MaterialSymbols.TRENDING_DOWN,
                contentDescription = null,
                tint = changeColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${if (isHigher) "+" else ""}${insight.expectedChangePercent.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = changeColor
            )
        }
    }
}

@Composable
private fun UniqueExpensesCard(expenses: List<SmartInsightEngine.UniqueExpenseInsight>) {
    ExpressiveCard(
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 16.dp
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.INFO,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.insights_unique_expenses),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.insights_unique_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            expenses.take(3).forEachIndexed { index, uniqueExpense ->
                UniqueExpenseItem(uniqueExpense)
                if (index < expenses.size - 1 && index < 2) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun UniqueExpenseItem(uniqueExpense: SmartInsightEngine.UniqueExpenseInsight) {
    val formatter = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = uniqueExpense.expense.description,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            val reasonText = when(uniqueExpense.reasonType) {
                SmartInsightEngine.UniqueReason.UNUSUALLY_LARGE -> {
                    stringResource(
                        R.string.insights_unique_reason_large,
                        uniqueExpense.zScore,
                        uniqueExpense.expense.category.displayName()
                    )
                }
                SmartInsightEngine.UniqueReason.MAJOR_PURCHASE -> {
                    stringResource(
                        R.string.insights_unique_reason_major,
                        uniqueExpense.expense.category.displayName()
                    )
                }
            }
            Text(
                text = reasonText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = uniqueExpense.expense.date.format(formatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.insights_unique_one_time),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
        Text(
            text = uniqueExpense.expense.amount.formatCurrency(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

// === Existing Components (kept from original) ===

@Composable
private fun CashflowForecastCard(forecast: CashflowEngine.CashflowForecast) {
    ExpressiveCard(
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 16.dp
    ) {
        Column {
            Text(
                text = stringResource(R.string.insights_forecast_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                CashflowChart(forecast.weeklyProjections, forecast.currentBalance)
            }
        }
    }
}

@Composable
private fun CashflowChart(points: List<CashflowEngine.WeeklyProjection>, currentBalance: Double) {
    if (points.isEmpty()) return

    val density = LocalDensity.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface
    val errorColor = MaterialTheme.colorScheme.error

    // State for selected point (tooltip)
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    // Prepare data: Index 0 is Today (Current Balance), 1..N are projections
    val values = remember(points, currentBalance) {
        listOf(currentBalance) + points.map { it.projectedEndBalance }
    }
    
    val labels = remember(points) {
        val today = LocalDate.now()
        val list = mutableListOf(today)
        // Verify if weeklyProjections are week-end or week-start. 
        // Based on BudgetEngine, weekStartDate is the start. 
        // We'll approximate the "point" as the start of the week for visualization.
        list.addAll(points.map { it.weekStartDate.plusDays(7) })
        list
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val stepX = if (values.size > 1) width / (values.size - 1) else width
        
        // Touch Handler
        val touchModifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset: Offset ->
                        val index = indexForX(offset.x, stepX, values.lastIndex)
                        selectedIndex = index
                    },
                    onTap = { offset: Offset ->
                         val index = indexForX(offset.x, stepX, values.lastIndex)
                         selectedIndex = index
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset: Offset ->
                        val index = indexForX(offset.x, stepX, values.lastIndex)
                        selectedIndex = index
                    },
                    onDrag = { change: PointerInputChange, _: Offset ->
                        val index = indexForX(change.position.x, stepX, values.lastIndex)
                        selectedIndex = index
                    },
                    onDragEnd = {
                        // Optional: clear selection or keep it? Keeping it enables reading.
                        // selectedIndex = null 
                    }
                )
            }

        Canvas(modifier = touchModifier) {
            val maxVal = values.maxOrNull() ?: 1.0
            val minVal = values.minOrNull() ?: 0.0
            val range = maxAndMinRange(maxVal, minVal)

            // Reserve top space for tooltip (approx 60.dp)
            val chartTopPadding = 50.dp.toPx()
            val chartHeight = height - chartTopPadding

            // Draw Grid
            val gridLines = 4
            for (i in 0..gridLines) {
                val y = chartTopPadding + chartHeight - (i.toFloat() / gridLines) * chartHeight
                drawLine(
                    color = onSurface.copy(alpha = 0.1f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val path = Path()
            values.forEachIndexed { index, value ->
                val x = index * stepX
                val normalizedY = if (range.second - range.first > 0) {
                    (value - range.first) / (range.second - range.first)
                } else 0.5
                // y is mapped from topPadding to height
                val y = chartTopPadding + chartHeight - (normalizedY * chartHeight).toFloat()

                // Draw Path
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }

                // Draw Dots
                drawCircle(
                    color = primaryColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )

                // Draw Selection Highlight
                if (selectedIndex == index) {
                    drawLine(
                        color = onSurface.copy(alpha = 0.5f),
                        start = Offset(x, chartTopPadding),
                        end = Offset(x, height),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                    drawCircle(
                        color = surfaceColor, // inner white
                        radius = 6.dp.toPx(),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = primaryColor, // outer ring
                        radius = 6.dp.toPx(),
                        center = Offset(x, y),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Draw Tooltip via Composable Overlay
        if (selectedIndex != null) {
            val index = selectedIndex!!
            val value = values[index]
            val date = labels.getOrNull(index) ?: LocalDate.now()
            
            // Calculate Position
            val xPos = index * stepX
            // Tooltip width approx (allows handling edge cases)
            val tooltipWidth = 140.dp 
            val xOffsetDp = with(density) { xPos.toDp() }
            
            // Adjust to keep on screen
            val centerOffset = xOffsetDp - (tooltipWidth / 2)
            val finalOffset = centerOffset.coerceAtMost(maxWidth - tooltipWidth).coerceAtLeast(0.dp)

            Box(
                modifier = Modifier
                    .offset(x = finalOffset, y = 0.dp) // Position at top of box (safe area)
                    .width(tooltipWidth)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                    .padding(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = DateUtils.formatDate(date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value.formatCurrency(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (value < 0) errorColor else onSurface
                    )
                }
            }
        }
    }
}

private fun maxAndMinRange(max: Double, min: Double): Pair<Double, Double> {
    val padding = (max - min) * 0.1
    return Pair((min - padding).coerceAtLeast(0.0), max + padding)
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    isPositive: Boolean = true
) {
    ExpressiveCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        contentPadding = 12.dp
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isPositive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun AnomalyItem(anomaly: SpendingPatternEngine.SpendingAnomaly, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MaterialSymbolIcon(
            icon = MaterialSymbols.WARNING,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = anomaly.expense.description,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = DateUtils.formatDate(anomaly.expense.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = anomaly.expense.amount.formatCurrency(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.insights_unusual),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun CategoryVelocityItem(velocityData: SpendingPatternEngine.CategoryVelocity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
             Text(
                text = velocityData.category.displayName(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
             Text(
                text = "${stringResource(R.string.insights_projected_balance)}: ${velocityData.projectedMonthlyTotal.formatCurrency()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${velocityData.dailyRate.formatCurrency()}${stringResource(R.string.insights_per_day, stringResource(R.string.insights_day))}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Maps a touch x-position to a data index without crashing on zero-width layouts. */
private fun indexForX(x: Float, stepX: Float, lastIndex: Int): Int {
    if (lastIndex <= 0 || stepX <= 0f || !stepX.isFinite() || !x.isFinite()) return 0
    return (x / stepX).roundToInt().coerceIn(0, lastIndex)
}
