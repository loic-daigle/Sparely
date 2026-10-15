package com.example.sparely.wallet

/** A tap-to-pay purchase read from a Google Wallet notification. */
data class WalletPayment(val amount: Double, val merchant: String?)

/**
 * Reads the amount and store from a Google Wallet payment notification, in English or French.
 *
 * Google doesn't document this text and it varies by language, bank and card ("Tim Hortons" /
 * "$4.50 with Visa ••1234", "4,50 $ chez Tim Hortons", ...), so this is deliberately tolerant: an
 * amount needs a currency marker next to it (so card digits never count), and anything that doesn't
 * look like a completed payment returns null, in which case the notification is simply ignored.
 */
object WalletNotificationParser {

    // Before the number: $, CA$, US$, C$, €, £ or a currency code. After it: $, $ CA, €, £ or a code.
    private const val NUMBER = """(\d{1,3}(?:[   ,.']\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)"""
    private val AMOUNT_BEFORE = Regex("""(?:(?:CA|US|C|A|NZ|HK|S)?\$|€|£|\b(?:CAD|USD|EUR|GBP|AUD)\b)\s?$NUMBER""")
    private val AMOUNT_AFTER = Regex("""$NUMBER\s?(?:\$\s?(?:CA|CAD|US|USD)?|€|£|\b(?:CAD|USD|EUR|GBP|AUD)\b)""")

    private val MERCHANT_AFTER = Regex("""(?:\bat|\bchez|\bà|\bau|\baux)\s+(.+?)(?:\s+(?:with|avec|using|par)\b|[.\n•]|$)""", RegexOption.IGNORE_CASE)

    // Not a completed purchase: refunds, declines, card setup, transit top-ups waiting, etc.
    private val NOT_A_PURCHASE = Regex(
        """refund|rembours|declin|refus|failed|échou|echou|pending|en attente|added to wallet|ajouté|ready to use|prête|verify|vérifi""",
        RegexOption.IGNORE_CASE
    )

    private val APP_NAMES = setOf("google wallet", "wallet", "google pay", "gpay")

    fun parse(title: String?, text: String?): WalletPayment? {
        val parts = listOfNotNull(title?.trim(), text?.trim()).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val all = parts.joinToString("\n")
        if (NOT_A_PURCHASE.containsMatchIn(all)) return null

        val amount = findAmount(all) ?: return null
        return WalletPayment(amount = amount, merchant = findMerchant(title?.trim(), all))
    }

    private fun findAmount(text: String): Double? {
        val match = listOfNotNull(AMOUNT_BEFORE.find(text), AMOUNT_AFTER.find(text)).minByOrNull { it.range.first }
            ?: return null
        return parseNumber(match.groupValues[1])?.takeIf { it > 0.0 && it.isFinite() }
    }

    /** "1,234.56", "1 234,56", "4.50", "4,50", "12" → a number; the last separator followed by 1-2 digits is the decimal one. */
    internal fun parseNumber(raw: String): Double? {
        val cleaned = raw.trim()
        val lastSep = cleaned.indexOfLast { it == '.' || it == ',' }
        val (whole, fraction) = if (lastSep >= 0 && cleaned.length - lastSep - 1 in 1..2) {
            cleaned.substring(0, lastSep) to cleaned.substring(lastSep + 1)
        } else {
            cleaned to ""
        }
        val digits = whole.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return "$digits.${fraction.ifEmpty { "0" }}".toDoubleOrNull()
    }

    private fun findMerchant(title: String?, all: String): String? {
        // Most Wallet payment notifications use the store as the title and the amount as the text.
        if (title != null && title.isNotEmpty() && title.lowercase() !in APP_NAMES &&
            AMOUNT_BEFORE.find(title) == null && AMOUNT_AFTER.find(title) == null
        ) {
            return title
        }
        return MERCHANT_AFTER.find(all)?.groupValues?.get(1)?.trim()?.trimEnd('.', ',')?.takeIf { it.isNotEmpty() }
    }
}
