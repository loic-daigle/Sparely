package com.example.sparely.data.local

import androidx.room.TypeConverter
import com.example.sparely.domain.model.AchievementCategory
import com.example.sparely.domain.model.AmountHistoryEntry
import com.example.sparely.domain.model.AutoDepositFrequency
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.sparely.domain.model.BankSyncProvider
import com.example.sparely.domain.model.ChallengeType
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.RecurringFrequency
import com.example.sparely.domain.model.RiskLevel
import com.example.sparely.domain.model.SavingsCategory
import com.example.sparely.domain.model.VaultAllocationMode
import com.example.sparely.domain.model.VaultAdjustmentType
import com.example.sparely.domain.model.VaultContributionSource
import com.example.sparely.domain.model.VaultPriority
import com.example.sparely.domain.model.VaultType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Room converters for enums and date types.
 */
class Converters {
    @TypeConverter
    fun fromEpochDay(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun fromEpochSecond(value: Long?): LocalDateTime? = value?.let { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }

    @TypeConverter
    fun toEpochSecond(dateTime: LocalDateTime?): Long? = dateTime?.toEpochSecond(ZoneOffset.UTC)

    @TypeConverter
    fun fromRisk(value: String?): RiskLevel? = value?.let { RiskLevel.valueOf(it) }

    @TypeConverter
    fun toRisk(level: RiskLevel?): String? = level?.name

    @TypeConverter
    fun fromSavingsCategory(value: String?): SavingsCategory? = value?.let { SavingsCategory.valueOf(it) }

    @TypeConverter
    fun toSavingsCategory(category: SavingsCategory?): String? = category?.name

    @TypeConverter
    fun fromExpenseCategory(value: String?): ExpenseCategory? = value?.let { ExpenseCategory.valueOf(it) }

    @TypeConverter
    fun toExpenseCategory(category: ExpenseCategory?): String? = category?.name

    @TypeConverter
    fun fromChallengeType(value: String?): ChallengeType? = value?.let { ChallengeType.valueOf(it) }

    @TypeConverter
    fun toChallengeType(type: ChallengeType?): String? = type?.name

    @TypeConverter
    fun fromAchievementCategory(value: String?): AchievementCategory? = value?.let { AchievementCategory.valueOf(it) }

    @TypeConverter
    fun toAchievementCategory(category: AchievementCategory?): String? = category?.name

    @TypeConverter
    fun fromRecurringFrequency(value: String?): RecurringFrequency? = value?.let { RecurringFrequency.valueOf(it) }

    @TypeConverter
    fun toRecurringFrequency(freq: RecurringFrequency?): String? = freq?.name

    @TypeConverter
    fun fromInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun toInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun fromBankSyncProvider(value: String?): BankSyncProvider? = value?.let { BankSyncProvider.valueOf(it) }

    @TypeConverter
    fun toBankSyncProvider(provider: BankSyncProvider?): String? = provider?.name

    @TypeConverter
    fun fromVaultPriority(value: String?): VaultPriority? = value?.let { VaultPriority.valueOf(it) }

    @TypeConverter
    fun toVaultPriority(priority: VaultPriority?): String? = priority?.name

    @TypeConverter
    fun fromVaultType(value: String?): VaultType? = value?.let { VaultType.valueOf(it) }

    @TypeConverter
    fun toVaultType(type: VaultType?): String? = type?.name

    @TypeConverter
    fun fromVaultAllocationMode(value: String?): VaultAllocationMode? = value?.let { VaultAllocationMode.valueOf(it) }

    @TypeConverter
    fun toVaultAllocationMode(mode: VaultAllocationMode?): String? = mode?.name

    @TypeConverter
    fun fromAutoDepositFrequency(value: String?): AutoDepositFrequency? = value?.let { AutoDepositFrequency.valueOf(it) }

    @TypeConverter
    fun toAutoDepositFrequency(frequency: AutoDepositFrequency?): String? = frequency?.name

    @TypeConverter
    fun fromVaultContributionSource(value: String?): VaultContributionSource? = value?.let { VaultContributionSource.valueOf(it) }

    @TypeConverter
    fun toVaultContributionSource(source: VaultContributionSource?): String? = source?.name

    @TypeConverter
    fun fromVaultAdjustmentType(value: String?): VaultAdjustmentType? = value?.let { VaultAdjustmentType.valueOf(it) }

    @TypeConverter
    fun toVaultAdjustmentType(type: VaultAdjustmentType?): String? = type?.name

    @TypeConverter
    fun fromMainAccountTransactionType(value: String?): MainAccountTransactionType? = value?.let { MainAccountTransactionType.valueOf(it) }

    @TypeConverter
    fun toMainAccountTransactionType(type: MainAccountTransactionType?): String? = type?.name

    @TypeConverter
    fun fromSavingsAccountTransactionType(value: String?): SavingsAccountTransactionType? = value?.let { SavingsAccountTransactionType.valueOf(it) }

    @TypeConverter
    fun toSavingsAccountTransactionType(type: SavingsAccountTransactionType?): String? = type?.name

    private val gson = Gson()

    @TypeConverter
    fun fromAmountHistoryJson(value: String?): List<AmountHistoryEntry>? {
        if (value == null) return null
        val type = object : TypeToken<List<AmountHistoryEntry>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun toAmountHistoryJson(history: List<AmountHistoryEntry>?): String? {
        return gson.toJson(history)
    }

    @TypeConverter
    fun fromAssetAllocationJson(value: String?): Map<Long, Double>? {
        if (value == null) return null
        val type = object : TypeToken<Map<Long, Double>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun toAssetAllocationJson(allocations: Map<Long, Double>?): String? {
        return gson.toJson(allocations)
    }
}
