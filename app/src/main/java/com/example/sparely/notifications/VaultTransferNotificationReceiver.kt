package com.example.sparely.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.sparely.SparelyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class VaultTransferNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val vaultId = intent.getLongExtra(EXTRA_VAULT_ID, -1L)
        val savingsAccountId = intent.getLongExtra(EXTRA_SAVINGS_ACCOUNT_ID, -1L)
        
        if (vaultId == -1L && savingsAccountId == -1L) return
        
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as SparelyApplication
                val container = app.container
                val finalVaultId = if (vaultId != -1L) vaultId else null
                val finalSavingsAccountId = if (savingsAccountId != -1L) savingsAccountId else null
                
                when (action) {
                    ACTION_TRANSFERRED -> handleTransferred(context, container, finalVaultId, finalSavingsAccountId)
                    ACTION_DISMISS -> handleDismiss(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleTransferred(
        context: Context, 
        container: com.example.sparely.AppContainer,
        currentVaultId: Long?,
        currentSavingsAccountId: Long?
    ) {
        // Reconcile all pending contributions for this vault/account
        val pendingForVault = container.savingsRepository.getPendingVaultContributions()
            .filter { 
                if (currentVaultId != null) it.vaultId == currentVaultId 
                else it.savingsAccountId == currentSavingsAccountId
            }
        
        pendingForVault.forEach { contribution ->
            container.savingsRepository.reconcileVaultContribution(contribution.id)
        }
        
        // Track completed count
        val prefs = context.getSharedPreferences("vault_transfer_workflow", Context.MODE_PRIVATE)
        val completedCount = prefs.getInt("completed_count", 0) + 1
        prefs.edit().putInt("completed_count", completedCount).apply()
        
        // Get remaining pending contributions grouped by vault
        val remainingPending = container.savingsRepository.getPendingVaultContributions()
        val groupedByVault = remainingPending.groupBy { it.vaultId }
        
        if (groupedByVault.isEmpty()) {
            // All done!
            NotificationHelper.dismissVaultTransferNotification(context)
            prefs.edit().clear().apply() // Reset counter
        } else {
            // Show notification for next vault
            val nextVaultId = groupedByVault.keys.first()
            val nextContributions = groupedByVault[nextVaultId]!!
            
            // Get vault name from flow - we need to collect it
            val allVaults = container.savingsRepository.observeSmartVaults().first()
            val vault = allVaults.find { it.id == nextVaultId }
            
            NotificationHelper.showVaultTransferNotification(
                context = context,
                vaultId = nextVaultId,
                vaultName = vault?.name ?: "Vault",
                contributions = nextContributions,
                currentIndex = completedCount,
                totalVaultCount = groupedByVault.size + completedCount
            )
        }
    }

    private fun handleDismiss(context: Context) {
        NotificationHelper.dismissVaultTransferNotification(context)
    }

    companion object {
        const val ACTION_TRANSFERRED = "com.example.sparely.VAULT_TRANSFER_TRANSFERRED"
        const val ACTION_DISMISS = "com.example.sparely.VAULT_TRANSFER_DISMISS"
        const val EXTRA_VAULT_ID = "vault_id"
        const val EXTRA_SAVINGS_ACCOUNT_ID = "savings_account_id"

        fun createTransferredIntent(context: Context, vaultId: Long?, savingsAccountId: Long? = null): Intent =
            Intent(context, VaultTransferNotificationReceiver::class.java).apply {
                action = ACTION_TRANSFERRED
                if (vaultId != null) putExtra(EXTRA_VAULT_ID, vaultId)
                if (savingsAccountId != null) putExtra(EXTRA_SAVINGS_ACCOUNT_ID, savingsAccountId)
            }

        fun createDismissIntent(context: Context, vaultId: Long?, savingsAccountId: Long? = null): Intent =
            Intent(context, VaultTransferNotificationReceiver::class.java).apply {
                action = ACTION_DISMISS
                if (vaultId != null) putExtra(EXTRA_VAULT_ID, vaultId)
                if (savingsAccountId != null) putExtra(EXTRA_SAVINGS_ACCOUNT_ID, savingsAccountId)
            }
    }
}
