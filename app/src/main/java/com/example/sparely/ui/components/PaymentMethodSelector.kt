package com.example.sparely.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.theme.MaterialSymbolIcon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.PaymentMethod
import com.example.sparely.domain.model.PaymentMethodType
import com.example.sparely.ui.theme.warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodSelector(
    paymentMethods: List<PaymentMethod>,
    selectedMethod: PaymentMethod?,
    onMethodSelected: (PaymentMethod?) -> Unit,
    onManageMethods: () -> Unit = {},
    expenseAmount: Double = 0.0 // Optional: to show projected utilization
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Payment Method", style = MaterialTheme.typography.titleSmall)
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            SparelyDropdownAnchor(
                value = selectedMethod?.name ?: "Select payment method",
                onValueChange = {},
                expanded = expanded,
                label = "Payment Method",
                leadingIcon = selectedMethod?.let { method -> { PaymentMethodIcon(method = method) } },
                modifier = Modifier.fillMaxWidth()
            )

            SparelyDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                paymentMethods.forEach { method ->
                    SparelyDropdownMenuItem(
                        text = { 
                            Column {
                                Text(method.name)
                                if (method.isCreditCard) {
                                    val utilizationPercentStr = method.utilizationPercent.formatPercent()
                                    Text(
                                        "Balance: ${method.currentBalance.formatCurrency()} ($utilizationPercentStr used)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            method.isUtilizationHealthy -> MaterialTheme.colorScheme.onSurfaceVariant
                                            method.isUtilizationWarning -> MaterialTheme.colorScheme.warning
                                            else -> MaterialTheme.colorScheme.error
                                        }
                                    )
                                }
                            }
                        },
                        leadingIcon = { PaymentMethodIcon(method = method) },
                        isSelected = method == selectedMethod,
                        onClick = {
                            onMethodSelected(method)
                            expanded = false
                        }
                    )
                }
                
                if (paymentMethods.isNotEmpty()) {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                
                SparelyDropdownMenuItem(
                    text = { Text("Manage methods") },
                    leadingIcon = { MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null) },
                    onClick = {
                        onManageMethods()
                        expanded = false
                    }
                )
            }
        }
        
        // Show credit card info and warnings when a credit card is selected
        selectedMethod?.let { method ->
            if (method.isCreditCard) {
                val currentUtilization = method.utilizationPercent
                val projectedBalance = method.currentBalance + expenseAmount
                val projectedUtilization = if (method.creditLimit != null && method.creditLimit > 0) {
                    projectedBalance / method.creditLimit
                } else 0.0
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Current Balance", style = MaterialTheme.typography.bodySmall)
                            Text(
                                method.currentBalance.formatCurrency(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Available Credit", style = MaterialTheme.typography.bodySmall)
                            Text(
                                method.availableCredit.formatCurrency(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Current Utilization", style = MaterialTheme.typography.bodySmall)
                            val utilizationColor = when {
                                method.isUtilizationHealthy -> MaterialTheme.colorScheme.primary
                                method.isUtilizationWarning -> MaterialTheme.colorScheme.warning
                                else -> MaterialTheme.colorScheme.error
                            }
                            Text(
                                currentUtilization.formatPercent(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = utilizationColor
                            )
                        }
                        
                        // Show projected utilization warning if expense would push utilization too high
                        if (expenseAmount > 0 && projectedUtilization > 0.30 && !method.isUtilizationDanger) {
                            Spacer(modifier = Modifier.size(4.dp))
                            val projectedColor = when {
                                projectedUtilization <= 0.30 -> MaterialTheme.colorScheme.primary
                                projectedUtilization <= 0.50 -> MaterialTheme.colorScheme.warning
                                else -> MaterialTheme.colorScheme.error
                            }
                            Text(
                                "⚠️ After this expense: ${projectedUtilization.formatPercent()} utilization",
                                style = MaterialTheme.typography.bodySmall,
                                color = projectedColor
                            )
                        }
                        
                        // Show warning/tip based on utilization level
                        if (method.isUtilizationWarning || method.isUtilizationDanger) {
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                if (method.isUtilizationDanger)
                                    "⚠️ High utilization may hurt your credit score"
                                else
                                    "💡 Keep utilization under 30% for best credit score",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (method.isUtilizationDanger) 
                                    MaterialTheme.colorScheme.error 
                                else 
                                    MaterialTheme.colorScheme.warning
                            )
                        }
                        
                        // Warning if expense would exceed credit limit
                        if (expenseAmount > 0 && method.creditLimit != null && projectedBalance > method.creditLimit) {
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                "🚫 This expense would exceed your credit limit!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentMethodIcon(method: PaymentMethod, modifier: Modifier = Modifier) {
    val iconRes = when {
        method.type == PaymentMethodType.CASH -> MaterialSymbols.PAYMENTS
        method.type == PaymentMethodType.CARD -> MaterialSymbols.CREDIT_CARD
        else -> MaterialSymbols.CREDIT_CARD
    }
    
    MaterialSymbolIcon(
        icon = iconRes,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}
