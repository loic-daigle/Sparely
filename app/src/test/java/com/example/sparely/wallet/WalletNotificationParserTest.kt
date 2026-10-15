package com.example.sparely.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalletNotificationParserTest {

    @Test
    fun storeAsTitleAmountInText() {
        assertEquals(WalletPayment(4.5, "Tim Hortons"), WalletNotificationParser.parse("Tim Hortons", "$4.50 with Visa ••1234"))
        assertEquals(WalletPayment(4.5, "Tim Hortons"), WalletNotificationParser.parse("Tim Hortons", "CA$4.50 with Visa •••• 1234"))
    }

    @Test
    fun frenchAmountAfterTheNumber() {
        assertEquals(WalletPayment(1234.56, "IGA"), WalletNotificationParser.parse("IGA", "1 234,56 \$ avec Visa ••1234"))
        assertEquals(WalletPayment(4.5, "Tim Hortons"), WalletNotificationParser.parse("Google Wallet", "4,50 \$ chez Tim Hortons"))
    }

    @Test
    fun storeInTheSentence() {
        assertEquals(WalletPayment(12.0, "Starbucks"), WalletNotificationParser.parse("Google Wallet", "You paid \$12 at Starbucks with Mastercard"))
        assertEquals(WalletPayment(8.99, "Metro"), WalletNotificationParser.parse("Paiement de 8,99 \$", "chez Metro"))
    }

    @Test
    fun thousandsAndOtherCurrencies() {
        assertEquals(1500.0, WalletNotificationParser.parse("Best Buy", "\$1,500.00 with Visa")!!.amount, 0.001)
        assertEquals(9.5, WalletNotificationParser.parse("Café", "9,50 € avec Visa")!!.amount, 0.001)
    }

    @Test
    fun cardDigitsAloneAreNotAnAmount() {
        assertNull(WalletNotificationParser.parse("Visa ••1234", "Card ready to tap"))
        assertNull(WalletNotificationParser.parse("Tim Hortons", "with Visa ••1234"))
    }

    @Test
    fun ignoresWhatIsNotAPurchase() {
        assertNull(WalletNotificationParser.parse("Tim Hortons", "Refund of \$4.50"))
        assertNull(WalletNotificationParser.parse("Paiement refusé", "4,50 \$ chez Tim Hortons"))
        assertNull(WalletNotificationParser.parse("Card added to Wallet", "Visa ••1234 is ready to use"))
        assertNull(WalletNotificationParser.parse(null, null))
    }

    @Test
    fun parsesNumbersInBothStyles() {
        assertEquals(1234.56, WalletNotificationParser.parseNumber("1,234.56")!!, 0.001)
        assertEquals(1234.56, WalletNotificationParser.parseNumber("1 234,56")!!, 0.001)
        assertEquals(1234.0, WalletNotificationParser.parseNumber("1,234")!!, 0.001)
        assertEquals(4.5, WalletNotificationParser.parseNumber("4,5")!!, 0.001)
    }
}
