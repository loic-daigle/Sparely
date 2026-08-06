package com.example.sparely.util

import com.example.sparely.domain.model.RegionalSettings
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Utility class for formatting currency amounts based on user preferences.
 */
class CurrencyFormatter(private val regionalSettings: RegionalSettings) {
    
    private val locale: Locale = regionalSettings.getLocale()
    private val currency: Currency = regionalSettings.getCurrency()
    
    /**
     * Format an amount with the currency symbol.
     * Example: "$1,234.56", "€1.234,56", "¥1,234"
     */
    fun format(amount: Double): String {
        // Avoid negative zero and very small negative values rounding to -0.00
        val sanitizedAmount = if (amount > -0.005 && amount <= 0.0) 0.0 else amount
        val formatter = NumberFormat.getCurrencyInstance(locale)
        formatter.currency = currency
        formatter.isGroupingUsed = true // Default to true for readable formatted output
        return formatter.format(sanitizedAmount)
    }
    
    /**
     * Format an amount without the currency symbol.
     * Example: "1,234.56", "1.234,56"
     */
    fun formatWithoutSymbol(amount: Double): String {
        // Avoid negative zero and very small negative values rounding to -0.00
        val sanitizedAmount = if (amount > -0.005 && amount <= 0.0) 0.0 else amount
        val symbols = DecimalFormatSymbols(locale)
        val formatter = DecimalFormat("0.00", symbols)
        formatter.isGroupingUsed = true
        return formatter.format(sanitizedAmount)
    }
    
    /**
     * Get just the currency symbol.
     * Example: "$", "€", "£"
     */
    fun getSymbol(): String = regionalSettings.getCurrencySymbol()
    
    /**
     * Format a compact amount (K, M notation).
     * Example: "$1.2K", "$3.5M"
     */
    fun formatCompact(amount: Double): String {
        return when {
            amount >= 1_000_000 -> "${getSymbol()}${"%.1f".format(amount / 1_000_000)}M"
            amount >= 1_000 -> "${getSymbol()}${"%.1f".format(amount / 1_000)}K"
            else -> format(amount)
        }
    }
    
    /**
     * Format a percentage.
     * Example: "15.5%", "100%"
     */
    fun formatPercentage(value: Double): String {
        val percentage = value * 100
        return if (percentage.rem(1.0) == 0.0) {
            "${percentage.toInt()}%"
        } else {
            "${"%.1f".format(percentage)}%"
        }
    }
    
    companion object {
        /**
         * Create a formatter with default USD settings.
         */
        fun default(): CurrencyFormatter = CurrencyFormatter(RegionalSettings())
        
        /**
         * Quick format with default settings.
         */
        fun quickFormat(amount: Double): String = default().format(amount)
    }
}
