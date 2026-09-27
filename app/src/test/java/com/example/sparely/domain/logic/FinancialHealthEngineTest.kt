package com.example.sparely.domain.logic

import com.example.sparely.domain.model.AnalyticsSnapshot
import com.example.sparely.domain.model.LivingSituation
import com.example.sparely.domain.model.SparelySettings
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialHealthEngineTest {

    @Test
    fun `score_boosts_for_users_living_with_parents`() {
        // Setup: Minimal emergency fund (1 month of expenses)
        val income = 3000.0
        val expenseEstimate = income * 0.7 // 2100
        val emergencyFund = 2100.0 // Exactly 1 month covered
        
        val analytics = AnalyticsSnapshot(
            totalSpent = 0.0,
            totalEmergency = emergencyFund,
            averageMonthlyExpense = expenseEstimate
        )
        
        // Case 1: Standard User (Rent/Homeowner) -> Expect low score (need 3-6 months)
        val standardSettings = SparelySettings(
            monthlyIncome = income,
            livingSituation = LivingSituation.RENTING,
            age = 30
        )
        val standardScore = FinancialHealthEngine.calculateHealthScore(
            expenses = emptyList(),
            transfers = emptyList(),
            goals = emptyList(),
            budgetSummary = null,
            settings = standardSettings,
            analytics = analytics
        ).emergencyFundScore
        
        // Case 2: User with Parents -> Expect high score (target is only 2 months)
        // 1 month covered / 2 month target = 50% ratio -> ~50 score (linear) vs standard which would be ~40 or less.
        val parentSettings = SparelySettings(
            monthlyIncome = income,
            livingSituation = LivingSituation.WITH_PARENTS,
            age = 20
        )
        val parentScore = FinancialHealthEngine.calculateHealthScore(
            expenses = emptyList(),
            transfers = emptyList(),
            goals = emptyList(),
            budgetSummary = null,
            settings = parentSettings,
            analytics = analytics
        ).emergencyFundScore
        
        println("Standard Score: $standardScore")
        println("Parent Score: $parentScore")
        
        assertTrue("Living with parents should yield perfect emergency fund score (covered by safety net)", parentScore == 100)
    }
}
