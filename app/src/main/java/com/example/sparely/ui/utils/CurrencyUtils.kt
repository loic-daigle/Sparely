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

/** Longer than a plain number so a few amounts can be strung together ("12.50+8.99+3"). */
private const val MAX_AMOUNT_EXPRESSION_LENGTH = 40

/** What the amount calculator accepts as operators, including the symbols its keys insert. */
private val EXPRESSION_OPERATORS = setOf('+', '-', '−', '*', '×', '/', '÷')

/**
 * Like [filterCurrencyInput] but keeps the calculator's operators, for amount fields that accept
 * simple math such as "12.50+8.99". Spaces are dropped so the expression stays compact.
 */
fun String.filterAmountExpressionInput(): String {
    // Leading operators are dropped: amounts are entered positive, as in the plain fields
    return this.filter { it.isDigit() || it == '.' || it == ',' || it in EXPRESSION_OPERATORS }
        .dropWhile { it in EXPRESSION_OPERATORS }
        .take(MAX_AMOUNT_EXPRESSION_LENGTH)
}

/** True when the text uses math (an operator after the first character), not just a number. */
fun String.isAmountExpression(): Boolean {
    val text = trim()
    return text.length > 1 && text.drop(1).any { it in EXPRESSION_OPERATORS }
}

/**
 * Evaluates simple calculator math typed into an amount field: + − × ÷ with the usual precedence
 * (× and ÷ before + and −), no brackets. Each number is read by [parseDecimalInput], so "12,50"
 * works for French users. A dangling operator at the end ("12+") is ignored so a half-typed
 * expression still has a value. Returns null for anything else, including division by zero.
 */
fun evaluateAmountExpression(raw: String?): Double? {
    if (raw == null) return null
    var text = raw.filterNot { it.isWhitespace() }
    while (text.isNotEmpty() && text.last() in EXPRESSION_OPERATORS) text = text.dropLast(1)
    if (text.isEmpty()) return null

    // Split into numbers and operators; a minus at the start or right after an operator is a sign
    val numbers = mutableListOf<Double>()
    val operators = mutableListOf<Char>()
    var start = 0
    var index = 0
    while (index < text.length) {
        val c = text[index]
        val isSign = (c == '-' || c == '−' || c == '+') &&
            (index == start)
        if (c in EXPRESSION_OPERATORS && !isSign) {
            numbers += parseDecimalInput(text.substring(start, index)) ?: return null
            operators += when (c) {
                '−' -> '-'
                '×' -> '*'
                '÷' -> '/'
                else -> c
            }
            start = index + 1
        }
        index++
    }
    numbers += parseDecimalInput(text.substring(start)) ?: return null

    // × and ÷ first, folding each run into the number before it
    val terms = mutableListOf(numbers.first())
    val termOperators = mutableListOf<Char>()
    operators.forEachIndexed { i, op ->
        val next = numbers[i + 1]
        when (op) {
            '*' -> terms[terms.lastIndex] = terms.last() * next
            '/' -> {
                if (next == 0.0) return null
                terms[terms.lastIndex] = terms.last() / next
            }
            else -> {
                terms += next
                termOperators += op
            }
        }
    }
    var result = terms.first()
    termOperators.forEachIndexed { i, op ->
        result = if (op == '+') result + terms[i + 1] else result - terms[i + 1]
    }
    return result.takeIf { !it.isNaN() && !it.isInfinite() }
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
 * Amount fields with the calculator can hold math like "12.50+8.99"; that gives its result,
 * rounded to cents. Returns null if parsing fails or the value is not finite.
 */
fun String.toSafeDouble(): Double? =
    if (isAmountExpression()) evaluateAmountExpression(this)?.roundToTwoDecimals() else parseDecimalInput(this)

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
 * Formats an amount for reading, the way the phone's language writes money: "$1,826.00" in
 * English, "1 826,00 $" in French (grouped digits, the language's decimal mark, the symbol where
 * that language puts it). Handles tiny negative numbers by rounding to 0.0 to avoid "-0.00".
 *
 * With an empty [symbol] it gives a plain number instead ("1826.00"): that form fills editable
 * amount fields and the CSV export, which need digits a parser reads back the same in any language.
 */
fun Double.formatCurrency(symbol: String = "$", decimals: Int = 2): String {
    val safeValue = if (this.isNaN() || this.isInfinite()) 0.0 else this
    val displayValue = if (safeValue > -0.005 && safeValue <= 0.0) 0.0 else safeValue
    if (symbol.isEmpty()) return String.format(Locale.US, "%.${decimals}f", displayValue)

    val locale = Locale.getDefault()
    val number = (java.text.NumberFormat.getNumberInstance(locale) as java.text.DecimalFormat).apply {
        isGroupingUsed = true
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
        roundingMode = RoundingMode.HALF_UP
    }.format(kotlin.math.abs(displayValue))
    val sign = if (displayValue < 0) "-" else ""
    // Where this language puts the currency sign, read from its own currency pattern ("¤#,##0.00"
    // in English, "#,##0.00 ¤" in French)
    val symbolFirst = (java.text.NumberFormat.getCurrencyInstance(locale) as? java.text.DecimalFormat)
        ?.toPattern()?.trimStart()?.startsWith('\u00A4') ?: true
    return if (symbolFirst) "$sign$symbol$number" else "$sign$number\u00A0$symbol"
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
