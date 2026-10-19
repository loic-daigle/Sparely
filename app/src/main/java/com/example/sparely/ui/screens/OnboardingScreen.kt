package com.example.sparely.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.sparely.AppLanguage
import com.example.sparely.domain.model.CountryConfig
import com.example.sparely.domain.model.CountryProfiles
import com.example.sparely.domain.model.CurrencyPresets
import com.example.sparely.domain.model.SmartVaultSetup
import com.example.sparely.domain.model.UserProfileSetup
import com.example.sparely.domain.model.RiskLevel
import com.example.sparely.domain.model.TransferReminderPreference
import com.example.sparely.domain.model.VaultPriority
import com.example.sparely.domain.model.VaultType
import com.example.sparely.ui.components.*
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.SparelyBrandSurface
import com.example.sparely.ui.utils.filterAmountExpressionInput
import com.example.sparely.ui.utils.toInputString
import com.example.sparely.ui.utils.toSafeDatePickerMillis
import com.example.sparely.ui.utils.toSafeDouble
import com.sparely.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

private const val STEP_WELCOME = 0
private const val STEP_ABOUT = 1
private const val STEP_MONEY = 2
private const val STEP_VAULT = 3
/** Steps after the welcome, as the progress bar counts them. */
private const val COUNTED_STEPS = 3

/**
 * First-run setup in four short screens: welcome (with country and language), about you, your money,
 * and a first emergency-fund vault. Every question is optional; the rest of the profile (risk level,
 * debts, job, housing, reminders) starts from sensible defaults and is edited later in Settings.
 */
