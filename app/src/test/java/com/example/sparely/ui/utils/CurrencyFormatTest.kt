package com.example.sparely.ui.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class CurrencyFormatTest {

    private lateinit var saved: Locale

    @Before
    fun save() {
        saved = Locale.getDefault()
    }

    @After
    fun restore() {
        Locale.setDefault(saved)
    }

    /** Any kind of space the JDK uses between groups or before the symbol, read as a plain one. */
    private fun String.plainSpaces() = replace(' ', ' ').replace(' ', ' ')

    @Test
    fun englishPutsTheSymbolFirstWithCommas() {
        Locale.setDefault(Locale.forLanguageTag("en-US"))
        assertEquals("$1,826", 1826.0.formatCurrency(decimals = 0))
        assertEquals("-$1,234.57", (-1234.567).formatCurrency())
    }

    @Test
    fun frenchPutsTheSymbolAfterWithSpacesAndAComma() {
        Locale.setDefault(Locale.forLanguageTag("fr-CA"))
        assertEquals("1 826 $", 1826.0.formatCurrency(decimals = 0).plainSpaces())
        assertEquals("-1 234,57 $", (-1234.567).formatCurrency().plainSpaces())
    }

    @Test
    fun noSymbolStaysAPlainNumberForInputFields() {
        Locale.setDefault(Locale.forLanguageTag("fr-CA"))
        assertEquals("1826.50", 1826.5.formatCurrency(""))
    }

    @Test
    fun tinyNegativesShowAsZero() {
        Locale.setDefault(Locale.forLanguageTag("en-US"))
        assertEquals("$0.00", (-0.001).formatCurrency())
    }
}
