@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.sparely.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.Asset
import com.example.sparely.domain.model.AssetCategory
import com.example.sparely.domain.model.AssetCostProjection
import com.example.sparely.domain.model.AssetKind
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.defaultKind
import com.example.sparely.domain.model.displayName
import com.example.sparely.domain.model.netAmount
import com.example.sparely.ui.components.ExpressiveEmptyState
import com.example.sparely.ui.components.ExpressiveListHeader
import com.example.sparely.ui.components.SegmentedListItem
import com.example.sparely.ui.components.SheetActions
import com.example.sparely.ui.components.SheetSectionLabel
import com.example.sparely.ui.components.SparelyAlertDialog
import com.example.sparely.ui.components.SparelyAmountTextField
import com.example.sparely.ui.components.SparelyBottomSheet
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelyChip
import com.example.sparely.ui.components.SparelySheetHeader
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.SparelyTextField
import com.example.sparely.ui.components.SparelyTonalButton
import com.example.sparely.ui.components.segmentSpacing
import com.example.sparely.ui.components.segmentedShape
import com.example.sparely.ui.theme.ExpressiveShapes
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.critical
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.utils.toInputString
import com.example.sparely.ui.utils.toSafeDouble
import com.sparely.app.R
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Projects and assets: what's been spent on each, grouped into projects under way, assets and
 * finished projects. Tapping one opens its sheet (spending, budget, what's linked, its actions);
 * the + makes a new one.
 */
@Composable
fun AssetManagementScreen(
    assets: List<Asset>,
    onNavigateBack: () -> Unit,
    onAddAsset: (Asset) -> Unit,
    onUpdateAsset: (Asset) -> Unit,
    onDeleteAsset: (Long) -> Unit,
    onLoadLinkedExpenses: suspend (Long) -> List<Pair<Expense, Double>> = { emptyList() },
    onLoadAllExpenses: suspend () -> List<Expense> = { emptyList() },
    onLinkCreatorExpense: (assetId: Long, expenseId: Long) -> Unit = { _, _ -> },
    onUnlinkCreatorExpense: (assetId: Long) -> Unit = { },
    onLoadAssetCostProjection: suspend (Long) -> AssetCostProjection? = { null },
    workingOnProjectId: Long? = null,
    onSetWorkingOn: (assetId: Long?) -> Unit = {},
    onSetProjectDone: (asset: Asset, done: Boolean) -> Unit = { _, _ -> }
) {
    var creating by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var openId by remember { mutableStateOf<Long?>(null) }
    var deletingId by remember { mutableStateOf<Long?>(null) }

    // Projects under way first, then what's owned, then finished projects with their final totals
    val activeProjects = remember(assets) { assets.filter { it.isProject && !it.isDone } }
    val ownedAssets = remember(assets) { assets.filter { !it.isProject } }
    val finishedProjects = remember(assets) { assets.filter { it.isDone }.sortedByDescending { it.completedAt } }
    val workingOn = assets.find { it.id == workingOnProjectId && it.isProject && !it.isDone }

    val listState = rememberLazyListState()
    // The FAB shows its label at the top of the list and shrinks to an icon once scrolling starts
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
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
                    icon = MaterialSymbols.FLAG,
                    title = stringResource(R.string.assets_empty_title_short),
                    description = stringResource(R.string.assets_empty_desc_short),
                    action = {
                        SparelyButton(
                            onClick = { creating = true },
                            icon = { MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null, size = 20.dp) }
                        ) { Text(stringResource(R.string.assets_add)) }
                    }
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                // Extra bottom padding so the last row can scroll clear of the FAB
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
            ) {
                item(key = "hero") {
                    AssetsHero(
                        total = assets.sumOf { it.totalSpending },
                        projectCount = activeProjects.size + finishedProjects.size,
                        assetCount = ownedAssets.size,
                        workingOn = workingOn,
                        onOpenWorkingOn = { workingOn?.let { openId = it.id } }
                    )
                }

                fun LazyListScope.section(key: String, titleRes: Int, @DrawableRes icon: Int, list: List<Asset>) {
                    if (list.isEmpty()) return
                    item(key = "header_$key") {
                        ExpressiveListHeader(
                            title = stringResource(titleRes),
                            icon = icon,
                            trailing = list.sumOf { it.totalSpending }.formatCurrency(decimals = 0)
                        )
                    }
                    itemsIndexed(list, key = { _, asset -> asset.id }) { index, asset ->
                        AssetRow(
                            asset = asset,
                            isWorkingOn = asset.id == workingOnProjectId,
                            shape = segmentedShape(index, list.size),
                            modifier = Modifier.segmentSpacing(index),
                            onClick = { openId = asset.id }
                        )
                    }
                }
                section("active", R.string.projects_section_active, MaterialSymbols.FLAG, activeProjects)
                section("assets", R.string.projects_section_assets, MaterialSymbols.SHOPPING_BAG, ownedAssets)
                section("finished", R.string.projects_section_finished, MaterialSymbols.CHECK_CIRCLE, finishedProjects)
            }
        }
    }

    // The open sheet follows the list, so its numbers update while it's open
    openId?.let { id ->
        val asset = assets.find { it.id == id }
        if (asset == null) {
            openId = null
        } else {
            AssetDetailSheet(
                asset = asset,
                isWorkingOn = asset.id == workingOnProjectId,
                onLoadLinkedExpenses = onLoadLinkedExpenses,
                onLoadProjection = onLoadAssetCostProjection,
                onLoadAllExpenses = onLoadAllExpenses,
                onLinkCreatorExpense = { expenseId -> onLinkCreatorExpense(asset.id, expenseId) },
                onUnlinkCreatorExpense = { onUnlinkCreatorExpense(asset.id) },
                onToggleWorkingOn = { onSetWorkingOn(if (asset.id == workingOnProjectId) null else asset.id) },
                onSetDone = { done -> onSetProjectDone(asset, done) },
                onEdit = { editingId = asset.id },
                onDelete = { deletingId = asset.id },
                onDismiss = { openId = null }
            )
        }
    }

    if (creating || editingId != null) {
        AssetEditorSheet(
            asset = assets.find { it.id == editingId },
            onSave = { saved ->
                if (editingId != null) onUpdateAsset(saved) else onAddAsset(saved)
                creating = false
                editingId = null
            },
            onDismiss = {
                creating = false
                editingId = null
            }
        )
    }

    deletingId?.let { id ->
        val asset = assets.find { it.id == id }
        SparelyAlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.assets_delete_title)) },
            text = { Text(stringResource(R.string.assets_delete_message, asset?.name.orEmpty())) },
            confirmButton = {
                SparelyButton(
                    onClick = {
                        onDeleteAsset(id)
                        if (workingOnProjectId == id) onSetWorkingOn(null)
                        deletingId = null
                        openId = null
                    },
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                SparelyTextButton(onClick = { deletingId = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

/** The icon for a project or asset, from what it is. */
@DrawableRes
private fun Asset.icon(): Int = when {
    isDone -> MaterialSymbols.CHECK_CIRCLE
    else -> when (category) {
        AssetCategory.CAR -> MaterialSymbols.DIRECTIONS_CAR
        AssetCategory.HOUSE, AssetCategory.ROOM -> MaterialSymbols.HOME
        AssetCategory.VACATION -> MaterialSymbols.FLIGHT
        AssetCategory.HOBBY -> MaterialSymbols.PALETTE
        AssetCategory.EDUCATION -> MaterialSymbols.SCHOOL
        AssetCategory.HEALTH -> MaterialSymbols.HEALTH_AND_SAFETY
        AssetCategory.OTHER, AssetCategory.CUSTOM -> if (isProject) MaterialSymbols.FLAG else MaterialSymbols.SHOPPING_BAG
    }
}

private val mediumDate: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** A project's line under its name: finished on a date, being worked on, or its category. */
@Composable
private fun assetSubtitle(asset: Asset, isWorkingOn: Boolean): String = when {
    asset.isDone -> asset.completedAt?.let { stringResource(R.string.projects_done_on, it.toLocalDate().format(mediumDate)) }
        ?: stringResource(R.string.projects_section_finished)
    isWorkingOn -> listOf(stringResource(R.string.projects_working_on), asset.category.displayName()).joinToString(" · ")
    else -> asset.category.displayName()
}

@Composable
private fun AssetsHero(
    total: Double,
    projectCount: Int,
    assetCount: Int,
    workingOn: Asset?,
    onOpenWorkingOn: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val onContainer = colors.onPrimaryContainer
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        color = colors.primaryContainer,
        contentColor = onContainer
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.assets_hero_label),
                style = MaterialTheme.typography.labelLarge,
                color = onContainer.copy(alpha = 0.8f)
            )
            Text(
                total.formatCurrency(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                listOfNotNull(
                    projectCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.projects_count, it, it) },
                    assetCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.projects_assets_count, it, it) }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer.copy(alpha = 0.8f)
            )
            if (workingOn != null) {
                // New expenses go to this project; tapping opens it
                Surface(
                    onClick = onOpenWorkingOn,
                    shape = CircleShape,
                    color = colors.surface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MaterialSymbolIcon(icon = MaterialSymbols.FLAG, contentDescription = null, size = 16.dp)
                        Text(
                            stringResource(R.string.assets_hero_working_on, workingOn.name),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        MaterialSymbolIcon(icon = MaterialSymbols.ARROW_FORWARD, contentDescription = null, size = 16.dp)
                    }
                }
            }
        }
    }
}

