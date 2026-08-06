package com.example.sparely.ui.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.jvm.JvmName

/**
 * Filters the input string to allow only digits, dots, and commas.
 * Useful for currency input fields.
 */
fun String.filterCurrencyInput(): String {
    return this.filter { it.isDigit() || it == '.' || it == ',' }
}

/**
 * Safely converts a string to a Double, handling both dot and comma separators.
 * Replaces commas with dots before parsing.
 * Returns null if parsing fails.
 */
fun String.toSafeDouble(): Double? {
    return this.replace(',', '.').toDoubleOrNull()
}

/**
 * Safely converts a string to a Double, defaulting to 0.0 if parsing fails.
 */
fun String.toSafeDoubleOrZero(): Double {
    return this.toSafeDouble() ?: 0.0
}

/**
 * Rounds a Double to 2 decimal places using BigDecimal for maximum precision.
 * Crucial for financial calculations to avoid floating point precision issues.
 */
fun Double.roundToTwoDecimals(): Double {
    if (this.isNaN() || this.isInfinite()) return 0.0
    return BigDecimal.valueOf(this)
        .setScale(2, RoundingMode.HALF_UP)
        .toDouble()
}

/**
 * Formats a Double as a currency string with specific decimal places.
 * Handles tiny negative numbers by rounding to 0.0 to avoid "-0.00" display.
 */
fun Double.formatCurrency(symbol: String = "$", decimals: Int = 2): String {
    val displayValue = if (this > -0.005 && this <= 0.0) 0.0 else this
    val format = "%.${decimals}f"
    val formatted = String.format(Locale.US, format, displayValue)
    return if (formatted.startsWith("-")) "-$symbol${formatted.substring(1)}" else symbol + formatted
}

@JvmName("formatCurrencyValue")
fun formatCurrency(value: Double, symbol: String = "$", decimals: Int = 2): String {
    return value.formatCurrency(symbol, decimals)
}

fun Number.formatCurrency(symbol: String = "$", decimals: Int = 2): String {
    return this.toDouble().formatCurrency(symbol, decimals)
}

/**
 * Formats a Double as a percentage string.
 * Handles tiny negative numbers by rounding to 0.0 to avoid "-0.0%" display.
 */
fun Double.formatPercent(decimals: Int = 1): String {
    val displayValue = if (this > -0.00005 && this <= 0.0) 0.0 else this
    val format = "%.${decimals}f%%"
    return String.format(Locale.US, format, displayValue * 100)
}

@JvmName("formatPercentValue")
fun formatPercent(value: Double, decimals: Int = 1): String {
    return value.formatPercent(decimals)
}

fun Number.formatPercent(decimals: Int = 1): String {
    return this.toDouble().formatPercent(decimals)
}
