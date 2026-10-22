# Ledger

Ledger **1.6.0** is an offline Android expense manager built with Kotlin and Jetpack Compose. It supports Android 9 and later and targets **Android 17 / API 37**. Import SBM Bank India and Axis Bank deposit-account SMS, or record income, expenses, and transfers manually. Credit card parsing is excluded.

## Features

- Monthly overview with Income, Spent, and Balance. A new month starts with zero activity; account balances retain their opening balances and transaction history.
- Tap Income, Spent, or a spending category to open matching transactions for the selected month. Back returns to the original screen.
- Graphs tab with category spending, daily spending bars, and six months of income versus spending.
- Recurring category budgets shared across every month. Spending resets each month; overspending appears as a negative remaining amount.
- Categories include Uber/Bus, Rent, Fuel, Food, Groceries, Transport, Bills, Health, Shopping, and Investment. Custom categories and remembered merchant rules are supported.
- Monthly summary and Budget home screen widgets that open the relevant screen when tapped.
- Light, dark, and system themes, collapsing large titles, spring sheets, push/pop navigation, and elastic scrolling.
- Local sender confirmation, duplicate detection, transfer matching, and encrypted backups.

## Getting started

1. In **Accounts**, map each bank's last four account digits. Set the opening date and balance immediately before that date. The opening date must cover transactions you intend to import.
2. In **SMS**, enable SMS access, choose a history range, and preview matching messages. The range filters receipt dates; transactions use dates extracted from alerts.
3. Confirm sender IDs and review incomplete or conflicting messages before saving. Sender confirmation does not prove authenticity.
4. Enable **Capture new bank alerts** for automatic capture. WorkManager and app resume reconcile missed alerts, subject to Android scheduling.
5. Use **+** for manual entries and transfers. Tap transactions to edit, categorize, or delete them.
6. In **Budgets**, set category limits once. The same limits apply to all months; changing a limit updates every month's comparison. Budget remaining measures spending in categories with active limits.
7. Add widgets through **Settings → Home screen**, or long-press your launcher and choose **Widgets → Ledger**. Resize widgets to fit your home screen.
8. Save an encrypted backup through **Settings**. Keep its password; it cannot be recovered.

## Bank SMS support

| Bank | Selected formats |
|---|---|
| SBM Bank India | UPI, NEFT, IMPS, IFT, ACH credits/debits, savings interest |
| Axis Bank | UPI, NEFT, RTGS, IMPS, merchant debits, completed mandate debits, UPI Lite top-ups |

Deposit-account refunds and reversals are included. Credit cards, OTPs, failed payments, payment requests, future mandates, mandate maintenance, and blocked-funds notices are excluded.

Automatic saving requires a confirmed sender, mapped account, positive amount, direction, date, and usable reference. Incomplete selected alerts remain in review. Missing references, account-less debits, interest, and wallet top-ups can require manual confirmation. SMS-reported balances are displayed separately from ledger calculations.

Amounts use integer paise in INR; dates use Asia/Kolkata. Transfers and deleted entries are excluded from income and spending reports. Missing alerts and cash activity require manual entries. Widgets use the current month, follow the device theme, and refresh when ledger data changes and during periodic Android updates.

## Privacy, backups, and upgrades

Ledger has no internet permission, analytics, cloud sync, or account service. Financial SMS and transactions remain on the device. Android automatic backup and device transfer are disabled; SMS links are never opened automatically.

Encrypted `.plbackup` files include accounts, transactions, financial SMS, budgets, rules, and import history. Encryption uses PBKDF2-HMAC-SHA256 and AES-256-GCM. Restoring validates the backup before replacing data and disables automatic capture until enabled again.

The package remains `in.thomas.pocketledger`, including upgrades from Pocket Ledger. Room database version 2 migrates old monthly budgets into recurring limits by keeping the latest saved limit per category. Older encrypted backups remain readable. Deleted-entry and dismissed-message identities survive rescans and backups.

## Build and install

Use **JDK 21**, SDK platform **37.0**, and build tools **37.0.0**. Configure `ANDROID_HOME` or an ignored `local.properties`, then run from the repository root:

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n in.thomas.pocketledger/.MainActivity
```

Version 1.6.0 uses versionCode **7**. Keep the same signing key when upgrading. Development APKs use debug signing; uninstalling removes app data.

Gradle configuration and local build caches are enabled. Use `assembleDebug` for routine builds and avoid `clean` unless diagnosing stale outputs. Dependency versions are pinned in the Gradle files; the wrapper verifies its distribution checksum.

## Development and validation

See [AGENTS.md](AGENTS.md) for structure and contributor conventions. Commit Room schema JSON from `app/schemas/`. Generated packages, device captures, databases, signing material, and financial backups are ignored by Git. Store local release snapshots in `artifacts/`.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat "-Pledger.testApplicationId=in.thomas.pocketledger.validation" :app:connectedDebugAndroidTest
```

Device tests use a separate application ID and synthetic data. Do not run instrumentation against the personal package: the runner may uninstall its target. Rebuild without the validation property before installing a personal APK.

Current validation: **49 JVM tests passed**, build and lint passed with **zero lint errors** (13 warnings), and device-test sources compiled. Android 17 checks covered installation, the income filter, negative budget amounts, the category chart, and monthly widget rendering. The latest instrumentation suite has not been run on a device.

Recurring transactions, receipt attachments, cloud synchronization, and Realbyte data migration are outside the current scope.
