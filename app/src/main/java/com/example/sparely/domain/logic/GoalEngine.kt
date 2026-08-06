package com.example.sparely.domain.logic

import com.example.sparely.domain.model.Goal
import com.example.sparely.ui.utils.formatCurrency
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

enum class GoalFeasibilityStatus {
    FEASIBLE,
    STRETCH,      // Doable but tight (> 30% of income or requires strict adherence)
    IMPOSSIBLE,   // Mathematically impossible by target date
    ON_TRACK,     // Current progress is ahead of schedule
    AT_RISK       // Falling behind
}

data class FeasibilityReport(
    val status: GoalFeasibilityStatus,
    val requiredMonthlyContribution: Double,
    val projectedCompletionDate: LocalDate?,
    val message: String
)

object GoalEngine {

    fun checkFeasibility(
        goal: Goal,
        monthlyAvailableAmount: Double, // Amount user can theoretically allocate
        currentMonthlyContribution: Double
    ): FeasibilityReport {
        if (goal.targetAmount <= 0) return FeasibilityReport(GoalFeasibilityStatus.FEASIBLE, 0.0, LocalDate.now(), "Target is zero.")
        
        val remaining = (goal.targetAmount - goal.progressAmount).coerceAtLeast(0.0)
        if (remaining <= 0) return FeasibilityReport(GoalFeasibilityStatus.FEASIBLE, 0.0, LocalDate.now(), "Goal already reached!")

        val targetDate = goal.targetDate
        
        // Scenario 1: No target date -> Always feasible if some income
        if (targetDate == null) {
            val monthsToFinish = if (currentMonthlyContribution > 0) ceil(remaining / currentMonthlyContribution).toLong() else 0
            val projected = if (currentMonthlyContribution > 0) LocalDate.now().plusMonths(monthsToFinish) else null
            return FeasibilityReport(
                status = GoalFeasibilityStatus.FEASIBLE,
                requiredMonthlyContribution = 0.0,
                projectedCompletionDate = projected,
                message = "No deadline set. At current pace, done by $projected."
            )
        }

        val monthsRemaining = ChronoUnit.MONTHS.between(LocalDate.now(), targetDate).coerceAtLeast(1)
        val requiredMonthly = remaining / monthsRemaining

        val status = when {
            requiredMonthly > monthlyAvailableAmount + 10.0 -> GoalFeasibilityStatus.IMPOSSIBLE
            requiredMonthly > monthlyAvailableAmount * 0.8 -> GoalFeasibilityStatus.STRETCH
            currentMonthlyContribution >= requiredMonthly -> GoalFeasibilityStatus.ON_TRACK
            else -> GoalFeasibilityStatus.AT_RISK
        }
        
        val projectedCompletion = if (currentMonthlyContribution > 0) {
            val months = ceil(remaining / currentMonthlyContribution).toLong()
            LocalDate.now().plusMonths(months)
        } else null

        val message = when (status) {
            GoalFeasibilityStatus.IMPOSSIBLE -> "You need ${requiredMonthly.formatCurrency("", 0)}/mo but only have ${monthlyAvailableAmount.formatCurrency("", 0)}. Extend the date?"
            GoalFeasibilityStatus.STRETCH -> "It's tight. You need ${requiredMonthly.formatCurrency("", 0)}/mo, which is most of your spare cash."
            GoalFeasibilityStatus.ON_TRACK -> "On track! You're contributing enough to finish by the deadline."
            GoalFeasibilityStatus.AT_RISK -> "You need ${requiredMonthly.formatCurrency("", 0)}/mo to hit the date, but currently adding ${currentMonthlyContribution.formatCurrency("", 0)}."
            else -> "Feasible."
        }

        return FeasibilityReport(status, requiredMonthly, projectedCompletion, message)
    }

}
