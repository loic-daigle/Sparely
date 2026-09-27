package com.example.sparely.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.domain.model.AssetCostProjection
import java.text.NumberFormat

@Composable
fun AssetCostProjectionCard(
    projection: AssetCostProjection?,
    modifier: Modifier = Modifier
) {
    if (projection == null) {
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance().apply {
            isGroupingUsed = false
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    ExpressiveCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 16.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Projected Cost",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Estimated spending based on recurring and historical expenses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Time horizon summary row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProjectionSummaryBox(
                    period = "3M",
                    amount = projection.totalProjected3Months,
                    currencyFormatter = currencyFormatter,
                    modifier = Modifier.weight(1f)
                )
                ProjectionSummaryBox(
                    period = "6M",
                    amount = projection.totalProjected6Months,
                    currencyFormatter = currencyFormatter,
                    modifier = Modifier.weight(1f)
                )
                ProjectionSummaryBox(
                    period = "12M",
                    amount = projection.totalProjected12Months,
                    currencyFormatter = currencyFormatter,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            // Budget status
            projection.costVsAssetPrice?.let { costVsPrice ->
                BudgetComparisonSection(
                    costVsPrice = costVsPrice,
                    currencyFormatter = currencyFormatter,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
            }

            // Tabs for detailed breakdown
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("3 Months") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("6 Months") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("12 Months") }
                )
            }

            // Monthly breakdown
            val displayMonths = when (selectedTab) {
                0 -> projection.monthlyProjections.take(3)
                1 -> projection.monthlyProjections.take(6)
                else -> projection.monthlyProjections.take(12)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                displayMonths.forEach { monthlyProjection ->
                    MonthlyProjectionRow(
                        monthlyProjection = monthlyProjection,
                        currencyFormatter = currencyFormatter
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectionSummaryBox(
    period: String,
    amount: Double,
    currencyFormatter: NumberFormat,
    modifier: Modifier = Modifier
) {
    ExpressiveCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = period,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = currencyFormatter.format(amount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BudgetComparisonSection(
    costVsPrice: com.example.sparely.domain.model.CostVsTargetPrice,
    currencyFormatter: NumberFormat,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "vs. Target Price",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Target Price",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = currencyFormatter.format(costVsPrice.targetPrice),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Projected (12M)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = currencyFormatter.format(costVsPrice.projectedIn12Months),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (costVsPrice.isOverBudget)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress indicator
        val percentageOfTarget = (costVsPrice.percentageOfTarget / 100.0).coerceIn(0.0, 1.0)
        androidx.compose.material3.LinearProgressIndicator(
            progress = { percentageOfTarget.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = when {
                costVsPrice.isOverBudget -> MaterialTheme.colorScheme.error
                percentageOfTarget > 0.7 -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.primary
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${(costVsPrice.percentageOfTarget / 100).formatPercent(0)} of target",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!costVsPrice.isOverBudget) {
                Text(
                    text = "Remaining: ${currencyFormatter.format(costVsPrice.remainingBudget)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun MonthlyProjectionRow(
    monthlyProjection: com.example.sparely.domain.model.MonthlyProjection,
    currencyFormatter: NumberFormat
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = monthlyProjection.month.format(
                    java.time.format.DateTimeFormatter.ofPattern("MMM yyyy")
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = currencyFormatter.format(monthlyProjection.projectedAmount),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (monthlyProjection.recurringContribution > 0) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Recurring",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currencyFormatter.format(monthlyProjection.recurringContribution),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (monthlyProjection.historicalAverage > 0) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Historical Avg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currencyFormatter.format(monthlyProjection.historicalAverage),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}
