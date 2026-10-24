@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.sparely.ui.screens

import com.example.sparely.domain.model.nextDueDate
import com.example.sparely.domain.model.perMonth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.res.pluralStringResource
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sparely.data.repository.RecurringPaymentUndo
import com.example.sparely.domain.model.Asset
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.Necessity
import com.example.sparely.domain.model.defaultNecessity
import com.example.sparely.domain.model.ExpenseItem
import com.example.sparely.domain.model.ExpenseType
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.PaymentMethod
import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.RecurringExpenseInput
import com.example.sparely.domain.model.RecurringFrequency
import com.example.sparely.domain.model.DetectedRecurringTransaction
import com.example.sparely.domain.model.Store
import com.example.sparely.domain.model.StoreInput
import com.example.sparely.domain.model.displayName
import com.example.sparely.domain.model.predictNextAmount
import com.example.sparely.ui.components.CategorySelector
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.components.FrequencySelector
import com.example.sparely.ui.components.FieldDescription
import com.example.sparely.ui.components.FormSection
import com.example.sparely.ui.components.PaymentMethodSelector
import com.example.sparely.ui.components.RequiredFieldLabel
import com.example.sparely.ui.components.SearchableStoreSelector
import com.example.sparely.ui.components.SectionHeader
import com.example.sparely.ui.components.SparelyAlertDialog
import com.example.sparely.ui.components.SparelyBottomSheet
import com.example.sparely.ui.components.*
import com.example.sparely.ui.components.SparelyChip
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.SparelyTextField
import com.example.sparely.ui.components.SparelyTonalButton
import com.example.sparely.ui.components.AssetSelectionDialog
import com.example.sparely.ui.theme.ExpressiveShapes
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.getCategoryColor
import com.example.sparely.ui.theme.getCategoryIcon
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.theme.warning
import com.sparely.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import com.example.sparely.ui.utils.filterAmountExpressionInput
import com.example.sparely.ui.utils.filterCurrencyInput
import com.example.sparely.ui.utils.toSafeDouble
import com.example.sparely.ui.utils.toSafeDatePickerMillis
import com.example.sparely.ui.utils.formatCurrency
/** The list's sections, in the order they show. */
private enum class RecurringGroup(val titleRes: Int, @androidx.annotation.DrawableRes val icon: Int) {
    TO_PAY(R.string.rec_group_to_pay, MaterialSymbols.WARNING),
    THIS_MONTH(R.string.rec_group_this_month, MaterialSymbols.CALENDAR_MONTH),
    LATER(R.string.rec_group_later, MaterialSymbols.SCHEDULE),
    PAUSED(R.string.rec_group_paused, MaterialSymbols.BLOCK)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurringExpensesScreen(
    recurringExpenses: List<RecurringExpense>,
    recurringPaidRecords: List<com.example.sparely.data.local.RecurringExpensePaidEntity> = emptyList(),
    smartVaults: List<SmartVault> = emptyList(),
    stores: List<Store> = emptyList(),
    assets: List<Asset> = emptyList(),
    onAddRecurring: (RecurringExpenseInput) -> Unit,
    pendingDetectedRecurring: DetectedRecurringTransaction? = null,
    // Patterns found in spending history that aren't saved as recurring payments yet
    detectedRecurring: List<DetectedRecurringTransaction> = emptyList(),
    onAddDetected: (DetectedRecurringTransaction) -> Unit = {},
    onAddDetectedRecurring: (RecurringExpenseInput, DetectedRecurringTransaction) -> Unit = { _, _ -> },
    onClearPendingDetectedRecurring: () -> Unit = {},
    onUpdateRecurring: (RecurringExpense) -> Unit,
    onDeleteRecurring: (Long) -> Unit,
    onMarkProcessed: (Long) -> Unit,
    onPayEarly: (recurringExpenseId: Long, dueDate: LocalDate, amountPaid: Double, paidDate: LocalDate, notes: String?) -> Unit = { _, _, _, _, _ -> },
    // Payments marked in this session that can still be taken back, keyed by recurring expense id
    recurringPaymentUndos: Map<Long, RecurringPaymentUndo> = emptyMap(),
    // Bills that came due and wait to be paid (their amount is frozen)
    awaitingPaymentIds: Set<Long> = emptySet(),
    onUndoPayment: (Long) -> Unit = {},
    onCreateStore: suspend (StoreInput) -> Store? = { null },
    onEditStore: (Store) -> Unit = {},
    onDeleteStore: (Store) -> Unit = {},
    brandfetchClientId: String? = null,
    paymentMethods: List<PaymentMethod> = emptyList(),
    onManagePaymentMethods: () -> Unit = {},
    brandSearchResults: List<com.example.sparely.data.remote.BrandfetchBrand> = emptyList(),
    onBrandSearch: (String) -> Unit = {},
    /** Figures for the on-device AI section; it hides itself when AI is off. */
    aiFacts: List<com.example.sparely.ai.Fact> = emptyList(),
    pendingVariableBills: List<com.example.sparely.domain.model.PendingVariableBill> = emptyList(),
    currencySymbol: String = "$",
    onConfirmVariableBill: (recurringExpenseId: Long, amount: Double) -> Unit = { _, _ -> }
) {
    var isDialogVisible by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<RecurringExpense?>(null) }
    var pendingPrefill by remember { mutableStateOf<RecurringExpenseInput?>(null) }
    var expenseToDelete by remember { mutableStateOf<RecurringExpense?>(null) }
    var expenseToPayEarly by remember { mutableStateOf<RecurringExpense?>(null) }
    var openId by remember { mutableStateOf<Long?>(null) }

    // Active payments first, soonest due on top; paused ones go to the bottom
    val sortedExpenses = remember(recurringExpenses) {
        recurringExpenses.sortedWith(
            compareBy<RecurringExpense> { !it.isActive }
                .thenBy { calculateNextDue(it) ?: it.startDate }
        )
    }

