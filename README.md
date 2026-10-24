# Ledger

Ledger **1.7.0** is an offline Android expense manager built with Kotlin and Jetpack Compose. It supports Android 9 and later and targets **Android 17 / API 37**. Import SBM Bank India and Axis Bank deposit-account SMS, or record income, expenses, and transfers manually. Credit card parsing is excluded.

## Features

- Monthly overview with Income, Spent, and Balance. A new month starts with zero activity; account balances retain their opening balances and transaction history.
- Tap Income, Spent, or a spending category to open matching transactions for the selected month. Back returns to the original screen.
- Insights from Overview, with category spending, daily spending inspection, an accessible data list, and six months of income versus spending.
- Recurring category budgets shared across every month. Spending resets each month; overspending appears as a negative remaining amount.
- Separate category lists offer 30 spending options and 15 income options, including Utilities, Education, Travel, Salary, Freelance, Interest and Refunds. Custom categories are reused by transaction type; remembered payee rules keep income and spending separate. Budgets use spending categories.
- A separate 50/30/20 plan splits entered monthly income into needs, wants, and savings targets; it does not represent confirmed savings or change category limits.
- A compact monthly widget switches between Income and Expenses and remembers the selection per widget. Tapping its total opens the matching transactions; the Budget widget shows recurring-limit remaining amounts.
- Material 3 dynamic color, light/dark themes, high contrast and reduced motion; adaptive navigation, predictive back, native spring sheets and standard stretch scrolling.
- Overview, Activity, Budgets, and Inbox navigation on phones; a rail and optional transaction editor pane on wider windows. Inbox supports pull-to-check and swipe dismissal with Undo.
- Local sender confirmation, duplicate detection, transfer matching, and encrypted backups.
- Newly saved SMS transactions that need a category show an **Add a category** notification. Tapping opens the transaction editor; duplicates, matched transfers, and remembered categories do not produce reminders.

## Getting started

1. In **Manage / Accounts**, map each bank's last four account digits. Set the opening date and balance immediately before that date. The opening date must cover transactions you intend to import.
2. In **Inbox**, enable SMS access, choose a history range, and preview matching messages. The range filters receipt dates; transactions use dates extracted from alerts.
3. Confirm sender IDs and review incomplete or conflicting messages before saving. Sender confirmation does not prove authenticity.
4. Enable **Capture new bank alerts** for automatic capture. Allow notifications when prompted to receive category reminders. WorkManager and app resume reconcile missed alerts, subject to Android scheduling.
5. Use **+** for manual entries and transfers. Tap transactions to edit, categorize, or delete them.
6. In **Budgets**, set category limits once. The same limits apply to all months; changing a limit updates every month's comparison. Budget remaining measures spending in categories with active limits.
7. Add widgets through **Settings → Home screen**, or long-press your launcher and choose **Widgets → Ledger**. Resize widgets to fit your home screen.
8. Save an encrypted backup through **Settings**. Keep its password; it cannot be recovered.

Category separation preserves existing transactions and category names. Backups now use format 2
for separate income/spending payee rules; format 1 backups still restore. Older app versions cannot
restore format 2 backups.

## Bank SMS support

| Bank | Selected formats |
|---|---|
| SBM Bank India | UPI, NEFT, IMPS, IFT, ACH credits/debits, savings interest |
| Axis Bank | UPI, NEFT, RTGS, IMPS, merchant debits, completed mandate debits, UPI Lite top-ups |

Deposit-account refunds and reversals are included. Credit cards, OTPs, failed payments, payment requests, future mandates, mandate maintenance, and blocked-funds notices are excluded.

Automatic saving requires a confirmed sender, mapped account, positive amount, direction, date, and usable reference. Incomplete selected alerts remain in review. Missing references, account-less debits, interest, and wallet top-ups can require manual confirmation. SMS-reported balances are displayed separately from ledger calculations.

Amounts use integer paise in INR; dates use Asia/Kolkata. Transfers and deleted entries are excluded from income and spending reports. Missing alerts and cash activity require manual entries. Widgets use the current month and refresh when ledger data changes and during periodic Android updates. Both widgets use frosted surfaces that follow the device theme. Monthly summary keeps its Income/Expenses selection per widget; taller widgets reveal month context, and Budget also reveals usage details. Monthly summary needs at least 112dp height for its touch targets. Frosting uses translucent gradients and highlights; it does not blur the launcher wallpaper. Very large amounts may shorten on compact widgets; accessibility descriptions retain the full values.

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

Version 1.7.0 uses versionCode **8**. Keep the same signing key when upgrading. Development APKs use debug signing; uninstalling removes app data.

Gradle configuration and local build caches are enabled. Use `assembleDebug` for routine builds and avoid `clean` unless diagnosing stale outputs. Dependency versions are pinned in the Gradle files; the wrapper verifies its distribution checksum.

## Development and validation

See [AGENTS.md](AGENTS.md) for contributor conventions, [the architecture map](docs/architecture.md) for code ownership and data flow, and [the development guide](docs/development.md) for validation and installation. Commit Room schema JSON from `app/schemas/`. Generated packages, device captures, databases, signing material, and financial backups are ignored by Git. Store local release snapshots in `artifacts/`.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat "-Pledger.testApplicationId=in.thomas.pocketledger.validation" :app:connectedDebugAndroidTest
```

Device tests use a separate application ID and synthetic data. Do not run instrumentation against the personal package: the runner may uninstall its target. Rebuild without the validation property before installing a personal APK.

On 24 October 2026 the tree's 111 JVM tests and lint (against `app/lint-baseline.xml`) passed, and 55 of 56 device tests passed on the isolated validation package (the 56th, `VisualReviewTest`, is opt-in and was skipped). UI redesign validation recorded for 1.7.0: 50 JVM tests and **22 isolated device tests** passed. The four UI journeys also passed in adaptive, locked 60Hz and locked 120Hz modes. Build and lint passed with zero lint errors; five performance-gate tooling tests passed. Baseline/Startup Profiles are included. Full startup and jank certification across the hardware matrix remains pending.

These are recorded validation results for the 1.7.0 work, not a guarantee for later changes. The final extended benchmark run was stopped at the user's request; it is not recorded as a pass. Benchmarks and refresh-mode matrix scripts are opt-in and are not part of routine build/install commands.

Recurring transactions, receipt attachments, cloud synchronization, and Realbyte data migration are outside the current scope.

## UI redesign and performance validation

See [the implementation notes](docs/ui-redesign.md) for adaptive navigation, motion, accessibility, isolated device tests, Baseline/Startup Profile generation and frame-performance gates. Performance targets require physical-device traces; a successful debug build does not certify jank or startup budgets.

Category reminder validation (23 October 2026): debug build, JVM tests, and lint passed; the personal APK was installed successfully through ADB. Notification delivery and tapping have not yet been verified on a device. Messages still awaiting sender/account review do not notify until saved as transactions. If notification permission is denied, enable it in Android app settings; capture continues without reminders.

Use **Choose category** on uncategorized Activity rows for a focused category update. **Manage** opens Accounts, the separate Income plan, and Settings. Budget editors offer explicit removal of the recurring limit while preserving transactions.
