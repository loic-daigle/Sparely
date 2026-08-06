@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.sparely.ui.screens

import androidx.compose.foundation.clickable
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
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var manualMode by remember { mutableStateOf(!settings.autoRecommendationsEnabled) }
    var emergencyPercent by remember { mutableFloatStateOf(settings.defaultPercentages.emergency.toFloat()) }
    var investPercent by remember { mutableFloatStateOf(settings.defaultPercentages.invest.toFloat()) }
    var funPercent by remember { mutableFloatStateOf(settings.defaultPercentages.`fun`.toFloat()) }
    var errorText by remember { mutableStateOf<String?>(null) }
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
    var showAutoCreateAssetDialog by remember { mutableStateOf(false) }
    var assetCreationName by remember { mutableStateOf("") }
    var assetCreationCategory by remember { mutableStateOf(com.example.sparely.domain.model.AssetCategory.OTHER) }
    var assetCreationDescription by remember { mutableStateOf("") }

    // Line Items State
    val expenseItems = remember {
        val items = androidx.compose.runtime.mutableStateListOf<com.example.sparely.domain.model.ExpenseItem>()
        prefillExpense?.items?.forEach { items.add(it) }
        items
    }

    // Collapsible section states
    var allocationsExpanded by remember { mutableStateOf(true) }
    var detailsExpanded by remember { mutableStateOf(false) }
    var lineItemsExpanded by remember { mutableStateOf(false) }

    // ========== Auto-sum logic for line items ==========
    androidx.compose.runtime.LaunchedEffect(expenseItems.toList()) {
        val itemsTotal = expenseItems.sumOf { it.totalPrice }
        if (itemsTotal > 0) {
            val currentAmount = amountText.toSafeDoubleOrZero()
            amountText = itemsTotal.formatCurrency("")
        }
    }

    // ========== Auto-create asset logic based on threshold ==========
    androidx.compose.runtime.LaunchedEffect(amountText, settings.autoCreateAssetThreshold) {
        val amount = amountText.toSafeDoubleOrZero()
        val threshold = settings.autoCreateAssetThreshold
        // Only show auto-create dialog if threshold is enabled (> 0) and amount meets it
        // and we haven't already decided for this amount
        if (threshold > 0 && amount >= threshold && !showCreateAssetDialog) {
            // Could show a suggestion banner here, but the user can still manually create
            // We don't auto-open the dialog, but we could enable a toggle or banner
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
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.toSafeDatePickerMillis()
    )

    val activeVaults = remember(vaults) { vaults.filter { !it.archived } }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ========== QUICK TEMPLATES (NEW EXPENSES ONLY) ==========
        if (isNewExpense) {
            QuickTemplateSelector(
                onTemplateSelected = { template ->
                    category = template.category
                    if (template.descriptionHint != null) {
                        description = template.descriptionHint
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ========== SECTION 1: CRITICAL FIELDS ==========
        FormSection(
            title = "Required Information",
            isCollapsible = false,
            defaultExpanded = true
        ) { _ ->
            // Description (Required)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RequiredFieldLabel(stringResource(R.string.expense_entry_description_label))
                SparelyTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.expense_entry_description_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Amount (Required)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RequiredFieldLabel(stringResource(R.string.expense_entry_amount_label))
                SparelyTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filterCurrencyInput() },
                    label = { Text(stringResource(R.string.expense_entry_amount_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Category (Required)
            CategorySelector(selected = category, onSelect = { category = it }, isRequired = true)

            // Type (Product/Service)
            ExpenseTypeSelector(selected = expenseType, onSelect = { expenseType = it }, isRequired = false, modifier = Modifier.fillMaxWidth())

            // Date (Required)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RequiredFieldLabel(stringResource(R.string.expense_entry_date_label))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column {
                        Text(selectedDate.format(dateFormatter), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SparelyTonalButton(
                        onClick = { showDatePicker = true }
                    ) {
                        Text(stringResource(R.string.expense_entry_change_date_button))
                    }
                }

                if (showDatePicker) {
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
        }

        // ========== SECTION 2: FINANCIAL ==========
        FormSection(
            title = "Payment & Financial Settings",
            isCollapsible = false,
            defaultExpanded = true
        ) { _ ->
            // Payment Method (Required)
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

            // Store/Website
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

            // Deduct from Main Account
            ExpressiveCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = ExpressiveShapes.medium
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.recurring_deduct_main_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Switch(
                            checked = deductFromMainAccount,
                            onCheckedChange = { deductFromMainAccount = it },
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    val helperText = when {
                        deductFromMainAccount && selectedPaymentMethod?.isCreditCard == true ->
                            stringResource(R.string.recurring_deduct_main_desc_credit_on_warning)
                        deductFromMainAccount ->
                            stringResource(R.string.recurring_deduct_main_desc_debit)
                        !deductFromMainAccount && selectedPaymentMethod?.isCreditCard == true ->
                            stringResource(R.string.recurring_deduct_main_desc_credit_off)
                        else -> stringResource(R.string.recurring_deduct_main_desc_debit)
                    }

                    val textColor = if (deductFromMainAccount && selectedPaymentMethod?.isCreditCard == true)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant

                    Text(
                        text = helperText,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor
                    )
                }
            }

            // Include Tax
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.expense_entry_include_tax_label), style = MaterialTheme.typography.bodyMedium)
                Checkbox(checked = includeTax, onCheckedChange = { includeTax = it })
            }
        }

        // ========== SECTION 3: ALLOCATIONS (COLLAPSIBLE) ==========
        FormSection(
            title = "Savings & Asset Allocations",
            isCollapsible = true,
            defaultExpanded = allocationsExpanded,
            helpText = "Configure how savings are allocated and link assets to this expense"
        ) { isExpanded ->
            allocationsExpanded = isExpanded

            // Auto/Manual Allocation Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.expense_entry_auto_allocation_title))
                    Text(
                        text = stringResource(R.string.expense_entry_auto_allocation_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = !manualMode, onCheckedChange = { manualMode = !it })
            }

            // Allocation Details
            if (!manualMode) {
                recommendation?.let {
                    ExpressiveCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = ExpressiveShapes.medium
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.expense_entry_applied_suggestion),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
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
                        options = listOf<SmartVault?>(null) + vaults,
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
                        text = "Link to Assets",
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (selectedAssetAllocations.isEmpty()) {
                        Text(
                            text = "No assets linked to this expense",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        selectedAssetAllocations.forEach { (assetId, percentage) ->
                            val asset = assets.find { it.id == assetId }
                            asset?.let { selectedAsset ->
                                ExpressiveCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    selectedAsset.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
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
                                                },
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                MaterialSymbolIcon(
                                                    icon = MaterialSymbols.CLOSE,
                                                    contentDescription = "Remove asset",
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
                                                    "Allocation",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                                Text(
                                                    percentage.formatPercent(0),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
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

                    // Add asset button
                    if (selectedAssetAllocations.size < assets.size) {
                        FilledTonalButton(
                            onClick = { assetSelectorExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add Asset")
                        }
                    }

                    // Create new asset button
                    FilledTonalButton(
                        onClick = {
                            // Pre-fill with current expense data
                            assetCreationName = description.takeIf { it.isNotBlank() } ?: ""
                            assetCreationDescription = ""
                            assetCreationCategory = com.example.sparely.domain.model.AssetCategory.OTHER
                            showCreateAssetDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create New Asset")
                    }
                }
            }

            // Asset selection dropdown
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
                    title = "Link Asset to Expense"
                )
            }

            // Asset creation dialog
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

            // Auto-create asset dialog (if threshold exceeded)
            if (showAutoCreateAssetDialog) {
                AssetCreationDialog(
                    isOpen = showAutoCreateAssetDialog,
                    initialName = description.takeIf { it.isNotBlank() } ?: "Asset",
                    initialCategory = com.example.sparely.domain.model.AssetCategory.OTHER,
                    initialDescription = "",
                    assetPrice = amountText.toSafeDoubleOrZero(),
                    onConfirm = { name, category, desc ->
                        onCreateAsset(name, category, desc, amountText.toSafeDoubleOrZero())
                        showAutoCreateAssetDialog = false
                    },
                    onDismiss = { showAutoCreateAssetDialog = false }
                )
            }
        }

        // ========== SECTION 4: DETAILS (COLLAPSIBLE - "Additional Info") ==========
        FormSection(
            title = "Additional Information",
            isCollapsible = true,
            defaultExpanded = detailsExpanded,
            helpText = "Optional notes and order information"
        ) { isExpanded ->
            detailsExpanded = isExpanded

            // Notes field
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

            // Order Number field
            SparelyTextField(
                value = orderNumber,
                onValueChange = { orderNumber = it },
                label = { Text(stringResource(R.string.expense_order_number_label)) },
                placeholder = { Text(stringResource(R.string.expense_order_number_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Ignore from predictions toggle
            ExpressiveCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = ExpressiveShapes.medium
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Exclude from predictions",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Switch(
                            checked = isIgnored,
                            onCheckedChange = { isIgnored = it },
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                    Text(
                        text = "Turn this on for rare, large purchases (like a car) to prevent skewing your expense predictions and recommendations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ========== SECTION 5: LINE ITEMS (COLLAPSIBLE - "Itemized Receipt") ==========
        FormSection(
            title = "Itemized Receipt",
            isCollapsible = true,
            defaultExpanded = lineItemsExpanded,
            helpText = "Break down the expense into individual items"
        ) { isExpanded ->
            lineItemsExpanded = isExpanded

            com.example.sparely.ui.components.ExpenseItemsList(
                items = expenseItems,
                onItemsChanged = { newItems ->
                    expenseItems.clear()
                    expenseItems.addAll(newItems)
                }
            )
        }

        // ========== ERROR DISPLAY ==========
        errorText?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        // ========== ACTION BUTTONS ==========
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SparelyTonalButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel))
            }
            SparelyButton(onClick = {
                val amount = amountText.toSafeDouble()
                if (amount == null || amount <= 0.0) {
                    errorText = context.getString(R.string.expense_entry_error_amount)
                    return@SparelyButton
                }

                scope.launch {
                    var finalStoreId = selectedStore?.id

                    // Auto-resolve store if name typed but not selected
                    if (finalStoreId == null && searchQuery.isNotBlank()) {
                        val existing = stores.find { it.name.equals(searchQuery.trim(), ignoreCase = true) }
                        if (existing != null) {
                            finalStoreId = existing.id
                        } else {
                            // Create new store
                            val newStore = onCreateStore(StoreInput(name = searchQuery.trim()))
                            finalStoreId = newStore?.id
                        }
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
                    errorText = null
                    onSave(
                        ExpenseInput(
                            id = prefillExpense?.id?.takeIf { it > 0L },
                            description = description,
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
            }, modifier = Modifier.weight(1f)) {
                Text(stringResource(if (isEditMode) R.string.save_changes else R.string.add_expense))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
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
