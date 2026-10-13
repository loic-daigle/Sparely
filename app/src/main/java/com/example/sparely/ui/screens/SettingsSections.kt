package com.example.sparely.ui.screens

import androidx.compose.foundation.layout.*
import com.example.sparely.ui.utils.toInputString
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
import com.example.sparely.ai.LocalAiResult
import com.example.sparely.ai.LocalAiState
import kotlinx.coroutines.launch
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.*
import com.example.sparely.ui.utils.filterCurrencyInput
import com.example.sparely.ui.utils.toSafeDatePickerMillis
import com.example.sparely.ui.utils.toSafeDouble
import com.sparely.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.res.pluralStringResource
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.ExpressiveShapes
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
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
        shape = LocalSettingsCardShape.current,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                label = { Text(stringResource(R.string.settings_min_balance_label)) },
                prefix = { Text("$") },
                supportingText = { Text(stringResource(R.string.settings_min_balance_help)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            
            SparelyTonalButton(
                onClick = onUpdateMinBalance,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_min_balance_update))
            }

            HorizontalDivider()

            // HISA Overflow
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_overflow_title), style = MaterialTheme.typography.titleSmall)
                    SparelyTextButton(onClick = onManageSavingsAccounts) {
                        Text(stringResource(R.string.settings_manage))
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
                            text = { Text(stringResource(R.string.settings_none)) },
                            isSelected = mainOverflowAccountId == null,
                            onClick = {
                                onMainOverflowAccountIdChange(null)
                                expanded = false
                            },
                        )
                        // Locked term deposits can't take overflow money; keep the current pick visible either way.
                        savingsAccounts.filter { it.acceptsDeposits() || it.id == mainOverflowAccountId }.forEach { account ->
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
        shape = LocalSettingsCardShape.current,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        mutableStateOf(if (minimumAmount > 0) minimumAmount.toInputString() else "") 
    }
    
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = LocalSettingsCardShape.current,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        shape = LocalSettingsCardShape.current,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                text = stringResource(R.string.settings_backup_local_desc),
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
                Text(stringResource(R.string.settings_backup_now))
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
        shape = LocalSettingsCardShape.current,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    aiAssistantAccessEnabled: Boolean,
    onAiAssistantAccessEnabledChange: (Boolean) -> Unit,
    aiAssistantWriteEnabled: Boolean,
    onAiAssistantWriteEnabledChange: (Boolean) -> Unit,
    assistantActions: List<AssistantAction>,
    currencySymbol: String,
    onUndoAssistantAction: (Long) -> Unit,
    onAuthenticateUser: ((Boolean) -> Unit) -> Unit
) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = LocalSettingsCardShape.current,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_ai_assistant_access_title), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(R.string.settings_ai_assistant_access_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = aiAssistantAccessEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && biometricEnabled) {
                            // Assistants bypass the app lock, so opening that door needs the same
                            // authentication as opening the app.
                            onAuthenticateUser { success ->
                                if (success) {
                                    onAiAssistantAccessEnabledChange(true)
                                }
                            }
                        } else {
                            onAiAssistantAccessEnabledChange(enabled)
                        }
                    }
                )
            }

            if (aiAssistantAccessEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_ai_assistant_write_title), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(R.string.settings_ai_assistant_write_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = aiAssistantWriteEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && biometricEnabled) {
                                onAuthenticateUser { success ->
                                    if (success) {
                                        onAiAssistantWriteEnabledChange(true)
                                    }
                                }
                            } else {
                                onAiAssistantWriteEnabledChange(enabled)
                            }
                        }
                    )
                }
            }

            if (assistantActions.isNotEmpty()) {
                AssistantActivityList(
                    actions = assistantActions,
                    currencySymbol = currencySymbol,
                    onUndo = onUndoAssistantAction
                )
            }
        }
    }
}

/**
 * Turns Sparely's on-device AI on or off and shows whether this phone can run it. The model is
 * downloaded only after the user taps Download, and nothing here sends data off the phone.
 */
