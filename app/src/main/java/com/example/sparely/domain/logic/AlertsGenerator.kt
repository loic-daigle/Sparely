package com.example.sparely.domain.logic

import com.example.sparely.domain.model.AlertMessage
import com.example.sparely.domain.model.AlertType
import com.example.sparely.domain.model.AnalyticsSnapshot
import com.example.sparely.domain.model.Goal
import com.example.sparely.domain.model.RecommendationResult
import com.example.sparely.domain.model.SparelySettings
import com.example.sparely.ui.utils.formatPercent
import java.time.LocalDate
import kotlin.math.abs

object AlertsGenerator {
    fun buildAlerts(
        analytics: AnalyticsSnapshot,
        recommendation: RecommendationResult?,
        settings: SparelySettings,
        goals: List<Goal>
    ): List<AlertMessage> {
        val alerts = mutableListOf<AlertMessage>()

        recommendation?.let {
            val observedRate = if (analytics.totalSpent <= 0.0) 0.0 else analytics.totalReserved / analytics.totalSpent
            val recommendedRate = it.recommendedPercentages.total
            val delta = observedRate - recommendedRate
            if (delta < -0.03) {
                alerts += AlertMessage(
                    title = "Savings below target",
                    description = "Set aside ${observedRate.formatPercent(0)} of spending this month. Target is ${recommendedRate.formatPercent(0)}.",
                    type = AlertType.WARNING
                )
            } else if (delta > 0.05) {
                alerts += AlertMessage(
                    title = "Great job staying ahead",
                    description = "You are reserving ${observedRate.formatPercent(0)} of spending, above the ${recommendedRate.formatPercent(0)} guidance.",
                    type = AlertType.SUCCESS
                )
            }
        }

        val upcomingGoals = goals.filter { !it.archived && it.targetDate != null }
        val today = LocalDate.now()
        upcomingGoals.forEach { goal ->
            val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, goal.targetDate)
            if (daysUntil in 0..45 && goal.progressPercent < 0.6) {
                alerts += AlertMessage(
                    title = "${goal.title} needs attention",
                    description = "${goal.progressPercent.formatPercent(0)} complete with ${daysUntil} days left.",
                    type = AlertType.WARNING
                )
            } else if (goal.progressPercent >= 0.85 && !goal.archived) {
                alerts += AlertMessage(
                    title = "${goal.title} nearly achieved",
                    description = "${goal.progressPercent.formatPercent(0)} complete. Keep the streak going!",
                    type = AlertType.SUCCESS
                )
            }
        }

        if (analytics.averageMonthlyReserve <= 0.0 && analytics.totalSpent > 0) {
            alerts += AlertMessage(
                title = "No savings captured yet",
                description = "Record new purchases or adjust percentages so Sparely can track progress.",
                type = AlertType.INFO
            )
        }

        if (abs(settings.defaultPercentages.safeInvestmentSplit - 0.5) > 0.25 && recommendation == null) {
            alerts += AlertMessage(
                title = "Check investment split",
                description = "Safe vs. high-risk split is ${formatPercent(settings.defaultPercentages.safeInvestmentSplit)} safe. Auto mode can help rebalance.",
                type = AlertType.INFO
            )
        }

        return alerts
    }

}
