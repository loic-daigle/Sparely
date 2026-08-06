package com.example.sparely.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.RecurringFrequency
import java.time.LocalDate

@Entity(
    tableName = "recurring_expenses",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = SmartVaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["deductedFromVaultId"],
            onDelete = androidx.room.ForeignKey.SET_NULL
        ),
        androidx.room.ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = androidx.room.ForeignKey.SET_NULL
        ),
        androidx.room.ForeignKey(
            entity = PaymentMethodEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentMethodId"],
            onDelete = androidx.room.ForeignKey.SET_NULL
        )
    ],
    indices = [
        androidx.room.Index("deductedFromVaultId"),
        androidx.room.Index("storeId"),
        androidx.room.Index("paymentMethodId"),
        androidx.room.Index("nextRunAt"),
        androidx.room.Index("isActive")
    ]
)
data class RecurringExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val amount: Double,
    val category: ExpenseCategory,
    val frequency: RecurringFrequency,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val lastProcessedDate: LocalDate? = null,
    val isActive: Boolean = true,
    val autoLog: Boolean = true,
    // If true, the recurring expense will be executed automatically when due.
    // If false, a pending payment will be created and funds will be frozen until user confirms.
    val executeAutomatically: Boolean = false,
    val reminderDaysBefore: Int = 2,
    val merchantName: String? = null,
    val notes: String? = null,
    val storeId: Long? = null,
    // Payment method for this recurring expense
    val paymentMethodId: Long? = null,
    // Expense-related fields (same as ExpenseEntity)
    val includesTax: Boolean = false,
    val deductFromMainAccount: Boolean = false,
    val deductedFromVaultId: Long? = null,
    val manualPercentEmergency: Double? = null,
    val manualPercentInvest: Double? = null,
    val manualPercentFun: Double? = null,
    val manualSafeSplit: Double? = null,
    // Variable amount support for bills like electricity, water, etc.
    val isVariableAmount: Boolean = false,
    // JSON-serialized list of AmountHistoryEntry for variable expense history
    val amountHistoryJson: String? = null,
    val estimatedAmount: Double? = null,
    val nextRunAt: java.time.LocalDateTime? = null,
    val type: String = "PRODUCT", // PRODUCT or SERVICE
    // JSON-serialized map of assetId -> percentage allocation for linked assets
    val assetAllocationsJson: String? = null
)
