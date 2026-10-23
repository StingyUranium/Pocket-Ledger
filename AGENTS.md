# Repository Guidelines

## Project Structure & Module Organization

Ledger is an offline Kotlin Android app targeting API 37. Sources are under `app/src/main/java/in/thomas/pocketledger/`. `MainActivity.kt` owns navigation; Compose screens, `DesignSystem.kt`, and `FluidMotion.kt` handle presentation. `GraphsScreen.kt` renders charts, `Reports.kt` calculates summaries, and `LedgerWidgets.kt` manages home screen widgets. `Database.kt`, `Repository.kt`, `SmsParser.kt`, and `Backup.kt` handle financial data. Resources are under `app/src/main/res/`, JVM tests under `app/src/test/`, and device tests under `app/src/androidTest/`. Commit Room schemas from `app/schemas/`.

`LedgerUiState.kt` loads snapshots and computes reports off-thread. `MetricTransition.kt` handles shared bounds; `DraftSavers.kt` preserves editor state; `FoldAwareWindow.kt` selects safe pane bounds. `DailySpendingChart.kt`, `MessageSwipe.kt`, and `IncomeAllocationPlan.kt` provide chart inspection, Inbox dismissal, and the separate 50/30/20 plan. See `docs/ui-redesign.md` for implementation details and limitations. The `benchmark/` test module and `app/src/benchmark/` contain isolated performance journeys and synthetic fixtures; `tools/` contains profile import and validation scripts.

## Codebase Reference

Read `docs/architecture.md` for ownership, data flow, entry points, and financial invariants. Read `docs/development.md` for validation, safe installation, and documentation maintenance. Keep these references synchronized with behavior changes; record evidence separately from intended behavior.

`CategoryNotifications.kt` posts category reminders after committed SMS entries are saved. Keep notification side effects outside Room transactions, preserve duplicate suppression, and resolve notification entry IDs only after snapshots load. Do not put financial details in notification text. Android 13+ notification permission is requested when capture is enabled; denial must not block ingestion.

## Build, Test, and Development Commands

Use JDK 21, SDK platform 37.0, and build tools 37.0.0. Set `ANDROID_HOME` or an ignored `local.properties`. Run from the repository root:

- `.\gradlew.bat :app:assembleDebug` builds the personal APK.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug` runs JVM tests and lint.
- `.\gradlew.bat "-Pledger.testApplicationId=in.thomas.pocketledger.validation" :app:connectedDebugAndroidTest` runs isolated device tests.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` installs an update.

Never run instrumentation against the personal package; the runner may uninstall it. Rebuild without the validation property before installing a personal APK. Reuse build caches; avoid routine `clean` builds.

Before personal installation, verify `app/build/outputs/apk/debug/output-metadata.json` has application ID `in.thomas.pocketledger`. Performance tooling must target `in.thomas.pocketledger.validation`; the benchmark-only seeder must refuse any other package. Run lengthy benchmarks, profile generation, or refresh-mode matrix scripts only when requested, and stop them promptly when asked. Refresh-mode scripts must restore the device's original settings in `finally`.

## Coding Style & Architecture

Use four spaces, PascalCase classes/composables, and camelCase functions/properties. Reuse shared motion and theme components. Keep root tabs independent from push/pop transaction views. Store money as integer paise and use Asia/Kolkata dates. Budgets are recurring category limits; spending calculations use the selected month. Preserve negative remaining amounts. Exclude transfers and deleted entries from reports. Keep widgets consistent with `Reports.kt` and preserve migration and backup compatibility.

Compact navigation is Overview, Activity, Budgets, and More; More exposes Accounts, Inbox, and Graphs. Wider windows use a rail and may show an editor beside the ledger. Preserve drafts, filters, month, and scroll state across navigation and resizing. Keep the 50/30/20 income plan separate from recurring category limits and recorded savings. Monthly widgets retain their own Income/Expenses selection.

Use Material 3 semantic colors, dynamic color with a fallback, 48dp touch targets, and layouts that tolerate 200% font scaling. Respect system and app reduced-motion settings. Prefer native sheet/scroll physics; avoid per-pointer coroutine launches and per-scroll header remeasurement. Keep I/O and report/filter calculations off the main thread, and use stable lazy keys and content types. Frame-rate hints are advisory: request Normal at rest and High during interaction with an inactivity delay; never assume High means exactly 120Hz or force a display mode in production.

## Testing Guidelines

Use JUnit 4 and AndroidX Test; name files `*Test.kt` and methods after behavior. Add meaningful synthetic fixtures for parser, money, migration, and backup changes. Preserve SMS identities, dismissal tombstones, and manual corrections. Capture supports selected SBM/Axis deposit-account alerts; exclude credit cards, OTPs, and payment requests. Validate UI changes with build/lint and focused device checks. No coverage percentage is enforced.

Device UI tests should wake the test device and cover navigation, editor dismissal/recreation, appearance, and recurring budgets. Use `python -m unittest discover -s tools -p 'test_*.py'` when changing performance-gate tooling. Functional tests at 60Hz/120Hz do not certify frame deadlines. Startup and jank claims require measured physical-device evidence; do not label interrupted or incomplete benchmarks as passing.

## Commit, Pull Request & Security Guidelines

Follow existing imperative commit subjects, such as `Add recurring budgets and financial charts`. PRs should describe behavior, validation, relevant issues, and UI screenshots. Keep processing local. Never commit personal SMS, databases, financial backups, signing material, or generated packages. Store local deliveries in ignored `artifacts/`; track synthetic fixtures and schema JSON.

Keep raw screenshots, traces, benchmark JSON, and logs ignored. Review and track the application-only `app/src/main/baseline-prof.txt` and `startup-prof.txt`; they are source profile rules, not private device data. Never blanket-ignore image resources or Room schema JSON.
