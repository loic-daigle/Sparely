package com.example.sparely.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sparely.data.repository.SavingsRepository
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.SmartVaultSetup
import com.example.sparely.domain.model.VaultBalanceAdjustment
import com.example.sparely.domain.model.VaultContribution
import com.example.sparely.domain.model.toSmartVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.example.sparely.domain.model.VaultHistoryItem
import com.example.sparely.domain.model.HistoryContribution
import com.example.sparely.domain.model.HistoryAdjustment

data class VaultUiState(
    val pendingVaultContributions: List<VaultContribution> = emptyList(),
    val vaultHistory: Map<Long, List<VaultHistoryItem>> = emptyMap()
)

class VaultViewModel(
    private val savingsRepository: SavingsRepository,
    private val notificationScheduler: com.example.sparely.notifications.NotificationScheduler,
    private val preferencesRepository: com.example.sparely.data.preferences.UserPreferencesRepository
) : ViewModel() {

    val smartVaults: StateFlow<List<SmartVault>> = savingsRepository.observeSmartVaults()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Reactive pending contributions - updates automatically when data changes
    private val _pendingContributions: StateFlow<List<VaultContribution>> = 
        savingsRepository.observePendingVaultContributions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _vaultHistory = MutableStateFlow<Map<Long, List<VaultHistoryItem>>>(emptyMap())

    val uiState: StateFlow<VaultUiState> = kotlinx.coroutines.flow.combine(
        _pendingContributions,
        _vaultHistory
    ) { pending, history ->
        VaultUiState(
            pendingVaultContributions = pending,
            vaultHistory = history
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VaultUiState())

    init {
        // No longer need manual refresh - data is reactive
    }


    fun addSmartVault(vault: SmartVault) {
        viewModelScope.launch {
            savingsRepository.upsertSmartVault(vault.copy(id = 0L))
        }
    }

    fun addSmartVault(setup: SmartVaultSetup) {
        viewModelScope.launch {
            val vault = setup.toSmartVault()
            savingsRepository.upsertSmartVault(vault.copy(id = 0L))
        }
    }

    fun updateSmartVault(vault: SmartVault) {
        viewModelScope.launch {
            savingsRepository.upsertSmartVault(vault)
        }
    }

    fun toggleVaultArchived(vaultId: Long, archived: Boolean) {
        if (vaultId == 0L) return
        viewModelScope.launch {
            savingsRepository.updateVaultArchived(vaultId, archived)
        }
    }

    fun deleteSmartVault(vaultId: Long) {
        if (vaultId == 0L) return
        viewModelScope.launch {
            savingsRepository.deleteSmartVault(vaultId)
        }
    }

    fun depositToVault(vaultId: Long, amount: Double, reason: String?, adjustMainAccount: Boolean) {
        if (vaultId == 0L || amount <= 0.0) return
        viewModelScope.launch {
            savingsRepository.depositToVault(vaultId, amount, reason, adjustMainAccount)
            refreshVaultHistory(vaultId)
        }
    }

    fun deductFromVault(vaultId: Long, amount: Double, reason: String?, creditMainAccount: Boolean) {
        if (vaultId == 0L || amount <= 0.0) return
        viewModelScope.launch {
            savingsRepository.deductFromVault(vaultId, amount, reason, creditMainAccount)
            refreshVaultHistory(vaultId)
        }
    }

    fun overrideVaultBalance(vaultId: Long, balance: Double, reason: String?) {
        if (vaultId == 0L || balance < 0.0) return
        viewModelScope.launch {
            savingsRepository.overrideVaultBalance(vaultId, balance, reason)
            refreshVaultHistory(vaultId)
        }
    }

    fun loadVaultHistory(vaultId: Long) {
        if (vaultId == 0L) return
        viewModelScope.launch {
            refreshVaultHistory(vaultId)
        }
    }

    private suspend fun refreshVaultHistory(vaultId: Long) {
        val contributions = savingsRepository.getVaultContributions(vaultId).map { HistoryContribution(it) }
        val adjustments = savingsRepository.getVaultAdjustments(vaultId).map { HistoryAdjustment(it) }
        val history = (contributions + adjustments).sortedByDescending { it.date }
        
        _vaultHistory.update { current ->
            current + (vaultId to history)
        }
    }

    // This is kept for backwards compatibility but is no-op since data is now reactive
    fun refreshPendingContributions() {
        // Data is now reactive via Flow - no manual refresh needed
    }

    fun reconcileVaultContribution(contributionId: Long) {
        viewModelScope.launch {
            savingsRepository.reconcileVaultContribution(contributionId)
            // No need to refresh - data is reactive
        }
    }

    fun reconcileVaultContributions(contributionIds: List<Long>) {
        if (contributionIds.isEmpty()) return
        viewModelScope.launch {
            savingsRepository.reconcileVaultContributions(contributionIds)
            // No need to refresh - data is reactive
        }
    }

    /** Approve a pending contribution: reconcile and remove frozen funds. */
    fun approvePendingVaultContribution(contributionId: Long) {
        viewModelScope.launch {
            savingsRepository.approvePendingContribution(contributionId)
            // No need to refresh - data is reactive
        }
    }

    /** Approve multiple pending contributions (reconcile + unfreeze) */
    fun approvePendingVaultContributions(contributionIds: List<Long>) {
        if (contributionIds.isEmpty()) return
        viewModelScope.launch {
            contributionIds.forEach { id ->
                savingsRepository.approvePendingContribution(id)
            }
            // No need to refresh - data is reactive
        }
    }

    /** Cancel a pending contribution: delete pending and remove frozen funds. */
    fun cancelPendingVaultContribution(contributionId: Long) {
        viewModelScope.launch {
            savingsRepository.cancelPendingContribution(contributionId)
            // No need to refresh - data is reactive
        }
    }
    
    /**
     * Create a pending contribution for the idle money suggested transfer.
     * Prioritises HISA transfer: if the user has a mainOverflowAccountId
     * configured, the contribution targets that savings account (vaultId = null,
     * savingsAccountId = mainOverflowAccountId) so it is treated as a true HISA
     * transfer. Falls back to a regular vault contribution when no overflow
     * account is set.
     */
    fun createIdleMoneyPendingTransfer(amount: Double, fallbackVaultId: Long) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            val settings = preferencesRepository.getSettingsSnapshot()
            val overflowId = settings.mainOverflowAccountId

            // Check for existing pending "Suggested idle money transfer"
            // to prevent duplicates if clicked multiple times
            val existing = savingsRepository.getPendingVaultContributions().any {
                it.source == com.example.sparely.domain.model.VaultContributionSource.TRANSFER &&
                it.note == "Suggested idle money transfer" &&
                ((overflowId != null && overflowId != 0L && it.savingsAccountId == overflowId) ||
                 (fallbackVaultId != 0L && it.vaultId == fallbackVaultId))
            }
            if (existing) return@launch

            val contribution = if (overflowId != null && overflowId != 0L) {
                // HISA transfer – savingsAccountId set, vaultId null
                VaultContribution(
                    vaultId = null,
                    savingsAccountId = overflowId,
                    amount = amount,
                    source = com.example.sparely.domain.model.VaultContributionSource.TRANSFER,
                    note = "Suggested idle money transfer",
                    reconciled = false
                )
            } else if (fallbackVaultId != 0L) {
                // Fallback: regular vault contribution
                VaultContribution(
                    vaultId = fallbackVaultId,
                    amount = amount,
                    source = com.example.sparely.domain.model.VaultContributionSource.TRANSFER,
                    note = "Suggested idle money transfer",
                    reconciled = false
                )
            } else {
                return@launch
            }

            val contributionId = savingsRepository.logVaultContribution(contribution)
            savingsRepository.insertFrozenFund(
                pendingType = "VAULT_CONTRIBUTION",
                pendingId = contributionId,
                amount = amount,
                description = "Pending idle money transfer"
            )
        }
    }

    /**
     * Update the amount of a pending contribution.
     * Use this when the user edits the transfer amount before approving.
     */
    fun updatePendingVaultContributionAmount(contributionId: Long, newAmount: Double) {
        if (newAmount <= 0.0) return
        viewModelScope.launch {
            val existing = savingsRepository.getPendingVaultContributions().find { it.id == contributionId }
            if (existing != null) {
                // Update contribution
                val updated = existing.copy(amount = newAmount)
                savingsRepository.logVaultContribution(updated)
                
                // Update associated frozen fund
                // We remove the old one and add a new one with the updated amount
                savingsRepository.removeFrozenForPending("VAULT_CONTRIBUTION", contributionId)
                savingsRepository.insertFrozenFund(
                    pendingType = "VAULT_CONTRIBUTION",
                    pendingId = contributionId,
                    amount = newAmount,
                    description = existing.note
                )
            }
        }
    }

    fun startVaultTransferNotificationWorkflow() {
        viewModelScope.launch {
            // Refactoring NotificationScheduler to take repository is cleaner
            notificationScheduler.showVaultTransferWorkflow(savingsRepository, preferencesRepository)
        }
    }
}

class VaultViewModelFactory(

    private val container: com.example.sparely.AppContainer
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VaultViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VaultViewModel(
                savingsRepository = container.savingsRepository,
                notificationScheduler = container.notificationScheduler,
                preferencesRepository = container.preferencesRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
