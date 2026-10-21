package com.example.sparely.ui.components

import com.sparely.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.Asset
import com.example.sparely.domain.model.displayName
import com.example.sparely.ui.components.SparelyBottomSheet
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols

/**
 * Picks a project or asset to link: projects under way first (the one being worked on at the
 * top), then assets. Finished projects and those already linked aren't offered.
 *
 * @param onCreateNew When set, a button at the bottom starts a new project or asset
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetSelectionDialog(
    isOpen: Boolean,
    selectedAssetIds: Set<Long>,
    assets: List<Asset>,
    onAssetSelected: (Asset) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.asset_selector_title),
    workingOnProjectId: Long? = null,
    onCreateNew: (() -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    val availableAssets = assets.filter { !it.isDone && !selectedAssetIds.contains(it.id) }
    val filteredAssets = availableAssets.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.category.displayName().contains(searchQuery, ignoreCase = true)
    }
    val projects = filteredAssets.filter { it.isProject }.sortedByDescending { it.id == workingOnProjectId }
    val owned = filteredAssets.filter { !it.isProject }

    SparelyBottomSheet(
        isOpen = isOpen,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title
            SparelySheetHeader(
                title = title,
                icon = MaterialSymbols.FLAG
            )

            // Search only helps once there's a list to search
            if (availableAssets.size > 5) {
                SparelyTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.asset_selector_search)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (availableAssets.isEmpty()) {
                Text(
                    stringResource(if (assets.any { !it.isDone }) R.string.asset_selector_all_linked else R.string.assets_empty_desc_short),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else if (filteredAssets.isEmpty()) {
                Text(
                    stringResource(R.string.asset_selector_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        R.string.projects_section_active to projects,
                        R.string.projects_section_assets to owned
                    ).forEach { (titleRes, list) ->
                        if (list.isNotEmpty()) {
                            SheetSectionLabel(stringResource(titleRes))
                            list.forEach { asset ->
                                AssetSelectionCard(
                                    asset = asset,
                                    isWorkingOn = asset.id == workingOnProjectId,
                                    onSelect = {
                                        onAssetSelected(asset)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (onCreateNew != null) {
                SparelyTonalButton(
                    onClick = {
                        onDismiss()
                        onCreateNew()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = { MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null, size = 20.dp) }
                ) {
                    Text(stringResource(R.string.projects_create_new))
                }
            }
        }
    }
}

/**
 * One project or asset to pick: what it is, and what's been spent on it (against the budget for a
 * project that has one).
 */
@Composable
private fun AssetSelectionCard(
    asset: Asset,
    isWorkingOn: Boolean,
    onSelect: () -> Unit
) {
    ExpressiveCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MaterialSymbolIcon(
                icon = if (asset.isProject) MaterialSymbols.FLAG else MaterialSymbols.SHOPPING_BAG,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 22.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    asset.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    listOfNotNull(
                        stringResource(R.string.projects_working_on).takeIf { isWorkingOn },
                        asset.category.displayName()
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isWorkingOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    asset.isProject && asset.assetPrice > 0 -> Text(
                        stringResource(
                            R.string.projects_selector_spent_of,
                            asset.totalSpending.formatCurrency(),
                            asset.assetPrice.formatCurrency()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (asset.totalSpending > asset.assetPrice) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                    )
                    asset.totalSpending > 0 -> Text(
                        stringResource(R.string.asset_selector_total, asset.totalSpending.formatCurrency()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            MaterialSymbolIcon(
                icon = MaterialSymbols.ADD_CIRCLE,
                contentDescription = stringResource(R.string.store_add_confirm),
                tint = MaterialTheme.colorScheme.primary,
                size = 28.dp
            )
        }
    }
}
