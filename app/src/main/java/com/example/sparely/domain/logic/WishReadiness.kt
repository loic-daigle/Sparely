package com.example.sparely.domain.logic

import com.example.sparely.domain.logic.PurchaseAffordabilityEngine.Verdict
import com.example.sparely.domain.model.Wishlist
import java.time.LocalDateTime

/** Where a saved wish stands today, combining the affordability verdict with its cool-off and savings. */
enum class WishReadiness {
    /** Affordable now and the cool-off is over: safe to buy. */
    READY,

    /** Affordable, but still inside the cool-off period. */
    COOLING_DOWN,

    /** Not comfortably affordable yet; keep saving. */
    SAVING_UP,

    /** Buying it now would put essentials or the balance at risk. */
    NOT_AFFORDABLE;

    companion object {
        /**
         * A wish that has been fully set aside counts as affordable unless buying it would put the
         * user at real risk: the money was deliberately held back for it, so a "wait" caused by a
         * thin cushion no longer applies.
         */
        fun assess(
            wish: Wishlist,
            verdict: Verdict,
            now: LocalDateTime = LocalDateTime.now(),
            /** Money held back for the wish so far (see [WishPlan.reserved]). */
            reserved: Double = wish.currentSavings
        ): WishReadiness {
            val fullySaved = wish.targetAmount > 0 && reserved >= wish.targetAmount
            val affordable = when (verdict) {
                Verdict.GO_AHEAD -> true
                Verdict.WAIT -> fullySaved
                Verdict.AVOID -> false
            }
            if (!affordable) return if (verdict == Verdict.AVOID) NOT_AFFORDABLE else SAVING_UP
            val coolingDown = wish.cooldownExpiresAt?.isAfter(now) ?: false
            return if (coolingDown) COOLING_DOWN else READY
        }
    }
}
