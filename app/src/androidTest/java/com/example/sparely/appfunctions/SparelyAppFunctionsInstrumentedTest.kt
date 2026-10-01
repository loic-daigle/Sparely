package com.example.sparely.appfunctions

import android.content.Context
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionDisabledException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.example.sparely.SparelyApplication
import com.example.sparely.domain.usecase.UndoAssistantActionUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Calls Sparely's AppFunctions end to end through the platform, the same way an assistant does.
 * Needs an Android 16+ (API 36) device or emulator; an app may call its own functions without
 * the EXECUTE_APP_FUNCTIONS permission. Runs against the app's real database: the write tests
 * undo the income they add and remove the temporary vault they create, leaving balances as they were.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 36)
class SparelyAppFunctionsInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = (context as SparelyApplication).container.preferencesRepository
    private val appFunctionManager = checkNotNull(AppFunctionManager.getInstance(context))
    private var accessWasEnabled = false
    private var writeWasEnabled = false

    @Before
    fun rememberAccessSettings() = runBlocking {
        val settings = preferences.getSettingsSnapshot()
        accessWasEnabled = settings.aiAssistantAccessEnabled
        writeWasEnabled = settings.aiAssistantWriteEnabled
    }

    @After
    fun restoreAccessSettings() = runBlocking {
        preferences.updateAiAssistantAccessEnabled(accessWasEnabled)
        preferences.updateAiAssistantWriteEnabled(writeWasEnabled)
    }

    @Test
    fun allFunctionsAreRegistered() = runBlocking {
        val ids = searchOwnFunctions().map { it.id }.toSet()

        listOf(
            SparelyAppFunctionService.FUNCTION_ID_GET_SPENDING_SUMMARY,
            SparelyAppFunctionService.FUNCTION_ID_SEARCH_EXPENSES,
            SparelyAppFunctionService.FUNCTION_ID_GET_BUDGET_STATUS,
            SparelyAppFunctionService.FUNCTION_ID_LIST_VAULTS,
            SparelyAppFunctionService.FUNCTION_ID_GET_ACCOUNT_BALANCES,
            SparelyAppFunctionService.FUNCTION_ID_LIST_UPCOMING_BILLS,
            SparelyAppFunctionService.FUNCTION_ID_LIST_WISHLIST,
            SparelyAppFunctionService.FUNCTION_ID_RECORD_EXPENSE,
            SparelyAppFunctionService.FUNCTION_ID_RECORD_INCOME,
            SparelyAppFunctionService.FUNCTION_ID_PREPARE_VAULT_DEPOSIT,
            SparelyAppFunctionService.FUNCTION_ID_PREPARE_VAULT_WITHDRAWAL,
            SparelyAppFunctionService.FUNCTION_ID_PREPARE_REFUND,
        ).forEach { assertTrue("$it not registered; found $ids", it in ids) }
    }

    @Test
    fun callsAreRefusedWhileAccessIsOff() = runBlocking {
        preferences.updateAiAssistantAccessEnabled(false)

        val response = execute(SparelyAppFunctionService.FUNCTION_ID_GET_ACCOUNT_BALANCES)

        val error = (response as ExecuteAppFunctionResponse.Error).error
        assertTrue("Unexpected error $error", error is AppFunctionDisabledException)
    }

    @Test
    fun accountBalancesMatchTheAppOnceAccessIsOn() = runBlocking {
        preferences.updateAiAssistantAccessEnabled(true)
        val repository = (context as SparelyApplication).container.savingsRepository

        val response = execute(SparelyAppFunctionService.FUNCTION_ID_GET_ACCOUNT_BALANCES)

        val balances = (response as ExecuteAppFunctionResponse.Success).returnValue
            .getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE)
            ?.deserialize(AccountBalances::class.java)
        assertNotNull(balances)
        assertEquals(repository.getLatestMainAccountBalance(), balances!!.mainAccountBalance, 0.01)
        assertEquals(preferences.getSettingsSnapshot().regionalSettings.currencyCode, balances.currencyCode)
    }

    @Test
    fun invalidArgumentsComeBackAsInvalidArgumentErrors() = runBlocking {
        preferences.updateAiAssistantAccessEnabled(true)

        val response = execute(SparelyAppFunctionService.FUNCTION_ID_GET_BUDGET_STATUS) {
            setString("month", "March")
        }

        val error = (response as ExecuteAppFunctionResponse.Error).error
        assertTrue("Unexpected error $error", error is AppFunctionInvalidArgumentException)
    }

    @Test
    fun recordingIsRefusedUntilWritesAreAllowed() = runBlocking {
        preferences.updateAiAssistantAccessEnabled(true)
        preferences.updateAiAssistantWriteEnabled(false)
        val repository = (context as SparelyApplication).container.savingsRepository
        val balanceBefore = repository.getLatestMainAccountBalance()

        val response = execute(SparelyAppFunctionService.FUNCTION_ID_RECORD_INCOME) {
            setDouble("amount", 12.34)
            setString("description", "Instrumented test income")
        }

        val error = (response as ExecuteAppFunctionResponse.Error).error
        assertTrue("Unexpected error $error", error is AppFunctionDisabledException)
        assertEquals(balanceBefore, repository.getLatestMainAccountBalance(), 0.001)
    }

    @Test
    fun recordedIncomeIsLoggedAndUndoRestoresTheBalance() = runBlocking {
        preferences.updateAiAssistantAccessEnabled(true)
        preferences.updateAiAssistantWriteEnabled(true)
        val container = (context as SparelyApplication).container
        val repository = container.savingsRepository
        val balanceBefore = repository.getLatestMainAccountBalance()
        // Unique so the duplicate guard never trips across test runs.
        val description = "Instrumented test income ${System.nanoTime()}"

        val response = execute(SparelyAppFunctionService.FUNCTION_ID_RECORD_INCOME) {
            setDouble("amount", 12.34)
            setString("description", description)
        }

        val entry = (response as ExecuteAppFunctionResponse.Success).returnValue
            .getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE)
            ?.deserialize(RecordedEntry::class.java)
        assertNotNull(entry)
        assertEquals(balanceBefore + 12.34, repository.getLatestMainAccountBalance(), 0.001)

        val action = container.assistantActionRepository.actionsSince(0L)
            .first { it.description == description }
        assertEquals(entry!!.id, action.recordId)

        val undo = UndoAssistantActionUseCase(repository, preferences, container.assistantActionRepository)
        assertEquals(UndoAssistantActionUseCase.Outcome.UNDONE, undo(action.id))
        assertEquals(balanceBefore, repository.getLatestMainAccountBalance(), 0.001)
        // A second tap on Undo must not withdraw the money again.
        assertEquals(UndoAssistantActionUseCase.Outcome.ALREADY_UNDONE, undo(action.id))
        assertEquals(balanceBefore, repository.getLatestMainAccountBalance(), 0.001)
    }

    @Test
    fun preparingAVaultDepositMovesNoMoney() = runBlocking {
        preferences.updateAiAssistantAccessEnabled(true)
        preferences.updateAiAssistantWriteEnabled(true)
        withTemporaryVault { vaultId, repository ->
            val response = execute(SparelyAppFunctionService.FUNCTION_ID_PREPARE_VAULT_DEPOSIT) {
                setLong("vaultId", vaultId)
                setDouble("amount", 1.0)
            }

            val confirmation = (response as ExecuteAppFunctionResponse.Success).returnValue
                .getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE)
                ?.deserialize(PendingConfirmation::class.java)
            assertNotNull(confirmation)
            assertTrue(confirmation!!.summary.contains(TEST_VAULT_NAME))
            assertEquals(0.0, repository.getSmartVaultById(vaultId)!!.currentBalance, 0.001)
        }
    }

    @Test
    fun aConfirmedRequestRunsOnlyOnce() = runBlocking {
        preferences.updateAiAssistantAccessEnabled(true)
        preferences.updateAiAssistantWriteEnabled(true)
        val container = (context as SparelyApplication).container
        withTemporaryVault { vaultId, repository ->
            val plan = AssistantTransferPlanner(RepositoryAssistantTransferGateway(container))
                .planVaultDeposit(vaultId, 1.0, null)
            val executor = AssistantTransferExecutor(context, container)

            executor.execute(plan.request)
            assertEquals(1.0, repository.getSmartVaultById(vaultId)!!.currentBalance, 0.001)

            // Opening the same confirmation link again must not deposit twice.
            val replay = runCatching { executor.execute(plan.request) }.exceptionOrNull()
            assertTrue("Unexpected result $replay", replay is AssistantInvalidArgumentException)
            assertEquals(1.0, repository.getSmartVaultById(vaultId)!!.currentBalance, 0.001)
        }
    }

    /**
     * Runs [block] with an empty vault whose transfers never touch the main account, then removes
     * it without moving money, so the test leaves the user's balances as they were.
     */
    private suspend fun withTemporaryVault(
        block: suspend (vaultId: Long, repository: com.example.sparely.data.repository.SavingsRepository) -> Unit
    ) {
        val repository = (context as SparelyApplication).container.savingsRepository
        repository.upsertSmartVault(
            com.example.sparely.domain.model.SmartVault(
                name = TEST_VAULT_NAME,
                targetAmount = 100.0,
                defaultManualDepositDeductFromMain = false,
                defaultManualWithdrawalCreditMain = false
            )
        )
        val vaultId = repository.observeAllSmartVaults().first().first { it.name == TEST_VAULT_NAME }.id
        try {
            block(vaultId, repository)
        } finally {
            val balance = repository.getSmartVaultById(vaultId)?.currentBalance ?: 0.0
            if (balance > 0.0) repository.deductFromVault(vaultId, balance, "Test cleanup", creditMainAccount = false)
            repository.deleteSmartVault(vaultId)
        }
    }

    private suspend fun searchOwnFunctions(): List<AppFunctionMetadata> =
        appFunctionManager.searchAppFunctions(AppFunctionSearchSpec(packageNames = setOf(context.packageName)))

    private suspend fun execute(
        functionId: String,
        parameters: AppFunctionData.Builder.() -> Unit = {}
    ): ExecuteAppFunctionResponse {
        val metadata = searchOwnFunctions().single { it.id == functionId }
        val data = AppFunctionData.Builder(metadata.parameters, metadata.packageMetadata.components)
            .apply(parameters)
            .build()
        return appFunctionManager.executeAppFunction(
            ExecuteAppFunctionRequest(context.packageName, functionId, data)
        )
    }

    private companion object {
        const val TEST_VAULT_NAME = "AppFunctions instrumented test vault"
    }
}
