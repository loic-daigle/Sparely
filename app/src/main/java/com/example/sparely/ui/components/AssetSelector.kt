package com.example.sparely.ui.components

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
 * Reusable asset selection bottom sheet component
 * Displays available assets with search functionality
 *
 * @param isOpen Whether the sheet is visible
 * @param selectedAssetIds Set of already-selected asset IDs to exclude
 * @param assets List of available assets to select from
 * @param onAssetSelected Callback when an asset is selected
 * @param onDismiss Callback when the sheet is dismissed
 * @param title Optional custom title for the sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetSelectionDialog(
    isOpen: Boolean,
    selectedAssetIds: Set<Long>,
    assets: List<Asset>,
    onAssetSelected: (Asset) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Link Asset"
) {
    var searchQuery by remember { mutableStateOf("") }
    val availableAssets = assets.filter { !selectedAssetIds.contains(it.id) }
    val filteredAssets = availableAssets.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.category.displayName().contains(searchQuery, ignoreCase = true)
    }

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
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Search field
            SparelyTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search assets...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (availableAssets.isEmpty()) {
                Text(
                    "All assets are already linked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else if (filteredAssets.isEmpty()) {
                Text(
                    "No assets match your search",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredAssets.forEach { asset ->
                        AssetSelectionCard(
                            asset = asset,
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
}

/**
 * Individual asset card for selection
 */
@Composable
private fun AssetSelectionCard(
    asset: Asset,
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    asset.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    asset.category.displayName(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (asset.totalSpending > 0) {
                    Text(
                        "Total: ${asset.totalSpending.formatCurrency()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            MaterialSymbolIcon(
                icon = MaterialSymbols.ADD_CIRCLE,
                contentDescription = "Add",
                tint = MaterialTheme.colorScheme.primary,
                size = 28.dp
            )
        }
    }
}
