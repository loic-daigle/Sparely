package com.example.sparely.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecimalInputParsingTest {

    private fun assertParses(expected: Double, input: String) {
        assertEquals("parsing '$input'", expected, parseDecimalInput(input)!!, 1e-9)
    }

    @Test
    fun acceptsDotAndCommaAsDecimalSeparator() {
        assertParses(12.5, "12.5")
        assertParses(12.5, "12,5")
        assertParses(0.99, "0,99")
        assertParses(0.5, ",5")
        assertParses(0.5, ".5")
        assertParses(12.0, "12.")
        assertParses(12.0, "12,")
        assertParses(42.0, "42")
    }

    @Test
    fun acceptsThousandsSeparatorsInEitherConvention() {
        assertParses(1234.56, "1,234.56")
        assertParses(1234.56, "1.234,56")
        assertParses(1234567.0, "1,234,567")
        assertParses(1234567.0, "1.234.567")
        assertParses(1234567.89, "1 234 567,89")
        assertParses(1234.5, "1 234,5")
        assertParses(1234.5, "1'234.5")
    }

    @Test
    fun ignoresCurrencySymbolsWhitespaceAndSign() {
        assertParses(10.0, " $10 ")
        assertParses(10.5, "€10,50")
        assertParses(15.0, "15%")
        assertParses(-3.25, "-3,25")
        assertParses(3.25, "+3.25")
    }

    @Test
    fun rejectsInvalidInput() {
        assertNull(parseDecimalInput(null))
        assertNull(parseDecimalInput(""))
        assertNull(parseDecimalInput("   "))
        assertNull(parseDecimalInput("."))
        assertNull(parseDecimalInput(","))
        assertNull(parseDecimalInput("abc"))
        assertNull(parseDecimalInput("NaN"))
        assertNull(parseDecimalInput("Infinity"))
        assertNull(parseDecimalInput("1e5"))
        assertNull(parseDecimalInput("1f"))
        assertNull(parseDecimalInput("0x10"))
        assertNull(parseDecimalInput("1.2.3"))
        assertNull(parseDecimalInput("1,2,3"))
        assertNull(parseDecimalInput("1.234,5.6"))
        assertNull(parseDecimalInput("1,23.45"))
        assertNull(parseDecimalInput("12-3"))
        assertNull(parseDecimalInput("9".repeat(400)))
    }

    @Test
    fun filterAndSafeDoubleWorkTogether() {
        assertEquals(12.34, "12,34".filterCurrencyInput().toSafeDouble()!!, 1e-9)
        assertEquals(0.0, "garbage".filterCurrencyInput().toSafeDoubleOrZero(), 0.0)
        assertEquals(18, "1".repeat(50).filterCurrencyInput().length)
    }

    @Test
    fun toInputStringNeverUsesScientificNotation() {
        assertEquals("10000000", 1.0E7.toInputString())
        assertEquals("1234.5", 1234.5.toInputString())
        assertEquals("10", (0.1 * 100).toInputString())
        assertEquals("0.07", 0.07.toInputString())
        assertEquals("0", 0.0.toInputString())
        assertEquals("", Double.NaN.toInputString())
        // Round-trips through the parser.
        assertEquals(12345678.9, 12345678.9.toInputString().toSafeDouble()!!, 1e-9)
    }

    @Test
    fun formattingNeverPrintsNaN() {
        assertEquals("$0.00", Double.NaN.formatCurrency())
        assertEquals("0.0%", Double.POSITIVE_INFINITY.formatPercent())
    }
}
