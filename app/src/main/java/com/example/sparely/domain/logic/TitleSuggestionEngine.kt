package com.example.sparely.domain.logic

import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.ExpenseType
import java.text.Normalizer
import java.time.LocalDate

/**
 * A description the user has logged before, with the details of its most recent use. Typing it
 * again lets the entry form refill those details instead of asking for them each time.
 */
data class KnownTitle(
    val title: String,
    val category: ExpenseCategory,
    val type: ExpenseType,
    val storeId: Long?,
    val paymentMethodId: Long?,
    val lastAmount: Double,
    val useCount: Int,
    val lastUsed: LocalDate
)

/**
 * Remembers expense titles from history. Nothing is stored: the most recent expense with a title
 * decides what it fills in, so recategorising it once updates the suggestion.
 */
object TitleSuggestionEngine {

    const val MIN_QUERY_LENGTH = 2

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("\\s+")

    /** Case-, accent- and spacing-insensitive form used to compare titles ("Épicerie " == "epicerie"). */
    fun normalize(title: String): String =
        Normalizer.normalize(title.trim(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace(WHITESPACE, " ")
            .lowercase()

    /**
     * Groups [expenses] by title. Store and payment method ids that no longer exist are dropped so
     * a suggestion never points at something deleted.
     */
    fun buildIndex(
        expenses: List<Expense>,
        validStoreIds: Set<Long>,
        validPaymentMethodIds: Set<Long>
    ): List<KnownTitle> =
        expenses
            .filter { it.description.isNotBlank() }
            .groupBy { normalize(it.description) }
            .values
            .map { uses ->
                val latest = uses.maxWith(compareBy<Expense>({ it.date }, { it.id }))
                KnownTitle(
                    title = latest.description.trim(),
                    category = latest.category,
                    type = latest.type,
                    storeId = latest.storeId?.takeIf { it in validStoreIds },
                    paymentMethodId = latest.paymentMethodId?.takeIf { it in validPaymentMethodIds },
                    lastAmount = latest.amount,
                    useCount = uses.size,
                    lastUsed = latest.date
                )
            }

    /**
     * Titles matching [query]: those starting with it first, then those with a word starting with it,
     * then any containing it. Within each group, frequent and recent titles come first.
     */
    fun suggest(query: String, index: List<KnownTitle>, limit: Int = 4): List<KnownTitle> {
        val needle = normalize(query)
        if (needle.length < MIN_QUERY_LENGTH) return emptyList()
        return index
            .mapNotNull { known ->
                val title = normalize(known.title)
                val rank = when {
                    title.startsWith(needle) -> 0
                    title.split(' ').any { it.startsWith(needle) } -> 1
                    needle in title -> 2
                    else -> return@mapNotNull null
                }
                rank to known
            }
            .sortedWith(
                compareBy<Pair<Int, KnownTitle>> { it.first }
                    .thenByDescending { it.second.useCount }
                    .thenByDescending { it.second.lastUsed }
            )
            .take(limit)
            .map { it.second }
    }

    /** The known title that [query] spells exactly (ignoring case, accents and spacing), if any. */
    fun exactMatch(query: String, index: List<KnownTitle>): KnownTitle? {
        val needle = normalize(query)
        if (needle.length < MIN_QUERY_LENGTH) return null
        return index.firstOrNull { normalize(it.title) == needle }
    }
}
