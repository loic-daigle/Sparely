package com.example.sparely.ui.screens

import androidx.compose.foundation.background
import com.example.sparely.ui.utils.toInputString
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.sparely.ui.components.SparelySheetHeader
import com.example.sparely.ui.components.SheetActions
import com.example.sparely.ui.components.SparelyChip
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.SheetAmountField
import com.example.sparely.ui.components.SheetSectionLabel
import com.example.sparely.ui.components.ExpressiveEmptyState
import com.example.sparely.ui.components.segmentSpacing
import com.example.sparely.ui.components.segmentedShape
import com.example.sparely.ui.theme.ExpressiveShapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.sparely.domain.model.*
import com.example.sparely.domain.logic.BudgetEngine
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.components.SingleLineText
import com.example.sparely.ui.components.SparelyBottomSheet
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.state.SparelyUiState
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.critical
import com.example.sparely.ui.theme.criticalContainer
import com.example.sparely.ui.components.SparelyTextField
import com.example.sparely.ui.components.SparelyTonalButton
import com.example.sparely.ui.theme.spacing
import com.example.sparely.ui.theme.getCategoryColor
import com.example.sparely.ui.theme.getCategoryIcon
import com.example.sparely.ui.theme.onCriticalContainer
import com.example.sparely.ui.theme.onSuccessContainer
import com.example.sparely.ui.theme.onWarningContainer
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.theme.successContainer
import com.example.sparely.ui.theme.warning
import com.example.sparely.ui.theme.warningContainer
import com.example.sparely.ui.components.SparelyExpressiveDropdown
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import com.example.sparely.ui.utils.toSafeDouble
import com.example.sparely.ui.utils.filterAmountExpressionInput
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.utils.displayName
import com.sparely.app.R
import java.time.format.TextStyle
import java.util.Locale
@Composable
fun StatusBadge(status: BudgetHealthStatus) {
    val containerColor = when (status) {
        BudgetHealthStatus.HEALTHY -> MaterialTheme.colorScheme.successContainer
        BudgetHealthStatus.WARNING -> MaterialTheme.colorScheme.warningContainer
        BudgetHealthStatus.CRITICAL -> MaterialTheme.colorScheme.criticalContainer
        BudgetHealthStatus.OVER_BUDGET -> MaterialTheme.colorScheme.criticalContainer
    }
    val contentColor = when (status) {
        BudgetHealthStatus.HEALTHY -> MaterialTheme.colorScheme.onSuccessContainer
        BudgetHealthStatus.WARNING -> MaterialTheme.colorScheme.onWarningContainer
        BudgetHealthStatus.CRITICAL -> MaterialTheme.colorScheme.onCriticalContainer
        BudgetHealthStatus.OVER_BUDGET -> MaterialTheme.colorScheme.onCriticalContainer
    }
    Surface(
        color = containerColor,
        shape = CircleShape
    ) {
        Text(
            text = status.displayName(),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun EmptyBudgetState(onAddBudget: () -> Unit) {
    ExpressiveEmptyState(
        icon = MaterialSymbols.ACCOUNT_BALANCE_WALLET,
        title = stringResource(R.string.budget_create_first_title),
        description = stringResource(R.string.budget_create_first_desc),
        action = {
            SparelyButton(
                onClick = onAddBudget,
                icon = {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.ADD,
                        contentDescription = null,
                        size = 18.dp
                    )
                }
            ) {
                Text(text = stringResource(R.string.budget_create_first_button))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddBudgetDialog(
    onDismiss: () -> Unit,
    onConfirm: (BudgetInput) -> Unit,
    initialCategory: ExpenseCategory? = null
) {
    var selectedCategory by remember { mutableStateOf(initialCategory ?: ExpenseCategory.GROCERIES) }
    var amount by remember { mutableStateOf("") }
    val limit = amount.toSafeDouble()?.takeIf { it > 0 }
    val save = { limit?.let { onConfirm(BudgetInput(selectedCategory, it)) } ?: Unit }

    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SparelySheetHeader(
                title = stringResource(R.string.budget_set_budget_title),
                icon = getCategoryIcon(selectedCategory),
                iconTint = getCategoryColor(selectedCategory)
            )

            // Every category visible at once, tinted with its own colour
            SheetSectionLabel(stringResource(R.string.onboarding_financial_category_label))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExpenseCategory.entries.forEach { category ->
                    SparelyChip(
                        selected = category == selectedCategory,
                        onClick = { selectedCategory = category },
                        label = { Text(category.displayName()) },
                        leadingIcon = {
                            MaterialSymbolIcon(
                                icon = getCategoryIcon(category),
                                contentDescription = null,
                                size = 18.dp,
                                tint = getCategoryColor(category)
                            )
                        }
                    )
                }
            }

            SheetAmountField(
                value = amount,
                onValueChange = { amount = it.filterAmountExpressionInput() },
                label = stringResource(R.string.budget_monthly_limit_label),
                // The category comes first, so the keypad waits for a tap
                autoFocus = false,
                onImeAction = save
            )

            SheetActions(
                confirmLabel = stringResource(R.string.action_save),
                onConfirm = save,
                confirmEnabled = limit != null,
                dismissLabel = stringResource(R.string.cancel),
                onDismiss = onDismiss
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBudgetDialog(
    budget: CategoryBudget,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
    onDelete: () -> Unit
) {
    var amount by remember { mutableStateOf(budget.monthlyLimit.toInputString()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val limit = amount.toSafeDouble()?.takeIf { it > 0 }
    val save = { limit?.let(onConfirm) ?: Unit }

    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismiss
    ) {
        if (showDeleteConfirmation) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SparelySheetHeader(
                    title = stringResource(R.string.budget_delete_confirmation_title),
                    subtitle = stringResource(R.string.budget_delete_confirmation_desc, budget.category.displayName()) +
                        "\n" + stringResource(R.string.budget_delete_undone),
                    icon = MaterialSymbols.WARNING,
                    iconTint = MaterialTheme.colorScheme.error
                )
                SheetActions(
                    confirmLabel = stringResource(R.string.delete),
                    onConfirm = onDelete,
                    destructive = true,
                    dismissLabel = stringResource(R.string.cancel),
                    onDismiss = { showDeleteConfirmation = false }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SparelySheetHeader(
                    title = stringResource(R.string.budget_edit_budget_title, budget.category.displayName()),
                    icon = getCategoryIcon(budget.category),
                    iconTint = getCategoryColor(budget.category)
                )

                SheetAmountField(
                    value = amount,
                    onValueChange = { amount = it.filterAmountExpressionInput() },
                    label = stringResource(R.string.budget_monthly_limit_label),
                    autoFocus = false,
                    onImeAction = save
                )

                SheetActions(
                    confirmLabel = stringResource(R.string.save),
                    onConfirm = save,
                    confirmEnabled = limit != null,
                    dismissLabel = stringResource(R.string.cancel),
                    onDismiss = onDismiss
                )

                // Deleting is rarer and destructive, so it sits apart from Save
                SparelyTextButton(
                    onClick = { showDeleteConfirmation = true },
                    contentColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    icon = { MaterialSymbolIcon(icon = MaterialSymbols.DELETE, contentDescription = null, size = 18.dp) }
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}


@Composable
private fun BudgetHealthStatus.displayName(): String = when (this) {
    BudgetHealthStatus.HEALTHY -> stringResource(R.string.budget_status_on_track)
    BudgetHealthStatus.WARNING -> stringResource(R.string.budget_status_warning)
    BudgetHealthStatus.CRITICAL -> stringResource(R.string.budget_status_critical)
    BudgetHealthStatus.OVER_BUDGET -> stringResource(R.string.budget_status_over)
}




// Extension function for ExpenseCategory.displayName() - using the one from RecurringScreen.kt
// (It's defined as a public function there, so we can use it directly)
