package com.example.sparely.ui.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.jvm.JvmName

/** Maximum number of characters accepted in a numeric input field. */
private const val MAX_NUMERIC_INPUT_LENGTH = 18

/** Characters that may appear as thousands separators in pasted/typed numbers. */
private val GROUPING_WHITESPACE = charArrayOf(' ', ' ', ' ', ' ', '\'', '’')

/**
 * Filters the input string to allow only digits, dots, and commas.
 * Useful for currency input fields.
 */
fun String.filterCurrencyInput(): String {
    return this.filter { it.isDigit() || it == '.' || it == ',' }
        .take(MAX_NUMERIC_INPUT_LENGTH)
}

/**
 * Filters the input string to allow only digits (e.g. quantities, day counts).
 */
fun String.filterIntegerInput(maxLength: Int = 9): String {
    return this.filter { it.isDigit() }.take(maxLength)
}

/**
 * Parses a user-entered decimal number, accepting both '.' and ',' as the decimal separator.
 *
 * Rules:
 *  - Surrounding whitespace, spaces/apostrophes used as grouping, currency symbols and '%' are ignored.
 *  - When both '.' and ',' are present, the one appearing last is the decimal separator and the
 *    other is treated as a thousands separator ("1,234.56" and "1.234,56" both give 1234.56).
 *  - When a single separator kind appears several times it is only accepted as a thousands
 *    separator if every group after the first has exactly three digits ("1.234.567" -> 1234567).
 *  - A single separator is always a decimal separator ("12,5" and "12.5" both give 12.5).
 *  - Exponents, hex, "NaN", "Infinity" and Kotlin type suffixes ("1f", "2d") are rejected.
 *
 * Returns null when the text is not a finite number.
 */
fun parseDecimalInput(raw: String?): Double? {
    if (raw == null) return null
    var text = raw.trim()
    if (text.isEmpty()) return null

    text = text.filterNot { it in GROUPING_WHITESPACE || it == '%' || Character.getType(it) == Character.CURRENCY_SYMBOL.toInt() }
    if (text.isEmpty()) return null

    var negative = false
    when {
        text.startsWith("-") || text.startsWith("−") -> { negative = true; text = text.substring(1) }
        text.startsWith("+") -> text = text.substring(1)
    }
    if (text.isEmpty() || text.any { !(it.isAsciiDigit() || it == '.' || it == ',') }) return null

    val lastDot = text.lastIndexOf('.')
    val lastComma = text.lastIndexOf(',')
    val normalized: String = when {
        lastDot >= 0 && lastComma >= 0 -> {
            val decimalSep = if (lastDot > lastComma) '.' else ','
            val groupSep = if (decimalSep == '.') ',' else '.'
            val decimalIndex = text.lastIndexOf(decimalSep)
            val integerPart = text.substring(0, decimalIndex)
            val fractionPart = text.substring(decimalIndex + 1)
            // Decimal separator must be unique and grouping must not occur in the fraction.
            if (integerPart.contains(decimalSep) || fractionPart.contains(groupSep)) return null
            if (!isValidGrouping(integerPart, groupSep)) return null
            integerPart.replace(groupSep.toString(), "") + "." + fractionPart
        }
        lastDot >= 0 || lastComma >= 0 -> {
            val sep = if (lastDot >= 0) '.' else ','
            val count = text.count { it == sep }
            if (count == 1) {
                text.replace(sep, '.')
            } else {
                if (!isValidGrouping(text, sep)) return null
                text.replace(sep.toString(), "")
            }
        }
        else -> text
    }

    if (normalized.none { it.isAsciiDigit() }) return null
    val value = try {
        BigDecimal(normalized.let { if (it.startsWith(".")) "0$it" else it }.let { if (it.endsWith(".")) it.dropLast(1) else it }).toDouble()
    } catch (e: NumberFormatException) {
        return null
    }
    if (value.isNaN() || value.isInfinite()) return null
    return if (negative) -value else value
}

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

/** A grouped integer like "1,234,567": first group 1-3 digits, following groups exactly 3 digits. */
private fun isValidGrouping(integerPart: String, groupSep: Char): Boolean {
    if (!integerPart.contains(groupSep)) return true
    val groups = integerPart.split(groupSep)
    if (groups.first().isEmpty() || groups.first().length > 3) return false
    return groups.drop(1).all { it.length == 3 }
}

/**
 * Safely converts a string to a Double, handling both dot and comma separators.
 * Returns null if parsing fails or the value is not finite.
 */
fun String.toSafeDouble(): Double? = parseDecimalInput(this)

/**
 * Safely converts a string to a Double, defaulting to 0.0 if parsing fails.
 */
fun String.toSafeDoubleOrZero(): Double {
    return this.toSafeDouble() ?: 0.0
}

/**
 * Parses a user-entered whole number, ignoring surrounding whitespace.
 * Returns null on overflow or invalid text.
 */
fun String.toSafeInt(): Int? = this.trim().toIntOrNull()

/**
 * Converts an amount into text suitable for pre-filling an editable numeric field.
 * Never uses scientific notation (Double.toString gives "1.0E7" for 10,000,000)
 * and never shows floating point noise ("10.000000000000002").
 */
fun Double.toInputString(maxDecimals: Int = 2): String {
    if (this.isNaN() || this.isInfinite()) return ""
    return BigDecimal.valueOf(this)
        .setScale(maxDecimals, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .let { if (it.signum() == 0) BigDecimal.ZERO else it }
        .toPlainString()
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
    val safeValue = if (this.isNaN() || this.isInfinite()) 0.0 else this
    val displayValue = if (safeValue > -0.005 && safeValue <= 0.0) 0.0 else safeValue
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
    val safeValue = if (this.isNaN() || this.isInfinite()) 0.0 else this
    val displayValue = if (safeValue > -0.00005 && safeValue <= 0.0) 0.0 else safeValue
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
