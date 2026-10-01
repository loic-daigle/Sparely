package com.example.sparely.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.sparely.AppContainer
import com.example.sparely.data.local.MainAccountTransactionType
import com.example.sparely.data.preferences.UserPreferencesRepository
import com.example.sparely.data.repository.BackupRepository
import com.example.sparely.data.repository.SavingsRepository
import com.example.sparely.domain.model.*
import com.example.sparely.notifications.NotificationScheduler
import com.example.sparely.workers.VaultAutoDepositScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs

data class SettingsUiState(
    val settings: SparelySettings = SparelySettings(),
    val autoDepositCheckHour: Int = 9,
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val smartVaults: List<SmartVault> = emptyList(),
    val savingsAccounts: List<SavingsAccount> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val backupRepository: BackupRepository,
    private val savingsRepository: SavingsRepository,
    private val notificationScheduler: NotificationScheduler,
    private val vaultAutoDepositScheduler: VaultAutoDepositScheduler,
    // We need container access for things like monthlyAllocationScheduler 
    private val container: AppContainer,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(isLoading = true))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Last line of defence for every coroutine this ViewModel starts: an exception that escapes a
    // viewModelScope coroutine would otherwise crash the whole app. Surface it instead.
    private val errorHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("SettingsViewModel", "Unhandled coroutine error", throwable)
        _uiState.update { it.copy(isLoading = false, errorMessage = throwable.message ?: "Something went wrong") }
    }

    init {
        viewModelScope.launch(dispatcher + errorHandler) {
            combine(
                preferencesRepository.settingsFlow,
                preferencesRepository.autoDepositCheckHourFlow
            ) { settings, autoDepositHour ->
                settings to autoDepositHour
            }
                .catch { throwable ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = throwable.message) }
                }
                .collect { (settings, autoDepositHour) ->
                    _uiState.update { 
                        it.copy(
                            settings = settings, 
                            autoDepositCheckHour = autoDepositHour, 
                            isLoading = false
                        ) 
                    }
                }
        }
        
        viewModelScope.launch(dispatcher + errorHandler) {
            savingsRepository.observePaymentMethods()
                .collect { methods ->
                    _uiState.update { it.copy(paymentMethods = methods) }
                }
        }

        viewModelScope.launch(dispatcher + errorHandler) {
            savingsRepository.observeSmartVaults()
                .catch { e -> _uiState.update { it.copy(errorMessage = "Failed to load vaults: ${e.message}") } }
                .collect { vaults ->
                    _uiState.update { it.copy(smartVaults = vaults) }
                }
        }
        
        viewModelScope.launch(dispatcher + errorHandler) {
            savingsRepository.observeSavingsAccounts()
                .catch { e -> _uiState.update { it.copy(errorMessage = "Failed to load savings accounts: ${e.message}") } }
                .collect { accounts ->
                    _uiState.update { it.copy(savingsAccounts = accounts) }
                }
        }
    }

    // --- Settings Updates ---

    fun updatePercentages(percentages: SavingsPercentages) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updatePercentages(percentages)
        }
    }

    fun toggleAutoMode(enabled: Boolean) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.toggleAutoRecommendations(enabled)
        }
    }

    fun updateRiskLevel(riskLevel: RiskLevel) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateRiskLevel(riskLevel)
        }
    }

    fun updateIncludeTax(defaultIncludeTax: Boolean) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateIncludeTax(defaultIncludeTax)
        }
    }

    fun updateMonthlyIncome(income: Double) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateMonthlyIncome(income)
        }
    }

    fun updateMainAccountBalance(balance: Double) {
        viewModelScope.launch(dispatcher + errorHandler) {
            savingsRepository.withMainAccountLock {
                val currentBalance = savingsRepository.getLatestMainAccountBalance()
                val delta = balance - currentBalance
                if (abs(delta) > 1e-6) {
                    val transaction = MainAccountTransaction(
                        type = MainAccountTransactionType.ADJUSTMENT,
                        amount = abs(delta),
                        balanceAfter = balance,
                        timestamp = java.time.LocalDateTime.now(),
                        description = "Manual balance update from settings"
                    )
                    savingsRepository.insertMainAccountTransaction(transaction)
                }
                preferencesRepository.updateMainAccountBalance(balance)
            }
        }
    }
    
    fun updateTargetSavingsRate(rate: Double) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateTargetSavingsRate(rate)
        }
    }

    fun updateSmartAllocationMode(mode: SmartAllocationMode) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateSmartAllocationMode(mode)
            val enabled = mode == SmartAllocationMode.AUTOMATIC
            container.monthlyAllocationScheduler.schedule(enabled)
        }
    }

    fun updateMainOverflowAccountId(accountId: Long?) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateMainOverflowAccountId(accountId)
        }
    }
    
    fun updateMinMainAccountBalance(amount: Double) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateMinMainAccountBalance(amount)
        }
    }

    /**
     * Trigger a one-off monthly allocation run immediately (useful for manual testing or "Run now" UI).
     */
    fun triggerRunMonthlyAllocation() {
        viewModelScope.launch(dispatcher + errorHandler) {
            container.monthlyAllocationScheduler.runImmediate()
        }
    }

    fun updateVaultAllocationMode(mode: VaultAllocationMode) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateVaultAllocationMode(mode)
        }
    }

    fun updatePaydayReminderSettings(
        enabled: Boolean,
        hour: Int,
        minute: Int,
        suggestAverage: Boolean
    ) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updatePaydayReminder(enabled, hour, minute, suggestAverage)
            val refreshedSettings = preferencesRepository.getSettingsSnapshot()
            notificationScheduler.schedulePaydayReminder(refreshedSettings)
        }
    }

    fun updateSavingTaxRate(rate: Double) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateSavingTaxRate(rate)
        }
    }

    fun updateDynamicSavingTaxEnabled(enabled: Boolean) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateDynamicSavingTaxEnabled(enabled)
        }
    }

    fun updateAutoDepositsEnabled(enabled: Boolean) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateAutoDepositsEnabled(enabled)
            val checkHour = preferencesRepository.getAutoDepositCheckHour()
            vaultAutoDepositScheduler.schedule(enabled, checkHour)
        }
    }
    
    fun updateAutoDepositCheckHour(hour: Int) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateAutoDepositCheckHour(hour)
            val enabled = preferencesRepository.getAutoDepositsEnabled()
            if (enabled) {
                vaultAutoDepositScheduler.schedule(true, hour)
            }
        }
    }
    
    fun triggerManualAutoDepositCheck() {
        vaultAutoDepositScheduler.runImmediateCheck()
    }

    fun updateCreditCardReminderSettings(enabled: Boolean, daysBefore: Int, hour: Int) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateCreditCardReminderSettings(enabled, daysBefore, hour)
            val settings = preferencesRepository.getSettingsSnapshot()
            notificationScheduler.scheduleCreditCardReminders(settings)
        }
    }

    fun updatePromptPayOnCreditCardExpense(enabled: Boolean) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updatePromptPayOnCreditCardExpense(enabled)
        }
    }

    fun updateCreditCardUtilizationAlert(enabled: Boolean, threshold: Int) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateCreditCardUtilizationAlert(enabled, threshold)
        }
    }

    fun updateBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateBiometricEnabled(enabled)
        }
    }

    fun updateAiAssistantAccessEnabled(enabled: Boolean) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateAiAssistantAccessEnabled(enabled)
        }
    }

    fun updateSmartTransferMinimumAmount(amount: Double) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateSmartTransferMinimumAmount(amount)
        }
    }

    fun updateAutoBackupSettings(enabled: Boolean, frequencyDays: Int) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateAutoBackupSettings(enabled, frequencyDays)
            // Schedule or cancel auto backup based on enabled state
            container.autoBackupScheduler.schedule(enabled, frequencyDays)
        }
    }

    fun triggerManualBackup(context: android.content.Context, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(dispatcher + errorHandler) {
            try {
                container.autoBackupScheduler.runImmediateBackup(context, onResult)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Backup failed: ${e.message}")
                }
            }
        }
    }

    fun updatePaySchedule(schedule: PayScheduleSettings) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updatePaySchedule(schedule)
            val refreshedSettings = preferencesRepository.getSettingsSnapshot()
            notificationScheduler.schedulePaydayReminder(refreshedSettings)
        }
    }

    // --- Profile Updates ---

    fun updateAge(age: Int) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateAge(age)
        }
    }

    fun updateEducationStatus(status: EducationStatus) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateEducationStatus(status)
        }
    }

    fun updateEmploymentStatus(status: EmploymentStatus) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateEmploymentStatus(status)
        }
    }

    fun updateLivingSituation(situation: LivingSituation) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateLivingSituation(situation)
        }
    }

    fun updateOccupation(occupation: String?) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateOccupation(occupation)
        }
    }

    fun updateHasDebts(hasDebts: Boolean) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateHasDebts(hasDebts)
        }
    }

    fun updateEmergencyFund(amount: Double) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateEmergencyFund(amount)
        }
    }

    fun updatePrimaryGoal(goal: String?) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updatePrimaryGoal(goal)
        }
    }

    fun updateDisplayName(name: String?) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateDisplayName(name)
        }
    }
    
    fun updateRegionalSettings(countryCode: String, languageCode: String, currencyCode: String, customTaxRate: Double?) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateRegionalSettings(countryCode, languageCode, currencyCode, customTaxRate)
        }
    }

    fun updateBrandfetchClientId(clientId: String?) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateBrandfetchClientId(clientId)
        }
    }

    fun updateBirthday(date: LocalDate?) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateBirthday(date)
            preferencesRepository.refreshAgeFromBirthday()
        }
    }

    fun updateReminderSettings(enabled: Boolean, hour: Int, frequencyDays: Int) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateReminders(enabled, hour, frequencyDays)
            if (enabled) {
                // We use the current settings state as a base for scheduling
                val current = _uiState.value.settings
                notificationScheduler.schedule(current.copy(remindersEnabled = true, reminderHour = hour, reminderFrequencyDays = frequencyDays))
            } else {
                notificationScheduler.cancel()
            }
        }
    }

    // --- Payment Methods ---

    fun addPaymentMethod(method: PaymentMethod) {
        viewModelScope.launch(dispatcher + errorHandler) {
            savingsRepository.insertPaymentMethod(method)
        }
    }

    fun addPaymentMethod(name: String, type: PaymentMethodType, defaultDeduct: Boolean, iconName: String?) {
        viewModelScope.launch(dispatcher + errorHandler) {
            val method = PaymentMethod(
                name = name,
                type = type,
                defaultDeductFromMainAccount = defaultDeduct,
                iconName = iconName
            )
            savingsRepository.insertPaymentMethod(method)
        }
    }

    fun updatePaymentMethod(method: PaymentMethod) {
        viewModelScope.launch(dispatcher + errorHandler) {
            savingsRepository.updatePaymentMethod(method)
        }
    }

    fun deletePaymentMethod(method: PaymentMethod) {
        viewModelScope.launch(dispatcher + errorHandler) {
            savingsRepository.deletePaymentMethod(method)
        }
    }

    // --- Data Management ---

    fun resetHistory(clearVaults: Boolean = false) {
        viewModelScope.launch(dispatcher + errorHandler) {
            savingsRepository.clearExpenses()
            savingsRepository.clearTransfers()
            if (clearVaults) {
                savingsRepository.clearSmartVaults()
            }
        }
    }

    fun updateExpenseHistoryRetention(retention: ExpenseHistoryRetention) {
        viewModelScope.launch(dispatcher + errorHandler) {
            preferencesRepository.updateExpenseHistoryRetention(retention)
            pruneHistory(retention)
        }
    }

    private suspend fun pruneHistory(retention: ExpenseHistoryRetention) {
        if (retention == ExpenseHistoryRetention.INDEFINITELY) return
        val months = retention.months ?: return
        val cutoff = LocalDate.now().minusMonths(months.toLong())
        savingsRepository.deleteExpensesBefore(cutoff)
    }

    fun exportData(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch(dispatcher + errorHandler) {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                val json = backupRepository.exportData()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = "Backup exported successfully") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Export failed: ${e.message}") }
            }
        }
    }

    fun exportExpensesToCsv(uri: android.net.Uri, context: android.content.Context, expenses: List<Expense>, stores: List<Store>) {
        viewModelScope.launch(dispatcher + errorHandler) {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                val success = com.example.sparely.ui.utils.CsvExporter.exportExpenses(context, uri, expenses, stores)
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        errorMessage = if (success) "CSV exported successfully" else "CSV export failed"
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Export failed: ${e.message}") }
            }
        }
    }

    fun importData(uri: android.net.Uri, context: android.content.Context, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch(dispatcher + errorHandler) {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                }
                if (json != null) {
                    backupRepository.restoreData(json)
                    withContext(Dispatchers.Main) {
                        onSuccess?.invoke()
                    }
                    _uiState.update { it.copy(errorMessage = "Restore successful", isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to read file") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Import failed: ${e.message}") }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}


class SettingsViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(
                preferencesRepository = container.preferencesRepository,
                backupRepository = container.backupRepository,
                savingsRepository = container.savingsRepository,
                notificationScheduler = container.notificationScheduler,
                vaultAutoDepositScheduler = container.vaultAutoDepositScheduler,
                container = container
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class $modelClass")
    }
}
