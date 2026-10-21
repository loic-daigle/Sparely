@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.sparely.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.logic.KnownTitle
import com.example.sparely.domain.logic.TitleSuggestionEngine
import com.example.sparely.domain.model.Asset
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.ExpenseInput
import com.example.sparely.domain.model.ExpenseType
import com.example.sparely.domain.model.PaymentMethod
import com.example.sparely.domain.model.RecommendationResult
import com.example.sparely.domain.model.SavingsPercentages
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.SparelySettings
import com.example.sparely.domain.model.Store
import com.example.sparely.domain.model.StoreInput
import com.example.sparely.domain.model.displayName
import com.example.sparely.ui.components.SparelySheetHeader
import com.example.sparely.ui.components.SheetActions
import com.example.sparely.ui.components.SheetSectionLabel
import com.example.sparely.ui.components.AmountCalculatorKeys
import com.example.sparely.ui.components.AssetSelectionDialog
import com.example.sparely.ui.components.rememberAmountFieldValue
import com.example.sparely.ui.components.settleAmountExpression
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.components.PaymentMethodSelector
import com.example.sparely.ui.components.SearchableStoreSelector
import com.example.sparely.ui.components.SparelyBottomSheet
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelyChip
import com.example.sparely.ui.components.SparelyExpressiveDropdown
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.SparelyTextField
import com.example.sparely.ui.components.SparelyTonalButton
import com.example.sparely.ui.components.TitleAutofillHint
import com.example.sparely.ui.components.TitleSuggestionRow
import com.example.sparely.ui.theme.ExpressiveShapes
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.categoryColor
import com.example.sparely.ui.theme.getCategoryIcon
import com.example.sparely.ui.theme.onCategoryColor
import com.example.sparely.ui.utils.filterAmountExpressionInput
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.utils.toSafeDatePickerMillis
import com.example.sparely.ui.utils.toSafeDouble
import com.example.sparely.ui.utils.toSafeDoubleOrZero
import com.sparely.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

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
    prefillAssetAllocations: Map<Long, Double> = emptyMap(),
    knownTitles: List<KnownTitle> = emptyList(),
    workingOnProjectId: Long? = null
) {
    val scope = rememberCoroutineScope()
    val haptics = com.example.sparely.ui.components.LocalSparelyHaptics.current
    val isEditMode = prefillExpense?.id?.let { it > 0L } == true
    val isNewExpense = !isEditMode

    // ========== Form State ==========
    var description by rememberSaveable { mutableStateOf(prefillExpense?.description ?: "") }
    var amountText by rememberSaveable { mutableStateOf(prefillExpense?.amount?.formatCurrency("") ?: "") }
    var category by rememberSaveable { mutableStateOf(prefillExpense?.category ?: ExpenseCategory.OTHER) }
    // Not asked in the form: nothing reads them, so an edited expense just keeps what it had
    val expenseType = prefillExpense?.type ?: ExpenseType.PRODUCT
    val includeTax = prefillExpense?.includesTax ?: false
    var deductFromMainAccount by rememberSaveable { mutableStateOf(false) }
    var deductFromVaultId by rememberSaveable { mutableStateOf(prefillExpense?.deductedFromVaultId) }
    // Editing keeps the original purchase date; new and repeated expenses default to today
    var selectedDate by rememberSaveable { mutableStateOf(if (isEditMode) prefillExpense!!.date else LocalDate.now()) }
    var manualMode by remember { mutableStateOf(!settings.autoRecommendationsEnabled) }
    var emergencyPercent by remember { mutableFloatStateOf(settings.defaultPercentages.emergency.toFloat()) }
    var investPercent by remember { mutableFloatStateOf(settings.defaultPercentages.invest.toFloat()) }
    var funPercent by remember { mutableFloatStateOf(settings.defaultPercentages.`fun`.toFloat()) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var descriptionError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var selectedStore by remember { mutableStateOf<Store?>(prefillExpense?.storeId?.let { storeId -> stores.find { it.id == storeId } }) }
    var searchQuery by rememberSaveable { mutableStateOf(prefillExpense?.storeId?.let { storeId -> stores.find { it.id == storeId }?.name } ?: "") }
    // Picked by the "Set initial payment method" effect below, which also sets its deduct default
    var selectedPaymentMethod by remember { mutableStateOf<PaymentMethod?>(null) }
    var notes by rememberSaveable { mutableStateOf(prefillExpense?.notes ?: "") }
    var orderNumber by rememberSaveable { mutableStateOf(prefillExpense?.orderNumber ?: "") }
    // A new expense starts linked to the project being worked on; it can be removed like any link
    val workingOnProject = assets.find { it.id == workingOnProjectId && it.isProject && !it.isDone }
        ?.takeIf { isNewExpense && prefillAssetAllocations.isEmpty() }
    val openingAllocations = remember(prefillAssetAllocations) {
        workingOnProject?.let { mapOf(it.id to 1.0) } ?: prefillAssetAllocations
    }
    var selectedAssetAllocations by remember { mutableStateOf(openingAllocations) }
    var assetSelectorExpanded by remember { mutableStateOf(false) }
    var isIgnored by rememberSaveable { mutableStateOf(prefillExpense?.isIgnored ?: false) }

    // ========== Remembered titles ==========
    // Fields the user set themselves are never overwritten by a remembered title
    var categoryTouched by rememberSaveable { mutableStateOf(false) }
    var storeTouched by rememberSaveable { mutableStateOf(false) }
    var paymentTouched by rememberSaveable { mutableStateOf(false) }
    var autofilledFrom by remember { mutableStateOf<KnownTitle?>(null) }
    var autofillUndo by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Asset links for an edited expense load asynchronously; adopt them when they arrive so saving
    // doesn't wipe existing links (unless the user already linked assets themselves)
    androidx.compose.runtime.LaunchedEffect(prefillAssetAllocations) {
        if (selectedAssetAllocations.isEmpty()) {
            selectedAssetAllocations = prefillAssetAllocations
        }
    }

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
    // The amount follows the items total until the user types an amount of their own: a receipt's
    // total often includes tax, tip or fees that aren't itemized
    var amountFollowsItems by remember {
        val itemsTotal = expenseItems.sumOf { it.totalPrice }
        val amount = amountText.toSafeDouble()
        mutableStateOf(amount == null || (itemsTotal > 0 && kotlin.math.abs(amount - itemsTotal) < 0.005))
    }
    androidx.compose.runtime.LaunchedEffect(expenseItems.toList()) {
        val itemsTotal = expenseItems.sumOf { it.totalPrice }
        if (amountFollowsItems && itemsTotal > 0) {
            amountText = itemsTotal.formatCurrency("")
        }
    }

    // ========== Set initial payment method ==========
    // Only the method's id survives leaving the form (to manage payment methods, say), so it's
    // looked up again when the form comes back
    var savedPaymentMethodId by rememberSaveable { mutableStateOf<Long?>(null) }
    androidx.compose.runtime.LaunchedEffect(selectedPaymentMethod) {
        selectedPaymentMethod?.let { savedPaymentMethodId = it.id }
    }
    androidx.compose.runtime.LaunchedEffect(paymentMethods, prefillExpense?.paymentMethodId) {
        // Only while nothing is picked: a method the user chose is never replaced, even when the
        // list changes (a method added in Settings)
        if (selectedPaymentMethod != null) return@LaunchedEffect
        val restored = savedPaymentMethodId?.let { methodId -> paymentMethods.find { it.id == methodId } }
        if (restored != null) {
            // Its deduct choice was saved along with the rest of the form
            selectedPaymentMethod = restored
            return@LaunchedEffect
        }
        val prefillMethod = prefillExpense?.paymentMethodId?.let { methodId -> paymentMethods.find { it.id == methodId } }
        val method = prefillMethod ?: paymentMethods.find { it.isDefault }
        if (method != null) {
            selectedPaymentMethod = method
            deductFromMainAccount = method.defaultDeductFromMainAccount
        }
    }

    /** Refills untouched fields from [known]'s last use; the amount only when it's still empty. */
    fun applyKnownTitle(known: KnownTitle) {
        val previousCategory = category
        val previousStore = selectedStore
        val previousStoreQuery = searchQuery
        val previousMethod = selectedPaymentMethod
        val previousDeduct = deductFromMainAccount
        val previousAmount = amountText

        if (!categoryTouched) category = known.category
        if (!storeTouched) {
            known.storeId?.let { id -> stores.find { it.id == id } }?.let { store ->
                selectedStore = store
                searchQuery = store.name
            }
        }
        if (!paymentTouched) {
            known.paymentMethodId?.let { id -> paymentMethods.find { it.id == id } }?.let { method ->
                selectedPaymentMethod = method
                deductFromMainAccount = method.defaultDeductFromMainAccount
            }
        }
        if (amountText.isBlank()) amountText = known.lastAmount.formatCurrency("")

        autofilledFrom = known
        autofillUndo = {
            category = previousCategory
            selectedStore = previousStore
            searchQuery = previousStoreQuery
            selectedPaymentMethod = previousMethod
            deductFromMainAccount = previousDeduct
            amountText = previousAmount
        }
    }

    // Typing a known title exactly refills the form once; changing the text away clears the hint
    androidx.compose.runtime.LaunchedEffect(description) {
        if (!isNewExpense) return@LaunchedEffect
        val match = TitleSuggestionEngine.exactMatch(description, knownTitles)
        val current = autofilledFrom
        if (match == null) {
            autofilledFrom = null
            autofillUndo = null
        } else if (current == null || TitleSuggestionEngine.normalize(current.title) != TitleSuggestionEngine.normalize(match.title)) {
            applyKnownTitle(match)
        }
    }

    val titleSuggestions = remember(description, knownTitles, isNewExpense) {
        if (!isNewExpense) emptyList()
        else TitleSuggestionEngine.suggest(description, knownTitles)
            .filter { TitleSuggestionEngine.normalize(it.title) != TitleSuggestionEngine.normalize(description) }
    }

    // ========== Date Picker State ==========
    var showDatePicker by remember { mutableStateOf(false) }

    val activeVaults = remember(vaults) { vaults.filter { !it.archived } }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    val currencySymbol = remember(settings.regionalSettings) { settings.regionalSettings.getCurrencySymbol() }
    val amountRequiredError = stringResource(R.string.expense_entry_error_amount)
    val descriptionRequiredError = stringResource(R.string.expense_entry_error_description)

    // ========== Keyboard flow ==========
    // A new expense opens with the keypad up on the amount; Next goes to the title, Done saves
    val amountFocus = remember { FocusRequester() }
    val descriptionFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (isNewExpense) amountFocus.requestFocus()
    }

    // ========== Leaving with unsaved input ==========
    // Snapshot of what the form opened with; leaving after changing any of it asks first
    val initialSnapshot = remember {
        // Line items overwrite the amount with their total on open, so start from that total
        val initialAmount = expenseItems.sumOf { it.totalPrice }.takeIf { it > 0 && amountFollowsItems }?.formatCurrency("") ?: amountText
        listOf(description, initialAmount, category, selectedDate, searchQuery, notes, orderNumber,
            expenseItems.toList(), deductFromVaultId, isIgnored, manualMode)
    }
    val changedSinceOpen = listOf(description, amountText, category, selectedDate, searchQuery, notes, orderNumber,
        expenseItems.toList(), deductFromVaultId, isIgnored, manualMode) != initialSnapshot ||
        paymentTouched || selectedAssetAllocations != openingAllocations
    // The snapshot is retaken when the form comes back from another screen (managing payment
    // methods), with the typed input already restored, so whether it was ever changed is saved too
    var changedBeforeLeaving by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(changedSinceOpen) {
        if (changedSinceOpen) changedBeforeLeaving = true
    }
    val hasChanges = changedSinceOpen || changedBeforeLeaving
    var showDiscardDialog by remember { mutableStateOf(false) }
    fun requestLeave() {
        if (hasChanges) showDiscardDialog = true else onCancel()
    }
    // Covers the system back gesture and the top bar's back arrow
    BackHandler(enabled = !isSaving) { requestLeave() }

    fun submit() {
        if (isSaving) return
        val amount = amountText.toSafeDouble()
        amountError = if (amount == null || amount <= 0.0) amountRequiredError else null
        descriptionError = if (description.isBlank()) descriptionRequiredError else null
        // Put the cursor where the fix is needed
        when {
            amountError != null -> amountFocus.requestFocus()
            descriptionError != null -> descriptionFocus.requestFocus()
        }
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
            haptics.confirm()
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
                    // Rows added but never filled in aren't part of the receipt
                    items = expenseItems.filter { it.name.isNotBlank() || it.totalPrice > 0.0 },
                    assetAllocations = selectedAssetAllocations,
                    type = expenseType,
                    isIgnored = isIgnored
                )
            )
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = {
                Text(stringResource(if (isEditMode) R.string.expense_entry_discard_edit_title else R.string.expense_entry_discard_title))
            },
            text = { Text(stringResource(R.string.expense_entry_discard_message)) },
            confirmButton = {
                SparelyTextButton(onClick = {
                    showDiscardDialog = false
                    onCancel()
                }) {
                    Text(stringResource(R.string.expense_entry_discard_confirm))
                }
            },
            dismissButton = {
                SparelyTextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.expense_entry_keep_editing))
                }
            }
        )
    }

    // Optional sections stay collapsed but say what's in them, so nothing set there goes unnoticed
    val itemsTotal = expenseItems.sumOf { it.totalPrice }
    val filledItemCount = expenseItems.count { it.name.isNotBlank() || it.totalPrice > 0.0 }
    val allocationSummary = listOfNotNull(
        stringResource(if (manualMode) R.string.expense_entry_manual_allocation_title else R.string.expense_entry_auto_allocation_title),
        vaults.find { it.id == deductFromVaultId }?.name,
        when (selectedAssetAllocations.size) {
            0 -> null
            1 -> assets.find { it.id == selectedAssetAllocations.keys.first() }?.name
            else -> pluralStringResource(R.plurals.expense_entry_assets_linked, selectedAssetAllocations.size, selectedAssetAllocations.size)
        }
    ).joinToString(" · ")
    val detailsSummary = listOfNotNull(
        notes.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() },
        orderNumber.takeIf { it.isNotBlank() }?.let { "#$it" },
        if (isIgnored) stringResource(R.string.expense_entry_exclude_predictions_title) else null
    ).joinToString(" · ").ifEmpty { stringResource(R.string.expense_entry_section_details_help) }
    val itemsSummary = if (filledItemCount == 0) {
        stringResource(R.string.expense_entry_section_items_help)
    } else {
        pluralStringResource(R.plurals.expense_entry_items_count, filledItemCount, filledItemCount, itemsTotal.formatCurrency(currencySymbol))
    }

    // imePadding keeps the save bar sitting on top of the keyboard instead of under it
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ========== HERO: AMOUNT, TITLE, DATE ==========
            AmountHeroCard(
                category = category,
                currencySymbol = currencySymbol,
                amountText = amountText,
                onAmountChange = {
                    amountText = it.filterAmountExpressionInput()
                    amountError = null
                    // Typing an amount takes it over from the items; clearing it hands it back
                    amountFollowsItems = amountText.isBlank()
                },
                amountError = amountError,
                amountFocus = amountFocus,
                onAmountNext = { descriptionFocus.requestFocus() },
                description = description,
                onDescriptionChange = {
                    description = it
                    descriptionError = null
                },
                descriptionError = descriptionError,
                descriptionFocus = descriptionFocus,
                // Amount + title is a complete expense, so Done saves it
                onDescriptionDone = ::submit
            ) {
                // Date: one-tap Today / Yesterday, or pick any date
                val today = LocalDate.now()
                val yesterday = today.minusDays(1)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth()
                ) {
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
            }

            if (titleSuggestions.isNotEmpty()) {
                TitleSuggestionRow(
                    suggestions = titleSuggestions,
                    currencySymbol = currencySymbol,
                    onSuggestionSelected = { known ->
                        // Setting the description triggers the exact-match refill above
                        description = known.title
                        descriptionError = null
                    }
                )
            }

            autofilledFrom?.let { known ->
                autofillUndo?.let { undo ->
                    TitleAutofillHint(
                        title = known.title,
                        onUndo = {
                            undo()
                            autofillUndo = null
                        }
                    )
                }
            }

            // ========== CATEGORY ==========
            CategoryPicker(selected = category, onSelect = { category = it; categoryTouched = true })

            // ========== PAYMENT ==========
            SectionCard {
                PaymentMethodSelector(
                    paymentMethods = paymentMethods,
                    selectedMethod = selectedPaymentMethod,
                    onMethodSelected = { method ->
                        selectedPaymentMethod = method
                        paymentTouched = true
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
                    onStoreSelected = { selectedStore = it; storeTouched = true },
                    onCreateStore = onCreateStore,
                    onEditStore = onEditStore,
                    onDeleteStore = onDeleteStore,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it; storeTouched = true },
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
            }

            // ========== ALLOCATIONS (advanced) ==========
            ExpandableSectionCard(
                icon = MaterialSymbols.PIE_CHART,
                title = stringResource(R.string.expense_entry_section_allocations),
                summary = allocationSummary,
                initiallyExpanded = manualMode || deductFromVaultId != null ||
                    selectedAssetAllocations.keys.any { it != workingOnProject?.id }
            ) {
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
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
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
                                assets.find { it.id == assetId }?.let { selectedAsset ->
                                    if (selectedAsset.id == workingOnProject?.id) {
                                        Text(
                                            text = stringResource(R.string.projects_entry_working_on, selectedAsset.name),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    LinkedAssetRow(
                                        asset = selectedAsset,
                                        percentage = percentage,
                                        onPercentageChange = { newValue ->
                                            selectedAssetAllocations = selectedAssetAllocations.toMutableMap().apply {
                                                put(assetId, newValue.coerceIn(0.01, 1.0))
                                            }
                                        },
                                        onRemove = {
                                            selectedAssetAllocations = selectedAssetAllocations.toMutableMap().apply {
                                                remove(assetId)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (assets.any { !it.isDone && it.id !in selectedAssetAllocations }) {
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
            }

            // ========== DETAILS ==========
            ExpandableSectionCard(
                icon = MaterialSymbols.EDIT,
                title = stringResource(R.string.expense_entry_section_details),
                summary = detailsSummary,
                initiallyExpanded = notes.isNotBlank() || orderNumber.isNotBlank() || isIgnored
            ) {
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

            // ========== LINE ITEMS ==========
            ExpandableSectionCard(
                icon = MaterialSymbols.RECEIPT,
                title = stringResource(R.string.expense_entry_section_items),
                summary = itemsSummary,
                initiallyExpanded = expenseItems.isNotEmpty()
            ) {
                com.example.sparely.ui.components.ExpenseItemsList(
                    items = expenseItems,
                    onItemsChanged = { newItems ->
                        expenseItems.clear()
                        expenseItems.addAll(newItems)
                    },
                    currencySymbol = currencySymbol
                )

                // How the items compare to the amount being saved
                val amountValue = amountText.toSafeDouble()
                if (itemsTotal > 0.0) {
                    val difference = (amountValue ?: 0.0) - itemsTotal
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.expense_items_total, itemsTotal.formatCurrency(currencySymbol)),
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (amountValue != null && kotlin.math.abs(difference) >= 0.005) {
                            Text(
                                text = if (difference > 0) {
                                    stringResource(R.string.expense_items_unitemized, difference.formatCurrency(currencySymbol))
                                } else {
                                    stringResource(R.string.expense_items_over_amount, (-difference).formatCurrency(currencySymbol))
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (difference > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                            )
                            SparelyTextButton(onClick = {
                                amountText = itemsTotal.formatCurrency("")
                                amountError = null
                                amountFollowsItems = true
                            }) {
                                Text(stringResource(R.string.expense_items_use_total))
                            }
                        }
                    }
                }
            }
        }

        // ========== STICKY ACTION BAR ==========
        // Always reachable, so saving never requires scrolling past the optional sections.
        // Leaving goes through the top bar's back arrow, which asks before dropping changes
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // The bar's colour runs under the system nav bar; with the keyboard up this is 0
                    .navigationBarsPadding()
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
                val actionLabel = stringResource(if (isEditMode) R.string.save_changes else R.string.add_expense)
                val amountValue = amountText.toSafeDouble()?.takeIf { it > 0.0 }
                SparelyButton(
                    onClick = ::submit,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    icon = { MaterialSymbolIcon(icon = MaterialSymbols.CHECK, contentDescription = null, size = 20.dp) }
                ) {
                    Text(
                        text = if (amountValue != null) {
                            stringResource(R.string.expense_entry_save_with_amount, actionLabel, amountValue.formatCurrency(currencySymbol))
                        } else {
                            actionLabel
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
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

    if (assetSelectorExpanded) {
        AssetSelectionDialog(
            isOpen = assetSelectorExpanded,
            selectedAssetIds = selectedAssetAllocations.keys,
            // Finished projects aren't offered for new spending
            assets = assets.filter { !it.isDone },
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

/**
 * The top of the form: the amount in large type, the title under it and [footer] (the date) last,
 * on a card tinted by the chosen category.
 */
@Composable
private fun AmountHeroCard(
    category: ExpenseCategory,
    currencySymbol: String,
    amountText: String,
    onAmountChange: (String) -> Unit,
    amountError: String?,
    amountFocus: FocusRequester,
    onAmountNext: () -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    descriptionError: String?,
    descriptionFocus: FocusRequester,
    onDescriptionDone: () -> Unit,
    footer: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val categoryColor by animateColorAsState(colors.categoryColor(category), label = "heroCategoryColor")
    val amountLabel = stringResource(R.string.expense_entry_amount_label)
    var amountFocused by remember { mutableStateOf(false) }
    var amountFieldValue by rememberAmountFieldValue(amountText)
    val descriptionLabel = stringResource(R.string.expense_entry_description_label)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        color = categoryColor.copy(alpha = 0.10f).compositeOver(colors.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                MaterialSymbolIcon(icon = getCategoryIcon(category), contentDescription = null, tint = categoryColor)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Long amounts step down a size so they don't push off the card
            val amountStyle = (if (amountText.length > 8) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium)
                .copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            // Sized to the digits plus room for the cursor so the symbol stays beside them; an
            // intrinsic width leaves the text a hair wider than the field, which then scrolls sideways
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val amountWidth = with(density) {
                textMeasurer.measure(amountText.ifEmpty { "0" }, amountStyle, maxLines = 1).size.width.toDp() + 8.dp
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = currencySymbol,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
                BasicTextField(
                    value = amountFieldValue,
                    onValueChange = { newValue ->
                        amountFieldValue = newValue
                        if (newValue.text != amountText) onAmountChange(newValue.text)
                    },
                    singleLine = true,
                    textStyle = amountStyle.copy(color = if (amountError != null) colors.error else colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = {
                        settleAmountExpression(amountText, onAmountChange)
                        onAmountNext()
                    }),
                    modifier = Modifier
                        .width(amountWidth)
                        .widthIn(min = 40.dp)
                        .focusRequester(amountFocus)
                        .onFocusChanged { state ->
                            // Leaving the field settles any math into its result
                            if (amountFocused && !state.isFocused) settleAmountExpression(amountText, onAmountChange)
                            amountFocused = state.isFocused
                        }
                        .semantics { contentDescription = amountLabel },
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.Center) {
                            if (amountText.isEmpty()) {
                                Text("0", style = amountStyle, color = colors.onSurfaceVariant.copy(alpha = 0.4f))
                            }
                            innerTextField()
                        }
                    }
                )
            }
            if (amountError != null) {
                Text(amountError, style = MaterialTheme.typography.labelMedium, color = colors.error)
            }
            AmountCalculatorKeys(
                value = amountText,
                onValueChange = onAmountChange,
                visible = amountFocused,
                currencySymbol = currencySymbol
            )

            Spacer(modifier = Modifier.height(16.dp))

            BasicTextField(
                value = description,
                onValueChange = onDescriptionChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onDescriptionDone() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(descriptionFocus)
                    .semantics { contentDescription = descriptionLabel },
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(colors.surface)
                            .then(
                                if (descriptionError != null) Modifier.border(1.dp, colors.error, CircleShape) else Modifier
                            )
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        if (description.isEmpty()) {
                            Text(
                                text = stringResource(R.string.expense_entry_description_placeholder),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (descriptionError != null) {
                Text(
                    descriptionError,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            footer()
        }
    }
}

/** Scrollable row of icon tiles, one per category; the chosen one fills with its colour. */
@Composable
private fun CategoryPicker(
    selected: ExpenseCategory,
    onSelect: (ExpenseCategory) -> Unit
) {
    val categories = ExpenseCategory.entries
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (categories.indexOf(selected) - 1).coerceAtLeast(0)
    )
    // A category filled in from a remembered title may be off-screen; bring it into view
    LaunchedEffect(selected) {
        val index = categories.indexOf(selected)
        val layout = listState.layoutInfo
        val fullyVisible = layout.visibleItemsInfo.any {
            it.index == index && it.offset >= 0 && it.offset + it.size <= layout.viewportEndOffset
        }
        if (!fullyVisible) listState.animateScrollToItem((index - 1).coerceAtLeast(0))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.expense_entry_category_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { item ->
                CategoryTile(category = item, selected = item == selected, onClick = { onSelect(item) })
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: ExpenseCategory,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val categoryColor = colors.categoryColor(category)
    val container by animateColorAsState(if (selected) categoryColor else colors.surfaceContainerHigh, label = "tileContainer")
    val iconTint by animateColorAsState(if (selected) colors.onCategoryColor(category) else categoryColor, label = "tileIcon")
    // Selected tiles morph from a circle to a rounded square
    val corner by animateDpAsState(if (selected) 18.dp else 28.dp, label = "tileCorner")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(76.dp)
            .clip(ExpressiveShapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(corner))
                .background(container),
            contentAlignment = Alignment.Center
        ) {
            MaterialSymbolIcon(icon = getCategoryIcon(category), contentDescription = null, tint = iconTint, size = 26.dp)
        }
        Text(
            text = category.displayName(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Plain card grouping related fields. */
@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/** Card for an optional section: collapsed, its header shows a one-line [summary] of what's set inside. */
@Composable
private fun ExpandableSectionCard(
    @DrawableRes icon: Int,
    title: String,
    summary: String,
    initiallyExpanded: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .semantics {
                        if (expanded) collapse { expanded = false; true } else expand { expanded = true; true }
                    }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialSymbolIcon(
                        icon = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        size = 20.dp
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                MaterialSymbolIcon(
                    icon = MaterialSymbols.ARROW_DROP_DOWN,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

/** A linked asset with its share of this expense and a way to unlink it. */
@Composable
private fun LinkedAssetRow(
    asset: Asset,
    percentage: Double,
    onPercentageChange: (Double) -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(asset.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${asset.category.displayName()} · ${stringResource(R.string.expense_entry_asset_allocation)} ${percentage.formatPercent(0)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRemove) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.CLOSE,
                        contentDescription = stringResource(R.string.expense_entry_remove_asset),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Slider(
                value = percentage.toFloat(),
                onValueChange = { onPercentageChange(it.toDouble()) },
                valueRange = 0.01f..1.0f,
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp)
            )
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

    SparelyBottomSheet(
        isOpen = isOpen,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // The price comes from this expense, so it's context rather than a field
            SparelySheetHeader(
                title = stringResource(R.string.asset_create_title),
                subtitle = stringResource(R.string.asset_create_price, assetPrice.formatCurrency()),
                icon = MaterialSymbols.SHOPPING_BAG
            )

            SparelyTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.assets_name)) },
                placeholder = { Text(stringResource(R.string.asset_name_placeholder)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            SheetSectionLabel(stringResource(R.string.category_label))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.example.sparely.domain.model.AssetCategory.entries.forEach { cat ->
                    SparelyChip(
                        selected = cat == category,
                        onClick = { category = cat },
                        label = { Text(cat.displayName()) }
                    )
                }
            }

            SparelyTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.assets_description_optional)) },
                placeholder = { Text(stringResource(R.string.asset_notes_placeholder)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 2
            )

            SheetActions(
                confirmLabel = stringResource(R.string.asset_create_confirm),
                onConfirm = {
                    if (name.isNotBlank()) {
                        onConfirm(name, category, description.takeIf { it.isNotBlank() } ?: "")
                    }
                },
                confirmEnabled = name.isNotBlank(),
                dismissLabel = stringResource(R.string.action_cancel),
                onDismiss = onDismiss
            )
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
