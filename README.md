<p align="center"><img src="docs/branding/sparely-banner.png" alt="Sparely: save the small stuff." width="100%"></p>

<p align="center">
  <a href="https://github.com/loic-daigle/Sparely/releases/latest"><img src="https://img.shields.io/github/v/release/loic-daigle/Sparely?label=download&color=0F3D34" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-8FE3C0" alt="Android 8.0 or newer">
  <img src="https://img.shields.io/badge/languages-English%20%7C%20Fran%C3%A7ais-F4CB6A" alt="English and French">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/loic-daigle/Sparely?color=9DBDB2" alt="License"></a>
</p>

**Sparely** is a personal finance and savings app for Android. Log what you spend, set money aside in
savings vaults, keep bills and budgets in check, and decide whether a purchase fits before you make it.
Everything stays on your phone: no account, no ads, no cloud.

> 🤖 **Developed with AI**: this project was developed with the help of AI coding agents.

## 📥 Download

1. Open the [latest release](https://github.com/loic-daigle/Sparely/releases/latest) on your phone.
2. Under **Assets**, tap the `.apk` file to download it.
3. Open it and allow your browser or file manager to install apps when Android asks.

Needs Android 8.0 or newer. Your data lives only on your phone, so export a backup
(Settings → Data) before uninstalling.

## 📸 Screenshots

<!-- The gallery shows once the images are in docs/screenshots/ (see the list there): remove this
     comment's opening and closing lines to turn it on.
<table>
  <tr>
    <td align="center"><img src="docs/screenshots/dashboard.png" width="240" alt="Home"><br><sub>Home</sub></td>
    <td align="center"><img src="docs/screenshots/add-expense.png" width="240" alt="Adding an expense"><br><sub>Add an expense</sub></td>
    <td align="center"><img src="docs/screenshots/history.png" width="240" alt="Expense history"><br><sub>History</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/vaults.png" width="240" alt="Savings vaults"><br><sub>Savings vaults</sub></td>
    <td align="center"><img src="docs/screenshots/budgets.png" width="240" alt="Budgets"><br><sub>Budgets</sub></td>
    <td align="center"><img src="docs/screenshots/recurring.png" width="240" alt="Recurring bills"><br><sub>Recurring bills</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/afford-it.png" width="240" alt="Can I afford it?"><br><sub>Can I afford it?</sub></td>
    <td align="center"><img src="docs/screenshots/insights.png" width="240" alt="Insights"><br><sub>Insights</sub></td>
    <td align="center"><img src="docs/screenshots/payment-suggestion.png" width="240" alt="Purchase detected from a payment notification"><br><sub>Purchase detection</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/widget.png" width="240" alt="Home screen widget"><br><sub>Widget</sub></td>
    <td align="center"><img src="docs/screenshots/dark.png" width="240" alt="Dark theme"><br><sub>Dark theme</sub></td>
    <td></td>
  </tr>
</table>
-->

*Screenshots are coming soon.*

## ✨ Features

**Spending**
- 💸 **Fast expense entry**: big amount field with a calculator (`12.50+8.99`), categories, stores with logos, notes and itemized receipts
- 🔔 **Purchase detection** (optional): when Google Wallet, your bank or card app, or PayPal reports a purchase, Sparely offers to log it with one tap, using the payment method you set for that app
- 🧾 **History**: search, filters by period, category and store, refunds and per-store totals
- 💳 **Payment methods and credit cards**: track card balances, limits and statement payments

**Saving**
- 🏦 **Smart vaults**: goal-based savings with automatic deposits and progress
- 💰 **Savings accounts**: interest, deposits and withdrawals
- 🎯 **Smart allocation**: a share of what you spend or earn goes to your savings automatically

**Planning**
- 📅 **Recurring bills and subscriptions**, including bills whose amount changes, which you confirm each time
- 📊 **Budgets** by category
- 🛍️ **"Can I afford it?" and a wishlist**: a verdict for a purchase, and a cooling-off period for wishes
- ❤️ **Financial health score** and insights into your spending patterns

**Everywhere**
- 🧠 **On-device AI** (optional, supported phones): plain-language summaries and answers that never leave the phone ([details](docs/ON_DEVICE_AI.md))
- 🗣️ **Assistant access** (optional, Android 16+): ask Gemini about your spending or to log an expense ([details](docs/AI_ASSISTANTS.md))
- 📱 **Home screen widget**
- 🔒 **App lock** with fingerprint or face unlock
- 💾 **Backups** of absolutely everything, by hand or on a schedule
- 🇨🇦 **English and French**, with Sparely's own colors or your wallpaper's (Material You)

## 🔒 Privacy

Sparely has no account and no server. Everything you enter is stored only on your phone. The only
optional network use is fetching store logos from Brandfetch, with your own key. Purchase detection
reads only the notifications of the apps you pick, on the phone. See [PRIVACY.md](PRIVACY.md).

## 🧑‍💻 For developers

### 🏗️ Architecture

- **UI Layer:** Jetpack Compose with Material 3
- **Database:** Room with SQLite for local persistence
- **Async Operations:** Kotlin Coroutines & Flow
- **Dependency Injection:** Manual DI via AppContainer pattern
- **State Management:** ViewModel + StateFlow
- **Background Tasks:** WorkManager for scheduled operations

### 🚀 Building from source

#### Prerequisites

- Android Studio (Hedgehog 2023.1.1 or later)
- JDK 11 or higher
- Android SDK (API 26+)

#### Setup

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

#### Optional: Brandfetch integration

To enable store logo fetching:

1. Get a free API key from [brandfetch.com](https://brandfetch.com)
2. In the app: Settings → Brandfetch Client ID
3. Enter your API key

### 🛠️ Tech stack

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

### 📦 Backup & restore

**Export your data:**
1. Go to Settings
2. Tap "Export Data"
3. Choose a location to save the JSON file

**Import data:**
1. Go to Settings
2. Tap "Import Data"
3. Select your backup JSON file

The backup includes all expenses, vaults, budgets, stores, recurring expenses, and settings.

### More docs

- [On-device AI (Gemini Nano)](docs/ON_DEVICE_AI.md): supported phones, setup on the Pixel 9a, code and tests
- [AI assistant access (AppFunctions)](docs/AI_ASSISTANTS.md): what assistants can read and do, safeguards, code and tests
- [Screenshots](docs/screenshots/README.md): which screenshots the README uses and how to update them
- `scripts/generate_icons.py` and `scripts/generate_banner.py`: launcher icons and the README banner

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
