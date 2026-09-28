package com.example.sparely.ui.screens

import androidx.compose.foundation.layout.Arrangement
import com.example.sparely.ui.utils.toInputString
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Divider
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import com.example.sparely.ui.components.SparelyTextField
import com.example.sparely.ui.components.SparelyChip
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sparely.app.R
import com.example.sparely.ui.components.ExpressiveCard
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.AlertMessage
import com.example.sparely.domain.model.EducationStatus
import com.example.sparely.domain.model.EmploymentStatus
import com.example.sparely.domain.model.RecommendationResult
import com.example.sparely.domain.model.RiskLevel
import com.example.sparely.domain.model.SavingsPercentages
import com.example.sparely.domain.model.SparelySettings
import com.example.sparely.domain.model.PayScheduleSettings
import com.example.sparely.domain.model.IncomeTrackingMode
import com.example.sparely.domain.model.PayInterval
import com.example.sparely.domain.model.VaultAllocationMode
import com.example.sparely.domain.model.ExpenseHistoryRetention
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.ui.components.SparelyAlertDialog
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.SparelyTonalButton
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.ExpressiveShapes
import com.example.sparely.ui.theme.warning
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.utils.roundToTwoDecimals
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import com.example.sparely.ui.utils.toSafeDatePickerMillis
import com.example.sparely.ui.utils.filterCurrencyInput
import com.example.sparely.ui.utils.toSafeDouble
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SparelySettings,
    activeSaveRate: Double,
    activeSavingTaxRate: Double,
    automationNotes: List<String>,
    autoModeEnabled: Boolean,
    recommendation: RecommendationResult?,
    alerts: List<AlertMessage>,
    onPercentagesChange: (SavingsPercentages) -> Unit,
    onAutoToggle: (Boolean) -> Unit,
    onRiskChange: (RiskLevel) -> Unit,
    onAgeChange: (Int) -> Unit,
    onEducationStatusChange: (EducationStatus) -> Unit,
    onEmploymentStatusChange: (EmploymentStatus) -> Unit,
    onHasDebtsChange: (Boolean) -> Unit,
    onEmergencyFundChange: (Double) -> Unit,
    onPrimaryGoalChange: (String?) -> Unit,
    onDisplayNameChange: (String?) -> Unit,
    onBirthdayChange: (LocalDate?) -> Unit,
    onMonthlyIncomeChange: (Double) -> Unit,
    onIncludeTaxToggle: (Boolean) -> Unit,
    onVaultAllocationModeChange: (VaultAllocationMode) -> Unit,
    onSavingTaxRateChange: (Double) -> Unit,
    onDynamicSavingTaxToggle: (Boolean) -> Unit,
    onReminderChange: (Boolean, Int, Int) -> Unit,
    onResetHistory: (Boolean) -> Unit,
    onPayScheduleChange: (PayScheduleSettings) -> Unit,
    onRecordPaycheck: (Double, LocalDate, Boolean, Boolean) -> Unit,
    onAutoDepositsEnabledChange: (Boolean) -> Unit,
    onAutoDepositCheckHourChange: (Int) -> Unit,
    onManualAutoDepositTrigger: () -> Unit,
    autoDepositsEnabled: Boolean,
    autoDepositCheckHour: Int,
    onRegionalSettingsChange: (String, String, String, Double?) -> Unit, // countryCode, languageCode, currencyCode, customTaxRate
    onMainAccountBalanceChange: (Double) -> Unit,
    onExportData: (android.net.Uri) -> Unit,
    onImportData: (android.net.Uri) -> Unit,
    onBrandfetchClientIdChange: (String?) -> Unit = {},
    expenses: List<Expense> = emptyList(),
    onExpenseHistoryRetentionChange: (ExpenseHistoryRetention) -> Unit = {},
    paymentMethods: List<com.example.sparely.domain.model.PaymentMethod> = emptyList(),
    onAddPaymentMethod: (com.example.sparely.domain.model.PaymentMethod) -> Unit = {},
    onEditPaymentMethod: (com.example.sparely.domain.model.PaymentMethod) -> Unit = {},
    onDeletePaymentMethod: (com.example.sparely.domain.model.PaymentMethod) -> Unit = {},
    onCreditCardReminderChange: (Boolean, Int, Int) -> Unit = { _, _, _ -> },
    onCreditCardUtilizationChange: (Boolean, Int) -> Unit = { _, _ -> },
    onBiometricEnabledChange: (Boolean) -> Unit = {},
    onAuthenticateUser: ((Boolean) -> Unit) -> Unit = {},
    stores: List<com.example.sparely.domain.model.Store> = emptyList(),
    onExportExpensesToCsv: (android.net.Uri, android.content.Context, List<Expense>, List<com.example.sparely.domain.model.Store>) -> Unit = { _, _, _, _ -> },
    // Smart Transfer Minimum
    onSmartTransferMinimumChange: (Double) -> Unit = {},
    // Auto Backup
    onAutoBackupSettingsChange: (Boolean, Int) -> Unit = { _, _ -> },
    onBackupNowClick: () -> Unit = {},
    smartVaults: List<SmartVault> = emptyList(),
    savingsAccounts: List<com.example.sparely.domain.model.SavingsAccount> = emptyList(),
    onMainOverflowAccountIdChange: (Long?) -> Unit = {},
    onMinMainAccountBalanceChange: (Double) -> Unit = {},
    onManageSavingsAccounts: () -> Unit = {}
) {
    var brandfetchClientId by remember(settings.brandfetchClientId) { mutableStateOf(settings.brandfetchClientId ?: "") }
    var emergency by remember(settings.defaultPercentages) { mutableStateOf(settings.defaultPercentages.emergency.toFloat()) }
    var invest by remember(settings.defaultPercentages) { mutableStateOf(settings.defaultPercentages.invest.toFloat()) }
    var funPercent by remember(settings.defaultPercentages) { mutableStateOf(settings.defaultPercentages.`fun`.toFloat()) }
    var monthlyIncomeText by remember(settings.monthlyIncome) { mutableStateOf(settings.monthlyIncome.toInputString()) }
    var mainAccountBalanceText by remember(settings.mainAccountBalance) { mutableStateOf(settings.mainAccountBalance.toInputString()) }
    var minMainAccountBalanceText by remember(settings.minMainAccountBalance) { mutableStateOf(settings.minMainAccountBalance.toInputString()) }
    var remindersEnabled by remember(settings.remindersEnabled) { mutableStateOf(settings.remindersEnabled) }
    var reminderHour by remember(settings.reminderHour) { mutableStateOf(settings.reminderHour) }
    var reminderFrequency by remember(settings.reminderFrequencyDays) { mutableStateOf(settings.reminderFrequencyDays) }
    var age by remember(settings.effectiveAge) { mutableStateOf(settings.effectiveAge) }
    var displayName by remember(settings.displayName) { mutableStateOf(settings.displayName.orEmpty()) }
    var hasDebts by remember(settings.hasDebts) { mutableStateOf(settings.hasDebts) }
    var emergencyFundText by remember(settings.currentEmergencyFund) {
        mutableStateOf(settings.currentEmergencyFund.takeIf { it > 0.0 }?.let { it.toInputString(0) } ?: "")
    }
    var primaryGoal by remember(settings.primaryGoal) { mutableStateOf(settings.primaryGoal.orEmpty()) }
    var educationExpanded by remember { mutableStateOf(false) }
    var employmentExpanded by remember { mutableStateOf(false) }
    var selectedEducation by remember(settings.educationStatus) { mutableStateOf(settings.educationStatus) }
    var selectedEmployment by remember(settings.employmentStatus) { mutableStateOf(settings.employmentStatus) }
    var showBirthdayPicker by remember { mutableStateOf(false) }
    var selectedAllocationMode by remember(settings.vaultAllocationMode) { mutableStateOf(settings.vaultAllocationMode) }
    var savingTaxRatePercent by remember(settings.savingTaxRate, activeSavingTaxRate, settings.dynamicSavingTaxEnabled) {
        mutableStateOf(
            if (settings.dynamicSavingTaxEnabled) {
                (activeSavingTaxRate * 100).toFloat()
            } else {
                (settings.savingTaxRate * 100).toFloat()
            }
        )
    }
    LaunchedEffect(settings.dynamicSavingTaxEnabled, settings.savingTaxRate, activeSavingTaxRate) {
        savingTaxRatePercent = if (settings.dynamicSavingTaxEnabled) {
            (activeSavingTaxRate * 100).toFloat()
        } else {
            (settings.savingTaxRate * 100).toFloat()
        }
        }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let(onExportData)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onImportData)
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { onExportExpensesToCsv(it, context, expenses, stores) }
    }

    var selectedTab by remember { mutableStateOf(SettingsTab.General) }



    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            SettingsTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(stringResource(tab.titleRes)) },
                    icon = { Icon(painter = androidx.compose.ui.res.painterResource(id = tab.icon), contentDescription = null, modifier = Modifier.size(24.dp)) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    SettingsTab.General -> {
                        ProfileCard(
                            displayName = displayName,
                            onDisplayNameChange = {
                                displayName = it
                                onDisplayNameChange(it.trim().takeIf { trimmed -> trimmed.isNotEmpty() })
                            },
                            hasDebts = hasDebts,
                            onHasDebtsChange = {
                                hasDebts = it
                                onHasDebtsChange(it)
                            },
                            emergencyFundText = emergencyFundText,
                            onEmergencyFundChange = { valueText ->
                                emergencyFundText = valueText
                                valueText.toSafeDouble()?.let(onEmergencyFundChange)
                            },
                            primaryGoal = primaryGoal,
                            onPrimaryGoalChange = {
                                primaryGoal = it
                                onPrimaryGoalChange(it.trim().takeIf { trimmed -> trimmed.isNotEmpty() })
                            },
                            birthday = settings.birthday,
                            effectiveAge = age,
                            onEditBirthday = { showBirthdayPicker = true },
                            onClearBirthday = {
                                onBirthdayChange(null)
                                showBirthdayPicker = false
                            }
                        )

                        if (showBirthdayPicker) {
                            val initialMillis = settings.birthday.toSafeDatePickerMillis()
                            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
                            LaunchedEffect(initialMillis) {
                                if (initialMillis != null && datePickerState.selectedDateMillis != initialMillis) {
                                    datePickerState.selectedDateMillis = initialMillis
                                }
                            }
                            DatePickerDialog(
                                onDismissRequest = { showBirthdayPicker = false },
                                confirmButton = {
                                    SparelyTextButton(onClick = {
                                        val selectedMillis = datePickerState.selectedDateMillis
                                        val selectedDate = selectedMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                                        onBirthdayChange(selectedDate)
                                        showBirthdayPicker = false
                                    }) {
                                        Text(stringResource(R.string.action_save))
                                    }
                                },
                                dismissButton = {
                                    SparelyTextButton(onClick = { showBirthdayPicker = false }) {
                                        Text(stringResource(R.string.action_cancel))
                                    }
                                }
                            ) {
                                DatePicker(state = datePickerState)
                            }
                        }

                        EducationEmploymentCard(
                            selectedEducation = selectedEducation,
                            onEducationSelected = {
                                selectedEducation = it
                                onEducationStatusChange(it)
                            },
                            educationExpanded = educationExpanded,
                            onEducationExpandedChange = { educationExpanded = it },
                            selectedEmployment = selectedEmployment,
                            onEmploymentSelected = {
                                selectedEmployment = it
                                onEmploymentStatusChange(it)
                            },
                            employmentExpanded = employmentExpanded,
                            onEmploymentExpandedChange = { employmentExpanded = it }
                        )

                        RiskLevelCard(current = settings.riskLevel, onRiskChange = onRiskChange)

                        LifeStageCard(age = age, onAgeChange = { updated ->
                            age = updated
                            onAgeChange(updated)
                        })

                        RegionalSettingsCard(
                            settings = settings,
                            onRegionalSettingsChange = onRegionalSettingsChange
                        )

                        SettingsSecurityCard(
                            biometricEnabled = settings.biometricEnabled,
                            onBiometricEnabledChange = onBiometricEnabledChange,
                            onAuthenticateUser = onAuthenticateUser
                        )
                    }

                    SettingsTab.Finances -> {
                        IncomeSettingsCard(
                            schedule = settings.paySchedule,
                            activeSaveRate = activeSaveRate,
                            automationNotes = automationNotes,
                            onScheduleSave = onPayScheduleChange,
                        )

                        SettingsMainAccountCard(
                            monthlyIncomeText = monthlyIncomeText,
                            onMonthlyIncomeTextChange = { monthlyIncomeText = it },
                            onUpdateIncome = { monthlyIncomeText.toSafeDouble()?.let(onMonthlyIncomeChange) },
                            mainAccountBalanceText = mainAccountBalanceText,
                            onMainAccountBalanceTextChange = { mainAccountBalanceText = it },
                            onUpdateBalance = { mainAccountBalanceText.toSafeDouble()?.let(onMainAccountBalanceChange) },
                            includeTax = settings.includeTaxByDefault,
                            onIncludeTaxToggle = onIncludeTaxToggle,
                            minMainAccountBalanceText = minMainAccountBalanceText,
                            onMinMainAccountBalanceTextChange = { minMainAccountBalanceText = it },
                            onUpdateMinBalance = { minMainAccountBalanceText.toSafeDouble()?.let(onMinMainAccountBalanceChange) },
                            smartVaults = smartVaults,
                            savingsAccounts = savingsAccounts,
                            mainOverflowAccountId = settings.mainOverflowAccountId,
                            onMainOverflowAccountIdChange = onMainOverflowAccountIdChange,
                            onManageSavingsAccounts = onManageSavingsAccounts
                        )

                        SettingsSmartSavingsCard(
                            settings = settings,
                            selectedAllocationMode = selectedAllocationMode,
                            onAllocationModeChange = {
                                selectedAllocationMode = it
                                onVaultAllocationModeChange(it)
                            },
                            savingTaxRatePercent = savingTaxRatePercent,
                            onSavingTaxRatePercentChange = { savingTaxRatePercent = it },
                            onSavingTaxRateCommit = onSavingTaxRateChange,
                            activeSavingTaxRate = activeSavingTaxRate,
                            onDynamicSavingTaxToggle = onDynamicSavingTaxToggle
                        )

                        AutomationOverviewCard(
                            settings = settings,
                            activeSaveRate = activeSaveRate,
                            activeSavingTaxRate = activeSavingTaxRate,
                            automationNotes = automationNotes
                        )

                        AutoDepositsCard(
                            enabled = autoDepositsEnabled,
                            checkHour = autoDepositCheckHour,
                            onEnabledChange = onAutoDepositsEnabledChange,
                            onCheckHourChange = onAutoDepositCheckHourChange,
                            onManualTrigger = onManualAutoDepositTrigger
                        )

                        SettingsBudgetCard(
                            autoModeEnabled = autoModeEnabled,
                            onAutoToggle = onAutoToggle,
                            recommendation = recommendation,
                            emergency = emergency,
                            invest = invest,
                            funPercent = funPercent,
                            onEmergencyChange = { emergency = it },
                            onInvestChange = { invest = it },
                            onFunChange = { funPercent = it }
                        )

                        LaunchedEffect(emergency, invest, funPercent, autoModeEnabled) {
                            if (!autoModeEnabled) {
                                onPercentagesChange(
                                    SavingsPercentages(
                                        emergency = emergency.toDouble(),
                                        invest = invest.toDouble(),
                                        `fun` = funPercent.toDouble(),
                                        safeInvestmentSplit = settings.defaultPercentages.safeInvestmentSplit
                                    )
                                )
                            }
                        }

                        PaymentMethodsSettingsCard(
                            paymentMethods = paymentMethods,
                            onAdd = onAddPaymentMethod,
                            onEdit = onEditPaymentMethod,
                            onDelete = onDeletePaymentMethod
                        )
                    }

                    SettingsTab.System -> {
                        ReminderCard(
                            remindersEnabled = remindersEnabled,
                            reminderHour = reminderHour,
                            reminderFrequency = reminderFrequency,
                            onReminderChange = { enabled, hour, days ->
                                remindersEnabled = enabled
                                reminderHour = hour
                                reminderFrequency = days
                                onReminderChange(enabled, hour, days)
                            }
                        )

                        CreditCardReminderCard(
                            enabled = settings.creditCardReminderEnabled,
                            daysBefore = settings.creditCardReminderDaysBefore,
                            hour = settings.creditCardReminderHour,
                            utilizationAlertEnabled = settings.creditCardUtilizationAlertEnabled,
                            utilizationThreshold = settings.creditCardUtilizationThreshold,
                            onReminderChange = onCreditCardReminderChange,
                            onUtilizationChange = onCreditCardUtilizationChange
                        )

                        if (alerts.isNotEmpty()) {
                            ExpressiveCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(R.string.settings_insights_title), style = MaterialTheme.typography.titleMedium)
                                    for (alert in alerts) {
                                        Text(alert.title, style = MaterialTheme.typography.titleSmall)
                                        Text(alert.description, style = MaterialTheme.typography.bodySmall)
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }

                        SettingsDataCard(
                            settings = settings,
                            expensesSize = expenses.size,
                            onResetHistory = onResetHistory,
                            onExpenseHistoryRetentionChange = onExpenseHistoryRetentionChange,
                            brandfetchClientId = brandfetchClientId,
                            onBrandfetchClientIdChange = onBrandfetchClientIdChange,
                            onExportBackupClick = {
                                val timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                                exportLauncher.launch("SparelyBackup_$timestamp.json")
                            },
                            onImportBackupClick = {
                                importLauncher.launch(arrayOf("application/json"))
                            },
                            onExportCsvClick = {
                                val timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                                csvExportLauncher.launch("SparelyExpenses_$timestamp.csv")
                            }
                        )
                        
                        SettingsAutoBackupCard(
                            autoBackupEnabled = settings.autoBackupEnabled,
                            autoBackupFrequencyDays = settings.autoBackupFrequencyDays,
                            lastAutoBackupTimestamp = settings.lastAutoBackupTimestamp,
                            onAutoBackupEnabledChange = onAutoBackupSettingsChange,
                            onBackupNowClick = onBackupNowClick
                        )

                        SettingsSmartTransferCard(
                            minimumAmount = settings.smartTransferMinimumAmount,
                            onMinimumAmountChange = onSmartTransferMinimumChange
                        )
                    }
                    else -> {}  // Assets tab navigates away via LaunchedEffect
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}


