package com.example.sparely.data.local

import com.example.sparely.domain.model.Achievement
import com.example.sparely.domain.model.CategoryBudget
import com.example.sparely.domain.model.ChallengeMilestone
import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.SavingsAccount
import com.example.sparely.domain.model.SavingsChallenge
import com.example.sparely.domain.model.SavingsProductType
import com.example.sparely.domain.model.SavingsPercentages
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.VaultBalanceAdjustment
import com.example.sparely.domain.model.VaultContribution
import com.example.sparely.domain.model.VaultSchedule
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.Store
import com.example.sparely.domain.model.PaymentMethod
import com.example.sparely.domain.model.PaymentMethodType
import com.example.sparely.domain.model.AmountHistoryEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.YearMonth

fun CategoryBudgetEntity.toDomain(): CategoryBudget = CategoryBudget(
    id = id,
    category = category,
    monthlyLimit = monthlyLimit,
    yearMonth = YearMonth.of(year, month),
    isActive = isActive
)

fun CategoryBudget.toEntity(): CategoryBudgetEntity = CategoryBudgetEntity(
    id = id,
    category = category,
    monthlyLimit = monthlyLimit,
    year = yearMonth.year,
    month = yearMonth.monthValue,
    isActive = isActive
)

private val gson = Gson()

fun RecurringExpenseEntity.toDomain(): RecurringExpense {
    val amountHistory: List<AmountHistoryEntry> = amountHistoryJson?.let {
        try {
            val type = object : TypeToken<List<AmountHistoryEntry>>() {}.type
            gson.fromJson(it, type)
        } catch (e: Exception) {
            emptyList()
        }
    } ?: emptyList()

    val assetAllocations: Map<Long, Double> = assetAllocationsJson?.let {
        try {
            val type = object : TypeToken<Map<Long, Double>>() {}.type
            gson.fromJson(it, type)
        } catch (e: Exception) {
            emptyMap()
        }
    } ?: emptyMap()

    return RecurringExpense(
        id = id,
        description = description,
        amount = amount,
        category = category,
        frequency = frequency,
        startDate = startDate,
        endDate = endDate,
        lastProcessedDate = lastProcessedDate,
        isActive = isActive,
        autoLog = autoLog,
        executeAutomatically = executeAutomatically,
        reminderDaysBefore = reminderDaysBefore,
        merchantName = merchantName,
        notes = notes,
        storeId = storeId,
        includesTax = includesTax,
        deductFromMainAccount = deductFromMainAccount,
        deductedFromVaultId = deductedFromVaultId,
        manualPercentages = if (manualPercentEmergency != null || manualPercentInvest != null || manualPercentFun != null || manualSafeSplit != null) {
            SavingsPercentages(
                emergency = manualPercentEmergency ?: 0.0,
                invest = manualPercentInvest ?: 0.0,
                `fun` = manualPercentFun ?: 0.0,
                safeInvestmentSplit = manualSafeSplit ?: 0.5
            )
        } else null,
        paymentMethodId = paymentMethodId,
        isVariableAmount = isVariableAmount,
        amountHistory = amountHistory,
        estimatedAmount = estimatedAmount,
        nextRunAt = nextRunAt,
        type = try {
            com.example.sparely.domain.model.ExpenseType.valueOf(type)
        } catch (e: Exception) {
            com.example.sparely.domain.model.ExpenseType.PRODUCT
        },
        items = emptyList(), // Items will be loaded separately via DAO relationship
        assetAllocations = assetAllocations,
        necessityOverride = necessity?.let { runCatching { com.example.sparely.domain.model.Necessity.valueOf(it) }.getOrNull() }
    )
}

