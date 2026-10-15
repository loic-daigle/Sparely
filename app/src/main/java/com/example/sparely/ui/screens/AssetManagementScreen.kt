package com.example.sparely.ui.screens

import com.example.sparely.ui.utils.filterCurrencyInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.example.sparely.domain.model.displayName
import com.example.sparely.ui.components.SparelySheetHeader
import com.example.sparely.ui.components.SheetActions
import com.example.sparely.ui.components.SheetSectionLabel
import com.example.sparely.ui.components.SparelyChip
import com.example.sparely.ui.components.SparelyTextButton
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.sparely.ui.components.ExpressiveEmptyState
import com.example.sparely.ui.components.segmentSpacing
import com.example.sparely.ui.components.segmentedShape
import com.example.sparely.ui.theme.ExpressiveShapes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sparely.app.R
import com.example.sparely.domain.model.Asset
import com.example.sparely.domain.model.AssetCategory
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.components.SparelyBottomSheet
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelyTextField
import com.example.sparely.ui.components.SparelyTonalButton
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.utils.toSafeDouble
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import java.text.NumberFormat
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetManagementScreen(
    assets: List<Asset>,
    onNavigateBack: () -> Unit,
    onAddAsset: (Asset) -> Unit,
    onUpdateAsset: (Asset) -> Unit,
    onDeleteAsset: (Long) -> Unit,
    onLoadLinkedExpenses: suspend (Long) -> List<Pair<com.example.sparely.domain.model.Expense, Double>> = { emptyList() },
    onLoadAllExpenses: suspend () -> List<com.example.sparely.domain.model.Expense> = { emptyList() },
    onLinkCreatorExpense: (assetId: Long, expenseId: Long) -> Unit = { _, _ -> },
    onUnlinkCreatorExpense: (assetId: Long) -> Unit = { },
    onLoadAssetCostProjection: suspend (Long) -> com.example.sparely.domain.model.AssetCostProjection? = { null }
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var assetToEdit by remember { mutableStateOf<Asset?>(null) }
    var assetToDelete by remember { mutableStateOf<Asset?>(null) }
    var selectedAssetForDetails by remember { mutableStateOf<Asset?>(null) }
    var linkedExpenses by remember { mutableStateOf<List<Pair<com.example.sparely.domain.model.Expense, Double>>>(emptyList()) }
    var assetProjections by remember { mutableStateOf<Map<Long, com.example.sparely.domain.model.AssetCostProjection?>>(emptyMap()) }
    var isLoadingDetails by remember { mutableStateOf(false) }

    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance().apply {
            isGroupingUsed = false
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    val totalSpending = remember(assets) { assets.sumOf { it.totalSpending } }

    // Load linked expenses and projections when asset details are requested
    LaunchedEffect(selectedAssetForDetails) {
        if (selectedAssetForDetails != null) {
            linkedExpenses = onLoadLinkedExpenses(selectedAssetForDetails!!.id)
            isLoadingDetails = false
        }
    }

    // Load projections for all assets
    LaunchedEffect(assets) {
        val projections = mutableMapOf<Long, com.example.sparely.domain.model.AssetCostProjection?>()
        assets.forEach { asset ->
            projections[asset.id] = onLoadAssetCostProjection(asset.id)
        }
        assetProjections = projections
    }

    val listState = rememberLazyListState()
    // The FAB shows its label at the top of the list and shrinks to an icon once scrolling starts
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                expanded = fabExpanded,
                icon = {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.ADD,
                        contentDescription = if (fabExpanded) null else stringResource(R.string.assets_add),
                        size = 24.dp
                    )
                },
                text = { Text(stringResource(R.string.assets_add)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { paddingValues ->
        if (assets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                ExpressiveEmptyState(
                    icon = MaterialSymbols.SHOPPING_BAG,
                    title = stringResource(R.string.assets_empty_title_short),
                    description = stringResource(R.string.assets_empty_desc_short),
                    action = {
                        SparelyButton(
                            onClick = { showAddDialog = true },
                            icon = { MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null, size = 20.dp) }
                        ) {
                            Text(stringResource(R.string.assets_add))
                        }
                    }
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                // Extra bottom padding so the last card can scroll clear of the FAB
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
            ) {
                // Summary card
                item {
                    ExpressiveCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(R.string.assets_total_spending),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                currencyFormatter.format(totalSpending),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "${assets.size} assets",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // Assets as one segmented block: rounded ends, thin gaps
                itemsIndexed(assets) { index, asset ->
                    AssetCard(
                        asset = asset,
                        currencyFormatter = currencyFormatter,
                        projection = assetProjections[asset.id],
                        onViewDetails = {
                            selectedAssetForDetails = asset
                            isLoadingDetails = true
                        },
                        onEdit = { assetToEdit = it },
                        onDelete = { assetToDelete = it },
                        shape = segmentedShape(index, assets.size, outer = 28.dp),
                        modifier = Modifier.segmentSpacing(index)
                    )
                }
            }
        }
    }

    // Add/Edit Asset Bottom Sheet
    AssetBottomSheet(
        isOpen = showAddDialog || assetToEdit != null,
        asset = assetToEdit,
        currencyFormatter = currencyFormatter,
        projection = assetProjections[assetToEdit?.id],
        onSave = { asset ->
            if (assetToEdit != null) {
                onUpdateAsset(asset)
            } else {
                onAddAsset(asset)
            }
            showAddDialog = false
            assetToEdit = null
        },
        onDismiss = {
            showAddDialog = false
            assetToEdit = null
        },
        onLoadAllExpenses = onLoadAllExpenses,
        onLinkCreatorExpense = { expenseId ->
            assetToEdit?.let { asset ->
                onLinkCreatorExpense(asset.id, expenseId)
                // Reload the asset by triggering a state change
                assetToEdit = null
            }
        },
        onUnlinkCreatorExpense = {
            assetToEdit?.let { asset ->
                onUnlinkCreatorExpense(asset.id)
                // Reload the asset by triggering a state change
                assetToEdit = null
            }
        }
    )

    // Delete confirmation dialog
    if (assetToDelete != null) {
        AlertDialog(
            onDismissRequest = { assetToDelete = null },
            title = { Text(stringResource(R.string.assets_delete_title)) },
            text = { Text(stringResource(R.string.assets_delete_message, assetToDelete!!.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAsset(assetToDelete!!.id)
                        assetToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { assetToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Asset Details Bottom Sheet
    AssetDetailsBottomSheet(
        asset = selectedAssetForDetails,
        linkedExpenses = linkedExpenses,
        projection = assetProjections[selectedAssetForDetails?.id],
        currencyFormatter = currencyFormatter,
        onDismiss = { selectedAssetForDetails = null }
    )
}

@Composable
private fun AssetCard(
    asset: Asset,
    currencyFormatter: NumberFormat,
    projection: com.example.sparely.domain.model.AssetCostProjection? = null,
    onViewDetails: (Asset) -> Unit,
    onEdit: (Asset) -> Unit,
    onDelete: (Asset) -> Unit,
    shape: androidx.compose.ui.graphics.Shape = ExpressiveShapes.large,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    ExpressiveCard(
        modifier = modifier.fillMaxWidth(),
        onClick = { onViewDetails(asset) },
        shape = shape,
        containerColor = colors.surfaceContainer,
        contentPadding = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(ExpressiveShapes.small)
                        .background(colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.SHOPPING_BAG,
                        contentDescription = null,
                        size = 22.dp,
                        tint = colors.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        asset.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        asset.category.displayName(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onEdit(asset) }) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.EDIT,
                        contentDescription = stringResource(R.string.action_edit_short),
                        size = 20.dp,
                        tint = colors.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onDelete(asset) }) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.DELETE,
                        contentDescription = stringResource(R.string.action_delete),
                        size = 20.dp,
                        tint = colors.onSurfaceVariant
                    )
                }
            }

            if (asset.description != null) {
                Text(
                    asset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // Creator expense indicator
            if (asset.creatorExpenseId != null) {
                Surface(
                    shape = CircleShape,
                    color = colors.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.CHECK_CIRCLE,
                            contentDescription = null,
                            size = 16.dp,
                            tint = colors.onPrimaryContainer
                        )
                        Text(
                            "Created from expense #${asset.creatorExpenseId}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onPrimaryContainer
                        )
                    }
                }
            }

            // Spending, cost and projections as small tiles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssetStat(
                    label = stringResource(R.string.assets_total_spending),
                    value = currencyFormatter.format(asset.totalSpending),
                    modifier = Modifier.weight(1f)
                )
                if (asset.assetPrice > 0) {
                    AssetStat(
                        label = stringResource(R.string.assets_cost),
                        value = currencyFormatter.format(asset.assetPrice),
                        valueColor = if (asset.totalSpending > asset.assetPrice) colors.error else colors.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (projection != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssetStat(
                        label = "6-Month Projection",
                        value = currencyFormatter.format(projection.totalProjected6Months),
                        valueColor = colors.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    AssetStat(
                        label = "12-Month Projection",
                        value = currencyFormatter.format(projection.totalProjected12Months),
                        valueColor = if (projection.costVsAssetPrice?.isOverBudget == true) colors.error else colors.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Label over value in a soft tile, for the numbers on an asset card. */
@Composable
private fun AssetStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier
            .clip(ExpressiveShapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AssetBottomSheet(
    isOpen: Boolean,
    asset: Asset?,
    currencyFormatter: NumberFormat,
    projection: com.example.sparely.domain.model.AssetCostProjection? = null,
    onSave: (Asset) -> Unit,
    onDismiss: () -> Unit,
    onLoadAllExpenses: suspend () -> List<com.example.sparely.domain.model.Expense> = { emptyList() },
    onLinkCreatorExpense: (expenseId: Long) -> Unit = { },
    onUnlinkCreatorExpense: () -> Unit = { }
) {
    var name by remember(isOpen, asset) { mutableStateOf(asset?.name ?: "") }
    var description by remember(isOpen, asset) { mutableStateOf(asset?.description ?: "") }
    var assetPriceText by remember(isOpen, asset) { 
        mutableStateOf(if ((asset?.assetPrice ?: 0.0) > 0) asset?.assetPrice?.formatCurrency("") ?: "" else "")
    }
    var selectedCategory by remember(isOpen, asset) { mutableStateOf(asset?.category ?: AssetCategory.OTHER) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var showCreatorExpenseDialog by remember { mutableStateOf(false) }
    var availableExpenses by remember { mutableStateOf<List<com.example.sparely.domain.model.Expense>>(emptyList()) }
    var expenseSearchQuery by remember { mutableStateOf("") }
    var isLoadingExpenses by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SparelySheetHeader(
                title = if (asset == null) stringResource(R.string.assets_new) else stringResource(R.string.assets_edit),
                icon = MaterialSymbols.SHOPPING_BAG
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SparelyTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.assets_name)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Category: every option visible, with readable names
                SheetSectionLabel(stringResource(R.string.category_label))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssetCategory.entries.forEach { category ->
                        SparelyChip(
                            selected = category == selectedCategory,
                            onClick = { selectedCategory = category },
                            label = { Text(category.displayName()) }
                        )
                    }
                }

                SparelyTextField(
                    value = assetPriceText,
                    onValueChange = { assetPriceText = it.filterCurrencyInput() },
                    label = { Text(stringResource(R.string.assets_cost_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    prefix = { Text(stringResource(R.string.currency_prefix)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                )

                // Show projection preview if asset price is set and projection is available
                if (assetPriceText.isNotEmpty() && projection != null && projection.costVsAssetPrice != null) {
                    val costVsPrice = projection.costVsAssetPrice
                    ExpressiveCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = ExpressiveShapes.medium,
                        contentPadding = 16.dp
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "12-Month Projection",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        currencyFormatter.format(costVsPrice.projectedIn12Months),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "vs. Target",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "${String.format("%.0f", costVsPrice.percentageOfTarget)}%",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when {
                                            costVsPrice.isOverBudget -> MaterialTheme.colorScheme.error
                                            costVsPrice.percentageOfTarget > 70 -> MaterialTheme.colorScheme.errorContainer
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                                }
                            }
                            Text(
                                if (costVsPrice.isOverBudget)
                                    stringResource(R.string.assets_pace_over)
                                else
                                    stringResource(R.string.assets_pace_remaining, currencyFormatter.format(costVsPrice.remainingBudget)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SparelyTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.assets_description_optional)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3
                )
            }

            // Creator Expense Section
            if (asset != null) {
                SheetSectionLabel(stringResource(R.string.assets_creator_expense))

                if (asset.creatorExpenseId != null) {
                    ExpressiveCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = ExpressiveShapes.medium,
                        contentPadding = 16.dp
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        stringResource(R.string.assets_linked_expense),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        "Expense #${asset.creatorExpenseId}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                SparelyTextButton(
                                    onClick = { onUnlinkCreatorExpense() },
                                    contentColor = MaterialTheme.colorScheme.error
                                ) {
                                    Text(stringResource(R.string.assets_unlink))
                                }
                            }
                        }
                    }
                } else {
                    SparelyTonalButton(
                        onClick = { showCreatorExpenseDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { MaterialSymbolIcon(icon = MaterialSymbols.SYNC, contentDescription = null, size = 20.dp) }
                    ) {
                        Text(stringResource(R.string.assets_link_creator))
                    }
                }
            }

            SheetActions(
                confirmLabel = stringResource(R.string.assets_save),
                onConfirm = {
                    if (name.isNotBlank()) {
                        val newAsset = Asset(
                            id = asset?.id ?: 0L,
                            name = name,
                            description = description.ifBlank { null },
                            category = selectedCategory,
                            assetPrice = assetPriceText.toSafeDouble() ?: 0.0,
                            createdAt = asset?.createdAt ?: LocalDateTime.now(),
                            archived = asset?.archived ?: false,
                            totalSpending = asset?.totalSpending ?: 0.0,
                            creatorExpenseId = asset?.creatorExpenseId
                        )
                        onSave(newAsset)
                    }
                },
                confirmEnabled = name.isNotBlank(),
                dismissLabel = stringResource(R.string.action_cancel),
                onDismiss = onDismiss
            )
        }
    }

    // Creator Expense Selection Bottom Sheet
    CreatorExpenseBottomSheet(
        isOpen = showCreatorExpenseDialog,
        onDismiss = {
            showCreatorExpenseDialog = false
            expenseSearchQuery = ""
        },
        searchQuery = expenseSearchQuery,
        onSearchQueryChange = { expenseSearchQuery = it },
        isLoading = isLoadingExpenses,
        expenses = availableExpenses,
        currencyFormatter = currencyFormatter,
        onSelectExpense = { expenseId ->
            onLinkCreatorExpense(expenseId)
            showCreatorExpenseDialog = false
            expenseSearchQuery = ""
        }
    )

    // Load expenses when bottom sheet opens
    LaunchedEffect(showCreatorExpenseDialog) {
        if (showCreatorExpenseDialog) {
            isLoadingExpenses = true
            availableExpenses = onLoadAllExpenses()
            isLoadingExpenses = false
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetDetailsBottomSheet(
    asset: Asset?,
    linkedExpenses: List<Pair<com.example.sparely.domain.model.Expense, Double>>,
    projection: com.example.sparely.domain.model.AssetCostProjection?,
    currencyFormatter: NumberFormat,
    onDismiss: () -> Unit
) {
    SparelyBottomSheet(
        isOpen = asset != null,
        onDismiss = onDismiss
    ) {
        if (asset == null) return@SparelyBottomSheet

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SparelySheetHeader(
                title = "${asset.name} Details",
                icon = MaterialSymbols.INFO
            )

            if (projection != null) {
                com.example.sparely.ui.components.AssetCostProjectionCard(projection = projection)
            }

            Text(
                stringResource(R.string.assets_linked_expenses),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.outline
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (linkedExpenses.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.assets_no_linked),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    linkedExpenses.forEach { (expense, percentage) ->
                        ExpressiveCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            expense.description,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            expense.date.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Text(
                                        currencyFormatter.format(expense.amount * percentage),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Full Amount:",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        currencyFormatter.format(expense.amount),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Allocation:",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        "${String.format("%.0f", percentage * 100)}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SparelyButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_close_short))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatorExpenseBottomSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isLoading: Boolean,
    expenses: List<com.example.sparely.domain.model.Expense>,
    currencyFormatter: NumberFormat,
    onSelectExpense: (Long) -> Unit
) {
    val filteredExpenses = remember(searchQuery, expenses) {
        if (searchQuery.isBlank()) {
            expenses
        } else {
            expenses.filter { expense ->
                expense.description.contains(searchQuery, ignoreCase = true) ||
                expense.id.toString().contains(searchQuery) ||
                expense.category.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    SparelyBottomSheet(
        isOpen = isOpen,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SparelySheetHeader(
                title = stringResource(R.string.assets_link_creator),
                icon = MaterialSymbols.RECEIPT
            )

            Text(
                stringResource(R.string.assets_select_creator),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Search field
            SparelyTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text(stringResource(R.string.assets_search_expenses)) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.SEARCH,
                        contentDescription = null,
                        size = 20.dp
                    )
                }
            )

            // Expenses list
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (expenses.isEmpty()) {
                    Text(
                        stringResource(R.string.assets_no_expenses),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (filteredExpenses.isEmpty()) {
                    Text(
                        stringResource(R.string.assets_no_matching),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredExpenses.forEach { expense ->
                            ExpenseSelectCard(
                                expense = expense,
                                currencyFormatter = currencyFormatter,
                                onSelect = {
                                    onSelectExpense(expense.id!!)
                                }
                            )
                        }
                    }
                }
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

@Composable
private fun ExpenseSelectCard(
    expense: com.example.sparely.domain.model.Expense,
    currencyFormatter: NumberFormat,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        expense.description,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        "ID: ${expense.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    currencyFormatter.format(expense.amount),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    expense.category.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    expense.date.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
