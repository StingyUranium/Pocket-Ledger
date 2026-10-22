# Ledger

Ledger **1.4.0** is a personal, offline Android expense manager targeting **Android 17 / API 37**. It imports SBM Bank India and Axis Bank account alerts, alongside manual income, expenses, and transfers. Credit card parsing is excluded.

Ledger was previously named Pocket Ledger. The upgrade preserves accounts, preferences, transactions, and existing encrypted backups. Its blue wallet icon supports adaptive launcher shapes and Android themed icons.

## Getting started

1. Open **Accounts**. Confirm the account suffixes and set each opening date and its balance immediately before that date. Initial zero balances are placeholders. Cash is optional.
2. Open **SMS ? Enable SMS access** and grant Android permission. Manual entries work without it.
3. Choose a history range and tap **Preview bank SMS**. The range filters receipt dates; transactions use dates extracted from messages. Confirm selected messages before importing. Opening dates must cover their transaction dates.
4. Inspect example messages and confirm each bank sender ID. Different carrier prefixes may require separate confirmation. Sender confirmation does not prove a message is authentic.
5. Enable **Capture new bank alerts**. Capture starts at activation; WorkManager and app resume reconcile missed messages, subject to Android scheduling. Preview history to fill earlier gaps.
6. Use **+** for manual entries or transfers. Tap entries to edit or delete them. Remember a payee's category when useful; configure monthly limits in **Budgets** and view reports on **Home**.
7. Save an encrypted backup through **Settings**. Restoring replaces current data and disables capture until you enable it again. Keep the password; it cannot be recovered.

## Supported SMS formats

| Bank | Formats recognized |
|---|---|
| SBM Bank India | UPI, NEFT, IMPS, IFT, ACH credits/debits, savings interest |
| Axis Bank | UPI, NEFT, RTGS, IMPS, alternate merchant debits, completed mandate debits, UPI Lite top-ups |

Inbox scanning applies the selected scope: SBM and Axis UPI, NEFT/IMPS/RTGS/IFT, ACH, savings interest, UPI Lite top-ups, completed Axis merchant debits, and deposit-account refunds/reversals. Credit card messages are always excluded. Incomplete selected alerts remain in review rather than being posted automatically.

Automatic saving requires a confirmed sender, mapped account, valid positive amount, direction, date, and usable reference. Interest without a reference, account-less mandate debits, merchant debits without references, and wallet top-ups remain in review. Unknown financial formats require correction. OTPs, credit card alerts, failed payments, payment requests, future mandates, and blocked-funds notices are excluded.

Amounts are stored as integer paise in INR. Dates use Asia/Kolkata, and date-only messages retain that precision. SMS balances are shown separately and never overwrite ledger balances. Missing bank alerts and cash activity require manual entries.

## Interface and appearance

The interface uses Jetpack Compose with collapsing 34sp large titles, inline navigation titles, horizontal push/pop for Settings, and independent root tabs. Creation, editing, choices, and import previews use vertical spring sheets with drag-handle dismissal. Android back dismisses the top presentation first. Scroll positions survive tab switches and Settings navigation.

`FluidMotion.kt` centralizes critically damped springs (ratio 1, stiffness 300) and the cubic Bezier (0.25, 1, 0.5, 1). Lists retain Compose momentum flings and apply resisted boundary translation with a spring return instead of Android stretch overscroll. Cards use 12-16dp corners, small controls use 8dp corners, dividers are physical-pixel hairlines, and original vector-like line icons use consistent rounded strokes. Choose Settings > Theme for light, dark, or system appearance.

These are Android adaptations of iOS interaction conventions, not UIKit controls or bundled SF Symbols. Android display density, system back, font scaling, and system animation duration settings remain supported.

## Ledger and upgrade behavior