fun RecurringExpense.toEntity(): RecurringExpenseEntity = RecurringExpenseEntity(
    id = id,
    description = description,
    amount = amount,
    category = category,
    frequency = frequency,
    startDate = startDate,
    endDate = endDate,
    lastProcessedDate = lastProcessedDate,
    isActive = isActive,
    autoLog = autoLog,
    executeAutomatically = executeAutomatically,
    reminderDaysBefore = reminderDaysBefore,
    merchantName = merchantName,
    notes = notes,
    storeId = storeId,
    includesTax = includesTax,
    deductFromMainAccount = deductFromMainAccount,
    deductedFromVaultId = deductedFromVaultId,
    manualPercentEmergency = manualPercentages?.emergency,
    manualPercentInvest = manualPercentages?.invest,
    manualPercentFun = manualPercentages?.`fun`,
    manualSafeSplit = manualPercentages?.safeInvestmentSplit,
    paymentMethodId = paymentMethodId,
    isVariableAmount = isVariableAmount,
    amountHistoryJson = if (amountHistory.isNotEmpty()) gson.toJson(amountHistory) else null,
    estimatedAmount = estimatedAmount,
    nextRunAt = nextRunAt,
    type = type.name,
    assetAllocationsJson = if (assetAllocations.isNotEmpty()) gson.toJson(assetAllocations) else null,
    necessity = necessityOverride?.name
)

fun ChallengeMilestoneEntity.toDomain(): ChallengeMilestone = ChallengeMilestone(
    description = description,
    targetAmount = targetAmount,
    isAchieved = isAchieved,
    achievedDate = achievedDate,
    rewardPoints = rewardPoints
)

fun ChallengeMilestone.toEntity(challengeId: Long): ChallengeMilestoneEntity = ChallengeMilestoneEntity(
    challengeId = challengeId,
    description = description,
    targetAmount = targetAmount,
    isAchieved = isAchieved,
    achievedDate = achievedDate,
    rewardPoints = rewardPoints
)

fun SavingsChallengeWithMilestones.toDomain(): SavingsChallenge = SavingsChallenge(
    id = challenge.id,
    type = challenge.type,
    title = challenge.title,
    description = challenge.description,
    targetAmount = challenge.targetAmount,
    currentAmount = challenge.currentAmount,
    startDate = challenge.startDate,
    endDate = challenge.endDate,
    isActive = challenge.isActive,
    isCompleted = challenge.isCompleted,
    completedDate = challenge.completedDate,
    streakDays = challenge.streakDays,
    milestones = milestones.map { it.toDomain() }
)

fun SavingsChallenge.toEntity(): Pair<SavingsChallengeEntity, List<ChallengeMilestoneEntity>> {
    val entity = SavingsChallengeEntity(
        id = id,
        type = type,
        title = title,
        description = description,
        targetAmount = targetAmount,
        currentAmount = currentAmount,
        startDate = startDate,
        endDate = endDate,
        isActive = isActive,
        isCompleted = isCompleted,
        completedDate = completedDate,
        streakDays = streakDays
    )
    val milestoneEntities = milestones.map { it.toEntity(id) }
    return entity to milestoneEntities
}

fun AchievementEntity.toDomain(): Achievement = Achievement(
    id = id,
    title = title,
    description = description,
    icon = icon,
    earnedDate = earnedDate,
    category = category
)

fun Achievement.toEntity(): AchievementEntity = AchievementEntity(
    id = id,
    title = title,
    description = description,
    icon = icon,
    earnedDate = earnedDate,
    category = category
)

fun SavingsAccountEntity.toDomain(): SavingsAccount = SavingsAccount(
    id = id,
    name = name,
    currentBalance = currentBalance,
    annualPercentageYield = annualPercentageYield,
    totalInterestEarned = totalInterestEarned,
    lastInterestEntryDate = lastInterestEntryDate,
    accountNumber = accountNumber,
    institution = institution,
    accountNotes = accountNotes,
    isMainOverflowAccount = isMainOverflowAccount,
    iconName = iconName,
    createdAt = createdAt,
    archived = archived,
    productType = runCatching { SavingsProductType.valueOf(productType) }.getOrDefault(SavingsProductType.FLEXIBLE),
    termMonths = termMonths,
    termStartDate = termStartDate,
    noticeDays = noticeDays,
    gracePeriodDays = gracePeriodDays,
    anniversaryWindowDays = anniversaryWindowDays,
    minWithdrawalAmount = minWithdrawalAmount,
    minRemainingBalance = minRemainingBalance,
    earlyWithdrawalPenaltyDays = earlyWithdrawalPenaltyDays
)

