# AI Assistant Access (AppFunctions)

On Android 16+ (API 36), Sparely exposes [AppFunctions](https://developer.android.com/ai/appfunctions) so on-device assistants such as Gemini can help with your money:

- **Read:** spending summaries, expense search, budgets, vaults, account balances, upcoming bills and the wishlist ("How much did I spend on dining this month?").
- **Record:** new expenses and income ("I spent 42 dollars on groceries"). Assistants cannot edit or delete existing data.
- **Prepare:** vault deposits, vault withdrawals and refunds ("Put 200 in my trip vault"). These move no money: the assistant opens a Sparely confirmation screen, and nothing happens until you tap Confirm.

Both are **off by default**, under Settings > Security:
- "Allow AI assistants" turns on reading.
- "Let assistants add entries" also allows recording entries and preparing transfers and refunds.

If biometric unlock is on, turning either on requires authentication. Until a setting is on, the matching calls fail with an error that tells the assistant which setting to ask the user to enable.

Every entry an assistant adds:
- goes through the same code as entering it in the app, so balances, vaults and saving tax stay consistent
- is logged and shows a notification with **Undo**; the last 10 also appear under Settings > Security with an Undo button
- is refused if an identical one was added in the last 5 minutes, since assistants sometimes retry

Every transfer or refund an assistant prepares:
- moves money only after you confirm it on Sparely's confirmation screen, which asks for biometric unlock first if app lock is on
- is checked again against your current balances when the screen opens and when you confirm
- expires after 30 minutes and can only be carried out once, even if the link is opened again

Code:
- Functions and their descriptions: `appfunctions/SparelyAppFunctionServiceBase.kt` (the KDoc is what the assistant reads)
- Read logic: `appfunctions/SparelyAssistantQueries.kt`; write logic: `appfunctions/SparelyAssistantActions.kt`
- Undo: `domain/usecase/UndoAssistantActionUseCase.kt`
- Transfers and refunds: `appfunctions/AssistantTransfers.kt` (validation), `AssistantTransferExecutor.kt` and `AssistantConfirmActivity.kt`

**Testing:**
- Unit tests (JVM): `./gradlew testDebugUnitTest --tests "com.example.sparely.appfunctions.*"`
- End-to-end on an API 36+ emulator or device: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.sparely.appfunctions.SparelyAppFunctionsInstrumentedTest`
- Check the functions are registered: `adb shell cmd app_function list-app-functions`
