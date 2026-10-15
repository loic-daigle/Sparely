package com.example.sparely.ui.screens

import android.annotation.SuppressLint
import com.example.sparely.ui.utils.toInputString
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
    aiFacts: List<com.example.sparely.ai.Fact> = emptyList()
) {

    var vaultToEdit by remember { mutableStateOf<SmartVault?>(null) }
    var vaultToDeposit by remember { mutableStateOf<SmartVault?>(null) }
    var vaultToWithdraw by remember { mutableStateOf<SmartVault?>(null) }
    var vaultToDelete by remember { mutableStateOf<SmartVault?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Vaults are pre-sorted by urgency in the ViewModel
    val sortedVaults = remember(vaults) {
        vaults.filter { !it.archived }
    }

    // Financial health indicators (archived vaults are hidden, so keep them out of the totals too)
    val totalVaultBalance = remember(sortedVaults) { sortedVaults.sumOf { it.currentBalance } }
    val totalTargetAmount = remember(sortedVaults) { sortedVaults.sumOf { it.targetAmount } }
    val overallProgress = remember(totalVaultBalance, totalTargetAmount) {
        if (totalTargetAmount > 0) (totalVaultBalance / totalTargetAmount * 100).toInt() else 0
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
            val newCompletions = currentlyCompleted - completedVaultIds
            if (newCompletions.isNotEmpty()) {
                showConfetti = true
            }
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
            fun LazyListScope.vaultGroup(
                key: String,
                titleRes: Int,
                subtitle: @Composable () -> String,
                @DrawableRes icon: Int,
                groupVaults: List<SmartVault>
            ) {
                item(key = "header_$key") {
                    VaultGroupHeader(title = stringResource(titleRes), subtitle = subtitle(), icon = icon)
                }
                itemsIndexed(groupVaults, key = { _, vault -> vault.id }) { index, vault ->
                    val isFirst = index == 0
                    val isLast = index == groupVaults.lastIndex
                    EnhancedVaultCard(
                        vault = vault,
                        shape = RoundedCornerShape(
                            topStart = if (isFirst) 28.dp else 6.dp,
                            topEnd = if (isFirst) 28.dp else 6.dp,
                            bottomStart = if (isLast) 28.dp else 6.dp,
                            bottomEnd = if (isLast) 28.dp else 6.dp
                        ),
                        modifier = Modifier.padding(top = if (isFirst) 0.dp else 2.dp),
                        onEdit = { vaultToEdit = vault },
                        onDelete = { vaultToDelete = vault },
                        onDeposit = onManualDeposit?.let { { vaultToDeposit = vault } },
                        onWithdraw = onManualWithdrawal?.let { { vaultToWithdraw = vault } },
                        onViewHistory = onViewHistory?.let { { it(vault.id) } }
                    )
                }
            }

            // Overall progress card
            if (sortedVaults.isNotEmpty()) {
                item(key = "overview") {
                    OverallProgressCard(
                        totalBalance = totalVaultBalance,
                        totalTarget = totalTargetAmount,
                        overallProgress = overallProgress,
                        vaultCount = sortedVaults.size,
                        monthlyIncome = monthlyIncome,
                        recentExpenses = recentMonthlyExpenses,
                        savingsRate = savingsRate
                    )
                }
                item(key = "ai") {
                    val aiLanguage = com.example.sparely.ui.components.LocalOnDeviceAi.current.language
                    com.example.sparely.ui.components.OnDeviceAiSection(
                        title = stringResource(R.string.ai_vaults_title),
                        facts = aiFacts,
                        task = com.example.sparely.ai.AiTask.VAULTS.text(aiLanguage),
                        askSlot = "vaults",
                        askPlaceholder = stringResource(R.string.ai_vaults_ask_placeholder),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            if (sortedVaults.isEmpty()) {
                item {
                    EmptyVaultsCard(onCreateVault = { showCreateDialog = true })
                }
            } else {
                // Group vaults for better organization. Groups are mutually exclusive (each vault
                // appears once): a vault in two groups would reuse its item key and crash the list.
                val today = LocalDate.now()
                val completedVaults = sortedVaults.filter { it.targetAmount > 0 && it.currentBalance >= it.targetAmount }
                val openVaults = sortedVaults - completedVaults.toSet()
                // Flow goals (recurring monthly need) copy their end date into targetDate, so they
                // are excluded from the urgent group; a missing start date means "starts now".
                val urgentVaults = openVaults.filter {
                    it.monthlyNeed == null &&
                        it.targetDate?.let { date -> ChronoUnit.DAYS.between(today, date) <= 90 } == true
                }
                val activeVaults = (openVaults - urgentVaults.toSet()).filter {
                    it.monthlyNeed != null && (it.startDate?.let { date -> date <= today } ?: true)
                }
                val plannedVaults = openVaults - urgentVaults.toSet() - activeVaults.toSet()

                if (urgentVaults.isNotEmpty()) {
                    vaultGroup(
                        key = "urgent",
                        titleRes = R.string.vault_urgent_goals,
                        subtitle = { stringResource(R.string.vault_urgent_goals_count, urgentVaults.size) },
                        icon = MaterialSymbols.LOCAL_FIRE_DEPARTMENT,
                        groupVaults = urgentVaults
                    )
                }

                if (activeVaults.isNotEmpty()) {
                    vaultGroup(
                        key = "active",
                        titleRes = R.string.vault_active_flow_goals,
                        subtitle = { stringResource(R.string.vault_active_flow_goals_count, activeVaults.size) },
                        icon = MaterialSymbols.TRENDING_UP,
                        groupVaults = activeVaults
                    )
                }

                if (plannedVaults.isNotEmpty()) {
                    vaultGroup(
                        key = "planned",
                        titleRes = R.string.vault_planned_goals,
                        subtitle = { stringResource(R.string.vault_planned_goals_count, plannedVaults.size) },
                        icon = MaterialSymbols.ACCOUNT_BALANCE_WALLET,
                        groupVaults = plannedVaults
                    )
                }

                if (completedVaults.isNotEmpty()) {
                    vaultGroup(
                        key = "completed",
                        titleRes = R.string.vault_completed_goals,
                        subtitle = { pluralStringResource(R.plurals.vault_completed_goals_count, completedVaults.size, completedVaults.size) },
                        icon = MaterialSymbols.CHECK_CIRCLE,
                        groupVaults = completedVaults
                    )
                }
            }
        }
    }

    // Dialogs
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

    vaultToEdit?.let { vault ->
        val balanceEditedNote = stringResource(R.string.vault_balance_edited_note)
        SmartVaultEditorDialog(
            vault = vault,
            existingVaults = vaults,
            monthlyIncome = monthlyIncome,
            onSave = { updatedVault ->
                // Detect if balance changed and record it in history
                val balanceDelta = updatedVault.currentBalance - vault.currentBalance
                if (balanceDelta != 0.0 && onBalanceOverride != null) {
                    onBalanceOverride(vault.id, updatedVault.currentBalance, balanceEditedNote)
                }
                onUpdateVault(updatedVault)
                vaultToEdit = null

            },
            onDelete = {
                vaultToDelete = vault
                vaultToEdit = null
            },
            onDismiss = { vaultToEdit = null }
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

    vaultToWithdraw?.let { vault: SmartVault ->
        ManualAdjustmentDialog(
            vaultName = vault.name,
            currentBalance = vault.currentBalance,
            isDeposit = false,
            defaultAffectMainAccount = vault.defaultManualWithdrawalCreditMain,
            onConfirm = { amount, reason, creditMain ->
                onManualWithdrawal?.invoke(vault.id, amount, reason, creditMain)
                vaultToWithdraw = null
            },
            onDismiss = { vaultToWithdraw = null }
        )
    }

    vaultToDelete?.let { vault: SmartVault ->
        DeleteConfirmationDialog(
            vault = vault,
            onConfirm = {
                onDeleteVault(vault.id)
                vaultToDelete = null
            },
            onDismiss = { vaultToDelete = null }
        )
    }

    if (showConfetti) {
        ConfettiExplosion(onComplete = { showConfetti = false })
    }
    }
    }


@Composable
private fun OverallProgressCard(
    totalBalance: Double,
    totalTarget: Double,
    overallProgress: Int,
    vaultCount: Int,
    monthlyIncome: Double,
    recentExpenses: Double,
    savingsRate: Double
) {
    val colors = MaterialTheme.colorScheme
    val onContainer = colors.onPrimaryContainer
    val animatedProgress by animateFloatAsState(
        targetValue = (overallProgress / 100f).coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = ExpressiveMotionTokens.EmphasizedDurationMillis,
            easing = ExpressiveMotionTokens.EmphasizedEasing
        ),
        label = "overallProgress"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = ExpressiveShapes.large,
        color = colors.primaryContainer,
        contentColor = onContainer
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.vault_label_total_saved),
                        style = MaterialTheme.typography.labelLarge,
                        color = onContainer.copy(alpha = 0.8f)
                    )
                    SingleLineText(
                        text = totalBalance.formatCurrency(),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = onContainer
                    )
                    Text(
                        // The string already carries the currency sign
                        text = stringResource(R.string.vault_label_of_target, totalTarget.formatCurrency("")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onContainer.copy(alpha = 0.8f)
                    )
                }
                // Overall progress as a ring with the percentage inside
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = colors.primary,
                        trackColor = colors.primary.copy(alpha = 0.18f),
                        strokeWidth = 7.dp,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Text(
                        text = "$overallProgress%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onContainer
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VaultStatTile(
                    label = stringResource(R.string.vault_label_active_vaults_count, vaultCount),
                    value = null,
                    icon = MaterialSymbols.SAVINGS,
                    isHealthy = null,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                // Financial health indicators need an income to compare against
                if (monthlyIncome > 0) {
                    val displaySavingsRate = (savingsRate * 100).toInt()
                    VaultStatTile(
                        label = stringResource(R.string.vault_health_savings_rate),
                        value = "$displaySavingsRate%",
                        icon = null,
                        isHealthy = displaySavingsRate >= 20,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    VaultStatTile(
                        label = stringResource(R.string.vault_health_monthly_spending),
                        value = recentExpenses.formatCurrency(decimals = 0),
                        icon = null,
                        isHealthy = recentExpenses < monthlyIncome * 0.5,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

/**
 * Small tile on the overview card. With an [isHealthy] verdict it shows a check or warning next to
 * the label; without a [value] the label itself is the content.
 */
@Composable
private fun VaultStatTile(
    label: String,
    value: String?,
    @DrawableRes icon: Int?,
    isHealthy: Boolean?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = ExpressiveShapes.medium,
        color = colors.surface.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val statusIcon = when (isHealthy) {
                true -> MaterialSymbols.CHECK_CIRCLE
                false -> MaterialSymbols.WARNING
                null -> icon
            }
            statusIcon?.let {
                MaterialSymbolIcon(
                    icon = it,
                    contentDescription = null,
                    size = 18.dp,
                    tint = when (isHealthy) {
                        true -> colors.success
                        false -> colors.warning
                        null -> colors.primary
                    }
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            value?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Heading above a group of vaults: tonal icon, title and how many vaults the group holds. */
@Composable
private fun VaultGroupHeader(
    title: String,
    subtitle: String,
    @DrawableRes icon: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            MaterialSymbolIcon(
                icon = icon,
                contentDescription = null,
                size = 18.dp,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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


/**
 * One vault: icon, name and status, then the balance against its target with a progress bar, then
 * quick actions. [shape] lets a group's vaults join into one segmented stack.
 */
@Composable
private fun EnhancedVaultCard(
    vault: SmartVault,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onDeposit: (() -> Unit)?,
    onWithdraw: (() -> Unit)?,
    onViewHistory: (() -> Unit)?,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = ExpressiveShapes.large
) {
    val colorScheme = MaterialTheme.colorScheme
    val progressTarget = if (vault.targetAmount > 0) (vault.currentBalance / vault.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(
            durationMillis = ExpressiveMotionTokens.EmphasizedDurationMillis,
            easing = ExpressiveMotionTokens.EmphasizedEasing
        ),
        label = "Progress Animation"
    )

    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }

    // Calculate financial insights
    val isFlowGoal = vault.monthlyNeed != null
    val daysUntilTarget = vault.targetDate?.takeUnless { isFlowGoal }
        ?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
    val isOverdue = daysUntilTarget != null && daysUntilTarget < 0
    val isUrgent = daysUntilTarget != null && daysUntilTarget in 0..90
    val remaining = (vault.targetAmount - vault.currentBalance).coerceAtLeast(0.0)

    // Projected Completion Logic - calculates estimated date to reach goal
    val projectedCompletionDate = remember(vault.currentBalance, vault.monthlyNeed, vault.targetAmount, vault.schedules) {
        if (remaining <= 0) null
        else {
            // Calculate effective monthly contribution from all sources
            val monthlyContribution = if (vault.monthlyNeed != null && vault.monthlyNeed > 0) {
                 vault.monthlyNeed
            } else {
                 vault.schedules
                    .filter { it.enabled && it.direction == VaultTransferDirection.MAIN_TO_VAULT }
                    .sumOf { schedule ->
                        val amount = schedule.amount ?: 0.0
                        when (schedule.type) {
                            // Daily: multiply by average days per month
                            VaultScheduleType.DAILY -> amount * 30.44
                            // Monthly: happens once per month
                            VaultScheduleType.DAY_OF_MONTH -> amount
                            // Weekly: approximately 4.33 weeks per month
                            VaultScheduleType.DAY_OF_WEEK -> {
                                val interval = schedule.weekInterval ?: 1
                                amount * (4.33 / interval)
                            }
                            // Quarterly: divide by 3 to get monthly equivalent
                            VaultScheduleType.QUARTERLY -> amount / 3.0
                            // Specific date: one-time contribution, treated as 0 for ongoing projection
                            VaultScheduleType.SPECIFIC_DATE -> 0.0
                        }
                    }
            }

            if (monthlyContribution > 0) {
                val monthsNeeded = kotlin.math.ceil(remaining / monthlyContribution).toLong()
                LocalDate.now().plusMonths(monthsNeeded)
            } else null
        }
    }


    // Smart status indicator
    val statusColor = when {
        progressTarget >= 1.0f -> colorScheme.success // Completed
        isOverdue -> MaterialTheme.colorScheme.error // Overdue
        isUrgent -> colorScheme.warning // Urgent
        else -> colorScheme.primary
    }

    val statusText = when {
        progressTarget >= 1.0f -> stringResource(R.string.vault_status_goal_reached)
        isOverdue -> stringResource(R.string.vault_status_overdue)
        isUrgent -> stringResource(R.string.vault_status_urgent, daysUntilTarget ?: 0)
        isFlowGoal && (vault.startDate?.let { it <= LocalDate.now() } ?: true) -> stringResource(R.string.vault_status_active_flow)
        else -> stringResource(R.string.vault_status_in_progress)
    }

    val statusIcon = when {
        progressTarget >= 1.0f -> MaterialSymbols.CHECK
        isOverdue -> MaterialSymbols.WARNING
        isUrgent -> MaterialSymbols.WARNING
        else -> null
    }

    val displayIcon = MaterialSymbols.getIconByName(vault.iconName) ?: when (vault.type) {
        VaultType.EMERGENCY -> MaterialSymbols.LOCAL_FIRE_DEPARTMENT
        VaultType.INVESTMENT -> MaterialSymbols.TRENDING_UP
        VaultType.SHORT_TERM -> MaterialSymbols.ATTACH_MONEY
        VaultType.LONG_TERM -> MaterialSymbols.ROCKET_LAUNCH
        else -> MaterialSymbols.ACCOUNT_BALANCE_WALLET
    }

    Surface(
        onClick = onEdit ?: {},
        enabled = onEdit != null,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: icon, name and status, edit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(ExpressiveShapes.small)
                        .background(statusColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialSymbolIcon(
                        icon = displayIcon,
                        contentDescription = null,
                        size = 22.dp,
                        tint = statusColor
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vault.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        statusIcon?.let { icon ->
                            MaterialSymbolIcon(
                                icon = icon,
                                contentDescription = null,
                                size = 14.dp,
                                tint = statusColor
                            )
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                onEdit?.let {
                    IconButton(onClick = it) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.EDIT,
                            contentDescription = stringResource(R.string.vault_management_edit_desc),
                            size = 20.dp,
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Balance against target, then the bar
            Column(
                modifier = Modifier.padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = vault.currentBalance.formatCurrency(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    if (remaining > 0) {
                        Text(
                            text = " / " + vault.targetAmount.formatCurrency(decimals = 0),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = progress.toDouble().formatPercent(0),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(ExpressiveShapes.pill),
                    color = statusColor,
                    trackColor = statusColor.copy(alpha = 0.18f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                // Remaining on the left, the forecast or due date on the right
                if (remaining > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.vault_label_remaining_to_go, remaining.formatCurrency()),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (projectedCompletionDate != null) {
                            Text(
                                text = stringResource(R.string.vault_label_on_track, projectedCompletionDate.format(dateFormatter)),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.success,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (vault.targetDate != null) {
                            Text(
                                text = stringResource(R.string.vault_label_due_date, vault.targetDate.format(dateFormatter)),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick actions
            if (onDeposit != null || onWithdraw != null || onViewHistory != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    onDeposit?.let {
                        VaultActionButton(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.vault_action_add_short),
                            icon = MaterialSymbols.ADD,
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary,
                            onClick = it
                        )
                    }
                    onWithdraw?.let {
                        VaultActionButton(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.vault_action_withdraw_short),
                            icon = MaterialSymbols.REMOVE,
                            containerColor = colorScheme.surfaceContainerHighest,
                            contentColor = colorScheme.onSurface,
                            onClick = it
                        )
                    }
                    onViewHistory?.let {
                        VaultActionButton(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.vault_action_history_short),
                            icon = MaterialSymbols.HISTORY,
                            containerColor = colorScheme.surfaceContainerHighest,
                            contentColor = colorScheme.onSurface,
                            onClick = it
                        )
                    }
                }
            }
        }
    }
}

/** Compact pill button for a vault's quick actions. */
@Composable
private fun VaultActionButton(
    modifier: Modifier = Modifier,
    label: String,
    @DrawableRes icon: Int,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MaterialSymbolIcon(icon = icon, contentDescription = null, size = 18.dp, tint = contentColor)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
private fun VaultBadgeChip(
    modifier: Modifier = Modifier,
    label: String,
    @DrawableRes icon: Int,
    tint: Color
) {
    Surface(
        modifier = modifier,
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MaterialSymbolIcon(
                icon = icon,
                contentDescription = null,
                size = 14.dp,
                tint = tint
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("DefaultLocale")
@Composable
private fun ManualAdjustmentDialog(
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
                onValueChange = { amountText = it.filterCurrencyInput() },
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
private fun DeleteConfirmationDialog(
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
private fun SmartVaultEditorDialog(
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
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }

    var priorityMenuExpanded by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var showTargetDatePicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    
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

    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SparelySheetHeader(
                title = if (vault == null) stringResource(R.string.vault_editor_add_title) else stringResource(R.string.vault_editor_edit_title),
                subtitle = stringResource(R.string.vault_allocation_desc),
                icon = MaterialSymbols.SAVINGS
            )

            // Goal type selector (prominent position)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.vault_goal_type),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GoalTypeCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.vault_fixed_goal),
                        description = stringResource(R.string.vault_fixed_goal_desc),
                        icon = MaterialSymbols.FLAG,
                        isSelected = !isFlowGoal,
                        onClick = {
                            if (isFlowGoal) {
                                targetDate = endDate
                                endDate = null
                                monthlyNeed = ""
                            }
                            isFlowGoal = false
                        }
                    )
                    GoalTypeCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.vault_flow_goal),
                        description = stringResource(R.string.vault_flow_goal_desc),
                        icon = MaterialSymbols.REFRESH,
                        isSelected = isFlowGoal,
                        onClick = {
                            if (!isFlowGoal) {
                                endDate = targetDate
                                targetDate = null
                            }
                            isFlowGoal = true
                        }
                    )
                }
            }

            val icons = listOf(
                MaterialSymbols.ACCOUNT_BALANCE_WALLET,
                MaterialSymbols.SAVINGS,
                MaterialSymbols.DIRECTIONS_CAR,
                MaterialSymbols.HOME,
                MaterialSymbols.FLIGHT,
                MaterialSymbols.SCHOOL,
                MaterialSymbols.SHOPPING_BAG,
                MaterialSymbols.PETS,
                MaterialSymbols.RESTAURANT,
                MaterialSymbols.COMPUTER,
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.vault_management_vault_icon_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier.fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (iconRes in icons) {
                        val iconStableName = MaterialSymbols.getNameByIcon(iconRes)
                        val isSelected = (iconName == null && iconRes == MaterialSymbols.ACCOUNT_BALANCE_WALLET) || (iconName == iconStableName)
                        Surface(
                            modifier = Modifier.size(40.dp).clickable { iconName = iconStableName },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
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
            }

            SparelyTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.vault_name_label)) },
                placeholder = { Text(stringResource(R.string.vault_name_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    val displayIcon = MaterialSymbols.getIconByName(iconName) ?: MaterialSymbols.ACCOUNT_BALANCE_WALLET
                    MaterialSymbolIcon(
                        icon = displayIcon,
                        contentDescription = null,
                        size = 20.dp
                    )
                }
            )

            // Different fields based on goal type
            if (isFlowGoal) {
                SparelyTextField(
                    value = monthlyNeed,
                    onValueChange = { monthlyNeed = it.filterCurrencyInput() },
                    label = { Text(stringResource(R.string.vault_monthly_need)) },
                    prefix = { Text(stringResource(R.string.currency_prefix)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = {
                        val amount = monthlyNeed.toSafeDouble()
                        if (amount != null && monthlyIncome > 0) {
                            val percent = (amount / monthlyIncome * 100).toInt()
                            Text(stringResource(R.string.vault_income_percent, percent))
                        }
                    }
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SparelyTonalButton(
                        onClick = { showStartPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        icon = {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.CALENDAR_MONTH,
                                contentDescription = null,
                                size = 18.dp
                            )
                        }
                    ) {
                        Text(
                            text = startDate?.format(dateFormatter) ?: stringResource(R.string.vault_set_start_date)
                        )
                    }
                    if (startDate != null) {
                        SparelyTextButton(
                            onClick = { startDate = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.vault_clear_start_date))
                        }
                    }
                }

                NeverExpiresRow(
                    checked = neverExpires,
                    onCheckedChange = {
                        neverExpires = it
                        if (it) {
                            endDate = null
                            targetDate = null
                        }
                    }
                )

                if (!neverExpires) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SparelyTonalButton(
                        onClick = { showEndPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        icon = {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.CALENDAR_MONTH,
                                contentDescription = null,
                                size = 18.dp
                            )
                        }
                    ) {
                        Text(
                            text = endDate?.format(dateFormatter) ?: stringResource(R.string.vault_set_end_date)
                        )
                    }
                    if (endDate != null) {
                        SparelyTextButton(
                            onClick = { endDate = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.vault_clear_end_date))
                        }
                    }
                }

                // Allow editing current balance for existing flow vaults
                if (vault != null) {
                    SparelyTextField(
                        value = currentBalance,
                        onValueChange = { currentBalance = it.filterCurrencyInput() },
                        label = { Text(stringResource(R.string.vault_current_balance_label)) },
                        prefix = { Text(stringResource(R.string.currency_prefix)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = {
                            Text(
                                text = stringResource(R.string.vault_balance_edit_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }

            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SparelyTextField(
                        value = targetAmount,
                        onValueChange = { targetAmount = it.filterCurrencyInput() },
                        label = { Text(stringResource(R.string.vault_target_amount_label)) },
                        prefix = { Text(stringResource(R.string.currency_prefix)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    if (vault != null) {
                        SparelyTextField(
                            value = currentBalance,
                            onValueChange = { currentBalance = it.filterCurrencyInput() },
                            label = { Text(stringResource(R.string.vault_current_balance_label)) },
                            prefix = { Text(stringResource(R.string.currency_prefix)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }

                NeverExpiresRow(
                    checked = neverExpires,
                    onCheckedChange = {
                        neverExpires = it
                        if (it) {
                            endDate = null
                            targetDate = null
                        }
                    }
                )

                if (!neverExpires) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SparelyTonalButton(
                        onClick = { showTargetDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        icon = {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.CALENDAR_MONTH,
                                contentDescription = null,
                                size = 18.dp
                            )
                        }
                    ) {
                        Text(
                            text = targetDate?.format(dateFormatter) ?: stringResource(R.string.vault_set_deadline)
                        )
                    }
                    if (targetDate != null) {
                        val daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), targetDate)
                        val monthsUntil = ChronoUnit.MONTHS.between(LocalDate.now(), targetDate)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.vault_days_until_deadline, daysUntil, monthsUntil),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            SparelyTextButton(onClick = { targetDate = null }) {
                                Text(stringResource(R.string.action_clear))
                            }
                        }
                    }
                }
            }

            SparelyExpressiveDropdown(
                selectedOption = type,
                label = stringResource(R.string.vault_type_label),
                options = listOf(VaultType.GOAL, VaultType.EMERGENCY, VaultType.INVESTMENT, VaultType.SHORT_TERM, VaultType.LONG_TERM),
                onOptionSelected = { type = it },
                optionLabel = { it.displayName() },
                optionIcon = { vaultType ->
                    when (vaultType) {
                        VaultType.EMERGENCY -> MaterialSymbols.LOCAL_FIRE_DEPARTMENT
                        VaultType.INVESTMENT -> MaterialSymbols.TRENDING_UP
                        VaultType.GOAL -> MaterialSymbols.FLAG
                        VaultType.SHORT_TERM -> MaterialSymbols.ATTACH_MONEY
                        VaultType.LONG_TERM -> MaterialSymbols.ROCKET_LAUNCH
                        else -> MaterialSymbols.ACCOUNT_BALANCE
                    }
                }
            )

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
                },
                trailingContent = { vaultPriority ->
                    if (vaultPriority == suggestedPriority) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.vault_suggested_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            )

            // Financial insights
            if (monthlyIncome > 0 && totalExistingAllocation > 0) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.vault_budget_insight_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = stringResource(R.string.vault_budget_insight_desc, totalExistingAllocation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SparelyTextField(
                value = accountNotes,
                onValueChange = { accountNotes = it },
                label = { Text(stringResource(R.string.vault_account_notes_label)) },
                placeholder = { Text(stringResource(R.string.vault_notes_placeholder_detailed)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.vault_manual_transfer_defaults),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.vault_manual_transfer_defaults_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.vault_deduct_deposits), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(R.string.vault_deduct_deposits_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = defaultManualDepositDeductFromMain,
                        onCheckedChange = { defaultManualDepositDeductFromMain = it }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.vault_credit_withdrawals), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(R.string.vault_credit_withdrawals_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = defaultManualWithdrawalCreditMain,
                        onCheckedChange = { defaultManualWithdrawalCreditMain = it }
                    )
                }
            }

            // Exclude from automatic allocation toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.vault_exclude_auto_funding), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = stringResource(R.string.vault_exclude_auto_funding_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = excludedFromAutoAllocation, onCheckedChange = { excludedFromAutoAllocation = it })
            }

            // Validation feedback
            if (!validationResult.isValid) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.WARNING,
                            contentDescription = null,
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = validationResult.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Auto-deposit editor (small, focused)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(R.string.vault_auto_deposit_schedule), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = stringResource(R.string.vault_auto_deposit_schedule_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoDepositEnabled, onCheckedChange = { autoDepositEnabled = it })
                }

                if (autoDepositEnabled) {
                    SparelyTextField(
                        value = autoDepositAmount,
                        onValueChange = { autoDepositAmount = it.filterCurrencyInput() },
                        label = { Text(stringResource(R.string.vault_amount_label)) },
                        prefix = { Text(stringResource(R.string.currency_prefix)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Frequency chips - using FlowRow for wrapping
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val frequencies = listOf(
                            AutoDepositFrequency.DAILY,
                            AutoDepositFrequency.WEEKLY,
                            AutoDepositFrequency.BIWEEKLY,
                            AutoDepositFrequency.MONTHLY,
                            AutoDepositFrequency.QUARTERLY
                        )
                        frequencies.forEach { freq ->
                            SparelyChip(
                                selected = autoDepositFrequency == freq,
                                onClick = { autoDepositFrequency = freq },
                                label = { Text(freq.displayName()) }
                            )
                        }
                    }


                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SparelyTonalButton(
                            onClick = { showScheduleDatePicker = true },
                            modifier = Modifier.weight(1f),
                            icon = {
                                MaterialSymbolIcon(
                                    icon = MaterialSymbols.CALENDAR_MONTH,
                                    contentDescription = null,
                                    size = 18.dp
                                )
                            }
                        ) {
                            Text(autoDepositNextRunDate.format(dateFormatter))
                        }

                        SparelyExpressiveDropdown(
                            modifier = Modifier.weight(1f),
                            selectedOption = autoDepositNextRunTime,
                            label = stringResource(R.string.vault_run_time),
                            options = timeOptions,
                            onOptionSelected = { autoDepositNextRunTime = it },
                            optionLabel = { it.format(timeFormatter) }
                        )
                    }

                    Text(
                        text = stringResource(R.string.vault_next_run, autoDepositNextRunDate.format(dateFormatter), autoDepositNextRunTime.format(timeFormatter)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.vault_protect_main_balance), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = stringResource(R.string.vault_protect_main_balance_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoDepositOnlyIfBalanceAvailable,
                            onCheckedChange = { autoDepositOnlyIfBalanceAvailable = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.vault_reminder_before), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = stringResource(R.string.vault_reminder_before_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoDepositNotifyBefore,
                            onCheckedChange = { autoDepositNotifyBefore = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.vault_confirmation_after), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = stringResource(R.string.vault_heads_up_moves),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoDepositNotifyAfter,
                            onCheckedChange = { autoDepositNotifyAfter = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.vault_alert_failure), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = stringResource(R.string.vault_ping_skipped),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoDepositNotifyOnFailure,
                            onCheckedChange = { autoDepositNotifyOnFailure = it }
                        )
                    }
                }
            }

            HorizontalDivider()

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Delete button (only for existing vaults)
                if (vault != null && onDelete != null) {
                    SparelyTextButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        contentColor = MaterialTheme.colorScheme.error,
                        icon = {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.DELETE,
                                contentDescription = null,
                                size = 18.dp,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    ) {
                        Text(stringResource(R.string.vault_delete_vault))
                    }
                }
                
                // Button row - using FlowRow for narrow screen support
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 2
                ) {
                    SparelyTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).widthIn(min = 100.dp)
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }


                    SparelyButton(
                        onClick = {
                            val balance = if (vault != null) currentBalance.toSafeDouble() ?: 0.0 else 0.0
                            val monthly = monthlyNeed.toSafeDouble()
                            // For flow goals, compute a sensible target: monthly need * number of months
                            // Determine the months span (inclusive). Use startDate if available, otherwise today.
                            val target = if (isFlowGoal && monthly != null) {
                                val start = (startDate ?: LocalDate.now()).withDayOfMonth(1)
                                // If endDate provided, use it; otherwise default to a 12-month window starting at start
                                val end = (endDate ?: start.plusMonths(11)).withDayOfMonth(1)
                                var months = ChronoUnit.MONTHS.between(start, end).toInt() + 1
                                months = max(1, months)
                                monthly * months
                            } else {
                                (targetAmount.toSafeDouble() ?: 0.0)
                            }
                            
                            // Smart priority weight calculation
                            val weight = when (priority) {
                                VaultPriority.CRITICAL -> 4.0
                                VaultPriority.HIGH -> 3.0
                                VaultPriority.MEDIUM -> 2.0
                                VaultPriority.LOW -> 1.0
                            }

                            val existingSchedulesList = vault?.schedules ?: emptyList()
                            val preservedSchedules = if (existingSchedule != null) {
                                existingSchedulesList.filter { it.id != existingSchedule.id }
                            } else {
                                existingSchedulesList
                            }

                            val scheduleAmount = autoDepositAmount.toSafeDouble()
                            val scheduleType = when (autoDepositFrequency) {
                                AutoDepositFrequency.DAILY -> VaultScheduleType.DAILY
                                AutoDepositFrequency.WEEKLY, AutoDepositFrequency.BIWEEKLY -> VaultScheduleType.DAY_OF_WEEK
                                AutoDepositFrequency.MONTHLY -> VaultScheduleType.DAY_OF_MONTH
                                AutoDepositFrequency.QUARTERLY -> VaultScheduleType.QUARTERLY
                            }
                            val newSchedule = if (autoDepositEnabled) {
                                val nextRunAtValue = LocalDateTime.of(autoDepositNextRunDate, autoDepositNextRunTime)
                                VaultSchedule(
                                    id = existingSchedule?.id ?: 0L,
                                    vaultId = vault?.id ?: 0L,
                                    type = scheduleType,
                                    amount = scheduleAmount?.takeIf { it > 0.0 },
                                    percentage = null,
                                    direction = VaultTransferDirection.MAIN_TO_VAULT,
                                    dayOfMonth = if (scheduleType == VaultScheduleType.DAY_OF_MONTH) autoDepositNextRunDate.dayOfMonth else null,
                                    dayOfWeek = if (scheduleType == VaultScheduleType.DAY_OF_WEEK) autoDepositNextRunDate.dayOfWeek.value else null,
                                    weekInterval = when (autoDepositFrequency) {
                                        AutoDepositFrequency.BIWEEKLY -> 2
                                        AutoDepositFrequency.WEEKLY -> 1
                                        AutoDepositFrequency.DAILY,
                                        AutoDepositFrequency.MONTHLY,
                                        AutoDepositFrequency.QUARTERLY -> null
                                    },
                                    onlyIfBalanceAvailable = autoDepositOnlyIfBalanceAvailable,
                                    notifyBefore = autoDepositNotifyBefore,
                                    notifyAfter = autoDepositNotifyAfter,
                                    notifyOnFailure = autoDepositNotifyOnFailure,
                                    nextRunAt = nextRunAtValue,
                                    lastRunAt = existingSchedule?.lastRunAt,
                                    enabled = true,
                                    createdAt = existingSchedule?.createdAt ?: Instant.now(),
                                    updatedAt = Instant.now()
                                )
                            } else null

                            val updatedSchedules = when {
                                newSchedule != null -> listOf(newSchedule) + preservedSchedules
                                else -> preservedSchedules
                            }

                            val updatedVault = SmartVault(
                                id = vault?.id ?: 0L,
                                name = name.trim(),
                                targetAmount = target,
                                currentBalance = balance,
                                priority = priority,
                                priorityWeight = weight,
                                type = type,
                                allocationMode = VaultAllocationMode.DYNAMIC_AUTO,
                                manualAllocationPercent = null,
                                targetDate = if (neverExpires) null else if (isFlowGoal) endDate else targetDate,
                                startDate = if (isFlowGoal) startDate else null,
                                endDate = if (isFlowGoal && !neverExpires) endDate else null,
                                monthlyNeed = monthly,
                                accountNotes = accountNotes.takeIf { it.isNotBlank() },
                                allowAutoIncome = !excludedFromAutoAllocation,
                                defaultManualDepositDeductFromMain = defaultManualDepositDeductFromMain,
                                defaultManualWithdrawalCreditMain = defaultManualWithdrawalCreditMain,
                                schedules = updatedSchedules,
                                archived = vault?.archived ?: false,
                                iconName = iconName
                            )
                            onSave(updatedVault)
                        },
                        modifier = Modifier.weight(1f).widthIn(min = 100.dp),

                        enabled = validationResult.isValid,
                        icon = {
                            MaterialSymbolIcon(
                                icon = if (vault == null) MaterialSymbols.ADD else MaterialSymbols.CHECK,
                                contentDescription = null,
                                size = 18.dp
                            )
                        }
                    ) {
                        Text(if (vault == null) stringResource(R.string.vault_management_add) else stringResource(R.string.vault_save_changes))
                    }
                }
            }
        }
            }
}


@Composable
private fun NeverExpiresRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.vault_never_expires),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.vault_never_expires_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun GoalTypeCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    @DrawableRes icon: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ExpressiveCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        containerColor = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        contentPadding = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MaterialSymbolIcon(
                icon = icon,
                contentDescription = null,
                size = 32.dp,
                tint = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (isSelected) {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.CHECK_CIRCLE,
                    contentDescription = null,
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
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
