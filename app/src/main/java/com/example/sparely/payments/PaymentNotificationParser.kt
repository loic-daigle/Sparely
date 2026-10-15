package com.example.sparely.payments

/** A purchase read from a payment notification (Google Wallet, a bank or card app). */
data class DetectedPayment(val amount: Double, val merchant: String?)

/**
 * Reads the amount and store from a purchase notification, in English or French.
 *
 * Apps don't document this text and it varies by app, bank, card and language:
 * "Tim Hortons" / "$4.50 with Visa ••1234" (Google Wallet), "A purchase of $12.34 was made at
 * TIM HORTONS on your card ending 1234" (a bank), "Un achat de 12,34 $ a été effectué chez ...".
 * So this is deliberately tolerant: an amount needs a currency marker next to it (card digits never
 * count), and anything that doesn't look like a purchase returns null and is simply ignored.
 */
object PaymentNotificationParser {

    // Before the number: $, CA$, US$, C$, €, £ or a currency code. After it: $, $ CA, €, £ or a code.
    private const val NUMBER = """(\d{1,3}(?:[   ,.']\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)"""
    private val AMOUNT_BEFORE = Regex("""(?:(?:CA|US|C|A|NZ|HK|S)?\$|€|£|\b(?:CAD|USD|EUR|GBP|AUD)\b)\s?$NUMBER""")
    private val AMOUNT_AFTER = Regex("""$NUMBER\s?(?:\$\s?(?:CA|CAD|US|USD)?|€|£|\b(?:CAD|USD|EUR|GBP|AUD)\b)""")

    // "at X", "chez X", "à X", "to X" (PayPal) up to where the sentence moves on ("on your card",
    // "le 15 octobre", "with Visa", ...)
    private val MERCHANT_AFTER = Regex(
        """(?<!\p{L})(?:at|chez|à|au|aux|to)\s+(.+?)(?:\s+(?:(?:with|avec|using|par|on|sur|from|for|pour|de votre|with your)\b|(?:le|on|du)\s+\d)|[.\n•,;]|$)""",
        RegexOption.IGNORE_CASE
    )
    private val STORE_NUMBER = Regex("""\s*#\s*\d+$""")

    // Not a purchase: refunds, declines, card setup, payments received, deposits, transfers, statements, due dates.
    private val NOT_A_PURCHASE = Regex(
        "refund|rembours|declin|refus|failed|échou|echou|added to wallet|ajoutée? à|ready to use|prête à|verify|vérifi|" +
            "received|reçu|deposit|dépôt|statement|relevé|e-?transfer|virement|balance is|solde est|available credit is|" +
            "crédit disponible est|payment due|paiement dû|due date|échéance|minimum payment|paiement minimum|" +
            "transferred to your bank|transféré vers votre|money request|demande d'argent",
        RegexOption.IGNORE_CASE
    )

    // A title like "Purchase alert" or "Alerte d'achat" is about the notification, not a store.
    private val GENERIC_TITLE = Regex(
        "purchase|transaction|alert|alerte|achat|payment|paiement|card|carte|spend|dépense|charge|débit|debit|notification|wallet|google pay|gpay",
        RegexOption.IGNORE_CASE
    )

    /** [appName] is the notifying app's name, so a title that's just the app's name isn't taken for a store. */
    fun parse(title: String?, text: String?, appName: String? = null): DetectedPayment? {
        val parts = listOfNotNull(title?.trim(), text?.trim()).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val all = parts.joinToString("\n")
        if (NOT_A_PURCHASE.containsMatchIn(all)) return null

        val amount = findAmount(all) ?: return null
        return DetectedPayment(amount = amount, merchant = findMerchant(title?.trim(), all, appName?.trim()))
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

    private fun findMerchant(title: String?, all: String, appName: String?): String? {
        // Google Wallet uses the store as the title and the amount as the text.
        val titleIsStore = title != null && title.isNotEmpty() &&
            !title.equals(appName, ignoreCase = true) &&
            !GENERIC_TITLE.containsMatchIn(title) &&
            AMOUNT_BEFORE.find(title) == null && AMOUNT_AFTER.find(title) == null
        val raw = if (titleIsStore) title else MERCHANT_AFTER.find(all)?.groupValues?.get(1)
        return raw?.trim()?.trimEnd('.', ',')?.replace(STORE_NUMBER, "")?.trim()?.takeIf { it.isNotEmpty() }
    }
}
