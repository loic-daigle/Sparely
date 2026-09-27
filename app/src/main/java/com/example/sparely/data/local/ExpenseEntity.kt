package com.example.sparely.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.RiskLevel
import java.time.LocalDate

@Entity(
    tableName = "expenses",
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
        androidx.room.Index("date"),
        androidx.room.Index("category")
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val amount: Double,
    val category: ExpenseCategory,
    val date: LocalDate,
    val includesTax: Boolean,
    val emergencyAmount: Double,
    val investmentAmount: Double,
    val funAmount: Double,
    val safeInvestmentAmount: Double,
    val highRiskInvestmentAmount: Double,
    val autoRecommended: Boolean,
    val appliedPercentEmergency: Double,
    val appliedPercentInvest: Double,
    val appliedPercentFun: Double,
    val appliedSafeSplit: Double,
    val riskLevelUsed: RiskLevel,
    val deductedFromVaultId: Long? = null,
    val storeId: Long? = null,
    val paymentMethodId: Long? = null,
    val isRecurring: Boolean = false,
    val notes: String? = null,
    val refundedAmount: Double = 0.0,
    val isRefunded: Boolean = false,
    val orderNumber: String? = null,
    val type: String = "PRODUCT", // PRODUCT or SERVICE
    val isIgnored: Boolean = false
)
