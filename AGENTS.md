# Repository Guidelines

## Project Structure & Module Organization

Ledger is an offline Kotlin Android app targeting API 37. Sources are under `app/src/main/java/in/thomas/pocketledger/`. `MainActivity.kt` owns navigation; Compose screens, `DesignSystem.kt`, and `FluidMotion.kt` handle presentation. `GraphsScreen.kt` renders charts, `Reports.kt` calculates summaries, and `LedgerWidgets.kt` manages home screen widgets. `Database.kt`, `Repository.kt`, `SmsParser.kt`, and `Backup.kt` handle financial data. Resources are under `app/src/main/res/`, JVM tests under `app/src/test/`, and device tests under `app/src/androidTest/`. Commit Room schemas from `app/schemas/`.

## Build, Test, and Development Commands

Use JDK 21, SDK platform 37.0, and build tools 37.0.0. Set `ANDROID_HOME` or an ignored `local.properties`. Run from the repository root:

- `.\gradlew.bat :app:assembleDebug` builds the personal APK.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug` runs JVM tests and lint.
- `.\gradlew.bat "-Pledger.testApplicationId=in.thomas.pocketledger.validation" :app:connectedDebugAndroidTest` runs isolated device tests.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` installs an update.

Never run instrumentation against the personal package; the runner may uninstall it. Rebuild without the validation property before installing a personal APK. Reuse build caches; avoid routine `clean` builds.

## Coding Style & Architecture

Use four spaces, PascalCase classes/composables, and camelCase functions/properties. Reuse shared motion and theme components. Keep root tabs independent from push/pop transaction views. Store money as integer paise and use Asia/Kolkata dates. Budgets are recurring category limits; spending calculations use the selected month. Preserve negative remaining amounts. Exclude transfers and deleted entries from reports. Keep widgets consistent with `Reports.kt` and preserve migration and backup compatibility.

## Testing Guidelines

Use JUnit 4 and AndroidX Test; name files `*Test.kt` and methods after behavior. Add meaningful synthetic fixtures for parser, money, migration, and backup changes. Preserve SMS identities, dismissal tombstones, and manual corrections. Capture supports selected SBM/Axis deposit-account alerts; exclude credit cards, OTPs, and payment requests. Validate UI changes with build/lint and focused device checks. No coverage percentage is enforced.

## Commit, Pull Request & Security Guidelines

Follow existing imperative commit subjects, such as `Add recurring budgets and financial charts`. PRs should describe behavior, validation, relevant issues, and UI screenshots. Keep processing local. Never commit personal SMS, databases, financial backups, signing material, or generated packages. Store local deliveries in ignored `artifacts/`; track synthetic fixtures and schema JSON.
