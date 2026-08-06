package com.example.sparely.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.*
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.*
import com.example.sparely.ui.utils.filterCurrencyInput
import com.example.sparely.ui.utils.toSafeDatePickerMillis
import com.example.sparely.ui.utils.toSafeDouble
import com.sparely.app.R
import java.time.Instant
import java.time.ZoneOffset
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.ExpressiveShapes
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMainAccountCard(
    monthlyIncomeText: String,
    onMonthlyIncomeTextChange: (String) -> Unit,
    onUpdateIncome: () -> Unit,
    mainAccountBalanceText: String,
    onMainAccountBalanceTextChange: (String) -> Unit,
    onUpdateBalance: () -> Unit,
    includeTax: Boolean,
    onIncludeTaxToggle: (Boolean) -> Unit,
    minMainAccountBalanceText: String,
    onMinMainAccountBalanceTextChange: (String) -> Unit,
    onUpdateMinBalance: () -> Unit,
    smartVaults: List<SmartVault>,
    savingsAccounts: List<com.example.sparely.domain.model.SavingsAccount>,
    mainOverflowAccountId: Long?,
    onMainOverflowAccountIdChange: (Long?) -> Unit,
    onManageSavingsAccounts: () -> Unit
) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_income_tax_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            SparelyTextField(
                value = monthlyIncomeText,
                onValueChange = { onMonthlyIncomeTextChange(it.filterCurrencyInput()) },
                label = { Text(stringResource(R.string.settings_monthly_income_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            SparelyTonalButton(onClick = onUpdateIncome) {
                Text(stringResource(R.string.settings_update_income))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.settings_main_account_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.settings_main_account_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            SparelyTextField(
                value = mainAccountBalanceText,
                onValueChange = { onMainAccountBalanceTextChange(it.filterCurrencyInput()) },
                label = { Text(stringResource(R.string.settings_main_account_balance_label)) },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            
            SparelyTonalButton(
                onClick = onUpdateBalance,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_main_account_update_balance))
            }

            Spacer(modifier = Modifier.height(8.dp))

             // Minimum Balance
            SparelyTextField(
                value = minMainAccountBalanceText,
                onValueChange = { onMinMainAccountBalanceTextChange(it.filterCurrencyInput()) },
                label = { Text("Minimum Balance Protection") },
                prefix = { Text("$") },
                supportingText = { Text("Saving Tax & Transfers will stop if balance falls below this.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            
            SparelyTonalButton(
                onClick = onUpdateMinBalance,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Update Minimum Balance")
            }

            HorizontalDivider()

            // HISA Overflow
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("High Interest Account (Overflow)", style = MaterialTheme.typography.titleSmall)
                    SparelyTextButton(onClick = onManageSavingsAccounts) {
                        Text("Manage")
                    }
                }
                Text(
                    "Excess funds after allocations will be sent here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                
                var expanded by remember { mutableStateOf(false) }
                val selectedVaultName = savingsAccounts.find { it.id == mainOverflowAccountId }?.name ?: "None"

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    SparelyDropdownAnchor(
                        value = selectedVaultName,
                        onValueChange = {},
                        expanded = expanded,
                        label = "Select HISA Vault",
                        modifier = Modifier.fillMaxWidth()
                    )
                    SparelyDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        SparelyDropdownMenuItem(
                            text = { Text("None") },
                            isSelected = mainOverflowAccountId == null,
                            onClick = {
                                onMainOverflowAccountIdChange(null)
                                expanded = false
                            },
                        )
                        savingsAccounts.forEach { account ->
                            SparelyDropdownMenuItem(
                                text = { Text(account.name) },
                                isSelected = mainOverflowAccountId == account.id,
                                onClick = {
                                    onMainOverflowAccountIdChange(account.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }



            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_include_tax))
                Switch(checked = includeTax, onCheckedChange = onIncludeTaxToggle)
            }
        }
    }
}

@Composable
fun SettingsSmartSavingsCard(
    settings: SparelySettings,
    selectedAllocationMode: VaultAllocationMode,
    onAllocationModeChange: (VaultAllocationMode) -> Unit,
    savingTaxRatePercent: Float,
    onSavingTaxRatePercentChange: (Float) -> Unit,
    onSavingTaxRateCommit: (Double) -> Unit,
    activeSavingTaxRate: Double,
    onDynamicSavingTaxToggle: (Boolean) -> Unit
) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_vault_automation_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SparelyChip(
                    selected = selectedAllocationMode == VaultAllocationMode.DYNAMIC_AUTO,
                    onClick = { onAllocationModeChange(VaultAllocationMode.DYNAMIC_AUTO) },
                    label = { Text(stringResource(R.string.settings_vault_mode_dynamic)) }
                )
                SparelyChip(
                    selected = selectedAllocationMode == VaultAllocationMode.MANUAL,
                    onClick = { onAllocationModeChange(VaultAllocationMode.MANUAL) },
                    label = { Text(stringResource(R.string.settings_vault_mode_manual)) }
                )
            }
            Text(
                text = when (selectedAllocationMode) {
                    VaultAllocationMode.DYNAMIC_AUTO -> stringResource(R.string.settings_vault_mode_dynamic_desc)
                    VaultAllocationMode.MANUAL -> stringResource(R.string.settings_vault_mode_manual_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_saving_tax_title), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = settings.dynamicSavingTaxEnabled,
                    onCheckedChange = onDynamicSavingTaxToggle
                )
            }

            val displayedSavingTaxRate = if (settings.dynamicSavingTaxEnabled) {
                activeSavingTaxRate
            } else {
                (savingTaxRatePercent / 100f).toDouble()
            }

            fun formatPer(v: Double): String = v.formatPercent(1)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (settings.dynamicSavingTaxEnabled) {
                        stringResource(R.string.settings_saving_tax_auto, formatPer(displayedSavingTaxRate))
                    } else {
                        stringResource(R.string.settings_saving_tax_manual, formatPer(displayedSavingTaxRate))
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                SparelyTextButton(
                    onClick = {
                        val baseline = SparelySettings().savingTaxRate
                        onSavingTaxRatePercentChange((baseline * 100).toFloat())
                        onSavingTaxRateCommit(baseline)
                    },
                    enabled = !settings.dynamicSavingTaxEnabled
                ) {
                    Text(stringResource(R.string.action_reset))
                }
            }
            Slider(
                value = savingTaxRatePercent,
                onValueChange = { updated ->
                    if (!settings.dynamicSavingTaxEnabled) {
                        onSavingTaxRatePercentChange(updated.coerceIn(0f, 25f))
                    }
                },
                valueRange = 0f..25f,
                enabled = !settings.dynamicSavingTaxEnabled,
                onValueChangeFinished = {
                    if (!settings.dynamicSavingTaxEnabled) {
                        onSavingTaxRateCommit((savingTaxRatePercent / 100f).toDouble())
                    }
                }
            )
            Text(
                text = stringResource(R.string.settings_saving_tax_desc_extended),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (settings.dynamicSavingTaxEnabled) {
                Text(
                    text = stringResource(R.string.settings_saving_tax_automation_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsSmartTransferCard(
    minimumAmount: Double,
    onMinimumAmountChange: (Double) -> Unit,
    currencySymbol: String = "$"
) {
    var amountText by remember(minimumAmount) { 
        mutableStateOf(if (minimumAmount > 0) minimumAmount.toString() else "") 
    }
    
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.settings_smart_transfer_title), 
                style = MaterialTheme.typography.titleSmall, 
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.settings_smart_transfer_minimum_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            SparelyTextField(
                value = amountText,
                onValueChange = { newValue ->
                    val filtered = newValue.filterCurrencyInput()
                    amountText = filtered
                },
                label = { Text(stringResource(R.string.settings_smart_transfer_minimum_label)) },
                prefix = { Text(currencySymbol) },
                placeholder = { Text(stringResource(R.string.settings_smart_transfer_minimum_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            
            SparelyTonalButton(
                onClick = { 
                    val amount = amountText.toSafeDouble() ?: 0.0
                    onMinimumAmountChange(amount.coerceAtLeast(0.0))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
fun SettingsAutoBackupCard(
    autoBackupEnabled: Boolean,
    autoBackupFrequencyDays: Int,
    lastAutoBackupTimestamp: Long?,
    onAutoBackupEnabledChange: (Boolean, Int) -> Unit,
    onBackupNowClick: () -> Unit
) {
    var selectedFrequency by remember(autoBackupFrequencyDays) { mutableStateOf(autoBackupFrequencyDays) }

    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.settings_auto_backup_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.settings_auto_backup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Automatic backups are stored locally on your device and can be restored anytime.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_auto_backup_enable), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = autoBackupEnabled,
                    onCheckedChange = { enabled ->
                        onAutoBackupEnabledChange(enabled, selectedFrequency)
                    }
                )
            }

            if (autoBackupEnabled) {
                Text(stringResource(R.string.settings_auto_backup_frequency_label), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SparelyChip(
                        selected = selectedFrequency == 1,
                        onClick = {
                            selectedFrequency = 1
                            onAutoBackupEnabledChange(true, 1)
                        },
                        label = { Text(stringResource(R.string.settings_auto_backup_frequency_daily)) }
                    )
                    SparelyChip(
                        selected = selectedFrequency == 7,
                        onClick = {
                            selectedFrequency = 7
                            onAutoBackupEnabledChange(true, 7)
                        },
                        label = { Text(stringResource(R.string.settings_auto_backup_frequency_weekly)) }
                    )
                    SparelyChip(
                        selected = selectedFrequency == 30,
                        onClick = {
                            selectedFrequency = 30
                            onAutoBackupEnabledChange(true, 30)
                        },
                        label = { Text(stringResource(R.string.settings_auto_backup_frequency_monthly)) }
                    )
                }
            }

            // Last backup info
            val lastBackupText = if (lastAutoBackupTimestamp != null) {
                val instant = java.time.Instant.ofEpochMilli(lastAutoBackupTimestamp)
                val dateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
                val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
                stringResource(R.string.settings_auto_backup_last_backup, dateTime.format(formatter))
            } else {
                stringResource(R.string.settings_auto_backup_last_backup, stringResource(R.string.settings_auto_backup_never))
            }

            Text(
                text = lastBackupText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SparelyTonalButton(
                onClick = onBackupNowClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Trigger Backup Now")
            }

            Text(
                text = stringResource(R.string.settings_auto_backup_location),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsBudgetCard(
    autoModeEnabled: Boolean,
    onAutoToggle: (Boolean) -> Unit,
    recommendation: RecommendationResult?,
    emergency: Float,
    invest: Float,
    funPercent: Float,
    onEmergencyChange: (Float) -> Unit,
    onInvestChange: (Float) -> Unit,
    onFunChange: (Float) -> Unit
) {
    fun formatPer(v: Double): String = v.formatPercent(0)
    
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.settings_auto_recommendations_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(R.string.settings_auto_recommendations_toggle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoModeEnabled, onCheckedChange = onAutoToggle)
            }
            recommendation?.let {
                Text(
                    text = stringResource(R.string.settings_auto_recommendations_latest, formatPer(it.recommendedPercentages.emergency), formatPer(it.recommendedPercentages.invest)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (!autoModeEnabled) {
                Text(stringResource(R.string.settings_manual_percentages_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                SettingsSlider(label = stringResource(R.string.settings_category_emergency), value = emergency, onValueChange = onEmergencyChange)
                SettingsSlider(label = stringResource(R.string.settings_category_invest), value = invest, onValueChange = onInvestChange)
                SettingsSlider(label = stringResource(R.string.settings_category_fun), value = funPercent, onValueChange = onFunChange)
                
                val total = emergency + invest + funPercent
                Text(
                    text = stringResource(R.string.settings_total_percent, formatPer(total.toDouble() / 100.0)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsSecurityCard(
    biometricEnabled: Boolean,
    onBiometricEnabledChange: (Boolean) -> Unit,
    onAuthenticateUser: ((Boolean) -> Unit) -> Unit
) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_security_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_biometric_unlock_title), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(R.string.settings_biometric_unlock_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = biometricEnabled,
                    onCheckedChange = { matched ->
                        if (!matched) {
                            // Disabling: require authentication
                            onAuthenticateUser { success ->
                                if (success) {
                                    onBiometricEnabledChange(false)
                                }
                            }
                        } else {
                            // Enabling: just do it
                            onBiometricEnabledChange(true)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDataCard(
    settings: SparelySettings,
    expensesSize: Int,
    onResetHistory: (Boolean) -> Unit,
    onExpenseHistoryRetentionChange: (ExpenseHistoryRetention) -> Unit,
    brandfetchClientId: String?,
    onBrandfetchClientIdChange: (String) -> Unit,
    onExportBackupClick: () -> Unit,
    onImportBackupClick: () -> Unit,
    onExportCsvClick: () -> Unit
) {
    var brandfetchKey by remember(brandfetchClientId) { mutableStateOf(brandfetchClientId ?: "") }

    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_data_privacy_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            // Backup & Restore Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_backup_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SparelyTonalButton(
                        onClick = onExportBackupClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(painter = androidx.compose.ui.res.painterResource(id = MaterialSymbols.UPLOAD_FILE), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.settings_export_backup), fontSize = MaterialTheme.typography.labelSmall.fontSize)
                    }

                    SparelyTonalButton(
                        onClick = onImportBackupClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(painter = androidx.compose.ui.res.painterResource(id = MaterialSymbols.DOWNLOAD), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.settings_restore_backup), fontSize = MaterialTheme.typography.labelSmall.fontSize)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Export Data Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Export Expenses",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Export your expense history as a spreadsheet for analysis or records",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SparelyTonalButton(
                    onClick = onExportCsvClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(painter = androidx.compose.ui.res.painterResource(id = MaterialSymbols.CSV), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_export_csv))
                }
            }
            
            
            Spacer(modifier = Modifier.height(16.dp))

            // Expense History Retention
            Text(stringResource(R.string.settings_insights_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            var retentionExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = retentionExpanded,
                onExpandedChange = { retentionExpanded = it }
            ) {
                SparelyDropdownAnchor(
                    value = settings.expenseHistoryRetention.label,
                    onValueChange = {},
                    expanded = retentionExpanded,
                    label = stringResource(R.string.settings_history_retention_all).substringBefore(" "),
                    modifier = Modifier.fillMaxWidth()
                )
                SparelyDropdownMenu(
                    expanded = retentionExpanded,
                    onDismissRequest = { retentionExpanded = false }
                ) {
                    ExpenseHistoryRetention.entries.forEach { retention ->
                         SparelyDropdownMenuItem(
                             text = { Text(retention.label) },
                             isSelected = settings.expenseHistoryRetention == retention,
                             onClick = {
                                 onExpenseHistoryRetentionChange(retention)
                                 retentionExpanded = false
                             }
                         )
                    }
                }
            }
            
            val currentRetention = settings.expenseHistoryRetention
            if (currentRetention == ExpenseHistoryRetention.INDEFINITELY) {
                 Text(
                    text = stringResource(R.string.settings_history_retention_all, expensesSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                 )
            } else {
                 Text(
                    text = stringResource(R.string.settings_history_retention_info, currentRetention.label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                 )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Brandfetch Integration
            Text(stringResource(R.string.settings_brandfetch_info).substringBefore(" "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.settings_brandfetch_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SparelyTextField(
                value = brandfetchKey,
                onValueChange = { brandfetchKey = it },
                label = { Text(stringResource(R.string.settings_brandfetch_info).substringBefore(" ")) },
                placeholder = { Text(stringResource(R.string.settings_brandfetch_info).substringAfter(" ")) },
                modifier = Modifier.fillMaxWidth()
            )
            SparelyTonalButton(
                onClick = { 
                    onBrandfetchClientIdChange(brandfetchKey.trim().takeIf { it.isNotEmpty() } ?: "")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_save))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Reset Expense History
            var showResetConfirmation by remember { mutableStateOf(false) }

            if (showResetConfirmation) {
                SparelyAlertDialog(
                    onDismissRequest = { showResetConfirmation = false },
                    title = { Text(stringResource(R.string.settings_reset_history_confirm_title)) },
                    text = { Text(stringResource(R.string.settings_reset_history_confirm_message)) },
                    confirmButton = {
                        SparelyButton(
                            onClick = {
                                onResetHistory(false)
                                showResetConfirmation = false
                            },
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) {
                            Text(stringResource(R.string.action_delete))
                        }
                    },
                    dismissButton = {
                        SparelyTextButton(onClick = { showResetConfirmation = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }

            SparelyButton(
                onClick = { showResetConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                icon = {
                    Icon(painter = androidx.compose.ui.res.painterResource(id = MaterialSymbols.DELETE), contentDescription = null, modifier = Modifier.size(20.dp))
                }
            ) {
                Text(stringResource(R.string.settings_reset_history))
            }
        }
    }
}


enum class SettingsTab(val titleRes: Int, val icon: Int) {
    General(R.string.settings_tab_general, MaterialSymbols.PERSON),
    Finances(R.string.settings_tab_finances, MaterialSymbols.PAYMENTS),
    System(R.string.settings_tab_system, MaterialSymbols.SETTINGS)
}

@Composable
fun SettingsSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${value.toInt()}%", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = { onValueChange(it) },
            valueRange = 0f..100f
        )
    }
}
