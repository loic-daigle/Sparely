package com.example.sparely.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.Asset
import com.example.sparely.domain.model.DetectedRecurringTransaction
import com.example.sparely.domain.model.EmergencyFundGoal
import com.example.sparely.domain.model.PaymentMethod
import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.SavingsAccount
import com.example.sparely.domain.model.SmartAllocationMode
import com.example.sparely.domain.model.SmartSavingSummary
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.UpcomingRecurringExpense
import com.example.sparely.domain.model.nextDueDate
import com.example.sparely.ui.components.SegmentGap
import com.example.sparely.ui.components.segmentedShape
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.critical
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.theme.warning
import com.example.sparely.ui.theme.warningContainer
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.sparely.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/** Days ahead within which a payment shows in orange. */
private const val DUE_SOON_DAYS = 3
/** Rows a dashboard group shows before its "see all" row. */
private const val GROUP_ROWS = 3
/** Credit use above this share of the limit starts to weigh on a credit score. */
private const val HEALTHY_UTILIZATION = 0.30

// ─────────────────────────────────────────────────────────────────────────────
// Shared pieces
// ─────────────────────────────────────────────────────────────────────────────

/** Typed holders, so lambdas built in lists and lets are composable. */
private fun groupRow(content: @Composable (Shape) -> Unit): @Composable (Shape) -> Unit = content
private fun slot(content: @Composable () -> Unit): @Composable () -> Unit = content
private fun rowSlot(content: @Composable RowScope.() -> Unit): @Composable RowScope.() -> Unit = content

/**
 * One row of a dashboard group: a tinted icon (or [leading]), a title over a subtitle that can
 * take its own color, [trailing] content, and [below] content under the text (a bar, a chip).
 */
@Composable
private fun DashRow(
    title: String,
    shape: Shape,
    subtitle: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    @DrawableRes icon: Int? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                when {
                    leading != null -> leading()
                    icon != null -> TintedIcon(icon, iconTint)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                trailing?.invoke(this)
            }
            below?.let {
                // Lined up with the text, past the 44dp icon and its gap
                Column(modifier = Modifier.padding(start = if (icon != null || leading != null) 58.dp else 0.dp, top = 10.dp)) {
                    it()
                }
            }
        }
    }
    val color = MaterialTheme.colorScheme.surfaceContainer
    if (onClick != null) {
        Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = shape, color = color) { content() }
    } else {
        Surface(modifier = Modifier.fillMaxWidth(), shape = shape, color = color) { content() }
    }
}

@Composable
private fun TintedIcon(@DrawableRes icon: Int, tint: Color, size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        MaterialSymbolIcon(icon = icon, contentDescription = null, size = size * 0.5f, tint = tint)
    }
}

/** Rows stacked as one segmented group: rounded at the ends, a hairline gap between them. */
@Composable
private fun SegmentedGroup(rows: List<@Composable (Shape) -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
        rows.forEachIndexed { index, row -> row(segmentedShape(index, rows.size)) }
    }
}

