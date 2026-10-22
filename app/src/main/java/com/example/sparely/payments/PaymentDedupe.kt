package com.example.sparely.payments

import kotlin.math.abs

/** A payment notification already turned into a suggestion. */
data class SeenPayment(
    val amount: Double,
    val merchant: String?,
    val sourcePackage: String,
    val postTime: Long,
    val notificationId: Int
)

/**
 * Decides when two payment notifications are the same purchase: Wallet updating its own
 * notification, or the store's app (Shop, PayPal) and the card's bank both announcing it.
 */
object PaymentDedupe {
    /** The same app repeating itself is quick; a bank's alert can lag the store's by a while. */
    const val SAME_APP_WINDOW_MS = 10 * 60 * 1000L
    const val OTHER_APP_WINDOW_MS = 30 * 60 * 1000L

    /** The earlier suggestion [payment] repeats, if any (the most recent one). */
    fun findSame(payment: DetectedPayment, sourcePackage: String, postTime: Long, seen: List<SeenPayment>): SeenPayment? =
        seen.filter { isSame(payment, sourcePackage, postTime, it) }.maxByOrNull { it.postTime }

    fun isSame(payment: DetectedPayment, sourcePackage: String, postTime: Long, earlier: SeenPayment): Boolean {
        val sameApp = earlier.sourcePackage == sourcePackage
        val window = if (sameApp) SAME_APP_WINDOW_MS else OTHER_APP_WINDOW_MS
        if (abs(postTime - earlier.postTime) > window) return false
        if (abs(payment.amount - earlier.amount) < 0.005) return true
        // Two apps naming the same store, with amounts that can differ by currency (US$ at the
        // store, CA$ at the bank). Two amounts from one app are two purchases.
        if (sameApp) return false
        val ratio = payment.amount / earlier.amount
        return ratio in 0.5..2.0 && sameStore(payment.merchant, earlier.merchant)
    }

    /** "SQ *ALLBIRDS #12" and "Allbirds" are the same store; a missing name matches nothing. */
    fun sameStore(a: String?, b: String?): Boolean {
        val x = normalize(a) ?: return false
        val y = normalize(b) ?: return false
        return x.contains(y) || y.contains(x)
    }

    private val NOISE = Regex("""#\s*\d+|\b(inc|ltd|ltée|llc|corp|co|the|store|shop|online)\b""", RegexOption.IGNORE_CASE)

    private fun normalize(name: String?): String? =
        name?.replace(NOISE, " ")?.lowercase()?.filter { it.isLetterOrDigit() }?.takeIf { it.length >= 3 }
}
