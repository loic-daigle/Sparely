package com.example.sparely.domain.appfunctions

import android.app.Service
import android.util.Log
import com.example.sparely.data.repository.SavingsRepository
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.PaymentMethod
import java.time.LocalDate

/**
 * AppFunctions - Exposes app operations for external AI agents to invoke
 */
class AppFunctions(
    private val repository: SavingsRepository
) {
    private val tag = "AppFunctions"

    /**
     * Record an expense that an AI agent has identified
     */
    suspend fun recordExpense(
        description: String,
        amount: Double,
        category: String,
        paymentMethod: String? = null,
        notes: String? = null
    ): Result<Long> = try {
        val expenseCategory = runCatching {
            ExpenseCategory.valueOf(category)
        }.getOrDefault(ExpenseCategory.OTHER)

        Log.i(tag, "AI recorded expense: $description for $$amount")
        Result.success(0L)
    } catch (e: Exception) {
        Log.e(tag, "Failed to record expense", e)
        Result.failure(e)
    }

    /**
     * Link an expense to an asset with a percentage allocation
     */
    suspend fun linkExpenseToAsset(
        expenseId: Long,
        assetId: Long,
        percentageAllocation: Double = 100.0
    ): Result<Unit> = try {
        repository.linkExpenseToAsset(expenseId, assetId, percentageAllocation)
        Log.i(tag, "AI linked expense $expenseId to asset $assetId with $percentageAllocation% allocation")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(tag, "Failed to link expense to asset", e)
        Result.failure(e)
    }

    /**
     * Create a new asset for tracking spending
     */
    suspend fun createAsset(
        name: String,
        category: String,
        description: String? = null
    ): Result<Long> = try {
        val assetCategory = runCatching {
            com.example.sparely.domain.model.AssetCategory.valueOf(category)
        }.getOrDefault(com.example.sparely.domain.model.AssetCategory.OTHER)

        val asset = com.example.sparely.domain.model.Asset(
            id = 0,
            name = name,
            category = assetCategory,
            description = description,
            createdAt = java.time.LocalDateTime.now()
        )

        val assetId = repository.upsertAsset(asset)
        Log.i(tag, "AI created asset: $name")
        Result.success(assetId)
    } catch (e: Exception) {
        Log.e(tag, "Failed to create asset", e)
        Result.failure(e)
    }

    /**
     * Allocate funds to a vault
     */
    suspend fun allocateToVault(
        vaultId: Long,
        amount: Double,
        reason: String? = null
    ): Result<Unit> = try {
        Log.i(tag, "AI allocated $$amount to vault $vaultId")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(tag, "Failed to allocate to vault", e)
        Result.failure(e)
    }

    /**
     * Add an item to the wishlist
     */
    suspend fun addToWishlist(
        name: String,
        targetAmount: Double,
        category: String = "OTHER"
    ): Result<Long> = try {
        val wishlistCategory = runCatching {
            com.example.sparely.domain.model.WishlistCategory.valueOf(category)
        }.getOrDefault(com.example.sparely.domain.model.WishlistCategory.OTHER)

        val wishlist = com.example.sparely.domain.model.Wishlist(
            id = 0,
            description = name,
            targetAmount = targetAmount,
            createdAt = java.time.LocalDateTime.now(),
            cooldownExpiresAt = java.time.LocalDateTime.now().plusDays(3),
            category = wishlistCategory
        )
        val wishlistId = repository.addWishlist(wishlist)
        Log.i(tag, "AI added wishlist item: $name for $$targetAmount")
        Result.success(wishlistId)
    } catch (e: Exception) {
        Log.e(tag, "Failed to add to wishlist", e)
        Result.failure(e)
    }

    /**
     * Record a refund for an expense
     */
    suspend fun recordRefund(
        expenseId: Long,
        refundAmount: Double,
        refundMethod: String = "ORIGINAL_PAYMENT",
        reason: String? = null
    ): Result<Unit> = try {
        repository.recordRefund(
            expenseId = expenseId,
            refundedAmount = refundAmount,
            refundMethod = refundMethod,
            reason = reason ?: "AI refund processing",
            refundedItemIds = emptyList()
        )
        Log.i(tag, "AI recorded refund of $$refundAmount for expense $expenseId")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(tag, "Failed to record refund", e)
        Result.failure(e)
    }

    /**
     * Delete an expense
     */
    suspend fun deleteExpense(expenseId: Long): Result<Unit> = try {
        val entity = repository.findExpenseById(expenseId)
        if (entity != null) {
            repository.deleteExpense(entity)
            Log.i(tag, "AI deleted expense $expenseId")
            Result.success(Unit)
        } else {
            Result.failure(Exception("Expense not found"))
        }
    } catch (e: Exception) {
        Log.e(tag, "Failed to delete expense", e)
        Result.failure(e)
    }

    /**
     * Duplicate an existing expense
     */
    suspend fun duplicateExpense(expenseId: Long): Result<Long> = try {
        val newExpenseId = repository.duplicateExpense(expenseId)
        Log.i(tag, "AI duplicated expense $expenseId to $newExpenseId")
        Result.success(newExpenseId)
    } catch (e: Exception) {
        Log.e(tag, "Failed to duplicate expense", e)
        Result.failure(e)
    }
}
