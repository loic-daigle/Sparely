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
 * the EXECUTE_APP_FUNCTIONS permission.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 36)
class SparelyAppFunctionsInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = (context as SparelyApplication).container.preferencesRepository
    private val appFunctionManager = checkNotNull(AppFunctionManager.getInstance(context))
    private var accessWasEnabled = false

    @Before
    fun rememberAccessSetting() = runBlocking {
        accessWasEnabled = preferences.getSettingsSnapshot().aiAssistantAccessEnabled
    }

    @After
    fun restoreAccessSetting() = runBlocking {
        preferences.updateAiAssistantAccessEnabled(accessWasEnabled)
    }

    @Test
    fun allReadOnlyFunctionsAreRegistered() = runBlocking {
        val ids = searchOwnFunctions().map { it.id }.toSet()

        listOf(
            SparelyAppFunctionService.FUNCTION_ID_GET_SPENDING_SUMMARY,
            SparelyAppFunctionService.FUNCTION_ID_SEARCH_EXPENSES,
            SparelyAppFunctionService.FUNCTION_ID_GET_BUDGET_STATUS,
            SparelyAppFunctionService.FUNCTION_ID_LIST_VAULTS,
            SparelyAppFunctionService.FUNCTION_ID_GET_ACCOUNT_BALANCES,
            SparelyAppFunctionService.FUNCTION_ID_LIST_UPCOMING_BILLS,
            SparelyAppFunctionService.FUNCTION_ID_LIST_WISHLIST,
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
}
