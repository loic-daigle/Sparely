# Sparely

**Sparely** is a modern Android personal finance and savings app built with Jetpack Compose. Track expenses, manage smart savings vaults, set budgets, and achieve your financial goals with intelligent allocation recommendations.

> 🤖 **Developed with AI**: This project was developed with the assistance of AI coding agents.


## ✨ Features

- 📊 **Smart Savings Allocation** - AI-powered recommendations based on your financial profile
- 💰 **Expense Tracking** - Category-based expense logging with automatic allocation
- 🎯 **Smart Vaults** - Goal-based savings with automated deposits and progress tracking
- 🔄 **Recurring Expenses** - Manage subscriptions and regular bills
- 📈 **Financial Analytics** - Comprehensive insights into spending patterns and savings rate
- 🏪 **Store/Merchant Tracking** - Track expenses by store with logo integration (via Brandfetch API)
- 📱 **Home Screen Widget** - Quick view of your savings and spending at a glance
- 💾 **Backup & Restore** - Export and import all your data in JSON format
- 🎨 **Material Design 3** - Modern, beautiful UI following Material You guidelines

## 🏗️ Architecture

- **UI Layer:** Jetpack Compose with Material 3
- **Database:** Room with SQLite for local persistence
- **Async Operations:** Kotlin Coroutines & Flow
- **Dependency Injection:** Manual DI via AppContainer pattern
- **State Management:** ViewModel + StateFlow
- **Background Tasks:** WorkManager for scheduled operations

## 🚀 Getting Started

### Prerequisites

- Android Studio (Hedgehog 2023.1.1 or later)
- JDK 11 or higher
- Android SDK (API 26+)

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/loic-daigle/Sparely.git
   cd Sparely
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned directory

3. **Sync Gradle**
   - Android Studio should automatically sync Gradle
   - If not, click "Sync Project with Gradle Files"

4. **Run the app**
   - Connect an Android device or start an emulator (API 26+)
   - Click the "Run" button or press `Shift + F10`

### Optional: Brandfetch Integration

To enable store logo fetching:

1. Get a free API key from [brandfetch.com](https://brandfetch.com)
2. In the app: Settings → Brandfetch Client ID
3. Enter your API key

## 🛠️ Tech Stack

| Category | Technology |
|----------|-----------|
| Language | Kotlin |
| UI Framework | Jetpack Compose |
| Database | Room (SQLite) |
| Design System | Material Design 3 |
| Image Loading | Coil |
| JSON Serialization | Gson |
| Background Work | WorkManager |
| Async | Coroutines + Flow |
| Navigation | Navigation Compose |

## 📦 Backup & Restore

**Export your data:**
1. Go to Settings
2. Tap "Export Data"
3. Choose a location to save the JSON file

**Import data:**
1. Go to Settings
2. Tap "Import Data"
3. Select your backup JSON file

The backup includes all expenses, vaults, budgets, stores, recurring expenses, and settings.

## 🤖 AI Assistant Access (AppFunctions)

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

## 🤝 Contributing

We welcome contributions! Please read our [Contributing Guidelines](CONTRIBUTING.md) and [Code of Conduct](CODE_OF_CONDUCT.md) before submitting pull requests.

### Development Workflow

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🔒 Privacy

Sparely stores all data locally on your device. No data is sent to external servers (except optional Brandfetch API calls for store logos). You have full control over your financial data with export/import capabilities.

## 🐛 Known Issues

- This is an early release - some features may have bugs
- See [Issues](https://github.com/loic-daigle/Sparely/issues) for known problems

## 📞 Support

- 🐛 [Report a bug](https://github.com/loic-daigle/Sparely/issues/new?template=bug_report.md)
- 💡 [Request a feature](https://github.com/loic-daigle/Sparely/issues/new?template=feature_request.md)
- 💬 [Discussions](https://github.com/loic-daigle/Sparely/discussions)

## 🙏 Acknowledgments

- Material Symbols icons from Google
- Brandfetch API for store logos
- The Android and Kotlin communities
- AI Coding Assistants for development support

---

**Note:** This app is for personal finance tracking and educational purposes. Always consult with financial professionals for important financial decisions.
