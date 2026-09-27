@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.sparely.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import com.example.sparely.ui.components.SparelyTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.ExpenseInput
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.ExpenseType
import com.example.sparely.domain.model.RecommendationResult
import com.example.sparely.domain.model.SparelySettings
import com.example.sparely.domain.model.SavingsPercentages
import com.example.sparely.ui.components.*
import com.example.sparely.ui.components.SparelyBottomSheet
import com.example.sparely.ui.components.SparelyTonalButton
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import com.example.sparely.domain.model.Store
import com.example.sparely.ui.components.SearchableStoreSelector
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import com.example.sparely.domain.model.StoreInput
import com.example.sparely.ui.utils.toSafeDatePickerMillis
import com.example.sparely.ui.utils.filterCurrencyInput
import com.example.sparely.ui.utils.toSafeDouble
import com.example.sparely.ui.utils.toSafeDoubleOrZero
import java.time.Instant
import java.time.ZoneOffset
import com.example.sparely.ui.components.SparelyTextButton
import kotlinx.coroutines.launch
import com.example.sparely.domain.model.PaymentMethod
import androidx.compose.ui.res.stringResource
import com.example.sparely.domain.model.displayName
import com.sparely.app.R
import com.example.sparely.ui.components.PaymentMethodSelector
import androidx.compose.ui.draw.scale
import com.example.sparely.domain.model.Asset
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.components.CategorySelector
import com.example.sparely.ui.components.ExpenseTypeSelector
import com.example.sparely.ui.components.QuickTemplateSelector
import com.example.sparely.ui.components.QuickTemplates
import com.example.sparely.ui.components.FormSection
import com.example.sparely.ui.components.SectionHeader
import com.example.sparely.ui.components.FieldDescription
import com.example.sparely.ui.components.RequiredFieldLabel
import com.example.sparely.ui.components.ExpenseTemplate
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.theme.ExpressiveShapes
import com.example.sparely.ui.components.SparelyChip
import com.example.sparely.ui.components.AssetSelectionDialog