@Composable
fun OnboardingScreen(
    onComplete: (UserProfileSetup) -> Unit,
    onImportData: (android.net.Uri) -> Unit,
    onSkip: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    /** Country, language and currency; saving the language switches the app to it. */
    onRegionChosen: (countryCode: String, languageCode: String, currencyCode: String) -> Unit = { _, _, _ -> }
) {
    // Saveable state: a language switch restarts the screen, and so can rotation or the system
    // reclaiming the app mid-setup; none of them may lose the step or what was typed
    var step by rememberSaveable { mutableIntStateOf(STEP_WELCOME) }
    var countryCode by rememberSaveable {
        mutableStateOf(
            CountryProfiles.getByCode(Locale.getDefault().country)?.countryCode
                ?: CountryProfiles.UNITED_STATES.countryCode
        )
    }
    var language by rememberSaveable { mutableStateOf(AppLanguage.deviceLanguage() ?: "en") }
    var name by rememberSaveable { mutableStateOf("") }
    var birthday by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var income by rememberSaveable { mutableStateOf("") }
    var mainBalance by rememberSaveable { mutableStateOf("") }
    var savingsBalance by rememberSaveable { mutableStateOf("") }
    var createVault by rememberSaveable { mutableStateOf(true) }
    // Null until edited: the target then follows the suggestion as income changes
    var vaultTarget by rememberSaveable { mutableStateOf<String?>(null) }

    val country = CountryProfiles.getByCode(countryCode) ?: CountryProfiles.UNITED_STATES
    val incomeValue = income.toSafeDouble()?.coerceAtLeast(0.0) ?: 0.0
    val suggestedTarget = emergencyFundSuggestion(incomeValue, country)
    val emergencyName = stringResource(R.string.onboarding_vault_emergency_name)

    fun saveRegion(newCountry: CountryConfig = country, newLanguage: String = language) {
        onRegionChosen(newCountry.countryCode, newLanguage, newCountry.defaultCurrency)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportData)
    }

    fun finish() {
        val main = mainBalance.toSafeDouble()?.coerceAtLeast(0.0) ?: 0.0
        val savings = savingsBalance.toSafeDouble()?.coerceAtLeast(0.0) ?: 0.0
        val target = (vaultTarget ?: suggestedTarget.toInputString(0)).toSafeDouble()?.coerceAtLeast(0.0) ?: 0.0
        val vaults = if (createVault && target > 0.0) {
            listOf(
                SmartVaultSetup(
                    name = emergencyName,
                    targetAmount = target,
                    priority = VaultPriority.HIGH,
                    type = VaultType.EMERGENCY,
                    iconName = "savings"
                )
            )
        } else {
            emptyList()
        }
        val age = birthday?.let { ChronoUnit.YEARS.between(it, LocalDate.now()).toInt().coerceAtLeast(0) }
        onComplete(
            UserProfileSetup(
                name = name.trim().ifBlank { null },
                age = age ?: 30,
                // Left empty, income stays 0: no budgets are made up from a guessed income
                monthlyIncome = incomeValue,
                riskLevel = RiskLevel.BALANCED,
                currentEmergencyFund = savings,
                smartVaults = vaults,
                createStarterVaults = createVault,
                transferReminder = TransferReminderPreference(enabled = true, frequencyDays = 7, hourOfDay = 20),
                mainAccountBalance = main,
                savingsAccountBalance = savings,
                birthday = birthday
            )
        )
    }

    BackHandler(enabled = step > STEP_WELCOME) { step-- }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // The app draws edge to edge: keep clear of the system bars and the keyboard, so each
            // step's scroll area ends above the keyboard and its button stays reachable
            .safeDrawingPadding()
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
        Column(modifier = Modifier.fillMaxSize()) {
            if (step > STEP_WELCOME) {
                OnboardingTopBar(step = step, onBack = { step-- })
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    // Slide in the direction of travel so going back feels like going back
                    if (targetState > initialState) {
                        (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                    }
                },
                modifier = Modifier.weight(1f),
                label = "onboarding_step"
            ) { current ->
                when (current) {
                    STEP_WELCOME -> WelcomeStep(
                        country = country,
                        language = language,
                        onCountryChange = {
                            countryCode = it.countryCode
                            saveRegion(newCountry = it)
                        },
                        // Saved right away, so this screen switches to the language at once
                        onLanguageChange = {
                            language = it
                            saveRegion(newLanguage = it)
                        },
                        onStart = {
                            saveRegion()
                            step = STEP_ABOUT
                        },
                        onImport = {
                            saveRegion()
                            importLauncher.launch(arrayOf("*/*"))
                        },
                        onSkip = {
                            saveRegion()
                            onSkip()
                        }
                    )
                    STEP_ABOUT -> AboutYouStep(
                        name = name,
                        onNameChange = { name = it },
                        birthday = birthday,
                        onBirthdayChange = { birthday = it },
                        onNext = { step = STEP_MONEY }
                    )
                    STEP_MONEY -> MoneyStep(
                        currencySymbol = CurrencyPresets.getByCode(country.defaultCurrency)?.symbol ?: "$",
                        income = income,
                        onIncomeChange = { income = it.filterAmountExpressionInput() },
                        mainBalance = mainBalance,
                        onMainBalanceChange = { mainBalance = it.filterAmountExpressionInput() },
                        savingsBalance = savingsBalance,
                        onSavingsBalanceChange = { savingsBalance = it.filterAmountExpressionInput() },
                        onNext = { step = STEP_VAULT }
                    )
                    STEP_VAULT -> FirstVaultStep(
                        currencySymbol = CurrencyPresets.getByCode(country.defaultCurrency)?.symbol ?: "$",
                        createVault = createVault,
                        onCreateVaultChange = { createVault = it },
                        target = vaultTarget ?: suggestedTarget.toInputString(0),
                        onTargetChange = { vaultTarget = it.filterAmountExpressionInput() },
                        months = country.savingsNorms.recommendedEmergencyMonths,
                        incomeKnown = incomeValue > 0.0,
                        onFinish = ::finish
                    )
                }
            }
        }
    }
}

/**
 * About [months] of expenses, taking expenses as 70% of income and rounding to the nearest 100.
 * Without an income it's a first goal of 1,000 to adjust later.
 */