@Composable
fun SettingsOnDeviceAiCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    state: LocalAiState,
    canAnswerQuestions: Boolean,
    onRefresh: () -> Unit,
    onDownload: () -> Unit,
    onTest: suspend () -> LocalAiResult
) {
    // Support can change after Play services fetches a new configuration, so check on every visit.
    LaunchedEffect(Unit) { onRefresh() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val supported = state is LocalAiState.NeedsDownload ||
        state is LocalAiState.Downloading ||
        state is LocalAiState.Ready

    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = LocalSettingsCardShape.current,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_on_device_ai_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_on_device_ai_toggle_title), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(R.string.settings_on_device_ai_toggle_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    // A setting restored onto an unsupported phone reads as off, since it can't do anything.
                    checked = enabled && supported,
                    enabled = supported,
                    onCheckedChange = onEnabledChange
                )
            }

            val status = when (state) {
                LocalAiState.Checking -> stringResource(R.string.settings_on_device_ai_status_checking)
                LocalAiState.Unsupported -> stringResource(R.string.settings_on_device_ai_status_unsupported)
                LocalAiState.NeedsDownload -> stringResource(R.string.settings_on_device_ai_status_needs_download)
                is LocalAiState.Downloading -> stringResource(
                    R.string.settings_on_device_ai_status_downloading,
                    android.text.format.Formatter.formatShortFileSize(context, state.bytesDownloaded)
                )
                LocalAiState.Ready -> stringResource(
                    if (canAnswerQuestions) R.string.settings_on_device_ai_status_ready
                    else R.string.settings_on_device_ai_status_ready_summaries_only
                )
                is LocalAiState.Error -> stringResource(R.string.settings_on_device_ai_status_error)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.settings_on_device_ai_status_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state is LocalAiState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                if (state is LocalAiState.Error && !state.message.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.settings_on_device_ai_error_details, state.message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state is LocalAiState.Checking || state is LocalAiState.Downloading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            when {
                state is LocalAiState.NeedsDownload && enabled -> {
                    SparelyButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_on_device_ai_download))
                    }
                }
                state is LocalAiState.Error -> {
                    SparelyOutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_on_device_ai_retry))
                    }
                }
            }

            if (state is LocalAiState.Ready && enabled) {
                OnDeviceAiTest(onTest = onTest)
            }

            Text(
                text = stringResource(R.string.settings_on_device_ai_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Runs a sample summary and shows what came back, or why nothing did. */
@Composable
private fun OnDeviceAiTest(onTest: suspend () -> LocalAiResult) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<LocalAiResult?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SparelyOutlinedButton(
            onClick = {
                running = true
                result = null
                scope.launch {
                    result = try {
                        onTest()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        LocalAiResult.Failed(e.message)
                    }
                    running = false
                }
            },
            enabled = !running,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(if (running) R.string.settings_on_device_ai_test_running else R.string.settings_on_device_ai_test))
        }
        if (running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        when (val r = result) {
            null -> Unit
            is LocalAiResult.Success -> {
                Text(
                    stringResource(R.string.settings_on_device_ai_test_result),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(r.text, style = MaterialTheme.typography.bodyMedium)
            }
            else -> Text(
                text = stringResource(
                    R.string.settings_on_device_ai_test_failed,
                    when (r) {
                        is LocalAiResult.Failed -> r.message ?: "—"
                        LocalAiResult.NotReady -> "not ready"
                        LocalAiResult.PromptTooLong -> "prompt too long"
                        else -> ""
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** Recent entries AI assistants added, each with an Undo button while it still stands. */
@Composable
private fun AssistantActivityList(
    actions: List<AssistantAction>,
    currencySymbol: String,
    onUndo: (Long) -> Unit
) {
    val dateFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.settings_ai_assistant_activity_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        actions.forEach { action ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val label = when (action.type) {
                        AssistantActionType.EXPENSE_RECORDED -> R.string.settings_ai_assistant_activity_expense
                        AssistantActionType.INCOME_RECORDED -> R.string.settings_ai_assistant_activity_income
                    }
                    Text(
                        text = stringResource(label, action.description),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${action.amount.formatCurrency(currencySymbol)} · " +
                            Instant.ofEpochMilli(action.createdAtEpochMillis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(dateFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (action.isUndone) {
                    Text(
                        stringResource(R.string.settings_ai_assistant_activity_undone),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SparelyTextButton(onClick = { onUndo(action.id) }) {
                        Text(stringResource(R.string.settings_ai_assistant_activity_undo))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDataCard(
    settings: SparelySettings,
    expenses: List<Expense>,
    onResetHistory: (Boolean) -> Unit,
    onExpenseHistoryRetentionChange: (ExpenseHistoryRetention) -> Unit,
    brandfetchClientId: String?,
    onBrandfetchClientIdChange: (String) -> Unit,
    onExportBackupClick: () -> Unit,
    onImportBackupClick: () -> Unit,
    onExportCsvClick: () -> Unit
) {
    var brandfetchKey by remember(brandfetchClientId) { mutableStateOf(brandfetchClientId ?: "") }
    var showImportConfirmation by remember { mutableStateOf(false) }
    // A shorter retention deletes older expenses as soon as it's picked, so it waits here for confirmation
    var pendingRetention by remember { mutableStateOf<ExpenseHistoryRetention?>(null) }

    if (showImportConfirmation) {
        SparelyAlertDialog(
            onDismissRequest = { showImportConfirmation = false },
            icon = { MaterialSymbolIcon(icon = MaterialSymbols.WARNING, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.settings_import_confirm_title)) },
            text = { Text(stringResource(R.string.settings_import_confirm_message)) },
            confirmButton = {
                SparelyButton(
                    onClick = {
                        showImportConfirmation = false
                        onImportBackupClick()
                    },
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Text(stringResource(R.string.settings_import_choose_file))
                }
            },
            dismissButton = {
                Row {
                    SparelyTextButton(onClick = {
                        showImportConfirmation = false
                        onExportBackupClick()
                    }) {
                        Text(stringResource(R.string.settings_export_first))
                    }
                    SparelyTextButton(onClick = { showImportConfirmation = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        )
    }

    pendingRetention?.let { retention ->
        // Same cutoff SettingsViewModel.pruneHistory uses: anything dated before it is deleted
        val cutoff = LocalDate.now().minusMonths((retention.months ?: 0).toLong())
        val doomedCount = expenses.count { it.date.isBefore(cutoff) }
        SparelyAlertDialog(
            onDismissRequest = { pendingRetention = null },
            icon = { MaterialSymbolIcon(icon = MaterialSymbols.WARNING, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.settings_retention_confirm_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.settings_retention_confirm_message,
                        doomedCount,
                        doomedCount,
                        cutoff.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
                    )
                )
            },
            confirmButton = {
                SparelyButton(
                    onClick = {
                        onExpenseHistoryRetentionChange(retention)
                        pendingRetention = null
                    },
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Text(stringResource(R.string.settings_retention_confirm_action))
                }
            },
            dismissButton = {
                SparelyTextButton(onClick = { pendingRetention = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = LocalSettingsCardShape.current,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        onClick = { showImportConfirmation = true },
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
                    text = stringResource(R.string.settings_export_expenses_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.settings_export_expenses_desc),
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
                                 retentionExpanded = false
                                 val cutoff = retention.months?.let { LocalDate.now().minusMonths(it.toLong()) }
                                 val deletesSomething = cutoff != null && expenses.any { it.date.isBefore(cutoff) }
                                 if (deletesSomething) {
                                     pendingRetention = retention
                                 } else {
                                     onExpenseHistoryRetentionChange(retention)
                                 }
                             }
                         )
                    }
                }
            }
            
            val currentRetention = settings.expenseHistoryRetention
            if (currentRetention == ExpenseHistoryRetention.INDEFINITELY) {
                 Text(
                    text = stringResource(R.string.settings_history_retention_all, expenses.size),
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
                    icon = { MaterialSymbolIcon(icon = MaterialSymbols.WARNING, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text(stringResource(R.string.settings_reset_history_confirm_title)) },
                    text = {
                        Text(pluralStringResource(R.plurals.settings_reset_history_confirm_message, expenses.size, expenses.size))
                    },
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
                        Row {
                            SparelyTextButton(onClick = {
                                showResetConfirmation = false
                                onExportBackupClick()
                            }) {
                                Text(stringResource(R.string.settings_export_first))
                            }
                            SparelyTextButton(onClick = { showResetConfirmation = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
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


/** One page of the settings hub; [groupRes] decides which hub card lists it. */
enum class SettingsSection(val titleRes: Int, val descRes: Int, val icon: Int, val groupRes: Int) {
    Profile(R.string.settings_group_profile, R.string.settings_hub_profile_desc, MaterialSymbols.PERSON, R.string.settings_hub_you),
    Region(R.string.settings_group_region, R.string.settings_hub_region_desc, MaterialSymbols.PUBLIC, R.string.settings_hub_you),
    Income(R.string.settings_group_income_accounts, R.string.settings_hub_income_desc, MaterialSymbols.ACCOUNT_BALANCE, R.string.settings_hub_money),
    Automation(R.string.settings_group_saving_automation, R.string.settings_hub_automation_desc, MaterialSymbols.SAVINGS, R.string.settings_hub_money),
    PaymentMethods(R.string.settings_group_payment_methods, R.string.settings_hub_payment_methods_desc, MaterialSymbols.CREDIT_CARD, R.string.settings_hub_money),
    Notifications(R.string.settings_group_notifications, R.string.settings_hub_notifications_desc, MaterialSymbols.NOTIFICATIONS, R.string.settings_hub_app),
    Security(R.string.settings_group_security, R.string.settings_hub_security_desc, MaterialSymbols.LOCK, R.string.settings_hub_app),
    OnDeviceAi(R.string.settings_group_on_device_ai, R.string.settings_hub_on_device_ai_desc, MaterialSymbols.LIGHTBULB, R.string.settings_hub_app),
    Data(R.string.settings_group_data_backup, R.string.settings_hub_data_desc, MaterialSymbols.HISTORY, R.string.settings_hub_app)
}

/** Shape the next settings card should take; [SettingsSegmentedColumn] sets it per position. */
val LocalSettingsCardShape = staticCompositionLocalOf<Shape> { ExpressiveShapes.large }

/**
 * Shape for item [index] of [count] in a segmented group: large corners at the group's ends,
 * small ones where items meet, so the stack reads as one rounded block split by thin gaps.
 */
fun settingsSegmentShape(index: Int, count: Int): Shape {
    val outer = 28.dp
    val inner = 6.dp
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

class SettingsSegmentScope {
    internal val segments = mutableListOf<@Composable () -> Unit>()

    fun segment(content: @Composable () -> Unit) {
        segments += content
    }
}

/** Stacks settings cards as one segmented group, each card taking its place's shape. */
@Composable
fun SettingsSegmentedColumn(build: SettingsSegmentScope.() -> Unit) {
    val segments = SettingsSegmentScope().apply(build).segments
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        segments.forEachIndexed { index, segment ->
            CompositionLocalProvider(LocalSettingsCardShape provides settingsSegmentShape(index, segments.size)) {
                segment()
            }
        }
    }
}

/** The settings landing page: sections grouped under a heading, each row its own segment of the group. */
@Composable
fun SettingsHub(onOpen: (SettingsSection) -> Unit) {
    val groups = SettingsSection.entries.groupBy { it.groupRes }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        for ((groupRes, sections) in groups) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(groupRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 16.dp, bottom = 6.dp)
                        .semantics { heading() }
                )
                sections.forEachIndexed { index, section ->
                    SettingsHubRow(
                        section = section,
                        shape = settingsSegmentShape(index, sections.size),
                        onClick = { onOpen(section) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHubRow(section: SettingsSection, shape: Shape, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                MaterialSymbolIcon(
                    icon = section.icon,
                    contentDescription = null,
                    size = 22.dp,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(section.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(section.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MaterialSymbolIcon(
                icon = MaterialSymbols.ARROW_FORWARD,
                contentDescription = null,
                size = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