/** The last row of a group: what's not shown, and where to see it. */
@Composable
private fun SeeAllRow(text: String, shape: Shape, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            MaterialSymbolIcon(
                icon = MaterialSymbols.ARROW_FORWARD,
                contentDescription = null,
                size = 18.dp,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun Amount(text: String, color: Color = MaterialTheme.colorScheme.onSurface, under: String? = null) {
    Column(horizontalAlignment = Alignment.End) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        under?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color, container: Color = color.copy(alpha = 0.14f)) {
    Surface(shape = CircleShape, color = container) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun Bar(progress: Float, color: Color, height: Dp = 6.dp) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape),
        color = color,
        trackColor = color.copy(alpha = 0.18f),
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

/** A bar with a tick where the goal is ([mark] as a share of the bar). */
@Composable
private fun BarWithMark(progress: Float, mark: Float, color: Color) {
    var widthPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .onSizeChanged { widthPx = it.width },
        contentAlignment = Alignment.CenterStart
    ) {
        Bar(progress = progress, color = color, height = 10.dp)
        if (widthPx > 0) {
            val x = with(density) { (widthPx * mark.coerceIn(0f, 1f)).toDp() } - 1.5.dp
            Box(
                modifier = Modifier
                    .offset(x = x)
                    .width(3.dp)
                    .height(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}

/** "Today", "Tomorrow", "In 5 days". */
@Composable
private fun whenLabel(daysUntil: Int): String = when (daysUntil) {
    0 -> stringResource(R.string.dash_when_today)
    1 -> stringResource(R.string.dash_when_tomorrow)
    else -> stringResource(R.string.dash_when_in_days, daysUntil)
}

/** How a payment is paid: the card's name, or the main account. */
@Composable
private fun paidWith(recurring: RecurringExpense, paymentMethods: List<PaymentMethod>): String? =
    paymentMethods.find { it.id == recurring.paymentMethodId }?.name
        ?: if (recurring.deductFromMainAccount) stringResource(R.string.dash_main_account) else null

private val dayMonthFormatter: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "dMMM"))

// ─────────────────────────────────────────────────────────────────────────────
// Needs attention
// ─────────────────────────────────────────────────────────────────────────────

internal enum class AttentionTone { INFO, WARNING, CRITICAL }

internal data class AttentionItem(
    @DrawableRes val icon: Int,
    val title: String,
    val subtitle: String?,
    val amount: String?,
    val tone: AttentionTone,
    val onClick: () -> Unit
)

@Composable
internal fun AttentionGroup(items: List<AttentionItem>) {
    val colors = MaterialTheme.colorScheme
    SegmentedGroup(
        items.map { item ->
            groupRow { shape ->
                val tint = when (item.tone) {
                    AttentionTone.INFO -> colors.primary
                    AttentionTone.WARNING -> colors.warning
                    AttentionTone.CRITICAL -> colors.critical
                }
                DashRow(
                    title = item.title,
                    shape = shape,
                    subtitle = item.subtitle,
                    subtitleColor = if (item.tone == AttentionTone.WARNING) colors.warning else colors.onSurfaceVariant,
                    icon = item.icon,
                    iconTint = tint,
                    onClick = item.onClick,
                    trailing = item.amount?.let { amount ->
                        rowSlot { Amount(amount, color = if (item.tone == AttentionTone.CRITICAL) colors.critical else colors.onSurface) }
                    }
                )
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Accounts & goals
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun AccountsAndGoals(
    savingsAccounts: List<SavingsAccount>,
    vaults: List<SmartVault>,
    pendingByVault: Map<Long, Double>,
    assets: List<Asset>,
    workingOnProjectId: Long?,
    onManageSavingsAccounts: () -> Unit,
    onManageVaults: () -> Unit,
    onOpenVault: (Long) -> Unit,
    onManageAssets: () -> Unit
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val activeAssets = assets.filter { !it.archived && !it.isDone }
        .sortedWith(compareByDescending<Asset> { it.id == workingOnProjectId }.thenByDescending { it.isProject }.thenByDescending { it.totalSpending })

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalTile(
                icon = MaterialSymbols.ACCOUNT_BALANCE,
                label = stringResource(R.string.dash_tile_accounts),
                amount = savingsAccounts.sumOf { it.currentBalance },
                selected = selected == 0,
                onClick = { selected = 0 }
            )
            GoalTile(
                icon = MaterialSymbols.SAVINGS,
                label = stringResource(R.string.dash_tile_vaults),
                amount = vaults.sumOf { it.currentBalance },
                selected = selected == 1,
                showDot = pendingByVault.isNotEmpty(),
                onClick = { selected = 1 }
            )
            GoalTile(
                icon = MaterialSymbols.FLAG,
                label = stringResource(R.string.dash_tile_projects),
                amount = activeAssets.sumOf { it.totalSpending },
                selected = selected == 2,
                onClick = { selected = 2 }
            )
        }
        when (selected) {
            0 -> AccountsList(savingsAccounts, onManageSavingsAccounts)
            1 -> VaultsList(vaults, pendingByVault, onManageVaults, onOpenVault)
            else -> ProjectsList(activeAssets, workingOnProjectId, onManageAssets)
        }
    }
}

@Composable
private fun RowScope.GoalTile(
    @DrawableRes icon: Int,
    label: String,
    amount: Double,
    selected: Boolean,
    showDot: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onPrimaryContainer else colors.onSurface
    Surface(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceContainer,
        contentColor = content
    ) {
        Box {
            Column(modifier = Modifier.padding(12.dp)) {
                MaterialSymbolIcon(icon = icon, contentDescription = null, size = 22.dp, tint = if (selected) content else colors.primary)
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) content.copy(alpha = 0.8f) else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    amount.formatCurrency(decimals = 0),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(8.dp)
                        .background(colors.critical, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun AccountsList(accounts: List<SavingsAccount>, onManage: () -> Unit) {
    if (accounts.isEmpty()) {
        DashRow(
            title = stringResource(R.string.dashboard_start_saving_title),
            shape = segmentedShape(0, 1),
            subtitle = stringResource(R.string.dashboard_start_saving_subtitle),
            icon = MaterialSymbols.ADD,
            onClick = onManage
        )
        return
    }
    val shown = accounts.sortedByDescending { it.currentBalance }.take(GROUP_ROWS)
    val rows = shown.map { account ->
        groupRow { shape ->
            DashRow(
                title = account.name,
                shape = shape,
                subtitle = stringResource(R.string.dash_interest, account.annualPercentageYield.formatPercent(2)),
                icon = MaterialSymbols.ACCOUNT_BALANCE,
                onClick = onManage,
                trailing = { Amount(account.currentBalance.formatCurrency()) }
            )
        }
    } + groupRow { shape ->
        SeeAllRow(stringResource(R.string.dash_manage_accounts), shape, onManage)
    }
    SegmentedGroup(rows)
}

@Composable
private fun VaultsList(
    vaults: List<SmartVault>,
    pendingByVault: Map<Long, Double>,
    onManage: () -> Unit,
    onOpenVault: (Long) -> Unit
) {
    if (vaults.isEmpty()) {
        DashRow(
            title = stringResource(R.string.dashboard_vaults_empty_title),
            shape = segmentedShape(0, 1),
            subtitle = stringResource(R.string.dashboard_vaults_empty_description),
            icon = MaterialSymbols.ADD,
            onClick = onManage
        )
        return
    }
    val colors = MaterialTheme.colorScheme
    // Ones with a transfer waiting first, then the closest to their date
    val shown = vaults
        .sortedWith(compareByDescending<SmartVault> { pendingByVault.containsKey(it.id) }.thenBy { it.targetDate ?: LocalDate.MAX })
        .take(GROUP_ROWS)
    val rows = shown.map { vault ->
        groupRow { shape ->
            val status = vaultStatus(vault)
            val target = vault.targetAmount.takeIf { it > 0 }
            val progress = target?.let { (vault.currentBalance / it).toFloat() }
            DashRow(
                title = vault.name,
                shape = shape,
                subtitle = status.text,
                subtitleColor = status.color,
                icon = vault.displayIcon(),
                onClick = { onOpenVault(vault.id) },
                trailing = {
                    Amount(
                        vault.currentBalance.formatCurrency(decimals = 0),
                        under = target?.let { stringResource(R.string.assets_of_amount, it.formatCurrency(decimals = 0)) }
                    )
                },
                below = if (progress == null && pendingByVault[vault.id] == null) null else slot {
                        progress?.let { Bar(it, if (status.color == colors.onSurfaceVariant) colors.primary else status.color) }
                        pendingByVault[vault.id]?.let { pending ->
                            Spacer(Modifier.height(8.dp))
                            Surface(shape = CircleShape, color = colors.primary.copy(alpha = 0.12f)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    MaterialSymbolIcon(icon = MaterialSymbols.SWAP_HORIZ, contentDescription = null, size = 14.dp, tint = colors.primary)
                                    Text(
                                        stringResource(R.string.dash_to_transfer, pending.formatCurrency(decimals = 0)),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.primary
                                    )
                                }
                            }
                        }
                }
            )
        }
    } + groupRow { shape ->
        SeeAllRow(pluralStringResource(R.plurals.dash_see_all_vaults, vaults.size, vaults.size), shape, onManage)
    }
    SegmentedGroup(rows)
}

@Composable
private fun ProjectsList(assets: List<Asset>, workingOnProjectId: Long?, onManage: () -> Unit) {
    if (assets.isEmpty()) {
        DashRow(
            title = stringResource(R.string.dash_projects_empty_title),
            shape = segmentedShape(0, 1),
            subtitle = stringResource(R.string.dash_projects_empty_desc),
            icon = MaterialSymbols.ADD,
            onClick = onManage
        )
        return
    }
    val colors = MaterialTheme.colorScheme
    val rows = assets.take(GROUP_ROWS).map { asset ->
        groupRow { shape ->
            val isWorkingOn = asset.id == workingOnProjectId
            val budget = asset.assetPrice.takeIf { it > 0 }
            val over = budget != null && asset.totalSpending > budget
            DashRow(
                title = asset.name,
                shape = shape,
                subtitle = assetSubtitle(asset, isWorkingOn),
                subtitleColor = if (isWorkingOn) colors.primary else colors.onSurfaceVariant,
                icon = asset.icon(),
                iconTint = if (over) colors.critical else colors.primary,
                onClick = onManage,
                trailing = {
                    Amount(
                        asset.totalSpending.formatCurrency(decimals = 0),
                        under = budget?.let { stringResource(R.string.assets_of_amount, it.formatCurrency(decimals = 0)) }
                    )
                },
                below = budget?.let {
                    slot {
                        Bar((asset.totalSpending / it).toFloat(), if (over) colors.critical else colors.primary)
                        if (over) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.assets_over_by, (asset.totalSpending - it).formatCurrency(decimals = 0)),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.critical
                            )
                        }
                    }
                }
            )
        }
    } + groupRow { shape ->
        SeeAllRow(stringResource(R.string.dash_see_projects), shape, onManage)
    }
    SegmentedGroup(rows)
}

// ─────────────────────────────────────────────────────────────────────────────
// Coming up
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun UpcomingPayments(
    items: List<UpcomingRecurringExpense>,
    recurring: List<RecurringExpense>,
    paymentMethods: List<PaymentMethod>,
    onManageRecurring: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val sorted = items.sortedBy { it.dueDate }
    if (sorted.isEmpty()) {
        val next = recurring.filter { it.isActive }
            .mapNotNull { r -> r.nextDueDate()?.let { r to it } }
            .minByOrNull { it.second }
        DashRow(
            title = stringResource(if (recurring.isNotEmpty()) R.string.dashboard_all_bills_paid else R.string.dashboard_up_next),
            shape = segmentedShape(0, 1),
            subtitle = when {
                next != null -> stringResource(R.string.dash_next_payment, next.first.description, next.second.format(dayMonthFormatter))
                recurring.isEmpty() -> stringResource(R.string.dashboard_add_subscriptions_prompt)
                else -> null
            },
            icon = if (recurring.isNotEmpty()) MaterialSymbols.CHECK_CIRCLE else MaterialSymbols.ADD,
            iconTint = if (recurring.isNotEmpty()) colors.success else colors.primary,
            onClick = onManageRecurring
        )
        return
    }

    val amountOf = { u: UpcomingRecurringExpense -> u.predictedAmount ?: u.recurringExpense.amount }
    val monthFormatter = DateTimeFormatter.ofPattern("MMM")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            pluralStringResource(R.plurals.dash_upcoming_summary, sorted.size, sorted.size, sorted.sumOf(amountOf).formatCurrency(decimals = 0)),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        val shown = sorted.take(GROUP_ROWS)
        val rest = sorted.drop(GROUP_ROWS)
        val rows = shown.map { upcoming ->
            groupRow { shape ->
                val soon = upcoming.daysUntilDue in 0..DUE_SOON_DAYS
                val recurringExpense = upcoming.recurringExpense
                DashRow(
                    title = recurringExpense.description,
                    shape = shape,
                    subtitle = listOfNotNull(
                        whenLabel(upcoming.daysUntilDue),
                        if (recurringExpense.isVariableAmount) stringResource(R.string.dash_variable_amount)
                        else paidWith(recurringExpense, paymentMethods)
                    ).joinToString(" · "),
                    subtitleColor = if (soon) colors.warning else colors.onSurfaceVariant,
                    onClick = onManageRecurring,
                    leading = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (soon) colors.warningContainer else colors.surfaceContainerHighest,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(upcoming.dueDate.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(upcoming.dueDate.format(monthFormatter), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    trailing = {
                        val amount = amountOf(upcoming).formatCurrency()
                        Amount(if (recurringExpense.isVariableAmount) "≈ $amount" else amount)
                    }
                )
            }
        } + if (rest.isEmpty()) emptyList() else listOf(groupRow { shape ->
            SeeAllRow(
                pluralStringResource(R.plurals.dash_more_payments, rest.size, rest.size, rest.sumOf(amountOf).formatCurrency(decimals = 0)),
                shape,
                onManageRecurring
            )
        })
        SegmentedGroup(rows)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Savings progress
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProgressCard(
    @DrawableRes icon: Int,
    iconTint: Color,
    title: String,
    subtitle: String,
    chip: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TintedIcon(icon, iconTint)
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                chip?.invoke()
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

/** 6.0 → "6", 2.4 → "2.4" (in the user's language). */
private fun Double.compact(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(java.util.Locale.getDefault(), "%.1f", this)

@Composable
internal fun EmergencyFundProgress(goal: EmergencyFundGoal) {
    val colors = MaterialTheme.colorScheme
    val coverage = goal.coverageRatio.coerceIn(0.0, 1.0)
    val reached = coverage >= 1.0
    val accent = if (reached) colors.success else colors.primary
    val saved = (goal.targetAmount - goal.shortfallAmount).coerceAtLeast(0.0)
    val monthsCovered = goal.targetMonths * coverage

    ProgressCard(
        icon = MaterialSymbols.LOCAL_FIRE_DEPARTMENT,
        iconTint = colors.critical,
        title = stringResource(R.string.dashboard_emergency_runway),
        subtitle = stringResource(R.string.dash_emergency_goal, goal.targetMonths.compact()),
        chip = {
            if (reached) StatusChip(stringResource(R.string.dash_reached), colors.success)
            else StatusChip("${(coverage * 100).toInt()} %", colors.primary)
        }
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.dash_months_value, monthsCovered.compact()),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.dash_covered),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        // One block per month of the goal; past a year it's one bar
        val months = ceil(goal.targetMonths).toInt()
        if (months in 1..12) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(months) { index ->
                    val fill = (monthsCovered - index).coerceIn(0.0, 1.0).toFloat()
                    Box(modifier = Modifier.weight(1f)) { Bar(fill, accent, height = 10.dp) }
                }
            }
        } else {
            Bar(coverage.toFloat(), accent, height = 10.dp)
        }
        Spacer(Modifier.height(6.dp))
        Row {
            Text(
                stringResource(R.string.dash_set_aside, saved.formatCurrency(decimals = 0)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.assets_of_amount, goal.targetAmount.formatCurrency(decimals = 0)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        val monthly = goal.recommendedMonthlyContribution
        if (!reached && monthly > 0.0) {
            val monthsToGo = ceil(goal.shortfallAmount / monthly).toInt().coerceAtLeast(1)
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = colors.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MaterialSymbolIcon(icon = MaterialSymbols.LIGHTBULB, contentDescription = null, size = 18.dp, tint = colors.primary)
                    Text(
                        pluralStringResource(R.plurals.dash_emergency_tip, monthsToGo, monthly.formatCurrency(decimals = 0), monthsToGo),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
internal fun SmartSavingProgress(summary: SmartSavingSummary, monthlyIncome: Double) {
    val colors = MaterialTheme.colorScheme
    val onTrack = summary.actualSavingsRate >= summary.targetSavingsRate
    val accent = if (onTrack) colors.success else colors.warning

    ProgressCard(
        icon = MaterialSymbols.SAVINGS,
        iconTint = colors.primary,
        title = stringResource(R.string.dashboard_smart_saving),
        subtitle = stringResource(
            when (summary.allocationMode) {
                SmartAllocationMode.MANUAL -> R.string.dash_mode_manual
                SmartAllocationMode.GUIDED -> R.string.dash_mode_guided
                SmartAllocationMode.AUTOMATIC -> R.string.dash_mode_automatic
            }
        )
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.dashboard_savings_rate_label), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Text(
                    summary.actualSavingsRate.formatPercent(0),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusChip(stringResource(if (onTrack) R.string.dashboard_on_track else R.string.dashboard_below_target), accent)
                Text(
                    stringResource(R.string.dash_target_pct, summary.targetSavingsRate.formatPercent(0)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // The bar runs to whichever is higher, the rate or a bit past the target, so the tick sits inside it
        val scale = maxOf(summary.actualSavingsRate, summary.targetSavingsRate * 1.25).takeIf { it > 0 } ?: 1.0
        BarWithMark(
            progress = (summary.actualSavingsRate / scale).toFloat(),
            mark = (summary.targetSavingsRate / scale).toFloat(),
            color = accent
        )
        if (monthlyIncome > 0.0) {
            val saved = monthlyIncome * summary.actualSavingsRate
            val aim = monthlyIncome * summary.targetSavingsRate
            Spacer(Modifier.height(6.dp))
            Text(
                if (onTrack) stringResource(R.string.dash_saved_ahead, saved.formatCurrency(decimals = 0), (saved - aim).formatCurrency(decimals = 0))
                else stringResource(R.string.dash_saved_aim, saved.formatCurrency(decimals = 0), aim.formatCurrency(decimals = 0)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }

        val split = if (summary.allocationMode == SmartAllocationMode.MANUAL) summary.manualSplit else summary.recommendedSplit
        if (split.total > 0.0) {
            val parts = listOf(
                Triple(R.string.dash_split_emergency, split.emergency, colors.critical),
                Triple(R.string.dash_split_invest, split.invest, colors.tertiary),
                Triple(R.string.dash_split_fun, split.`fun`, colors.primary)
            ).filter { it.second > 0.0 }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.dash_split_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHighest),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                parts.forEach { (_, share, color) ->
                    Box(modifier = Modifier.weight(share.toFloat()).height(10.dp).background(color))
                }
                val rest = (1.0 - split.total).coerceAtLeast(0.0)
                if (rest > 0.0) Spacer(Modifier.weight(rest.toFloat()))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                parts.forEach { (label, share, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(8.dp).background(color, CircleShape))
                        Text(
                            stringResource(label, share.formatPercent(0)),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Credit cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun CreditCardsGroup(cards: List<PaymentMethod>, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // What is owed: a card in credit (after a refund) doesn't pay off another card's balance
    val owed = cards.sumOf { it.currentBalance.coerceAtLeast(0.0) }
    val limit = cards.sumOf { it.creditLimit ?: 0.0 }
    val utilization = if (limit > 0) owed / limit else 0.0
    val healthy = utilization <= HEALTHY_UTILIZATION
    val accent = if (healthy) colors.success else colors.warning

    val rows = listOf(groupRow { shape ->
        Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = shape, color = colors.surfaceContainer) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.dash_you_owe), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(owed.formatCurrency(decimals = 0), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (limit > 0) {
                        Text(
                            stringResource(R.string.dash_of_limit, limit.formatCurrency(decimals = 0)),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }
                }
                if (limit > 0) {
                    Spacer(Modifier.height(8.dp))
                    val scale = maxOf(utilization, 1.0)
                    BarWithMark((utilization / scale).toFloat(), (HEALTHY_UTILIZATION / scale).toFloat(), accent)
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text(
                            stringResource(R.string.dash_used_pct, utilization.formatPercent(0)),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(HEALTHY_UTILIZATION.formatPercent(0), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Text(
                        stringResource(if (healthy) R.string.dash_util_good else R.string.dash_util_high),
                        style = MaterialTheme.typography.bodySmall,
                        color = accent
                    )
                }
            }
        }
    }) + cards.sortedByDescending { it.currentBalance }.map { card ->
        groupRow { shape ->
            val cardLimit = card.creditLimit?.takeIf { it > 0 }
            val inCredit = card.currentBalance < 0
            val used = cardLimit?.let { card.currentBalance.coerceAtLeast(0.0) / it }
            val barColor = when {
                used == null || used <= HEALTHY_UTILIZATION -> colors.primary
                used <= 0.5 -> colors.warning
                else -> colors.critical
            }
            DashRow(
                title = card.name,
                shape = shape,
                subtitle = card.billingCycleDay?.let { stringResource(R.string.dash_statement_day, it) }
                    ?: stringResource(R.string.dash_credit_card),
                icon = MaterialSymbols.CREDIT_CARD,
                iconTint = colors.tertiary,
                onClick = onClick,
                trailing = {
                    if (inCredit) Amount(stringResource(R.string.dash_credit_of, (-card.currentBalance).formatCurrency(decimals = 0)), color = colors.success)
                    else Amount(card.currentBalance.formatCurrency(), color = if (used != null && used > 0.5) colors.critical else colors.onSurface)
                },
                below = if (used == null || inCredit) null else slot {
                        Bar(used.toFloat(), barColor)
                        Spacer(Modifier.height(6.dp))
                        Row {
                            Text(
                                stringResource(R.string.dash_of_limit_pct, used.formatPercent(0)),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                stringResource(R.string.dash_available, ((cardLimit ?: 0.0) - card.currentBalance).coerceAtLeast(0.0).formatCurrency(decimals = 0)),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                }
            )
        }
    }
    SegmentedGroup(rows)
}

// ─────────────────────────────────────────────────────────────────────────────
// Payments that repeat
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun cadenceLabel(amount: String, days: Int): String = when (days) {
    in 6..8 -> stringResource(R.string.dash_cadence_week, amount)
    in 13..16 -> stringResource(R.string.dash_cadence_two_weeks, amount)
    in 27..33 -> stringResource(R.string.dash_cadence_month, amount)
    in 355..375 -> stringResource(R.string.dash_cadence_year, amount)
    else -> stringResource(R.string.dash_cadence_days, amount, days)
}

@Composable
internal fun RepeatingPayments(insights: List<DetectedRecurringTransaction>, onAdd: (DetectedRecurringTransaction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.dash_patterns_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        SegmentedGroup(
            insights.take(GROUP_ROWS).map { insight ->
                groupRow { shape ->
                    val next = insight.lastOccurrence.plusDays(insight.cadenceDays.toLong())
                    DashRow(
                        title = insight.description,
                        shape = shape,
                        subtitle = cadenceLabel(insight.averageAmount.formatCurrency(), insight.cadenceDays) + "\n" +
                            stringResource(R.string.dash_next_on, next.format(dayMonthFormatter)),
                        icon = MaterialSymbols.AUTORENEW,
                        onClick = { onAdd(insight) },
                        trailing = {
                            Surface(onClick = { onAdd(insight) }, shape = CircleShape, color = colors.secondaryContainer) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null, size = 16.dp, tint = colors.onSecondaryContainer)
                                    Text(
                                        stringResource(R.string.dash_add),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    )
                }
            }
        )
    }
}
