package com.example.sparely.ui.screens

import androidx.compose.animation.*
import com.example.sparely.ui.utils.toSafeDouble
import com.example.sparely.ui.utils.filterCurrencyInput
import com.example.sparely.ui.utils.toInputString
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import com.example.sparely.domain.model.*
import com.example.sparely.ui.components.*
import com.example.sparely.ui.theme.*
import com.sparely.app.R
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    expenses: List<Expense>,
    pagedExpenses: List<Expense> = emptyList(),
    canLoadMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    selectedStore: Store? = null,
    selectedStoreHistory: List<Expense> = emptyList(),
    isStoreHistoryLoading: Boolean = false,
    onStoreSelected: (Store) -> Unit = {},
    onClearSelectedStore: () -> Unit = {},
    onNavigateBack: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (Expense) -> Unit,
    onEditExpenseWithAssets: (Expense, Map<Long, Double>) -> Unit = { _, _ -> },
    onDeleteExpense: (Expense) -> Unit,
    onDeleteExpenses: (List<Expense>) -> Unit,
    onDuplicateExpense: (Expense) -> Unit = {},
    stores: List<Store> = emptyList(),
    onCreateStore: suspend (StoreInput) -> Store?,
    onEditStore: (Store) -> Unit,
    onDeleteStore: (Store) -> Unit,
    brandfetchClientId: String? = null,
    brandSearchResults: List<com.example.sparely.data.remote.BrandfetchBrand> = emptyList(),
    onBrandSearch: (String) -> Unit = {},
    onRefundExpense: (Long, Double, List<Long>) -> Unit = { _, _, _ -> },
    paymentMethods: List<com.example.sparely.domain.model.PaymentMethod> = emptyList(),
    vaults: List<com.example.sparely.domain.model.SmartVault> = emptyList(),
    assets: List<com.example.sparely.domain.model.Asset> = emptyList(),
    onLoadAssetAllocationsForExpense: suspend (Long) -> Map<Long, Double> = { emptyMap() },
    highlightExpenseId: Long? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf<ExpenseCategory?>(null) }
    var dateFilter by remember { mutableStateOf(DateRangeFilter.LAST_30_DAYS) }
    var customStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var customEndDate by remember { mutableStateOf<LocalDate?>(null) }
    
    // Expand/collapse detail
    var expandedExpenseId by remember { mutableStateOf<Long?>(null) }

    // Bulk selection mode
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedExpenseIds by remember { mutableStateOf(setOf<Long>()) }
    var showBulkDeleteConfirmation by remember { mutableStateOf(false) }
    
    var showFilterSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var highlightedExpenseId by remember { mutableStateOf(highlightExpenseId) }

    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    var expenseToRefund by remember { mutableStateOf<Expense?>(null) }
    
    // Determine if we are in "Browsing Mode" (All Time, no filters) where infinite scroll applies
    // OR "Filter Mode" where we show the filtered results from the full loaded list.
    val isFiltering = searchQuery.isNotEmpty() || categoryFilter != null || dateFilter != DateRangeFilter.ALL_TIME

    val filteredExpenses = remember(expenses, searchQuery, categoryFilter, dateFilter, customStartDate, customEndDate) {
        expenses.filter { expense ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                expense.description.contains(searchQuery, ignoreCase = true) ||
                expense.amount.toString().contains(searchQuery) ||
                (expense.storeId?.let { id -> stores.find { it.id == id }?.name } ?: "").contains(searchQuery, ignoreCase = true)
            }
            val matchesCategory = categoryFilter == null || expense.category == categoryFilter
            val matchesDate = matchesDate(expense.date, dateFilter, customStartDate, customEndDate)

            matchesSearch && matchesCategory && matchesDate
        }.sortedByDescending { it.date }
    }

    // If not filtering (and thus showing All Time), use pagedExpenses to support infinite scroll.
    // Otherwise, use the filtered subset of the full expenses list.
    val listToDisplay = if (isFiltering) filteredExpenses else pagedExpenses

    val groupedExpenses = remember(listToDisplay) {
        listToDisplay.groupBy { it.date }.toSortedMap(compareByDescending { it })
    }

    val storeStatsSource = if (isFiltering) filteredExpenses else expenses

    val storeStats = remember(storeStatsSource, stores) {
        storeStatsSource
            .filter { it.storeId != null }
            .groupBy { it.storeId }
            .map { (storeId, expenses) ->
                val store = stores.find { it.id == storeId }
                val total = expenses.sumOf { it.amount }
                val count = expenses.size
                Triple(store, total, count)
            }
            .filter { it.first != null }
            .map { Triple(it.first!!, it.second, it.third) }
            .sortedByDescending { it.second }
            .take(5)
    }

    val showQuickResults = searchQuery.isNotBlank() && filteredExpenses.isNotEmpty()
    val showSummary = filteredExpenses.isNotEmpty()
    val showStoreStats = storeStats.isNotEmpty() && !isFiltering
    val activeFilterCount = listOf(
        categoryFilter != null,
        dateFilter != DateRangeFilter.ALL_TIME
    ).count { it }

    fun clearFilters() {
        searchQuery = ""
        categoryFilter = null
        dateFilter = DateRangeFilter.ALL_TIME
        customStartDate = null
        customEndDate = null
    }

    // Mirrors the item order of the LazyColumn below so we can scroll to a given expense.
    // Keep in sync when adding or removing items.
    fun findExpenseIndex(expenseId: Long): Int? {
        var index = 0 // search bar
        if (showQuickResults) index += 1
        index += 1 // filter chips row
        if (showSummary) index += 1
        if (showStoreStats) index += 1
        groupedExpenses.forEach { (_, dailyExpenses) ->
            index += 1 // sticky date header
            dailyExpenses.forEach { expense ->
                if (expense.id == expenseId) return index
                index += 1
            }
        }
        return null
    }

    LaunchedEffect(highlightedExpenseId, groupedExpenses, showQuickResults) {
        highlightedExpenseId?.let { id ->
            expandedExpenseId = id
            findExpenseIndex(id)?.let { target ->
                listState.animateScrollToItem(target)
            }
            delay(2000)
            highlightedExpenseId = null
        }
    }

    // The FAB shows its label at the top of the list and shrinks to an icon once scrolling starts
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            // Content padding (not Modifier.padding) so cards scroll edge-to-edge without clipping
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = if (isSelectionMode) 112.dp else 96.dp
            )
        ) {
            item(key = "search") {
                HistorySearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    activeFilterCount = activeFilterCount,
                    onOpenFilters = { showFilterSheet = true },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            if (showQuickResults) {
                item(key = "quick_results") {
                    Column(
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer, ExpressiveShapes.large)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.history_search_results_quick),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                        filteredExpenses.take(4).forEach { expense ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ExpressiveShapes.small)
                                    .clickable {
                                        expandedExpenseId = expense.id
                                        highlightedExpenseId = expense.id
                                        findExpenseIndex(expense.id)?.let { target ->
                                            scope.launch { listState.animateScrollToItem(target) }
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val categoryColor = getCategoryColor(expense.category)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(categoryColor.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    MaterialSymbolIcon(
                                        icon = getCategoryIcon(expense.category),
                                        contentDescription = null,
                                        tint = categoryColor,
                                        size = 18.dp
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(expense.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    val storeName = expense.storeId?.let { id -> stores.find { it.id == id }?.name }
                                    Text(
                                        text = storeName ?: expense.category.displayName(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = expense.amount.formatCurrency(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            item(key = "filters") {
                // Quick period chips: tapping the active one clears it back to "All time".
                // Everything else lives behind the filter button in the search bar.
                // Chips draw 32dp but reserve a 48dp touch target, which left a blank band above
                // and below the row, so the row drops that padding and hugs the chips
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(
                        listOf(DateRangeFilter.THIS_MONTH, DateRangeFilter.LAST_30_DAYS, DateRangeFilter.YEAR_TO_DATE)
                    ) { filter ->
                        val isSelected = dateFilter == filter
                        SparelyChip(
                            selected = isSelected,
                            onClick = {
                                dateFilter = if (isSelected) DateRangeFilter.ALL_TIME else filter
                                customStartDate = null
                                customEndDate = null
                            },
                            label = { Text(filter.displayName()) }
                        )
                    }
                    // Periods only reachable from the sheet still show here while active
                    if (dateFilter !in listOf(DateRangeFilter.ALL_TIME, DateRangeFilter.THIS_MONTH, DateRangeFilter.LAST_30_DAYS, DateRangeFilter.YEAR_TO_DATE)) {
                        item {
                            SparelyChip(
                                selected = true,
                                onClick = { showFilterSheet = true },
                                label = { Text(dateFilter.displayName()) }
                            )
                        }
                    }
                    categoryFilter?.let { category ->
                        item {
                            SparelyChip(
                                selected = true,
                                onClick = { categoryFilter = null },
                                label = { Text(category.displayName()) },
                                leadingIcon = {
                                    MaterialSymbolIcon(
                                        icon = getCategoryIcon(category),
                                        contentDescription = null,
                                        size = 16.dp
                                    )
                                }
                            )
                        }
                    }
                    if (isFiltering) {
                        item {
                            SparelyChip(
                                selected = false,
                                onClick = ::clearFilters,
                                label = { Text(stringResource(R.string.history_clear_filters)) }
                            )
                        }
                    }
                }
                }
            }

            // Summary Insight Card
            if (showSummary) {
                item(key = "summary") {
                    ModernSummaryCard(
                        filteredExpenses = filteredExpenses,
                        dateFilter = dateFilter,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }

            // Store Analytics Card - only when browsing everything, to avoid repeating the summary
            if (showStoreStats) {
                item(key = "store_stats") {
                    StoreAnalyticsCard(
                        storeStats = storeStats,
                        totalSpent = filteredExpenses.sumOf { it.amount },
                        onStoreClick = onStoreSelected,
                        brandfetchClientId = brandfetchClientId,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }

            groupedExpenses.forEach { (date, dailyExpenses) ->
                stickyHeader(key = "header_$date") {
                    HistoryDateHeader(date = date, dailyTotal = dailyExpenses.sumOf { it.amount })
                }
                // Each day reads as one segmented list: rounded ends, tight gaps between rows
                itemsIndexed(dailyExpenses, key = { _, expense -> expense.id }) { index, expense ->
                    val isFirst = index == 0
                    val isLast = index == dailyExpenses.lastIndex
                    ModernExpenseCard(
                        modifier = Modifier.padding(top = if (isFirst) 0.dp else 2.dp),
                        shape = RoundedCornerShape(
                            topStart = if (isFirst) 24.dp else 6.dp,
                            topEnd = if (isFirst) 24.dp else 6.dp,
                            bottomStart = if (isLast) 24.dp else 6.dp,
                            bottomEnd = if (isLast) 24.dp else 6.dp
                        ),
                        expense = expense,
                        store = stores.find { it.id == expense.storeId },
                        brandfetchClientId = brandfetchClientId,
                        isExpanded = expandedExpenseId == expense.id,
                        isHighlighted = highlightedExpenseId == expense.id,
                        onClick = {
                            if (isSelectionMode) {
                                selectedExpenseIds = if (selectedExpenseIds.contains(expense.id)) {
                                    val newSet = selectedExpenseIds - expense.id
                                    if (newSet.isEmpty()) isSelectionMode = false
                                    newSet
                                } else {
                                    selectedExpenseIds + expense.id
                                }
                            } else {
                                // Toggle expand/collapse
                                expandedExpenseId = if (expandedExpenseId == expense.id) null else expense.id
                            }
                        },
                        isSelected = selectedExpenseIds.contains(expense.id),
                        isSelectionMode = isSelectionMode,
                        onLongClick = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedExpenseIds = setOf(expense.id)
                            }
                        },
                        onToggleSelection = {
                             selectedExpenseIds = if (selectedExpenseIds.contains(expense.id)) {
                                val newSet = selectedExpenseIds - expense.id
                                if (newSet.isEmpty()) isSelectionMode = false
                                newSet
                            } else {
                                selectedExpenseIds + expense.id
                            }
                        },
                        // Edits open the full expense editor (same form as adding)
                        onEdit = { onEditExpense(expense) },
                        onDelete = { expenseToDelete = expense },
                        onRefund = { expenseToRefund = expense },
                        onDuplicate = { onDuplicateExpense(expense) }
                    )
                }
            }

            // "Show more" for pagination when browsing all expenses
            if (!isFiltering && canLoadMore) {
                item(key = "load_more") {
                    SparelyTonalButton(
                        onClick = onLoadMore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Text(stringResource(R.string.history_show_more))
                    }
                }
            }

            if (listToDisplay.isEmpty()) {
                item(key = "empty") {
                    EmptyHistoryNotice(
                        isFiltering = isFiltering,
                        onClearFilters = ::clearFilters,
                        onAddExpense = onAddExpense
                    )
                }
            }
        }

        // Bulk Action Bar - shown when in selection mode
        if (isSelectionMode) {
            BulkActionBar(
                selectedCount = selectedExpenseIds.size,
                onDelete = { showBulkDeleteConfirmation = true },
                onClear = {
                    selectedExpenseIds = emptySet()
                    isSelectionMode = false
                },
                onSelectAll = {
                    selectedExpenseIds = listToDisplay.map { it.id }.toSet() // Changed from filteredExpenses to listToDisplay
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        } else {
            // FAB for adding expense - only show when not in selection mode
            ExtendedFloatingActionButton(
                onClick = onAddExpense,
                expanded = fabExpanded,
                icon = {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.ADD,
                        // The label carries the meaning while it's showing
                        contentDescription = if (fabExpanded) null else stringResource(R.string.expense_entry_title),
                        size = 24.dp
                    )
                },
                text = { Text(stringResource(R.string.dashboard_log_purchase)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
    
    if (showFilterSheet) {
        HistoryFilterBottomSheet(
            dateFilter = dateFilter,
            onDateSelected = { 
                dateFilter = it
                if (it != DateRangeFilter.CUSTOM) {
                    customStartDate = null
                    customEndDate = null
                }
            },
            categoryFilter = categoryFilter,
            onCategorySelected = { categoryFilter = it },
            customStartDate = customStartDate,
            customEndDate = customEndDate,
            onCustomStartDateChange = { customStartDate = it },
            onCustomEndDateChange = { customEndDate = it },
            onDismiss = { showFilterSheet = false }
        )
    }

    if (selectedStore != null) {
        StoreHistoryDialog(
            store = selectedStore,
            history = selectedStoreHistory,
            isLoading = isStoreHistoryLoading,
            onDismiss = onClearSelectedStore,
            stores = stores,
            onEditExpense = {
                onClearSelectedStore()
                onEditExpense(it)
            },
            onDeleteExpense = { expenseToDelete = it },
            onRefundExpense = { expenseToRefund = it },
            brandfetchClientId = brandfetchClientId
        )
    }

    // Confirmation dialog
    expenseToDelete?.let { expense ->
        DeleteExpenseConfirmationDialog(
            expense = expense,
            onConfirm = {
                onDeleteExpense(expense) // Changed to pass Expense object
                expenseToDelete = null
            },
            onDismiss = { expenseToDelete = null }
        )
    }
    
    // Refund Dialog
    expenseToRefund?.let { expense ->
        RefundExpenseDialog(
            expense = expense,
            onConfirm = { amount, itemIds ->
                onRefundExpense(expense.id, amount, itemIds)
                expenseToRefund = null
            },
            onDismiss = { expenseToRefund = null }
        )
    }
    
    // Bulk Delete Confirmation Dialog
    if (showBulkDeleteConfirmation) {
        BulkDeleteConfirmationDialog(
            count = selectedExpenseIds.size,
            onConfirm = {
                val expensesToDelete = listToDisplay.filter { selectedExpenseIds.contains(it.id) } // Filter from listToDisplay
                onDeleteExpenses(expensesToDelete) // Call new bulk delete function
                selectedExpenseIds = emptySet()
                isSelectionMode = false
                showBulkDeleteConfirmation = false
            },
            onDismiss = { showBulkDeleteConfirmation = false }
        )
    }


}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun HistoryFilterBottomSheet(
    dateFilter: DateRangeFilter,
    onDateSelected: (DateRangeFilter) -> Unit,
    categoryFilter: ExpenseCategory?,
    onCategorySelected: (ExpenseCategory?) -> Unit,
    customStartDate: LocalDate? = null,
    customEndDate: LocalDate? = null,
    onCustomStartDateChange: (LocalDate?) -> Unit = {},
    onCustomEndDateChange: (LocalDate?) -> Unit = {},
    onDismiss: () -> Unit
) {
    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "Done" applies and closes, so it stands in for the close button
                SparelySheetHeader(
                    title = stringResource(R.string.history_filters),
                    icon = MaterialSymbols.LIST,
                    modifier = Modifier.weight(1f)
                )
                SparelyTextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_done))
                }
            }

            // Date Range Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.history_filter_period),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateRangeFilter.entries.forEach { filter ->
                        val isSelected = dateFilter == filter
                        SparelyChip(
                            selected = isSelected,
                            onClick = { 
                                onDateSelected(if (isSelected) DateRangeFilter.ALL_TIME else filter)
                            },
                            label = { Text(filter.displayName()) }
                        )
                    }
                }

                if (dateFilter == DateRangeFilter.CUSTOM) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
                        var showStartPicker by remember { mutableStateOf(false) }
                        var showEndPicker by remember { mutableStateOf(false) }

                        SparelyOutlinedButton(
                            onClick = { showStartPicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(customStartDate?.format(dateFormatter) ?: stringResource(R.string.history_custom_start_date))
                        }
                        SparelyOutlinedButton(
                            onClick = { showEndPicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(customEndDate?.format(dateFormatter) ?: stringResource(R.string.history_custom_end_date))
                        }

                        if (showStartPicker) {
                            val state = rememberDatePickerState(initialSelectedDateMillis = (customStartDate ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                            DatePickerDialog(
                                onDismissRequest = { showStartPicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        state.selectedDateMillis?.let { 
                                            onCustomStartDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) 
                                        }
                                        showStartPicker = false
                                    }) { Text(stringResource(R.string.common_done)) }
                                }
                            ) { DatePicker(state = state) }
                        }
                        if (showEndPicker) {
                            val state = rememberDatePickerState(initialSelectedDateMillis = (customEndDate ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                            DatePickerDialog(
                                onDismissRequest = { showEndPicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        state.selectedDateMillis?.let { 
                                            onCustomEndDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) 
                                        }
                                        showEndPicker = false
                                    }) { Text(stringResource(R.string.common_done)) }
                                }
                            ) { DatePicker(state = state) }
                        }
                    }
                }
            }

            // Categories Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.category_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SparelyChip(
                        selected = categoryFilter == null,
                        onClick = { onCategorySelected(null) },
                        label = { Text(stringResource(R.string.history_all_categories)) }
                    )
                    ExpenseCategory.entries.forEach { category ->
                        SparelyChip(
                            selected = categoryFilter == category,
                            onClick = { onCategorySelected(category) },
                            label = { Text(category.displayName()) },
                            leadingIcon = {
                                MaterialSymbolIcon(
                                    icon = getCategoryIcon(category),
                                    contentDescription = null,
                                    size = 16.dp
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Rounded search field with the filter button (and active-filter badge) built in. */
@Composable
private fun HistorySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    activeFilterCount: Int,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = CircleShape,
        color = colors.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MaterialSymbolIcon(
                icon = MaterialSymbols.SEARCH,
                contentDescription = null,
                size = 22.dp,
                tint = colors.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            val hint = stringResource(R.string.history_search_hint)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = hint },
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(hint, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.CLOSE,
                        contentDescription = stringResource(R.string.history_search_clear),
                        size = 20.dp,
                        tint = colors.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onOpenFilters) {
                BadgedBox(
                    badge = {
                        if (activeFilterCount > 0) {
                            Badge { Text(activeFilterCount.toString()) }
                        }
                    }
                ) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.LIST,
                        contentDescription = if (activeFilterCount > 0) {
                            stringResource(R.string.history_filters_count, activeFilterCount)
                        } else {
                            stringResource(R.string.history_filters)
                        },
                        size = 22.dp,
                        tint = if (activeFilterCount > 0) colors.primary else colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernSummaryCard(
    filteredExpenses: List<Expense>,
    dateFilter: DateRangeFilter,
    modifier: Modifier = Modifier
) {
    val totalFilteredSpent = filteredExpenses.sumOf { it.amount }
    val totalFilteredReserve = filteredExpenses.sumOf { it.allocation.totalSetAside }
    val savingsRate = if (totalFilteredSpent > 0) totalFilteredReserve / totalFilteredSpent else 0.0

    val animatedRate by animateFloatAsState(
        targetValue = savingsRate.toFloat(),
        animationSpec = tween(
            durationMillis = ExpressiveMotionTokens.EmphasizedDurationMillis,
            easing = ExpressiveMotionTokens.EmphasizedEasing
        ),
        label = "savingsRate"
    )
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Headline number first, supporting stats and chart after
            Column {
                Text(
                    text = stringResource(R.string.history_total_spent_period, dateFilter.displayName()),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onPrimaryContainer.copy(alpha = 0.8f)
                )
                SingleLineText(
                    text = totalFilteredSpent.formatCurrency(),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.onPrimaryContainer
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.history_transactions_count,
                        filteredExpenses.size,
                        filteredExpenses.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryStat(
                    label = stringResource(R.string.history_savings),
                    value = totalFilteredReserve.formatCurrency(),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                SummaryStat(
                    label = stringResource(R.string.history_savings_rate),
                    value = savingsRate.formatPercent(),
                    progress = animatedRate,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            SpendingGraph(
                expenses = filteredExpenses,
                dateFilter = dateFilter,
                barColor = colors.primary,
                labelColor = colors.onPrimaryContainer.copy(alpha = 0.7f),
                emptyBarColor = colors.onPrimaryContainer.copy(alpha = 0.12f)
            )
        }
    }
}

/** Small stat tile on the summary card, with an optional progress bar under the value. */
@Composable
private fun SummaryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    progress: Float? = null
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = ExpressiveShapes.medium,
        color = colors.surface.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            // Plain Text, not SingleLineText: the tiles are sized with intrinsics, which
            // SingleLineText's BoxWithConstraints (a SubcomposeLayout) can't answer
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            progress?.let { fraction ->
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(6.dp)
                        .clip(CircleShape),
                    color = colors.primary,
                    trackColor = colors.primary.copy(alpha = 0.18f),
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun SpendingGraph(
    expenses: List<Expense>,
    dateFilter: DateRangeFilter,
    barColor: Color = MaterialTheme.colorScheme.primary,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    emptyBarColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    // Determine aggregation (Daily for < 90 days, Monthly otherwise)
    val isMonthly = dateFilter == DateRangeFilter.YEAR_TO_DATE || dateFilter == DateRangeFilter.ALL_TIME || dateFilter == DateRangeFilter.LAST_90_DAYS

    val dataPoints = remember(expenses, isMonthly) {
        if (isMonthly) {
            expenses.groupBy { java.time.YearMonth.from(it.date) }
                .mapValues { it.value.sumOf { e -> e.amount } }
                .entries.sortedBy { it.key }
                .takeLast(6) // Last 6 months
                .map { it.key.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) to it.value }
        } else {
            // Daily - take last 7 days with data or just fill dates
            val last7Days = (0..6).map { LocalDate.now().minusDays(it.toLong()) }.reversed()
            val expenseMap = expenses.groupBy { it.date }

            last7Days.map { date ->
                val amount = expenseMap[date]?.sumOf { it.amount } ?: 0.0
                date.format(DateTimeFormatter.ofPattern("EEE")) to amount
            }
        }
    }

    val maxAmount = dataPoints.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        dataPoints.forEachIndexed { index, (label, amount) ->
            // The latest day or month stands out; earlier ones sit back
            val isCurrent = index == dataPoints.lastIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Empty slots keep a short stub so the period still reads as a full row
                    val fraction = if (amount > 0) (amount / maxAmount).toFloat().coerceIn(0.06f, 1f) else 0.04f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    amount <= 0 -> emptyBarColor
                                    isCurrent -> barColor
                                    else -> barColor.copy(alpha = 0.45f)
                                }
                            )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    fontSize = 10.sp
                )
            }
        }
    }
}




/**
 * One expense in the history list: icon, title and where it was bought on the left, amount on the
 * right. Tapping opens details and actions underneath; [shape] lets a day's rows join into one group.
 */
@Composable
private fun ModernExpenseCard(
    expense: Expense,
    store: Store?,
    brandfetchClientId: String?,
    modifier: Modifier = Modifier,
    shape: Shape = ExpressiveShapes.large,
    showStoreLogo: Boolean = true,
    isExpanded: Boolean = false,
    isHighlighted: Boolean = false,
    onClick: () -> Unit = {},
    onDelete: () -> Unit,
    onEdit: () -> Unit = {},
    onRefund: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    onToggleSelection: () -> Unit = {}
) {
    val savingsRate = if (expense.amount > 0) expense.allocation.totalSetAside / expense.amount else 0.0
    val categoryColor = getCategoryColor(expense.category)
    val colorScheme = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> colorScheme.primaryContainer
            isHighlighted -> colorScheme.secondaryContainer
            else -> colorScheme.surfaceContainer
        },
        label = "expenseContainer"
    )
    val hasStoreLogo = showStoreLogo && store?.getBrandfetchLogoUrl(brandfetchClientId) != null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (isSelectionMode) {
                    Modifier.clickable { onToggleSelection() }
                } else {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                }
            ),
        shape = shape,
        color = containerColor
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar: a check while selected, else the store's logo, else the category icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isSelected -> colorScheme.primary
                                hasStoreLogo -> colorScheme.surface
                                else -> categoryColor.copy(alpha = 0.14f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isSelected -> MaterialSymbolIcon(
                            icon = MaterialSymbols.CHECK,
                            contentDescription = stringResource(R.string.history_card_selected),
                            tint = colorScheme.onPrimary,
                            size = 24.dp
                        )
                        hasStoreLogo -> com.example.sparely.ui.components.StoreIcon(
                            store = store!!,
                            brandfetchClientId = brandfetchClientId,
                            size = 44
                        )
                        else -> MaterialSymbolIcon(
                            icon = getCategoryIcon(expense.category),
                            contentDescription = null,
                            tint = categoryColor,
                            size = 22.dp
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = expense.description.ifBlank { stringResource(R.string.history_unnamed_expense) },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (expense.isRecurring) {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.AUTORENEW,
                                contentDescription = stringResource(R.string.history_card_recurring),
                                size = 16.dp,
                                tint = colorScheme.tertiary
                            )
                        }
                    }
                    Text(
                        text = listOfNotNull(expense.category.displayName(), store?.name).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = expense.amount.formatCurrency(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        // A fully refunded purchase no longer counts, so it's struck through
                        color = if (expense.isRefunded) colorScheme.onSurfaceVariant else colorScheme.onSurface,
                        textDecoration = if (expense.isRefunded) TextDecoration.LineThrough else null
                    )
                    if (expense.refundedAmount > 0) {
                        Text(
                            text = stringResource(
                                if (expense.isRefunded) R.string.history_card_refunded_badge
                                else R.string.history_card_partial_refund_badge
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.success
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpenseDetailRow(
                        icon = MaterialSymbols.TRENDING_UP,
                        iconTint = colorScheme.primary,
                        label = stringResource(R.string.history_savings_rate),
                        value = stringResource(R.string.history_card_saved_percent, savingsRate.formatPercent(0)),
                        valueColor = colorScheme.primary
                    )

                    if (expense.refundedAmount > 0) {
                        ExpenseDetailRow(
                            icon = MaterialSymbols.UNDO,
                            iconTint = colorScheme.tertiary,
                            label = stringResource(
                                if (expense.isRefunded) R.string.history_card_refunded_badge
                                else R.string.history_card_partial_refund_badge
                            ),
                            value = formatCurrency(expense.refundedAmount),
                            valueColor = colorScheme.success
                        )
                    }

                    expense.notes?.takeIf { it.isNotBlank() }?.let { noteText ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveShapes.small,
                            color = colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = noteText,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (expense.items.isNotEmpty()) {
                        var itemsExpanded by remember { mutableStateOf(false) }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ExpressiveShapes.small)
                                .clickable { itemsExpanded = !itemsExpanded },
                            shape = ExpressiveShapes.small,
                            color = colorScheme.surfaceContainerHigh
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = pluralStringResource(R.plurals.history_card_items_count, expense.items.size, expense.items.size),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    val chevronRotation by animateFloatAsState(if (itemsExpanded) 180f else 0f, label = "itemsChevron")
                                    MaterialSymbolIcon(
                                        icon = MaterialSymbols.ARROW_DROP_DOWN,
                                        contentDescription = stringResource(if (itemsExpanded) R.string.dashboard_collapse else R.string.dashboard_expand),
                                        size = 20.dp,
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.rotate(chevronRotation)
                                    )
                                }
                                if (itemsExpanded) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    expense.items.forEach { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${item.quantity}× ${item.name}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = item.totalPrice.formatCurrency(),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Labeled actions: clearer than icon-only buttons (Duplicate/Refund icons were ambiguous)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExpenseCardAction(
                            icon = MaterialSymbols.EDIT,
                            label = stringResource(R.string.edit),
                            containerColor = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer,
                            onClick = onEdit,
                            modifier = Modifier.weight(1f)
                        )
                        ExpenseCardAction(
                            icon = MaterialSymbols.CONTENT_COPY,
                            label = stringResource(R.string.history_action_duplicate),
                            containerColor = colorScheme.surfaceContainerHigh,
                            contentColor = colorScheme.onSurfaceVariant,
                            onClick = onDuplicate,
                            modifier = Modifier.weight(1f)
                        )
                        if (!expense.isRefunded) {
                            ExpenseCardAction(
                                icon = MaterialSymbols.UNDO,
                                label = stringResource(R.string.history_action_refund),
                                containerColor = colorScheme.surfaceContainerHigh,
                                contentColor = colorScheme.onSurfaceVariant,
                                onClick = onRefund,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        ExpenseCardAction(
                            icon = MaterialSymbols.DELETE,
                            label = stringResource(R.string.delete),
                            containerColor = colorScheme.errorContainer,
                            contentColor = colorScheme.onErrorContainer,
                            onClick = onDelete,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** Label on the left, value on the right, in a soft panel inside an expanded expense. */
@Composable
private fun ExpenseDetailRow(
    @androidx.annotation.DrawableRes icon: Int,
    iconTint: Color,
    label: String,
    value: String,
    valueColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MaterialSymbolIcon(icon = icon, contentDescription = null, size = 16.dp, tint = iconTint)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}


@Composable
private fun ExpenseCardAction(
    @androidx.annotation.DrawableRes icon: Int,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = ExpressiveShapes.small,
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
        ) {
            MaterialSymbolIcon(icon = icon, contentDescription = null, size = 20.dp)
            SingleLineText(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun AllocationChip(
    label: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = ExpressiveShapes.small,
        color = color.copy(alpha = 0.15f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatCurrency(amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun EmptyHistoryNotice(
    isFiltering: Boolean,
    onClearFilters: () -> Unit,
    onAddExpense: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            MaterialSymbolIcon(
                icon = if (isFiltering) MaterialSymbols.SEARCH else MaterialSymbols.RECEIPT,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                size = 32.dp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(if (isFiltering) R.string.history_empty_filtered_title else R.string.history_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = stringResource(if (isFiltering) R.string.history_empty_filtered_desc else R.string.history_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (isFiltering) {
            SparelyTonalButton(onClick = onClearFilters) {
                Text(stringResource(R.string.history_clear_filters))
            }
        } else {
            SparelyButton(
                onClick = onAddExpense,
                icon = { MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null, size = 20.dp) }
            ) {
                Text(stringResource(R.string.dashboard_log_purchase))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteExpenseConfirmationDialog(
    expense: Expense,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    
    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
             // Warning Icon
             Surface(
                 shape = CircleShape,
                 color = MaterialTheme.colorScheme.errorContainer,
                 modifier = Modifier.size(72.dp)
             ) {
                 Box(contentAlignment = Alignment.Center) {
                     MaterialSymbolIcon(
                         icon = MaterialSymbols.DELETE,
                         contentDescription = null,
                         modifier = Modifier.size(32.dp),
                         tint = MaterialTheme.colorScheme.error
                     )
                 }
             }

            Text(
                text = stringResource(R.string.history_delete_confirmation_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
             Text(
                text = stringResource(R.string.history_delete_confirmation_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
                 textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Expense Preview
            Surface(
                shape = ExpressiveShapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                         Text(
                            text = expense.description,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${formatCurrency(expense.amount)} • ${expense.date.format(formatter)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SparelyButton(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Text(stringResource(R.string.action_delete))
                }
                
                 SparelyTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun StoreAnalyticsCard(
    storeStats: List<Triple<Store, Double, Int>>,
    totalSpent: Double,
    onStoreClick: (Store) -> Unit,
    brandfetchClientId: String?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        color = colors.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.history_top_stores),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            storeStats.forEach { (store, amount, count) ->
                val share = if (totalSpent > 0) amount / totalSpent else 0.0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStoreClick(store) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.example.sparely.ui.components.StoreIcon(
                        store = store,
                        brandfetchClientId = brandfetchClientId,
                        size = 40
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = store.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = amount.formatCurrency(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        // Share of total spending
                        LinearProgressIndicator(
                            progress = { share.toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = colors.primary,
                            trackColor = colors.primary.copy(alpha = 0.14f),
                            strokeCap = StrokeCap.Round
                        )
                        Text(
                            text = "${pluralStringResource(R.plurals.history_store_purchases, count, count)} · ${share.formatPercent(0)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun matchesDate(
    date: LocalDate,
    filter: DateRangeFilter,
    customStart: LocalDate? = null,
    customEnd: LocalDate? = null
): Boolean {
    val today = LocalDate.now()
    return when (filter) {
        DateRangeFilter.LAST_7_DAYS -> !date.isBefore(today.minusDays(6))
        DateRangeFilter.LAST_30_DAYS -> !date.isBefore(today.minusDays(29))
        DateRangeFilter.THIS_MONTH -> date.month == today.month && date.year == today.year
        DateRangeFilter.LAST_90_DAYS -> !date.isBefore(today.minusDays(89))
        DateRangeFilter.YEAR_TO_DATE -> date.year == today.year
        DateRangeFilter.ALL_TIME -> true
        DateRangeFilter.CUSTOM -> {
            val start = customStart ?: LocalDate.MIN
            val end = customEnd ?: LocalDate.MAX
            !date.isBefore(start) && !date.isAfter(end)
        }
    }
}

@Composable
fun DateRangeFilter.displayName(): String = when(this) {
    DateRangeFilter.LAST_7_DAYS -> stringResource(R.string.history_filter_7_days)
    DateRangeFilter.LAST_30_DAYS -> stringResource(R.string.history_filter_30_days)
    DateRangeFilter.THIS_MONTH -> stringResource(R.string.history_filter_this_month)
    DateRangeFilter.LAST_90_DAYS -> stringResource(R.string.history_filter_90_days)
    DateRangeFilter.YEAR_TO_DATE -> stringResource(R.string.history_filter_this_year)
    DateRangeFilter.ALL_TIME -> stringResource(R.string.history_filter_all_time)
    DateRangeFilter.CUSTOM -> stringResource(R.string.history_filter_custom)
}

private fun formatCurrency(value: Double): String {
    return value.formatCurrency()
}

// Category colors and icons are now sourced from com.example.sparely.ui.theme.CategoryUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefundExpenseDialog(
    expense: Expense,
    onConfirm: (Double, List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    val maxRefundable = expense.amount - expense.refundedAmount
    var amountText by remember { mutableStateOf(maxRefundable.toInputString()) }
    var selectedItemIds by remember { mutableStateOf(emptySet<Long>()) }
    var wasManuallyEdited by remember { mutableStateOf(false) }

    // When items are selected, update the amount
    LaunchedEffect(selectedItemIds) {
        if (selectedItemIds.isNotEmpty()) {
            val itemsTotal = expense.items
                .filter { selectedItemIds.contains(it.id) }
                .sumOf { it.totalPrice }
            amountText = "%.2f".format(itemsTotal)
            wasManuallyEdited = false
        }
    }

    val refundAmount = amountText.toSafeDouble() ?: 0.0
    val tooMuch = refundAmount > maxRefundable + 0.01
    val canRefund = refundAmount > 0 && !tooMuch
    val refund = {
        if (canRefund) onConfirm(refundAmount, selectedItemIds.toList())
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
                    title = stringResource(R.string.refund_title),
                    subtitle = stringResource(R.string.refund_subtitle, expense.description, maxRefundable.formatCurrency()),
                    icon = MaterialSymbols.UNDO
                )

                if (expense.items.isNotEmpty()) {
                    // Items as one segmented list; tapping a row ticks it
                    Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
                        SheetSectionLabel(
                            text = stringResource(R.string.refund_items_title),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        expense.items.forEachIndexed { index, item ->
                            val checked = selectedItemIds.contains(item.id)
                            Surface(
                                shape = segmentedShape(index, expense.items.size, outer = 20.dp),
                                color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .toggleable(
                                            value = checked,
                                            role = androidx.compose.ui.semantics.Role.Checkbox,
                                            onValueChange = { isChecked ->
                                                selectedItemIds = if (isChecked) selectedItemIds + item.id else selectedItemIds - item.id
                                            }
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = null)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name.ifBlank { "Item ${item.id}" },
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${item.quantity} × ${item.unitPrice.formatCurrency()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = item.totalPrice.formatCurrency(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                SheetAmountField(
                    value = amountText,
                    onValueChange = {
                        amountText = it.filterCurrencyInput()
                        wasManuallyEdited = true
                    },
                    label = stringResource(R.string.refund_amount),
                    isError = tooMuch,
                    supportingText = when {
                        tooMuch -> stringResource(R.string.refund_too_much, maxRefundable.formatCurrency())
                        selectedItemIds.isNotEmpty() && !wasManuallyEdited -> stringResource(R.string.refund_from_items)
                        else -> null
                    },
                    // Prefilled with the full refundable amount
                    autoFocus = false,
                    onImeAction = refund
                )
            }

            SheetActions(
                confirmLabel = stringResource(R.string.refund_confirm),
                onConfirm = refund,
                confirmEnabled = canRefund,
                dismissLabel = stringResource(R.string.action_cancel),
                onDismiss = onDismiss
            )
        }
    }
}


@Composable
fun BulkActionBar(
    selectedCount: Int,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selected count
            Text(
                text = stringResource(R.string.history_bulk_selected, selectedCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Select All
                IconButton(onClick = onSelectAll) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.CHECK_CIRCLE,
                        contentDescription = stringResource(R.string.history_bulk_select_all),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        size = 24.dp
                    )
                }
                
                // Delete
                IconButton(onClick = onDelete) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.DELETE,
                        contentDescription = stringResource(R.string.history_bulk_delete),
                        tint = MaterialTheme.colorScheme.error,
                        size = 24.dp
                    )
                }
                
                // Clear selection
                IconButton(onClick = onClear) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.CLOSE,
                        contentDescription = stringResource(R.string.history_bulk_clear),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        size = 24.dp
                    )
                }
            }
        }
    }
}

@Composable
fun BulkDeleteConfirmationDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    SparelyAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.history_bulk_delete_confirm_title, count))
        },
        text = {
            Text(stringResource(R.string.history_bulk_delete_confirm_message))
        },
        confirmButton = {
            SparelyButton(
                onClick = onConfirm
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            SparelyTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}


