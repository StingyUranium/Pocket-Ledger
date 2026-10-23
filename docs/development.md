# Development and validation

## Routine workflow

Use JDK 21 and Android SDK platform 37.0 / build tools 37.0.0. Configure the local SDK with `ANDROID_HOME` or an ignored `local.properties`. Run commands from the repository root and reuse Gradle caches.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Reports are generated under `app/build/reports/`; unit test XML is under `app/build/test-results/`. Add focused synthetic tests when changing financial, parser, backup, migration, or notification eligibility behavior. For performance tooling changes, also run:

```powershell
python -m unittest discover -s tools -p 'test_*.py'
```

## Device tests and personal installs

Instrumentation must use the separate validation package because the test runner may uninstall its target:

```powershell
.\gradlew.bat "-Pledger.testApplicationId=in.thomas.pocketledger.validation" :app:connectedDebugAndroidTest
```

Before installing a personal update, rebuild without that property and check the metadata:

```powershell
.\gradlew.bat :app:assembleDebug
Get-Content app/build/outputs/apk/debug/output-metadata.json
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The metadata application ID must be `in.thomas.pocketledger`. Use `adb -s <serial>` if multiple devices are connected. Keep the signing key consistent; do not uninstall the personal app to resolve an install problem because that removes its data.

## Category notification checks

Use synthetic SMS and data in an isolated validation installation. Confirm permission grant and denial, a newly saved uncategorized alert, duplicate delivery/reconciliation, remembered categories, matched transfers, and sender-review processing. Tap reminders with the app closed and already open; verify the correct editor opens. Check that saving a category clears its reminder and denied permission does not prevent saving. Do not send synthetic alerts to the personal ledger.

The permission prompt is attempted once when capture is enabled on Android 13+. After denial, enable notifications through Android app settings. Notifications begin after an alert becomes a saved transaction; Inbox review items are not yet eligible.

## Performance and evidence

Benchmarks, profile generation, and refresh-mode matrix runs are opt-in. Target `in.thomas.pocketledger.validation` and restore device settings in `finally`. Never treat interrupted runs or functional 60Hz/120Hz tests as proof of frame deadlines. Keep raw captures, logs, traces, benchmark output, APKs, and personal data ignored; track reviewed application-only profile rules and Room schemas.

## Maintaining these docs

- `README.md`: user behavior, setup, supported formats, and limitations.
- `AGENTS.md`: contributor constraints and safe commands.
- `docs/architecture.md`: ownership, data flow, and invariants.
- `docs/ui-redesign.md`: UI decisions and dated validation evidence.

Record what was actually run, the scope of device checks, and remaining limitations. An installed APK proves installation, not notification delivery or UI correctness. Keep personal device identifiers, SMS, financial amounts, and backups out of committed documentation.