fun SavingsAccount.toEntity(): SavingsAccountEntity = SavingsAccountEntity(
    id = id,
    name = name,
    currentBalance = currentBalance,
    annualPercentageYield = annualPercentageYield,
    totalInterestEarned = totalInterestEarned,
    lastInterestEntryDate = lastInterestEntryDate,
    accountNumber = accountNumber,
    institution = institution,
    accountNotes = accountNotes,
    isMainOverflowAccount = isMainOverflowAccount,
    iconName = iconName,
    createdAt = createdAt,
    archived = archived,
    productType = productType.name,
    termMonths = termMonths,
    termStartDate = termStartDate,
    noticeDays = noticeDays,
    gracePeriodDays = gracePeriodDays,
    anniversaryWindowDays = anniversaryWindowDays,
    minWithdrawalAmount = minWithdrawalAmount,
    minRemainingBalance = minRemainingBalance,
    earlyWithdrawalPenaltyDays = earlyWithdrawalPenaltyDays
)

fun SmartVaultEntity.toDomain(schedules: List<VaultSchedule> = emptyList()): SmartVault = SmartVault(
    id = id,
    name = name,
    targetAmount = targetAmount,
    currentBalance = currentBalance,
    targetDate = targetDate,
    startDate = startDate,
    endDate = endDate,
    monthlyNeed = monthlyNeed,
    priorityWeight = priorityWeight,
    autoSaveEnabled = autoSaveEnabled,
    allowAutoIncome = allowAutoIncome,
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
    createdAt = createdAt,
    defaultManualDepositDeductFromMain = defaultManualDepositDeductFromMain,
    defaultManualWithdrawalCreditMain = defaultManualWithdrawalCreditMain,
    schedules = schedules,
    iconName = iconName,
    isHighYieldAccount = isHighYieldAccount,
    annualPercentageYield = annualPercentageYield,
    lastInterestCalculation = lastInterestCalculation,
    accruedInterest = accruedInterest
)

fun SmartVault.toEntity(): SmartVaultEntity = SmartVaultEntity(
    id = id,
    name = name,
    targetAmount = targetAmount,
    currentBalance = currentBalance,
    targetDate = targetDate,
    startDate = startDate,
    endDate = endDate,
    monthlyNeed = monthlyNeed,
    priorityWeight = priorityWeight,
    autoSaveEnabled = autoSaveEnabled,
    allowAutoIncome = allowAutoIncome,
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
    createdAt = createdAt,
    defaultManualDepositDeductFromMain = defaultManualDepositDeductFromMain,
    defaultManualWithdrawalCreditMain = defaultManualWithdrawalCreditMain,
    iconName = iconName,
    isHighYieldAccount = isHighYieldAccount,
    annualPercentageYield = annualPercentageYield,
    lastInterestCalculation = lastInterestCalculation,
    accruedInterest = accruedInterest
)