@Composable
fun ExpenseEntryScreen(
    settings: SparelySettings,
    recommendation: RecommendationResult?,
    vaults: List<SmartVault> = emptyList(),
    stores: List<Store> = emptyList(),
    assets: List<Asset> = emptyList(),
    onSave: (ExpenseInput) -> Unit,
    onCancel: () -> Unit,
    onCreateStore: suspend (StoreInput) -> Store? = { null },
    onEditStore: (Store) -> Unit = {},
    onDeleteStore: (Store) -> Unit = {},
    brandfetchClientId: String? = null,
    paymentMethods: List<PaymentMethod> = emptyList(),
    onManagePaymentMethods: () -> Unit = {},
    brandSearchResults: List<com.example.sparely.data.remote.BrandfetchBrand> = emptyList(),
    onBrandSearch: (String) -> Unit = {},
    onLinkAssetToExpense: (assetId: Long, percentageAllocated: Double) -> Unit = { _, _ -> },
    onCreateAsset: (name: String, category: com.example.sparely.domain.model.AssetCategory, description: String?, price: Double) -> Unit = { _, _, _, _ -> },
    prefillExpense: com.example.sparely.domain.model.Expense? = null,
    prefillAssetAllocations: Map<Long, Double> = emptyMap()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEditMode = prefillExpense?.id?.let { it > 0L } == true
    val isNewExpense = !isEditMode

    // ========== Form State ==========
    var description by remember { mutableStateOf(prefillExpense?.description ?: "") }
    var amountText by remember { mutableStateOf(prefillExpense?.amount?.formatCurrency("") ?: "") }
    var category by remember { mutableStateOf(prefillExpense?.category ?: ExpenseCategory.OTHER) }
    var expenseType by remember { mutableStateOf(prefillExpense?.type ?: ExpenseType.PRODUCT) }
    var includeTax by remember { mutableStateOf(prefillExpense?.includesTax ?: settings.includeTaxByDefault) }
    var deductFromMainAccount by remember { mutableStateOf(false) }
    var deductFromVaultId by remember { mutableStateOf(prefillExpense?.deductedFromVaultId) }
    // Editing keeps the original purchase date; new and repeated expenses default to today
    var selectedDate by remember { mutableStateOf(if (isEditMode) prefillExpense!!.date else LocalDate.now()) }
    var manualMode by remember { mutableStateOf(!settings.autoRecommendationsEnabled) }
    var emergencyPercent by remember { mutableFloatStateOf(settings.defaultPercentages.emergency.toFloat()) }
    var investPercent by remember { mutableFloatStateOf(settings.defaultPercentages.invest.toFloat()) }
    var funPercent by remember { mutableFloatStateOf(settings.defaultPercentages.`fun`.toFloat()) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var descriptionError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var selectedStore by remember { mutableStateOf<Store?>(prefillExpense?.storeId?.let { storeId -> stores.find { it.id == storeId } }) }
    var searchQuery by remember { mutableStateOf(prefillExpense?.storeId?.let { storeId -> stores.find { it.id == storeId }?.name } ?: "") }
    var selectedPaymentMethod by remember { mutableStateOf<PaymentMethod?>(prefillExpense?.paymentMethodId?.let { methodId -> paymentMethods.find { it.id == methodId } }) }
    var notes by remember { mutableStateOf(prefillExpense?.notes ?: "") }
    var orderNumber by remember { mutableStateOf(prefillExpense?.orderNumber ?: "") }
    var selectedAssetAllocations by remember { mutableStateOf(prefillAssetAllocations) }
    var assetSelectorExpanded by remember { mutableStateOf(false) }
    var isIgnored by remember { mutableStateOf(prefillExpense?.isIgnored ?: false) }

    // Asset creation state
    var showCreateAssetDialog by remember { mutableStateOf(false) }
    var assetCreationName by remember { mutableStateOf("") }
    var assetCreationCategory by remember { mutableStateOf(com.example.sparely.domain.model.AssetCategory.OTHER) }
    var assetCreationDescription by remember { mutableStateOf("") }

    // Line Items State
    val expenseItems = remember {
        val items = androidx.compose.runtime.mutableStateListOf<com.example.sparely.domain.model.ExpenseItem>()
        prefillExpense?.items?.forEach { items.add(it) }
        items
    }

    // ========== Auto-sum logic for line items ==========
    androidx.compose.runtime.LaunchedEffect(expenseItems.toList()) {
        val itemsTotal = expenseItems.sumOf { it.totalPrice }
        if (itemsTotal > 0) {
            amountText = itemsTotal.formatCurrency("")
        }
    }

    // ========== Set initial payment method ==========
    androidx.compose.runtime.LaunchedEffect(paymentMethods, prefillExpense?.paymentMethodId) {
        val prefillMethodId = prefillExpense?.paymentMethodId
        val prefillMethod = prefillMethodId?.let { methodId -> paymentMethods.find { it.id == methodId } }

        if (prefillMethod != null) {
            selectedPaymentMethod = prefillMethod
            deductFromMainAccount = prefillMethod.defaultDeductFromMainAccount
        } else if (selectedPaymentMethod == null) {
            val default = paymentMethods.find { it.isDefault }
            if (default != null) {
                selectedPaymentMethod = default
                deductFromMainAccount = default.defaultDeductFromMainAccount
            }
        }
    }

    // ========== Date Picker State ==========
    var showDatePicker by remember { mutableStateOf(false) }

    val activeVaults = remember(vaults) { vaults.filter { !it.archived } }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    val currencySymbol = remember(settings.regionalSettings) { settings.regionalSettings.getCurrencySymbol() }
    val amountRequiredError = stringResource(R.string.expense_entry_error_amount)
    val descriptionRequiredError = stringResource(R.string.expense_entry_error_description)

    fun submit() {
        if (isSaving) return
        val amount = amountText.toSafeDouble()
        amountError = if (amount == null || amount <= 0.0) amountRequiredError else null
        descriptionError = if (description.isBlank()) descriptionRequiredError else null
        if (amountError != null || descriptionError != null || amount == null) return

        // Guard against double taps while the store is being created
        isSaving = true
        scope.launch {
            var finalStoreId = selectedStore?.id

            // Auto-resolve store if name typed but not selected
            if (finalStoreId == null && searchQuery.isNotBlank()) {
                val existing = stores.find { it.name.equals(searchQuery.trim(), ignoreCase = true) }
                finalStoreId = existing?.id ?: onCreateStore(StoreInput(name = searchQuery.trim()))?.id
            }

            val manualPercentages = if (manualMode) {
                SavingsPercentages(
                    emergency = emergencyPercent.toDouble(),
                    invest = investPercent.toDouble(),
                    `fun` = funPercent.toDouble(),
                    safeInvestmentSplit = settings.defaultPercentages.safeInvestmentSplit
                ).adjustWithinBudget()
            } else {
                null
            }
            onSave(
                ExpenseInput(
                    id = prefillExpense?.id?.takeIf { it > 0L },
                    description = description.trim(),
                    amount = amount,
                    category = category,
                    date = selectedDate,
                    includesTax = includeTax,
                    manualPercentages = manualPercentages,
                    deductFromMainAccount = deductFromMainAccount,
                    deductFromVaultId = deductFromVaultId,
                    storeId = finalStoreId,
                    paymentMethodId = selectedPaymentMethod?.id,
                    notes = notes.takeIf { it.isNotBlank() },
                    orderNumber = orderNumber.takeIf { it.isNotBlank() },
                    items = expenseItems.toList(),
                    assetAllocations = selectedAssetAllocations,
                    type = expenseType,
                    isIgnored = isIgnored
                )
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ========== SECTION 1: THE BASICS ==========
            FormSection(
                title = stringResource(R.string.expense_entry_section_basics),
                isCollapsible = false,
                defaultExpanded = true
            ) { _ ->
                val amountErr = amountError
                val descriptionErr = descriptionError
                // Amount first: it's the one thing every entry needs
                SparelyTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it.filterCurrencyInput()
                        amountError = null
                    },
                    label = { Text(stringResource(R.string.expense_entry_amount_label)) },
                    prefix = { Text(currencySymbol) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    isError = amountErr != null,
                    supportingText = if (amountErr != null) { { Text(amountErr) } } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                SparelyTextField(
                    value = description,
                    onValueChange = {
                        description = it
                        descriptionError = null
                    },
                    label = { Text(stringResource(R.string.expense_entry_description_label)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    isError = descriptionErr != null,
                    supportingText = if (descriptionErr != null) { { Text(descriptionErr) } } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick templates fill category + description for common purchases (new expenses only)
                if (isNewExpense) {
                    QuickTemplateSelector(
                        onTemplateSelected = { template ->
                            category = template.category
                            if (template.descriptionHint != null) {
                                description = template.descriptionHint
                                descriptionError = null
                            }
                        }
                    )
                }

                // Date: one-tap Today / Yesterday, or pick any date
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.expense_entry_date_label),
                        style = MaterialTheme.typography.titleSmall
                    )
                    val today = LocalDate.now()
                    val yesterday = today.minusDays(1)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SparelyChip(
                            selected = selectedDate == today,
                            onClick = { selectedDate = today },
                            label = { Text(stringResource(R.string.history_today)) }
                        )
                        SparelyChip(
                            selected = selectedDate == yesterday,
                            onClick = { selectedDate = yesterday },
                            label = { Text(stringResource(R.string.history_yesterday)) }
                        )
                        val isOtherDate = selectedDate != today && selectedDate != yesterday
                        SparelyChip(
                            selected = isOtherDate,
                            onClick = { showDatePicker = true },
                            label = {
                                Text(
                                    if (isOtherDate) selectedDate.format(dateFormatter)
                                    else stringResource(R.string.expense_entry_pick_date)
                                )
                            },
                            leadingIcon = {
                                MaterialSymbolIcon(
                                    icon = MaterialSymbols.CALENDAR_MONTH,
                                    contentDescription = null,
                                    size = 16.dp
                                )
                            }
                        )
                    }

                    if (showDatePicker) {
                        val datePickerState = rememberDatePickerState(
                            initialSelectedDateMillis = selectedDate.toSafeDatePickerMillis()
                        )
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker = false },
                            confirmButton = {
                                SparelyTextButton(onClick = {
                                    datePickerState.selectedDateMillis?.let { millis ->
                                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                                    }
                                    showDatePicker = false
                                }) {
                                    Text(stringResource(R.string.ok))
                                }
                            },
                            dismissButton = {
                                SparelyTextButton(onClick = { showDatePicker = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        ) {
                            DatePicker(state = datePickerState)
                        }
                    }
                }

                CategorySelector(selected = category, onSelect = { category = it }, isRequired = false)

                ExpenseTypeSelector(selected = expenseType, onSelect = { expenseType = it }, isRequired = false, modifier = Modifier.fillMaxWidth())
            }

            // ========== SECTION 2: PAYMENT ==========
            FormSection(
                title = stringResource(R.string.expense_entry_section_payment),
                isCollapsible = false,
                defaultExpanded = true
            ) { _ ->
                PaymentMethodSelector(
                    paymentMethods = paymentMethods,
                    selectedMethod = selectedPaymentMethod,
                    onMethodSelected = { method ->
                        selectedPaymentMethod = method
                        method?.let {
                            deductFromMainAccount = it.defaultDeductFromMainAccount
                        }
                    },
                    onManageMethods = onManagePaymentMethods,
                    expenseAmount = amountText.toSafeDoubleOrZero()
                )

                SearchableStoreSelector(
                    stores = stores,
                    selectedStore = selectedStore,
                    onStoreSelected = { selectedStore = it },
                    onCreateStore = onCreateStore,
                    onEditStore = onEditStore,
                    onDeleteStore = onDeleteStore,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    brandfetchClientId = brandfetchClientId,
                    brandSearchResults = brandSearchResults,
                    onBrandSearch = onBrandSearch
                )

                val deductHelperText = when {
                    deductFromMainAccount && selectedPaymentMethod?.isCreditCard == true ->
                        stringResource(R.string.recurring_deduct_main_desc_credit_on_warning)
                    !deductFromMainAccount && selectedPaymentMethod?.isCreditCard == true ->
                        stringResource(R.string.recurring_deduct_main_desc_credit_off)
                    else -> stringResource(R.string.recurring_deduct_main_desc_debit)
                }
                ToggleRow(
                    title = stringResource(R.string.recurring_deduct_main_title),
                    description = deductHelperText,
                    descriptionIsWarning = deductFromMainAccount && selectedPaymentMethod?.isCreditCard == true,
                    checked = deductFromMainAccount,
                    onCheckedChange = { deductFromMainAccount = it }
                )

                ToggleRow(
                    title = stringResource(R.string.expense_entry_include_tax_label),
                    description = null,
                    checked = includeTax,
                    onCheckedChange = { includeTax = it }
                )
            }

            // ========== SECTION 3: ALLOCATIONS (COLLAPSIBLE, advanced) ==========
            FormSection(
                title = stringResource(R.string.expense_entry_section_allocations),
                isCollapsible = true,
                defaultExpanded = manualMode || deductFromVaultId != null || selectedAssetAllocations.isNotEmpty(),
                helpText = stringResource(R.string.expense_entry_section_allocations_help)
            ) { _ ->
                ToggleRow(
                    title = stringResource(R.string.expense_entry_auto_allocation_title),
                    description = stringResource(R.string.expense_entry_auto_allocation_desc),
                    checked = !manualMode,
                    onCheckedChange = { manualMode = !it }
                )

                if (!manualMode) {
                    recommendation?.let {
                        ExpressiveCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = ExpressiveShapes.medium,
                            contentPadding = 16.dp
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.expense_entry_applied_suggestion),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.expense_entry_suggestion_detail, formatPercent(it.recommendedPercentages.emergency), formatPercent(it.recommendedPercentages.invest), formatPercent(it.recommendedPercentages.`fun`)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    PercentSliders(
                        emergency = emergencyPercent,
                        invest = investPercent,
                        funValue = funPercent,
                        onEmergencyChange = { emergencyPercent = it },
                        onInvestChange = { investPercent = it },
                        onFunChange = { funPercent = it }
                    )
                }

                // Vault Deduction
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.expense_entry_deduct_vault_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (activeVaults.isEmpty()) {
                            stringResource(R.string.expense_entry_no_vaults)
                        } else if (deductFromVaultId != null && deductFromMainAccount) {
                            stringResource(R.string.expense_entry_vault_overflow)
                        } else {
                            stringResource(R.string.expense_entry_choose_vault_desc)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (deductFromVaultId != null && deductFromMainAccount) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (activeVaults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SparelyExpressiveDropdown(
                            modifier = Modifier.fillMaxWidth(),
                            selectedOption = vaults.find { it.id == deductFromVaultId },
                            label = stringResource(R.string.recurring_choose_vault),
                            // Archived vaults can't take new expenses
                            options = listOf<SmartVault?>(null) + activeVaults,
                            onOptionSelected = { selectedVault ->
                                deductFromVaultId = selectedVault?.id
                            },
                            optionLabel = { selectedVault ->
                                selectedVault?.name ?: stringResource(R.string.expense_entry_none)
                            },
                            supportingText = { vault ->
                                if (vault == null) {
                                    stringResource(R.string.expense_entry_none_desc)
                                } else {
                                    stringResource(R.string.recurring_vault_balance, vault.currentBalance.formatCurrency())
                                }
                            }
                        )
                    }
                }

                // Asset Linking
                if (assets.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.expense_entry_link_assets_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (selectedAssetAllocations.isEmpty()) {
                            Text(
                                text = stringResource(R.string.expense_entry_no_assets_linked),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            selectedAssetAllocations.forEach { (assetId, percentage) ->
                                val asset = assets.find { it.id == assetId }
                                asset?.let { selectedAsset ->
                                    ExpressiveCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentPadding = 12.dp
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        selectedAsset.name,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        selectedAsset.category.displayName(),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                                androidx.compose.material3.IconButton(
                                                    onClick = {
                                                        selectedAssetAllocations = selectedAssetAllocations.toMutableMap().apply {
                                                            remove(assetId)
                                                        }
                                                    }
                                                ) {
                                                    MaterialSymbolIcon(
                                                        icon = MaterialSymbols.CLOSE,
                                                        contentDescription = stringResource(R.string.expense_entry_remove_asset),
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }

                                            // Allocation percentage slider
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        stringResource(R.string.expense_entry_asset_allocation),
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                    Text(
                                                        percentage.formatPercent(0),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                Slider(
                                                    value = percentage.toFloat(),
                                                    onValueChange = { newValue ->
                                                        selectedAssetAllocations = selectedAssetAllocations.toMutableMap().apply {
                                                            put(assetId, newValue.toDouble().coerceIn(0.01, 1.0))
                                                        }
                                                    },
                                                    valueRange = 0.01f..1.0f,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (selectedAssetAllocations.size < assets.size) {
                                SparelyTonalButton(
                                    onClick = { assetSelectorExpanded = true },
                                    modifier = Modifier.weight(1f),
                                    icon = { MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null, size = 18.dp) }
                                ) {
                                    Text(stringResource(R.string.expense_entry_add_asset), maxLines = 1)
                                }
                            }
                            SparelyTonalButton(
                                onClick = {
                                    // Pre-fill with current expense data
                                    assetCreationName = description.takeIf { it.isNotBlank() } ?: ""
                                    assetCreationDescription = ""
                                    assetCreationCategory = com.example.sparely.domain.model.AssetCategory.OTHER
                                    showCreateAssetDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.expense_entry_create_asset), maxLines = 1)
                            }
                        }
                    }
                }

                if (assetSelectorExpanded) {
                    AssetSelectionDialog(
                        isOpen = assetSelectorExpanded,
                        selectedAssetIds = selectedAssetAllocations.keys,
                        assets = assets,
                        onAssetSelected = { asset ->
                            selectedAssetAllocations = selectedAssetAllocations.toMutableMap().apply {
                                put(asset.id, 1.0)
                            }
                        },
                        onDismiss = { assetSelectorExpanded = false },
                        title = stringResource(R.string.expense_entry_link_asset_dialog_title)
                    )
                }

                if (showCreateAssetDialog) {
                    AssetCreationDialog(
                        isOpen = showCreateAssetDialog,
                        initialName = assetCreationName,
                        initialCategory = assetCreationCategory,
                        initialDescription = assetCreationDescription,
                        assetPrice = amountText.toSafeDoubleOrZero(),
                        onConfirm = { name, category, description ->
                            onCreateAsset(name, category, description, amountText.toSafeDoubleOrZero())
                            showCreateAssetDialog = false
                        },
                        onDismiss = { showCreateAssetDialog = false }
                    )
                }
            }

            // ========== SECTION 4: DETAILS (COLLAPSIBLE) ==========
            FormSection(
                title = stringResource(R.string.expense_entry_section_details),
                isCollapsible = true,
                defaultExpanded = notes.isNotBlank() || orderNumber.isNotBlank() || isIgnored,
                helpText = stringResource(R.string.expense_entry_section_details_help)
            ) { _ ->
                SparelyTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.expense_notes_label)) },
                    placeholder = { Text(stringResource(R.string.expense_notes_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4
                )

                SparelyTextField(
                    value = orderNumber,
                    onValueChange = { orderNumber = it },
                    label = { Text(stringResource(R.string.expense_order_number_label)) },
                    placeholder = { Text(stringResource(R.string.expense_order_number_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                ToggleRow(
                    title = stringResource(R.string.expense_entry_exclude_predictions_title),
                    description = stringResource(R.string.expense_entry_exclude_predictions_desc),
                    checked = isIgnored,
                    onCheckedChange = { isIgnored = it }
                )
            }

            // ========== SECTION 5: LINE ITEMS (COLLAPSIBLE) ==========
            FormSection(
                title = stringResource(R.string.expense_entry_section_items),
                isCollapsible = true,
                defaultExpanded = expenseItems.isNotEmpty(),
                helpText = stringResource(R.string.expense_entry_section_items_help)
            ) { _ ->
                com.example.sparely.ui.components.ExpenseItemsList(
                    items = expenseItems,
                    onItemsChanged = { newItems ->
                        expenseItems.clear()
                        expenseItems.addAll(newItems)
                    }
                )
            }
        }

        // ========== STICKY ACTION BAR ==========
        // Always reachable, so saving never requires scrolling past the optional sections
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (amountError != null || descriptionError != null) {
                    Text(
                        text = stringResource(R.string.expense_entry_fix_errors),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SparelyTonalButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.cancel))
                    }
                    SparelyButton(
                        onClick = ::submit,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(if (isEditMode) R.string.save_changes else R.string.add_expense))
                    }
                }
            }
        }
    }
}

/** Full-width row with a title, optional description and a switch; the whole row toggles. */
@Composable
private fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    descriptionIsWarning: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (descriptionIsWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Row handles the toggle; the switch only mirrors state
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun AssetCreationDialog(
    isOpen: Boolean,
    initialName: String,
    initialCategory: com.example.sparely.domain.model.AssetCategory,
    initialDescription: String,
    assetPrice: Double,
    onConfirm: (name: String, category: com.example.sparely.domain.model.AssetCategory, description: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var category by remember { mutableStateOf(initialCategory) }
    var description by remember { mutableStateOf(initialDescription) }
    var categoryExpanded by remember { mutableStateOf(false) }

    SparelyBottomSheet(
        isOpen = isOpen,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                "Create New Asset",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Name field
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Asset Name", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                SparelyTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("e.g., MacBook Pro") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Category dropdown
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SparelyTextField(
                        value = category.displayName(),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        com.example.sparely.domain.model.AssetCategory.values().forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.displayName()) },
                                onClick = {
                                    category = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Description field
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Description (Optional)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                SparelyTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes about this asset") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2
                )
            }

            // Asset price display
            ExpressiveCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Asset Price (from expense)", style = MaterialTheme.typography.labelSmall)
                    Text(assetPrice.formatCurrency(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SparelyTextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                SparelyButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            onConfirm(name, category, description.takeIf { it.isNotBlank() } ?: "")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank()
                ) {
                    Text("Create Asset")
                }
            }
        }
    }
}

@Composable
private fun PercentSliders(
    emergency: Float,
    invest: Float,
    funValue: Float,
    onEmergencyChange: (Float) -> Unit,
    onInvestChange: (Float) -> Unit,
    onFunChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.expense_entry_manual_allocation_title), style = MaterialTheme.typography.titleSmall)
        AllocationSlider(label = stringResource(R.string.onboarding_financial_emergency_title), value = emergency, onValueChange = onEmergencyChange)
        AllocationSlider(label = stringResource(R.string.onboarding_financial_invest_title), value = invest, onValueChange = onInvestChange)
        AllocationSlider(label = stringResource(R.string.onboarding_financial_fun_title), value = funValue, onValueChange = onFunChange)
        val total = emergency + invest + funValue
        Text(stringResource(R.string.expense_entry_total_allocation, formatPercent(total.toDouble())), color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
    }
}

@Composable
private fun AllocationSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label)
            Text(formatPercent(value.toDouble()))
        }
        Slider(
            value = value,
            onValueChange = { onValueChange(it.coerceIn(0f, 0.5f)) },
            valueRange = 0f..0.5f
        )
    }
}

private fun formatPercent(value: Double): String {
    return value.formatPercent()
}
