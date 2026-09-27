package com.example.sparely.domain.model

import java.time.YearMonth

/**
 * Represents a monthly cost projection for an asset.
 * Breaks down costs into recurring (from scheduled expenses) and historical (from past one-time expenses).
 */
data class MonthlyProjection(
    val month: YearMonth,
    val projectedAmount: Double,
    val recurringContribution: Double,
    val historicalAverage: Double
)

/**
 * Comprehensive cost projection for an asset over time.
 * Helps users understand how much they will spend on an asset over different time horizons.
 */
data class AssetCostProjection(
    val assetId: Long,
    val assetName: String,
    val assetPrice: Double? = null,  // Target price if set by user
    val monthlyProjections: List<MonthlyProjection>,
    val totalProjected3Months: Double,
    val totalProjected6Months: Double,
    val totalProjected12Months: Double,
    val averageMonthly: Double,
    val costVsAssetPrice: CostVsTargetPrice? = null  // null if assetPrice not set
)

/**
 * Comparison between projected costs and target asset price.
 */
data class CostVsTargetPrice(
    val targetPrice: Double,
    val projectedIn12Months: Double,
    val percentageOfTarget: Double,  // (projectedIn12Months / targetPrice) * 100
    val isOverBudget: Boolean,  // true if projected12Months >= targetPrice
    val remainingBudget: Double  // targetPrice - projectedIn12Months (can be negative)
)
