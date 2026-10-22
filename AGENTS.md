# Repository Guidelines

## Project Structure & Module Organization

Pocket Ledger 1.1 is an offline Android app targeting API 37. Kotlin sources are in `app/src/main/java/in/thomas/pocketledger/`: Compose screens handle UI, `Repository.kt` coordinates imports and ledger updates, `Database.kt` defines Room entities, and parsing, capture, and encrypted backups have dedicated files. Resources and the manifest are under `app/src/main/`. JVM tests use `app/src/test/`; device tests use `app/src/androidTest/`. Keep Room schemas in `app/schemas/`. Generated deliveries belong in `artifacts/`, outside source commits.

## Build, Test, and Development Commands

Use JDK 21, SDK platform 37.0, and build tools 37.0.0. Configure `ANDROID_HOME` or an untracked `local.properties`. Run from the repository root:

- `.\gradlew.bat :app:assembleDebug` builds the personal app.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug` runs JVM tests and Android Lint.
- `.\gradlew.bat "-Pledger.testApplicationId=in.thomas.pocketledger.validation" :app:connectedDebugAndroidTest` runs device tests under a separate application ID.
- `adb install -r .\app\build\outputs\apk\debug\app-debug.apk` updates the installed app.

Rebuild without the validation property before installing the personal APK. Avoid instrumenting the personal package: the test runner may uninstall its target afterward.

## Coding Style & Architecture

Use four-space indentation, PascalCase classes and composables, and camelCase functions and properties. Prefer readable Kotlin blocks. Keep UI, parsing, persistence, and cryptography separate. Store money as integer paise and interpret transaction dates in Asia/Kolkata. Android Lint is configured; no standalone formatter is configured.

## Parser & Testing Guidelines

Support observed SBM and Axis account formats; exclude credit cards, OTPs, requests, and future mandates. Keep ambiguous transactions in review. Preserve existing UPI keys, legacy message identities, dismissal tombstones, and manual corrections. Scope reused ACH references to posting dates. Refresh stored review fields without automatically posting them.

Use JUnit 4 and AndroidX Test. Name files `*Test.kt` and methods after behavior. Add synthetic regression fixtures for parser and upgrade changes; never save real phone messages in source. Device tests use in-memory Room databases. Version 1.1 validation passed 44 JVM and 17 device tests; no coverage percentage is enforced.

## Commit, Review & Security

No commit history establishes a convention. Use imperative subjects, such as `Expand bank SMS parsing`. Describe behavior, validation, relevant issues, and UI screenshots in PRs. Keep processing local. Never commit personal SMS, passwords, signing keys, local SDK paths, generated binaries, or caches.
