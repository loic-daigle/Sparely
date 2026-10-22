package com.example.sparely.payments

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentDedupeTest {
    private val minute = 60_000L
    private val shop = "com.shopify.arrive"
    private val bank = "com.bank"
    private fun seen(amount: Double, merchant: String?, source: String, at: Long) = SeenPayment(amount, merchant, source, at, 1)

    @Test
    fun sameAmountFromTwoAppsIsOnePurchase() {
        val earlier = seen(98.0, "Allbirds", shop, 0)
        assertTrue(PaymentDedupe.isSame(DetectedPayment(98.0, null), bank, 3 * minute, earlier))
        // A bank alert can come a while after the store's
        assertTrue(PaymentDedupe.isSame(DetectedPayment(98.0, "ALLBIRDS"), bank, 25 * minute, earlier))
        assertFalse(PaymentDedupe.isSame(DetectedPayment(98.0, "ALLBIRDS"), bank, 45 * minute, earlier))
    }

    @Test
    fun sameStoreInAnotherCurrencyIsOnePurchase() {
        val earlier = seen(98.0, "Allbirds", shop, 0)
        assertTrue(PaymentDedupe.isSame(DetectedPayment(134.56, "SQ *ALLBIRDS #12"), bank, 2 * minute, earlier))
        assertFalse(PaymentDedupe.isSame(DetectedPayment(134.56, "Tim Hortons"), bank, 2 * minute, earlier))
        assertFalse(PaymentDedupe.isSame(DetectedPayment(134.56, null), bank, 2 * minute, earlier))
    }

    @Test
    fun twoAmountsFromOneAppAreTwoPurchases() {
        val earlier = seen(4.5, "Tim Hortons", "wallet", 0)
        assertFalse(PaymentDedupe.isSame(DetectedPayment(6.25, "Tim Hortons"), "wallet", minute, earlier))
        // Wallet updating its own notification
        assertTrue(PaymentDedupe.isSame(DetectedPayment(4.5, "Tim Hortons"), "wallet", minute, earlier))
        assertFalse(PaymentDedupe.isSame(DetectedPayment(4.5, "Tim Hortons"), "wallet", 15 * minute, earlier))
    }

    @Test
    fun storeNames() {
        assertTrue(PaymentDedupe.sameStore("SQ *ALLBIRDS", "Allbirds"))
        assertTrue(PaymentDedupe.sameStore("TIM HORTONS #1234", "Tim Hortons"))
        assertFalse(PaymentDedupe.sameStore("Esso", "IGA"))
        assertFalse(PaymentDedupe.sameStore(null, "IGA"))
    }
}
