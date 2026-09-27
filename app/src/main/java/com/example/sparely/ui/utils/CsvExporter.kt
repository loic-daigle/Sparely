package com.example.sparely.ui.utils

import android.content.Context
import android.net.Uri
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.Store
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.time.format.DateTimeFormatter

object CsvExporter {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun exportExpenses(
        context: Context,
        uri: Uri,
        expenses: List<Expense>,
        stores: List<Store>
    ): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                    // Header
                    writer.write("Date,Description,Amount,Category,Store,Notes,Tax Included")
                    writer.newLine()

                    // Data
                    expenses.sortedByDescending { it.date }.forEach { expense ->
                        val storeName = stores.find { it.id == expense.storeId }?.name ?: ""
                        val row = listOf(
                            expense.date.format(dateFormatter),
                            escapeCsv(expense.description),
                            expense.amount.formatCurrency("", 2),
                            expense.category.name,
                            escapeCsv(storeName),
                            escapeCsv(expense.notes ?: ""),
                            expense.includesTax.toString()
                        ).joinToString(",")
                        writer.write(row)
                        writer.newLine()
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun escapeCsv(text: String): String {
        if (text.isEmpty()) return ""
        val escaped = text.replace("\"", "\"\"")
        return if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
