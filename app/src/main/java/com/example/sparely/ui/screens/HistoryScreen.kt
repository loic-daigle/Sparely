package com.example.sparely.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }
    var editExpenseAssetAllocations by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
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

    // Load asset allocations when opening edit
    LaunchedEffect(expenseToEdit) {
        if (expenseToEdit != null) {
            editExpenseAssetAllocations = onLoadAssetAllocationsForExpense(expenseToEdit!!.id)
        } else {
            editExpenseAssetAllocations = emptyMap()
        }
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
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "search") {
                SparelyTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.history_search_hint)) },
                    leadingIcon = {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.SEARCH,
                            contentDescription = null,
                            size = 20.dp
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                MaterialSymbolIcon(
                                    icon = MaterialSymbols.CLOSE,
                                    contentDescription = stringResource(R.string.history_search_clear),
                                    size = 20.dp
                                )
                            }
                        }
                    },
                    singleLine = true
                )
            }
            if (showQuickResults) {
                item(key = "quick_results") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, ExpressiveShapes.medium)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.history_search_results_quick),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val quickResultShape = ExpressiveShapes.small
                        filteredExpenses.take(4).forEach { expense ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(quickResultShape),
                                shape = quickResultShape,
                                color = MaterialTheme.colorScheme.surface,
                                onClick = {
                                    expandedExpenseId = expense.id
                                    highlightedExpenseId = expense.id
                                    findExpenseIndex(expense.id)?.let { target ->
                                        scope.launch { listState.animateScrollToItem(target) }
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(expense.description, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
            }
            item(key = "filters") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        SparelyChip(
                            selected = activeFilterCount > 0,
                            onClick = { showFilterSheet = true },
                            label = {
                                Text(
                                    if (activeFilterCount > 0) {
                                        stringResource(R.string.history_filters_count, activeFilterCount)
                                    } else {
                                        stringResource(R.string.history_filters)
                                    }
                                )
                            },
                            leadingIcon = {
                                MaterialSymbolIcon(
                                    icon = MaterialSymbols.LIST,
                                    contentDescription = null,
                                    size = 18.dp
                                )
                            }
                        )
                    }
                    // Quick period chips: tapping the active one clears it back to "All time"
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
                            SparelyTextButton(onClick = ::clearFilters) {
                                Text(stringResource(R.string.history_clear_filters))
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
                        dateFilter = dateFilter
                    )
                }
            }

            // Store Analytics Card - only when browsing everything, to avoid repeating the summary
            if (showStoreStats) {
                item(key = "store_stats") {
                    StoreAnalyticsCard(
                        storeStats = storeStats,
                        totalSpent = filteredExpenses.sumOf { it.amount },
                        onStoreClick = onStoreSelected
                    )
                }
            }

            groupedExpenses.forEach { (date, dailyExpenses) ->
                stickyHeader(key = "header_$date") {
                    HistoryDateHeader(date = date, dailyTotal = dailyExpenses.sumOf { it.amount })
                }
                items(dailyExpenses, key = { it.id }) { expense ->
                    ModernExpenseCard(
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
                        onEdit = { expenseToEdit = expense },
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
                            .padding(vertical = 8.dp)
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
            androidx.compose.material3.FloatingActionButton(
                onClick = onAddExpense,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.ADD,
                    contentDescription = stringResource(R.string.expense_entry_title),
                    size = 24.dp
                )
            }
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
            onEditExpense = { expenseToEdit = it },
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
    
    // Edit dialog
    expenseToEdit?.let { expense ->
        EditExpenseDialog(
            expense = expense,
            stores = stores,
            paymentMethods = paymentMethods,
            vaults = vaults,
            assets = assets,
            assetAllocations = editExpenseAssetAllocations,
            onConfirm = { editedExpense, assetAllocations ->
                onEditExpenseWithAssets(editedExpense, assetAllocations)
                expenseToEdit = null
                editExpenseAssetAllocations = emptyMap()
            },
            onDismiss = {
                expenseToEdit = null
                editExpenseAssetAllocations = emptyMap()
            },
            onCreateStore = onCreateStore,
            onEditStore = onEditStore,
            onDeleteStore = onDeleteStore,
            brandfetchClientId = brandfetchClientId,
            brandSearchResults = brandSearchResults,
            onBrandSearch = onBrandSearch
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
                Text(
                    text = stringResource(R.string.history_filters),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
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

@Composable
private fun ModernSummaryCard(
    filteredExpenses: List<Expense>,
    dateFilter: DateRangeFilter
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
    
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = ExpressiveShapes.large
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Headline numbers first, chart second
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.history_total_spent_period, dateFilter.displayName()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleLineText(
                        text = totalFilteredSpent.formatCurrency(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.history_transactions_count,
                            filteredExpenses.size,
                            filteredExpenses.size
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.history_savings),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = totalFilteredReserve.formatCurrency(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Savings rate with progress
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.history_savings_rate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.TRENDING_UP,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = savingsRate.formatPercent(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { animatedRate.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    strokeCap = StrokeCap.Round,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SpendingGraph(expenses = filteredExpenses, dateFilter = dateFilter)
        }
    }
}

@Composable
fun SpendingGraph(expenses: List<Expense>, dateFilter: DateRangeFilter) {
    // Determine aggregation (Daily for < 90 days, Monthly otherwise)
    val isMonthly = dateFilter == DateRangeFilter.YEAR_TO_DATE || dateFilter == DateRangeFilter.ALL_TIME || dateFilter == DateRangeFilter.LAST_90_DAYS
    
    val dataPoints = remember(expenses, isMonthly) {
        if (isMonthly) {
            expenses.groupBy { java.time.YearMonth.from(it.date) }
                .mapValues { it.value.sumOf { e -> e.amount } }
                .entries.sortedBy { it.key }
                .takeLast(6) // Last 6 months
                .map { it.key.month.name.take(3) to it.value }
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
    
    Column(
        modifier = Modifier.fillMaxWidth().height(150.dp), // Increased height slightly
        verticalArrangement = Arrangement.Bottom
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            dataPoints.forEach { (label, amount) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom 
                ) {
                    // Bar Container (Takes flexible space)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val heightFraction = (amount / maxAmount).toFloat().coerceIn(0.02f, 1f)
                        // Only show bar if amount > 0 or for visual placeholder? 
                        // If amount is 0, fraction is 0.02f (tiny bar). 
                        // If we want to hide 0 bars, we can check amount > 0. 
                        // Let's keep a tiny blip for 0 to show the slot exists, or just 0 height.
                        // User said "hides the text", so primary fix is layout.
                        
                        val displayedFraction = if (amount > 0) heightFraction else 0.005f
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .fillMaxHeight(displayedFraction)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (amount > 0) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}




@Composable
private fun ModernExpenseCard(
    expense: Expense,
    store: Store?,
    brandfetchClientId: String?,
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
        targetValue = if (isHighlighted) colorScheme.secondaryContainer else colorScheme.surfaceContainerHigh,
        label = "expenseHighlight"
    )

    ExpressiveCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(ExpressiveShapes.large)
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
        shape = ExpressiveShapes.large,
        containerColor = if (isSelected) colorScheme.primaryContainer else containerColor,
        contentPadding = 0.dp
    ) {
        var contentHeightPx by remember { mutableStateOf(0) }
        val watermarkSize = with(LocalDensity.current) {
            (contentHeightPx * 0.55f).toDp().coerceIn(36.dp, 88.dp)
        }
        val hasStoreLogo = showStoreLogo && store?.getBrandfetchLogoUrl(brandfetchClientId) != null

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .clip(ExpressiveShapes.large)
                    .animateContentSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .onSizeChanged { contentHeightPx = it.height },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) colorScheme.primary else if (hasStoreLogo) Color.Transparent else categoryColor.copy(alpha = 0.1f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isSelected) {
                                    MaterialSymbolIcon(
                                        icon = MaterialSymbols.CHECK,
                                        contentDescription = "Selected",
                                        tint = colorScheme.onPrimary,
                                        size = 24.dp
                                    )
                                } else if (hasStoreLogo) {
                                    com.example.sparely.ui.components.StoreIcon(
                                        store = store!!,
                                        brandfetchClientId = brandfetchClientId,
                                        size = 42
                                    )
                                } else {
                                    MaterialSymbolIcon(
                                        icon = getCategoryIcon(expense.category),
                                        contentDescription = null,
                                        tint = categoryColor,
                                        size = 22.dp
                                    )
                                }
                            }
                        }

                        Text(
                            text = expense.description,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Surface(
                        shape = ExpressiveShapes.small,
                        color = colorScheme.surfaceContainerLow
                    ) {
                        Text(
                            text = expense.amount.formatCurrency(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = ExpressiveShapes.extraSmall,
                        color = categoryColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = expense.category.displayName(),
                            style = MaterialTheme.typography.labelSmall,
                            color = categoryColor,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (store != null) {
                        Text(
                            text = store.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    if (expense.isRecurring) {
                        Surface(
                            shape = ExpressiveShapes.extraSmall,
                            color = colorScheme.tertiaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                MaterialSymbolIcon(
                                    icon = MaterialSymbols.AUTORENEW,
                                    size = 12.dp,
                                    tint = colorScheme.onTertiaryContainer,
                                    contentDescription = stringResource(R.string.recurring_paused)
                                )
                                Text(
                                    text = "Recurring",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveShapes.small,
                            color = colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Savings Rate",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.onSurfaceVariant
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    MaterialSymbolIcon(
                                        icon = MaterialSymbols.TRENDING_UP,
                                        contentDescription = null,
                                        size = 16.dp,
                                        tint = colorScheme.primary
                                    )
                                    Text(
                                        text = savingsRate.formatPercent(0) + " saved",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.primary
                                    )
                                }
                            }
                        }

                        expense.notes?.let { noteText ->
                            if (noteText.isNotBlank()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ExpressiveShapes.small,
                                    color = colorScheme.surfaceContainerLow
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
                        }

                        if (expense.refundedAmount > 0) {
                            Surface(
                                color = colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                shape = ExpressiveShapes.extraSmall,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MaterialSymbolIcon(
                                        icon = MaterialSymbols.REFRESH,
                                        contentDescription = "Refunded",
                                        size = 16.dp,
                                        tint = colorScheme.tertiary
                                    )
                                    Text(
                                        text = if (expense.isRefunded) "Refunded ${formatCurrency(expense.refundedAmount)}"
                                        else "Partially Refunded: ${formatCurrency(expense.refundedAmount)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }

                        if (expense.items.isNotEmpty()) {
                            var itemsExpanded by remember { mutableStateOf(false) }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { itemsExpanded = !itemsExpanded },
                                shape = ExpressiveShapes.extraSmall,
                                color = colorScheme.surfaceContainerLow
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${expense.items.size} item${if (expense.items.size > 1) "s" else ""}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                        MaterialSymbolIcon(
                                            icon = if (itemsExpanded) MaterialSymbols.ARROW_DROP_UP else MaterialSymbols.ARROW_DROP_DOWN,
                                            contentDescription = if (itemsExpanded) "Collapse" else "Expand",
                                            size = 20.dp,
                                            tint = colorScheme.onSurfaceVariant
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    shape = ExpressiveShapes.small,
                                    color = colorScheme.primaryContainer,
                                    tonalElevation = 0.dp
                                ) {
                                    IconButton(onClick = onDuplicate) {
                                        MaterialSymbolIcon(
                                            icon = MaterialSymbols.ADD,
                                            contentDescription = "Duplicate",
                                            size = 20.dp,
                                            tint = colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                Surface(
                                    shape = ExpressiveShapes.small,
                                    color = colorScheme.surfaceContainerLow,
                                    tonalElevation = 0.dp
                                ) {
                                    IconButton(onClick = onEdit) {
                                        MaterialSymbolIcon(
                                            icon = MaterialSymbols.EDIT,
                                            contentDescription = stringResource(R.string.edit),
                                            size = 20.dp,
                                            tint = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (!expense.isRefunded) {
                                    Surface(
                                        shape = ExpressiveShapes.small,
                                        color = colorScheme.tertiaryContainer,
                                        tonalElevation = 0.dp
                                    ) {
                                        IconButton(onClick = onRefund) {
                                            MaterialSymbolIcon(
                                                icon = MaterialSymbols.REFRESH,
                                                contentDescription = "Refund",
                                                size = 20.dp,
                                                tint = colorScheme.onTertiaryContainer
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = ExpressiveShapes.small,
                                    color = colorScheme.errorContainer,
                                    tonalElevation = 0.dp
                                ) {
                                    IconButton(onClick = onDelete) {
                                        MaterialSymbolIcon(
                                            icon = MaterialSymbols.DELETE,
                                            contentDescription = stringResource(R.string.delete),
                                            size = 20.dp,
                                            tint = colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            MaterialSymbolIcon(
                icon = getCategoryIcon(expense.category),
                contentDescription = null,
                size = watermarkSize,
                tint = categoryColor.copy(alpha = 0.04f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 12.dp)
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun EditExpenseDialog(
    expense: Expense,
    stores: List<Store>,
    paymentMethods: List<com.example.sparely.domain.model.PaymentMethod> = emptyList(),
    vaults: List<com.example.sparely.domain.model.SmartVault> = emptyList(),
    assets: List<com.example.sparely.domain.model.Asset> = emptyList(),
    assetAllocations: Map<Long, Double> = emptyMap(),
    onConfirm: (Expense, Map<Long, Double>) -> Unit,
    onDismiss: () -> Unit,
    onCreateStore: suspend (StoreInput) -> Store?,
    onEditStore: (Store) -> Unit,
    onDeleteStore: (Store) -> Unit,
    brandfetchClientId: String? = null,
    brandSearchResults: List<com.example.sparely.data.remote.BrandfetchBrand> = emptyList(),
    onBrandSearch: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var description by remember { mutableStateOf(expense.description) }
    var amountText by remember { mutableStateOf(expense.amount.toString()) }
    var category by remember { mutableStateOf(expense.category) }
    var expenseType by remember { mutableStateOf(expense.type) }
    // Initialize with current stores, then update if store appears later (e.g. after loading)
    var selectedStore by remember { mutableStateOf(stores.find { it.id == expense.storeId }) }
    var searchQuery by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(expense.notes ?: "") }
    var orderNumber by remember { mutableStateOf(expense.orderNumber ?: "") }
    var selectedPaymentMethod by remember { mutableStateOf(paymentMethods.find { it.id == expense.paymentMethodId }) }
    var selectedDate by remember { mutableStateOf(expense.date) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Asset selection state
    var selectedAssetAllocations by remember { mutableStateOf(assetAllocations) }
    var assetSelectorExpanded by remember { mutableStateOf(false) }

    // Update selectedAssetAllocations when assetAllocations parameter changes
    LaunchedEffect(assetAllocations) {
        selectedAssetAllocations = assetAllocations
    }

    LaunchedEffect(stores) {
        if (selectedStore == null && expense.storeId != null) {
            stores.find { it.id == expense.storeId }?.let { selectedStore = it }
        }
    }
    LaunchedEffect(paymentMethods) {
        if (selectedPaymentMethod == null && expense.paymentMethodId != null) {
            paymentMethods.find { it.id == expense.paymentMethodId }?.let { selectedPaymentMethod = it }
        }
    }
    var showError by remember { mutableStateOf(false) }
    
    // Line items state
    val expenseItems = remember { 
        androidx.compose.runtime.mutableStateListOf<com.example.sparely.domain.model.ExpenseItem>().apply {
            addAll(expense.items)
        }
    }
    var showAddItemDialog by remember { mutableStateOf(false) }
    
    SparelyBottomSheet(isOpen = true, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                Text(
                    text = "Edit Expense",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                if (showError) {
                    Text(
                        text = "Please fill out all required fields correctly.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                // Description
                SparelyTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Amount
                SparelyTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Amount") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    )
                )
                
                // Category Selector
                Column {
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (cat in ExpenseCategory.entries) {
                            com.example.sparely.ui.components.SparelyChip(
                                onClick = { category = cat },
                                label = { Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                selected = category == cat
                            )
                        }
                    }
                }

                // Type Selector (Product/Service)
                Column {
                    Text("Type", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (type in com.example.sparely.domain.model.ExpenseType.entries) {
                            com.example.sparely.ui.components.SparelyChip(
                                onClick = { expenseType = type },
                                label = { Text(type.displayName()) },
                                selected = expenseType == type
                            )
                        }
                    }
                }

                // Store Selector
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
                
                // Notes
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
                
                // Order Number
                SparelyTextField(
                    value = orderNumber,
                    onValueChange = { orderNumber = it },
                    label = { Text(stringResource(R.string.expense_order_number_label)) },
                    placeholder = { Text(stringResource(R.string.expense_order_number_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Date Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.expense_entry_date_label),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    SparelyTextButton(onClick = { showDatePicker = true }) {
                        Text(stringResource(R.string.expense_entry_change_date_button))
                    }
                }
                
                // Payment Method Selector (if payment methods exist)
                if (paymentMethods.isNotEmpty()) {
                    Column {
                        Text(
                            text = "Payment Method",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // None option
                            com.example.sparely.ui.components.SparelyChip(
                                onClick = { selectedPaymentMethod = null },
                                label = { Text("None") },
                                selected = selectedPaymentMethod == null
                            )
                            paymentMethods.forEach { pm ->
                                com.example.sparely.ui.components.SparelyChip(
                                    onClick = { selectedPaymentMethod = pm },
                                    label = { Text(pm.name) },
                                    selected = selectedPaymentMethod?.id == pm.id
                                )
                            }
                        }
                    }
                }

                // Asset Linking Section
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
                                text = "No assets linked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            selectedAssetAllocations.forEach { (assetId, percentage) ->
                                val asset = assets.find { it.id == assetId }
                                asset?.let { selectedAsset ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                selectedAsset.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
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
                                            Text(
                                                "${String.format("%.0f", percentage * 100)}%",
                                                style = MaterialTheme.typography.labelSmall
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
                                                contentDescription = "Remove asset",
                                                tint = MaterialTheme.colorScheme.error,
                                                size = 20.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (selectedAssetAllocations.size < assets.size) {
                            SparelyTextButton(
                                onClick = { assetSelectorExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("+ Add Asset")
                            }
                        }
                    }
                }

                // Line Items Section
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Line Items (${expenseItems.size})",
                            style = MaterialTheme.typography.labelLarge
                        )
                        SparelyTextButton(onClick = { showAddItemDialog = true }) {
                            Text("+ Add Item")
                        }
                    }
                    
                    if (expenseItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        expenseItems.forEachIndexed { index, item ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = ExpressiveShapes.small
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${item.quantity} × ${formatCurrency(item.unitPrice)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.totalPrice.formatCurrency(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                                        androidx.compose.material3.IconButton(
                                            onClick = { expenseItems.removeAt(index) }
                                        ) {
                                        MaterialSymbolIcon(
                                                icon = MaterialSymbols.DELETE,
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.error,
                                                size = 20.dp
                                            )
                                        }
                                    }
                                }
                            }
                            if (index < expenseItems.lastIndex) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
                
                // Actions
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
                            val amount = amountText.toDoubleOrNull()
                            if (description.isBlank() || amount == null || amount <= 0) {
                                showError = true
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

                                onConfirm(
                                    expense.copy(
                                        description = description.trim(),
                                        amount = amount,
                                        category = category,
                                        storeId = finalStoreId,
                                        notes = notes.trim().takeIf { it.isNotBlank() },
                                        orderNumber = orderNumber.trim().takeIf { it.isNotBlank() },
                                        date = selectedDate,
                                        paymentMethodId = selectedPaymentMethod?.id,
                                        type = expenseType,
                                        items = expenseItems.toList()
                                    ),
                                    selectedAssetAllocations
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }
            }
    }
    if (assetSelectorExpanded) {
        AlertDialog(
            onDismissRequest = { assetSelectorExpanded = false },
            title = { Text("Select Asset to Link") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    assets.filter { !selectedAssetAllocations.containsKey(it.id) }.forEach { asset ->
                        androidx.compose.material3.Button(
                            onClick = {
                                selectedAssetAllocations = selectedAssetAllocations.toMutableMap().apply {
                                    put(asset.id, 1.0)
                                }
                                assetSelectorExpanded = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(asset.name)
                                Text(
                                    asset.category.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(onClick = { assetSelectorExpanded = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                SparelyTextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneOffset.UTC)
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                SparelyTextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            androidx.compose.material3.DatePicker(state = datePickerState)
        }
    }
    
    // Add Item Dialog
    if (showAddItemDialog) {
        var itemName by remember { mutableStateOf("") }
        var itemQuantity by remember { mutableStateOf("1") }
        var itemUnitPrice by remember { mutableStateOf("") }

        SparelyBottomSheet(isOpen = true, onDismiss = { showAddItemDialog = false }) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                    Text(
                        text = "Add Item",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    SparelyTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text("Item Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SparelyTextField(
                            value = itemQuantity,
                            onValueChange = { itemQuantity = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Qty") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )
                        SparelyTextField(
                            value = itemUnitPrice,
                            onValueChange = { itemUnitPrice = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("Unit Price") },
                            modifier = Modifier.weight(2f),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                            )
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SparelyTextButton(
                            onClick = { showAddItemDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        SparelyButton(
                            onClick = {
                                val qty = itemQuantity.toIntOrNull() ?: 1
                                val price = itemUnitPrice.toDoubleOrNull() ?: 0.0
                                if (itemName.isNotBlank() && price > 0) {
                                    expenseItems.add(
                                        com.example.sparely.domain.model.ExpenseItem(
                                            id = 0L,
                                            expenseId = expense.id,
                                            name = itemName.trim(),
                                            quantity = qty.coerceAtLeast(1),
                                            unitPrice = price,
                                            totalPrice = qty * price
                                        )
                                    )
                                    showAddItemDialog = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Add")
                        }
                    }
                }
            }
        }
    }




@Composable
private fun StoreAnalyticsCard(
    storeStats: List<Triple<Store, Double, Int>>,
    totalSpent: Double,
    onStoreClick: (Store) -> Unit
) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        contentPadding = 24.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Top Stores",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                MaterialSymbolIcon(
                    icon = MaterialSymbols.SHOPPING_BAG,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 24.dp
                )
            }
            
            storeStats.forEach { (store, amount, count) ->
                val percentage = if (totalSpent > 0) (amount / totalSpent * 100) else 0.0
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onStoreClick(store) }
                        .padding(vertical = 4.dp)
                ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = store.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$count purchase${if (count > 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = amount.formatCurrency(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = percentage.formatPercent(1),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // Progress bar showing percentage of total spending
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (percentage / 100).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
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
    var amountText by remember { mutableStateOf(maxRefundable.toString()) }
    var selectedItemIds by remember { mutableStateOf(emptySet<Long>()) }
    var showError by remember { mutableStateOf(false) }
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

    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Refund Expense",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Select items refunded or enter custom amount. Max refundable: ${maxRefundable.formatCurrency()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (expense.items.isNotEmpty()) {
                    Text(
                        text = "Itemized Refund - Select items to refund",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        expense.items.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = selectedItemIds.contains(item.id),
                                    onCheckedChange = { isChecked ->
                                        val newSelection = selectedItemIds.toMutableSet()
                                        if (isChecked) {
                                            newSelection.add(item.id)
                                        } else {
                                            newSelection.remove(item.id)
                                        }
                                        selectedItemIds = newSelection
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name.ifBlank { "Item ${item.id}" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Qty: ${item.quantity} × ${item.unitPrice.formatCurrency()} = ${item.totalPrice.formatCurrency()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    if (selectedItemIds.isNotEmpty() && !wasManuallyEdited) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Amount will be calculated from selected items",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                SparelyTextField(
                    value = amountText,
                    onValueChange = { 
                        amountText = it.filter { c -> c.isDigit() || c == '.' }
                        wasManuallyEdited = true
                    },
                    label = { Text("Refund Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("$") }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SparelyButton(
                    onClick = {
                        val refundAmt = amountText.toDoubleOrNull() ?: 0.0
                        if (refundAmt > 0 && refundAmt <= maxRefundable + 0.01) {
                            onConfirm(refundAmt, selectedItemIds.toList())
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0 && (amountText.toDoubleOrNull() ?: 0.0) <= maxRefundable + 0.01
                ) {
                    Text("Refund")
                }
                
                SparelyTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
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


