# Privacy Policy for Sparely

**Last Updated:** October 13, 2026

## Overview

Sparely is committed to protecting your privacy. This privacy policy explains how the Sparely mobile application handles your data.

## Data Collection and Storage

### What Data is Collected

Sparely collects and stores the following data **locally on your device only**:

- **Financial Data:**
  - Expense records (amount, category, date, description, store)
  - Savings vault information (name, target amount, balance)
  - Budget settings and limits
  - Recurring expense schedules
  - Main account balance

- **User Settings:**
  - Display name and birthday (optional)
  - Monthly income
  - Savings preferences and percentages
  - Risk level and financial profile
  - Brandfetch API client ID (optional, if you choose to enable store logos)

- **App Configuration:**
  - Theme preferences
  - Notification settings
  - Onboarding completion status

### How Data is Stored

- **Local Storage Only:** All data is stored locally on your device using SQLite database (via Android Room)
- **No Cloud Sync:** Sparely does NOT automatically sync your data to any cloud service
- **No Server Communication:** Your financial data is NEVER sent to our servers or any third-party servers (except as noted below)

## Third-Party Services

### Brandfetch API (Optional)

If you choose to enable store logo fetching:
- You must provide your own Brandfetch API client ID
- When viewing expenses with store information, the app makes API calls to Brandfetch to fetch store logos
- Only the store website URL is sent to Brandfetch
- No personal financial data is sent to Brandfetch
- You can disable this feature at any time by removing your API key from Settings

### On-Device AI (Optional)

If you turn on On-device AI in Settings and your phone supports it:
- Sparely uses Gemini Nano, Google's AI model that runs entirely on your phone through Android AICore
- The model is downloaded once by Google Play services, only after you tap Download
- Your financial data is processed on the phone and is never sent to Google, to us or to anyone else
- You can turn it off at any time in Settings

**This is the ONLY external service that receives any data from the app, and it is entirely optional.**

### Payment Notification Detection (Optional)

If you turn on "Detect payments" in Settings, choose which apps to watch (for example Google Wallet, your bank or card app, or PayPal) and grant Sparely Notification access:
- Sparely reads only the notifications posted by the apps you chose, and only to find purchases (amount and store); every other app's notifications are ignored without being read
- It uses them to suggest logging the purchase; nothing is recorded until you tap Add or save it yourself
- Everything happens on your phone; notification content is never stored or sent anywhere
- You can change the apps, turn it off in Settings, or remove Notification access in Android's settings at any time

## Data You Control

### Export Your Data

You can export all your data at any time:
1. Go to Settings
2. Tap "Export Data"
3. Save the JSON file to your preferred location

The export includes ALL your data in a human-readable JSON format.

### Import Data

You can import previously exported data:
1. Go to Settings
2. Tap "Import Data"
3. Select your backup JSON file

### Delete Your Data

You can delete all your data:
1. Uninstall the Sparely app from your device
2. All locally stored data will be permanently deleted

Alternatively, use the "Reset History" option in Settings to clear specific data while keeping your settings.

## Data Security

- **Device Security:** Your data security depends on your device's security (lock screen, encryption, etc.)
- **No Transmission:** Since data is stored locally and not transmitted, there's no risk of data interception
- **Backup Encryption:** Exported backup files are in plain JSON format - you are responsible for securing these files

## Permissions

Sparely requests the following Android permissions:

- **INTERNET:** Required for optional Brandfetch API calls to fetch store logos
- **POST_NOTIFICATIONS:** Required to send you notifications about recurring expenses and vault transfers (optional, can be disabled)
- **Notification access (BIND_NOTIFICATION_LISTENER_SERVICE):** Only if you turn on payment detection; used to read purchase notifications from the apps you chose, on your phone
- **Installed apps list (queries):** Only to show which apps you can choose for payment detection

## Analytics and Tracking

**Sparely does NOT use any analytics or tracking services.** We do not collect:
- Usage statistics
- Crash reports
- Device information
- Location data
- Advertising identifiers

## Children's Privacy

Sparely is not directed at children under 13. We do not knowingly collect data from children under 13.

## Changes to This Policy

We may update this privacy policy from time to time. Changes will be reflected in the app's repository with an updated "Last Updated" date.

## Open Source

Sparely is open-source software. You can review the source code to verify these privacy practices:
[GitHub Repository](https://github.com/yourusername/Sparely)

## Contact

If you have questions about this privacy policy or data handling:
- Open an issue on [GitHub](https://github.com/yourusername/Sparely/issues)
- Email: [your-email@example.com]

## Your Rights

You have the right to:
- Access your data (via export feature)
- Delete your data (via app uninstall or reset)
- Control what data is collected (via app settings)
- Opt-out of optional features (Brandfetch integration, notifications)

## Consent

By using Sparely, you consent to this privacy policy. If you do not agree, please do not use the app.

---

**Summary:** Sparely stores all data locally on your device. No data is sent to external servers except optional store logo fetching via Brandfetch API. You have full control over your data with export, import, and delete capabilities.
