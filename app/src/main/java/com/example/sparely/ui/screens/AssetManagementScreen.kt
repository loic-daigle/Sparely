package com.example.sparely.ui.screens

import com.example.sparely.ui.utils.filterCurrencyInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.ADD,
                        contentDescription = "Add Asset",
                        size = 24.dp
                    )
                    Text("Add Asset", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    ) { paddingValues ->
        if (assets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.SHOPPING_BAG,
                        contentDescription = null,
                        size = 64.dp,
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No assets yet",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary card
                item {
                    ExpressiveCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Total Spending",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                currencyFormatter.format(totalSpending),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${assets.size} assets",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // Asset list
                items(assets) { asset ->
                    AssetCard(
                        asset = asset,
                        currencyFormatter = currencyFormatter,
                        projection = assetProjections[asset.id],
                        onViewDetails = {
                            selectedAssetForDetails = asset
                            isLoadingDetails = true
                        },
                        onEdit = { assetToEdit = it },
                        onDelete = { assetToDelete = it }
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
            title = { Text("Delete Asset?") },
            text = { Text("Delete ${assetToDelete!!.name}? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAsset(assetToDelete!!.id)
                        assetToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { assetToDelete = null }) {
                    Text("Cancel")
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
    onDelete: (Asset) -> Unit
) {
    ExpressiveCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetails(asset) }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        asset.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        asset.category.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onEdit(asset) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.EDIT,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { onDelete(asset) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.DELETE,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (asset.description != null) {
                Text(
                    asset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Creator expense indicator
            if (asset.creatorExpenseId != null) {
                ExpressiveCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.CHECK_CIRCLE,
                            contentDescription = null,
                            size = 18.dp,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Created from expense #${asset.creatorExpenseId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Total Spending",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        currencyFormatter.format(asset.totalSpending),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (asset.assetPrice > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Asset Cost",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            currencyFormatter.format(asset.assetPrice),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (asset.totalSpending > asset.assetPrice)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Show projection summary if available
            if (projection != null) {
                Divider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "6-Month Projection",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            currencyFormatter.format(projection.totalProjected6Months),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "12-Month Projection",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            currencyFormatter.format(projection.totalProjected12Months),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                projection.costVsAssetPrice?.isOverBudget == true ->
                                    MaterialTheme.colorScheme.error
                                else ->
                                    MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = if (asset == null) "New Asset" else "Edit Asset",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SparelyTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Asset Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Category dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory.name,
                        onValueChange = {},
                        label = { Text("Category") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        AssetCategory.entries.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategory = category
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                SparelyTextField(
                    value = assetPriceText,
                    onValueChange = { assetPriceText = it.filterCurrencyInput() },
                    label = { Text("Asset Cost (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                // Show projection preview if asset price is set and projection is available
                if (assetPriceText.isNotEmpty() && projection != null && projection.costVsAssetPrice != null) {
                    val costVsPrice = projection.costVsAssetPrice
                    ExpressiveCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
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
                                    "At current spending pace, this asset will exceed budget"
                                else
                                    "At current spending pace, remaining budget: ${currencyFormatter.format(costVsPrice.remainingBudget)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SparelyTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
            }

            // Creator Expense Section
            if (asset != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    "Creator Expense",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                if (asset.creatorExpenseId != null) {
                    ExpressiveCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
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
                                Column {
                                    Text(
                                        "Linked Expense",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        "Expense #${asset.creatorExpenseId}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                SparelyTonalButton(
                                    onClick = { onUnlinkCreatorExpense() },
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Unlink", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showCreatorExpenseDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.SYNC, 
                                contentDescription = null, 
                                size = 20.dp
                            )
                            Text("Link Creator Expense")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SparelyTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                SparelyButton(
                    onClick = {
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
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank()
                ) {
                    Text("Save Asset")
                }
            }
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
            Text(
                text = "${asset.name} Details",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (projection != null) {
                com.example.sparely.ui.components.AssetCostProjectionCard(projection = projection)
            }

            Text(
                "Linked Expenses",
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
                            "No expenses linked to this asset",
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
                Text("Close")
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
            Text(
                "Link Creator Expense",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                "Select the expense that created this asset",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Search field
            SparelyTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("Search expenses") },
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
                        "No expenses found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (filteredExpenses.isEmpty()) {
                    Text(
                        "No matching expenses",
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
                Text("Cancel")
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
