package com.example.sparely.payments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentNotificationParserTest {
    private val p = PaymentNotificationParser

    @Test
    fun googleWalletStoreAsTitle() {
        assertEquals(DetectedPayment(4.5, "Tim Hortons"), p.parse("Tim Hortons", "\$4.50 with Visa ••1234", "Google Wallet"))
        assertEquals(DetectedPayment(4.5, "Tim Hortons"), p.parse("Tim Hortons", "CA\$4.50 with Visa •••• 1234"))
        assertEquals(DetectedPayment(1234.56, "IGA"), p.parse("IGA", "1 234,56 \$ avec Visa ••1234"))
        assertEquals(DetectedPayment(4.5, "Tim Hortons"), p.parse("Google Wallet", "4,50 \$ chez Tim Hortons"))
    }

    @Test
    fun bankAndCardAlerts() {
        assertEquals(
            DetectedPayment(12.34, "TIM HORTONS"),
            p.parse("Purchase alert", "A purchase of \$12.34 was made at TIM HORTONS #1234 on your card ending 5678", "PC Financial")
        )
        assertEquals(
            DetectedPayment(12.34, "TIM HORTONS"),
            p.parse("PC Financial", "Un achat de 12,34 \$ a été effectué chez TIM HORTONS le 15 octobre", "PC Financial")
        )
        assertEquals(DetectedPayment(56.0, "Loblaws"), p.parse("Transaction", "You spent \$56.00 at Loblaws.", "PC Financial"))
        // A pending card purchase is still a purchase
        assertEquals(42.1, p.parse("Pending transaction", "\$42.10 at Esso", "PC Financial")!!.amount, 0.001)
    }

    @Test
    fun payPal() {
        assertEquals(DetectedPayment(12.34, "Netflix"), p.parse("PayPal", "You paid \$12.34 USD to Netflix", "PayPal"))
        assertEquals(DetectedPayment(12.34, "Netflix"), p.parse("PayPal", "Vous avez payé 12,34 \$ CA à Netflix", "PayPal"))
        assertNull(p.parse("PayPal", "You received \$20.00 from Alex", "PayPal"))
        assertNull(p.parse("PayPal", "\$150.00 transferred to your bank", "PayPal"))
    }

    @Test
    fun storeInTheSentence() {
        assertEquals(DetectedPayment(12.0, "Starbucks"), p.parse("Google Wallet", "You paid \$12 at Starbucks with Mastercard"))
        assertEquals(DetectedPayment(8.99, "Metro"), p.parse("Paiement de 8,99 \$", "chez Metro"))
    }

    @Test
    fun thousandsAndOtherCurrencies() {
        assertEquals(1500.0, p.parse("Best Buy", "\$1,500.00 with Visa")!!.amount, 0.001)
        assertEquals(9.5, p.parse("Café", "9,50 € avec Visa")!!.amount, 0.001)
    }

    @Test
    fun cardDigitsAloneAreNotAnAmount() {
        assertNull(p.parse("Visa ••1234", "Card ready to tap"))
        assertNull(p.parse("Tim Hortons", "with Visa ••1234"))
    }

    @Test
    fun ignoresWhatIsNotAPurchase() {
        assertNull(p.parse("Tim Hortons", "Refund of \$4.50"))
        assertNull(p.parse("Paiement refusé", "4,50 \$ chez Tim Hortons"))
        assertNull(p.parse("Card added to Wallet", "Visa ••1234 is ready to use"))
        assertNull(p.parse("PC Financial", "Your payment of \$500.00 has been received", "PC Financial"))
        assertNull(p.parse("PC Financial", "Your statement is ready. Minimum payment: \$25.00", "PC Financial"))
        assertNull(p.parse("Virement Interac", "Vous avez reçu 50,00 \$", "PC Financial"))
        assertNull(p.parse(null, null))
    }

    @Test
    fun appNameIsNotAStore() {
        assertNull(p.parse("PC Financial", "\$20.00 on your card ending 5678", "PC Financial")!!.merchant)
    }

    @Test
    fun parsesNumbersInBothStyles() {
        assertEquals(1234.56, p.parseNumber("1,234.56")!!, 0.001)
        assertEquals(1234.56, p.parseNumber("1 234,56")!!, 0.001)
        assertEquals(1234.0, p.parseNumber("1,234")!!, 0.001)
        assertEquals(4.5, p.parseNumber("4,5")!!, 0.001)
    }

    @Test
    fun shoppingAppOrders() {
        assertEquals(
            DetectedPayment(98.0, "Allbirds"),
            p.parse("Order confirmed", "Your order from Allbirds for \$98.00 is confirmed.", "Shop")
        )
        assertEquals(
            DetectedPayment(45.5, "Allbirds"),
            p.parse("Commande confirmée", "Votre commande chez Allbirds de 45,50 \$ est confirmée", "Shop")
        )
        assertEquals(
            DetectedPayment(98.0, "Allbirds"),
            p.parse("Thanks for shopping with Shop Pay", "Your \$98.00 order from Allbirds is confirmed", "Shop")
        )
    }

    @Test
    fun shoppingAppNoise() {
        // Deliveries, promotions, rewards and installment reminders aren't new purchases
        assertNull(p.parse("Allbirds", "Your \$98.00 order has shipped", "Shop"))
        assertNull(p.parse("Allbirds", "Votre commande de 98,00 \$ a été livrée", "Shop"))
        assertNull(p.parse("Allbirds", "Get \$10 off your next order", "Shop"))
        assertNull(p.parse("Allbirds", "Free shipping on orders over \$50", "Shop"))
        assertNull(p.parse("Price drop", "Tree Runners are now \$80", "Shop"))
        assertNull(p.parse("Shop", "You earned \$1.20 in Shop Cash", "Shop"))
        assertNull(p.parse("Shop Pay", "Your installment of \$24.50 is due tomorrow", "Shop"))
        assertNull(p.parse("Allbirds", "Sale: 20% off everything, from \$40", "Shop"))
    }
}