- Opening balances anchor account balances. Transfers and deleted entries are excluded from spending reports; transfers affect both accounts once.
- Deduplication includes bank, account, direction, and reference. NEFT/RTGS/IMPS/IFT references include their channel; reused ACH references include their posting date. Conflicting records require review.
- Matching opposing entries with a unique shared reference, equal amount, different accounts, and dates within 24 hours combine into a transfer. Other likely matches require confirmation. The secondary entry remains hidden as an audit record; editing the combined entry does not recreate it.
- Dismissed messages and deleted-entry identifiers survive rescans and backups. Parser upgrades preserve legacy keys and manual corrections.
- On launch, stored review messages are reparsed locally to refresh fields without posting them automatically. This refresh does not read the phone inbox; enabled capture reconciles the inbox separately.

## Privacy and backups

There is no internet permission, account service, analytics, or cloud synchronization. Android automatic backup and device transfer of financial data are disabled. Relevant financial SMS remain as local transaction evidence; links are never opened automatically.

Backups include accounts, transactions, financial SMS, budgets, rules, and import history. Encryption uses PBKDF2-HMAC-SHA256 with 600,000 iterations and random salt, plus AES-256-GCM with a random nonce and authenticated header. Passwords require at least eight characters. Authentication and relationship validation happen before transactional database replacement.

## Build and install

Install JDK 21, SDK platform 37.0, and build tools 37.0.0. Configure `ANDROID_HOME` or an untracked `local.properties`. Run from the project root:

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n in.thomas.pocketledger/.MainActivity
```

The personal package is `in.thomas.pocketledger`, versionCode 4. Keep the same signing key for upgrades. Current APKs use debug signing; uninstalling removes local app data.

Pinned dependencies: Gradle 9.8.2, AGP 9.4.1, Kotlin/Compose compiler 2.4.21, Compose BOM 2026.09.00, Activity 1.13.0, Lifecycle 2.11.0, Room 2.8.5, WorkManager 2.12.0, and KSP 2.3.12. The wrapper verifies its distribution checksum.

### Faster development builds

Configuration caching and the local build cache are enabled in `gradle.properties`. The first run populates caches; later runs reuse compatible configuration and task outputs. Use `:app:assembleDebug` for routine edits and run lint before delivery or after substantial changes. Avoid `clean` unless troubleshooting stale outputs. Use Android Studio Compose Preview or Live Edit for layout iterations when supported. USB ADB can reduce installation time compared with wireless ADB.

## Development and validation

See [AGENTS.md](AGENTS.md) for contributor conventions. Main sources are under `app/src/main/`, JVM tests under `app/src/test/`, device tests under `app/src/androidTest/`, and Room schemas under `app/schemas/`.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat "-Pledger.testApplicationId=in.thomas.pocketledger.validation" :app:connectedDebugAndroidTest
```

The second command uses a separate validation package and synthetic messages in in-memory databases. Do not run instrumentation against your personal package: the runner may uninstall its target. Rebuild without the validation property before installing a personal APK from the shared output directory.

Version 1.1 passed **44 JVM tests and 17 device tests**, with **zero lint errors**. Coverage includes payment formats, date and money parsing, deduplication, transfers, encryption, sender trust, account mapping, and preservation of legacy records during upgrades.

A read-only ADB audit checked 1,963 bank-matching messages: 1,843 were fully parsed, 39 needed review, and 81 were excluded. These counts measure extraction, not financial accuracy or authenticity. The audit imported no ledger entries and retained only aggregate results; new fixtures use invented details. Live capture still requires verification with an incoming bank alert after permission and sender confirmation.

Version 1.2 changes presentation and interaction while keeping the parser, repository, database, capture, and backup implementation unchanged. Its validation consists of build/lint and on-device checks of navigation, themes, sheets, and date selection.

## Deliverables and project location

The project is in `C:\Users\Thomas\AppData\Local\PocketLedgerProject` because Windows Controlled Folder Access blocked writes to the originally selected Documents workspace. Security settings were not changed.

Local deliverables:

- [Version 1.4 APK](artifacts/Ledger-1.4.0.apk)
- [Version 1.4 source snapshot](artifacts/Ledger-source-1.4.0.zip)
- [UI validation report](artifacts/VALIDATION-1.4.0.md)
- [Aggregate parser coverage](artifacts/parser-coverage-1.1.0.json)

The source archive is a release snapshot; consult working-tree files for later documentation updates. Recurring entries, receipts, calendar views, asset valuation, cloud sync, and Realbyte migration are outside this version.