private fun emergencyFundSuggestion(monthlyIncome: Double, country: CountryConfig): Double {
    if (monthlyIncome <= 0.0) return 1_000.0
    val months = country.savingsNorms.recommendedEmergencyMonths.coerceAtLeast(1)
    val target = monthlyIncome * 0.7 * months
    return ((target / 100.0).roundToInt() * 100).toDouble().coerceAtLeast(500.0)
}

/** Back arrow and "Step 2 of 3" with a progress bar, above every step after the welcome. */
@Composable
private fun OnboardingTopBar(step: Int, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                MaterialSymbolIcon(icon = MaterialSymbols.ARROW_BACK, contentDescription = stringResource(R.string.common_back))
            }
            Text(
                text = stringResource(R.string.onboarding_step_text, step, COUNTED_STEPS),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { step / COUNTED_STEPS.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(CircleShape),
            drawStopIndicator = {}
        )
    }
}

/**
 * Shared layout for a step: title and explanation, the step's content scrolling under them, and the
 * main button (with an optional Skip) pinned at the bottom so it never scrolls away.
 */
@Composable
private fun StepLayout(
    title: String,
    description: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onSkip: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            content()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SparelyButton(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
                Text(primaryLabel, style = MaterialTheme.typography.titleMedium)
            }
            if (onSkip != null) {
                SparelyTextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_skip_step)) }
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    country: CountryConfig,
    language: String,
    onCountryChange: (CountryConfig) -> Unit,
    onLanguageChange: (String) -> Unit,
    onStart: () -> Unit,
    onImport: () -> Unit,
    onSkip: () -> Unit
) {
    var showCountryPicker by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // The brand card: same spruce, logo and wordmark as the splash screen
        SparelyBrandSurface {
            Surface(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_sparely_logo),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp)
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_sparely_wordmark),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.width(150.dp).height(38.dp)
                    )
                    Text(
                        text = stringResource(R.string.splash_tagline),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ValueRow(MaterialSymbols.LOCK, R.string.onboarding_value_private_title, R.string.onboarding_value_private_desc)
            ValueRow(MaterialSymbols.PAYMENTS, R.string.onboarding_value_fast_title, R.string.onboarding_value_fast_desc)
            ValueRow(MaterialSymbols.SAVINGS, R.string.onboarding_value_autopilot_title, R.string.onboarding_value_autopilot_desc)
        }

        // Country and language, both changeable later in Settings > Region
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showCountryPicker = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MaterialSymbolIcon(icon = MaterialSymbols.PUBLIC, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.onboarding_region_country), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${countryDisplayName(country)} · ${country.defaultCurrency}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    MaterialSymbolIcon(icon = MaterialSymbols.ARROW_DROP_DOWN, contentDescription = null)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_language_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    listOf("en" to "English", "fr" to "Français").forEach { (code, label) ->
                        SparelyChip(selected = language == code, onClick = { onLanguageChange(code) }, label = { Text(label) })
                    }
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SparelyButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_get_started), style = MaterialTheme.typography.titleMedium)
            }
            SparelyTonalButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_import_backup))
            }
            SparelyTextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_skip_setup)) }
        }
    }

    if (showCountryPicker) {
        SparelyAlertDialog(
            onDismissRequest = { showCountryPicker = false },
            title = { Text(stringResource(R.string.dialog_select_country_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    CountryProfiles.ALL_COUNTRIES
                        .sortedBy { countryDisplayName(it) }
                        .forEach { option ->
                            val selected = option.countryCode == country.countryCode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onCountryChange(option)
                                        showCountryPicker = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    countryDisplayName(option),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(option.defaultCurrency, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (selected) {
                                    Spacer(Modifier.width(8.dp))
                                    MaterialSymbolIcon(icon = MaterialSymbols.CHECK, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                }
            },
            confirmButton = {
                SparelyTextButton(onClick = { showCountryPicker = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

/** The country's name in the app's current language ("Canada", "Allemagne"). */
private fun countryDisplayName(country: CountryConfig): String =
    Locale("", country.countryCode).getDisplayCountry(Locale.getDefault()).ifBlank { country.countryName }

@Composable
private fun ValueRow(icon: Int, titleRes: Int, descRes: Int) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            MaterialSymbolIcon(icon = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, size = 22.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(descRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutYouStep(
    name: String,
    onNameChange: (String) -> Unit,
    birthday: LocalDate?,
    onBirthdayChange: (LocalDate?) -> Unit,
    onNext: () -> Unit
) {
    var showBirthdayPicker by remember { mutableStateOf(false) }
    StepLayout(
        title = stringResource(R.string.onboarding_about_title),
        description = stringResource(R.string.onboarding_about_desc),
        primaryLabel = stringResource(R.string.onboarding_continue),
        onPrimary = onNext,
        onSkip = onNext
    ) {
        SparelyTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.onboarding_name_label)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SparelyTonalButton(onClick = { showBirthdayPicker = true }, modifier = Modifier.fillMaxWidth()) {
                MaterialSymbolIcon(icon = MaterialSymbols.CAKE, contentDescription = null, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    birthday?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
                        ?: stringResource(R.string.onboarding_birthday_button)
                )
            }
            Text(
                stringResource(R.string.onboarding_birthday_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showBirthdayPicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = birthday.toSafeDatePickerMillis())
        DatePickerDialog(
            onDismissRequest = { showBirthdayPicker = false },
            confirmButton = {
                SparelyTextButton(onClick = {
                    onBirthdayChange(
                        datePickerState.selectedDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                    )
                    showBirthdayPicker = false
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                SparelyTextButton(onClick = { showBirthdayPicker = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun MoneyStep(
    currencySymbol: String,
    income: String,
    onIncomeChange: (String) -> Unit,
    mainBalance: String,
    onMainBalanceChange: (String) -> Unit,
    savingsBalance: String,
    onSavingsBalanceChange: (String) -> Unit,
    onNext: () -> Unit
) {
    StepLayout(
        title = stringResource(R.string.onboarding_money_title),
        description = stringResource(R.string.onboarding_money_desc),
        primaryLabel = stringResource(R.string.onboarding_continue),
        onPrimary = onNext,
        onSkip = onNext
    ) {
        MoneyField(income, onIncomeChange, R.string.onboarding_money_income_label, R.string.onboarding_money_income_helper, currencySymbol)
        MoneyField(mainBalance, onMainBalanceChange, R.string.onboarding_main_balance_label, R.string.onboarding_money_main_helper, currencySymbol)
        MoneyField(savingsBalance, onSavingsBalanceChange, R.string.onboarding_savings_balance_label, R.string.onboarding_money_savings_helper, currencySymbol)
    }
}

@Composable
private fun MoneyField(value: String, onValueChange: (String) -> Unit, labelRes: Int, helperRes: Int, currencySymbol: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SparelyAmountTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(labelRes)) },
            prefix = { Text(currencySymbol) }
        )
        Text(
            stringResource(helperRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun FirstVaultStep(
    currencySymbol: String,
    createVault: Boolean,
    onCreateVaultChange: (Boolean) -> Unit,
    target: String,
    onTargetChange: (String) -> Unit,
    months: Int,
    incomeKnown: Boolean,
    onFinish: () -> Unit
) {
    StepLayout(
        title = stringResource(R.string.onboarding_vault_title),
        description = stringResource(R.string.onboarding_vault_desc),
        primaryLabel = stringResource(R.string.onboarding_finish),
        onPrimary = onFinish
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MaterialSymbolIcon(icon = MaterialSymbols.HEALTH_AND_SAFETY, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.onboarding_vault_create),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = createVault, onCheckedChange = onCreateVaultChange)
                }
                if (createVault) {
                    SparelyAmountTextField(
                        value = target,
                        onValueChange = onTargetChange,
                        label = { Text(stringResource(R.string.onboarding_vault_target_label)) },
                        prefix = { Text(currencySymbol) }
                    )
                    Text(
                        text = if (incomeKnown) {
                            stringResource(R.string.onboarding_vault_suggestion, months)
                        } else {
                            stringResource(R.string.onboarding_vault_suggestion_no_income)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            stringResource(R.string.onboarding_finish_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
