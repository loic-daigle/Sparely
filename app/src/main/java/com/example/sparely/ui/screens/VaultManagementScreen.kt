@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.sparely.ui.screens

import android.annotation.SuppressLint
import com.example.sparely.ui.utils.toInputString
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.example.sparely.domain.model.AccountType
import com.example.sparely.ui.components.SparelyAmountTextField
import com.example.sparely.ui.components.SheetSectionLabel
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.FilledTonalIconButton
import com.example.sparely.ui.components.segmentSpacing
import com.example.sparely.ui.components.segmentedShape
import com.example.sparely.ui.components.ExpressiveListHeader
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.sparely.ui.theme.ExpressiveShapes
import com.example.sparely.ui.theme.pill
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.theme.warning
import com.example.sparely.ui.components.ExpressiveSectionHeader
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.sparely.app.R
import com.example.sparely.domain.model.*
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.components.SingleLineText
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.components.*
import com.example.sparely.ui.theme.PoppinsFontFamily
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle
import java.util.Locale
import com.example.sparely.ui.utils.toSafeDatePickerMillis
import com.example.sparely.ui.utils.filterAmountExpressionInput
import com.example.sparely.ui.utils.filterCurrencyInput
import com.example.sparely.ui.utils.toSafeDouble
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.utils.roundToTwoDecimals
import kotlin.math.abs
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import com.example.sparely.ui.theme.PoppinsFontFamily
import com.example.sparely.ui.theme.ExpressiveMotionTokens
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultManagementScreen(
    vaults: List<SmartVault>,
    monthlyIncome: Double = 0.0,
    recentMonthlyExpenses: Double = 0.0,
    savingsRate: Double = 0.0,
    onAddVault: (SmartVault) -> Unit,
    onUpdateVault: (SmartVault) -> Unit,
    onDeleteVault: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onManualDeposit: ((Long, Double, String?, Boolean) -> Unit)? = null,
    onManualWithdrawal: ((Long, Double, String?, Boolean) -> Unit)? = null,
    onViewHistory: ((Long) -> Unit)? = null,
    onBalanceOverride: ((Long, Double, String?) -> Unit)? = null,
    /** Figures for the on-device AI section; it hides itself when AI is off. */
    aiFacts: List<com.example.sparely.ai.Fact> = emptyList(),
    /** Money waiting to be transferred to each vault, by vault id. */
    pendingByVault: Map<Long, Double> = emptyMap(),
    onOpenTransfers: () -> Unit = {}
) {
    var vaultToDeposit by remember { mutableStateOf<SmartVault?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Vaults are pre-sorted by urgency in the ViewModel
    val sortedVaults = remember(vaults) { vaults.filter { !it.archived } }

    // Archived vaults are hidden, so they stay out of the totals too
    val totalVaultBalance = remember(sortedVaults) { sortedVaults.sumOf { it.currentBalance } }
    val totalTargetAmount = remember(sortedVaults) { sortedVaults.sumOf { it.targetAmount } }
    val monthlyPlanned = remember(sortedVaults) { sortedVaults.sumOf { it.monthlyPlanned() } }
    val pendingTotal = remember(pendingByVault, sortedVaults) {
        sortedVaults.sumOf { pendingByVault[it.id] ?: 0.0 }
    }

    // Effect to detect new completions and trigger confetti
    var completedVaultIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showConfetti by remember { mutableStateOf(false) }
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(vaults) {
        val currentlyCompleted = vaults.filter { it.targetAmount > 0 && it.currentBalance >= it.targetAmount }.map { it.id }.toSet()
        if (!isInitialized) {
            completedVaultIds = currentlyCompleted
            isInitialized = true
        } else {
            if ((currentlyCompleted - completedVaultIds).isNotEmpty()) showConfetti = true
            completedVaultIds = currentlyCompleted
        }
    }

    val listState = rememberLazyListState()
    // The FAB shows its label at the top of the list and shrinks to an icon once scrolling starts
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Box(modifier = Modifier.fillMaxSize()) { // Wrap Scaffold in Box to overlay confetti
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                expanded = fabExpanded,
                icon = {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.ADD,
                        // The label carries the meaning while it's showing
                        contentDescription = if (fabExpanded) null else stringResource(R.string.vault_management_create_desc),
                        size = 24.dp
                    )
                },
                text = { Text(stringResource(R.string.vault_management_add)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues),
            // Extra bottom padding so the last card can scroll clear of the FAB
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
        ) {
            /** A titled group of vaults drawn as one segmented stack: rounded ends, tight gaps. */
            fun LazyListScope.vaultGroup(key: String, titleRes: Int, @DrawableRes icon: Int, groupVaults: List<SmartVault>) {
                if (groupVaults.isEmpty()) return
                item(key = "header_$key") {
                    ExpressiveListHeader(title = stringResource(titleRes), icon = icon, trailing = groupVaults.size.toString())
                }
                itemsIndexed(groupVaults, key = { _, vault -> vault.id }) { index, vault ->
                    VaultRow(
                        vault = vault,
                        pending = pendingByVault[vault.id] ?: 0.0,
                        shape = segmentedShape(index, groupVaults.size),
                        modifier = Modifier.segmentSpacing(index),
                        // A vault opens on its page: history, add, withdraw, edit
                        onOpen = onViewHistory?.let { { it(vault.id) } },
                        onAdd = onManualDeposit?.let { { vaultToDeposit = vault } }
                    )
                }
            }

            if (sortedVaults.isEmpty()) {
                item { EmptyVaultsCard(onCreateVault = { showCreateDialog = true }) }
            } else {
                item(key = "overview") {
                    VaultsHero(
                        totalBalance = totalVaultBalance,
                        totalTarget = totalTargetAmount,
                        monthlyPlanned = monthlyPlanned,
                        pendingTotal = pendingTotal,
                        onOpenTransfers = onOpenTransfers
                    )
                }

                // Groups are mutually exclusive (each vault appears once): a vault in two groups
                // would reuse its item key and crash the list.
                val today = LocalDate.now()
                val completedVaults = sortedVaults.filter { it.targetAmount > 0 && it.currentBalance >= it.targetAmount }
                val openVaults = sortedVaults - completedVaults.toSet()
                // Monthly goals copy their end date into targetDate, so they aren't "due soon";
                // a missing start date means "starts now".
                val dueSoon = openVaults.filter {
                    it.monthlyNeed == null &&
                        it.targetDate?.let { date -> ChronoUnit.DAYS.between(today, date) <= 90 } == true
                }
                val inProgress = (openVaults - dueSoon.toSet()).filter {
                    (it.monthlyNeed != null && (it.startDate?.let { date -> date <= today } ?: true)) ||
                        it.monthlyPlanned() > 0.0
                }
                val later = openVaults - dueSoon.toSet() - inProgress.toSet()

                vaultGroup("due", R.string.vault_group_due_soon, MaterialSymbols.LOCAL_FIRE_DEPARTMENT, dueSoon)
                vaultGroup("progress", R.string.vault_group_in_progress, MaterialSymbols.TRENDING_UP, inProgress)
                vaultGroup("later", R.string.vault_group_later, MaterialSymbols.SCHEDULE, later)
                vaultGroup("reached", R.string.vault_group_reached, MaterialSymbols.CHECK_CIRCLE, completedVaults)

                // The assistant comes after the vaults, not between them and their summary
                item(key = "ai") {
                    val aiLanguage = com.example.sparely.ui.components.LocalOnDeviceAi.current.language
                    com.example.sparely.ui.components.OnDeviceAiSection(
                        title = stringResource(R.string.ai_vaults_title),
                        facts = aiFacts,
                        task = com.example.sparely.ai.AiTask.VAULTS.text(aiLanguage),
                        askSlot = "vaults",
                        askPlaceholder = stringResource(R.string.ai_vaults_ask_placeholder),
                        modifier = Modifier.padding(top = 20.dp)
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        SmartVaultEditorDialog(
            vault = null,
            existingVaults = vaults,
            monthlyIncome = monthlyIncome,
            onSave = { newVault ->
                onAddVault(newVault)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    vaultToDeposit?.let { vault ->
        ManualAdjustmentDialog(
            vaultName = vault.name,
            currentBalance = vault.currentBalance,
            isDeposit = true,
            defaultAffectMainAccount = vault.defaultManualDepositDeductFromMain,
            onConfirm = { amount, reason, adjustMain ->
                onManualDeposit?.invoke(vault.id, amount, reason, adjustMain)
                vaultToDeposit = null
            },
            onDismiss = { vaultToDeposit = null }
        )
    }

    if (showConfetti) {
        ConfettiExplosion(onComplete = { showConfetti = false })
    }
    }
}

/**
 * What a vault takes in a month: its monthly need, or else its automatic deposits brought to a
 * monthly amount (a year has 52 weeks, 365 days).
 */
internal fun SmartVault.monthlyPlanned(): Double = monthlyNeed?.takeIf { it > 0.0 } ?: schedules
    .filter { it.enabled && it.direction == VaultTransferDirection.MAIN_TO_VAULT }
    .sumOf { schedule ->
        val amount = schedule.amount ?: 0.0
        when (schedule.type) {
            VaultScheduleType.DAILY -> amount * 365.0 / 12.0
            VaultScheduleType.DAY_OF_MONTH -> amount
            VaultScheduleType.DAY_OF_WEEK -> amount * 52.0 / 12.0 / (schedule.weekInterval ?: 1).coerceAtLeast(1)
            VaultScheduleType.QUARTERLY -> amount / 3.0
            VaultScheduleType.SPECIFIC_DATE -> 0.0
        }
    }

/** The vault's icon, or one for its kind. */
@DrawableRes
internal fun SmartVault.displayIcon(): Int = MaterialSymbols.getIconByName(iconName) ?: when (type) {
    VaultType.EMERGENCY -> MaterialSymbols.LOCAL_FIRE_DEPARTMENT
    VaultType.INVESTMENT -> MaterialSymbols.TRENDING_UP
    VaultType.SHORT_TERM -> MaterialSymbols.ATTACH_MONEY
    VaultType.LONG_TERM -> MaterialSymbols.ROCKET_LAUNCH
    else -> MaterialSymbols.ACCOUNT_BALANCE_WALLET
}

/** Month and year in the user's language: "mars 2027", "Mar 2027". */
internal val monthYearFormatter: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "MMMyyyy"))

/** How a vault is doing, in a few words, and the color for it. */
internal data class VaultStatus(val text: String, val color: Color)

@Composable
internal fun vaultStatus(vault: SmartVault): VaultStatus {
    val colors = MaterialTheme.colorScheme
    val today = LocalDate.now()
    val remaining = (vault.targetAmount - vault.currentBalance).coerceAtLeast(0.0)
    val isMonthly = vault.monthlyNeed != null
    val daysLeft = vault.targetDate?.takeUnless { isMonthly }?.let { ChronoUnit.DAYS.between(today, it) }
    val planned = vault.monthlyPlanned()
    return when {
        vault.targetAmount > 0 && remaining <= 0.0 -> VaultStatus(stringResource(R.string.vault_status_goal_reached), colors.success)
        daysLeft != null && daysLeft < 0 -> VaultStatus(stringResource(R.string.vault_status_overdue), colors.error)
        daysLeft != null && daysLeft <= 90 ->
            VaultStatus(pluralStringResource(R.plurals.vault_status_in_days, daysLeft.toInt(), daysLeft.toInt()), colors.warning)
        isMonthly -> VaultStatus(stringResource(R.string.vault_status_monthly, vault.monthlyNeed!!.formatCurrency(decimals = 0)), colors.primary)
        planned > 0.0 -> {
            val reachedBy = today.plusMonths(kotlin.math.ceil(remaining / planned).toLong())
            VaultStatus(stringResource(R.string.vault_status_reached_by, reachedBy.format(monthYearFormatter)), colors.success)
        }
        vault.targetDate != null -> VaultStatus(stringResource(R.string.vault_status_due_on, vault.targetDate.format(monthYearFormatter)), colors.onSurfaceVariant)
        else -> VaultStatus(stringResource(R.string.vault_ed_no_deadline), colors.onSurfaceVariant)
    }
}

/** The total saved, the share of all goals it is, what's planned a month and what's on the way. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VaultsHero(
    totalBalance: Double,
    totalTarget: Double,
    monthlyPlanned: Double,
    pendingTotal: Double,
    onOpenTransfers: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val onContainer = colors.onPrimaryContainer
    val progress = if (totalTarget > 0) (totalBalance / totalTarget).toFloat().coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(
            durationMillis = ExpressiveMotionTokens.EmphasizedDurationMillis,
            easing = ExpressiveMotionTokens.EmphasizedEasing
        ),
        label = "overallProgress"
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        color = colors.primaryContainer,
        contentColor = onContainer
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.vault_hero_saved),
                        style = MaterialTheme.typography.labelLarge,
                        color = onContainer.copy(alpha = 0.8f)
                    )
                    SingleLineText(
                        text = totalBalance.formatCurrency(),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = onContainer
                    )
                    if (totalTarget > 0) {
                        Text(
                            stringResource(R.string.vault_hero_of_goals, totalTarget.formatCurrency(decimals = 0)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = onContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                if (totalTarget > 0) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(68.dp)) {
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxSize(),
                            color = colors.primary,
                            trackColor = colors.primary.copy(alpha = 0.18f),
                            strokeWidth = 7.dp,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        Text(
                            progress.toDouble().formatPercent(0),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (monthlyPlanned > 0.0 || pendingTotal > 0.005) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (monthlyPlanned > 0.0) {
                        HeroPill(
                            icon = MaterialSymbols.SCHEDULE,
                            text = stringResource(R.string.vault_hero_planned, monthlyPlanned.formatCurrency(decimals = 0))
                        )
                    }
                    if (pendingTotal > 0.005) {
                        HeroPill(
                            icon = MaterialSymbols.SWAP_HORIZ,
                            text = stringResource(R.string.vault_hero_on_the_way, pendingTotal.formatCurrency()),
                            onClick = onOpenTransfers
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HeroPill(@DrawableRes icon: Int, text: String, onClick: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MaterialSymbolIcon(icon = icon, contentDescription = null, size = 16.dp)
            Text(text, style = MaterialTheme.typography.labelLarge)
            if (onClick != null) MaterialSymbolIcon(icon = MaterialSymbols.ARROW_FORWARD, contentDescription = null, size = 16.dp)
        }
    }
    val color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    if (onClick != null) Surface(onClick = onClick, shape = CircleShape, color = color) { content() }
    else Surface(shape = CircleShape, color = color) { content() }
}

/**
 * One vault in the list: icon, name and how it's doing, a ＋ to add money, then its balance against
 * its target with a bar, what's left and what it needs or gets a month (or what's on its way).
 */
@Composable
private fun VaultRow(
    vault: SmartVault,
    pending: Double,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)?,
    onAdd: (() -> Unit)?
) {
    val colors = MaterialTheme.colorScheme
    val status = vaultStatus(vault)
    val accent = if (status.color == colors.onSurfaceVariant) colors.primary else status.color
    val progressTarget = if (vault.targetAmount > 0) (vault.currentBalance / vault.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(
            durationMillis = ExpressiveMotionTokens.EmphasizedDurationMillis,
            easing = ExpressiveMotionTokens.EmphasizedEasing
        ),
        label = "vaultProgress"
    )
    val remaining = (vault.targetAmount - vault.currentBalance).coerceAtLeast(0.0)
    val planned = vault.monthlyPlanned()
    val daysLeft = vault.targetDate?.takeUnless { vault.monthlyNeed != null }?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
    // What a dated goal still needs a month to make it in time
    val neededMonthly = daysLeft?.takeIf { it > 0 && remaining > 0 }?.let { remaining / kotlin.math.max(1.0, it / 30.44) }

    Surface(
        onClick = onOpen ?: {},
        enabled = onOpen != null,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = colors.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
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
                    MaterialSymbolIcon(icon = vault.displayIcon(), contentDescription = null, size = 22.dp, tint = accent)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        vault.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        status.text,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = status.color
                    )
                }
                onAdd?.let {
                    FilledTonalIconButton(onClick = it) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.ADD,
                            contentDescription = stringResource(R.string.vault_action_add_short),
                            size = 22.dp
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    vault.currentBalance.formatCurrency(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (vault.targetAmount > 0) {
                    Text(
                        " / " + vault.targetAmount.formatCurrency(decimals = 0),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        progress.toDouble().formatPercent(0),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
            }
            if (vault.targetAmount > 0) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(ExpressiveShapes.pill),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.18f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }
            val left = when {
                pending > 0.005 -> stringResource(R.string.vault_hero_on_the_way, pending.formatCurrency())
                remaining > 0 && vault.targetAmount > 0 -> stringResource(R.string.vault_line_left, remaining.formatCurrency(decimals = 0))
                else -> null
            }
            val right = when {
                neededMonthly != null -> stringResource(R.string.vault_line_per_month, neededMonthly.formatCurrency(decimals = 0))
                vault.monthlyNeed == null && planned > 0.0 -> stringResource(R.string.vault_line_auto, planned.formatCurrency(decimals = 0))
                else -> null
            }
            if (left != null || right != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        left.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (pending > 0.005) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    right?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}


@Composable
private fun EmptyVaultsCard(onCreateVault: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(ExpressiveShapes.large)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            MaterialSymbolIcon(
                icon = MaterialSymbols.ACCOUNT_BALANCE_WALLET,
                contentDescription = null,
                size = 36.dp,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.vault_empty_title_short),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.vault_empty_desc_detailed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        SparelyButton(
            onClick = onCreateVault,
            icon = {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.ADD,
                    contentDescription = null,
                    size = 18.dp
                )
            }
        ) {
            Text(stringResource(R.string.vault_management_add))
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("DefaultLocale")
@Composable
internal fun ManualAdjustmentDialog(
    vaultName: String,
    currentBalance: Double,
    isDeposit: Boolean,
    defaultAffectMainAccount: Boolean,
    onConfirm: (Double, String?, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var affectMainAccount by remember(defaultAffectMainAccount) { mutableStateOf(defaultAffectMainAccount) }

    val title = if (isDeposit) stringResource(R.string.vault_action_add) else stringResource(R.string.vault_action_withdraw)
    val icon = if (isDeposit) MaterialSymbols.ADD else MaterialSymbols.REMOVE
    val affectMainLabel = if (isDeposit) stringResource(R.string.vault_affect_main_deduct) else stringResource(R.string.vault_affect_main_credit)
    val affectMainHelper = if (isDeposit) {
        stringResource(R.string.vault_affect_main_deduct_helper)
    } else {
        stringResource(R.string.vault_affect_main_credit_helper)
    }
    
    val defaultDepositReason = stringResource(R.string.vault_manual_deposit_default)
    val defaultWithdrawReason = stringResource(R.string.vault_manual_withdrawal_default)

    // Smart suggestions based on context
    val suggestedAmounts = remember(currentBalance, isDeposit) {
        if (isDeposit) {
            listOf(50.0, 100.0, 250.0, 500.0)
        } else {
            listOf(
                currentBalance * 0.25,
                currentBalance * 0.5,
                currentBalance * 0.75,
                currentBalance
            ).filter { it > 0 }
        }
    }

    val amount = amountText.toSafeDouble()
    val overBalance = !isDeposit && amount != null && amount > currentBalance
    val canConfirm = amount != null && amount > 0 && !overBalance
    val confirm = {
        if (canConfirm && amount != null) {
            val finalReason = reason.trim().ifBlank { if (isDeposit) defaultDepositReason else defaultWithdrawReason }
            onConfirm(amount, finalReason, affectMainAccount)
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
                .padding(bottom = 24.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SparelySheetHeader(
                title = title,
                subtitle = "$vaultName · ${stringResource(R.string.vault_current_balance_label)} ${currentBalance.formatCurrency()}",
                icon = icon,
                iconTint = if (isDeposit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            SheetAmountField(
                value = amountText,
                onValueChange = { amountText = it.filterAmountExpressionInput() },
                label = stringResource(R.string.vault_amount_label),
                isError = overBalance,
                supportingText = if (overBalance) stringResource(R.string.vault_error_insufficient_balance) else null,
                imeAction = ImeAction.Next,
                onImeAction = {}
            )

            // Quick amount suggestions
            if (suggestedAmounts.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (suggestion in suggestedAmounts.take(4)) {
                        SparelyChip(
                            selected = amount == suggestion,
                            onClick = { amountText = suggestion.toInputString(0) },
                            label = {
                                SingleLineText(
                                    text = suggestion.formatCurrency(decimals = 0),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            SheetToggleRow(
                title = affectMainLabel,
                description = affectMainHelper,
                checked = affectMainAccount,
                onCheckedChange = { affectMainAccount = it }
            )

            SparelyTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text(stringResource(R.string.vault_note_optional)) },
                placeholder = { Text(stringResource(R.string.vault_note_placeholder)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3
            )

            SheetActions(
                confirmLabel = stringResource(if (isDeposit) R.string.vault_management_add else R.string.vault_withdraw),
                onConfirm = confirm,
                confirmEnabled = canConfirm,
                destructive = !isDeposit,
                dismissLabel = stringResource(R.string.action_cancel),
                onDismiss = onDismiss
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeleteConfirmationDialog(
    vault: SmartVault,
    onConfirm: () -> Unit,
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
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MaterialSymbolIcon(
                icon = MaterialSymbols.WARNING,
                contentDescription = null,
                size = 48.dp,
                tint = MaterialTheme.colorScheme.error
            )
            
            Text(
                text = stringResource(R.string.vault_delete_confirm_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.vault_delete_confirm_message, vault.name),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.vault_important),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        
                        if (vault.currentBalance > 0) {
                            Text(
                                text = stringResource(R.string.vault_delete_balance_return, vault.currentBalance),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        
                        Text(
                            text = stringResource(R.string.vault_undone_warning),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SmartVaultEditorDialog(
    vault: SmartVault?,
    existingVaults: List<SmartVault>,
    monthlyIncome: Double,
    onSave: (SmartVault) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val existingSchedule = remember(vault?.id) { vault?.schedules?.firstOrNull() }

    fun scheduleToFrequency(schedule: VaultSchedule?): AutoDepositFrequency {
        if (schedule == null) return AutoDepositFrequency.MONTHLY
        return when (schedule.type) {
            VaultScheduleType.DAY_OF_WEEK -> {
                val interval = schedule.weekInterval ?: 1
                if (interval >= 2) AutoDepositFrequency.BIWEEKLY else AutoDepositFrequency.WEEKLY
            }
            VaultScheduleType.DAY_OF_MONTH -> AutoDepositFrequency.MONTHLY
            else -> AutoDepositFrequency.MONTHLY
        }
    }

    var name by remember { mutableStateOf(vault?.name ?: "") }
    var iconName by remember { mutableStateOf(vault?.iconName) }
    var targetAmount by remember { mutableStateOf(vault?.targetAmount?.toInputString() ?: "") }
    var currentBalance by remember { mutableStateOf(vault?.currentBalance?.toInputString() ?: "0") }
    var monthlyNeed by remember { mutableStateOf(vault?.monthlyNeed?.toString() ?: "") }
    var isFlowGoal by remember { mutableStateOf(vault?.monthlyNeed != null) }
    var startDate by remember { mutableStateOf(vault?.startDate) }
    var endDate by remember { mutableStateOf(vault?.endDate) }
    var priority by remember { mutableStateOf(vault?.priority ?: VaultPriority.MEDIUM) }
    var type by remember { mutableStateOf(vault?.type ?: VaultType.GOAL) }
    var targetDate by remember { mutableStateOf(vault?.targetDate) }
    var neverExpires by remember { mutableStateOf(vault != null && vault.targetDate == null && vault.endDate == null) }
    var accountNotes by remember { mutableStateOf(vault?.accountNotes ?: "") }
    var priorityWeight by remember { mutableStateOf(vault?.priorityWeight?.toString() ?: "1.0") }
    var excludedFromAutoAllocation by remember { mutableStateOf(!(vault?.allowAutoIncome ?: true)) }
    var defaultManualDepositDeductFromMain by remember(vault?.id) { mutableStateOf(vault?.defaultManualDepositDeductFromMain ?: true) }
    var defaultManualWithdrawalCreditMain by remember(vault?.id) { mutableStateOf(vault?.defaultManualWithdrawalCreditMain ?: true) }
    var accountType by remember(vault?.id) { mutableStateOf(vault?.accountType) }
    var accountNumber by remember(vault?.id) { mutableStateOf(vault?.accountNumber.orEmpty()) }
    var showAutoOptions by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }

    // Auto-schedule editing (supports single primary schedule for now)
    var autoDepositEnabled by remember(vault?.id) { mutableStateOf(existingSchedule != null) }
    var autoDepositAmount by remember(vault?.id) {
        mutableStateOf(
            existingSchedule?.amount
                ?.takeIf { it > 0.0 }
                ?.let { it.toString() }
                ?: ""
        )
    }

    var autoDepositFrequency by remember(vault?.id) { mutableStateOf(scheduleToFrequency(existingSchedule)) }
    var autoDepositNextRunDate by remember(vault?.id) {
        mutableStateOf(
            existingSchedule?.nextRunAt?.toLocalDate()
                ?: existingSchedule?.lastRunAt?.toLocalDate()
                ?: LocalDate.now().plusDays(1)
        )
    }
    var autoDepositNextRunTime by remember(vault?.id) {
        mutableStateOf(
            existingSchedule?.nextRunAt?.toLocalTime()
                ?: LocalTime.of(9, 0)
        )
    }
    var autoDepositOnlyIfBalanceAvailable by remember(vault?.id) { mutableStateOf(existingSchedule?.onlyIfBalanceAvailable ?: true) }
    var autoDepositNotifyBefore by remember(vault?.id) { mutableStateOf(existingSchedule?.notifyBefore ?: false) }
    var autoDepositNotifyAfter by remember(vault?.id) { mutableStateOf(existingSchedule?.notifyAfter ?: true) }
    var autoDepositNotifyOnFailure by remember(vault?.id) { mutableStateOf(existingSchedule?.notifyOnFailure ?: true) }
    var showScheduleDatePicker by remember { mutableStateOf(false) }
    var autoDepositTimeMenuExpanded by remember { mutableStateOf(false) }

    val timeOptions = remember(existingSchedule?.nextRunAt) {
        val defaults = listOf(6, 8, 9, 12, 15, 18, 21).map { LocalTime.of(it, 0) }.toMutableList()
        val existingTime = existingSchedule?.nextRunAt?.toLocalTime()
        if (existingTime != null && defaults.none { it == existingTime }) {
            defaults.add(existingTime)
        }
        defaults.sorted()
    }
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT) }

    var priorityMenuExpanded by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var showTargetDatePicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM) }
    
    // Smart validation with helpful feedback
    val context = LocalContext.current
    val validationResult = remember(
        name,
        targetAmount,
        monthlyNeed,
        isFlowGoal,
        targetDate,
        endDate,
        autoDepositEnabled,
        autoDepositAmount,
        autoDepositNextRunDate,
        autoDepositNextRunTime
    ) {
        val nextRunCandidate = LocalDateTime.of(autoDepositNextRunDate, autoDepositNextRunTime)
        when {
            name.isBlank() -> ValidationResult(false, context.getString(R.string.vault_error_name_required))
            isFlowGoal && monthlyNeed.toSafeDouble()?.let { it <= 0 } != false -> 
                ValidationResult(false, context.getString(R.string.vault_error_monthly_need_positive))
            !isFlowGoal && targetAmount.toSafeDouble()?.let { it <= 0 } != false -> 
                ValidationResult(false, context.getString(R.string.vault_error_target_amount_positive))
            autoDepositEnabled && autoDepositAmount.toSafeDouble()?.let { it <= 0 } != false ->
                ValidationResult(false, context.getString(R.string.vault_error_auto_deposit_positive))
            autoDepositEnabled && !nextRunCandidate.isAfter(LocalDateTime.now()) ->
                ValidationResult(false, context.getString(R.string.vault_error_schedule_future))
            else -> ValidationResult(true, "")
        }
    }

    // Smart suggestions based on context
    val suggestedPriority = remember(type, isFlowGoal, targetDate) {
        when {
            type == VaultType.EMERGENCY -> VaultPriority.HIGH
            targetDate?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) <= 90 } == true -> VaultPriority.HIGH
            isFlowGoal -> VaultPriority.MEDIUM
            else -> VaultPriority.LOW
        }
    }

    // Financial insights
    val totalExistingAllocation = remember(existingVaults, monthlyIncome) {
        if (monthlyIncome > 0) {
            existingVaults.filter { it.id != vault?.id }
                .sumOf { it.monthlyNeed ?: 0.0 } / monthlyIncome * 100
        } else 0.0
    }

    if (showTargetDatePicker) {
        val initialMillis = targetDate.toSafeDatePickerMillis()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showTargetDatePicker = false },
            confirmButton = {
                SparelyTextButton(onClick = {
                    val selected = datePickerState.selectedDateMillis
                    targetDate = selected?.let { 
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() 
                    }
                    if (isFlowGoal) endDate = targetDate
                    showTargetDatePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                SparelyTextButton(onClick = { showTargetDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartPicker) {
        val initialStartMillis = startDate.toSafeDatePickerMillis()
        val startPickerState = rememberDatePickerState(initialSelectedDateMillis = initialStartMillis)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                SparelyTextButton(onClick = {
                    val selected = startPickerState.selectedDateMillis
                    startDate = selected?.let { 
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() 
                    }
                    showStartPicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                SparelyTextButton(onClick = { showStartPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = startPickerState)
        }
    }

    if (showEndPicker) {
        val initialEndMillis = endDate.toSafeDatePickerMillis()
        val endPickerState = rememberDatePickerState(initialSelectedDateMillis = initialEndMillis)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                SparelyTextButton(onClick = {
                    val selected = endPickerState.selectedDateMillis
                    endDate = selected?.let { 
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() 
                    }
                    if (isFlowGoal) targetDate = endDate
                    showEndPicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                SparelyTextButton(onClick = { showEndPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = endPickerState)
        }
    }

    if (showScheduleDatePicker) {
        val initialScheduleMillis = autoDepositNextRunDate.toSafeDatePickerMillis() ?: System.currentTimeMillis()
        val schedulePickerState = rememberDatePickerState(initialSelectedDateMillis = initialScheduleMillis)
        DatePickerDialog(
            onDismissRequest = { showScheduleDatePicker = false },
            confirmButton = {
                SparelyTextButton(onClick = {
                    val selected = schedulePickerState.selectedDateMillis
                    autoDepositNextRunDate = selected?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    } ?: autoDepositNextRunDate
                    showScheduleDatePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                SparelyTextButton(onClick = { showScheduleDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = schedulePickerState)
        }
    }

    // Builds the vault from the form; everything the form doesn't show is kept from the vault
    fun buildVault(): SmartVault {
        val monthly = monthlyNeed.toSafeDouble()
        // For a monthly goal, the target is the monthly amount times the months it runs (a year
        // when it has no end)
        val target = if (isFlowGoal && monthly != null) {
            val start = (startDate ?: LocalDate.now()).withDayOfMonth(1)
            val end = (endDate ?: start.plusMonths(11)).withDayOfMonth(1)
            monthly * max(1, ChronoUnit.MONTHS.between(start, end).toInt() + 1)
        } else {
            targetAmount.toSafeDouble() ?: 0.0
        }
        val weight = when (priority) {
            VaultPriority.CRITICAL -> 4.0
            VaultPriority.HIGH -> 3.0
            VaultPriority.MEDIUM -> 2.0
            VaultPriority.LOW -> 1.0
        }
        val preservedSchedules = (vault?.schedules ?: emptyList()).filter { it.id != existingSchedule?.id }
        val scheduleType = when (autoDepositFrequency) {
            AutoDepositFrequency.DAILY -> VaultScheduleType.DAILY
            AutoDepositFrequency.WEEKLY, AutoDepositFrequency.BIWEEKLY -> VaultScheduleType.DAY_OF_WEEK
            AutoDepositFrequency.MONTHLY -> VaultScheduleType.DAY_OF_MONTH
            AutoDepositFrequency.QUARTERLY -> VaultScheduleType.QUARTERLY
        }
        val newSchedule = if (autoDepositEnabled) {
            VaultSchedule(
                id = existingSchedule?.id ?: 0L,
                vaultId = vault?.id ?: 0L,
                type = scheduleType,
                amount = autoDepositAmount.toSafeDouble()?.takeIf { it > 0.0 },
                percentage = null,
                direction = VaultTransferDirection.MAIN_TO_VAULT,
                dayOfMonth = if (scheduleType == VaultScheduleType.DAY_OF_MONTH) autoDepositNextRunDate.dayOfMonth else null,
                dayOfWeek = if (scheduleType == VaultScheduleType.DAY_OF_WEEK) autoDepositNextRunDate.dayOfWeek.value else null,
                weekInterval = when (autoDepositFrequency) {
                    AutoDepositFrequency.BIWEEKLY -> 2
                    AutoDepositFrequency.WEEKLY -> 1
                    else -> null
                },
                onlyIfBalanceAvailable = autoDepositOnlyIfBalanceAvailable,
                notifyBefore = autoDepositNotifyBefore,
                notifyAfter = autoDepositNotifyAfter,
                notifyOnFailure = autoDepositNotifyOnFailure,
                nextRunAt = LocalDateTime.of(autoDepositNextRunDate, autoDepositNextRunTime),
                lastRunAt = existingSchedule?.lastRunAt,
                enabled = true,
                createdAt = existingSchedule?.createdAt ?: Instant.now(),
                updatedAt = Instant.now()
            )
        } else null
        val base = vault ?: SmartVault(name = "", targetAmount = 0.0)
        return base.copy(
            name = name.trim(),
            targetAmount = target,
            // The balance moves through deposits, withdrawals and "Adjust balance", not this form
            currentBalance = vault?.currentBalance ?: 0.0,
            priority = priority,
            priorityWeight = weight,
            type = type,
            allocationMode = VaultAllocationMode.DYNAMIC_AUTO,
            manualAllocationPercent = null,
            targetDate = if (neverExpires) null else if (isFlowGoal) endDate else targetDate,
            startDate = if (isFlowGoal) startDate else null,
            endDate = if (isFlowGoal && !neverExpires) endDate else null,
            monthlyNeed = if (isFlowGoal) monthly else null,
            accountType = accountType,
            accountNumber = accountNumber.trim().takeIf { it.isNotEmpty() },
            accountNotes = accountNotes.takeIf { it.isNotBlank() },
            allowAutoIncome = !excludedFromAutoAllocation,
            defaultManualDepositDeductFromMain = defaultManualDepositDeductFromMain,
            defaultManualWithdrawalCreditMain = defaultManualWithdrawalCreditMain,
            schedules = listOfNotNull(newSchedule) + preservedSchedules,
            archived = vault?.archived ?: false,
            iconName = iconName
        )
    }

    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismiss
    ) {
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
                title = if (vault == null) stringResource(R.string.vault_editor_add_title) else stringResource(R.string.vault_editor_edit_title),
                icon = MaterialSymbols.getIconByName(iconName) ?: MaterialSymbols.SAVINGS
            )

            // ---- Icon and name ----
            SheetSectionLabel(stringResource(R.string.vault_ed_icon_name))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (iconRes in editorIcons) {
                    val iconStableName = MaterialSymbols.getNameByIcon(iconRes)
                    val isSelected = (iconName == null && iconRes == MaterialSymbols.ACCOUNT_BALANCE_WALLET) || iconName == iconStableName
                    Surface(
                        onClick = { iconName = iconStableName },
                        modifier = Modifier.size(48.dp),
                        shape = ExpressiveShapes.medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            MaterialSymbolIcon(
                                icon = iconRes,
                                contentDescription = null,
                                size = 24.dp,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            SparelyTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.vault_name_label)) },
                placeholder = { Text(stringResource(R.string.vault_name_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )

            // ---- What it aims for ----
            SheetSectionLabel(stringResource(R.string.vault_ed_aim))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ExpressiveShapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AimOption(
                    title = stringResource(R.string.vault_ed_aim_amount),
                    description = stringResource(R.string.vault_ed_aim_amount_desc),
                    selected = !isFlowGoal,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (isFlowGoal) {
                            targetDate = endDate
                            endDate = null
                            monthlyNeed = ""
                        }
                        isFlowGoal = false
                    }
                )
                AimOption(
                    title = stringResource(R.string.vault_ed_aim_monthly),
                    description = stringResource(R.string.vault_ed_aim_monthly_desc),
                    selected = isFlowGoal,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!isFlowGoal) {
                            endDate = targetDate
                            targetDate = null
                        }
                        isFlowGoal = true
                    }
                )
            }
            if (isFlowGoal) {
                SparelyAmountTextField(
                    value = monthlyNeed,
                    onValueChange = { monthlyNeed = it },
                    label = { Text(stringResource(R.string.vault_monthly_need)) },
                    prefix = { Text(stringResource(R.string.currency_prefix)) },
                    modifier = Modifier.fillMaxWidth()
                )
                monthlyNeed.toSafeDouble()?.takeIf { it > 0 && monthlyIncome > 0 }?.let { amount ->
                    EditorHint(stringResource(R.string.vault_income_percent, (amount / monthlyIncome * 100).toInt()))
                }
            } else {
                SparelyAmountTextField(
                    value = targetAmount,
                    onValueChange = { targetAmount = it },
                    label = { Text(stringResource(R.string.vault_target_amount_label)) },
                    prefix = { Text(stringResource(R.string.currency_prefix)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ---- When ----
            SheetSectionLabel(stringResource(if (isFlowGoal) R.string.vault_ed_when_monthly else R.string.vault_ed_when))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isFlowGoal) {
                    DateChip(
                        text = startDate?.let { stringResource(R.string.vault_ed_starts_on, it.format(dateFormatter)) }
                            ?: stringResource(R.string.vault_ed_starts_now),
                        selected = startDate != null,
                        onClick = { showStartPicker = true }
                    )
                }
                val deadline = if (isFlowGoal) endDate else targetDate
                DateChip(
                    text = deadline?.let {
                        if (isFlowGoal) stringResource(R.string.vault_ed_until, it.format(dateFormatter)) else it.format(dateFormatter)
                    } ?: stringResource(R.string.vault_ed_pick_date),
                    selected = !neverExpires && deadline != null,
                    onClick = {
                        neverExpires = false
                        if (isFlowGoal) showEndPicker = true else showTargetDatePicker = true
                    }
                )
                DateChip(
                    text = stringResource(R.string.vault_ed_no_deadline),
                    selected = neverExpires || deadline == null,
                    showIcon = false,
                    onClick = {
                        neverExpires = true
                        endDate = null
                        targetDate = null
                    }
                )
            }
            if (!isFlowGoal && !neverExpires) targetDate?.let { date ->
                val days = ChronoUnit.DAYS.between(LocalDate.now(), date)
                val remaining = (targetAmount.toSafeDouble() ?: 0.0) - (vault?.currentBalance ?: 0.0)
                if (days > 0 && remaining > 0) {
                    EditorHint(
                        stringResource(
                            R.string.vault_ed_need_hint,
                            days.toInt(),
                            (remaining / max(1.0, days / 30.44)).formatCurrency(decimals = 0)
                        )
                    )
                }
            }

            // ---- Automatic deposit ----
            EditorCard(
                icon = MaterialSymbols.SCHEDULE,
                title = stringResource(R.string.vault_auto_deposit_schedule),
                subtitle = stringResource(R.string.vault_ed_auto_desc),
                trailing = { Switch(checked = autoDepositEnabled, onCheckedChange = { autoDepositEnabled = it }) }
            ) {
                if (autoDepositEnabled) {
                    SparelyAmountTextField(
                        value = autoDepositAmount,
                        onValueChange = { autoDepositAmount = it },
                        label = { Text(stringResource(R.string.vault_amount_label)) },
                        prefix = { Text(stringResource(R.string.currency_prefix)) },
                        modifier = Modifier.fillMaxWidth(),
                        showCalculatorKeys = false
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            AutoDepositFrequency.WEEKLY,
                            AutoDepositFrequency.BIWEEKLY,
                            AutoDepositFrequency.MONTHLY,
                            AutoDepositFrequency.QUARTERLY,
                            AutoDepositFrequency.DAILY
                        ).forEach { freq ->
                            SparelyChip(
                                selected = autoDepositFrequency == freq,
                                onClick = { autoDepositFrequency = freq },
                                label = { Text(freq.displayName()) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DateChip(
                            text = stringResource(R.string.vault_ed_first_on, autoDepositNextRunDate.format(dateFormatter)),
                            selected = false,
                            onClick = { showScheduleDatePicker = true }
                        )
                        SparelyExpressiveDropdown(
                            modifier = Modifier.weight(1f),
                            selectedOption = autoDepositNextRunTime,
                            label = stringResource(R.string.vault_run_time),
                            options = timeOptions,
                            onOptionSelected = { autoDepositNextRunTime = it },
                            optionLabel = { it.format(timeFormatter) }
                        )
                    }
                    // The four notification and safety options, summed up in one line until opened
                    val optionSummary = listOfNotNull(
                        stringResource(R.string.vault_protect_main_balance).takeIf { autoDepositOnlyIfBalanceAvailable },
                        stringResource(R.string.vault_reminder_before).takeIf { autoDepositNotifyBefore },
                        stringResource(R.string.vault_confirmation_after).takeIf { autoDepositNotifyAfter },
                        stringResource(R.string.vault_alert_failure).takeIf { autoDepositNotifyOnFailure }
                    ).joinToString(" · ").ifEmpty { stringResource(R.string.vault_ed_auto_no_options) }
                    Surface(
                        onClick = { showAutoOptions = !showAutoOptions },
                        shape = ExpressiveShapes.medium,
                        color = Color.Transparent
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                optionSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            MaterialSymbolIcon(
                                icon = if (showAutoOptions) MaterialSymbols.ARROW_DROP_UP else MaterialSymbols.ARROW_DROP_DOWN,
                                contentDescription = stringResource(R.string.vault_ed_auto_options),
                                size = 24.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (showAutoOptions) {
                        EditorSwitch(stringResource(R.string.vault_protect_main_balance), stringResource(R.string.vault_protect_main_balance_desc), autoDepositOnlyIfBalanceAvailable) { autoDepositOnlyIfBalanceAvailable = it }
                        EditorSwitch(stringResource(R.string.vault_reminder_before), stringResource(R.string.vault_reminder_before_desc), autoDepositNotifyBefore) { autoDepositNotifyBefore = it }
                        EditorSwitch(stringResource(R.string.vault_confirmation_after), stringResource(R.string.vault_heads_up_moves), autoDepositNotifyAfter) { autoDepositNotifyAfter = it }
                        EditorSwitch(stringResource(R.string.vault_alert_failure), stringResource(R.string.vault_ping_skipped), autoDepositNotifyOnFailure) { autoDepositNotifyOnFailure = it }
                    }
                }
            }

            // ---- Bank account ----
            EditorCard(
                icon = MaterialSymbols.ACCOUNT_BALANCE,
                title = stringResource(R.string.vault_ed_bank),
                subtitle = stringResource(R.string.vault_ed_bank_desc)
            ) {
                SparelyExpressiveDropdown(
                    selectedOption = accountType,
                    label = stringResource(R.string.vault_ed_account_type),
                    options = listOf<AccountType?>(null) + AccountType.entries,
                    onOptionSelected = { accountType = it },
                    optionLabel = { it?.displayName ?: stringResource(R.string.vault_ed_account_none) }
                )
                SparelyTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it.take(34) },
                    label = { Text(stringResource(R.string.vault_ed_account_number)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // ---- More options, closed until wanted ----
            Surface(
                onClick = { showMoreOptions = !showMoreOptions },
                shape = ExpressiveShapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.vault_ed_more),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.vault_ed_more_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MaterialSymbolIcon(
                        icon = if (showMoreOptions) MaterialSymbols.ARROW_DROP_UP else MaterialSymbols.ARROW_DROP_DOWN,
                        contentDescription = null,
                        size = 24.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (showMoreOptions) {
                SparelyExpressiveDropdown(
                    selectedOption = priority,
                    label = stringResource(R.string.vault_priority_label),
                    options = VaultPriority.entries,
                    onOptionSelected = { priority = it },
                    optionLabel = { it.displayName() },
                    supportingText = { vaultPriority ->
                        if (vaultPriority != suggestedPriority) {
                            stringResource(R.string.vault_suggested_priority, suggestedPriority.displayName())
                        } else null
                    }
                )
                SparelyExpressiveDropdown(
                    selectedOption = type,
                    label = stringResource(R.string.vault_type_label),
                    options = listOf(VaultType.GOAL, VaultType.EMERGENCY, VaultType.INVESTMENT, VaultType.SHORT_TERM, VaultType.LONG_TERM),
                    onOptionSelected = { type = it },
                    optionLabel = { it.displayName() }
                )
                SparelyTextField(
                    value = accountNotes,
                    onValueChange = { accountNotes = it },
                    label = { Text(stringResource(R.string.vault_account_notes_label)) },
                    placeholder = { Text(stringResource(R.string.vault_notes_placeholder_detailed)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
                EditorSwitch(stringResource(R.string.vault_deduct_deposits), stringResource(R.string.vault_deduct_deposits_desc), defaultManualDepositDeductFromMain) { defaultManualDepositDeductFromMain = it }
                EditorSwitch(stringResource(R.string.vault_credit_withdrawals), stringResource(R.string.vault_credit_withdrawals_desc), defaultManualWithdrawalCreditMain) { defaultManualWithdrawalCreditMain = it }
                EditorSwitch(stringResource(R.string.vault_exclude_auto_funding), stringResource(R.string.vault_exclude_auto_funding_desc), excludedFromAutoAllocation) { excludedFromAutoAllocation = it }
                if (monthlyIncome > 0 && totalExistingAllocation > 0) {
                    EditorHint(stringResource(R.string.vault_budget_insight_desc, totalExistingAllocation))
                }
            }

            if (vault != null && onDelete != null) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    MaterialSymbolIcon(icon = MaterialSymbols.DELETE, contentDescription = null, size = 18.dp, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.vault_delete_vault))
                }
            }
        }

        // Pinned under the form: what's missing, then save
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp)) {
            if (!validationResult.isValid && name.isNotBlank()) {
                Text(
                    validationResult.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            SheetActions(
                confirmLabel = stringResource(if (vault == null) R.string.vault_ed_create else R.string.vault_save_changes),
                onConfirm = { if (validationResult.isValid) onSave(buildVault()) },
                confirmEnabled = validationResult.isValid,
                dismissLabel = stringResource(R.string.action_cancel),
                onDismiss = onDismiss
            )
        }
        }
    }
}

private val editorIcons = listOf(
    MaterialSymbols.ACCOUNT_BALANCE_WALLET,
    MaterialSymbols.SAVINGS,
    MaterialSymbols.FLIGHT,
    MaterialSymbols.HOME,
    MaterialSymbols.DIRECTIONS_CAR,
    MaterialSymbols.SCHOOL,
    MaterialSymbols.SHOPPING_BAG,
    MaterialSymbols.PETS,
    MaterialSymbols.RESTAURANT,
    MaterialSymbols.COMPUTER,
)

/** One half of the "what it aims for" choice. */
@Composable
private fun AimOption(title: String, description: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ExpressiveShapes.medium,
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DateChip(text: String, selected: Boolean, showIcon: Boolean = true, onClick: () -> Unit) {
    SparelyChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = if (showIcon) {
            { MaterialSymbolIcon(icon = MaterialSymbols.CALENDAR_MONTH, contentDescription = null, size = 18.dp) }
        } else null
    )
}

@Composable
private fun EditorHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

/** A card grouping one part of the editor: tonal icon, title and subtitle, optional switch. */
@Composable
private fun EditorCard(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(shape = ExpressiveShapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialSymbolIcon(icon = icon, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun EditorSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}


@Composable
fun AutoDepositFrequency.displayName(): String = when (this) {
    AutoDepositFrequency.DAILY -> stringResource(R.string.vault_frequency_daily)
    AutoDepositFrequency.WEEKLY -> stringResource(R.string.vault_frequency_weekly)
    AutoDepositFrequency.BIWEEKLY -> stringResource(R.string.vault_frequency_biweekly)
    AutoDepositFrequency.MONTHLY -> stringResource(R.string.vault_frequency_monthly)
    AutoDepositFrequency.QUARTERLY -> stringResource(R.string.vault_frequency_quarterly)
}


private data class ValidationResult(
    val isValid: Boolean,
    val message: String
)
