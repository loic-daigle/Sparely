package com.example.sparely.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.datastore.preferences.preferencesDataStore
import com.example.sparely.domain.model.EducationStatus
import com.example.sparely.domain.model.EmploymentStatus
import com.example.sparely.domain.model.LivingSituation
import com.example.sparely.domain.model.IncomeTrackingMode
import com.example.sparely.domain.model.PayInterval
import com.example.sparely.domain.model.PayScheduleSettings
import com.example.sparely.domain.model.RegionalSettings
import com.example.sparely.domain.model.RiskLevel
import com.example.sparely.domain.model.SmartAllocationMode
import com.example.sparely.domain.model.SparelySettings
import com.example.sparely.domain.model.SavingsPercentages
import com.example.sparely.domain.model.VaultAllocationMode
import com.example.sparely.setAppLocale
import com.example.sparely.domain.model.CountryProfiles
import com.example.sparely.domain.model.ExpenseHistoryRetention
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

private const val DATASTORE_NAME = "sparely_settings"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)

class UserPreferencesRepository(private val context: Context) {

    val settingsFlow: Flow<SparelySettings> = context.dataStore.data.map { preferences ->
        preferences.toSettings()
    }

    suspend fun updateMainOverflowAccountId(accountId: Long?) {
        context.dataStore.edit { prefs ->
            if (accountId != null) {
                prefs[PreferenceKeys.mainOverflowAccountId] = accountId
            } else {
                prefs.remove(PreferenceKeys.mainOverflowAccountId)
            }
        }
    }

