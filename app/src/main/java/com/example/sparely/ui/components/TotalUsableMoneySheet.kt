package com.example.sparely.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.SavingsAccount
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.critical
import com.example.sparely.ui.theme.spacing
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.sparely.app.R

/** One amount held back from the money in the accounts, with its icon. */
private data class SetAside(val title: String, val amount: Double, @DrawableRes val icon: Int)

/**
 * What "usable money" is made of: the hero total, the accounts it comes from, and what is set
 * aside before it can be spent, in the app's segmented lists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotalUsableMoneyBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    mainBalance: Double,
    minMainBalance: Double,
    wishlistReserved: Double,
    pendingTransfers: Double,
    billsDue: Double = 0.0,
    creditCardOwed: Double = 0.0,
    netUsable: Double,
    hisaAccounts: List<SavingsAccount>,
    onNavigateToMainAccount: () -> Unit,
    onNavigateToAccount: (Long) -> Unit
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val today = java.time.LocalDate.now()
    // Locked terms, notice periods and required minimums can't be spent today.
    val totalHisa = hisaAccounts.sumOf { it.accessibleBalance(today) }
    val totalLocked = hisaAccounts.sumOf { it.lockedBalance(today) }
    val totalLiquid = mainBalance + totalHisa

    val setAside = listOf(
        SetAside(stringResource(R.string.usable_money_reserved_min), minMainBalance, MaterialSymbols.ACCOUNT_BALANCE),
        SetAside(stringResource(R.string.usable_money_reserved_wishes), wishlistReserved, MaterialSymbols.FAVORITE),
        SetAside(stringResource(R.string.usable_money_pending_transfers), pendingTransfers, MaterialSymbols.SWAP_HORIZ),
        SetAside(stringResource(R.string.usable_money_bills_due), billsDue, MaterialSymbols.RECEIPT),
        SetAside(stringResource(R.string.usable_money_credit_cards), creditCardOwed, MaterialSymbols.CREDIT_CARD)
    ).filter { it.amount > 0.005 }

    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.md)
                .padding(bottom = spacing.xl)
        ) {
            SparelySheetHeader(
                title = stringResource(R.string.dashboard_total_usable_money_title),
                icon = MaterialSymbols.ACCOUNT_BALANCE_WALLET
            )

            // The total, and how much of the money in the accounts it is
            ExpressiveCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.md),
                containerColor = colors.primaryContainer
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.usable_money_net),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        netUsable.formatCurrency(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer
                    )
                    if (totalLiquid > 0.005) {
                        Text(
                            stringResource(R.string.usable_money_out_of, totalLiquid.formatCurrency()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        LinearProgressIndicator(
                            progress = { (netUsable / totalLiquid).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(8.dp),
                            color = colors.primary,
                            trackColor = colors.onPrimaryContainer.copy(alpha = 0.12f),
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                    }
                }
            }

            // Where the money is
            ExpressiveListHeader(
                title = stringResource(R.string.usable_money_accounts),
                icon = MaterialSymbols.ACCOUNT_BALANCE,
                trailing = totalLiquid.formatCurrency()
            )
            val accountRows = 1 + hisaAccounts.size + if (totalLocked > 0.005) 1 else 0
            SegmentedListItem(
                title = stringResource(R.string.dashboard_main_account_title),
                shape = segmentedShape(0, accountRows),
                icon = MaterialSymbols.ACCOUNT_BALANCE_WALLET,
                onClick = {
                    onNavigateToMainAccount()
                    onDismissRequest()
                },
                trailing = { Amount(mainBalance) }
            )
            hisaAccounts.forEachIndexed { i, account ->
                SegmentedListItem(
                    title = account.name,
                    subtitle = stringResource(R.string.usable_money_apy, account.annualPercentageYield.formatPercent(1)),
                    shape = segmentedShape(i + 1, accountRows),
                    modifier = Modifier.segmentSpacing(i + 1),
                    icon = MaterialSymbols.SAVINGS,
                    iconTint = colors.success,
                    onClick = {
                        onNavigateToAccount(account.id)
                        onDismissRequest()
                    },
                    trailing = { Amount(account.accessibleBalance(today)) }
                )
            }
            if (totalLocked > 0.005) {
                // Money that exists but can't be withdrawn today, so it isn't counted
                SegmentedListItem(
                    title = stringResource(R.string.usable_money_locked_savings),
                    subtitle = stringResource(R.string.usable_money_locked_desc),
                    shape = segmentedShape(accountRows - 1, accountRows),
                    modifier = Modifier.segmentSpacing(accountRows - 1),
                    icon = MaterialSymbols.LOCK,
                    iconTint = colors.onSurfaceVariant,
                    trailing = { Amount(totalLocked, muted = true) }
                )
            }

            // What comes off before it can be spent
            if (setAside.isNotEmpty()) {
                ExpressiveListHeader(
                    title = stringResource(R.string.usable_money_set_aside),
                    icon = MaterialSymbols.LOCK,
                    trailing = "-" + setAside.sumOf { it.amount }.formatCurrency()
                )
                setAside.forEachIndexed { i, item ->
                    SegmentedListItem(
                        title = item.title,
                        shape = segmentedShape(i, setAside.size),
                        modifier = Modifier.segmentSpacing(i),
                        icon = item.icon,
                        iconTint = colors.critical,
                        trailing = { Amount(-item.amount) }
                    )
                }
            }
        }
    }
}

/** An amount at the end of a row: taken off in the critical color, locked money muted. */
@Composable
private fun Amount(amount: Double, muted: Boolean = false) {
    Text(
        text = if (amount < 0) "-" + (-amount).formatCurrency() else amount.formatCurrency(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = when {
            muted -> MaterialTheme.colorScheme.onSurfaceVariant
            amount < 0 -> MaterialTheme.colorScheme.critical
            else -> MaterialTheme.colorScheme.onSurface
        }
    )
}
