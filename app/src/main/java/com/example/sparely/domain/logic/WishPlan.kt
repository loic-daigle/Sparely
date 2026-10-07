package com.example.sparely.domain.logic

import com.example.sparely.domain.model.Wishlist
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The "put money aside for this" plan behind a wish.
 *
 * Nothing is moved in the real bank account. A wish with a target date builds up its reserve evenly
 * from the day it was saved to that date, and whatever the user adds by hand counts too. The
 * reserve is computed from the dates, so no scheduled job or stored deposits are needed, and the
 * app simply treats that money as unavailable.
 */
object WishPlan {

    private const val DAYS_PER_MONTH = 30.4375

    /** Money currently held back for [wish]: the larger of the manual amount and the scheduled build-up. */
    fun reserved(wish: Wishlist, today: LocalDate = LocalDate.now()): Double {
        if (wish.archived || wish.targetAmount <= 0.0) return 0.0
        val manual = wish.currentSavings.coerceAtLeast(0.0)
        return min(wish.targetAmount, max(manual, scheduled(wish, today)))
    }

    /** Total held back across all active wishes. */
    fun totalReserved(wishes: List<Wishlist>, today: LocalDate = LocalDate.now()): Double =
        wishes.sumOf { reserved(it, today) }

    /** How much should be set aside each month to reach the target on time. Null without a date. */
    fun monthlyAmount(wish: Wishlist): Double? {
        val target = wish.targetDate ?: return null
        return wish.targetAmount / monthsBetween(wish.createdAt.toLocalDate(), target)
    }

    /** Whole months (rounded up, at least one) from [start] to [target]. */
    fun monthsBetween(start: LocalDate, target: LocalDate): Int =
        ceil(daysBetween(start, target) / DAYS_PER_MONTH).coerceAtLeast(1.0).toInt()

    /** Whole days left until the target date, never negative. Null without a date. */
    fun daysLeft(wish: Wishlist, today: LocalDate = LocalDate.now()): Int? =
        wish.targetDate?.let { ChronoUnit.DAYS.between(today, it).toInt().coerceAtLeast(0) }

    private fun scheduled(wish: Wishlist, today: LocalDate): Double {
        val target = wish.targetDate ?: return 0.0
        val start = wish.createdAt.toLocalDate()
        val total = daysBetween(start, target)
        if (total <= 0) return wish.targetAmount // already due (or dated in the past)
        val elapsed = ChronoUnit.DAYS.between(start, today).coerceIn(0, total.toLong())
        return wish.targetAmount * elapsed / total
    }

    private fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)
}