    val listState = rememberLazyListState()
    // The FAB shows its label at the top of the list and shrinks to an icon once scrolling starts
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Content padding (not Modifier.padding) so rows don't clip, and room to scroll past the FAB
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
        ) {
            // Defensive: never count a detected pattern that already has a saved recurring payment
            val savedNames = recurringExpenses.map { it.description.trim().lowercase() }.toSet()
            val unsavedDetected = detectedRecurring.filter { it.description.trim().lowercase() !in savedNames }

            // Bills waiting for their real amount come first: nothing else moves until they're confirmed.
            if (pendingVariableBills.isNotEmpty()) {
                item(key = "pending_variable_bills") {
                    com.example.sparely.ui.components.PendingVariableBillsCard(
                        bills = pendingVariableBills,
                        currencySymbol = currencySymbol,
                        onConfirm = onConfirmVariableBill,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
            if (sortedExpenses.isNotEmpty() || unsavedDetected.isNotEmpty()) {
                item(key = "overview") {
                    RecurringHero(expenses = sortedExpenses, detected = unsavedDetected)
                }
            }
            item(key = "ai") {
                val aiLanguage = com.example.sparely.ui.components.LocalOnDeviceAi.current.language
                com.example.sparely.ui.components.OnDeviceAiSection(
                    title = stringResource(R.string.ai_recurring_title),
                    facts = aiFacts,
                    task = com.example.sparely.ai.AiTask.RECURRING.text(aiLanguage),
                    askSlot = "recurring",
                    askPlaceholder = stringResource(R.string.ai_recurring_ask_placeholder),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            // Grouped by what they need: to be paid now, this month, later, paused
            val today = LocalDate.now()
            val isPaid = { expense: RecurringExpense ->
                recurringPaidRecords.any { it.recurringExpenseId == expense.id && it.dueDate == calculateNextDue(expense) }
            }
            val groups = sortedExpenses.groupBy { expense ->
                val days = calculateNextDue(expense, today)?.let { ChronoUnit.DAYS.between(today, it).toInt() }
                when {
                    !expense.isActive -> RecurringGroup.PAUSED
                    expense.id in awaitingPaymentIds -> RecurringGroup.TO_PAY
                    !isPaid(expense) && days != null && days <= maxOf(expense.reminderDaysBefore, 3) -> RecurringGroup.TO_PAY
                    days != null && days <= 30 -> RecurringGroup.THIS_MONTH
                    else -> RecurringGroup.LATER
                }
            }
            for (group in RecurringGroup.entries) {
                val list = groups[group].orEmpty()
                if (list.isEmpty()) continue
                item(key = "header_${group.name}") {
                    ExpressiveListHeader(
                        title = stringResource(group.titleRes),
                        icon = group.icon,
                        trailing = list.sumOf { if (it.isVariableAmount) it.predictNextAmount() else it.amount }.formatCurrency()
                    )
                }
                itemsIndexed(list, key = { _, expense -> expense.id }) { index, expense ->
                    RecurringExpenseRow(
                        expense = expense,
                        shape = segmentedShape(index, list.size),
                        modifier = Modifier.segmentSpacing(index),
                        store = stores.find { it.id == expense.storeId },
                        brandfetchClientId = brandfetchClientId,
                        isAlreadyPaid = isPaid(expense),
                        isAwaitingPayment = expense.id in awaitingPaymentIds,
                        onOpen = { openId = expense.id },
                        // The quick check only where paying is what the row is waiting for
                        onMarkPaid = if (group == RecurringGroup.TO_PAY) { { onMarkProcessed(expense.id) } } else null
                    )
                }
            }

            if (unsavedDetected.isNotEmpty()) {
                item(key = "detected") {
                    DetectedRecurringCard(detected = unsavedDetected, onAdd = onAddDetected)
                }
            }
            if (sortedExpenses.isEmpty()) {
                item(key = "empty") {
                    EmptyRecurringState(onAddRecurring = {
                        editingExpense = null
                        isDialogVisible = true
                    })
                }
            }
        }

        ExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            onClick = {
                editingExpense = null
                isDialogVisible = true
            },
            expanded = fabExpanded,
            icon = {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.ADD,
                    contentDescription = if (fabExpanded) null else stringResource(R.string.recurring_add_button)
                )
            },
            text = { Text(stringResource(R.string.recurring_add_button)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }

    sortedExpenses.find { it.id == openId }?.let { expense ->
        val nextDue = calculateNextDue(expense)
        RecurringDetailSheet(
            expense = expense,
            nextDue = nextDue,
            store = stores.find { it.id == expense.storeId },
            brandfetchClientId = brandfetchClientId,
            paidWith = paymentMethods.find { it.id == expense.paymentMethodId }?.name
                ?: if (expense.deductFromMainAccount) stringResource(R.string.dash_main_account) else null,
            isAlreadyPaid = recurringPaidRecords.any { it.recurringExpenseId == expense.id && it.dueDate == nextDue },
            isAwaitingPayment = expense.id in awaitingPaymentIds,
            onDismiss = { openId = null },
            onMarkPaid = { onMarkProcessed(expense.id) },
            onPayEarly = { expenseToPayEarly = expense },
            // Only while the schedule is still where the payment left it
            onUndoPayment = recurringPaymentUndos[expense.id]
                ?.takeIf { it.scheduledNextRunAt == expense.nextRunAt }
                ?.let { { onUndoPayment(expense.id) } },
            onToggleActive = { onUpdateRecurring(expense.copy(isActive = !expense.isActive)) },
            onEdit = {
                editingExpense = expense
                isDialogVisible = true
            },
            onDelete = { expenseToDelete = expense }
        )
    }

    if (isDialogVisible) {
        RecurringExpenseDialog(
            expense = editingExpense,
            prefillInput = pendingPrefill,
            smartVaults = smartVaults,
            stores = stores,
            assets = assets,
            onDismiss = {
                isDialogVisible = false
                editingExpense = null
                pendingPrefill = null
                onClearPendingDetectedRecurring()
            },
            onConfirm = { input, existing ->
                if (existing == null) {
                    if (pendingDetectedRecurring != null) {
                        onAddDetectedRecurring(input, pendingDetectedRecurring)
                    } else {
                        onAddRecurring(input)
                    }
                } else {
                    onUpdateRecurring(
                        existing.copy(
                            description = input.description,
                            amount = input.amount,
                            category = input.category,
                            frequency = input.frequency,
                            startDate = input.startDate,
                            endDate = input.endDate,
                            autoLog = input.autoLog,
                            executeAutomatically = input.executeAutomatically,
                            reminderDaysBefore = input.reminderDaysBefore,
                            notes = input.notes,
                            storeId = input.storeId,
                            paymentMethodId = input.paymentMethodId,
                            includesTax = input.includesTax,
                            deductFromMainAccount = input.deductFromMainAccount,
                            deductedFromVaultId = input.deductedFromVaultId,
                            manualPercentages = input.manualPercentages,
                            isVariableAmount = input.isVariableAmount,
                            type = input.type,
                            items = input.items,
                            assetAllocations = input.assetAllocations,
                            necessityOverride = input.necessityOverride
                        )
                    )
                }
                isDialogVisible = false
                editingExpense = null
                pendingPrefill = null
                onClearPendingDetectedRecurring()
            },
            onCreateStore = onCreateStore,
            onEditStore = onEditStore,
            onDeleteStore = onDeleteStore,
            brandfetchClientId = brandfetchClientId,
            paymentMethods = paymentMethods,
            onManagePaymentMethods = onManagePaymentMethods,
            brandSearchResults = brandSearchResults,
            onBrandSearch = onBrandSearch
        )
    }

    LaunchedEffect(pendingDetectedRecurring) {
        if (pendingDetectedRecurring != null) {
            isDialogVisible = true
            editingExpense = null
            pendingPrefill = buildPrefillFromInsight(pendingDetectedRecurring)
        }
    }
    if (expenseToDelete != null) {
        SparelyAlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text(stringResource(R.string.recurring_delete_confirm_title)) },
            text = { Text(stringResource(R.string.recurring_delete_confirm_message, expenseToDelete?.description.orEmpty())) },
            confirmButton = {
                SparelyTextButton(onClick = {
                    expenseToDelete?.let { onDeleteRecurring(it.id) }
                    if (expenseToDelete?.id == openId) openId = null
                    expenseToDelete = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                SparelyTextButton(onClick = { expenseToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (expenseToPayEarly != null) {
        val nextDueDate = calculateNextDue(expenseToPayEarly!!) ?: LocalDate.now()
        PayRecurringExpenseEarlyDialog(
            expense = expenseToPayEarly!!,
            nextDueDate = nextDueDate,
            onConfirm = { amountPaid, paidDate, notes ->
                onPayEarly(expenseToPayEarly!!.id, nextDueDate, amountPaid, paidDate, notes)
                expenseToPayEarly = null
            },
            onDismiss = { expenseToPayEarly = null }
        )
    }
}

private fun buildPrefillFromInsight(insight: DetectedRecurringTransaction): RecurringExpenseInput {
    val frequency = when {
        insight.cadenceDays <= 2 -> RecurringFrequency.DAILY
        insight.cadenceDays <= 9 -> RecurringFrequency.WEEKLY
        insight.cadenceDays <= 17 -> RecurringFrequency.BIWEEKLY
        insight.cadenceDays <= 60 -> RecurringFrequency.MONTHLY
        insight.cadenceDays <= 120 -> RecurringFrequency.QUARTERLY
        else -> RecurringFrequency.YEARLY
    }
    return RecurringExpenseInput(
        description = insight.description,
        amount = insight.averageAmount,
        category = insight.suggestedCategory,
        frequency = frequency,
        startDate = LocalDate.now(),
        autoLog = true,
        reminderDaysBefore = 2,
        type = ExpenseType.PRODUCT
    )
}


/** What every active payment costs a month and a year, and the next one due. */
@Composable
private fun RecurringHero(
    expenses: List<RecurringExpense>,
    detected: List<DetectedRecurringTransaction>
) {
    val colors = MaterialTheme.colorScheme
    val onContainer = colors.onPrimaryContainer
    val today = LocalDate.now()
    val active = expenses.filter { it.isActive }
    val paused = expenses.size - active.size
    val savedMonthly = active.sumOf { expense ->
        val amount = if (expense.isVariableAmount) expense.predictNextAmount() else expense.amount
        amount * expense.frequency.perMonth
    }
    // Detected-but-unsaved patterns still cost money every month, so they count toward the total
    val detectedMonthly = detected.sumOf { detectedMonthlyAmount(it) }
    val monthlyTotal = savedMonthly + detectedMonthly
    val next = active.mapNotNull { e -> calculateNextDue(e, today)?.let { e to it } }.minByOrNull { it.second }
    val automatic = active.count { it.executeAutomatically }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        color = colors.primaryContainer,
        contentColor = onContainer
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text(
                    stringResource(R.string.recurring_monthly_total_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = onContainer.copy(alpha = 0.8f)
                )
                SingleLineText(
                    text = monthlyTotal.formatCurrency(),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = onContainer
                )
                Text(
                    stringResource(R.string.recurring_monthly_total_detail, (monthlyTotal * 12).formatCurrency(decimals = 0), active.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer.copy(alpha = 0.8f)
                )
            }
            if (next != null || paused > 0 || automatic > 0 || detected.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    next?.let { (expense, date) ->
                        HeroPill(
                            MaterialSymbols.CALENDAR_MONTH,
                            stringResource(R.string.rec_hero_next, expense.description, date.format(recurringDay))
                        )
                    }
                    if (automatic > 0) {
                        HeroPill(MaterialSymbols.AUTORENEW, pluralStringResource(R.plurals.rec_hero_automatic, automatic, automatic))
                    }
                    if (paused > 0) {
                        HeroPill(MaterialSymbols.BLOCK, pluralStringResource(R.plurals.rec_hero_paused, paused, paused))
                    }
                    if (detected.isNotEmpty()) {
                        HeroPill(MaterialSymbols.LIGHTBULB, pluralStringResource(R.plurals.rec_hero_detected, detected.size, detected.size))
                    }
                }
            }
        }
    }
}

/** "Oct 24", "24 oct." in the user's language. */
private val recurringDay: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMM"))

private val recurringLongDate: DateTimeFormatter
    get() = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)

/** Where a payment stands, in a few words, and the color for it. */
@Composable
private fun recurringStatus(
    expense: RecurringExpense,
    nextDue: LocalDate?,
    isAlreadyPaid: Boolean,
    isAwaitingPayment: Boolean
): Pair<String, Color> {
    val colors = MaterialTheme.colorScheme
    val daysUntil = nextDue?.let { ChronoUnit.DAYS.between(LocalDate.now(), it).toInt() }
    return when {
        !expense.isActive -> stringResource(R.string.recurring_paused) to colors.onSurfaceVariant
        isAwaitingPayment -> stringResource(R.string.recurring_awaiting_payment) to colors.error
        isAlreadyPaid -> stringResource(R.string.recurring_paid) to colors.success
        nextDue == null || daysUntil == null -> stringResource(R.string.recurring_active) to colors.onSurfaceVariant
        else -> "${nextDue.format(recurringDay)} · ${formatCountdown(daysUntil)}" to when {
            daysUntil < 0 -> colors.error
            daysUntil <= expense.reminderDaysBefore -> colors.warning
            else -> colors.onSurfaceVariant
        }
    }
}

@Composable
private fun RecurringIcon(expense: RecurringExpense, store: Store?, brandfetchClientId: String?) {
    val hasStoreLogo = store?.getBrandfetchLogoUrl(brandfetchClientId) != null
    val tint = getCategoryColor(expense.category)
    Surface(
        shape = CircleShape,
        color = if (hasStoreLogo) Color.Transparent else tint.copy(alpha = 0.14f),
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (hasStoreLogo) {
                com.example.sparely.ui.components.StoreIcon(store = store!!, brandfetchClientId = brandfetchClientId, size = 44)
            } else {
                MaterialSymbolIcon(icon = getCategoryIcon(expense.category), contentDescription = null, tint = tint, size = 22.dp)
            }
        }
    }
}

/** One payment: its logo, name and where it stands, its amount and how often, and a ✓ when due. */
@Composable
private fun RecurringExpenseRow(
    expense: RecurringExpense,
    store: Store?,
    brandfetchClientId: String?,
    isAlreadyPaid: Boolean,
    isAwaitingPayment: Boolean,
    onOpen: () -> Unit,
    onMarkPaid: (() -> Unit)?,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val (status, statusColor) = recurringStatus(expense, calculateNextDue(expense), isAlreadyPaid, isAwaitingPayment)
    Surface(onClick = onOpen, modifier = modifier.fillMaxWidth(), shape = shape, color = colors.surfaceContainer) {
        Row(
            // Paused payments are dimmed so active ones stand out
            modifier = Modifier
                .alpha(if (expense.isActive) 1f else 0.6f)
                .padding(start = 16.dp, end = if (onMarkPaid != null) 8.dp else 16.dp, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RecurringIcon(expense, store, brandfetchClientId)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    expense.description,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (statusColor == colors.onSurfaceVariant) FontWeight.Normal else FontWeight.Medium,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (expense.isVariableAmount) "≈ ${expense.predictNextAmount().formatCurrency()}" else expense.amount.formatCurrency(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    expense.frequency.displayName(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            if (onMarkPaid != null) {
                FilledTonalIconButton(onClick = onMarkPaid) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.CHECK,
                        contentDescription = stringResource(R.string.recurring_mark_paid),
                        size = 20.dp
                    )
                }
            }
        }
    }
}

/**
 * Everything about one payment: what it costs a month and a year, when it's next due, how it's
 * paid, and its actions (mark paid, pay early, undo, pause, edit, delete).
 */
@Composable
private fun RecurringDetailSheet(
    expense: RecurringExpense,
    nextDue: LocalDate?,
    store: Store?,
    brandfetchClientId: String?,
    paidWith: String?,
    isAlreadyPaid: Boolean,
    isAwaitingPayment: Boolean,
    onDismiss: () -> Unit,
    onMarkPaid: () -> Unit,
    onPayEarly: () -> Unit,
    onUndoPayment: (() -> Unit)?,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val amount = if (expense.isVariableAmount) expense.predictNextAmount() else expense.amount
    val (status, statusColor) = recurringStatus(expense, nextDue, isAlreadyPaid, isAwaitingPayment)
    val daysUntil = nextDue?.let { ChronoUnit.DAYS.between(LocalDate.now(), it).toInt() }
    val canPayEarly = daysUntil != null && daysUntil >= 0 && daysUntil <= getMaxDaysBefore(expense.frequency)
    // A bill that already came due can always be marked paid, whatever the next date is
    val canMarkPaid = expense.isActive && (isAwaitingPayment || (!isAlreadyPaid && daysUntil != null && daysUntil <= 5))

    SparelyBottomSheet(isOpen = true, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                RecurringIcon(expense, store, brandfetchClientId)
                Column(modifier = Modifier.weight(1f)) {
                    Text(expense.description, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOf(expense.category.displayName(), expense.frequency.displayName()).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            // The amount and when it's next due
            Surface(shape = ExpressiveShapes.large, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(if (expense.isVariableAmount) R.string.rec_next_amount_estimate else R.string.rec_next_amount),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        (if (expense.isVariableAmount) "≈ " else "") + amount.formatCurrency(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (nextDue != null && expense.isActive && !isAwaitingPayment && !isAlreadyPaid)
                            stringResource(R.string.rec_due_on, nextDue.format(recurringLongDate), formatCountdown(daysUntil ?: 0))
                        else status,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (statusColor == colors.onSurfaceVariant || statusColor == colors.warning) colors.onPrimaryContainer else statusColor
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.rec_tile_month), (amount * expense.frequency.perMonth).formatCurrency(decimals = 0), Modifier.weight(1f).fillMaxHeight())
                StatTile(stringResource(R.string.rec_tile_year), (amount * expense.frequency.perMonth * 12).formatCurrency(decimals = 0), Modifier.weight(1f).fillMaxHeight())
                StatTile(stringResource(R.string.rec_tile_paid_with), paidWith ?: "—", Modifier.weight(1f).fillMaxHeight())
            }

            if (canMarkPaid || (canPayEarly && expense.isActive && !isAlreadyPaid)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (canMarkPaid) {
                        SparelyButton(
                            onClick = {
                                onMarkPaid()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            icon = { MaterialSymbolIcon(icon = MaterialSymbols.CHECK, contentDescription = null, size = 18.dp) }
                        ) { Text(stringResource(R.string.recurring_mark_paid)) }
                    }
                    if (canPayEarly && !isAlreadyPaid) {
                        SparelyTonalButton(
                            onClick = onPayEarly,
                            modifier = Modifier.weight(1f),
                            icon = { MaterialSymbolIcon(icon = MaterialSymbols.SCHEDULE, contentDescription = null, size = 18.dp) }
                        ) { Text(stringResource(R.string.recurring_pay_early)) }
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onUndoPayment != null) {
                    SparelyTonalButton(
                        onClick = onUndoPayment,
                        icon = { MaterialSymbolIcon(icon = MaterialSymbols.UNDO, contentDescription = null, size = 18.dp) }
                    ) { Text(stringResource(R.string.recurring_action_undo_paid)) }
                }
                SparelyTonalButton(
                    onClick = onToggleActive,
                    icon = {
                        MaterialSymbolIcon(
                            icon = if (expense.isActive) MaterialSymbols.BLOCK else MaterialSymbols.PLAY_ARROW,
                            contentDescription = null,
                            size = 18.dp
                        )
                    }
                ) { Text(stringResource(if (expense.isActive) R.string.recurring_action_pause else R.string.recurring_action_resume)) }
                SparelyTonalButton(
                    onClick = onEdit,
                    icon = { MaterialSymbolIcon(icon = MaterialSymbols.EDIT, contentDescription = null, size = 18.dp) }
                ) { Text(stringResource(R.string.action_edit_short)) }
            }

            // How it runs
            val details = buildList {
                add(stringResource(R.string.rec_d_reminder) to
                    if (expense.reminderDaysBefore > 0) pluralStringResource(R.plurals.rec_days_before, expense.reminderDaysBefore, expense.reminderDaysBefore)
                    else stringResource(R.string.rec_d_none))
                add(stringResource(R.string.rec_d_logged) to stringResource(if (expense.executeAutomatically) R.string.rec_d_automatically else R.string.rec_d_when_you_confirm))
                add(stringResource(R.string.rec_d_since) to expense.startDate.format(recurringLongDate))
                expense.endDate?.let { add(stringResource(R.string.rec_d_until) to it.format(recurringLongDate)) }
            }
            ExpressiveListHeader(title = stringResource(R.string.sav_details_header), icon = MaterialSymbols.INFO)
            Column {
                details.forEachIndexed { index, (label, value) ->
                    SegmentedListItem(
                        title = label,
                        shape = segmentedShape(index, details.size),
                        modifier = Modifier.segmentSpacing(index),
                        containerColor = colors.surfaceContainerHigh,
                        trailing = { Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
            expense.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }

            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error)
            ) {
                MaterialSymbolIcon(icon = MaterialSymbols.DELETE, contentDescription = null, size = 18.dp, tint = colors.error)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.delete))
            }
        }
    }
}

@Composable
private fun EmptyRecurringState(onAddRecurring: () -> Unit) {
    ExpressiveEmptyState(
        icon = MaterialSymbols.SCHEDULE,
        title = stringResource(R.string.recurring_empty_title),
        description = stringResource(R.string.recurring_empty_desc),
        action = {
            SparelyButton(
                onClick = onAddRecurring,
                icon = { MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null, size = 20.dp) }
            ) {
                Text(stringResource(R.string.recurring_add_button))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringExpenseDialog(
    expense: RecurringExpense?,
    prefillInput: RecurringExpenseInput? = null,
    smartVaults: List<SmartVault>,
    stores: List<Store>,
    assets: List<Asset> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (RecurringExpenseInput, RecurringExpense?) -> Unit,
    onCreateStore: suspend (StoreInput) -> Store?,
    onEditStore: (Store) -> Unit,
    onDeleteStore: (Store) -> Unit,
    brandfetchClientId: String?,
    paymentMethods: List<PaymentMethod>,
    onManagePaymentMethods: () -> Unit,
    brandSearchResults: List<com.example.sparely.data.remote.BrandfetchBrand> = emptyList(),
    onBrandSearch: (String) -> Unit
) {
    var description by remember { mutableStateOf(expense?.description ?: prefillInput?.description ?: "") }
    var amountText by remember {
        mutableStateOf(
            when {
                expense != null -> "${expense.amount}"
                prefillInput != null -> prefillInput.amount.formatCurrency("", 2)
                else -> ""
            }
        )
    }
    var category by remember { mutableStateOf(expense?.category ?: prefillInput?.category ?: ExpenseCategory.OTHER) }
    var expenseType by remember { mutableStateOf(expense?.type ?: prefillInput?.type ?: ExpenseType.PRODUCT) }
    var frequency by remember { mutableStateOf(expense?.frequency ?: prefillInput?.frequency ?: RecurringFrequency.MONTHLY) }
    var startDate by remember { mutableStateOf(expense?.startDate ?: prefillInput?.startDate ?: LocalDate.now()) }
    var endDate by remember { mutableStateOf(expense?.endDate ?: prefillInput?.endDate) }
    var reminderDays by remember { mutableStateOf(expense?.reminderDaysBefore?.toString() ?: prefillInput?.reminderDaysBefore?.toString() ?: "2") }
    var autoLog by remember { mutableStateOf(expense?.autoLog ?: prefillInput?.autoLog ?: true) }
    var executeAutomatically by remember { mutableStateOf(expense?.executeAutomatically ?: prefillInput?.executeAutomatically ?: false) }
    var notes by remember { mutableStateOf(expense?.notes ?: prefillInput?.notes ?: "") }
    var includesTax by remember { mutableStateOf(expense?.includesTax ?: prefillInput?.includesTax ?: false) }
    var deductFromMainAccount by remember { mutableStateOf(expense?.deductFromMainAccount ?: prefillInput?.deductFromMainAccount ?: false) }
    var deductFromVaultId by remember { mutableStateOf(expense?.deductedFromVaultId ?: prefillInput?.deductedFromVaultId) }
    var showError by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var isVariableAmount by remember { mutableStateOf(expense?.isVariableAmount ?: false) }
    // Editing starts from the saved links; otherwise saving an edit would wipe them
    var selectedAssetAllocations by remember {
        mutableStateOf<Map<Long, Double>>(expense?.assetAllocations ?: prefillInput?.assetAllocations ?: emptyMap())
    }
    var assetSelectorExpanded by remember { mutableStateOf(false) }
    var necessityOverride by remember { mutableStateOf(expense?.necessityOverride ?: prefillInput?.necessityOverride) }

    // Store selection state
    var selectedStore by remember(stores, expense?.storeId) {
        mutableStateOf(stores.find { it.id == expense?.storeId })
    }
    var storeSearchQuery by remember { mutableStateOf("") }

    var selectedPaymentMethod by remember { mutableStateOf<PaymentMethod?>(null) }

    // Line Items State
    // Editing starts from the saved items; otherwise saving an edit would wipe the itemized receipt
    val expenseItems = remember {
        androidx.compose.runtime.mutableStateListOf<ExpenseItem>().apply {
            addAll(expense?.items ?: prefillInput?.items ?: emptyList())
        }
    }

    // Collapsible section states
    var allocationsExpanded by remember { mutableStateOf(false) }
    var detailsExpanded by remember { mutableStateOf(false) }
    var lineItemsExpanded by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(paymentMethods, expense) {
        if (selectedPaymentMethod == null) {
            if (expense?.paymentMethodId != null) {
                selectedPaymentMethod = paymentMethods.find { it.id == expense.paymentMethodId }
            } else {
                 val default = paymentMethods.find { it.isDefault }
                 if (default != null) {
                     selectedPaymentMethod = default
                     // Only set default deduct if creating new expense
                     if (expense == null) {
                        deductFromMainAccount = default.defaultDeductFromMainAccount
                     }
                 }
            }
        }
    }

    val activeVaults = remember(smartVaults) { smartVaults.filter { !it.archived } }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }

    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                SparelySheetHeader(
                    title = if (expense == null) stringResource(R.string.recurring_add_title) else stringResource(R.string.recurring_edit_title),
                    icon = MaterialSymbols.SCHEDULE
                )

                if (showError) {
                    Surface(
                        shape = ExpressiveShapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.WARNING,
                                contentDescription = null,
                                size = 20.dp,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = stringResource(R.string.recurring_error_fields),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // ========== SECTION 1: CRITICAL FIELDS ==========
                FormSection(
                    title = stringResource(R.string.expense_entry_section_basics),
                    isCollapsible = false,
                    defaultExpanded = true
                ) { _ ->
                    SparelyTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.onboarding_financial_description_label)) },
                        isError = showError && description.isBlank(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    SparelyTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filterCurrencyInput() },
                        label = { Text(stringResource(R.string.onboarding_financial_amount_label)) },
                        prefix = { Text(stringResource(R.string.currency_prefix)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        isError = showError && (amountText.toSafeDouble() ?: 0.0) <= 0.0,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Category (Required)
                    CategorySelector(selected = category, onSelect = { category = it }, isRequired = true)

                    // Necessity: how essential is this bill (defaults from category)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.recurring_form_necessity_title), style = MaterialTheme.typography.labelLarge)
                        val effectiveNecessity = necessityOverride ?: category.defaultNecessity()
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Necessity.values().forEach { option ->
                                androidx.compose.material3.FilterChip(
                                    selected = effectiveNecessity == option,
                                    onClick = { necessityOverride = option },
                                    label = { Text(option.displayName()) }
                                )
                            }
                        }
                    }

                    // Frequency (Required)
                    FrequencySelector(selected = frequency, onSelect = { frequency = it }, isRequired = true)

                    // Variable Amount Toggle
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SheetToggleRow(
                                title = stringResource(R.string.recurring_variable_amount),
                                description = stringResource(R.string.recurring_variable_amount_desc),
                                checked = isVariableAmount,
                                onCheckedChange = { isVariableAmount = it }
                            )

                            // Show predicted amount if variable and has history
                            if (isVariableAmount && expense?.amountHistory?.isNotEmpty() == true) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val predictedAmount = expense.predictNextAmount()
                                Text(
                                    text = stringResource(R.string.recurring_predicted_amount) + ": ${predictedAmount.formatCurrency()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Show info when variable amount + auto-execute
                            if (isVariableAmount && executeAutomatically) {
                                Spacer(modifier = Modifier.height(12.dp))
                                ExpressiveCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                    shape = ExpressiveShapes.small
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        MaterialSymbolIcon(
                                            icon = MaterialSymbols.INFO,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            size = 20.dp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.recurring_form_variable_title),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                            Text(
                                                text = stringResource(R.string.recurring_form_variable_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                }

                // ========== SECTION 2: SCHEDULE ==========
                FormSection(
                    title = stringResource(R.string.recurring_form_section_schedule),
                    isCollapsible = false,
                    defaultExpanded = true
                ) { _ ->
                    // Start Date (Required)
                    DateSelector(
                        label = stringResource(R.string.onboarding_financial_start_date_label),
                        date = startDate,
                        onDateSelected = { startDate = it }
                    )

                    // End Date (Optional): picked from a calendar, never typed
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val currentEndDate = endDate
                        if (currentEndDate == null) {
                            // Shown as an empty date field; tapping it adds an end date a year out
                            SheetDateField(
                                label = stringResource(R.string.onboarding_financial_end_date_label),
                                value = stringResource(R.string.recurring_form_no_end_date),
                                onClick = { endDate = startDate.plusYears(1) }
                            )
                        } else {
                            DateSelector(
                                label = stringResource(R.string.onboarding_financial_end_date_label),
                                date = currentEndDate,
                                onDateSelected = { endDate = it }
                            )
                            if (currentEndDate.isBefore(startDate)) {
                                Text(
                                    text = stringResource(R.string.recurring_form_end_before_start),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            SparelyTextButton(onClick = { endDate = null }) {
                                Text(stringResource(R.string.recurring_form_remove_end_date))
                            }
                        }
                    }

                    // Reminder Days Before
                    SparelyTextField(
                        value = reminderDays,
                        onValueChange = { reminderDays = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.onboarding_financial_reminder_label)) },
                        supportingText = { Text(stringResource(R.string.recurring_form_reminder_help)) },
                        isError = showError && reminderDays.toIntOrNull() == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // ========== SECTION 3: FINANCIAL ==========
                FormSection(
                    title = stringResource(R.string.expense_entry_section_payment),
                    isCollapsible = false,
                    defaultExpanded = true
                ) { _ ->
                    // Payment Method
                    PaymentMethodSelector(
                        paymentMethods = paymentMethods,
                        selectedMethod = selectedPaymentMethod,
                        onMethodSelected = { method ->
                            selectedPaymentMethod = method
                            method?.let {
                                deductFromMainAccount = !it.isCreditCard
                            }
                        },
                        onManageMethods = onManagePaymentMethods
                    )

                    // Store/Website selector
                    SearchableStoreSelector(
                        stores = stores,
                        selectedStore = selectedStore,
                        onStoreSelected = { selectedStore = it },
                        onCreateStore = onCreateStore,
                        onEditStore = onEditStore,
                        onDeleteStore = onDeleteStore,
                        searchQuery = storeSearchQuery,
                        onSearchQueryChange = { storeSearchQuery = it },
                        brandfetchClientId = brandfetchClientId,
                        brandSearchResults = brandSearchResults,
                        onBrandSearch = onBrandSearch
                    )

                    // Deduct from Main Account Toggle
                    SheetToggleRow(
                        title = stringResource(R.string.recurring_deduct_main_title),
                        description = when {
                            deductFromMainAccount && selectedPaymentMethod?.isCreditCard == true ->
                                stringResource(R.string.recurring_deduct_main_desc_credit_on_warning)
                            !deductFromMainAccount && selectedPaymentMethod?.isCreditCard == true ->
                                stringResource(R.string.recurring_deduct_main_desc_credit_off)
                            else -> stringResource(R.string.recurring_deduct_main_desc_debit)
                        },
                        checked = deductFromMainAccount,
                        onCheckedChange = { deductFromMainAccount = it }
                    )

                    // Vault selection
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (activeVaults.isEmpty()) {
                            Text(
                                text = stringResource(R.string.recurring_no_vaults),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            SparelyExpressiveDropdown(
                                modifier = Modifier.fillMaxWidth(),
                                selectedOption = smartVaults.find { it.id == deductFromVaultId },
                                label = stringResource(R.string.recurring_deduct_vault),
                                options = listOf<SmartVault?>(null) + smartVaults,
                                onOptionSelected = { selectedVault ->
                                    deductFromVaultId = selectedVault?.id
                                },
                                optionLabel = { selectedVault ->
                                    selectedVault?.name ?: stringResource(R.string.expense_entry_none)
                                },
                                supportingText = { vault ->
                                    if (vault == null) {
                                        stringResource(R.string.recurring_vault_none_desc)
                                    } else {
                                        stringResource(R.string.recurring_vault_balance, vault.currentBalance.formatCurrency())
                                    }
                                }
                            )
                        }
                    }
                }

                // ========== SECTION 4: ALLOCATIONS (COLLAPSIBLE) ==========
                FormSection(
                    title = stringResource(R.string.recurring_form_section_assets),
                    isCollapsible = true,
                    defaultExpanded = allocationsExpanded,
                    helpText = stringResource(R.string.recurring_form_section_assets_help)
                ) { isExpanded ->
                    allocationsExpanded = isExpanded

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
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
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
                                                        },
                                                        modifier = Modifier.size(40.dp)
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
                                                            "${String.format("%.0f", percentage * 100)}%",
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

                            // Add asset button
                            if (selectedAssetAllocations.size < assets.size) {
                                FilledTonalButton(
                                    onClick = { assetSelectorExpanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.expense_entry_add_asset))
                                }
                            }
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
                        title = stringResource(R.string.recurring_form_link_asset_title)
                    )
                }

                // ========== SECTION 5: DETAILS (COLLAPSIBLE) ==========
                FormSection(
                    title = stringResource(R.string.recurring_form_section_automation),
                    isCollapsible = true,
                    defaultExpanded = detailsExpanded,
                    helpText = stringResource(R.string.recurring_form_section_automation_help)
                ) { isExpanded ->
                    detailsExpanded = isExpanded

                    // Notes field
                    SparelyTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.onboarding_financial_notes_label)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 2
                    )

                    // Auto-execute toggle
                    SheetToggleRow(
                        title = stringResource(R.string.recurring_execute_auto),
                        description = stringResource(R.string.recurring_form_execute_help),
                        checked = executeAutomatically,
                        onCheckedChange = { executeAutomatically = it }
                    )
                }

                // ========== SECTION 6: LINE ITEMS (COLLAPSIBLE) ==========
                FormSection(
                    title = stringResource(R.string.expense_entry_section_items),
                    isCollapsible = true,
                    defaultExpanded = lineItemsExpanded,
                    helpText = stringResource(R.string.expense_entry_section_items_help)
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
            }

            fun save() {
                        val amount = amountText.toSafeDouble()
                        val reminder = reminderDays.toIntOrNull()
                        val endDateInvalid = endDate?.isBefore(startDate) == true
                        if (description.isBlank() || amount == null || amount <= 0 || reminder == null || endDateInvalid) {
                            showError = true
                            return
                        }
                        if (isSaving) return
                        isSaving = true
                        scope.launch {
                        // A store name typed but not picked from the list is resolved or created,
                        // matching the expense form (it used to be silently dropped)
                        val typedStore = storeSearchQuery.trim()
                        val finalStoreId = selectedStore?.id ?: if (typedStore.isNotEmpty()) {
                            stores.find { it.name.equals(typedStore, ignoreCase = true) }?.id
                                ?: onCreateStore(StoreInput(name = typedStore))?.id
                        } else {
                            null
                        }
                        val input = RecurringExpenseInput(
                            description = description.trim(),
                            amount = amount,
                            category = category,
                            frequency = frequency,
                            startDate = startDate,
                            endDate = endDate,
                            autoLog = autoLog,
                            executeAutomatically = executeAutomatically,
                            reminderDaysBefore = reminder,
                            notes = notes.takeIf { it.isNotBlank() },
                            storeId = finalStoreId,
                            includesTax = includesTax,
                            deductFromMainAccount = deductFromMainAccount,
                            deductedFromVaultId = deductFromVaultId,
                            paymentMethodId = selectedPaymentMethod?.id,
                            isVariableAmount = isVariableAmount,
                            type = expenseType,
                            items = expenseItems.toList(),
                            assetAllocations = selectedAssetAllocations,
                            necessityOverride = necessityOverride
                        )
                        onConfirm(input, expense)
                        }
            }

            SheetActions(
                confirmLabel = stringResource(R.string.save),
                onConfirm = ::save,
                confirmEnabled = !isSaving,
                dismissLabel = stringResource(R.string.cancel),
                onDismiss = onDismiss
            )
            }
        }
    }


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DateSelector(label: String, date: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    var showDialog by remember { mutableStateOf(false) }
    val millis = remember(date) { date.toSafeDatePickerMillis() }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = millis)
    LaunchedEffect(millis) {
        pickerState.selectedDateMillis = millis
    }

    SheetDateField(
        label = label,
        value = date.format(formatter),
        onClick = { showDialog = true }
    )

    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                SparelyTextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millisSelected ->
                        val selectedDate = Instant.ofEpochMilli(millisSelected).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateSelected(selectedDate)
                    }
                    showDialog = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                SparelyTextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun RecurringFrequency.displayName(): String = when (this) {
    RecurringFrequency.DAILY -> stringResource(R.string.freq_daily)
    RecurringFrequency.WEEKLY -> stringResource(R.string.freq_weekly)
    RecurringFrequency.BIWEEKLY -> stringResource(R.string.freq_biweekly)
    RecurringFrequency.MONTHLY -> stringResource(R.string.freq_monthly)
    RecurringFrequency.QUARTERLY -> stringResource(R.string.freq_quarterly)
    RecurringFrequency.YEARLY -> stringResource(R.string.freq_yearly)
}

@Composable
private fun formatCountdown(daysUntil: Int): String = when {
    daysUntil < 0 -> stringResource(R.string.countdown_overdue, -daysUntil)
    daysUntil == 0 -> stringResource(R.string.countdown_today)
    daysUntil == 1 -> stringResource(R.string.countdown_tomorrow)
    else -> stringResource(R.string.countdown_days, daysUntil)
}

/** Monthly cost of a detected pattern from its average amount and cadence in days. */
private fun detectedMonthlyAmount(pattern: DetectedRecurringTransaction): Double =
    if (pattern.cadenceDays > 0) pattern.averageAmount * (365.25 / 12.0) / pattern.cadenceDays else 0.0

/** Detected patterns not yet saved, as a group: each with how often and an Add button. */
@Composable
private fun DetectedRecurringCard(
    detected: List<DetectedRecurringTransaction>,
    onAdd: (DetectedRecurringTransaction) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column {
        ExpressiveListHeader(
            title = stringResource(R.string.recurring_detected_title),
            icon = MaterialSymbols.AUTORENEW,
            trailing = stringResource(R.string.recurring_detected_per_month, detected.sumOf { detectedMonthlyAmount(it) }.formatCurrency(decimals = 0))
        )
        Text(
            stringResource(R.string.recurring_detected_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
        )
        detected.forEachIndexed { index, pattern ->
            SegmentedListItem(
                title = pattern.description,
                subtitle = cadenceLabel(pattern.averageAmount.formatCurrency(), pattern.cadenceDays) + "\n" +
                    stringResource(R.string.rec_last_on, pattern.lastOccurrence.format(recurringDay)),
                shape = segmentedShape(index, detected.size),
                modifier = Modifier.segmentSpacing(index),
                icon = getCategoryIcon(pattern.suggestedCategory),
                iconTint = getCategoryColor(pattern.suggestedCategory),
                onClick = { onAdd(pattern) },
                trailing = {
                    SparelyTonalButton(
                        onClick = { onAdd(pattern) },
                        icon = { MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null, size = 18.dp) }
                    ) { Text(stringResource(R.string.recurring_detected_add)) }
                }
            )
        }
    }
}

private fun getMaxDaysBefore(frequency: RecurringFrequency): Int {
    return when (frequency) {
        RecurringFrequency.DAILY -> 1
        RecurringFrequency.WEEKLY -> 3
        RecurringFrequency.BIWEEKLY -> 5
        RecurringFrequency.MONTHLY -> 15
        RecurringFrequency.QUARTERLY -> 30
        RecurringFrequency.YEARLY -> 60
    }
}

/** Shared with the home screen and the scheduler, so every screen shows the same due date. */
private fun calculateNextDue(expense: RecurringExpense, today: LocalDate = LocalDate.now()): LocalDate? =
    if (!expense.isActive) null else expense.nextDueDate(today)




@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayRecurringExpenseEarlyDialog(
    expense: RecurringExpense,
    nextDueDate: LocalDate,
    onConfirm: (amountPaid: Double, paidDate: LocalDate, notes: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf(expense.amount.formatCurrency("", 2)) }
    var paidDate by remember { mutableStateOf(LocalDate.now()) }
    var notes by remember { mutableStateOf("") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }

    // Calculate max days before due date based on frequency
    val maxDaysBefore = getMaxDaysBefore(expense.frequency)

    val earliestPaymentDate = nextDueDate.minusDays(maxDaysBefore.toLong())
    val isDateValid = !paidDate.isBefore(earliestPaymentDate) && !paidDate.isAfter(nextDueDate)
    val errorMessage = when {
        paidDate.isBefore(earliestPaymentDate) -> stringResource(R.string.recurring_pay_from_error, earliestPaymentDate.format(dateFormatter))
        paidDate.isAfter(nextDueDate) -> stringResource(R.string.recurring_pay_after_due_error)
        else -> null
    }
    val confirm = {
        if (isDateValid) {
            val amount = amountText.toSafeDouble() ?: expense.amount
            onConfirm(amount, paidDate, notes.takeIf { it.isNotBlank() })
        }
    }

    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SparelySheetHeader(
                    title = stringResource(R.string.recurring_pay_early_title),
                    subtitle = "${expense.description} · Due ${nextDueDate.format(dateFormatter)}",
                    icon = MaterialSymbols.PAYMENTS
                )

                SheetAmountField(
                    value = amountText,
                    onValueChange = { amountText = it.filterAmountExpressionInput() },
                    label = stringResource(R.string.recurring_amount_paid),
                    supportingText = stringResource(R.string.recurring_pay_from_hint, earliestPaymentDate.format(dateFormatter)),
                    autoFocus = false,
                    imeAction = ImeAction.Next,
                    onImeAction = {}
                )

                DateSelector(
                    label = stringResource(R.string.recurring_pay_early_date_label),
                    date = paidDate,
                    onDateSelected = { paidDate = it }
                )

                // Validation error message
                if (errorMessage != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = ExpressiveShapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.WARNING,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                size = 20.dp
                            )
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                SparelyTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.onboarding_financial_notes_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    placeholder = { Text(stringResource(R.string.form_optional)) }
                )
            }

            SheetActions(
                confirmLabel = stringResource(R.string.save),
                onConfirm = confirm,
                confirmEnabled = isDateValid,
                dismissLabel = stringResource(R.string.cancel),
                onDismiss = onDismiss
            )
        }
    }
}
