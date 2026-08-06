package com.example.sparely.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.sparely.domain.model.AccountType
import com.example.sparely.domain.model.VaultAllocationMode
import com.example.sparely.domain.model.VaultContributionSource
import com.example.sparely.domain.model.VaultAdjustmentType
import com.example.sparely.domain.model.VaultPriority
import com.example.sparely.domain.model.VaultScheduleType
import com.example.sparely.domain.model.VaultTransferDirection
import com.example.sparely.domain.model.VaultType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "smart_vaults")
data class SmartVaultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val targetAmount: Double,
    val currentBalance: Double,
    val targetDate: LocalDate?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val monthlyNeed: Double?,
    val priorityWeight: Double,
    val autoSaveEnabled: Boolean,
    @ColumnInfo(name = "allow_auto_income") val allowAutoIncome: Boolean = true,
    val priority: VaultPriority,
    val type: VaultType,
    val interestRate: Double?,
    val allocationMode: VaultAllocationMode,
    val manualAllocationPercent: Double?,
    val nextExpectedContribution: Double?,
    val lastContributionDate: LocalDate?,
    val savingTaxRateOverride: Double?,
    val archived: Boolean,
    val accountType: AccountType?,
    val accountNumber: String?,
    val accountNotes: String?,
    val createdAt: LocalDate = LocalDate.now(),
    @ColumnInfo(name = "default_manual_deposit_deduct_main")
    val defaultManualDepositDeductFromMain: Boolean = true,
    @ColumnInfo(name = "default_manual_withdraw_add_main")
    val defaultManualWithdrawalCreditMain: Boolean = true,
    val iconName: String? = null,
    // High-yield savings support
    val isHighYieldAccount: Boolean = false,
    val annualPercentageYield: Double? = null,
    val lastInterestCalculation: LocalDate? = null,
    val accruedInterest: Double = 0.0
)

{
    /**
     * Secondary constructor to support older generated code that used a smaller parameter list.
     * Delegates to the primary constructor, providing sensible defaults for newly added fields.
     */
    constructor(
        id: Long,
        name: String,
        targetAmount: Double,
        currentBalance: Double,
        targetDate: LocalDate?,
        priority: com.example.sparely.domain.model.VaultPriority,
        type: com.example.sparely.domain.model.VaultType,
        interestRate: Double?,
        allocationMode: com.example.sparely.domain.model.VaultAllocationMode,
        manualAllocationPercent: Double?,
        nextExpectedContribution: Double?,
        lastContributionDate: LocalDate?,
        savingTaxRateOverride: Double?,
        archived: Boolean,
        accountType: com.example.sparely.domain.model.AccountType?,
        accountNumber: String?,
        accountNotes: String?,
        createdAt: LocalDate?,
        iconName: String? = null
    ) : this(
        id = id,
        name = name,
        targetAmount = targetAmount,
        currentBalance = currentBalance,
        targetDate = targetDate,
        startDate = null,
        endDate = null,
        monthlyNeed = null,
        priorityWeight = 1.0,
        autoSaveEnabled = true,
        allowAutoIncome = true,
        priority = priority,
        type = type,
        interestRate = interestRate,
        allocationMode = allocationMode,
        manualAllocationPercent = manualAllocationPercent,
        nextExpectedContribution = nextExpectedContribution,
        lastContributionDate = lastContributionDate,
        savingTaxRateOverride = savingTaxRateOverride,
        archived = archived,
        accountType = accountType,
        accountNumber = accountNumber,
        accountNotes = accountNotes,
        createdAt = createdAt ?: LocalDate.now(),
        defaultManualDepositDeductFromMain = true,
        defaultManualWithdrawalCreditMain = true,
        iconName = iconName,
        isHighYieldAccount = false,
        annualPercentageYield = null,
        lastInterestCalculation = null,
        accruedInterest = 0.0
    )
}

@Entity(
    tableName = "vault_contributions",
    foreignKeys = [
        ForeignKey(
            entity = SmartVaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["relatedExpenseId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = SavingsAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["savingsAccountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vaultId"), Index("date"), Index("relatedExpenseId"), Index("savingsAccountId")]
)
data class VaultContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val vaultId: Long? = null,  // Nullable: null when this is an HISA transfer
    val amount: Double,
    val date: LocalDate,
    val source: VaultContributionSource,
    val note: String?,
    val reconciled: Boolean,
    val relatedExpenseId: Long? = null,
    val savingsAccountId: Long? = null  // Non-null when this is an HISA transfer
)

@Entity(
    tableName = "vault_balance_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = SmartVaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vaultId"), Index("createdAt")]
)
data class VaultBalanceAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val vaultId: Long,
    val type: VaultAdjustmentType,
    val delta: Double,
    val resultingBalance: Double,
    val createdAt: Instant,
    val reason: String?
)

@Entity(
    tableName = "vault_schedules",
    foreignKeys = [
        ForeignKey(
            entity = SmartVaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vaultId"), Index("enabled"), Index("nextRunAt")]
)
data class VaultScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val vaultId: Long,
    val type: VaultScheduleType,
    val amount: Double?,
    val percentage: Double?,
    val direction: VaultTransferDirection,
    val dateValue: LocalDate?,
    val repeatAnnually: Boolean,
    val dayOfMonth: Int?,
    val dayOfWeek: Int?,
    val weekInterval: Int?,
    val onlyIfBalanceAvailable: Boolean,
    val notifyBefore: Boolean,
    val notifyAfter: Boolean,
    val notifyOnFailure: Boolean,
    val nextRunAt: LocalDateTime?,
    val lastRunAt: LocalDateTime?,
    val enabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class SmartVaultWithSchedules(
    @Embedded val vault: SmartVaultEntity,
    @Relation(parentColumn = "id", entityColumn = "vaultId") val schedules: List<VaultScheduleEntity>
)
