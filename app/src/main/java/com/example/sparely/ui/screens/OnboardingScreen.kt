package com.example.sparely.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.ExpressiveShapes
import com.example.sparely.ui.components.*
import com.example.sparely.ui.utils.toSafeDatePickerMillis
import com.example.sparely.ui.utils.filterCurrencyInput
import com.example.sparely.ui.utils.toSafeDouble
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.sparely.domain.logic.SavingsAdvisor
import com.example.sparely.domain.model.*
import com.sparely.app.R
import java.time.Instant
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.utils.roundToTwoDecimals
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: (UserProfileSetup) -> Unit,
    onImportData: (android.net.Uri) -> Unit,
    onSkip: () -> Unit,
    snackbarHostState: SnackbarHostState? = null
) {
    var currentStep by remember { mutableStateOf(0) }
    var selectedCountry by remember { mutableStateOf<CountryConfig?>(null) }
    var userName by remember { mutableStateOf("") }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onImportData(it) }
    }
    var age by remember { mutableStateOf("30") }
    var birthday by remember { mutableStateOf<LocalDate?>(null) }
    var monthlyIncome by remember { mutableStateOf("") }
    var selectedRiskLevel by remember { mutableStateOf(RiskLevel.BALANCED) }
    var primaryGoal by remember { mutableStateOf("") }
    var hasDebts by remember { mutableStateOf(false) }
    var currentEmergencyFund by remember { mutableStateOf("0") }
    var educationStatus by remember { mutableStateOf(EducationStatus.OTHER) }
    var employmentStatus by remember { mutableStateOf(EmploymentStatus.EMPLOYED) }
    var livingSituation by remember { mutableStateOf(LivingSituation.OTHER) }
    var occupation by remember { mutableStateOf("") }
    var mainAccountBalance by remember { mutableStateOf("") }
    var savingsAccountBalance by remember { mutableStateOf("") }
    var vaultsBalanceInput by remember { mutableStateOf("") }
    val vaultDrafts = remember { mutableStateListOf<VaultDraft>() }
    var nextDraftId by remember { mutableStateOf(0L) }
    val subscriptionDrafts = remember { mutableStateListOf<SubscriptionDraft>() }
    var nextSubscriptionId by remember { mutableStateOf(0L) }
    var reminderEnabled by remember { mutableStateOf(true) }
    var reminderFrequency by remember { mutableStateOf(7) }
    var reminderHour by remember { mutableStateOf(20) }

    val totalSteps = 8  // Increased from 7 to 8
    val vaultsStepIndex = 6  // Shifted from 5 to 6
    val derivedAge = birthday?.let { ChronoUnit.YEARS.between(it, LocalDate.now()).coerceAtLeast(0L).toInt() }
    val ageValue = derivedAge ?: age.toIntOrNull() ?: 30
    val monthlyIncomeValue = monthlyIncome.toSafeDouble() ?: 0.0
    val emergencyFundValue = currentEmergencyFund.toSafeDouble() ?: 0.0

    fun allocateDraftId(): Long {
        val id = nextDraftId
        nextDraftId += 1
        return id
    }

    fun removeVaultAt(index: Int) {
        if (index < 0 || index >= vaultDrafts.size) return
        vaultDrafts.removeAt(index)
    }

    fun addVaultDraft(template: VaultDraft = VaultDraft.blank()) {
        val newDraft = template.copy(id = allocateDraftId())
        vaultDrafts.add(newDraft)
    }

    fun updateVaultDraft(index: Int, updated: VaultDraft) {
        if (index < 0 || index >= vaultDrafts.size) return
        vaultDrafts[index] = updated
    }

    fun allocateSubscriptionId(): Long {
        val id = nextSubscriptionId
        nextSubscriptionId += 1
        return id
    }

    fun addSubscriptionDraft() {
        subscriptionDrafts.add(SubscriptionDraft(id = allocateSubscriptionId(), name = "", amount = ""))
    }

    fun updateSubscriptionName(id: Long, value: String) {
        val index = subscriptionDrafts.indexOfFirst { it.id == id }
        if (index >= 0) {
            val draft = subscriptionDrafts[index]
            subscriptionDrafts[index] = draft.copy(name = value)
        }
    }

    fun updateSubscriptionAmount(id: Long, value: String) {
        val index = subscriptionDrafts.indexOfFirst { it.id == id }
        if (index >= 0) {
            val draft = subscriptionDrafts[index]
            subscriptionDrafts[index] = draft.copy(amount = value)
        }
    }

    fun removeSubscription(id: Long) {
        subscriptionDrafts.removeAll { it.id == id }
    }

    LaunchedEffect(
        currentStep,
        ageValue,
        educationStatus,
        employmentStatus,
        hasDebts,
        monthlyIncome,
        currentEmergencyFund
    ) {
        if (currentStep == vaultsStepIndex && vaultDrafts.isEmpty()) {
            val templates = recommendedVaultDrafts(
                age = ageValue,
                educationStatus = educationStatus,
                employmentStatus = employmentStatus,
                hasDebts = hasDebts,
                monthlyIncome = monthlyIncomeValue,
                currentEmergencyFund = emergencyFundValue
            )
            templates.forEach { template ->
                addVaultDraft(template)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (snackbarHostState != null) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .zIndex(1f)
            )
        }
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Progress indicator (skip on country selection step)
            if (currentStep > 0) {
                OnboardingProgressBar(
                    currentStep = currentStep - 1,  // Adjust for zero-indexed country step
                    totalSteps = totalSteps - 1,
                    onBack = { if (currentStep > 0) currentStep-- }
                )
            }

            // Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                    },
                    label = "onboarding_step"
                ) { step ->
                    when (step) {
                        0 -> CountrySelectionStep(
                            selectedCountry = selectedCountry,
                            onCountrySelected = { selectedCountry = it },
                            onNext = { currentStep = 1 }
                        )
                        1 -> WelcomeStep(
                            countryConfig = selectedCountry,
                            onNext = { currentStep = 2 },
                            onImport = { importLauncher.launch(arrayOf("*/*")) },
                            onSkip = onSkip
                        )
                        2 -> NameStep(
                            name = userName,
                            onNameChange = { userName = it },
                            onNext = { currentStep = 3 }
                        )
                        3 -> IncomeStep(
                            income = monthlyIncome,
                            age = age,
                            birthday = birthday,
                            onIncomeChange = { monthlyIncome = it },
                            onAgeChange = {
                                age = it
                                if (birthday != null) {
                                    birthday = null
                                }
                            },
                            onBirthdayChange = { selected ->
                                birthday = selected
                                selected?.let {
                                    val computedAge = ChronoUnit.YEARS.between(it, LocalDate.now()).coerceAtLeast(0L).toInt()
                                    age = computedAge.toString()
                                }
                            },
                            onNext = { currentStep = 4 }
                        )
                        4 -> RiskLevelStep(
                            selectedRisk = selectedRiskLevel,
                            onRiskSelected = { selectedRiskLevel = it },
                            onNext = { currentStep = 5 }
                        )
                        5 -> FinancialSituationStep(
                            hasDebts = hasDebts,
                            onDebtsChange = { hasDebts = it },
                            emergencyFund = currentEmergencyFund,
                            onEmergencyFundChange = { currentEmergencyFund = it },
                            educationStatus = educationStatus,
                            onEducationStatusChange = { educationStatus = it },
                            employmentStatus = employmentStatus,
                            onEmploymentStatusChange = { employmentStatus = it },
                            livingSituation = livingSituation,
                            onLivingSituationChange = { livingSituation = it },
                            occupation = occupation,
                            onOccupationChange = { occupation = it },
                            mainAccountBalance = mainAccountBalance,
                            onMainAccountBalanceChange = { mainAccountBalance = it },
                            savingsAccountBalance = savingsAccountBalance,
                            onSavingsAccountBalanceChange = { savingsAccountBalance = it },
                            vaultsBalance = vaultsBalanceInput,
                            onVaultsBalanceChange = { vaultsBalanceInput = it },
                            subscriptions = subscriptionDrafts,
                            onAddSubscription = { addSubscriptionDraft() },
                            onSubscriptionNameChange = { id, value -> updateSubscriptionName(id, value) },
                            onSubscriptionAmountChange = { id, value -> updateSubscriptionAmount(id, value) },
                            onRemoveSubscription = { id -> removeSubscription(id) },
                            age = ageValue,
                            onNext = { currentStep = 6 }
                        )
                        6 -> SmartVaultsStep(
                            drafts = vaultDrafts,
                            onDraftChange = { index, draft -> updateVaultDraft(index, draft) },
                            onRemove = { index -> removeVaultAt(index) },
                            onAddVault = { addVaultDraft() },
                            onNext = { currentStep = 7 }
                        )
                        7 -> TransferReminderStep(
                            reminderEnabled = reminderEnabled,
                            onReminderEnabledChange = { reminderEnabled = it },
                            reminderFrequency = reminderFrequency,
                            onReminderFrequencyChange = { reminderFrequency = it },
                            reminderHour = reminderHour,
                            onReminderHourChange = { reminderHour = it },
                            pendingVaults = vaultDrafts,
                            onNext = { currentStep = 8 }
                        )
                        8 -> GoalStep(
                            goal = primaryGoal,
                            onGoalChange = { primaryGoal = it },
                            onComplete = {
                                val vaults = vaultDrafts.mapNotNull { draft -> draft.toSetup() }
                                val reminderPreference = TransferReminderPreference(
                                    enabled = reminderEnabled,
                                    frequencyDays = reminderFrequency,
                                    hourOfDay = reminderHour
                                )
                                val sanitizedOccupation = occupation.trim().ifBlank { null }
                                val mainBalance = mainAccountBalance.toSafeDouble()?.coerceAtLeast(0.0) ?: 0.0
                                val savingsBalance = savingsAccountBalance.toSafeDouble()?.coerceAtLeast(0.0) ?: 0.0
                                val vaultsBalance = vaultsBalanceInput.toSafeDouble()?.coerceAtLeast(0.0) ?: 0.0
                                val subscriptions = subscriptionDrafts.mapNotNull { draft ->
                                    val name = draft.name.trim()
                                    val amount = draft.amount.toSafeDouble()?.coerceAtLeast(0.0) ?: 0.0
                                    if (name.isEmpty() || amount <= 0.0) {
                                        null
                                    } else {
                                        OnboardingSubscription(name = name, amount = amount)
                                    }
                                }
                                val profile = UserProfileSetup(
                                    name = userName.ifBlank { null },
                                    age = age.toIntOrNull() ?: 30,
                                    monthlyIncome = monthlyIncome.toSafeDouble() ?: 4500.0,
                                    riskLevel = selectedRiskLevel,
                                    hasDebts = hasDebts,
                                    currentEmergencyFund = currentEmergencyFund.toSafeDouble() ?: 0.0,
                                    primaryGoal = primaryGoal.ifBlank { null },
                                    smartVaults = vaults,
                                    transferReminder = reminderPreference,
                                    educationStatus = educationStatus,
                                    employmentStatus = employmentStatus,
                                    livingSituation = livingSituation,
                                    occupation = sanitizedOccupation,
                                    mainAccountBalance = mainBalance,
                                    savingsAccountBalance = savingsBalance,
                                    vaultsBalance = vaultsBalance,
                                    subscriptions = subscriptions,
                                    birthday = birthday
                                )
                                onComplete(profile)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingProgressBar(
    currentStep: Int,
    totalSteps: Int,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                MaterialSymbolIcon(icon = MaterialSymbols.ARROW_BACK, contentDescription = stringResource(R.string.common_back))
            }
            Text(
                text = stringResource(R.string.onboarding_step_text, currentStep, totalSteps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
        progress = { currentStep.toFloat() / totalSteps },
        modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        )
    }
}

@Composable
fun WelcomeStep(
    countryConfig: CountryConfig?,
    onNext: () -> Unit,
    onImport: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "💰",
            style = MaterialTheme.typography.displayLarge,
            fontSize = MaterialTheme.typography.displayLarge.fontSize * 2
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.onboarding_welcome_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureHighlight(
                icon = "🎯",
                title = stringResource(R.string.onboarding_feature_allocation_title),
                description = stringResource(R.string.onboarding_feature_allocation_desc)
            )
            
            FeatureHighlight(
                icon = "📊",
                title = stringResource(R.string.onboarding_feature_health_title),
                description = stringResource(R.string.onboarding_feature_health_desc)
            )
            
            FeatureHighlight(
                icon = "🏆",
                title = stringResource(R.string.onboarding_feature_challenges_title),
                description = stringResource(R.string.onboarding_feature_challenges_desc)
            )
            
            FeatureHighlight(
                icon = "💡",
                title = stringResource(R.string.onboarding_feature_budgeting_title),
                description = stringResource(R.string.onboarding_feature_budgeting_desc)
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        SparelyButton(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text(stringResource(R.string.onboarding_get_started), style = MaterialTheme.typography.titleMedium)
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        SparelyTonalButton(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.onboarding_import_backup))
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        SparelyTextButton(onClick = onSkip) {
            Text(stringResource(R.string.onboarding_skip_setup))
        }
    }
}

@Composable
fun FeatureHighlight(
    icon: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(end = 16.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TransferReminderStep(
    reminderEnabled: Boolean,
    onReminderEnabledChange: (Boolean) -> Unit,
    reminderFrequency: Int,
    onReminderFrequencyChange: (Int) -> Unit,
    reminderHour: Int,
    onReminderHourChange: (Int) -> Unit,
    pendingVaults: List<VaultDraft>,
    onNext: () -> Unit
) {
    val reminderOptions = listOf(3, 7, 14, 30)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = stringResource(R.string.onboarding_reminder_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_reminder_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = reminderEnabled, onCheckedChange = onReminderEnabledChange)
            Spacer(modifier = Modifier.width(12.dp))
            Text(stringResource(R.string.onboarding_reminder_enable))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.onboarding_reminder_frequency_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in reminderOptions) {
                SparelyChip(
                    selected = reminderFrequency == option,
                    onClick = { onReminderFrequencyChange(option) },
                    enabled = reminderEnabled,
                    label = { Text(stringResource(R.string.onboarding_reminder_frequency_option, option)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.onboarding_reminder_time_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = reminderHour.toFloat(),
            onValueChange = { value -> onReminderHourChange(value.roundToInt().coerceIn(0, 23)) },
            valueRange = 0f..23f,
            steps = 22,
            enabled = reminderEnabled
        )
        Text(
            text = stringResource(R.string.onboarding_reminder_time_display, reminderHour.toString().padStart(2, '0')),
            style = MaterialTheme.typography.bodyMedium
        )

        if (pendingVaults.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.onboarding_reminder_target_vaults, pendingVaults.joinToString { it.name.ifBlank { "Vault" } }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        SparelyButton(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text(stringResource(R.string.onboarding_continue), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SmartVaultsStep(
        drafts: List<VaultDraft>,
        onDraftChange: (Int, VaultDraft) -> Unit,
        onRemove: (Int) -> Unit,
        onAddVault: () -> Unit,
        onNext: () -> Unit
    ) {
        val canProceed = drafts.isNotEmpty() && drafts.all { draft ->
            draft.name.isNotBlank() && draft.targetAmount.toSafeDouble()?.let { it > 0.0 } == true
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = stringResource(R.string.onboarding_vaults_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_vaults_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            for ((index, draft) in drafts.withIndex()) {
                SmartVaultCard(
                    draft = draft,
                    onDraftChange = { updated -> onDraftChange(index, updated) },
                    onRemove = { onRemove(index) },
                    showRemove = drafts.size > 1
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            SparelyTonalButton(onClick = onAddVault) {
                Text(stringResource(R.string.onboarding_add_vault))
            }

            Spacer(modifier = Modifier.height(32.dp))

            SparelyButton(
                onClick = onNext,
                enabled = canProceed,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Text("Continue", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SmartVaultCard(
        draft: VaultDraft,
        onDraftChange: (VaultDraft) -> Unit,
        onRemove: () -> Unit,
        showRemove: Boolean
    ) {
        ExpressiveCard(modifier = Modifier.fillMaxWidth(), tonalElevation = 6.dp, contentPadding = 20.dp) {
            Column {
                if (draft.recommended) {
                    AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.onboarding_vault_recommended)) })
                    Spacer(modifier = Modifier.height(12.dp))
                }

                val icons = listOf(
                    MaterialSymbols.ACCOUNT_BALANCE_WALLET,
                    MaterialSymbols.SAVINGS,
                    MaterialSymbols.DIRECTIONS_CAR,
                    MaterialSymbols.HOME,
                    MaterialSymbols.FLIGHT,
                    MaterialSymbols.SCHOOL,
                    MaterialSymbols.SHOPPING_BAG,
                    MaterialSymbols.PETS,
                    MaterialSymbols.RESTAURANT,
                    MaterialSymbols.COMPUTER,
                )

                Text(
                    text = stringResource(R.string.onboarding_vault_icon_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (iconRes in icons) {
                        val iconStableName = MaterialSymbols.getNameByIcon(iconRes)
                        val isSelected = (draft.iconName == null && iconRes == MaterialSymbols.ACCOUNT_BALANCE_WALLET) || (draft.iconName == iconStableName)
                        ExpressiveCard(
                            onClick = { onDraftChange(draft.copy(iconName = iconStableName)) },
                            modifier = Modifier.size(36.dp),
                            shape = ExpressiveShapes.medium,
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentPadding = 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                MaterialSymbolIcon(
                                    icon = iconRes,
                                    contentDescription = null,
                                    size = 20.dp,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                SparelyTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    label = { Text(stringResource(R.string.onboarding_vault_name_label)) },
                    leadingIcon = { 
                        val displayIcon = MaterialSymbols.getIconByName(draft.iconName) ?: MaterialSymbols.ACCOUNT_BALANCE_WALLET
                        MaterialSymbolIcon(icon = displayIcon, contentDescription = null) 
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SparelyTextField(
                        value = draft.targetAmount,
                        onValueChange = { onDraftChange(draft.copy(targetAmount = it.filterCurrencyInput())) },
                        label = { Text(stringResource(R.string.onboarding_vault_target_label)) },
                        modifier = Modifier.weight(1f),
                        leadingIcon = { MaterialSymbolIcon(icon = MaterialSymbols.FLAG, contentDescription = null) }
                    )
                    SparelyTextField(
                        value = draft.currentBalance,
                        onValueChange = { onDraftChange(draft.copy(currentBalance = it.filterCurrencyInput())) },
                        label = { Text(stringResource(R.string.onboarding_vault_balance_label)) },
                        modifier = Modifier.weight(1f),
                        leadingIcon = { MaterialSymbolIcon(icon = MaterialSymbols.ATTACH_MONEY, contentDescription = null) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.onboarding_vault_priority_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (priority in VaultPriority.entries) {
                        SparelyChip(
                            selected = draft.priority == priority,
                            onClick = { onDraftChange(draft.copy(priority = priority)) },
                            label = { Text(priority.displayName()) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.onboarding_vault_focus_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Show only core vault types during onboarding to keep choices simple
                    val allowedTypes = listOf(VaultType.GOAL, VaultType.EMERGENCY, VaultType.INVESTMENT)
                    for (type in allowedTypes) {
                        SparelyChip(
                            selected = draft.type == type,
                            onClick = { onDraftChange(draft.copy(type = type)) },
                            label = { Text(type.displayName()) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.onboarding_vault_allocation_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SparelyChip(
                        selected = draft.allocationMode == VaultAllocationMode.DYNAMIC_AUTO,
                        onClick = {
                            onDraftChange(draft.copy(allocationMode = VaultAllocationMode.DYNAMIC_AUTO, manualPercent = ""))
                        },
                        label = { Text(stringResource(R.string.onboarding_vault_allocation_dynamic)) }
                    )
                    SparelyChip(
                        selected = draft.allocationMode == VaultAllocationMode.MANUAL,
                        onClick = {
                            onDraftChange(draft.copy(allocationMode = VaultAllocationMode.MANUAL))
                        },
                        label = { Text(stringResource(R.string.onboarding_vault_allocation_manual)) }
                    )
                }

                if (draft.allocationMode == VaultAllocationMode.MANUAL) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SparelyTextField(
                        value = draft.manualPercent,
                        onValueChange = { onDraftChange(draft.copy(manualPercent = it.filterCurrencyInput())) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text(stringResource(R.string.onboarding_vault_manual_percent_label)) },
                        placeholder = { Text(stringResource(R.string.onboarding_vault_manual_percent_placeholder)) },
                        trailingIcon = { Text("%") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                SparelyTextField(
                    value = draft.savingTaxRate,
                    onValueChange = { onDraftChange(draft.copy(savingTaxRate = it.filterCurrencyInput())) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(stringResource(R.string.onboarding_vault_tax_boost_label)) },
                    placeholder = { Text(stringResource(R.string.onboarding_vault_tax_boost_placeholder)) },
                    trailingIcon = { Text("%") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (showRemove) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SparelyTextButton(onClick = onRemove) {
                        Text(stringResource(R.string.onboarding_remove_vault))
                    }
                }
            }
        }
    }

    private data class VaultDraft(
        val id: Long,
        val name: String,
        val targetAmount: String,
        val currentBalance: String,
        val priority: VaultPriority,
        val type: VaultType,
        val allocationMode: VaultAllocationMode,
        val manualPercent: String,
        val savingTaxRate: String,
        val recommended: Boolean,
        val iconName: String? = null
    ) {
        companion object {
            fun blank(): VaultDraft = VaultDraft(
                id = -1,
                name = "New vault",
                targetAmount = "2000",
                currentBalance = "0",
                priority = VaultPriority.MEDIUM,
                type = VaultType.SHORT_TERM,
                allocationMode = VaultAllocationMode.DYNAMIC_AUTO,
                manualPercent = "",
                savingTaxRate = "",
                recommended = false,
                iconName = "account_balance_wallet"
            )
        }

        fun toSetup(): SmartVaultSetup? {
            val trimmedName = name.trim()
            if (trimmedName.isEmpty()) return null
            val target = targetAmount.toSafeDouble()?.coerceAtLeast(0.0) ?: return null
            if (target <= 0.0) return null
            val balance = currentBalance.toSafeDouble()?.coerceAtLeast(0.0) ?: 0.0
            val manualShare = if (allocationMode == VaultAllocationMode.MANUAL) {
                manualPercent.toSafeDouble()?.div(100.0)?.coerceIn(0.0, 1.0)
            } else {
                null
            }
            val taxOverride = savingTaxRate.toSafeDouble()?.div(100.0)?.coerceIn(0.0, 1.0)
            return SmartVaultSetup(
                name = trimmedName,
                targetAmount = target,
                currentBalance = balance,
                priority = priority,
                type = type,
                allocationMode = allocationMode,
                manualAllocationPercent = manualShare,
                savingTaxRateOverride = taxOverride,
                iconName = iconName
            )
        }
    }

    private fun recommendedVaultDrafts(
        age: Int,
        educationStatus: EducationStatus,
        employmentStatus: EmploymentStatus,
        hasDebts: Boolean,
        monthlyIncome: Double,
        currentEmergencyFund: Double
    ): List<VaultDraft> {
        val setups = SavingsAdvisor.recommendedVaults(
            age = age,
            educationStatus = educationStatus,
            employmentStatus = employmentStatus,
            hasDebts = hasDebts,
            emergencyFund = currentEmergencyFund,
            monthlyIncome = monthlyIncome
        )
        return setups.map { it.toDraft(recommended = true) }
    }

    private data class SubscriptionDraft(
        val id: Long,
        val name: String,
        val amount: String
    )

    private fun SmartVaultSetup.toDraft(recommended: Boolean): VaultDraft = VaultDraft(
        id = -1,
        name = name,
        targetAmount = targetAmount.toInputText(defaultWhenZero = ""),
        currentBalance = currentBalance.toInputText(defaultWhenZero = "0"),
        priority = priority,
        type = type,
        allocationMode = allocationMode,
        manualPercent = manualAllocationPercent?.let { (it * 100).toPercentageInput() } ?: "",
        savingTaxRate = savingTaxRateOverride?.let { (it * 100).toPercentageInput() } ?: "",
        recommended = recommended,
        iconName = iconName
    )

@Composable
private fun EducationStatus.displayName(): String = when (this) {
    EducationStatus.HIGH_SCHOOL -> stringResource(R.string.edu_high_school)
    EducationStatus.UNIVERSITY -> stringResource(R.string.edu_university)
    EducationStatus.GRADUATED -> stringResource(R.string.edu_graduated)
    EducationStatus.OTHER -> stringResource(R.string.edu_other)
}

@Composable
private fun EmploymentStatus.displayName(): String = when (this) {
    EmploymentStatus.STUDENT -> stringResource(R.string.emp_student)
    EmploymentStatus.PART_TIME -> stringResource(R.string.emp_part_time)
    EmploymentStatus.FULL_TIME, EmploymentStatus.EMPLOYED -> stringResource(R.string.emp_employed)
    EmploymentStatus.SELF_EMPLOYED -> stringResource(R.string.emp_self_employed)
    EmploymentStatus.UNEMPLOYED -> stringResource(R.string.emp_unemployed)
    EmploymentStatus.RETIRED -> stringResource(R.string.emp_retired)
}

@Composable
private fun LivingSituation.displayName(): String = when (this) {
    LivingSituation.WITH_PARENTS -> stringResource(R.string.living_with_parents)
    LivingSituation.RENTING -> stringResource(R.string.living_renting)
    LivingSituation.HOMEOWNER -> stringResource(R.string.living_homeowner)
    LivingSituation.OTHER -> stringResource(R.string.living_other)
}




private fun Double.toInputText(defaultWhenZero: String = ""): String {
    if (this <= 0.0) return defaultWhenZero
    val displayValue = if (this > -0.005 && this <= 0.0) 0.0 else this
    return if (displayValue % 1.0 == 0.0) {
        displayValue.toInt().toString()
    } else {
        displayValue.formatCurrency("", 2)
    }
}

private fun Double.toPercentageInput(): String {
    val displayValue = if (this > -0.00005 && this <= 0.0) 0.0 else this
    return if (displayValue % 1.0 == 0.0) {
        displayValue.toInt().toString()
    } else {
        displayValue.formatPercent(1).removeSuffix("%")
    }
}

@Composable
fun NameStep(
    name: String,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "👋",
            style = MaterialTheme.typography.displayLarge,
            fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.5f
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.onboarding_name_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = stringResource(R.string.onboarding_name_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        SparelyTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.onboarding_name_label)) },
            placeholder = { Text(stringResource(R.string.onboarding_name_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                MaterialSymbolIcon(icon = MaterialSymbols.PERSON, contentDescription = null)
            }
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        SparelyButton(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text(stringResource(R.string.onboarding_continue), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeStep(
    income: String,
    age: String,
    birthday: LocalDate?,
    onIncomeChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onBirthdayChange: (LocalDate?) -> Unit,
    onNext: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    val computedAge = remember(birthday) {
        birthday?.let { ChronoUnit.YEARS.between(it, LocalDate.now()).coerceAtLeast(0L).toInt() }
    }
    var showBirthdayPicker by remember { mutableStateOf(false) }
    val birthdayLabel = birthday?.format(dateFormatter) ?: stringResource(R.string.onboarding_income_birthday_label)
    val isAgeValid = (birthday != null && computedAge != null) || age.toIntOrNull() != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "💵",
            style = MaterialTheme.typography.displayLarge,
            fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.5f
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.onboarding_income_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "This helps us provide personalized savings recommendations",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        SparelyTextField(
            value = income,
            onValueChange = { onIncomeChange(it.filterCurrencyInput()) },
            label = { Text(stringResource(R.string.onboarding_income_label)) },
            placeholder = { Text(stringResource(R.string.onboarding_income_placeholder)) },
            prefix = { Text("$") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                MaterialSymbolIcon(icon = MaterialSymbols.ATTACH_MONEY, contentDescription = null)
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        SparelyTonalButton(
            onClick = { showBirthdayPicker = true },
            modifier = Modifier.fillMaxWidth(),
            icon = {
                MaterialSymbolIcon(icon = MaterialSymbols.CALENDAR_MONTH, contentDescription = null)
            }
        ) {
            Text(birthdayLabel)
        }

        if (birthday != null && computedAge != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_income_age_milestone_desc, computedAge ?: 0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            SparelyTextButton(
                onClick = { onBirthdayChange(null) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.onboarding_income_clear_birthday))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val ageHelper = if (birthday != null) {
            stringResource(R.string.onboarding_income_age_helper_auto)
        } else {
            stringResource(R.string.onboarding_income_age_helper_manual)
        }

        SparelyTextField(
            value = age,
            onValueChange = onAgeChange,
            label = { Text(stringResource(R.string.onboarding_income_age_label)) },
            placeholder = { Text(stringResource(R.string.onboarding_income_age_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = birthday == null,
            leadingIcon = {
                MaterialSymbolIcon(icon = MaterialSymbols.CAKE, contentDescription = null)
            },
            supportingText = { Text(ageHelper) }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        ExpressiveCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.LOCK,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.onboarding_income_privacy_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        SparelyButton(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth(),
            enabled = income.toSafeDouble() != null && isAgeValid
        ) {
            Text(stringResource(R.string.onboarding_continue), style = MaterialTheme.typography.titleMedium)
        }
    }

    if (showBirthdayPicker) {
        val initialMillis = birthday.toSafeDatePickerMillis()
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
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                SparelyTextButton(onClick = { showBirthdayPicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun RiskLevelStep(
    selectedRisk: RiskLevel,
    onRiskSelected: (RiskLevel) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "📈",
            style = MaterialTheme.typography.displayLarge,
            fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.5f
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.onboarding_risk_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = stringResource(R.string.onboarding_risk_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        RiskLevelOption(
            icon = "🛡️",
            title = stringResource(R.string.onboarding_risk_conservative_title),
            description = stringResource(R.string.onboarding_risk_conservative_desc),
            isSelected = selectedRisk == RiskLevel.CONSERVATIVE,
            onClick = { onRiskSelected(RiskLevel.CONSERVATIVE) }
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        RiskLevelOption(
            icon = "⚖️",
            title = stringResource(R.string.onboarding_risk_balanced_title),
            description = stringResource(R.string.onboarding_risk_balanced_desc),
            isSelected = selectedRisk == RiskLevel.BALANCED,
            onClick = { onRiskSelected(RiskLevel.BALANCED) }
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        RiskLevelOption(
            icon = "🚀",
            title = stringResource(R.string.onboarding_risk_aggressive_title),
            description = stringResource(R.string.onboarding_risk_aggressive_desc),
            isSelected = selectedRisk == RiskLevel.AGGRESSIVE,
            onClick = { onRiskSelected(RiskLevel.AGGRESSIVE) }
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        SparelyButton(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text("Continue", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun RiskLevelOption(
    icon: String,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ExpressiveCard(
        onClick = onClick,
        containerColor = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.CHECK_CIRCLE,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun FinancialSituationStep(
    hasDebts: Boolean,
    onDebtsChange: (Boolean) -> Unit,
    emergencyFund: String,
    onEmergencyFundChange: (String) -> Unit,
    educationStatus: EducationStatus,
    onEducationStatusChange: (EducationStatus) -> Unit,
    employmentStatus: EmploymentStatus,
    onEmploymentStatusChange: (EmploymentStatus) -> Unit,
    livingSituation: LivingSituation,
    onLivingSituationChange: (LivingSituation) -> Unit,
    occupation: String,
    onOccupationChange: (String) -> Unit,
    mainAccountBalance: String,
    onMainAccountBalanceChange: (String) -> Unit,
    savingsAccountBalance: String,
    onSavingsAccountBalanceChange: (String) -> Unit,
    vaultsBalance: String,
    onVaultsBalanceChange: (String) -> Unit,
    subscriptions: List<SubscriptionDraft>,
    onAddSubscription: () -> Unit,
    onSubscriptionNameChange: (Long, String) -> Unit,
    onSubscriptionAmountChange: (Long, String) -> Unit,
    onRemoveSubscription: (Long) -> Unit,
    age: Int,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "💼",
            style = MaterialTheme.typography.displayLarge,
            fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.5f
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.onboarding_financial_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = stringResource(R.string.onboarding_financial_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        // Education Status (only show if age < 30)
        if (age < 30) {
            Text(
                text = stringResource(R.string.onboarding_financial_education_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (status in EducationStatus.entries) {
                    SparelyChip(
                        selected = educationStatus == status,
                        onClick = { onEducationStatusChange(status) },
                        label = { Text(status.displayName()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Employment Status
        Text(
            text = stringResource(R.string.onboarding_financial_employment_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (status in EmploymentStatus.entries) {
                SparelyChip(
                    selected = employmentStatus == status,
                    onClick = { onEmploymentStatusChange(status) },
                    label = { Text(status.displayName()) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.onboarding_living_situation_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in LivingSituation.entries) {
                SparelyChip(
                    selected = livingSituation == option,
                    onClick = { onLivingSituationChange(option) },
                    label = { Text(option.displayName()) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SparelyTextField(
            value = occupation,
            onValueChange = onOccupationChange,
            label = { Text(stringResource(R.string.onboarding_occupation_label)) },
            placeholder = { Text(stringResource(R.string.onboarding_occupation_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                MaterialSymbolIcon(icon = MaterialSymbols.WORK, contentDescription = null)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.onboarding_financial_debts_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SparelyChip(
                selected = !hasDebts,
                onClick = { onDebtsChange(false) },
                label = { Text(stringResource(R.string.common_no)) },
                modifier = Modifier.weight(1f),
                leadingIcon = if (!hasDebts) {
                    { MaterialSymbolIcon(icon = MaterialSymbols.CHECK, contentDescription = null) }
                } else null
            )
            SparelyChip(
                selected = hasDebts,
                onClick = { onDebtsChange(true) },
                label = { Text(stringResource(R.string.common_yes)) },
                modifier = Modifier.weight(1f),
                leadingIcon = if (hasDebts) {
                    { MaterialSymbolIcon(icon = MaterialSymbols.CHECK, contentDescription = null) }
                } else null
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        // Tailored suggestion for young part-time users still living with parents
        val showSmallFundSuggestion = age <= 18 && livingSituation == LivingSituation.WITH_PARENTS && employmentStatus == EmploymentStatus.PART_TIME
        if (showSmallFundSuggestion) {
            ExpressiveCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.onboarding_small_fund_suggestion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SparelyTonalButton(onClick = { onEmergencyFundChange("250") }) {
                            Text(stringResource(R.string.onboarding_apply_suggestion, "250"))
                        }
                        SparelyTextButton(onClick = { /* user can still input their own value */ }) {
                            Text(stringResource(R.string.onboarding_keep_value))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        SparelyTextField(
            value = emergencyFund,
            onValueChange = { onEmergencyFundChange(it.filterCurrencyInput()) },
            label = { Text(stringResource(R.string.onboarding_emergency_fund_label)) },
            placeholder = { Text("0") },
            prefix = { Text(stringResource(R.string.currency_symbol)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                MaterialSymbolIcon(icon = MaterialSymbols.ACCOUNT_BALANCE_WALLET, contentDescription = null)
            },
            supportingText = {
                Text(stringResource(R.string.onboarding_emergency_fund_helper))
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.onboarding_balances_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_balances_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        SparelyTextField(
            value = mainAccountBalance,
            onValueChange = { onMainAccountBalanceChange(it.filterCurrencyInput()) },
            label = { Text(stringResource(R.string.onboarding_main_balance_label)) },
            placeholder = { Text("0") },
            prefix = { Text(stringResource(R.string.currency_symbol)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                MaterialSymbolIcon(icon = MaterialSymbols.ACCOUNT_BALANCE, contentDescription = null)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        SparelyTextField(
            value = savingsAccountBalance,
            onValueChange = { onSavingsAccountBalanceChange(it.filterCurrencyInput()) },
            label = { Text(stringResource(R.string.onboarding_savings_balance_label)) },
            placeholder = { Text("0") },
            prefix = { Text(stringResource(R.string.currency_symbol)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                MaterialSymbolIcon(icon = MaterialSymbols.SAVINGS, contentDescription = null)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        SparelyTextField(
            value = vaultsBalance,
            onValueChange = { onVaultsBalanceChange(it.filterCurrencyInput()) },
            label = { Text(stringResource(R.string.onboarding_vaults_balance_label)) },
            placeholder = { Text("0") },
            prefix = { Text(stringResource(R.string.currency_symbol)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                MaterialSymbolIcon(icon = MaterialSymbols.LOCK, contentDescription = null)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.onboarding_subscriptions_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_subscriptions_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (subscriptions.isEmpty()) {
            Text(
                text = stringResource(R.string.onboarding_no_subscriptions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            for ((index, draft) in subscriptions.withIndex()) {
                ExpressiveCard(modifier = Modifier.fillMaxWidth(), tonalElevation = 4.dp, contentPadding = 16.dp) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_subscription_item_title, index + 1),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            IconButton(onClick = { onRemoveSubscription(draft.id) }) {
                                MaterialSymbolIcon(icon = MaterialSymbols.DELETE, contentDescription = stringResource(R.string.onboarding_remove_subscription))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        SparelyTextField(
                            value = draft.name,
                            onValueChange = { onSubscriptionNameChange(draft.id, it) },
                            label = { Text(stringResource(R.string.common_name)) },
                            placeholder = { Text(stringResource(R.string.onboarding_subscription_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SparelyTextField(
                            value = draft.amount,
                            onValueChange = { onSubscriptionAmountChange(draft.id, it.filterCurrencyInput()) },
                            label = { Text(stringResource(R.string.onboarding_subscription_amount_label)) },
                            placeholder = { Text("0") },
                            prefix = { Text(stringResource(R.string.currency_symbol)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                MaterialSymbolIcon(icon = MaterialSymbols.ATTACH_MONEY, contentDescription = null)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        SparelyTonalButton(
            onClick = onAddSubscription,
            modifier = Modifier.fillMaxWidth(),
            icon = {
                MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null)
            }
        ) {
            Text(stringResource(R.string.onboarding_add_subscription))
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        SparelyButton(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text(stringResource(R.string.onboarding_continue), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun GoalStep(
    goal: String,
    onGoalChange: (String) -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "🎯",
            style = MaterialTheme.typography.displayLarge,
            fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.5f
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.onboarding_goal_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = stringResource(R.string.onboarding_goal_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        SparelyTextField(
            value = goal,
            onValueChange = onGoalChange,
            label = { Text(stringResource(R.string.onboarding_goal_label)) },
            placeholder = { Text(stringResource(R.string.onboarding_goal_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                MaterialSymbolIcon(icon = MaterialSymbols.FLAG, contentDescription = null)
            },
            supportingText = {
                Text(stringResource(R.string.onboarding_goal_helper))
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        val accentColor = MaterialTheme.colorScheme.primary
        val accentContainer = MaterialTheme.colorScheme.primaryContainer
        ExpressiveCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = accentContainer
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.CHECK_CIRCLE,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.onboarding_all_set_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.onboarding_all_set_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        SparelyButton(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth(),
            containerColor = accentColor,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = {
                MaterialSymbolIcon(icon = MaterialSymbols.ROCKET_LAUNCH, contentDescription = null)
            }
        ) {
            Text(stringResource(R.string.onboarding_start_saving), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun CountrySelectionStep(
    selectedCountry: CountryConfig?,
    onCountrySelected: (CountryConfig) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        MaterialSymbolIcon(icon = MaterialSymbols.PUBLIC,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.onboarding_country_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = stringResource(R.string.onboarding_country_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Country selection cards
        for (country in CountryProfiles.ALL_COUNTRIES) {
            val isSelected = selectedCountry?.countryCode == country.countryCode
            ExpressiveCard(
                onClick = { onCountrySelected(country) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
                contentPadding = 16.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = country.countryName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${country.languageName} • ${MaterialTheme.colorScheme.onSurfaceVariant.let { if (isSelected) stringResource(R.string.common_selected) else CurrencyPresets.getByCode(country.defaultCurrency)?.symbol ?: country.defaultCurrency }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (isSelected) {
                        MaterialSymbolIcon(icon = MaterialSymbols.CHECK_CIRCLE,
                            contentDescription = stringResource(R.string.common_selected),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        SparelyButton(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth(),
            enabled = selectedCountry != null
        ) {
            Text(stringResource(R.string.onboarding_continue), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun WelcomeStep(
    countryConfig: CountryConfig?,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MaterialSymbolIcon(icon = MaterialSymbols.SAVINGS,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = buildString {
                append(stringResource(R.string.onboarding_welcome_simple))
                countryConfig?.let {
                    append(" ")
                    append(stringResource(R.string.onboarding_welcome_for_country, it.countryName))
                }
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Show country-specific welcome message
        countryConfig?.let { config ->
            ExpressiveCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_country_customized_note),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.onboarding_country_currency_stat, CurrencyPresets.getByCode(config.defaultCurrency)?.name ?: config.defaultCurrency),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.onboarding_country_tax_stat, (config.taxConfig.incomeTaxRate * 100).toInt()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.onboarding_country_savings_stat, config.savingsNorms.recommendedEmergencyMonths),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        SparelyButton(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text(stringResource(R.string.onboarding_get_started), style = MaterialTheme.typography.titleMedium)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        SparelyTextButton(onClick = onSkip) {
            Text(stringResource(R.string.onboarding_skip_setup))
        }
    }
}
