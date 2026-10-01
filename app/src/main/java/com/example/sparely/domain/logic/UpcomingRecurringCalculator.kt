package com.example.sparely.domain.logic

import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.UpcomingRecurringExpense
import com.example.sparely.domain.model.nextOccurrenceAfter
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Finds the active recurring expenses that fall due within the next [windowDays] days. */
object UpcomingRecurringCalculator {

    fun compute(
        recurring: List<RecurringExpense>,
        today: LocalDate = LocalDate.now(),
        windowDays: Int = 30
    ): List<UpcomingRecurringExpense> {
        return recurring
            .filter { it.isActive }
            .mapNotNull { expense ->
                var nextDue = expense.nextRunAt?.toLocalDate()
                    ?: expense.lastProcessedDate?.let { expense.nextOccurrenceAfter(it) }
                    ?: expense.startDate

                // Advance until we reach a future date or today (if not processed today)
                while (nextDue.isBefore(today) && expense.lastProcessedDate != today) {
                    nextDue = expense.nextOccurrenceAfter(nextDue)
                }

                expense.endDate?.let { end ->
                    if (nextDue.isAfter(end)) return@mapNotNull null
                }
                val daysUntilDue = ChronoUnit.DAYS.between(today, nextDue).toInt()
                if (daysUntilDue < 0 || daysUntilDue > windowDays) return@mapNotNull null
                UpcomingRecurringExpense(expense, nextDue, daysUntilDue)
            }
            .sortedBy { it.dueDate }
    }
}
