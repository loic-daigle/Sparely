package com.example.sparely.domain.appfunctions

import com.example.sparely.data.local.ExpenseEntity
import com.example.sparely.data.repository.SavingsRepository
import com.example.sparely.domain.model.*
import com.example.sparely.ui.utils.formatCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Serializable request/response data classes for AppFunction operations
 */

/**
 * Input for recording an expense
 */
data class RecordExpenseRequest(
    /** The expense description or item name */
    val description: String,
    /** Amount spent in the user's local currency */
    val amount: Double,
    /** Category of the expense (e.g., FOOD, ENTERTAINMENT, TRANSPORT) */
    val category: String,
    /** Payment method used (CASH, DEBIT_CARD, CREDIT_CARD, etc.) */
    val paymentMethod: String = "CASH",
    /** Additional notes about the expense */
    val notes: String? = null
)

/**
 * Response after recording an expense
 */
data class RecordExpenseResponse(
    /** The ID of the newly recorded expense */
    val expenseId: Long,
    /** Confirmation message */
    val message: String
)

/**
 * Input for linking expense to an asset
 */
data class LinkExpenseToAssetRequest(
    /** ID of the expense to link */
    val expenseId: Long,
    /** ID of the asset category */
    val assetId: Long,
    /** Percentage of expense allocated to this asset (0-100) */
    val percentageAllocation: Double = 100.0
)

/**
 * Input for creating a new asset
 */
data class CreateAssetRequest(
    /** Name of the asset (e.g., "Summer Vacation") */
    val name: String,
    /** Category of the asset (CAR, HOUSE, VACATION, etc.) */
    val category: String,
    /** Additional notes about the asset */
    val notes: String? = null
)

/**
 * Response after creating an asset
 */
data class CreateAssetResponse(
    /** The ID of the newly created asset */
    val assetId: Long,
    /** Confirmation message */
    val message: String
)

/**
 * Input for adding item to wishlist
 */
data class AddToWishlistRequest(
    /** Description of the desired item */
    val description: String,
    /** Target amount to save for this item */
    val targetAmount: Double,
    /** Wishlist category */
    val category: String = "OTHER",
    /** Additional notes */
    val notes: String? = null
)

/**
 * Response after adding to wishlist
 */
data class AddToWishlistResponse(
    /** The ID of the wishlist item */
    val wishlistId: Long,
    /** Confirmation message and cooldown period info */
    val message: String
)

/**
 * Input for recording a refund
 */
data class RecordRefundRequest(
    /** ID of the expense being refunded */
    val expenseId: Long,
    /** Amount being refunded */
    val refundAmount: Double,
    /** Method of refund (ORIGINAL_PAYMENT, STORE_CREDIT, CASH) */
    val refundMethod: String = "ORIGINAL_PAYMENT",
    /** Reason for the refund */
    val reason: String? = null
)

/**
 * Response for delete operations
 */
data class DeleteResponse(
    val success: Boolean,
    val message: String
)

/**
 * AppFunction Controller - Exposes app operations for AI assistants
 *
 * These suspend functions can be discovered and invoked by external services
 * to perform financial management tasks on behalf of the user.
 *
 * This class provides the core functionality that can be wrapped by AppFunction
 * decorators or other service discovery mechanisms as needed.
 */
