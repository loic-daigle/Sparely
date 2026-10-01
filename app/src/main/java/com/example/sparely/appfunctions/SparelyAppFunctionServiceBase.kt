package com.example.sparely.appfunctions

import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionDisabledException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import androidx.appfunctions.AppFunctionStringValueConstraint
import com.example.sparely.SparelyApplication
import kotlinx.coroutines.CancellationException

/**
 * Read-only AppFunctions that let on-device AI assistants (such as Gemini) answer questions about
 * the user's finances. Nothing here changes data.
 *
 * The KSP compiler generates the concrete `SparelyAppFunctionService` (registered in the
 * manifest) and the function metadata from this class; the KDoc on each function is the
 * description the assistant sees. All logic lives in [SparelyAssistantQueries].
 *
 * Every function fails with [AppFunctionDisabledException] until the user turns on
 * "Allow AI assistants" in Settings > Security.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "SparelyAppFunctionService",
    appFunctionXmlFileName = "sparely_app_function_service",
)
abstract class SparelyAppFunctionServiceBase : AppFunctionService() {

    private val queries: SparelyAssistantQueries by lazy {
        val container = (application as SparelyApplication).container
        SparelyAssistantQueries(
            RepositoryAssistantDataSource(container.savingsRepository, container.preferencesRepository)
        )
    }

    /**
     * Get how much the user spent over a date range, in total and per category.
     * Use this for questions like "How much did I spend this month?" or "What did I spend the most on in March?".
     *
     * @param startDate First day to include, as YYYY-MM-DD. Defaults to the first day of the current month.
     * @param endDate Last day to include, as YYYY-MM-DD. Defaults to today.
     * @return Total spending net of refunds, with a per-category breakdown sorted largest first.
     * @throws AppFunctionInvalidArgumentException If a date is not YYYY-MM-DD or startDate is after endDate. Fix the dates and retry.
     * @throws AppFunctionDisabledException If the user has not allowed assistant access. Tell the user how to turn it on, using the exception message.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getSpendingSummary(startDate: String? = null, endDate: String? = null): SpendingSummary =
        call { queries.getSpendingSummary(startDate, endDate) }

    /**
     * Find individual expenses the user recorded, most recent first.
     * Use this to answer "When did I last buy groceries?" or "How much was my Netflix payment?".
     * All filters are optional and combined with AND.
     *
     * @param query Text to look for in the expense description, notes, order number or item names. Case-insensitive.
     * @param category Only return expenses in this category.
     * @param startDate Only return expenses on or after this date, as YYYY-MM-DD.
     * @param endDate Only return expenses on or before this date, as YYYY-MM-DD.
     * @param limit Maximum number of expenses to return, from 1 to 50. Defaults to 20.
     * @return Matching expenses and whether more matched than were returned.
     * @throws AppFunctionInvalidArgumentException If a date, the category or the limit is invalid. The message says which; fix it and retry.
     * @throws AppFunctionDisabledException If the user has not allowed assistant access. Tell the user how to turn it on, using the exception message.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchExpenses(
        query: String? = null,
        @AppFunctionStringValueConstraint(
            enumValues = [
                "GROCERIES", "DINING", "TRANSPORTATION", "ENTERTAINMENT", "UTILITIES",
                "HEALTH", "EDUCATION", "SHOPPING", "TRAVEL", "OTHER",
            ]
        )
        category: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        limit: Int? = null,
    ): ExpenseSearchResult = call { queries.searchExpenses(query, category, startDate, endDate, limit) }

    /**
     * Get the user's monthly budgets: the limit, amount spent and amount left for each budgeted category.
     * Use this for "Am I over budget?" or "How much can I still spend on dining this month?".
     *
     * @param month The month to check, as YYYY-MM. Defaults to the current month.
     * @return Budget status per category plus totals. The category list is empty when the user set no budgets for that month.
     * @throws AppFunctionInvalidArgumentException If month is not YYYY-MM. Fix it and retry.
     * @throws AppFunctionDisabledException If the user has not allowed assistant access. Tell the user how to turn it on, using the exception message.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getBudgetStatus(month: String? = null): BudgetOverview = call { queries.getBudgetStatus(month) }

    /**
     * List the user's savings vaults (goal-based savings such as "Emergency fund" or "Trip to Japan") with their balance and progress.
     * Use this for "How close am I to my vacation goal?" or "How much is in my emergency fund?".
     *
     * @return Active vaults with balance, goal and progress.
     * @throws AppFunctionDisabledException If the user has not allowed assistant access. Tell the user how to turn it on, using the exception message.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listVaults(): VaultList = call { queries.listVaults() }

    /**
     * Get the balance of the user's main (everyday) account and the total held in savings vaults.
     * Use this for "How much money do I have?" or "What's my balance?".
     *
     * @return Main account balance and combined vault balance.
     * @throws AppFunctionDisabledException If the user has not allowed assistant access. Tell the user how to turn it on, using the exception message.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getAccountBalances(): AccountBalances = call { queries.getAccountBalances() }

    /**
     * List the user's bills and subscriptions due soon, soonest first.
     * Use this for "What bills are coming up?" or "Do I have any payments due this week?".
     *
     * @param days How many days ahead to look, from 0 (due today) to 365. Defaults to 30.
     * @return Upcoming bills with due dates and expected amounts, plus the total due.
     * @throws AppFunctionInvalidArgumentException If days is out of range. Fix it and retry.
     * @throws AppFunctionDisabledException If the user has not allowed assistant access. Tell the user how to turn it on, using the exception message.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listUpcomingBills(days: Int? = null): UpcomingBillList = call { queries.listUpcomingBills(days) }

    /**
     * List the items on the user's wishlist (things they want to buy and are saving for).
     * Items in their cooling-off period are ones Sparely asks the user to wait on before buying; mention this if the user asks whether to buy one.
     *
     * @return Active wishlist items, highest priority first.
     * @throws AppFunctionDisabledException If the user has not allowed assistant access. Tell the user how to turn it on, using the exception message.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listWishlist(): WishlistOverview = call { queries.listWishlist() }

    /** Runs [block], translating query failures into the AppFunction errors assistants understand. */
    private suspend fun <T> call(block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: AssistantAccessDisabledException) {
            throw AppFunctionDisabledException(e.message)
        } catch (e: AssistantInvalidArgumentException) {
            throw AppFunctionInvalidArgumentException(e.message)
        } catch (e: Exception) {
            Log.e(TAG, "AppFunction call failed", e)
            throw AppFunctionAppUnknownException("Sparely could not read this data. Ask the user to try again later.")
        }

    private companion object {
        const val TAG = "SparelyAppFunctions"
    }
}
