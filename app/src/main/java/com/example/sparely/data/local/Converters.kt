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
    fun fromEpochDay(value: Long?): LocalDate? = value?.let { runCatching { LocalDate.ofEpochDay(it) }.getOrNull() }

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun fromEpochSecond(value: Long?): LocalDateTime? = value?.let { runCatching { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }.getOrNull() }

    @TypeConverter
    fun toEpochSecond(dateTime: LocalDateTime?): Long? = dateTime?.toEpochSecond(ZoneOffset.UTC)

    @TypeConverter
    fun fromRisk(value: String?): RiskLevel? = value?.let { safeEnumValueOf(it, RiskLevel.BALANCED) }

    @TypeConverter
    fun toRisk(level: RiskLevel?): String? = level?.name

    @TypeConverter
    fun fromSavingsCategory(value: String?): SavingsCategory? = value?.let { safeEnumValueOf(it, SavingsCategory.EMERGENCY) }

    @TypeConverter
    fun toSavingsCategory(category: SavingsCategory?): String? = category?.name

    @TypeConverter
    fun fromExpenseCategory(value: String?): ExpenseCategory? = value?.let { safeEnumValueOf(it, ExpenseCategory.OTHER) }

    @TypeConverter
    fun toExpenseCategory(category: ExpenseCategory?): String? = category?.name

    @TypeConverter
    fun fromChallengeType(value: String?): ChallengeType? = value?.let { safeEnumValueOf(it, ChallengeType.CUSTOM) }

    @TypeConverter
    fun toChallengeType(type: ChallengeType?): String? = type?.name

    @TypeConverter
    fun fromAchievementCategory(value: String?): AchievementCategory? = value?.let { safeEnumValueOf(it, AchievementCategory.SAVINGS_MILESTONE) }

    @TypeConverter
    fun toAchievementCategory(category: AchievementCategory?): String? = category?.name

    @TypeConverter
    fun fromRecurringFrequency(value: String?): RecurringFrequency? = value?.let { safeEnumValueOf(it, RecurringFrequency.MONTHLY) }

    @TypeConverter
    fun toRecurringFrequency(freq: RecurringFrequency?): String? = freq?.name

    @TypeConverter
    fun fromInstant(value: Long?): Instant? = value?.let { runCatching { Instant.ofEpochMilli(it) }.getOrNull() }

    @TypeConverter
    fun toInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun fromBankSyncProvider(value: String?): BankSyncProvider? = value?.let { safeEnumValueOf(it, BankSyncProvider.MOCK) }

    @TypeConverter
    fun toBankSyncProvider(provider: BankSyncProvider?): String? = provider?.name

    @TypeConverter
    fun fromVaultPriority(value: String?): VaultPriority? = value?.let { safeEnumValueOf(it, VaultPriority.MEDIUM) }

    @TypeConverter
    fun toVaultPriority(priority: VaultPriority?): String? = priority?.name

    @TypeConverter
    fun fromVaultType(value: String?): VaultType? = value?.let { safeEnumValueOf(it, VaultType.GOAL) }

    @TypeConverter
    fun toVaultType(type: VaultType?): String? = type?.name

    @TypeConverter
    fun fromVaultAllocationMode(value: String?): VaultAllocationMode? = value?.let { safeEnumValueOf(it, VaultAllocationMode.DYNAMIC_AUTO) }

    @TypeConverter
    fun toVaultAllocationMode(mode: VaultAllocationMode?): String? = mode?.name

    @TypeConverter
    fun fromAutoDepositFrequency(value: String?): AutoDepositFrequency? = value?.let { safeEnumValueOf(it, AutoDepositFrequency.MONTHLY) }

    @TypeConverter
    fun toAutoDepositFrequency(frequency: AutoDepositFrequency?): String? = frequency?.name

    @TypeConverter
    fun fromVaultContributionSource(value: String?): VaultContributionSource? = value?.let { safeEnumValueOf(it, VaultContributionSource.MANUAL) }

    @TypeConverter
    fun toVaultContributionSource(source: VaultContributionSource?): String? = source?.name

    @TypeConverter
    fun fromVaultAdjustmentType(value: String?): VaultAdjustmentType? = value?.let { safeEnumValueOf(it, VaultAdjustmentType.MANUAL_EDIT) }

    @TypeConverter
    fun toVaultAdjustmentType(type: VaultAdjustmentType?): String? = type?.name

    @TypeConverter
    fun fromMainAccountTransactionType(value: String?): MainAccountTransactionType? = value?.let { safeEnumValueOf(it, MainAccountTransactionType.ADJUSTMENT) }

    @TypeConverter
    fun toMainAccountTransactionType(type: MainAccountTransactionType?): String? = type?.name

    @TypeConverter
    fun fromSavingsAccountTransactionType(value: String?): SavingsAccountTransactionType? = value?.let { safeEnumValueOf(it, SavingsAccountTransactionType.DEPOSIT) }

    @TypeConverter
    fun toSavingsAccountTransactionType(type: SavingsAccountTransactionType?): String? = type?.name

    private val gson = Gson()

    @TypeConverter
    fun fromAmountHistoryJson(value: String?): List<AmountHistoryEntry>? {
        if (value.isNullOrBlank()) return null
        val type = object : TypeToken<List<AmountHistoryEntry>>() {}.type
        return runCatching { gson.fromJson<List<AmountHistoryEntry>?>(value, type) }
            .getOrNull()
            ?.filterNotNull()
            // Gson bypasses Kotlin null-safety, so guard against partially written entries.
            ?.filter { (it.date as LocalDate?) != null && it.amount.isFinite() }
    }

    @TypeConverter
    fun toAmountHistoryJson(history: List<AmountHistoryEntry>?): String? {
        return gson.toJson(history)
    }

    @TypeConverter
    fun fromAssetAllocationJson(value: String?): Map<Long, Double>? {
        if (value.isNullOrBlank()) return null
        val type = object : TypeToken<Map<Long, Double>>() {}.type
        return runCatching { gson.fromJson<Map<Long, Double>?>(value, type) }
            .getOrNull()
            ?.filterValues { it != null && it.isFinite() }
    }

    @TypeConverter
    fun toAssetAllocationJson(allocations: Map<Long, Double>?): String? {
        return gson.toJson(allocations)
    }
}

/**
 * Resolves an enum constant by name without throwing. Unknown names (for example values written
 * by a newer app version or restored from an old backup) fall back to [default] instead of
 * crashing every query that touches the row.
 */
internal inline fun <reified T : Enum<T>> safeEnumValueOf(name: String?, default: T): T {
    if (name == null) return default
    return enumValues<T>().firstOrNull { it.name == name }
        ?: enumValues<T>().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        ?: default
}