fun VaultScheduleEntity.toDomain(): VaultSchedule = VaultSchedule(
    id = id,
    vaultId = vaultId,
    type = type,
    amount = amount,
    percentage = percentage,
    direction = direction,
    dateValue = dateValue,
    repeatAnnually = repeatAnnually,
    dayOfMonth = dayOfMonth,
    dayOfWeek = dayOfWeek,
    weekInterval = weekInterval,
    onlyIfBalanceAvailable = onlyIfBalanceAvailable,
    notifyBefore = notifyBefore,
    notifyAfter = notifyAfter,
    notifyOnFailure = notifyOnFailure,
    nextRunAt = nextRunAt,
    lastRunAt = lastRunAt,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun VaultSchedule.toEntity(): VaultScheduleEntity = VaultScheduleEntity(
    id = id,
    vaultId = vaultId,
    type = type,
    amount = amount,
    percentage = percentage,
    direction = direction,
    dateValue = dateValue,
    repeatAnnually = repeatAnnually,
    dayOfMonth = dayOfMonth,
    dayOfWeek = dayOfWeek,
    weekInterval = weekInterval,
    onlyIfBalanceAvailable = onlyIfBalanceAvailable,
    notifyBefore = notifyBefore,
    notifyAfter = notifyAfter,
    notifyOnFailure = notifyOnFailure,
    nextRunAt = nextRunAt,
    lastRunAt = lastRunAt,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun VaultContributionEntity.toDomain(): VaultContribution = VaultContribution(
    id = id,
    vaultId = vaultId,
    amount = amount,
    date = date,
    source = source,
    note = note,
    reconciled = reconciled,
    relatedExpenseId = relatedExpenseId,
    savingsAccountId = savingsAccountId
)

fun VaultContribution.toEntity(): VaultContributionEntity = VaultContributionEntity(
    id = id,
    vaultId = vaultId,
    amount = amount,
    date = date,
    source = source,
    note = note,
    reconciled = reconciled,
    relatedExpenseId = relatedExpenseId,
    savingsAccountId = savingsAccountId
)

fun VaultBalanceAdjustmentEntity.toDomain(): VaultBalanceAdjustment = VaultBalanceAdjustment(
    id = id,
    vaultId = vaultId,
    type = type,
    delta = delta,
    resultingBalance = resultingBalance,
    createdAt = createdAt,
    reason = reason
)

fun VaultBalanceAdjustment.toEntity(): VaultBalanceAdjustmentEntity = VaultBalanceAdjustmentEntity(
    id = id,
    vaultId = vaultId,
    type = type,
    delta = delta,
    resultingBalance = resultingBalance,
    createdAt = createdAt,
    reason = reason
)

fun SmartVaultWithSchedules.toDomain(): SmartVault {
    val schedules = schedules.map { it.toDomain() }
    return vault.toDomain(schedules)
}

fun MainAccountTransactionDetails.toDomain(): com.example.sparely.domain.model.MainAccountTransaction =
    com.example.sparely.domain.model.MainAccountTransaction(
        id = transaction.id,
        type = transaction.type,
        amount = transaction.amount,
        balanceAfter = transaction.balanceAfter,
        timestamp = transaction.timestamp,
        description = transaction.description,
        relatedExpenseId = transaction.relatedExpenseId,
        relatedVaultContributionIds = vaultContributions.map { it.id },
        incomeCategory = transaction.incomeCategory
    )

fun com.example.sparely.domain.model.MainAccountTransaction.toEntity(): MainAccountTransactionEntity =
    MainAccountTransactionEntity(
        id = id,
        type = type,
        amount = amount,
        balanceAfter = balanceAfter,
        timestamp = timestamp,
        description = description,
        relatedExpenseId = relatedExpenseId,
        incomeCategory = incomeCategory
    )

fun ExpenseEntity.toDomain(): Expense = Expense(
    id = id,
    description = description,
    amount = amount,
    category = category,
    date = date,
    includesTax = includesTax,
    allocation = com.example.sparely.domain.model.AllocationBreakdown(
        emergencyAmount = emergencyAmount,
        investmentAmount = investmentAmount,
        funAmount = funAmount,
        safeInvestmentAmount = safeInvestmentAmount,
        highRiskInvestmentAmount = highRiskInvestmentAmount
    ),
    appliedPercentages = com.example.sparely.domain.model.SavingsPercentages(
        emergency = appliedPercentEmergency,
        invest = appliedPercentInvest,
        `fun` = appliedPercentFun,
        safeInvestmentSplit = appliedSafeSplit
    ),
    autoRecommended = autoRecommended,
    riskLevelUsed = riskLevelUsed,
    deductedFromVaultId = deductedFromVaultId,
    storeId = storeId,
    paymentMethodId = paymentMethodId,
    isRecurring = isRecurring,
    notes = notes,
    refundedAmount = refundedAmount,
    isRefunded = isRefunded,
    orderNumber = orderNumber,
    type = try {
        com.example.sparely.domain.model.ExpenseType.valueOf(type)
    } catch (e: Exception) {
        com.example.sparely.domain.model.ExpenseType.PRODUCT
    },
    isIgnored = isIgnored
)

fun Expense.toEntity(): ExpenseEntity = ExpenseEntity(
    id = id,
    description = description,
    amount = amount,
    category = category,
    date = date,
    includesTax = includesTax,
    emergencyAmount = allocation.emergencyAmount,
    investmentAmount = allocation.investmentAmount,
    funAmount = allocation.funAmount,
    safeInvestmentAmount = allocation.safeInvestmentAmount,
    highRiskInvestmentAmount = allocation.highRiskInvestmentAmount,
    autoRecommended = autoRecommended,
    appliedPercentEmergency = appliedPercentages.emergency,
    appliedPercentInvest = appliedPercentages.invest,
    appliedPercentFun = appliedPercentages.`fun`,
    appliedSafeSplit = appliedPercentages.safeInvestmentSplit,
    riskLevelUsed = riskLevelUsed,
    deductedFromVaultId = deductedFromVaultId,
    storeId = storeId,
    paymentMethodId = paymentMethodId,
    isRecurring = isRecurring,
    notes = notes,
    refundedAmount = refundedAmount,
    isRefunded = isRefunded,
    orderNumber = orderNumber,
    type = type.name,
    isIgnored = isIgnored
)

fun StoreEntity.toDomain(): Store = Store(
    id = id,
    name = name,
    websiteUrl = websiteUrl,
    iconName = iconName,
    createdAt = createdAt
)

fun Store.toEntity(): StoreEntity = StoreEntity(
    id = id,
    name = name,
    websiteUrl = websiteUrl,
    iconName = iconName,
    createdAt = createdAt
)

fun PaymentMethodEntity.toDomain(): PaymentMethod = PaymentMethod(
    id = id,
    name = name,
    type = safeEnumValueOf(type, PaymentMethodType.OTHER),
    defaultDeductFromMainAccount = defaultDeductFromMainAccount,
    isDefault = isDefault,
    iconName = iconName,
    isCreditCard = isCreditCard,
    creditLimit = creditLimit,
    currentBalance = currentBalance,
    billingCycleDay = billingCycleDay,
    lastPaymentDate = lastPaymentDate,
    lastPaymentAmount = lastPaymentAmount
)

fun PaymentMethod.toEntity(): PaymentMethodEntity = PaymentMethodEntity(
    id = id,
    name = name,
    type = type.name,
    defaultDeductFromMainAccount = defaultDeductFromMainAccount,
    isDefault = isDefault,
    iconName = iconName,
    isCreditCard = isCreditCard,
    creditLimit = creditLimit,
    currentBalance = currentBalance,
    billingCycleDay = billingCycleDay,
    lastPaymentDate = lastPaymentDate,
    lastPaymentAmount = lastPaymentAmount
)

fun CreditCardPaymentEntity.toDomain(): com.example.sparely.domain.model.CreditCardPayment =
    com.example.sparely.domain.model.CreditCardPayment(
        id = id,
        paymentMethodId = paymentMethodId,
        amount = amount,
        date = date,
        note = note
    )

fun com.example.sparely.domain.model.CreditCardPayment.toEntity(): CreditCardPaymentEntity =
    CreditCardPaymentEntity(
        id = id,
        paymentMethodId = paymentMethodId,
        amount = amount,
        date = date,
        note = note
    )

fun ExpenseItemEntity.toDomain(): com.example.sparely.domain.model.ExpenseItem =
    com.example.sparely.domain.model.ExpenseItem(
        id = id,
        expenseId = expenseId,
        name = name,
        quantity = quantity,
        unitPrice = unitPrice,
        totalPrice = totalPrice,
        type = try {
            com.example.sparely.domain.model.ExpenseType.valueOf(type)
        } catch (e: Exception) {
            com.example.sparely.domain.model.ExpenseType.PRODUCT
        }
    )

fun com.example.sparely.domain.model.ExpenseItem.toEntity(): ExpenseItemEntity =
    ExpenseItemEntity(
        id = id,
        expenseId = expenseId,
        name = name,
        quantity = quantity,
        unitPrice = unitPrice,
        totalPrice = totalPrice,
        type = type.name
    )

fun ExpenseRefundEntity.toDomain(): com.example.sparely.domain.model.ExpenseRefund =
    com.example.sparely.domain.model.ExpenseRefund(
        id = id,
        expenseId = expenseId,
        refundedAmount = refundedAmount,
        refundDate = refundDate,
        refundMethod = refundMethod,
        reason = reason,
        refundedItemIds = refundedItemIds?.let {
            try {
                val type = object : TypeToken<List<Long>>() {}.type
                gson.fromJson(it, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()
    )

fun com.example.sparely.domain.model.ExpenseRefund.toEntity(): ExpenseRefundEntity =
    ExpenseRefundEntity(
        id = id,
        expenseId = expenseId,
        refundedAmount = refundedAmount,
        refundDate = refundDate,
        refundMethod = refundMethod,
        reason = reason,
        refundedItemIds = if (refundedItemIds.isNotEmpty()) gson.toJson(refundedItemIds) else null
    )

fun AssetEntity.toDomain(): com.example.sparely.domain.model.Asset =
    com.example.sparely.domain.model.Asset(
        id = id,
        name = name,
        description = description,
        category = try {
            com.example.sparely.domain.model.AssetCategory.valueOf(category)
        } catch (e: Exception) {
            com.example.sparely.domain.model.AssetCategory.OTHER
        },
        icon = icon,
        createdAt = createdAt,
        archived = archived,
        assetPrice = assetPrice,
        totalSpending = 0.0,
        creatorExpenseId = creatorExpenseId
    )

fun com.example.sparely.domain.model.Asset.toEntity(): AssetEntity =
    AssetEntity(
        id = id,
        name = name,
        description = description,
        category = category.name,
        icon = icon,
        createdAt = createdAt,
        archived = archived,
        assetPrice = assetPrice,
        creatorExpenseId = creatorExpenseId
    )

fun AssetExpenseLinkEntity.toDomain(): com.example.sparely.domain.model.AssetExpenseLink =
    com.example.sparely.domain.model.AssetExpenseLink(
        id = id,
        assetId = assetId,
        expenseId = expenseId,
        percentageAllocated = percentageAllocated,
        linkedAt = linkedAt
    )

fun com.example.sparely.domain.model.AssetExpenseLink.toEntity(): AssetExpenseLinkEntity =
    AssetExpenseLinkEntity(
        id = id,
        assetId = assetId,
        expenseId = expenseId,
        percentageAllocated = percentageAllocated,
        linkedAt = linkedAt
    )

fun WishlistEntity.toDomain(): com.example.sparely.domain.model.Wishlist =
    com.example.sparely.domain.model.Wishlist(
        id = id,
        description = description,
        targetAmount = targetAmount,
        currentSavings = currentSavings,
        priority = priority,
        createdAt = createdAt,
        cooldownExpiresAt = cooldownExpiresAt,
        isReallyNeeded = isReallyNeeded,
        category = category?.let {
            try {
                com.example.sparely.domain.model.WishlistCategory.valueOf(it)
            } catch (e: Exception) {
                null
            }
        },
        notes = notes,
        imageUrl = imageUrl,
        archived = archived
    )

fun com.example.sparely.domain.model.Wishlist.toEntity(): WishlistEntity =
    WishlistEntity(
        id = id,
        description = description,
        targetAmount = targetAmount,
        currentSavings = currentSavings,
        priority = priority,
        createdAt = createdAt,
        cooldownExpiresAt = cooldownExpiresAt,
        isReallyNeeded = isReallyNeeded,
        category = category?.name,
        notes = notes,
        imageUrl = imageUrl,
        archived = archived
    )

fun WishlistSavingsEntity.toDomain(): com.example.sparely.domain.model.WishlistSavings =
    com.example.sparely.domain.model.WishlistSavings(
        id = id,
        wishlistId = wishlistId,
        amount = amount,
        date = date,
        source = source?.let {
            try {
                com.example.sparely.domain.model.SavingsSource.valueOf(it)
            } catch (e: Exception) {
                null
            }
        }
    )

fun com.example.sparely.domain.model.WishlistSavings.toEntity(): WishlistSavingsEntity =
    WishlistSavingsEntity(
        id = id,
        wishlistId = wishlistId,
        amount = amount,
        date = date,
        source = source?.name
    )



