package com.example.sparely.ui.screens

import android.annotation.SuppressLint
import com.example.sparely.ui.utils.toInputString
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
    onBalanceOverride: ((Long, Double, String?) -> Unit)? = null
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
    
    Box(modifier = Modifier.fillMaxSize()) { // Wrap Scaffold in Box to overlay confetti


    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.ADD,
                        contentDescription = stringResource(R.string.vault_management_create_desc),
                        size = 24.dp
                    )
                },
                text = { Text(stringResource(R.string.vault_management_add)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues),
            // Extra bottom padding so the last card can scroll clear of the FAB
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                val urgentVaults = openVaults.filter {
                    it.targetDate?.let { date -> ChronoUnit.DAYS.between(today, date) <= 90 } == true
                }
                val activeVaults = (openVaults - urgentVaults.toSet()).filter {
                    it.monthlyNeed != null && it.startDate?.let { date -> date <= today } == true
                }
                val plannedVaults = openVaults - urgentVaults.toSet() - activeVaults.toSet()

                if (urgentVaults.isNotEmpty()) {
                    item(key = "header_urgent") {
                        SectionHeader(
                            title = stringResource(R.string.vault_urgent_goals),
                            subtitle = stringResource(R.string.vault_urgent_goals_count, urgentVaults.size),
                            icon = MaterialSymbols.LOCAL_FIRE_DEPARTMENT
                        )
                    }
                    items(urgentVaults, key = { it.id }) { vault ->
                        EnhancedVaultCard(
                            vault = vault,
                            onEdit = { vaultToEdit = vault },
                            onDelete = { vaultToDelete = vault },
                            onDeposit = onManualDeposit?.let { { vaultToDeposit = vault } },
                            onWithdraw = onManualWithdrawal?.let { { vaultToWithdraw = vault } },
                            onViewHistory = onViewHistory?.let { { it(vault.id) } }
                        )
                    }
                }

                if (activeVaults.isNotEmpty()) {
                    item(key = "header_active") {
                        SectionHeader(
                            title = stringResource(R.string.vault_active_flow_goals),
                            subtitle = stringResource(R.string.vault_active_flow_goals_count, activeVaults.size),
                            icon = MaterialSymbols.TRENDING_UP
                        )
                    }
                    items(activeVaults, key = { it.id }) { vault ->
                        EnhancedVaultCard(
                            vault = vault,
                            onEdit = { vaultToEdit = vault },
                            onDelete = { vaultToDelete = vault },
                            onDeposit = onManualDeposit?.let { { vaultToDeposit = vault } },
                            onWithdraw = onManualWithdrawal?.let { { vaultToWithdraw = vault } },
                            onViewHistory = onViewHistory?.let { { it(vault.id) } }
                        )
                    }
                }

                if (plannedVaults.isNotEmpty()) {
                    item(key = "header_planned") {
                        SectionHeader(
                            title = stringResource(R.string.vault_planned_goals),
                            subtitle = stringResource(R.string.vault_planned_goals_count, plannedVaults.size),
                            icon = MaterialSymbols.ACCOUNT_BALANCE_WALLET
                        )
                    }
                    items(plannedVaults, key = { it.id }) { vault ->
                        EnhancedVaultCard(
                            vault = vault,
                            onEdit = { vaultToEdit = vault },
                            onDelete = { vaultToDelete = vault },
                            onDeposit = onManualDeposit?.let { { vaultToDeposit = vault } },
                            onWithdraw = onManualWithdrawal?.let { { vaultToWithdraw = vault } },
                            onViewHistory = onViewHistory?.let { { it(vault.id) } }
                        )
                    }
                }

                if (completedVaults.isNotEmpty()) {
                    item(key = "header_completed") {
                        SectionHeader(
                            title = stringResource(R.string.vault_completed_goals),
                            subtitle = pluralStringResource(R.plurals.vault_completed_goals_count, completedVaults.size, completedVaults.size),
                            icon = MaterialSymbols.CHECK_CIRCLE
                        )
                    }
                    items(completedVaults, key = { it.id }) { vault ->
                        EnhancedVaultCard(
                            vault = vault,
                            onEdit = { vaultToEdit = vault },
                            onDelete = { vaultToDelete = vault },
                            onDeposit = onManualDeposit?.let { { vaultToDeposit = vault } },
                            onWithdraw = onManualWithdrawal?.let { { vaultToWithdraw = vault } },
                            onViewHistory = onViewHistory?.let { { it(vault.id) } }
                        )
                    }
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
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.primaryContainer
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
                Column {
                    Text(
                        text = stringResource(R.string.vault_label_total_saved),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = totalBalance.formatCurrency(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.vault_label_of_target, totalTarget.formatCurrency()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$overallProgress%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.vault_label_active_vaults_count, vaultCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (overallProgress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(ExpressiveShapes.pill),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
            )

            // Financial health indicator
            if (monthlyIncome > 0) {
                val displaySavingsRate = (savingsRate * 100).toInt()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HealthIndicator(
                        label = stringResource(R.string.vault_health_savings_rate),
                        value = "$displaySavingsRate%",
                        isHealthy = displaySavingsRate >= 20
                    )
                    HealthIndicator(
                        label = stringResource(R.string.vault_health_monthly_spending),
                        value = recentExpenses.formatCurrency(decimals = 0),
                        isHealthy = recentExpenses < monthlyIncome * 0.5
                    )
                }
            }
        }
    }
    }


@Composable
private fun HealthIndicator(
    label: String,
    value: String,
    isHealthy: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MaterialSymbolIcon(
            icon = if (isHealthy) MaterialSymbols.CHECK_CIRCLE else MaterialSymbols.WARNING,
            contentDescription = null,
            size = 16.dp,
            tint = if (isHealthy) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.warning
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
    }


@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    @DrawableRes icon: Int
) {
    ExpressiveSectionHeader(
        title = title,
        subtitle = subtitle,
        icon = {
            MaterialSymbolIcon(
                icon = icon,
                contentDescription = null,
                size = 24.dp,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    )
    }


@Composable
private fun EmptyVaultsCard(onCreateVault: () -> Unit) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = 32.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MaterialSymbolIcon(
                icon = MaterialSymbols.ACCOUNT_BALANCE_WALLET,
                contentDescription = null,
                size = 64.dp,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Text(
                text = stringResource(R.string.vault_empty_title_short),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.vault_empty_desc_detailed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            SparelyButton(
                onClick = onCreateVault,
                modifier = Modifier.fillMaxWidth(0.6f),
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
    }


@Composable
private fun EnhancedVaultCard(
    vault: SmartVault,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onDeposit: (() -> Unit)?,
    onWithdraw: (() -> Unit)?,
    onViewHistory: (() -> Unit)?
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
    val atText = stringResource(R.string.vault_date_time_at)
    val scheduleFormatter = remember(atText) { DateTimeFormatter.ofPattern("MMM d, yyyy '$atText' h:mm a") }
    val locale = remember { Locale.getDefault() }
    val primarySchedule = remember(vault.schedules) {
        vault.schedules
            .filter { it.enabled }.minByOrNull { it.nextRunAt ?: LocalDateTime.MAX }
    }
    
    // Calculate financial insights
    val daysUntilTarget = vault.targetDate?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
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
        vault.monthlyNeed != null && vault.startDate?.let { it <= LocalDate.now() } == true -> stringResource(R.string.vault_status_active_flow)
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

    ExpressiveCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = ExpressiveShapes.large,
        containerColor = colorScheme.surfaceContainerHigh,
        contentPadding = 0.dp,
        onClick = onEdit ?: {}
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle Gradient Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                statusColor.copy(alpha = 0.08f),
                                colorScheme.surface.copy(alpha = 0.5f)
                            )
                        )
                    )
            )

            // Watermark Icon
            MaterialSymbolIcon(
                icon = displayIcon,
                contentDescription = null,
                size = 180.dp,
                tint = statusColor.copy(alpha = 0.05f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 40.dp, y = 20.dp)
                    .rotate(-15f)
            )

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon Circle
                        Surface(
                            shape = CircleShape,
                            color = statusColor.copy(alpha = 0.1f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                MaterialSymbolIcon(
                                    icon = displayIcon,
                                    contentDescription = null,
                                    size = 24.dp,
                                    tint = statusColor
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = vault.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
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
                                        size = 16.dp,
                                        tint = statusColor
                                    )
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Action Buttons (simplified)
                    Row {
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
                }

                // Balance and Progress
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.vault_label_current_balance),
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = vault.currentBalance.formatCurrency(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                             if (remaining > 0) {
                                Text(
                                    text = stringResource(R.string.vault_label_target_with_amount, vault.targetAmount.formatCurrency("", 0)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = progress.formatPercent(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(ExpressiveShapes.pill),
                        color = statusColor,
                        trackColor = statusColor.copy(alpha = 0.2f),
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                    )
                    
                    // Forecast & Remaining
                    Row(
                         modifier = Modifier.fillMaxWidth(),
                         horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start)
                    ) {
                         if (remaining > 0) {
                             Text(
                                 text = stringResource(R.string.vault_label_remaining_to_go, remaining.formatCurrency()),
                                 style = MaterialTheme.typography.bodySmall,
                                 color = colorScheme.onSurfaceVariant,
                                 fontWeight = FontWeight.Medium,
                                 modifier = Modifier.weight(1f)
                             )
                         }
                         
                         if(projectedCompletionDate != null && remaining > 0) {
                             Text(
                                 text = stringResource(R.string.vault_label_on_track, projectedCompletionDate.format(dateFormatter)),
                                 style = MaterialTheme.typography.bodySmall,
                                 color = colorScheme.tertiary,
                                 fontWeight = FontWeight.Bold
                             )
                         } else if (vault.targetDate != null && remaining > 0) {
                             Text(
                                 text = stringResource(R.string.vault_label_due_date, vault.targetDate.format(dateFormatter)),
                                 style = MaterialTheme.typography.bodySmall,
                                 color = colorScheme.onSurfaceVariant
                             )
                         }
                    }
                }

                // Quick Actions Row
                if (onDeposit != null || onWithdraw != null || onViewHistory != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        onDeposit?.let {
                            VaultActionButton(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.vault_action_add_short),
                                icon = MaterialSymbols.ADD,
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = it
                            )
                        }
                        onWithdraw?.let {
                            VaultActionButton(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.vault_action_withdraw_short),
                                icon = MaterialSymbols.REMOVE,
                                tint = MaterialTheme.colorScheme.error,
                                onClick = it
                            )
                        }
                        onViewHistory?.let {
                             // Assuming we might want a smaller button or just an icon for history
                            VaultActionButton(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.vault_action_history_short),
                                icon = MaterialSymbols.HISTORY,
                                tint = MaterialTheme.colorScheme.secondary,
                                onClick = it
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultActionButton(
    modifier: Modifier = Modifier,
    label: String,
    @DrawableRes icon: Int,
    tint: Color,
    onClick: () -> Unit
) {
    SparelyTonalButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = tint.copy(alpha = 0.12f),
        contentColor = tint,
        icon = {
            MaterialSymbolIcon(
                icon = icon,
                contentDescription = null,
                size = 16.dp,
                tint = tint
            )
        }
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MaterialSymbolIcon(
                        icon = icon,
                        contentDescription = null,
                        size = 32.dp,
                        tint = if (isDeposit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = vaultName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Affect main account toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = affectMainLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = affectMainHelper,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = affectMainAccount,
                        onCheckedChange = { affectMainAccount = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.vault_current_balance_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currentBalance.formatCurrency(""),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                SparelyTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filterCurrencyInput() },
                    label = { Text(stringResource(R.string.vault_amount_label)) },
                    prefix = { Text(stringResource(R.string.currency_prefix)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = !isDeposit && amountText.toSafeDouble()?.let { it > currentBalance } == true,
                    supportingText = {
                        if (!isDeposit && amountText.toSafeDouble()?.let { it > currentBalance } == true) {
                            Text(
                                text = stringResource(R.string.vault_error_insufficient_balance),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )

                // Quick amount suggestions
                if (suggestedAmounts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.vault_quick_amounts),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (amount in suggestedAmounts.take(4)) {
                                SparelyChip(
                                    selected = amountText.toSafeDouble() == amount,
                                    onClick = { amountText = amount.toInputString(0) },
                                    label = {
                                        SingleLineText(
                                            text = String.format("%.0f", amount),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                SparelyTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(stringResource(R.string.vault_note_optional)) },
                    placeholder = { Text(stringResource(R.string.vault_note_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    // minLines not supported in simple SparelyTextField yet, but singleLine=false defaults.
                    // Assuming SparelyTextField handles multiline if singleLine is false (default is true).
                    // I will remove min/maxLines for now as my SparelyTextField definition was simple.
                    // Wait, SparelyTextField definition hardcodes singleLine=true default but allows override.
                    // But it passes singleLine to internal TextField.
                    // However, internal TextField in SparelyUiComponents uses singleLine=singleLine.
                    // And it does NOT expose minLines/maxLines.
                    // To avoid regression, I'll pass singleLine=false.
                    singleLine = false
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        SparelyTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }

                    if (isDeposit) {
                        SparelyButton(
                            onClick = {
                                val amount = amountText.toSafeDouble()
                                if (amount != null && amount > 0) {
                                    val finalReason = reason.trim().ifBlank { defaultDepositReason }
                                    onConfirm(amount, finalReason, affectMainAccount)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = amountText.toSafeDouble()?.let { it > 0 } == true
                        ) {
                            Text(stringResource(R.string.vault_management_add))
                        }
                    } else {
                        // For withdraw, we want a red button, so we might need a custom Sparely button or just configure SparelyButton
                        // Since SparelyButton uses primary color, we can't easily change it to error color without adding a param.
                        // Let's use Button with the shape/style of SparelyButton manually or add a SparelyErrorButton.
                        // For now, I'll use Button but style it to match SparelyButton (height 48, radius 16, bold text).
                        SparelyButton(
                            onClick = {
                                val amount = amountText.toSafeDouble()
                                if (amount != null && amount > 0 && amount <= currentBalance) {
                                    val finalReason = reason.trim().ifBlank { defaultWithdrawReason }
                                    onConfirm(amount, finalReason, affectMainAccount)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = amountText.toSafeDouble()?.let { 
                                it > 0 && it <= currentBalance
                            } == true,
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) {
                            Text(stringResource(R.string.vault_withdraw))
                        }
                    }
                }
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
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (vault == null) stringResource(R.string.vault_editor_add_title) else stringResource(R.string.vault_editor_edit_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.vault_allocation_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                targetDate = if (isFlowGoal) endDate else targetDate,
                                startDate = if (isFlowGoal) startDate else null,
                                endDate = if (isFlowGoal) endDate else null,
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