@Composable
private fun ProfileCard(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    hasDebts: Boolean,
    onHasDebtsChange: (Boolean) -> Unit,
    emergencyFundText: String,
    onEmergencyFundChange: (String) -> Unit,
    primaryGoal: String,
    onPrimaryGoalChange: (String) -> Unit,
    birthday: LocalDate?,
    effectiveAge: Int,
    onEditBirthday: () -> Unit,
    onClearBirthday: () -> Unit
) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.settings_profile_basics_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            SparelyTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                label = { Text(stringResource(R.string.settings_display_name_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            Column {
                val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
                Text(
                    text = if (birthday == null) stringResource(R.string.settings_birthday_not_set) else stringResource(R.string.settings_birthday_label, birthday.format(formatter)),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SparelyTextButton(onClick = onEditBirthday) { Text(if (birthday == null) stringResource(R.string.settings_set_birthday) else stringResource(R.string.settings_change_birthday)) }
                    if (birthday != null) {
                        SparelyTextButton(onClick = onClearBirthday) { Text(stringResource(R.string.action_clear)) }
                    }
                }
                Text(
                    text = stringResource(R.string.settings_current_age, effectiveAge),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.settings_active_debts), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(R.string.settings_debts_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = hasDebts, onCheckedChange = onHasDebtsChange)
            }
            SparelyTextField(
                value = emergencyFundText,
                onValueChange = { text ->
                    val filtered = text.filterCurrencyInput()
                    onEmergencyFundChange(filtered)
                },
                label = { Text(stringResource(R.string.settings_emergency_fund_label)) },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            SparelyTextField(
                value = primaryGoal,
                onValueChange = onPrimaryGoalChange,
                label = { Text(stringResource(R.string.settings_primary_goal_label)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EducationEmploymentCard(
    selectedEducation: EducationStatus,
    onEducationSelected: (EducationStatus) -> Unit,
    educationExpanded: Boolean,
    onEducationExpandedChange: (Boolean) -> Unit,
    selectedEmployment: EmploymentStatus,
    onEmploymentSelected: (EmploymentStatus) -> Unit,
    employmentExpanded: Boolean,
    onEmploymentExpandedChange: (Boolean) -> Unit
) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.settings_life_context_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            ExposedDropdownMenuBox(
                expanded = educationExpanded,
                onExpandedChange = onEducationExpandedChange
            ) {
                SparelyTextField(
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    value = selectedEducation.displayLabel(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.settings_education_status_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = educationExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = educationExpanded,
                    onDismissRequest = { onEducationExpandedChange(false) }
                ) {
                    for (status in EducationStatus.entries) {
                        DropdownMenuItem(
                            text = { Text(status.displayLabel()) },
                            onClick = {
                                onEducationSelected(status)
                                onEducationExpandedChange(false)
                            }
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(
                expanded = employmentExpanded,
                onExpandedChange = onEmploymentExpandedChange
            ) {
                SparelyTextField(
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    value = selectedEmployment.displayLabel(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.settings_employment_status_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = employmentExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = employmentExpanded,
                    onDismissRequest = { onEmploymentExpandedChange(false) }
                ) {
                    for (status in EmploymentStatus.entries) {
                        DropdownMenuItem(
                            text = { Text(status.displayLabel()) },
                            onClick = {
                                onEmploymentSelected(status)
                                onEmploymentExpandedChange(false)
                            }
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_life_context_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}



@Composable
private fun LifeStageCard(
    age: Int,
    onAgeChange: (Int) -> Unit
) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_profile_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.settings_age_label, age),
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = age.toFloat(),
                onValueChange = { raw ->
                    val coerced = raw.roundToInt().coerceIn(13, 80)
                    if (coerced != age) {
                        onAgeChange(coerced)
                    }
                },
                valueRange = 13f..80f,
                steps = 66
            )
            Text(
                text = stringResource(R.string.settings_minor_user_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RiskLevelCard(
    current: RiskLevel,
    onRiskChange: (RiskLevel) -> Unit
) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_risk_profile_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (level in RiskLevel.values()) {
                    SparelyChip(
                        selected = level == current,
                        onClick = { onRiskChange(level) },
                        label = { Text(when(level) {
                            RiskLevel.CONSERVATIVE -> stringResource(R.string.risk_conservative)
                            RiskLevel.BALANCED -> stringResource(R.string.risk_balanced)
                            RiskLevel.AGGRESSIVE -> stringResource(R.string.risk_aggressive)
                        }) }
                    )
                }
            }
            Text(
                text = when (current) {
                    RiskLevel.CONSERVATIVE -> stringResource(R.string.risk_conservative_desc)
                    RiskLevel.BALANCED -> stringResource(R.string.risk_balanced_desc)
                    RiskLevel.AGGRESSIVE -> stringResource(R.string.risk_aggressive_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReminderCard(
    remindersEnabled: Boolean,
    reminderHour: Int,
    reminderFrequency: Int,
    onReminderChange: (Boolean, Int, Int) -> Unit
) {
    var hour by remember(reminderHour) { mutableStateOf(reminderHour) }
    var frequency by remember(reminderFrequency) { mutableStateOf(reminderFrequency) }
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
                Text(stringResource(R.string.settings_reminders_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Switch(checked = remindersEnabled, onCheckedChange = { enabled ->
                    onReminderChange(enabled, hour, frequency)
                })
            }
            if (remindersEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_reminders_hour, hour))
                    Slider(
                        value = hour.toFloat(),
                        onValueChange = {
                            hour = it.toInt().coerceIn(0, 23)
                            onReminderChange(true, hour, frequency)
                        },
                        valueRange = 0f..23f,
                        steps = 22
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_reminders_frequency, frequency))
                    Slider(
                        value = frequency.toFloat(),
                        onValueChange = {
                            frequency = it.toInt().coerceIn(1, 7)
                            onReminderChange(true, hour, frequency)
                        },
                        valueRange = 1f..7f,
                        steps = 5
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditCardReminderCard(
    enabled: Boolean,
    daysBefore: Int,
    hour: Int,
    utilizationAlertEnabled: Boolean,
    utilizationThreshold: Int,
    onReminderChange: (Boolean, Int, Int) -> Unit,
    onUtilizationChange: (Boolean, Int) -> Unit
) {
    var isEnabled by remember(enabled) { mutableStateOf(enabled) }
    var days by remember(daysBefore) { mutableStateOf(daysBefore) }
    var reminderHour by remember(hour) { mutableStateOf(hour) }
    var isUtilizationEnabled by remember(utilizationAlertEnabled) { mutableStateOf(utilizationAlertEnabled) }
    var threshold by remember(utilizationThreshold) { mutableStateOf(utilizationThreshold) }
    
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Due date reminders section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_credit_card_reminders_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(R.string.settings_credit_card_reminders_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = isEnabled, onCheckedChange = { newEnabled ->
                    isEnabled = newEnabled
                    onReminderChange(newEnabled, days, reminderHour)
                })
            }
            if (isEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_credit_card_days_before, days), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = days.toFloat(),
                        onValueChange = {
                            days = it.toInt().coerceIn(1, 14)
                            onReminderChange(true, days, reminderHour)
                        },
                        valueRange = 1f..14f,
                        steps = 12,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.settings_credit_card_hour, reminderHour), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = reminderHour.toFloat(),
                        onValueChange = {
                            reminderHour = it.toInt().coerceIn(0, 23)
                            onReminderChange(true, days, reminderHour)
                        },
                        valueRange = 0f..23f,
                        steps = 22,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            // Utilization alert section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_credit_card_utilization_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(R.string.settings_credit_card_utilization_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = isUtilizationEnabled, onCheckedChange = { newEnabled ->
                    isUtilizationEnabled = newEnabled
                    onUtilizationChange(newEnabled, threshold)
                })
            }
            if (isUtilizationEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_credit_card_utilization_threshold, threshold),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = threshold.toFloat(),
                        onValueChange = {
                            threshold = it.toInt().coerceIn(1, 100)
                            onUtilizationChange(true, threshold)
                        },
                        valueRange = 1f..100f,
                        steps = 98,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Recommended thresholds
                    Text(
                        text = stringResource(R.string.settings_credit_card_recommended_thresholds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10 to "10%", 30 to "30%", 50 to "50%").forEach { (value, label) ->
                            FilterChip(
                                selected = threshold == value,
                                onClick = {
                                    threshold = value
                                    onUtilizationChange(true, value)
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.settings_credit_card_utilization_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomationOverviewCard(
    settings: SparelySettings,
    activeSaveRate: Double,
    activeSavingTaxRate: Double,
    automationNotes: List<String>
) {
    val schedule = settings.paySchedule
    val payReference = listOf(schedule.lastPayAmount, schedule.defaultNetPay).firstOrNull { it > 0.0 } ?: 0.0
    val estimatedPerPay = (payReference * activeSaveRate).takeIf { payReference > 0.0 }
    val estimatedMonthly = settings.monthlyIncome.takeIf { it > 0.0 }?.let { it * activeSaveRate }

    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.settings_automation_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(if (schedule.dynamicSaveRateEnabled) stringResource(R.string.settings_save_rate_automatic) else stringResource(R.string.settings_save_rate_manual)) },
                    colors = AssistChipDefaults.assistChipColors(disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    border = null
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(if (settings.dynamicSavingTaxEnabled) stringResource(R.string.settings_saving_tax_automatic) else stringResource(R.string.settings_saving_tax_manual_label)) },
                    colors = AssistChipDefaults.assistChipColors(disabledContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
                    border = null
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.settings_paycheck_save_rate_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = activeSaveRate.coerceIn(0.0, 1.0).formatPercent(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (estimatedPerPay != null) {
                    Text(
                        text = stringResource(R.string.settings_estimated_per_pay, estimatedPerPay.formatCurrency()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (estimatedMonthly != null) {
                    Text(
                        text = stringResource(R.string.settings_estimated_monthly, estimatedMonthly.formatCurrency()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.settings_saving_tax_skim_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = activeSavingTaxRate.coerceIn(0.0, 1.0).formatPercent(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.settings_saving_tax_skim_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val notesToShow = automationNotes.take(4)
            if (notesToShow.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.settings_why_these_numbers), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    for (note in notesToShow) {
                        Text(
                            text = "• ${note}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (automationNotes.size > notesToShow.size) {
                        Text(
                            text = stringResource(R.string.settings_automation_insights_trimmed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoDepositsCard(
    enabled: Boolean,
    checkHour: Int,
    onEnabledChange: (Boolean) -> Unit,
    onCheckHourChange: (Int) -> Unit,
    onManualTrigger: () -> Unit
) {
    var showHourPicker by remember { mutableStateOf(false) }
    var selectedHour by remember(checkHour) { mutableStateOf(checkHour) }

    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_auto_deposit_header),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (enabled) stringResource(R.string.settings_auto_deposit_active) else stringResource(R.string.settings_auto_deposit_disabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
            }

            if (enabled) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showHourPicker = true }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_daily_check_time),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${checkHour.toString().padStart(2, '0')}:00",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                SparelyTonalButton(
                    onClick = onManualTrigger,
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        MaterialSymbolIcon(icon = MaterialSymbols.REFRESH,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                ) {
                    Text(stringResource(R.string.settings_auto_deposit_check_now))
                }

                Text(
                    text = stringResource(R.string.settings_auto_deposit_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(R.string.settings_auto_deposit_enable_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showHourPicker) {
        SparelyAlertDialog(
            onDismissRequest = { showHourPicker = false },
            title = { Text(stringResource(R.string.dialog_select_check_time_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.dialog_select_check_time_desc))
                    Slider(
                        value = selectedHour.toFloat(),
                        onValueChange = { selectedHour = it.toInt() },
                        valueRange = 0f..23f,
                        steps = 22
                    )
                    Text(
                        text = "${selectedHour.toString().padStart(2, '0')}:00",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            confirmButton = {
                SparelyButton(
                    onClick = {
                        showHourPicker = false
                        onCheckHourChange(selectedHour)
                    }
                ) {
                    Text(stringResource(R.string.action_done))
                }
            },
            dismissButton = {
                SparelyTextButton(onClick = { showHourPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun IncomeSettingsCard(
    schedule: PayScheduleSettings,
    activeSaveRate: Double,
    automationNotes: List<String>,
    onScheduleSave: (PayScheduleSettings) -> Unit
) {
        var trackingMode by remember(schedule) { mutableStateOf(schedule.trackingMode) }
        var interval by remember(schedule) { mutableStateOf(schedule.interval) }
        var defaultPayText by remember(schedule) {
            mutableStateOf(if (schedule.defaultNetPay > 0.0) schedule.defaultNetPay.formatCurrency("") else "")
        }
        var dynamicSave by remember(schedule) { mutableStateOf(schedule.dynamicSaveRateEnabled) }
        var manualSaveRateSnapshot by remember(schedule) { mutableStateOf((schedule.defaultSaveRate * 100).toFloat()) }
        var saveRate by remember(schedule, activeSaveRate) {
            mutableStateOf(
                if (schedule.dynamicSaveRateEnabled) {
                    (activeSaveRate * 100).toFloat()
                } else {
                    (schedule.defaultSaveRate * 100).toFloat()
                }
            )
        }
        LaunchedEffect(schedule.dynamicSaveRateEnabled, activeSaveRate) {
            if (schedule.dynamicSaveRateEnabled) {
                saveRate = (activeSaveRate * 100).toFloat()
            }
        }
        var weeklyDay by remember(schedule) { mutableStateOf(schedule.weeklyDayOfWeek) }
        var semiDay1 by remember(schedule) { mutableStateOf(schedule.semiMonthlyDay1) }
        var semiDay2 by remember(schedule) { mutableStateOf(schedule.semiMonthlyDay2) }
        var monthlyDay by remember(schedule) { mutableStateOf(schedule.monthlyDay) }
        var customDays by remember(schedule) { mutableStateOf(schedule.customDaysBetween ?: 14) }
        var nextPayDate by remember(schedule) { mutableStateOf(schedule.nextPayDate) }
        var showNextDatePicker by remember { mutableStateOf(false) }
        var intervalExpanded by remember { mutableStateOf(false) }
        var autoDistribute by remember(schedule) { mutableStateOf(schedule.autoDistributeToVaults) }
        var autoPending by remember(schedule) { mutableStateOf(schedule.autoCreatePendingTransfers) }

        fun buildSchedule(nextDateOverride: LocalDate? = nextPayDate): PayScheduleSettings {
            val defaultPayAmount = defaultPayText.toSafeDouble()?.coerceAtLeast(0.0) ?: 0.0
            val effectiveNext = if (trackingMode == IncomeTrackingMode.MANUAL_PER_PAYCHECK) null else nextDateOverride
            return schedule.copy(
                trackingMode = trackingMode,
                interval = interval,
                defaultNetPay = defaultPayAmount,
                defaultSaveRate = (manualSaveRateSnapshot / 100f).toDouble().coerceIn(0.0, 1.0),
                weeklyDayOfWeek = weeklyDay,
                semiMonthlyDay1 = semiDay1.coerceIn(1, 28),
                semiMonthlyDay2 = semiDay2.coerceIn(1, 28),
                monthlyDay = monthlyDay.coerceIn(1, 28),
                customDaysBetween = if (interval == PayInterval.CUSTOM) customDays.coerceAtLeast(1) else null,
                nextPayDate = effectiveNext,
                autoDistributeToVaults = autoDistribute,
                autoCreatePendingTransfers = autoPending,
                dynamicSaveRateEnabled = dynamicSave
            )
        }

    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.settings_income_paydays_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val modes = listOf(
                        IncomeTrackingMode.MANUAL_PER_PAYCHECK to stringResource(R.string.income_mode_manual),
                        IncomeTrackingMode.SCHEDULED to stringResource(R.string.income_mode_scheduled),
                        IncomeTrackingMode.HYBRID to stringResource(R.string.income_mode_hybrid)
                    )
                    for ((mode, label) in modes) {
                        SparelyChip(
                            selected = trackingMode == mode,
                            onClick = {
                                trackingMode = mode
                                if (mode == IncomeTrackingMode.MANUAL_PER_PAYCHECK) {
                                    nextPayDate = null
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = intervalExpanded,
                    onExpandedChange = { intervalExpanded = !intervalExpanded }
                ) {
                    SparelyTextField(
                        value = when (interval) {
                            PayInterval.WEEKLY -> stringResource(R.string.pay_interval_weekly)
                            PayInterval.BIWEEKLY -> stringResource(R.string.pay_interval_biweekly)
                            PayInterval.SEMI_MONTHLY -> stringResource(R.string.pay_interval_semi_monthly)
                            PayInterval.MONTHLY -> stringResource(R.string.pay_interval_monthly)
                            PayInterval.CUSTOM -> stringResource(R.string.pay_interval_custom)
                        },
                        onValueChange = {},
                        label = { Text(stringResource(R.string.settings_pay_interval_label)) },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = intervalExpanded,
                        onDismissRequest = { intervalExpanded = false }
                    ) {
                        for (option in PayInterval.values()) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (option) {
                                            PayInterval.WEEKLY -> stringResource(R.string.pay_interval_weekly)
                                            PayInterval.BIWEEKLY -> stringResource(R.string.pay_interval_biweekly)
                                            PayInterval.SEMI_MONTHLY -> stringResource(R.string.pay_interval_semi_monthly)
                                            PayInterval.MONTHLY -> stringResource(R.string.pay_interval_monthly)
                                            PayInterval.CUSTOM -> stringResource(R.string.pay_interval_custom)
                                        }
                                    )
                                },
                                onClick = {
                                    interval = option
                                    intervalExpanded = false
                                }
                            )
                        }
                    }
                }

                SparelyTextField(
                    value = defaultPayText,
                    onValueChange = { text ->
                        defaultPayText = text.filterCurrencyInput()
                    },
                    label = { Text(stringResource(R.string.settings_pay_amount_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_automate_savings_rate), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = dynamicSave,
                        onCheckedChange = { enabled ->
                            dynamicSave = enabled
                            if (enabled) {
                                saveRate = (activeSaveRate * 100).toFloat()
                            } else {
                                saveRate = manualSaveRateSnapshot
                            }
                            onScheduleSave(buildSchedule())
                        }
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (dynamicSave) {
                            stringResource(R.string.settings_active_savings_rate_display, activeSaveRate.coerceIn(0.0, 1.0).formatPercent())
                        } else {
                            stringResource(R.string.settings_default_savings_rate_display, (saveRate / 100f).toDouble().formatPercent())
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = saveRate,
                        onValueChange = { updated ->
                            if (!dynamicSave) {
                                val coerced = updated.coerceIn(0f, 60f)
                                saveRate = coerced
                                manualSaveRateSnapshot = coerced
                            }
                        },
                        valueRange = 0f..60f,
                        enabled = !dynamicSave,
                        steps = 59
                    )
                    if (dynamicSave) {
                        Text(
                            text = stringResource(R.string.settings_savings_rate_auto_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.settings_savings_rate_manual_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (dynamicSave && automationNotes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.settings_automation_insights_label), style = MaterialTheme.typography.titleSmall)
                        val limitedNotes = automationNotes.take(4)
                        for (note in limitedNotes) {
                            Text(
                                text = "• $note",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                when (interval) {
                    PayInterval.WEEKLY, PayInterval.BIWEEKLY -> {
                        val days = DayOfWeek.values()
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (day in days) {
                                SparelyChip(
                                    selected = weeklyDay == day,
                                    onClick = { weeklyDay = day },
                                    label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) }
                                )
                            }
                        }
                    }

                    PayInterval.SEMI_MONTHLY -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SparelyTextField(
                                value = semiDay1.toString(),
                                onValueChange = { value ->
                                    semiDay1 = value.toIntOrNull()?.coerceIn(1, 28) ?: semiDay1
                                },
                                label = { Text(stringResource(R.string.settings_semi_monthly_day1)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            SparelyTextField(
                                value = semiDay2.toString(),
                                onValueChange = { value ->
                                    semiDay2 = value.toIntOrNull()?.coerceIn(1, 28) ?: semiDay2
                                },
                                label = { Text(stringResource(R.string.settings_semi_monthly_day2)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    PayInterval.MONTHLY -> {
                        SparelyTextField(
                            value = monthlyDay.toString(),
                            onValueChange = { value ->
                                monthlyDay = value.toIntOrNull()?.coerceIn(1, 28) ?: monthlyDay
                            },
                            label = { Text(stringResource(R.string.settings_monthly_day_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    PayInterval.CUSTOM -> {
                        SparelyTextField(
                            value = customDays.toString(),
                            onValueChange = { value ->
                                customDays = value.toIntOrNull()?.coerceAtLeast(1) ?: customDays
                            },
                            label = { Text(stringResource(R.string.settings_custom_days_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_next_payday_label), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = nextPayDate?.format(DateTimeFormatter.ofPattern("MMM d, yyyy")) ?: stringResource(R.string.common_not_set),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SparelyTextButton(onClick = { showNextDatePicker = true }) {
                        Text(stringResource(R.string.action_pick_date))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_auto_distribute_label), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = autoDistribute, onCheckedChange = { autoDistribute = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_auto_pending_label), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = autoPending, onCheckedChange = { autoPending = it })
                }

                SparelyButton(onClick = { onScheduleSave(buildSchedule()) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_save_pay_defaults))
                }

                if (schedule.lastPayDate != null) {
                    val lastDate = schedule.lastPayDate
                    val lastAmount = schedule.lastPayAmount.formatCurrency()
                    Text(
                        text = stringResource(R.string.settings_last_logged_pay_info, lastAmount, lastDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showNextDatePicker) {
            val initialMillis = nextPayDate.toSafeDatePickerMillis()
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
            LaunchedEffect(initialMillis) {
                if (initialMillis != null && datePickerState.selectedDateMillis != initialMillis) {
                    datePickerState.selectedDateMillis = initialMillis
                }
            }
            DatePickerDialog(
                onDismissRequest = { showNextDatePicker = false },
                confirmButton = {
                    SparelyTextButton(onClick = {
                        val selected = datePickerState.selectedDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        nextPayDate = selected
                        if (trackingMode != IncomeTrackingMode.MANUAL_PER_PAYCHECK) {
                            onScheduleSave(buildSchedule(selected))
                        }
                        showNextDatePicker = false
                    }) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    SparelyTextButton(onClick = { showNextDatePicker = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

}

private val monthDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

@Composable
private fun EducationStatus.displayLabel(): String = when (this) {
    EducationStatus.HIGH_SCHOOL -> stringResource(R.string.edu_high_school)
    EducationStatus.UNIVERSITY -> stringResource(R.string.edu_university)
    EducationStatus.GRADUATED -> stringResource(R.string.edu_graduated)
    EducationStatus.OTHER -> stringResource(R.string.edu_other)
}

@Composable
private fun EmploymentStatus.displayLabel(): String = when (this) {
    EmploymentStatus.STUDENT -> stringResource(R.string.emp_student)
    EmploymentStatus.PART_TIME -> stringResource(R.string.emp_part_time)
    EmploymentStatus.FULL_TIME, EmploymentStatus.EMPLOYED -> stringResource(R.string.emp_employed)
    EmploymentStatus.SELF_EMPLOYED -> stringResource(R.string.emp_self_employed)
    EmploymentStatus.UNEMPLOYED -> stringResource(R.string.emp_unemployed)
    EmploymentStatus.RETIRED -> stringResource(R.string.emp_retired)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionalSettingsCard(
    settings: SparelySettings,
    onRegionalSettingsChange: (String, String, String, Double?) -> Unit
) {
    val regionalSettings = settings.regionalSettings
    val currentCountry = regionalSettings.getCountryConfig()
    
    var showCountryPicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var customTaxRate by remember(regionalSettings.customIncomeTaxRate) {
        mutableStateOf(regionalSettings.customIncomeTaxRate?.let { (it * 100).toInputString() } ?: "")
    }
    
    val allCountries = listOf(
        com.example.sparely.domain.model.CountryProfiles.UNITED_STATES,
        com.example.sparely.domain.model.CountryProfiles.UNITED_KINGDOM,
        com.example.sparely.domain.model.CountryProfiles.CANADA,
        com.example.sparely.domain.model.CountryProfiles.FRANCE,
        com.example.sparely.domain.model.CountryProfiles.GERMANY,
        com.example.sparely.domain.model.CountryProfiles.SPAIN,
        com.example.sparely.domain.model.CountryProfiles.JAPAN,
        com.example.sparely.domain.model.CountryProfiles.AUSTRALIA,
        com.example.sparely.domain.model.CountryProfiles.INDIA,
        com.example.sparely.domain.model.CountryProfiles.MEXICO,
        com.example.sparely.domain.model.CountryProfiles.BRAZIL
    )
    
    // Available languages for the selected country
    val availableLanguages = when (regionalSettings.countryCode) {
        "CA" -> listOf("en" to "English", "fr" to "Français")
        "BE" -> listOf("en" to "English", "fr" to "Français", "nl" to "Nederlands")
        "CH" -> listOf("en" to "English", "fr" to "Français", "de" to "Deutsch", "it" to "Italiano")
        else -> listOf(
            "en" to "English",
            "es" to "Español",
            "fr" to "Français",
            "de" to "Deutsch",
            "pt" to "Português",
            "ja" to "日本語"
        )
    }
    
    // Available currencies
    val availableCurrencies = listOf("USD", "CAD", "GBP", "EUR", "JPY", "AUD", "INR", "MXN", "BRL")
    
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_regional_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Country Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_country_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = currentCountry?.countryName ?: regionalSettings.countryCode,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SparelyTonalButton(onClick = { showCountryPicker = true }) {
                    Text("Change")
                }
            }
            
            // Language Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_language_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = availableLanguages.find { it.first == regionalSettings.languageCode }?.second 
                            ?: currentCountry?.languageName
                            ?: regionalSettings.languageCode,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SparelyTonalButton(onClick = { showLanguagePicker = true }) {
                    Text("Change")
                }
            }
            
            // Currency Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_currency_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${regionalSettings.currencyCode} (${getCurrencySymbol(regionalSettings.currencyCode)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SparelyTonalButton(onClick = { showCurrencyPicker = true }) {
                    Text(stringResource(R.string.action_change))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Custom Tax Rate
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_custom_tax_rate_header),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Override the default tax rate for ${currentCountry?.countryName ?: "your country"}. " +
                            "Default: ${currentCountry?.taxConfig?.incomeTaxRate?.times(100)?.toInt() ?: 0}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SparelyTextField(
                        value = customTaxRate,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.matches(Regex("^\\d{0,2}([.,]\\d{0,2})?$"))) {
                                customTaxRate = newValue
                            }
                        },
                        label = { Text(stringResource(R.string.settings_tax_rate_field_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    SparelyTonalButton(
                        onClick = {
                            val taxRate = customTaxRate.toSafeDouble()?.div(100)
                            onRegionalSettingsChange(
                                regionalSettings.countryCode,
                                regionalSettings.languageCode,
                                regionalSettings.currencyCode,
                                taxRate
                            )
                        }
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                    if (customTaxRate.isNotEmpty()) {
                        SparelyTonalButton(
                            onClick = {
                                customTaxRate = ""
                                onRegionalSettingsChange(
                                    regionalSettings.countryCode,
                                    regionalSettings.languageCode,
                                    regionalSettings.currencyCode,
                                    null
                                )
                            }
                        ) {
                            Text(stringResource(R.string.action_reset))
                        }
                    }
                }
            }
        }
    }
    
    // Country Picker Dialog
    if (showCountryPicker) {
        SparelyAlertDialog(
            onDismissRequest = { showCountryPicker = false },
            title = { Text(stringResource(R.string.dialog_select_country_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (country in allCountries) {
                        ExpressiveCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onRegionalSettingsChange(
                                        country.countryCode,
                                        country.languageCode,
                                        country.defaultCurrency,
                                        regionalSettings.customIncomeTaxRate
                                    )
                                    showCountryPicker = false
                                },
                            containerColor = if (country.countryCode == regionalSettings.countryCode)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = country.countryName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${country.defaultCurrency} • ${country.languageName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                SparelyTextButton(onClick = { showCountryPicker = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
    
    // Language Picker Dialog
    if (showLanguagePicker) {
        SparelyAlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            title = { Text(stringResource(R.string.dialog_select_language_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((code, name) in availableLanguages) {
                        ExpressiveCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onRegionalSettingsChange(
                                        regionalSettings.countryCode,
                                        code,
                                        regionalSettings.currencyCode,
                                        regionalSettings.customIncomeTaxRate
                                    )
                                    showLanguagePicker = false
                                },
                            containerColor = if (code == regionalSettings.languageCode)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = name,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                SparelyTextButton(onClick = { showLanguagePicker = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
    
    // Currency Picker Dialog
    if (showCurrencyPicker) {
        SparelyAlertDialog(
            onDismissRequest = { showCurrencyPicker = false },
            title = { Text(stringResource(R.string.dialog_select_currency_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (currency in availableCurrencies) {
                        ExpressiveCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onRegionalSettingsChange(
                                        regionalSettings.countryCode,
                                        regionalSettings.languageCode,
                                        currency,
                                        regionalSettings.customIncomeTaxRate
                                    )
                                    showCurrencyPicker = false
                                },
                            containerColor = if (currency == regionalSettings.currencyCode)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = "$currency (${getCurrencySymbol(currency)})",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                SparelyTextButton(onClick = { showCurrencyPicker = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

private fun getCurrencySymbol(currencyCode: String): String = when (currencyCode) {
    "USD", "CAD", "AUD", "MXN" -> "$"
    "GBP" -> "£"
    "EUR" -> "€"
    "JPY" -> "¥"
    "INR" -> "₹"
    "BRL" -> "R$"
    else -> currencyCode
}
@Composable
private fun PaymentMethodsSettingsCard(
    paymentMethods: List<com.example.sparely.domain.model.PaymentMethod>,
    onAdd: (com.example.sparely.domain.model.PaymentMethod) -> Unit,
    onEdit: (com.example.sparely.domain.model.PaymentMethod) -> Unit,
    onDelete: (com.example.sparely.domain.model.PaymentMethod) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMethod by remember { mutableStateOf<com.example.sparely.domain.model.PaymentMethod?>(null) }

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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MaterialSymbolIcon(icon = MaterialSymbols.PAYMENTS, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.settings_payment_methods_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = stringResource(R.string.settings_payment_methods_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.IconButton(onClick = { showAddDialog = true }) {
                    MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = stringResource(R.string.action_add_method_desc), tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            if (paymentMethods.isEmpty()) {
                Text(stringResource(R.string.settings_no_payment_methods), style = MaterialTheme.typography.bodySmall)
            } else {
                paymentMethods.forEach { method ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingMethod = method }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        com.example.sparely.ui.components.PaymentMethodIcon(
                           method = method,
                           modifier = Modifier.size(40.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(method.name, style = MaterialTheme.typography.bodyMedium)
                            if (method.isCreditCard) {
                                val utilization = (method.utilizationPercent * 100).toInt()
                                val utilizationColor = when {
                                    method.isUtilizationHealthy -> MaterialTheme.colorScheme.primary
                                    method.isUtilizationWarning -> MaterialTheme.colorScheme.warning
                                    else -> MaterialTheme.colorScheme.error
                                }
                                val limit = String.format("%.0f", method.creditLimit ?: 0.0)
                                Text(
                                    stringResource(R.string.settings_credit_card_summary_line, method.currentBalance.formatCurrency(), method.creditLimit?.formatCurrency() ?: 0.0.formatCurrency(), utilization),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = utilizationColor
                                )
                            } else {
                                Text(
                                    if (method.type == com.example.sparely.domain.model.PaymentMethodType.CASH) stringResource(R.string.payment_method_type_cash) else stringResource(R.string.payment_method_type_card),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (method.isDefault) {
                            SparelyChip(
                                selected = true, 
                                onClick = {}, 
                                label = { Text(stringResource(R.string.common_default)) }
                            )
                        }
                    }
                    if (method != paymentMethods.last()) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAddDialog || editingMethod != null) {
        val isEditing = editingMethod != null
        var name by remember { mutableStateOf(editingMethod?.name ?: "") }
        var type by remember { mutableStateOf(editingMethod?.type ?: com.example.sparely.domain.model.PaymentMethodType.CARD) }
        var defaultDeduct by remember { mutableStateOf(editingMethod?.defaultDeductFromMainAccount ?: true) }
        var isDefault by remember { mutableStateOf(editingMethod?.isDefault ?: false) }
        
        // Credit card specific fields
        var isCreditCard by remember { mutableStateOf(editingMethod?.isCreditCard ?: false) }
        var creditLimitText by remember { mutableStateOf(editingMethod?.creditLimit?.toInputString() ?: "") }
        var billingCycleDayText by remember { mutableStateOf(editingMethod?.billingCycleDay?.toString() ?: "") }
        
        // Reset default deduct when type changes if creating new
        LaunchedEffect(type) {
             if (!isEditing) {
                 defaultDeduct = type == com.example.sparely.domain.model.PaymentMethodType.CARD
             }
        }
        
        // Auto-set deduct to false for credit cards
        LaunchedEffect(isCreditCard) {
            if (isCreditCard) {
                defaultDeduct = false
            }
        }

        SparelyAlertDialog(
            onDismissRequest = { 
                showAddDialog = false 
                editingMethod = null
            },
            title = { Text(if (isEditing) "Edit Payment Method" else "Add Payment Method") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SparelyTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name (e.g. Visa, Cash)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Column {
                        Text("Type", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SparelyChip(
                                selected = type == com.example.sparely.domain.model.PaymentMethodType.CARD,
                                onClick = { type = com.example.sparely.domain.model.PaymentMethodType.CARD },
                                label = { Text("Card/Digital") }
                            )
                            SparelyChip(
                                selected = type == com.example.sparely.domain.model.PaymentMethodType.CASH,
                                onClick = { 
                                    type = com.example.sparely.domain.model.PaymentMethodType.CASH
                                    isCreditCard = false // Can't be credit card if cash
                                },
                                label = { Text("Cash") }
                            )
                        }
                    }
                    
                    // Credit Card Toggle - only show for CARD type
                    if (type == com.example.sparely.domain.model.PaymentMethodType.CARD) {
                        HorizontalDivider()
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MaterialSymbolIcon(icon = MaterialSymbols.CREDIT_CARD, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                                    Text("This is a Credit Card", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    "Track balance and credit utilization",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = isCreditCard, onCheckedChange = { isCreditCard = it })
                        }
                        
                        // Credit card specific fields
                        if (isCreditCard) {
                            SparelyTextField(
                                value = creditLimitText,
                                onValueChange = { creditLimitText = it.filterCurrencyInput() },
                                label = { Text("Credit Limit") },
                                leadingIcon = { Text("$", style = MaterialTheme.typography.bodyLarge) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            SparelyTextField(
                                value = billingCycleDayText,
                                onValueChange = { 
                                    val filtered = it.filter { c -> c.isDigit() }
                                    val num = filtered.toIntOrNull()
                                    billingCycleDayText = if (num != null && num in 1..31) filtered else filtered.take(2)
                                },
                                label = { Text("Billing Cycle Day (1-31)") },
                                supportingText = { Text("Day of month when statement closes") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            // Show current balance for editing (read-only info)
                            if (isEditing && editingMethod?.currentBalance ?: 0.0 > 0) {
                                ExpressiveCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ExpressiveShapes.medium,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Current Balance", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            text = editingMethod?.currentBalance?.formatCurrency() ?: 0.0.formatCurrency(),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val utilPct = (editingMethod?.utilizationPercent ?: 0.0) * 100
                                        val utilizationColor = when {
                                            editingMethod?.isUtilizationHealthy == true -> MaterialTheme.colorScheme.primary
                                            editingMethod?.isUtilizationWarning == true -> MaterialTheme.colorScheme.warning
                                            else -> MaterialTheme.colorScheme.error
                                        }
                                        Text(
                                            utilPct.formatPercent(1) + " utilization",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = utilizationColor
                                        )
                                        if (editingMethod?.isUtilizationWarning == true || editingMethod?.isUtilizationDanger == true) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                if (editingMethod?.isUtilizationDanger == true)
                                                    "⚠️ High utilization may hurt your credit score"
                                                else
                                                    "💡 Keep utilization under 30% for best credit score",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = utilizationColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        HorizontalDivider()
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Deduct from Main Account by default?", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (isCreditCard) "Credit cards don't deduct immediately."
                                else "Turn off for Credit Cards, on for Debit/Cash.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = defaultDeduct, 
                            onCheckedChange = { defaultDeduct = it },
                            enabled = !isCreditCard // Disable for credit cards
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                         Text("Set as default method", style = MaterialTheme.typography.bodyMedium)
                         Switch(checked = isDefault, onCheckedChange = { isDefault = it })
                    }
                }
            },
            confirmButton = {
                SparelyButton(
                    onClick = {
                        val method = com.example.sparely.domain.model.PaymentMethod(
                            id = editingMethod?.id ?: 0,
                            name = name,
                            type = type,
                            defaultDeductFromMainAccount = if (isCreditCard) false else defaultDeduct,
                            isDefault = isDefault,
                            iconName = if (type == com.example.sparely.domain.model.PaymentMethodType.CASH) "payments" else "credit_card",
                            isCreditCard = isCreditCard && type == com.example.sparely.domain.model.PaymentMethodType.CARD,
                            creditLimit = if (isCreditCard) creditLimitText.toSafeDouble() else null,
                            currentBalance = editingMethod?.currentBalance ?: 0.0,
                            billingCycleDay = if (isCreditCard) billingCycleDayText.toIntOrNull()?.takeIf { it in 1..31 } else null,
                            lastPaymentDate = editingMethod?.lastPaymentDate,
                            lastPaymentAmount = editingMethod?.lastPaymentAmount
                        )
                        if (isEditing) onEdit(method) else onAdd(method)
                        showAddDialog = false
                        editingMethod = null
                    },
                    enabled = name.isNotBlank() && (!isCreditCard || creditLimitText.toSafeDouble() != null)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                     if (isEditing) {
                        SparelyTextButton(onClick = {
                            editingMethod?.let { onDelete(it) }
                            editingMethod = null
                        }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                     }
                    SparelyTextButton(onClick = {
                        showAddDialog = false
                        editingMethod = null
                    }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