class SparelyAppFunctions(
    private val repository: SavingsRepository
) {

    /**
     * Record a new expense that the AI assistant has identified.
     *
     * Call this function to add an expense to the user's financial records. Useful
     * when the assistant detects a spending opportunity, purchase confirmation, or
     * receipt that should be tracked.
     *
     * @param request The expense details including amount, category, and payment method
     * @return Response containing the new expense ID and confirmation message
     */
    suspend fun recordExpense(request: RecordExpenseRequest): RecordExpenseResponse =
        withContext(Dispatchers.IO) {
            if (!request.amount.isFinite() || request.amount <= 0.0) {
                return@withContext RecordExpenseResponse(
                    expenseId = 0L,
                    message = "Could not record expense: amount must be a positive number"
                )
            }
            val expenseCategory = runCatching {
                ExpenseCategory.valueOf(request.category.trim().uppercase())
            }.getOrDefault(ExpenseCategory.OTHER)

            // Create expense entity with default allocation values
            val entity = ExpenseEntity(
                id = 0,
                description = request.description.trim().ifEmpty { expenseCategory.name },
                amount = request.amount,
                category = expenseCategory,
                date = LocalDate.now(),
                includesTax = false,
                emergencyAmount = 0.0,
                investmentAmount = 0.0,
                funAmount = 0.0,
                safeInvestmentAmount = 0.0,
                highRiskInvestmentAmount = 0.0,
                autoRecommended = false,
                appliedPercentEmergency = 0.0,
                appliedPercentInvest = 0.0,
                appliedPercentFun = 0.0,
                appliedSafeSplit = 0.65,
                riskLevelUsed = RiskLevel.BALANCED,
                notes = request.notes
            )

            val expenseId = repository.upsertExpense(entity)
            RecordExpenseResponse(
                expenseId = expenseId,
                message = "Successfully recorded expense: ${request.description} for ${request.amount.formatCurrency()}"
            )
        }

    /**
     * Link an expense to an asset category for tracking.
     *
     * Use this to categorize expenses toward specific goals or assets (e.g., link
     * car insurance to "Car Fund", vacation booking to "Trip Fund").
     *
     * @param request The expense ID, asset ID, and allocation percentage
     */
    suspend fun linkExpenseToAsset(request: LinkExpenseToAssetRequest): String =
        withContext(Dispatchers.IO) {
            val percent = request.percentageAllocation
            if (!percent.isFinite() || percent <= 0.0 || percent > 100.0) {
                return@withContext "Could not link expense: allocation must be between 0 and 100%"
            }
            if (repository.findExpenseById(request.expenseId) == null) {
                return@withContext "Could not link expense: expense with ID ${request.expenseId} not found"
            }
            // The repository expects a fraction (1.0 = 100%); the request is expressed in percent.
            repository.linkExpenseToAsset(
                request.expenseId,
                request.assetId,
                percent / 100.0
            )
            "Successfully linked expense to asset with ${percent}% allocation"
        }

    /**
     * Create a new asset category for tracking spending goals.
     *
     * Create assets to organize and track spending toward specific financial goals
     * like "Car Purchase", "House Down Payment", or "Vacation Fund".
     *
     * @param request Asset name, category, and optional description
     * @return Response containing the new asset ID
     */
    suspend fun createAsset(request: CreateAssetRequest): CreateAssetResponse =
        withContext(Dispatchers.IO) {
            val assetCategory = runCatching {
                AssetCategory.valueOf(request.category.trim().uppercase())
            }.getOrDefault(AssetCategory.OTHER)

            val asset = Asset(
                id = 0,
                name = request.name,
                category = assetCategory,
                description = request.notes,
                createdAt = LocalDateTime.now(),
                archived = false
            )

            val assetId = repository.upsertAsset(asset)
            CreateAssetResponse(
                assetId = assetId,
                message = "Successfully created asset: ${request.name}"
            )
        }

    /**
     * Add an item to the user's wishlist with cooldown protection.
     *
     * Items are protected by a 3-day cooldown period before purchase to prevent
     * impulsive buying. After 3 days, the user will be prompted to confirm if they
     * still want the item.
     *
     * @param request Item description, target amount, and category
     * @return Response containing the wishlist item ID and cooldown info
     */
    suspend fun addToWishlist(request: AddToWishlistRequest): AddToWishlistResponse =
        withContext(Dispatchers.IO) {
            if (!request.targetAmount.isFinite() || request.targetAmount < 0.0) {
                return@withContext AddToWishlistResponse(
                    wishlistId = 0L,
                    message = "Could not add to wishlist: target amount must be a positive number"
                )
            }
            val wishlistCategory = runCatching {
                WishlistCategory.valueOf(request.category.trim().uppercase())
            }.getOrNull()

            val wishlist = Wishlist(
                id = 0,
                description = request.description,
                targetAmount = request.targetAmount,
                category = wishlistCategory,
                createdAt = LocalDateTime.now(),
                notes = request.notes,
                cooldownExpiresAt = LocalDateTime.now().plusDays(3) // Set 3-day cooldown
            )

            val wishlistId = repository.addWishlist(wishlist)

            AddToWishlistResponse(
                wishlistId = wishlistId,
                message = "Added ${request.description} to wishlist. 3-day cooldown period applies to prevent impulse purchases."
            )
        }

    /**
     * Record a refund for a previously recorded expense.
     *
     * Track refunds, returns, and partial reimbursements. This adjusts the user's
     * financial records accordingly.
     *
     * @param request Expense ID, refund amount, method, and reason
     */
    suspend fun recordRefund(request: RecordRefundRequest): String =
        withContext(Dispatchers.IO) {
            if (!request.refundAmount.isFinite() || request.refundAmount <= 0.0) {
                return@withContext "Could not record refund: amount must be a positive number"
            }
            val actualRefund = repository.processExpenseRefund(
                expenseId = request.expenseId,
                requestedAmount = request.refundAmount,
                refundMethod = request.refundMethod,
                reason = request.reason,
                refundedItemIds = emptyList()
            )
            if (actualRefund != null) {
                "Successfully recorded refund of ${actualRefund.formatCurrency()} via ${request.refundMethod}"
            } else {
                "Could not record refund: expense with ID ${request.expenseId} not found or already fully refunded"
            }
        }

    /**
     * Delete an expense from the financial records.
     *
     * Remove an incorrectly recorded or duplicate expense. This will also reverse
     * any related savings contributions and asset links.
     *
     * @param expenseId The ID of the expense to delete
     */
    suspend fun deleteExpense(expenseId: Long): String =
        withContext(Dispatchers.IO) {
            val expenseEntity = repository.findExpenseById(expenseId)
            if (expenseEntity != null) {
                repository.deleteExpense(expenseEntity)
                "Successfully deleted expense with ID $expenseId"
            } else {
                "Expense with ID $expenseId not found"
            }
        }

    /**
     * Create a duplicate of an existing expense.
     *
     * Useful for recurring expenses or when you need to record a similar transaction
     * again (e.g., weekly groceries, monthly subscription).
     *
     * @param expenseId The ID of the expense to duplicate
     * @return The ID of the new expense
     */
    suspend fun duplicateExpense(expenseId: Long): String =
        withContext(Dispatchers.IO) {
            val newExpenseId = repository.duplicateExpense(expenseId)
            if (newExpenseId > 0L) {
                "Successfully duplicated expense. New expense ID: $newExpenseId"
            } else {
                "Failed to duplicate expense with ID $expenseId - expense not found"
            }
        }
}
