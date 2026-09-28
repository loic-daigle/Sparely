package com.example.sparely.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.res.pluralStringResource
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.sparely.ui.components.ExpenseTypeSelector
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
import com.sparely.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import com.example.sparely.ui.utils.filterCurrencyInput
import com.example.sparely.ui.utils.toSafeDouble
import com.example.sparely.ui.utils.toSafeDatePickerMillis
import com.example.sparely.ui.utils.formatCurrency
private enum class RecurringOverviewMode {
    OVERVIEW,
    SMART_REMINDERS,
    AUTO_LOGGING,
    UPCOMING
}

private data class RecurringHighlight(val title: String, val detail: String? = null)

private data class RecurringUpcomingPreview(
    val expense: RecurringExpense,
    val dueDate: LocalDate,
    val daysUntil: Int
)

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
    onCreateStore: suspend (StoreInput) -> Store? = { null },
    onEditStore: (Store) -> Unit = {},
    onDeleteStore: (Store) -> Unit = {},
    brandfetchClientId: String? = null,
    paymentMethods: List<PaymentMethod> = emptyList(),
    onManagePaymentMethods: () -> Unit = {},
    brandSearchResults: List<com.example.sparely.data.remote.BrandfetchBrand> = emptyList(),
    onBrandSearch: (String) -> Unit = {}
) {
    var isDialogVisible by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<RecurringExpense?>(null) }
    var pendingPrefill by remember { mutableStateOf<RecurringExpenseInput?>(null) }
    var expenseToDelete by remember { mutableStateOf<RecurringExpense?>(null) }
    var expenseToPayEarly by remember { mutableStateOf<RecurringExpense?>(null) }
    var overviewMode by remember { mutableStateOf(RecurringOverviewMode.OVERVIEW) }

    // Active payments first, soonest due on top; paused ones go to the bottom
    val sortedExpenses = remember(recurringExpenses) {
        recurringExpenses.sortedWith(
            compareBy<RecurringExpense> { !it.isActive }
                .thenBy { calculateNextDue(it) ?: it.startDate }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Content padding (not Modifier.padding) so rows don't clip, and room to scroll past the FAB
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Defensive: never count a detected pattern that already has a saved recurring payment
            val savedNames = recurringExpenses.map { it.description.trim().lowercase() }.toSet()
            val unsavedDetected = detectedRecurring.filter { it.description.trim().lowercase() !in savedNames }

            item(key = "overview") {
                RecurringOverviewCard(
                    expenses = sortedExpenses,
                    detected = unsavedDetected,
                    selectedMode = overviewMode,
                    onModeChange = { overviewMode = it }
                )
            }
            if (unsavedDetected.isNotEmpty()) {
                item(key = "detected") {
                    DetectedRecurringCard(detected = unsavedDetected, onAdd = onAddDetected)
                }
            }
            items(sortedExpenses, key = { it.id }) { expense ->
                val store = stores.find { it.id == expense.storeId }
                RecurringExpenseRow(
                    expense = expense,
                    store = store,
                    brandfetchClientId = brandfetchClientId,
                    onEdit = {
                        editingExpense = expense
                        isDialogVisible = true
                    },
                    onDelete = { expenseToDelete = expense }, // Set expense to delete
                    onToggleActive = { active ->
                        onUpdateRecurring(expense.copy(isActive = active))
                    },
                    onMarkProcessed = { onMarkProcessed(expense.id) },
                    onPayEarly = { expenseToPayEarly = expense },
                    isAlreadyPaid = recurringPaidRecords.any { 
                        it.recurringExpenseId == expense.id && it.dueDate == calculateNextDue(expense)
                    }
                )
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
                .padding(16.dp),
            onClick = {
                editingExpense = null
                isDialogVisible = true
            },
            icon = { MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null) },
            text = { Text(stringResource(R.string.recurring_add_button)) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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


@Composable
private fun RecurringOverviewCard(
    expenses: List<RecurringExpense>,
    detected: List<DetectedRecurringTransaction>,
    selectedMode: RecurringOverviewMode,
    onModeChange: (RecurringOverviewMode) -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    val today = LocalDate.now()
    val activeExpenses = expenses.filter { it.isActive }
    val pausedCount = expenses.count { !it.isActive }
    val upcomingPreviews = activeExpenses.mapNotNull { expense ->
        calculateNextDue(expense, today)?.let { dueDate ->
            val daysUntil = ChronoUnit.DAYS.between(today, dueDate).toInt()
            RecurringUpcomingPreview(expense, dueDate, daysUntil)
        }
    }.sortedBy { it.dueDate }
    val reminderMatches = upcomingPreviews.filter { preview ->
        val lead = preview.expense.reminderDaysBefore
        preview.daysUntil >= 0 && preview.daysUntil <= lead
    }
    val autoLogActive = activeExpenses.filter { it.autoLog }
    val savedMonthly = activeExpenses.sumOf { expense ->
        val amount = if (expense.isVariableAmount) expense.predictNextAmount() else expense.amount
        amount * monthlyFactor(expense.frequency)
    }
    // Detected-but-unsaved patterns still cost money every month, so they count toward the total
    val detectedMonthly = detected.sumOf { detectedMonthlyAmount(it) }
    val monthlyTotal = savedMonthly + detectedMonthly
    val autoLogUpcoming = upcomingPreviews.filter { it.expense.autoLog }

    val highlights = when (selectedMode) {
        RecurringOverviewMode.OVERVIEW -> listOfNotNull(
            RecurringHighlight(
                title = stringResource(R.string.recurring_title),
                detail = stringResource(R.string.recurring_active_paused_count, activeExpenses.size, pausedCount)
            ),
            RecurringHighlight(
                title = stringResource(R.string.recurring_auto_log_enabled),
                detail = stringResource(R.string.recurring_auto_log_count, autoLogActive.size)
            ),
            upcomingPreviews.firstOrNull()?.let {
                RecurringHighlight(
                    title = stringResource(R.string.recurring_next_due_label),
                    detail = stringResource(R.string.recurring_next_due_stat, it.expense.description, it.dueDate.format(dateFormatter), formatCountdown(it.daysUntil))
                )
            } ?: RecurringHighlight(
                title = stringResource(R.string.recurring_next_due_label),
                detail = stringResource(R.string.recurring_no_upcoming)
            )
        )
        RecurringOverviewMode.SMART_REMINDERS -> if (reminderMatches.isEmpty()) {
            listOf(RecurringHighlight(stringResource(R.string.recurring_reminders_all_clear), stringResource(R.string.recurring_reminders_none)))
        } else {
            reminderMatches.take(3).map {
                RecurringHighlight(
                    title = it.expense.description,
                    detail = stringResource(R.string.recurring_reminder_stat, it.expense.reminderDaysBefore, it.dueDate.format(dateFormatter), formatCountdown(it.daysUntil))
                )
            }
        }
        RecurringOverviewMode.AUTO_LOGGING -> when {
            autoLogActive.isEmpty() -> listOf(RecurringHighlight(stringResource(R.string.recurring_auto_log_off), stringResource(R.string.recurring_auto_log_tutorial)))
            autoLogUpcoming.isNotEmpty() -> autoLogUpcoming.take(3).map {
                RecurringHighlight(
                    title = it.expense.description,
                    detail = stringResource(R.string.recurring_auto_log_stat, it.dueDate.format(dateFormatter), formatCountdown(it.daysUntil))
                )
            }
            else -> autoLogActive.take(3).map {
                RecurringHighlight(
                    title = it.description,
                    detail = stringResource(R.string.recurring_auto_log_ready)
                )
            }
        }
        RecurringOverviewMode.UPCOMING -> if (upcomingPreviews.isEmpty()) {
            listOf(RecurringHighlight(stringResource(R.string.recurring_nothing_scheduled), stringResource(R.string.recurring_add_suggestion)))
        } else {
            upcomingPreviews.take(3).map {
                RecurringHighlight(
                    title = it.expense.description,
                    detail = stringResource(R.string.recurring_due_stat, it.dueDate.format(dateFormatter), formatCountdown(it.daysUntil))
                )
            }
        }
    }

    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = 24.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Headline: what all active recurring payments cost per month
            Column {
                Text(
                    text = stringResource(R.string.recurring_monthly_total_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = monthlyTotal.formatCurrency(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(
                        R.string.recurring_monthly_total_detail,
                        (monthlyTotal * 12).formatCurrency(),
                        activeExpenses.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (detected.isNotEmpty()) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.recurring_monthly_total_detected,
                            detected.size,
                            detectedMonthly.formatCurrency(),
                            detected.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = stringResource(R.string.recurring_insights_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.recurring_insights_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (mode in RecurringOverviewMode.entries) {
                    SparelyChip(
                        selected = selectedMode == mode,
                        onClick = { onModeChange(mode) },
                        label = { Text(mode.displayName()) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            for (highlight in highlights) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(highlight.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    highlight.detail?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecurringExpenseRow(
    expense: RecurringExpense,
    store: Store?,
    brandfetchClientId: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onMarkProcessed: () -> Unit,
    onPayEarly: () -> Unit,
    isAlreadyPaid: Boolean = false
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d") }
    val nextDue = calculateNextDue(expense)
    val daysUntil = nextDue?.let { ChronoUnit.DAYS.between(LocalDate.now(), it).toInt() }

    var showMenu by remember { mutableStateOf(false) }

    ExpressiveCard(
        // Paused payments are dimmed so active ones stand out
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (expense.isActive) 1f else 0.6f),
        shape = ExpressiveShapes.medium,
        contentPadding = 16.dp,
        onClick = onEdit
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Box
                val hasStoreLogo = store?.getBrandfetchLogoUrl(brandfetchClientId) != null
                Surface(
                    shape = CircleShape,
                    color = if (hasStoreLogo) Color.Transparent else getCategoryColor(expense.category).copy(alpha = 0.15f),
                    modifier = Modifier.size(56.dp)
                ) {
                     Box(contentAlignment = Alignment.Center) {
                         if (hasStoreLogo) {
                             com.example.sparely.ui.components.StoreIcon(
                                 store = store!!,
                                 brandfetchClientId = brandfetchClientId,
                                 size = 56
                             )
                         } else {
                             MaterialSymbolIcon(
                                 icon = getCategoryIcon(expense.category),
                                 contentDescription = null,
                                 tint = getCategoryColor(expense.category),
                                 size = 28.dp
                             )
                         }
                     }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = expense.description,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = expense.category.displayName(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    // Show amount with variable indicator if applicable
                    if (expense.isVariableAmount) {
                        val predictedAmount = expense.predictNextAmount()
                        Text(
                            text = "~${predictedAmount.formatCurrency()}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = stringResource(R.string.recurring_variable_amount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    } else {
                        Text(
                            text = expense.amount.formatCurrency(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = expense.frequency.displayName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Status / Next Due
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (statusIcon, statusColor, statusText) = when {
                        !expense.isActive -> Triple(MaterialSymbols.BLOCK, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.recurring_paused))
                        isAlreadyPaid -> Triple(MaterialSymbols.CHECK_CIRCLE, MaterialTheme.colorScheme.primary, stringResource(R.string.recurring_paid))
                        daysUntil != null && daysUntil <= expense.reminderDaysBefore -> Triple(MaterialSymbols.WARNING, MaterialTheme.colorScheme.error, stringResource(R.string.recurring_due_soon))
                        else -> Triple(MaterialSymbols.SCHEDULE, MaterialTheme.colorScheme.primary, stringResource(R.string.recurring_active))
                    }
                    
                    MaterialSymbolIcon(
                        icon = statusIcon, 
                        contentDescription = null, 
                        tint = statusColor,
                        size = 18.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isAlreadyPaid || nextDue == null || daysUntil == null -> statusText
                            // Date plus countdown ("Due Mar 3 · in 2d") so urgency is readable at a glance
                            else -> stringResource(R.string.recurring_due_date, nextDue.format(formatter)) +
                                " · " + formatCountdown(daysUntil)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Secondary actions in an overflow menu (full 48dp target); tapping the card edits
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.MORE_VERT,
                            contentDescription = stringResource(R.string.history_more_actions),
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = { MaterialSymbolIcon(icon = MaterialSymbols.EDIT, contentDescription = null, size = 20.dp) }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(if (expense.isActive) R.string.recurring_action_pause else R.string.recurring_action_resume))
                            },
                            onClick = {
                                showMenu = false
                                onToggleActive(!expense.isActive)
                            },
                            leadingIcon = {
                                MaterialSymbolIcon(
                                    icon = if (expense.isActive) MaterialSymbols.BLOCK else MaterialSymbols.PLAY_ARROW,
                                    contentDescription = null,
                                    size = 20.dp
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { MaterialSymbolIcon(icon = MaterialSymbols.DELETE, contentDescription = null, size = 20.dp) },
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.error,
                                leadingIconColor = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }

            val maxDaysBefore = getMaxDaysBefore(expense.frequency)
            val canPayEarly = daysUntil != null && daysUntil >= 0 && daysUntil <= maxDaysBefore
            val isDueSoon = daysUntil != null && daysUntil <= 5

            if (!isAlreadyPaid && expense.isActive) {
                if (isDueSoon) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SparelyTonalButton(
                            onClick = onMarkProcessed,
                            modifier = if (canPayEarly) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                            icon = { MaterialSymbolIcon(icon = MaterialSymbols.CHECK, contentDescription = null, size = 18.dp) }
                        ) {
                            Text(stringResource(R.string.recurring_mark_paid))
                        }
                        if (canPayEarly) {
                            SparelyTonalButton(
                                onClick = onPayEarly,
                                modifier = Modifier.weight(1f),
                                icon = { MaterialSymbolIcon(icon = MaterialSymbols.SCHEDULE, contentDescription = null, size = 18.dp) }
                            ) {
                                Text(stringResource(R.string.recurring_pay_early))
                            }
                        }
                    }
                } else if (canPayEarly) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SparelyTonalButton(
                        onClick = onPayEarly,
                        modifier = Modifier.fillMaxWidth(),
                        icon = { MaterialSymbolIcon(icon = MaterialSymbols.SCHEDULE, contentDescription = null, size = 18.dp) }
                    ) {
                        Text(stringResource(R.string.recurring_pay_early))
                    }
                }
            }
        }
    }
}



@Composable
private fun EmptyRecurringState(onAddRecurring: () -> Unit) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.small,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = 24.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.recurring_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.recurring_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            SparelyButton(onClick = onAddRecurring) {
                Text(stringResource(R.string.recurring_add_button))
            }
        }
    }
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
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = if (expense == null) stringResource(R.string.recurring_add_title) else stringResource(R.string.recurring_edit_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                if (showError) {
                Text(
                    text = stringResource(R.string.recurring_error_fields),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
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
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    SparelyTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filterCurrencyInput() },
                        label = { Text(stringResource(R.string.onboarding_financial_amount_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

                    // Type (Product/Service)
                    ExpenseTypeSelector(selected = expenseType, onSelect = { expenseType = it }, isRequired = false, modifier = Modifier.fillMaxWidth())

                    // Frequency (Required)
                    FrequencySelector(selected = frequency, onSelect = { frequency = it }, isRequired = true)

                    // Variable Amount Toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.recurring_variable_amount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(R.string.recurring_variable_amount_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isVariableAmount,
                                    onCheckedChange = { isVariableAmount = it },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }

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
                }

                // ========== SECTION 2: SCHEDULE ==========
                FormSection(
                    title = stringResource(R.string.recurring_form_section_schedule),
                    isCollapsible = false,
                    defaultExpanded = true
                ) { _ ->
                    // Start Date (Required)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RequiredFieldLabel(stringResource(R.string.onboarding_financial_start_date_label))
                        DateSelector(
                            label = stringResource(R.string.onboarding_financial_start_date_label),
                            date = startDate,
                            onDateSelected = { startDate = it }
                        )
                    }

                    // End Date (Optional): picked from a calendar, never typed
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val currentEndDate = endDate
                        if (currentEndDate == null) {
                            Text(stringResource(R.string.onboarding_financial_end_date_label), style = MaterialTheme.typography.titleSmall)
                            FieldDescription(stringResource(R.string.recurring_form_no_end_date))
                            SparelyTonalButton(
                                onClick = { endDate = startDate.plusYears(1) },
                                icon = { MaterialSymbolIcon(icon = MaterialSymbols.CALENDAR_MONTH, contentDescription = null, size = 18.dp) }
                            ) {
                                Text(stringResource(R.string.recurring_form_add_end_date))
                            }
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
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.onboarding_financial_reminder_label), style = MaterialTheme.typography.titleSmall)
                        SparelyTextField(
                            value = reminderDays,
                            onValueChange = { reminderDays = it.filter { ch -> ch.isDigit() } },
                            label = { Text(stringResource(R.string.onboarding_financial_reminder_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        FieldDescription(stringResource(R.string.recurring_form_reminder_help))
                    }
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

                    // Include Tax
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.recurring_includes_tax), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = includesTax, onCheckedChange = { includesTax = it }, modifier = Modifier.scale(0.8f))
                    }

                    // Deduct from Main Account Toggle
                    ExpressiveCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = ExpressiveShapes.medium
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.recurring_deduct_main_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
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

                    // Vault selection
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.recurring_deduct_vault), style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = if (activeVaults.isEmpty()) stringResource(R.string.recurring_no_vaults) else stringResource(R.string.recurring_choose_vault),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (activeVaults.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            SparelyExpressiveDropdown(
                                modifier = Modifier.fillMaxWidth(),
                                selectedOption = smartVaults.find { it.id == deductFromVaultId },
                                label = stringResource(R.string.recurring_choose_vault),
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
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 2
                    )

                    // Auto-log toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.recurring_auto_log_history), style = MaterialTheme.typography.bodyMedium)
                            FieldDescription(stringResource(R.string.recurring_form_autolog_help))
                        }
                        Switch(checked = autoLog, onCheckedChange = { autoLog = it }, modifier = Modifier.scale(0.8f))
                    }

                    // Auto-execute toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.recurring_execute_auto), style = MaterialTheme.typography.bodyMedium)
                            FieldDescription(stringResource(R.string.recurring_form_execute_help))
                        }
                        Switch(checked = executeAutomatically, onCheckedChange = { executeAutomatically = it }, modifier = Modifier.scale(0.8f))
                    }
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

            // Action Buttons - Fixed at bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SparelyTextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                SparelyButton(
                    onClick = {
                        val amount = amountText.toSafeDouble()
                        val reminder = reminderDays.toIntOrNull()
                        val endDateInvalid = endDate?.isBefore(startDate) == true
                        if (description.isBlank() || amount == null || amount <= 0 || reminder == null || endDateInvalid) {
                            showError = true
                            return@SparelyButton
                        }
                        if (isSaving) return@SparelyButton
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
                    },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.save))
                }
            }
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

    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        SparelyTextButton(onClick = { showDialog = true }) {
            Text(date.format(formatter))
        }
    }

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
private fun RecurringOverviewMode.displayName(): String = when (this) {
    RecurringOverviewMode.OVERVIEW -> stringResource(R.string.overview_mode_overview)
    RecurringOverviewMode.SMART_REMINDERS -> stringResource(R.string.overview_mode_reminders)
    RecurringOverviewMode.AUTO_LOGGING -> stringResource(R.string.overview_mode_autolog)
    RecurringOverviewMode.UPCOMING -> stringResource(R.string.overview_mode_upcoming)
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

/** Detected patterns not yet saved: shown so they can be confirmed with one tap. */
@Composable
private fun DetectedRecurringCard(
    detected: List<DetectedRecurringTransaction>,
    onAdd: (DetectedRecurringTransaction) -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d") }
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentPadding = 16.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.AUTORENEW,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        size = 20.dp
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.recurring_detected_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.recurring_detected_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            detected.forEach { pattern ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pattern.description,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(
                                R.string.recurring_detected_detail,
                                pattern.averageAmount.formatCurrency(),
                                pattern.cadenceDays,
                                pattern.lastOccurrence.format(formatter)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = stringResource(R.string.recurring_detected_per_month, detectedMonthlyAmount(pattern).formatCurrency()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    SparelyTextButton(onClick = { onAdd(pattern) }) {
                        Text(stringResource(R.string.recurring_detected_add))
                    }
                }
            }
        }
    }
}

/** How many times per month a payment of this frequency occurs, for monthly-equivalent totals. */
private fun monthlyFactor(frequency: RecurringFrequency): Double = when (frequency) {
    RecurringFrequency.DAILY -> 365.0 / 12.0
    RecurringFrequency.WEEKLY -> 52.0 / 12.0
    RecurringFrequency.BIWEEKLY -> 26.0 / 12.0
    RecurringFrequency.MONTHLY -> 1.0
    RecurringFrequency.QUARTERLY -> 1.0 / 3.0
    RecurringFrequency.YEARLY -> 1.0 / 12.0
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

private fun calculateNextDue(expense: RecurringExpense, today: LocalDate = LocalDate.now()): LocalDate? {
    if (!expense.isActive) return null

    if (expense.lastProcessedDate == null) {
        // Never been processed - check if start date is still in the future
        if (!expense.startDate.isBefore(today)) {
            // Start date hasn't passed yet, so that's the next due date
            return expense.startDate
        }

        // Start date is in the past, so calculate next occurrence from start date
        var nextDue = expense.startDate
        while (nextDue.isBefore(today)) {
            nextDue = addFrequencyInterval(nextDue, expense.frequency)
        }
        expense.endDate?.let { if (nextDue.isAfter(it)) return null }
        return nextDue
    }

    // Already been processed at least once, calculate next from last processed date
    var nextDue = addFrequencyInterval(expense.lastProcessedDate, expense.frequency)

    // If next due is in the past, keep advancing until we reach a future date
    while (nextDue.isBefore(today)) {
        nextDue = addFrequencyInterval(nextDue, expense.frequency)
    }

    expense.endDate?.let { if (nextDue.isAfter(it)) return null }
    return nextDue
}

/**
 * Add one frequency interval to a date.
 * For monthly/quarterly/yearly, this preserves the day of month (e.g., 25th stays 25th).
 */
private fun addFrequencyInterval(date: LocalDate, frequency: RecurringFrequency): LocalDate {
    return when (frequency) {
        RecurringFrequency.DAILY -> date.plusDays(1)
        RecurringFrequency.WEEKLY -> date.plusWeeks(1)
        RecurringFrequency.BIWEEKLY -> date.plusWeeks(2)
        RecurringFrequency.MONTHLY -> date.plusMonths(1)
        RecurringFrequency.QUARTERLY -> date.plusMonths(3)
        RecurringFrequency.YEARLY -> date.plusYears(1)
    }
}



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
        paidDate.isBefore(earliestPaymentDate) -> "Can only pay from ${earliestPaymentDate.format(dateFormatter)} onwards"
        paidDate.isAfter(nextDueDate) -> "Payment date cannot be after due date"
        else -> null
    }

    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = stringResource(R.string.recurring_pay_early_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                // Expense info card
                ExpressiveCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = ExpressiveShapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = expense.description,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Due: ${nextDueDate.format(dateFormatter)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Pay from: ${earliestPaymentDate.format(dateFormatter)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = expense.amount.formatCurrency(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Amount paid field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RequiredFieldLabel("Amount Paid")
                    SparelyTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filterCurrencyInput() },
                        label = { Text("Amount Paid") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Date paid selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.recurring_pay_early_date_label),
                        style = MaterialTheme.typography.titleSmall
                    )
                    DateSelector(
                        label = stringResource(R.string.recurring_pay_early_date_label),
                        date = paidDate,
                        onDateSelected = { paidDate = it }
                    )
                }

                // Validation error message
                if (errorMessage != null) {
                    ExpressiveCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
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
                                icon = MaterialSymbols.WARNING,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                size = 20.dp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Notes field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.onboarding_financial_notes_label),
                        style = MaterialTheme.typography.titleSmall
                    )
                    SparelyTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.onboarding_financial_notes_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 2,
                        placeholder = { Text("Optional") }
                    )
                }
            }

            // Action Buttons - Fixed at bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SparelyTextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                SparelyButton(
                    onClick = {
                        val amount = amountText.toSafeDouble() ?: expense.amount
                        onConfirm(amount, paidDate, notes.takeIf { it.isNotBlank() })
                    },
                    enabled = isDateValid,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