/**
 * One project or asset: icon, name and what it is, what's been spent, and against its budget (or
 * cost) a bar with what's left or how far over it is.
 */
@Composable
private fun AssetRow(
    asset: Asset,
    isWorkingOn: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val budget = asset.assetPrice.takeIf { it > 0 }
    val over = budget != null && asset.totalSpending > budget
    val accent = when {
        asset.isDone -> colors.success
        over -> colors.critical
        else -> colors.primary
    }
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = colors.surfaceContainer) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialSymbolIcon(icon = asset.icon(), contentDescription = null, size = 22.dp, tint = accent)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        asset.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        assetSubtitle(asset, isWorkingOn),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isWorkingOn) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isWorkingOn) colors.primary else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        asset.totalSpending.formatCurrency(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    budget?.let {
                        Text(
                            stringResource(R.string.assets_of_amount, it.formatCurrency(decimals = 0)),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
            if (budget != null) {
                LinearProgressIndicator(
                    progress = { (asset.totalSpending / budget).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(ExpressiveShapes.pill),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.18f),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                Text(
                    if (over) stringResource(R.string.assets_over_by, (asset.totalSpending - budget).formatCurrency(decimals = 0))
                    else stringResource(R.string.vault_line_left, (budget - asset.totalSpending).formatCurrency(decimals = 0)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) colors.critical else colors.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Everything about one project or asset: what's been spent against its budget, this month and a
 * month on average, the expenses linked to it, and its actions (working on, done, edit, delete).
 */
@Composable
private fun AssetDetailSheet(
    asset: Asset,
    isWorkingOn: Boolean,
    onLoadLinkedExpenses: suspend (Long) -> List<Pair<Expense, Double>>,
    onLoadProjection: suspend (Long) -> AssetCostProjection?,
    onLoadAllExpenses: suspend () -> List<Expense>,
    onLinkCreatorExpense: (Long) -> Unit,
    onUnlinkCreatorExpense: () -> Unit,
    onToggleWorkingOn: () -> Unit,
    onSetDone: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var linked by remember(asset.id) { mutableStateOf<List<Pair<Expense, Double>>?>(null) }
    var projection by remember(asset.id) { mutableStateOf<AssetCostProjection?>(null) }
    var pickingPurchase by remember { mutableStateOf(false) }
    // Reloaded when its total moves (an expense linked, edited or refunded meanwhile)
    LaunchedEffect(asset.id, asset.totalSpending) {
        linked = onLoadLinkedExpenses(asset.id).sortedByDescending { it.first.date }
        projection = onLoadProjection(asset.id)
    }
    val thisMonth = YearMonth.now()
    val spentThisMonth = linked.orEmpty()
        .filter { YearMonth.from(it.first.date) == thisMonth }
        .sumOf { (expense, share) -> expense.netAmount * share }
    val budget = asset.assetPrice.takeIf { it > 0 }
    val over = budget != null && asset.totalSpending > budget
    val purchase = linked?.firstOrNull { it.first.id == asset.creatorExpenseId }?.first

    SparelyBottomSheet(isOpen = true, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SparelySheetHeader(
                title = asset.name,
                subtitle = listOfNotNull(
                    stringResource(if (asset.isProject) R.string.projects_kind_project else R.string.projects_kind_asset),
                    assetSubtitle(asset, isWorkingOn),
                    asset.description
                ).joinToString(" · "),
                icon = asset.icon()
            )

            // Spent, against the budget
            Surface(shape = ExpressiveShapes.large, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.projects_spent), style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer.copy(alpha = 0.8f))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(asset.totalSpending.formatCurrency(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        budget?.let {
                            Text(
                                " / " + it.formatCurrency(decimals = 0),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                    if (budget != null) {
                        LinearProgressIndicator(
                            progress = { (asset.totalSpending / budget).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = if (over) colors.critical else colors.primary,
                            trackColor = colors.onPrimaryContainer.copy(alpha = 0.12f),
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                        Text(
                            if (over) stringResource(R.string.assets_over_by, (asset.totalSpending - budget).formatCurrency())
                            else stringResource(R.string.vault_line_left, (budget - asset.totalSpending).formatCurrency()),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (over) colors.critical else colors.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // This month, a month on average, the next 12 months
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.assets_this_month), spentThisMonth.formatCurrency(decimals = 0), Modifier.weight(1f).fillMaxHeight())
                projection?.takeIf { it.averageMonthly > 0 }?.let { p ->
                    StatTile(stringResource(R.string.assets_per_month), p.averageMonthly.formatCurrency(decimals = 0), Modifier.weight(1f).fillMaxHeight())
                    if (!asset.isDone) {
                        StatTile(stringResource(R.string.assets_next_12_months), p.totalProjected12Months.formatCurrency(decimals = 0), Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }

            // Actions
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (asset.isProject && !asset.isDone) {
                    SparelyChip(
                        selected = isWorkingOn,
                        onClick = onToggleWorkingOn,
                        label = { Text(stringResource(R.string.projects_working_on)) },
                        leadingIcon = if (isWorkingOn) {
                            { MaterialSymbolIcon(icon = MaterialSymbols.CHECK, contentDescription = null, size = 18.dp) }
                        } else null
                    )
                }
                if (asset.isProject) {
                    SparelyTonalButton(
                        onClick = { onSetDone(!asset.isDone) },
                        icon = {
                            MaterialSymbolIcon(
                                icon = if (asset.isDone) MaterialSymbols.UNDO else MaterialSymbols.CHECK_CIRCLE,
                                contentDescription = null,
                                size = 18.dp
                            )
                        }
                    ) { Text(stringResource(if (asset.isDone) R.string.projects_reopen else R.string.projects_mark_done)) }
                }
                SparelyTonalButton(
                    onClick = onEdit,
                    icon = { MaterialSymbolIcon(icon = MaterialSymbols.EDIT, contentDescription = null, size = 18.dp) }
                ) { Text(stringResource(R.string.action_edit_short)) }
            }
            if (isWorkingOn) {
                Text(stringResource(R.string.projects_working_on_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }

            // What an asset was bought with
            if (!asset.isProject) {
                if (purchase != null) {
                    SegmentedListItem(
                        title = purchase.description,
                        subtitle = stringResource(R.string.assets_bought_with, purchase.date.format(mediumDate)),
                        shape = ExpressiveShapes.large,
                        icon = MaterialSymbols.RECEIPT,
                        trailing = {
                            SparelyTextButton(onClick = onUnlinkCreatorExpense, contentColor = colors.error) {
                                Text(stringResource(R.string.assets_unlink))
                            }
                        }
                    )
                } else if (asset.creatorExpenseId == null) {
                    SparelyTextButton(
                        onClick = { pickingPurchase = true },
                        icon = { MaterialSymbolIcon(icon = MaterialSymbols.RECEIPT, contentDescription = null, size = 18.dp) }
                    ) { Text(stringResource(R.string.assets_link_creator)) }
                }
            }

            // Linked expenses
            val items = linked
            ExpressiveListHeader(
                title = stringResource(R.string.assets_linked_expenses),
                icon = MaterialSymbols.RECEIPT,
                trailing = items?.size?.takeIf { it > 0 }?.toString()
            )
            when {
                items == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                items.isEmpty() -> Text(
                    stringResource(R.string.assets_no_linked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
                else -> Column {
                    items.forEachIndexed { index, (expense, share) ->
                        val counted = expense.netAmount * share
                        SegmentedListItem(
                            title = expense.description,
                            subtitle = listOfNotNull(
                                expense.date.format(mediumDate),
                                if (share < 0.999) stringResource(R.string.assets_share_of, share.formatPercent(0), expense.netAmount.formatCurrency()) else null,
                                if (expense.refundedAmount > 0) stringResource(R.string.assets_refunded, expense.refundedAmount.formatCurrency()) else null
                            ).joinToString(" · "),
                            shape = segmentedShape(index, items.size),
                            modifier = Modifier.segmentSpacing(index),
                            containerColor = colors.surfaceContainerHigh,
                            trailing = {
                                Text(counted.formatCurrency(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            }
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error)
            ) {
                MaterialSymbolIcon(icon = MaterialSymbols.DELETE, contentDescription = null, size = 18.dp, tint = colors.error)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (asset.isProject) R.string.assets_delete_project else R.string.assets_delete_asset))
            }
        }
    }

    if (pickingPurchase) {
        PurchasePickerSheet(
            onLoadAllExpenses = onLoadAllExpenses,
            onSelect = { id ->
                onLinkCreatorExpense(id)
                pickingPurchase = false
            },
            onDismiss = { pickingPurchase = false }
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = ExpressiveShapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** New or edited project or asset: what it is, its name and category, its budget or cost, a note. */
@Composable
private fun AssetEditorSheet(
    asset: Asset?,
    onSave: (Asset) -> Unit,
    onDismiss: () -> Unit
) {
    var kind by remember(asset?.id) { mutableStateOf(asset?.kind ?: AssetKind.PROJECT) }
    // A new one follows its category's usual kind until the user picks a kind themselves
    var kindTouched by remember(asset?.id) { mutableStateOf(asset != null) }
    var name by remember(asset?.id) { mutableStateOf(asset?.name.orEmpty()) }
    var category by remember(asset?.id) { mutableStateOf(asset?.category ?: AssetCategory.OTHER) }
    var amountText by remember(asset?.id) { mutableStateOf(asset?.assetPrice?.takeIf { it > 0 }?.toInputString().orEmpty()) }
    var description by remember(asset?.id) { mutableStateOf(asset?.description.orEmpty()) }
    val isProject = kind == AssetKind.PROJECT

    SparelyBottomSheet(isOpen = true, onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SparelySheetHeader(
                    title = stringResource(
                        when {
                            asset == null && isProject -> R.string.projects_new
                            asset == null -> R.string.assets_new
                            isProject -> R.string.projects_edit
                            else -> R.string.assets_edit
                        }
                    ),
                    icon = if (isProject) MaterialSymbols.FLAG else MaterialSymbols.SHOPPING_BAG
                )

                SheetSectionLabel(stringResource(R.string.projects_kind_label))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ExpressiveShapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    KindOption(
                        title = stringResource(R.string.projects_kind_project),
                        description = stringResource(R.string.projects_kind_project_desc),
                        selected = isProject,
                        modifier = Modifier.weight(1f),
                        onClick = { kind = AssetKind.PROJECT; kindTouched = true }
                    )
                    KindOption(
                        title = stringResource(R.string.projects_kind_asset),
                        description = stringResource(R.string.projects_kind_asset_desc),
                        selected = !isProject,
                        modifier = Modifier.weight(1f),
                        onClick = { kind = AssetKind.ASSET; kindTouched = true }
                    )
                }

                SparelyTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(if (isProject) R.string.projects_name else R.string.assets_name)) },
                    placeholder = {
                        Text(stringResource(if (isProject) R.string.projects_project_name_placeholder else R.string.asset_name_placeholder))
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                SheetSectionLabel(stringResource(R.string.category_label))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssetCategory.entries.filter { it != AssetCategory.CUSTOM || category == AssetCategory.CUSTOM }.forEach { option ->
                        SparelyChip(
                            selected = option == category,
                            onClick = {
                                category = option
                                if (!kindTouched) kind = option.defaultKind()
                            },
                            label = { Text(option.displayName()) }
                        )
                    }
                }

                SparelyAmountTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(if (isProject) R.string.projects_budget_optional else R.string.assets_cost_optional)) },
                    prefix = { Text(stringResource(R.string.currency_prefix)) },
                    modifier = Modifier.fillMaxWidth()
                )

                SparelyTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.assets_description_optional)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp)) {
                SheetActions(
                    confirmLabel = stringResource(if (asset == null) R.string.asset_create_confirm else R.string.assets_save),
                    onConfirm = {
                        if (name.isNotBlank()) {
                            val base = asset ?: Asset(name = "", category = category, createdAt = LocalDateTime.now())
                            onSave(
                                base.copy(
                                    name = name.trim(),
                                    description = description.trim().ifBlank { null },
                                    category = category,
                                    kind = kind,
                                    assetPrice = amountText.toSafeDouble()?.coerceAtLeast(0.0) ?: 0.0,
                                    // Only a project can be done
                                    completedAt = asset?.completedAt?.takeIf { isProject }
                                )
                            )
                        }
                    },
                    confirmEnabled = name.isNotBlank(),
                    dismissLabel = stringResource(R.string.action_cancel),
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun KindOption(title: String, description: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ExpressiveShapes.medium,
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Picks the expense an asset was bought with, newest first, with a search once the list is long. */
@Composable
private fun PurchasePickerSheet(
    onLoadAllExpenses: suspend () -> List<Expense>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var all by remember { mutableStateOf<List<Expense>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { all = onLoadAllExpenses().sortedByDescending { it.date } }
    val shown = all.orEmpty().filter { query.isBlank() || it.description.contains(query, ignoreCase = true) }.take(100)

    SparelyBottomSheet(isOpen = true, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SparelySheetHeader(
                title = stringResource(R.string.assets_link_creator),
                subtitle = stringResource(R.string.assets_select_creator),
                icon = MaterialSymbols.RECEIPT
            )
            if ((all?.size ?: 0) > 8) {
                SparelyTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.assets_search_expenses)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when {
                    all == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    shown.isEmpty() -> Text(
                        stringResource(if (all.isNullOrEmpty()) R.string.assets_no_expenses else R.string.assets_no_matching),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    else -> shown.forEachIndexed { index, expense ->
                        SegmentedListItem(
                            title = expense.description,
                            subtitle = expense.date.format(mediumDate),
                            shape = segmentedShape(index, shown.size),
                            modifier = Modifier.segmentSpacing(index),
                            onClick = { onSelect(expense.id) },
                            trailing = {
                                Text(expense.amount.formatCurrency(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            }
                        )
                    }
                }
            }
        }
    }
}