    suspend fun updateMinMainAccountBalance(value: Double) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.minMainAccountBalance] = value.coerceAtLeast(0.0)
        }
    }

    suspend fun updateAutoCreateAssetThreshold(threshold: Double) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.autoCreateAssetThreshold] = threshold.coerceAtLeast(0.0)
        }
    }

    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.onboardingCompleted] ?: false
    }

    val autoDepositCheckHourFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.autoDepositCheckHour] ?: 9
    }

    suspend fun getSettingsSnapshot(): SparelySettings = settingsFlow.first()

    suspend fun updatePercentages(percentages: SavingsPercentages) {
        context.dataStore.edit { prefs ->
            val adjusted = percentages.adjustWithinBudget()
            prefs[PreferenceKeys.emergencyPercent] = adjusted.emergency
            prefs[PreferenceKeys.investPercent] = adjusted.invest
            prefs[PreferenceKeys.funPercent] = adjusted.`fun`
            prefs[PreferenceKeys.safeSplit] = adjusted.safeInvestmentSplit
        }
    }

    suspend fun updateRiskLevel(riskLevel: RiskLevel) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.riskLevel] = riskLevel.name
        }
    }

    suspend fun toggleAutoRecommendations(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.autoRecommend] = enabled
        }
    }

    suspend fun updateIncludeTax(includeTax: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.includeTax] = includeTax
        }
    }

    suspend fun updateMonthlyIncome(value: Double) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.monthlyIncome] = value.coerceAtLeast(0.0)
        }
    }

    suspend fun updatePaySchedule(schedule: PayScheduleSettings) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.incomeTrackingMode] = schedule.trackingMode.name
            prefs[PreferenceKeys.payInterval] = schedule.interval.name
            prefs[PreferenceKeys.payDefaultNet] = schedule.defaultNetPay.coerceAtLeast(0.0)
            prefs[PreferenceKeys.payDefaultSaveRate] = schedule.defaultSaveRate.coerceIn(0.0, 1.0)
            prefs[PreferenceKeys.payWeeklyDay] = schedule.weeklyDayOfWeek.value
            prefs[PreferenceKeys.paySemiDay1] = schedule.semiMonthlyDay1.coerceIn(1, 28)
            prefs[PreferenceKeys.paySemiDay2] = schedule.semiMonthlyDay2.coerceIn(1, 28)
            prefs[PreferenceKeys.payMonthlyDay] = schedule.monthlyDay.coerceIn(1, 28)
            if (schedule.customDaysBetween != null) {
                prefs[PreferenceKeys.payCustomDays] = schedule.customDaysBetween.coerceAtLeast(1)
            } else {
                prefs.remove(PreferenceKeys.payCustomDays)
            }
            if (schedule.nextPayDate != null) {
                prefs[PreferenceKeys.payNextDate] = schedule.nextPayDate.toEpochDay()
            } else {
                prefs.remove(PreferenceKeys.payNextDate)
            }
            if (schedule.lastPayDate != null) {
                prefs[PreferenceKeys.payLastDate] = schedule.lastPayDate.toEpochDay()
            } else {
                prefs.remove(PreferenceKeys.payLastDate)
            }
            prefs[PreferenceKeys.payLastAmount] = schedule.lastPayAmount.coerceAtLeast(0.0)
            prefs[PreferenceKeys.payAutoDistribute] = schedule.autoDistributeToVaults
            prefs[PreferenceKeys.payAutoPending] = schedule.autoCreatePendingTransfers
            prefs[PreferenceKeys.payDynamicSaveEnabled] = schedule.dynamicSaveRateEnabled
            if (schedule.lastComputedSaveRate != null) {
                prefs[PreferenceKeys.payLastComputedSaveRate] = schedule.lastComputedSaveRate.coerceIn(0.0, 1.0)
            } else {
                prefs.remove(PreferenceKeys.payLastComputedSaveRate)
            }
        }
    }

    suspend fun updateTargetSavingsRate(rate: Double) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.targetSavingsRate] = rate.coerceIn(0.0, 1.0)
        }
    }

    suspend fun updateSmartAllocationMode(mode: SmartAllocationMode) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.smartAllocationMode] = mode.name
        }
    }

    suspend fun updateVaultAllocationMode(mode: VaultAllocationMode) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.vaultAllocationMode] = mode.name
        }
    }

    suspend fun updateSavingTaxRate(rate: Double, fromAutomation: Boolean = false) {
        context.dataStore.edit { prefs ->
            val sanitized = rate.coerceIn(0.0, 1.0)
            prefs[PreferenceKeys.savingTaxRate] = sanitized
            if (fromAutomation) {
                prefs[PreferenceKeys.lastComputedSavingTaxRate] = sanitized
            } else {
                prefs.remove(PreferenceKeys.lastComputedSavingTaxRate)
            }
        }
    }

    suspend fun updateDynamicSavingTaxEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.dynamicSavingTaxEnabled] = enabled
        }
    }
    
    suspend fun updateAutoDepositsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.autoDepositsEnabled] = enabled
        }
    }
    
    suspend fun updateAutoDepositCheckHour(hour: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.autoDepositCheckHour] = hour.coerceIn(0, 23)
        }
    }
    
    suspend fun getAutoDepositsEnabled(): Boolean {
        return context.dataStore.data.first()[PreferenceKeys.autoDepositsEnabled] ?: true
    }
    
    suspend fun getAutoDepositCheckHour(): Int {
        return context.dataStore.data.first()[PreferenceKeys.autoDepositCheckHour] ?: 9
    }

    suspend fun updateAge(age: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.age] = age.coerceIn(13, 100)
        }
    }

    suspend fun updateEducationStatus(status: EducationStatus) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.educationStatus] = status.name
        }
    }

    suspend fun updateEmploymentStatus(status: EmploymentStatus) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.employmentStatus] = status.name
        }
    }

    suspend fun updateLivingSituation(situation: LivingSituation) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.livingSituation] = situation.name
        }
    }

    suspend fun updateOccupation(occupation: String?) {
        context.dataStore.edit { prefs ->
            if (occupation.isNullOrBlank()) {
                prefs.remove(PreferenceKeys.occupation)
            } else {
                prefs[PreferenceKeys.occupation] = occupation.trim()
            }
        }
    }

    suspend fun updateMainAccountBalance(balance: Double) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.mainAccountBalance] = balance.coerceAtLeast(0.0)
        }
    }

    suspend fun updateSavingsAccountBalance(balance: Double) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.savingsAccountBalance] = balance.coerceAtLeast(0.0)
        }
    }

    suspend fun updateVaultsBalance(balance: Double) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.vaultsBalance] = balance.coerceAtLeast(0.0)
        }
    }

    suspend fun updateSubscriptionTotal(amount: Double) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.subscriptionTotal] = amount.coerceAtLeast(0.0)
        }
    }

    suspend fun updateHasDebts(hasDebts: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.hasDebts] = hasDebts
        }
    }

    suspend fun updateEmergencyFund(amount: Double) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.emergencyFund] = amount.coerceAtLeast(0.0)
        }
    }

    suspend fun updatePrimaryGoal(goal: String?) {
        context.dataStore.edit { prefs ->
            if (goal.isNullOrBlank()) {
                prefs.remove(PreferenceKeys.primaryGoal)
            } else {
                prefs[PreferenceKeys.primaryGoal] = goal.trim()
            }
        }
    }

    suspend fun updateDisplayName(name: String?) {
        context.dataStore.edit { prefs ->
            if (name.isNullOrBlank()) {
                prefs.remove(PreferenceKeys.displayName)
            } else {
                prefs[PreferenceKeys.displayName] = name.trim()
            }
        }
    }

    suspend fun updateBirthday(date: LocalDate?) {
        context.dataStore.edit { prefs ->
            if (date == null) {
                prefs.remove(PreferenceKeys.birthdayEpochDay)
            } else {
                prefs[PreferenceKeys.birthdayEpochDay] = date.toEpochDay()
                prefs[PreferenceKeys.age] = ChronoUnit.YEARS.between(date, LocalDate.now()).coerceAtLeast(0).toInt()
            }
        }
    }

    suspend fun refreshAgeFromBirthday() {
        context.dataStore.edit { prefs ->
            val epoch = prefs[PreferenceKeys.birthdayEpochDay] ?: return@edit
            val birthday = LocalDate.ofEpochDay(epoch)
            val computed = ChronoUnit.YEARS.between(birthday, LocalDate.now()).coerceAtLeast(0).toInt()
            val stored = prefs[PreferenceKeys.age]
            if (stored == null || stored != computed) {
                prefs[PreferenceKeys.age] = computed
            }
        }
    }
    
    suspend fun updateRegionalSettings(countryCode: String, languageCode: String, currencyCode: String, customTaxRate: Double?) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.countryCode] = countryCode
            prefs[PreferenceKeys.languageCode] = languageCode
            prefs[PreferenceKeys.currencyCode] = currencyCode
            // Persist a locale string so RegionalSettings.getLocale() can
            // construct the correct Locale and so region-specific resource
            // qualifiers (e.g. values-fr-rCA) are preferred.
            val defaultLocale = CountryProfiles.getByCode(countryCode)?.defaultLocale
                ?: "${languageCode}_$countryCode"
            prefs[PreferenceKeys.locale] = defaultLocale
            if (customTaxRate != null) {
                prefs[PreferenceKeys.customIncomeTaxRate] = customTaxRate
            } else {
                prefs.remove(PreferenceKeys.customIncomeTaxRate)
            }
        }
    // Apply the new locale immediately using language + country so the
    // system prefers region-specific qualifiers (e.g. values-fr-rCA) and
    // falls back to the base language if the regionized resources are
    // missing.
    context.setAppLocale(languageCode, countryCode)
    }

    suspend fun updateBrandfetchClientId(clientId: String?) {
        context.dataStore.edit { prefs ->
            if (clientId.isNullOrBlank()) {
                prefs.remove(PreferenceKeys.brandfetchClientId)
            } else {
                prefs[PreferenceKeys.brandfetchClientId] = clientId.trim()
            }
        }
    }

    suspend fun updateExpenseHistoryRetention(retention: ExpenseHistoryRetention) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.expenseHistoryRetention] = retention.name
        }
    }

    suspend fun updateCreditCardReminderSettings(
        enabled: Boolean,
        daysBefore: Int,
        hour: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.creditCardReminderEnabled] = enabled
            prefs[PreferenceKeys.creditCardReminderDaysBefore] = daysBefore.coerceIn(1, 14)
            prefs[PreferenceKeys.creditCardReminderHour] = hour.coerceIn(0, 23)
        }
    }

    suspend fun updatePromptPayOnCreditCardExpense(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.promptPayOnCreditCardExpense] = enabled
        }
    }

    suspend fun updateCreditCardUtilizationAlert(
        enabled: Boolean,
        threshold: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.creditCardUtilizationAlertEnabled] = enabled
            prefs[PreferenceKeys.creditCardUtilizationThreshold] = threshold.coerceIn(1, 100)
        }
    }

    suspend fun updateBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.biometricEnabled] = enabled
        }
    }

    suspend fun updateSmartTransferMinimumAmount(amount: Double) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.smartTransferMinimumAmount] = amount.coerceAtLeast(0.0)
        }
    }

    suspend fun updateAutoBackupSettings(enabled: Boolean, frequencyDays: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.autoBackupEnabled] = enabled
            prefs[PreferenceKeys.autoBackupFrequencyDays] = frequencyDays.coerceIn(1, 30)
        }
    }

    suspend fun updateLastAutoBackupTimestamp(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.lastAutoBackupTimestamp] = timestamp
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.onboardingCompleted] = completed
        }
    }

    suspend fun setJoinedDate(date: LocalDate) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.joinedDateEpochDay] = date.toEpochDay()
        }
    }

    suspend fun updateReminders(enabled: Boolean, hour: Int? = null, frequencyDays: Int? = null) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.remindersEnabled] = enabled
            hour?.let { prefs[PreferenceKeys.reminderHour] = it.coerceIn(0, 23) }
            frequencyDays?.let { prefs[PreferenceKeys.reminderFrequencyDays] = it.coerceAtLeast(1) }
        }
    }

    suspend fun updatePaydayReminder(
        enabled: Boolean,
        hour: Int,
        minute: Int,
        suggestAverage: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.paydayReminderEnabled] = enabled
            prefs[PreferenceKeys.paydayReminderHour] = hour.coerceIn(0, 23)
            prefs[PreferenceKeys.paydayReminderMinute] = minute.coerceIn(0, 59)
            prefs[PreferenceKeys.paydaySuggestAverage] = suggestAverage
        }
    }

    suspend fun recordPaycheckHistory(amount: Double) {
        if (amount <= 0.0) return
        context.dataStore.edit { prefs ->
            val accumulated = prefs[PreferenceKeys.payHistoryTotal] ?: 0.0
            val count = prefs[PreferenceKeys.payHistoryCount] ?: 0
            prefs[PreferenceKeys.payHistoryTotal] = accumulated + amount
            prefs[PreferenceKeys.payHistoryCount] = count + 1
        }
    }

    suspend fun getPayHistoryStats(): PayHistoryStats {
        val prefs = context.dataStore.data.first()
        val count = prefs[PreferenceKeys.payHistoryCount] ?: 0
        val total = prefs[PreferenceKeys.payHistoryTotal] ?: 0.0
        return PayHistoryStats(count, total)
    }

    private fun Preferences.toSettings(): SparelySettings {
        val defaults = SparelySettings()
        val scheduleDefaults = defaults.paySchedule

        val emergency = this[PreferenceKeys.emergencyPercent] ?: defaults.defaultPercentages.emergency
        val invest = this[PreferenceKeys.investPercent] ?: defaults.defaultPercentages.invest
        val funPercent = this[PreferenceKeys.funPercent] ?: defaults.defaultPercentages.`fun`
        val safeSplit = this[PreferenceKeys.safeSplit] ?: defaults.defaultPercentages.safeInvestmentSplit
        val riskLevel = this[PreferenceKeys.riskLevel]?.let { runCatching { RiskLevel.valueOf(it) }.getOrNull() }
            ?: defaults.riskLevel
        val autoRecommend = this[PreferenceKeys.autoRecommend] ?: defaults.autoRecommendationsEnabled
        val includeTax = this[PreferenceKeys.includeTax] ?: defaults.includeTaxByDefault
        val income = this[PreferenceKeys.monthlyIncome] ?: defaults.monthlyIncome
        val remindersEnabled = this[PreferenceKeys.remindersEnabled] ?: defaults.remindersEnabled
        val reminderHour = this[PreferenceKeys.reminderHour] ?: defaults.reminderHour
        val reminderFrequency = this[PreferenceKeys.reminderFrequencyDays] ?: defaults.reminderFrequencyDays
    val paydayReminderEnabled = this[PreferenceKeys.paydayReminderEnabled] ?: defaults.paydayReminderEnabled
    val paydayReminderHour = this[PreferenceKeys.paydayReminderHour] ?: defaults.paydayReminderHour
    val paydayReminderMinute = this[PreferenceKeys.paydayReminderMinute] ?: defaults.paydayReminderMinute
    val paydaySuggestAverage = this[PreferenceKeys.paydaySuggestAverage] ?: defaults.paydaySuggestAverageIncome
        val age = this[PreferenceKeys.age] ?: defaults.age
        val joinedDate = this[PreferenceKeys.joinedDateEpochDay]?.let { LocalDate.ofEpochDay(it) }
        val education = this[PreferenceKeys.educationStatus]?.let { runCatching { EducationStatus.valueOf(it) }.getOrNull() }
            ?: defaults.educationStatus
        val employment = this[PreferenceKeys.employmentStatus]?.let { runCatching { EmploymentStatus.valueOf(it) }.getOrNull() } ?: defaults.employmentStatus
        val living = this[PreferenceKeys.livingSituation]?.let { runCatching { LivingSituation.valueOf(it) }.getOrNull() } ?: defaults.livingSituation
        val occupation = this[PreferenceKeys.occupation]
        val mainAccountBalance = this[PreferenceKeys.mainAccountBalance] ?: 0.0
        val savingsAccountBalance = this[PreferenceKeys.savingsAccountBalance] ?: 0.0
        val vaultsBalance = this[PreferenceKeys.vaultsBalance] ?: 0.0
        val subscriptionTotal = this[PreferenceKeys.subscriptionTotal] ?: 0.0
        val hasDebts = this[PreferenceKeys.hasDebts] ?: defaults.hasDebts
        val emergencyFund = this[PreferenceKeys.emergencyFund] ?: defaults.currentEmergencyFund
        val primaryGoal = this[PreferenceKeys.primaryGoal]
        val displayName = this[PreferenceKeys.displayName]
        val birthday = this[PreferenceKeys.birthdayEpochDay]?.let { LocalDate.ofEpochDay(it) }

        val incomeTrackingMode = this[PreferenceKeys.incomeTrackingMode]?.let { runCatching { IncomeTrackingMode.valueOf(it) }.getOrNull() } ?: scheduleDefaults.trackingMode
        val payInterval = this[PreferenceKeys.payInterval]?.let { runCatching { PayInterval.valueOf(it) }.getOrNull() } ?: scheduleDefaults.interval
        val payDefaultNet = this[PreferenceKeys.payDefaultNet] ?: scheduleDefaults.defaultNetPay
        val payDefaultSaveRate = this[PreferenceKeys.payDefaultSaveRate] ?: scheduleDefaults.defaultSaveRate
        val payWeeklyDay = this[PreferenceKeys.payWeeklyDay]?.let { DayOfWeek.of(it) } ?: scheduleDefaults.weeklyDayOfWeek
        val paySemiDay1 = this[PreferenceKeys.paySemiDay1] ?: scheduleDefaults.semiMonthlyDay1
        val paySemiDay2 = this[PreferenceKeys.paySemiDay2] ?: scheduleDefaults.semiMonthlyDay2
        val payMonthlyDay = this[PreferenceKeys.payMonthlyDay] ?: scheduleDefaults.monthlyDay
        val payCustomDays = this[PreferenceKeys.payCustomDays]
        val payNextDate = this[PreferenceKeys.payNextDate]?.let { LocalDate.ofEpochDay(it) }
        val payLastDate = this[PreferenceKeys.payLastDate]?.let { LocalDate.ofEpochDay(it) }
        val payLastAmount = this[PreferenceKeys.payLastAmount] ?: scheduleDefaults.lastPayAmount
        val payAutoDistribute = this[PreferenceKeys.payAutoDistribute] ?: scheduleDefaults.autoDistributeToVaults
        val payAutoPending = this[PreferenceKeys.payAutoPending] ?: scheduleDefaults.autoCreatePendingTransfers
        val payDynamicSaveEnabled = this[PreferenceKeys.payDynamicSaveEnabled] ?: scheduleDefaults.dynamicSaveRateEnabled
        val payLastComputedSaveRate = this[PreferenceKeys.payLastComputedSaveRate]

        val smartAllocationMode = this[PreferenceKeys.smartAllocationMode]?.let { runCatching { SmartAllocationMode.valueOf(it) }.getOrNull() } ?: defaults.smartAllocationMode
        val targetSavingsRate = this[PreferenceKeys.targetSavingsRate] ?: defaults.targetSavingsRate
        val savingTaxRate = this[PreferenceKeys.savingTaxRate] ?: defaults.savingTaxRate
        val vaultAllocationMode = this[PreferenceKeys.vaultAllocationMode]?.let { runCatching { VaultAllocationMode.valueOf(it) }.getOrNull() } ?: defaults.vaultAllocationMode
        val dynamicSavingTaxEnabled = this[PreferenceKeys.dynamicSavingTaxEnabled] ?: defaults.dynamicSavingTaxEnabled
        val lastComputedSavingTaxRate = this[PreferenceKeys.lastComputedSavingTaxRate]
        
        val countryCode = this[PreferenceKeys.countryCode] ?: defaults.regionalSettings.countryCode
        val languageCode = this[PreferenceKeys.languageCode] ?: defaults.regionalSettings.languageCode
        val currencyCode = this[PreferenceKeys.currencyCode] ?: defaults.regionalSettings.currencyCode
        val customIncomeTaxRate = this[PreferenceKeys.customIncomeTaxRate]
        val brandfetchClientId = this[PreferenceKeys.brandfetchClientId]
        val expenseHistoryRetention = this[PreferenceKeys.expenseHistoryRetention]?.let { runCatching { ExpenseHistoryRetention.valueOf(it) }.getOrNull() } ?: ExpenseHistoryRetention.INDEFINITELY
        val creditCardReminderEnabled = this[PreferenceKeys.creditCardReminderEnabled] ?: defaults.creditCardReminderEnabled
        val creditCardReminderDaysBefore = this[PreferenceKeys.creditCardReminderDaysBefore] ?: defaults.creditCardReminderDaysBefore
        val creditCardReminderHour = this[PreferenceKeys.creditCardReminderHour] ?: defaults.creditCardReminderHour
        val promptPayOnCreditCardExpense = this[PreferenceKeys.promptPayOnCreditCardExpense] ?: defaults.promptPayOnCreditCardExpense
        val creditCardUtilizationAlertEnabled = this[PreferenceKeys.creditCardUtilizationAlertEnabled] ?: defaults.creditCardUtilizationAlertEnabled
        val creditCardUtilizationThreshold = this[PreferenceKeys.creditCardUtilizationThreshold] ?: defaults.creditCardUtilizationThreshold
        val biometricEnabled = this[PreferenceKeys.biometricEnabled] ?: defaults.biometricEnabled

        return SparelySettings(
            defaultPercentages = SavingsPercentages(
                emergency = emergency,
                invest = invest,
                `fun` = funPercent,
                safeInvestmentSplit = safeSplit
            ),
            riskLevel = riskLevel,
            autoRecommendationsEnabled = autoRecommend,
            includeTaxByDefault = includeTax,
            monthlyIncome = income,
            remindersEnabled = remindersEnabled,
            reminderHour = reminderHour,
            reminderFrequencyDays = reminderFrequency,
            paydayReminderEnabled = paydayReminderEnabled,
            paydayReminderHour = paydayReminderHour,
            paydayReminderMinute = paydayReminderMinute,
            paydaySuggestAverageIncome = paydaySuggestAverage,
            age = age,
            joinedDate = joinedDate,
            educationStatus = education,
            employmentStatus = employment,
            livingSituation = living,
            occupation = occupation,
            mainAccountBalance = mainAccountBalance,
            savingsAccountBalance = savingsAccountBalance,
            vaultsBalance = vaultsBalance,
            subscriptionTotal = subscriptionTotal,
            hasDebts = hasDebts,
            currentEmergencyFund = emergencyFund,
            primaryGoal = primaryGoal,
            displayName = displayName,
            birthday = birthday,
            paySchedule = PayScheduleSettings(
                trackingMode = incomeTrackingMode,
                interval = payInterval,
                defaultNetPay = payDefaultNet,
                defaultSaveRate = payDefaultSaveRate,
                weeklyDayOfWeek = payWeeklyDay,
                semiMonthlyDay1 = paySemiDay1,
                semiMonthlyDay2 = paySemiDay2,
                monthlyDay = payMonthlyDay,
                customDaysBetween = payCustomDays,
                nextPayDate = payNextDate,
                lastPayDate = payLastDate,
                lastPayAmount = payLastAmount,
                autoDistributeToVaults = payAutoDistribute,
                autoCreatePendingTransfers = payAutoPending,
                dynamicSaveRateEnabled = payDynamicSaveEnabled,
                lastComputedSaveRate = payLastComputedSaveRate
            ),
            smartAllocationMode = smartAllocationMode,
            targetSavingsRate = targetSavingsRate,
            savingTaxRate = savingTaxRate,
            vaultAllocationMode = vaultAllocationMode,
            dynamicSavingTaxEnabled = dynamicSavingTaxEnabled,
            lastComputedSavingTaxRate = lastComputedSavingTaxRate,
            regionalSettings = RegionalSettings(
                countryCode = countryCode,
                languageCode = languageCode,
                currencyCode = currencyCode,
                // read persisted locale or fallback to defaults
                locale = this[PreferenceKeys.locale] ?: defaults.regionalSettings.locale,
                customIncomeTaxRate = customIncomeTaxRate
            ),
            brandfetchClientId = brandfetchClientId,
            expenseHistoryRetention = expenseHistoryRetention,
            creditCardReminderEnabled = creditCardReminderEnabled,
            creditCardReminderDaysBefore = creditCardReminderDaysBefore,
            creditCardReminderHour = creditCardReminderHour,
            promptPayOnCreditCardExpense = promptPayOnCreditCardExpense,
            creditCardUtilizationAlertEnabled = creditCardUtilizationAlertEnabled,
            creditCardUtilizationThreshold = creditCardUtilizationThreshold,
            biometricEnabled = biometricEnabled,
            smartTransferMinimumAmount = this[PreferenceKeys.smartTransferMinimumAmount] ?: 0.0,
            autoBackupEnabled = this[PreferenceKeys.autoBackupEnabled] ?: false,
            autoBackupFrequencyDays = this[PreferenceKeys.autoBackupFrequencyDays] ?: 7,
            lastAutoBackupTimestamp = this[PreferenceKeys.lastAutoBackupTimestamp],
            mainOverflowAccountId = this[PreferenceKeys.mainOverflowAccountId],
            minMainAccountBalance = this[PreferenceKeys.minMainAccountBalance] ?: 0.0,
            autoCreateAssetThreshold = this[PreferenceKeys.autoCreateAssetThreshold] ?: 0.0
        )
    }
    private object PreferenceKeys {
        val emergencyPercent = doublePreferencesKey("percent_emergency")
        val investPercent = doublePreferencesKey("percent_invest")
        val funPercent = doublePreferencesKey("percent_fun")
        val safeSplit = doublePreferencesKey("percent_safe_split")
        val riskLevel = stringPreferencesKey("risk_level")
        val autoRecommend = booleanPreferencesKey("auto_recommend")
        val includeTax = booleanPreferencesKey("include_tax_default")
        val monthlyIncome = doublePreferencesKey("monthly_income")
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderFrequencyDays = intPreferencesKey("reminder_frequency_days")
        val paydayReminderEnabled = booleanPreferencesKey("payday_reminder_enabled")
        val paydayReminderHour = intPreferencesKey("payday_reminder_hour")
        val paydayReminderMinute = intPreferencesKey("payday_reminder_minute")
        val paydaySuggestAverage = booleanPreferencesKey("payday_suggest_average")
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val age = intPreferencesKey("profile_age")
        val joinedDateEpochDay = longPreferencesKey("profile_joined_date")
        val educationStatus = stringPreferencesKey("profile_education")
        val employmentStatus = stringPreferencesKey("profile_employment")
        val livingSituation = stringPreferencesKey("profile_living_situation")
        val occupation = stringPreferencesKey("profile_occupation")
        val mainAccountBalance = doublePreferencesKey("profile_main_account_balance")
        val savingsAccountBalance = doublePreferencesKey("profile_savings_account_balance")
        val vaultsBalance = doublePreferencesKey("profile_vaults_balance")
        val subscriptionTotal = doublePreferencesKey("profile_subscription_total")
        val hasDebts = booleanPreferencesKey("profile_has_debts")
        val emergencyFund = doublePreferencesKey("profile_emergency_fund")
        val primaryGoal = stringPreferencesKey("profile_primary_goal")
        val displayName = stringPreferencesKey("profile_display_name")
        val birthdayEpochDay = longPreferencesKey("profile_birthday")

        // New Pay Schedule keys
        val incomeTrackingMode = stringPreferencesKey("pay_tracking_mode")
        val payInterval = stringPreferencesKey("pay_interval")
        val payDefaultNet = doublePreferencesKey("pay_default_net")
        val payDefaultSaveRate = doublePreferencesKey("pay_default_save_rate")
        val payWeeklyDay = intPreferencesKey("pay_weekly_day")
        val paySemiDay1 = intPreferencesKey("pay_semi_day1")
        val paySemiDay2 = intPreferencesKey("pay_semi_day2")
        val payMonthlyDay = intPreferencesKey("pay_monthly_day")
        val payCustomDays = intPreferencesKey("pay_custom_days")
        val payNextDate = longPreferencesKey("pay_next_date")
        val payLastDate = longPreferencesKey("pay_last_date")
        val payLastAmount = doublePreferencesKey("pay_last_amount")
        val payAutoDistribute = booleanPreferencesKey("pay_auto_distribute")
        val payAutoPending = booleanPreferencesKey("pay_auto_pending")
        val payDynamicSaveEnabled = booleanPreferencesKey("pay_dynamic_save_enabled")
        val payLastComputedSaveRate = doublePreferencesKey("pay_last_computed_save_rate")
        val payHistoryTotal = doublePreferencesKey("pay_history_total")
        val payHistoryCount = intPreferencesKey("pay_history_count")

        // Smart allocation
        val smartAllocationMode = stringPreferencesKey("smart_allocation_mode")
        val targetSavingsRate = doublePreferencesKey("target_savings_rate")
        val savingTaxRate = doublePreferencesKey("saving_tax_rate")
        val vaultAllocationMode = stringPreferencesKey("vault_allocation_mode")
        val dynamicSavingTaxEnabled = booleanPreferencesKey("dynamic_saving_tax_enabled")
        val lastComputedSavingTaxRate = doublePreferencesKey("last_computed_saving_tax_rate")

        // Auto-deposits
        val autoDepositsEnabled = booleanPreferencesKey("auto_deposits_enabled")
        val autoDepositCheckHour = intPreferencesKey("auto_deposit_check_hour")
        
        // Regional settings
        val countryCode = stringPreferencesKey("regional_country")
        val languageCode = stringPreferencesKey("regional_language")
        val currencyCode = stringPreferencesKey("regional_currency")
    val locale = stringPreferencesKey("regional_locale")
        val customIncomeTaxRate = doublePreferencesKey("regional_custom_tax_rate")
        
        // Store logos
        val brandfetchClientId = stringPreferencesKey("brandfetch_client_id")
        val expenseHistoryRetention = stringPreferencesKey("expense_history_retention")
        
        val creditCardReminderEnabled = booleanPreferencesKey("credit_card_reminder_enabled")
        val creditCardReminderDaysBefore = intPreferencesKey("credit_card_reminder_days_before")
        val creditCardReminderHour = intPreferencesKey("credit_card_reminder_hour")
        val promptPayOnCreditCardExpense = booleanPreferencesKey("prompt_pay_on_credit_card_expense")
        val creditCardUtilizationAlertEnabled = booleanPreferencesKey("credit_card_utilization_alert_enabled")
        val creditCardUtilizationThreshold = intPreferencesKey("credit_card_utilization_threshold")
        val biometricEnabled = booleanPreferencesKey("biometric_enabled")
        
        // Smart transfer notification threshold
        val smartTransferMinimumAmount = doublePreferencesKey("smart_transfer_minimum_amount")
        
        // Auto backup settings
        val autoBackupEnabled = booleanPreferencesKey("auto_backup_enabled")
        val autoBackupFrequencyDays = intPreferencesKey("auto_backup_frequency_days")
        val lastAutoBackupTimestamp = longPreferencesKey("last_auto_backup_timestamp")
        
        // Overflow Account (HISA/High-Yield Savings)
        val mainOverflowAccountId = longPreferencesKey("main_overflow_account_id")
        val minMainAccountBalance = doublePreferencesKey("min_main_account_balance")

        // Asset creation
        val autoCreateAssetThreshold = doublePreferencesKey("auto_create_asset_threshold")
    }
}

data class PayHistoryStats(
    val count: Int,
    val total: Double
) {
    val average: Double = if (count > 0) total / count else 0.0
}
