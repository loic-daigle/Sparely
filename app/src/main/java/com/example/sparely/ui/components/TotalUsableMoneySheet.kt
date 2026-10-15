package com.example.sparely.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.SavingsAccount
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.theme.spacing
import com.sparely.app.R
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotalUsableMoneyBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    mainBalance: Double,
    minMainBalance: Double,
    wishlistReserved: Double,
    pendingTransfers: Double,
    netUsable: Double,
    hisaAccounts: List<SavingsAccount>,
    onNavigateToMainAccount: () -> Unit,
    onNavigateToAccount: (Long) -> Unit
) {
    val spacing = MaterialTheme.spacing
    val today = java.time.LocalDate.now()
    // Locked terms, notice periods and required minimums can't be spent today.
    val totalHisa = hisaAccounts.sumOf { it.accessibleBalance(today) }
    val totalLocked = hisaAccounts.sumOf { it.lockedBalance(today) }
    val totalLiquid = mainBalance + totalHisa
    val usable = netUsable

    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md)
                .padding(bottom = spacing.xl), // Add padding for bottom nav/safe area
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            SparelySheetHeader(
                title = stringResource(R.string.dashboard_total_usable_money_title),
                icon = com.example.sparely.ui.theme.MaterialSymbols.ACCOUNT_BALANCE_WALLET
            )

            // Main Account
            BalanceRow(
                title = stringResource(R.string.dashboard_main_account_title),
                amount = mainBalance,
                isMain = true,
                onClick = {
                    onNavigateToMainAccount()
                    onDismissRequest()
                }
            )

            // HISA Accounts
            hisaAccounts.forEach { account ->
                BalanceRow(
                    title = account.name,
                    subtitle = "${account.annualPercentageYield.formatPercent(1)} APY",
                    amount = account.accessibleBalance(today),
                    onClick = {
                        onNavigateToAccount(account.id)
                        onDismissRequest()
                    }
                )
            }

            // Money that exists but can't be withdrawn today
            if (totalLocked > 0.005) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.usable_money_locked_savings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = totalLocked.formatCurrency(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Minimum Balance Deduction (if any)
            if (minMainBalance > 0) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.usable_money_reserved_min),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "-${minMainBalance.formatCurrency()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (wishlistReserved > 0.005) {
                ReservedRow(stringResource(R.string.usable_money_reserved_wishes), wishlistReserved)
            }
            if (pendingTransfers > 0.005) {
                ReservedRow(stringResource(R.string.usable_money_pending_transfers), pendingTransfers)
            }

            HorizontalDivider()

            // Final Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.usable_money_net),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = usable.formatCurrency(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun BalanceRow(
    title: String,
    amount: Double,
    subtitle: String? = null,
    isMain: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isMain) FontWeight.Bold else FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = amount.formatCurrency(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


@Composable
private fun ReservedRow(title: String, amount: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "-${amount.formatCurrency()}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
    }
}
