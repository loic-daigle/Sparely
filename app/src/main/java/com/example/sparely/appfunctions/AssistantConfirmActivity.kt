package com.example.sparely.appfunctions

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.sparely.SparelyApplication
import com.example.sparely.ui.theme.SparelyTheme
import com.sparely.app.R
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency

/**
 * Where the user approves a money movement an AI assistant proposed. Opened only through the
 * one-time PendingIntent an AppFunction returns (not exported). Asks for the app lock first when
 * it is on, re-checks the request against current data, and moves money only on "Confirm".
 */
class AssistantConfirmActivity : FragmentActivity() {

    private sealed interface UiState {
        data object Loading : UiState
        data class Ready(val plan: PlannedTransfer) : UiState
        data object Working : UiState
        data object Done : UiState
        data class Failed(val message: String) : UiState
    }

    private var state by mutableStateOf<UiState>(UiState.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A confirmation button is a tapjacking target: ignore taps while another window covers us.
        window.decorView.filterTouchesWhenObscured = true

        val request = AssistantMoneyRequestIntents.readFrom(intent)
        if (request == null) {
            finish()
            return
        }
        val container = (application as SparelyApplication).container
        val executor = AssistantTransferExecutor(this, container)
        val planner = AssistantTransferPlanner(RepositoryAssistantTransferGateway(container))

        setContent {
            SparelyTheme {
                ConfirmScreen(
                    state = state,
                    onConfirm = { plan ->
                        state = UiState.Working
                        lifecycleScope.launch {
                            state = try {
                                executor.execute(plan.request)
                                UiState.Done
                            } catch (e: Exception) {
                                UiState.Failed(e.message ?: getString(R.string.assistant_confirm_failed))
                            }
                        }
                    },
                    onClose = { finish() }
                )
            }
        }

        lifecycleScope.launch {
            val settings = container.preferencesRepository.getSettingsSnapshot()
            val load = {
                lifecycleScope.launch {
                    state = try {
                        UiState.Ready(planner.describe(request))
                    } catch (e: Exception) {
                        UiState.Failed(e.message ?: getString(R.string.assistant_confirm_failed))
                    }
                }
            }
            // Assistants never pass the app lock, so approving money moves must.
            if (settings.biometricEnabled) {
                authenticate { success -> if (success) load() else finish() }
            } else {
                load()
            }
        }
    }

    // Same behaviour as the app lock in MainActivity: no usable biometric/credential means no lock.
    private fun authenticate(onResult: (Boolean) -> Unit) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            onResult(true)
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.auth_confirm_identity))
                .setSubtitle(getString(R.string.assistant_confirm_auth_subtitle))
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    @Composable
    private fun ConfirmScreen(state: UiState, onConfirm: (PlannedTransfer) -> Unit, onClose: () -> Unit) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(24.dp)) {
                Column(
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(stringResource(R.string.assistant_confirm_title), style = MaterialTheme.typography.titleLarge)
                    when (state) {
                        UiState.Loading, UiState.Working -> CircularProgressIndicator()
                        is UiState.Ready -> {
                            PlanDetails(state.plan)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                                TextButton(onClick = onClose) { Text(stringResource(R.string.assistant_confirm_cancel)) }
                                Button(onClick = { onConfirm(state.plan) }) { Text(stringResource(R.string.assistant_confirm_button)) }
                            }
                        }
                        UiState.Done -> {
                            Text(stringResource(R.string.assistant_confirm_success), style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                                Text(stringResource(R.string.assistant_confirm_close))
                            }
                        }
                        is UiState.Failed -> {
                            Text(state.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                                Text(stringResource(R.string.assistant_confirm_close))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PlanDetails(plan: PlannedTransfer) {
        val request = plan.request
        val amount = formatMoney(request.amount, plan.currencyCode)
        val action = when (request.kind) {
            AssistantMoneyRequest.Kind.VAULT_DEPOSIT -> stringResource(R.string.assistant_confirm_deposit, amount, plan.targetName)
            AssistantMoneyRequest.Kind.VAULT_WITHDRAWAL -> stringResource(R.string.assistant_confirm_withdrawal, amount, plan.targetName)
            AssistantMoneyRequest.Kind.REFUND -> stringResource(R.string.assistant_confirm_refund, amount, plan.targetName)
        }
        val mainAccount = when {
            !plan.affectsMainAccount -> R.string.assistant_confirm_main_unchanged
            request.kind == AssistantMoneyRequest.Kind.VAULT_DEPOSIT -> R.string.assistant_confirm_main_debited
            else -> R.string.assistant_confirm_main_credited
        }
        Text(action, style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(mainAccount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        request.reason?.let {
            Text(stringResource(R.string.assistant_confirm_reason, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    private fun formatMoney(amount: Double, currencyCode: String): String {
        val format = NumberFormat.getCurrencyInstance()
        runCatching { format.currency = Currency.getInstance(currencyCode) }
        return format.format(amount)
    }
}
