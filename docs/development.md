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

## Release build, signing and lint baseline

- Release builds use R8 minification and resource shrinking with `app/proguard-rules.pro` (WorkManager workers, Room database/entities, enum names). Backup JSON uses explicit string keys, so it does not depend on field names surviving obfuscation.
- Release signing reads `keystore.properties` (ignored) at the repository root with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. Without it the release APK is unsigned. Never commit keystores, passwords or `*.mapping.txt` (ignored; keep the mapping file from each shipped release outside the repository to decode stack traces).
- `LedgerFrameTracker` and `androidx.metrics` are compiled only into debug and benchmark builds (`app/src/tracking/java`); `app/src/release/java` holds a no-op stub with the same API. `ui-tooling` and `ui-tooling-preview` are `debugImplementation`.
- Dependency versions live in `gradle/libs.versions.toml`; `app` and `benchmark` consume it.
- `app/build.gradle.kts` has a `lint {}` block pointing at `app/lint-baseline.xml`. The committed baseline records the 27 warnings that existed on 24 October 2026; new issues beyond it fail lint. To regenerate it, delete `app/lint-baseline.xml`, run `.\gradlew.bat :app:lintDebug` (the first run writes the file and reports a failure on purpose), review the result, rerun, and commit it. `kotlinx-serialization-core` is pinned to 1.8.1 on `debugImplementation` because Room's `MigrationTestHelper` needs it and the debug runtime otherwise resolves 1.7.3; lint's newer-version notice for it is baselined.

### Minified release backup round trip

Run this when changing `Backup.kt`, Room entities or ProGuard rules. Use the validation package only; never the personal one.

1. Build a minified release for the validation ID: `.\gradlew.bat "-Pledger.testApplicationId=in.thomas.pocketledger.validation" :app:assembleRelease`. Sign it with a local keystore through `keystore.properties`, or an unsigned APK will not install.
2. Confirm `app/build/outputs/apk/release/output-metadata.json` has application ID `in.thomas.pocketledger.validation`, then `adb install -r` the APK.
3. In the validation app, add synthetic accounts and a few transactions (including a transfer), then export an encrypted backup from Settings.
4. Clear the app's data or add a different entry, then restore that backup with the same password. Accounts, transactions, budgets, rules and dismissed messages must match, with no crash from missing classes. Also restore a backup exported by a debug build (and a `PKTLGR01` fixture if one is available).
5. Keep the mapping file and any logs out of git. No script automates this because it needs interactive password entry; do not add one that touches the personal package.

## Category notification checks

Use synthetic SMS and data in an isolated validation installation. Confirm permission grant and denial, a newly saved uncategorized alert, duplicate delivery/reconciliation, remembered categories, matched transfers, and sender-review processing. Tap reminders with the app closed and already open; verify the correct editor opens. Check that saving a category clears its reminder and denied permission does not prevent saving. Do not send synthetic alerts to the personal ledger.

The permission prompt is attempted once when capture is enabled on Android 13+. After denial, enable notifications through Android app settings. Notifications begin after an alert becomes a saved transaction; Inbox review items are not yet eligible.

## Performance and evidence

Benchmarks, profile generation, and refresh-mode matrix runs are opt-in. Target `in.thomas.pocketledger.validation` and restore device settings in `finally`. Never treat interrupted runs or functional 60Hz/120Hz tests as proof of frame deadlines. Keep raw captures, logs, traces, benchmark output, APKs, and personal data ignored; track reviewed application-only profile rules and Room schemas.

## Maintaining these docs

- `README.md`: user behavior, setup, supported formats, and limitations.
- `AGENTS.md`: contributor constraints and safe commands.
- `docs/architecture.md`: ownership, data flow, invariants and dated behavior changes.
- `docs/ui-redesign.md`: UI decisions and dated validation evidence.
- `docs/review-redesign.md`: intended screen design and acceptance checklist (not evidence).
- `docs/audit-2026-10-24.md`: the October 2026 audit, its findings and follow-up status.

Record what was actually run, the scope of device checks, and remaining limitations. An installed APK proves installation, not notification delivery or UI correctness. Keep personal device identifiers, SMS, financial amounts, and backups out of committed documentation.

For a reliability audit, capture bounded logs for the validation app's UID during synthetic journeys, including `LedgerSms`, `LedgerWidgets`, and `LedgerFrames`. `LedgerSms` records generic capture/retry/scheduling failures without message contents or exception text. Frame counts are heuristic diagnostics, not frame-deadline certification. Review the merged release manifest and build a minified release when changing backup serialization. See [the October 2026 audit](audit-2026-10-24.md) for findings and dated validation evidence.

## Optional visual review

`VisualReviewTest` is skipped by default. Enable it with the instrumentation argument
`ledger.visualReview=true`, using only the validation package. It creates an in-memory synthetic
ledger and captures the main destinations, editor and scrolled breakdown/budget/category rows in
light and dark mode. Screenshots go to the validation app's external `files/ui-review` directory;
pull them into ignored `artifacts/` before removing that installation. Review the images themselves;
successful capture alone is not visual acceptance.

For large-text review, launch the test separately at system font scales 1.0 and 2.0. Save the original
`settings get system font_scale` value and restore it in `finally`, including on test failure. This
checks modal windows as well as the main composition. Portrait checks do not cover keyboard,
landscape, foldable, TalkBack or frame-time acceptance.

The spending-review redesign adds `SpendingReviewUiTest` (focused category save and explicit budget removal) and extends `RedesignUiTest` with widget-route and notification-entry routing checks. `VisualReviewTest` follows Manage and Insights rather than the former More destination. Select vertical scroll containers explicitly when a page also contains a horizontal filter list.

For screenshots that must remain available after a run, build both validation APKs, verify the application ID, install those APKs, and invoke `adb shell am instrument -w` against `in.thomas.pocketledger.validation.test/androidx.test.runner.AndroidJUnitRunner` with `-e class in.thomas.pocketledger.VisualReviewTest -e ledger.visualReview true`. Pull the validation application's `files/ui-review` directory into ignored `artifacts/`. Some Gradle-managed runs remove the validation installation after collecting results. Save and restore font scale and rotation settings in `finally` when testing those configurations. Do not install an APK built without the validation property for these checks.

## Database v4, local seed accounts, and backup safety

- Schema 4 adds a unique index on `Entry.smsKey` (NULLs allowed), indices on `Entry.date` and `Entry.accountId`, and purges stored SMS text for SAVED, DISMISSED and LINKED messages (`body` becomes `purged:<sha256 of sender + normalized body>`; identity keys, parsed fields and tombstones are unchanged). Migration 3 to 4 first detaches duplicate `smsKey` entries (keeps live, then transfer, then categorized, then oldest) and soft-deletes the extras instead of removing them. Dismissed rows keep their text until the next launch so undo works. After building, commit the generated `app/schemas/.../4.json`.
- Migration tests live in `LedgerMigrationTest` (androidTest, `room-testing`, schemas as test assets). Run only with `-Pledger.testApplicationId=in.thomas.pocketledger.validation`. They passed on a physical device on 24 October 2026 (v1 to v4 straight, one version at a time, and v3 to v4 with purged bodies).
- Seeded SBM/Axis suffixes are no longer in source. First run reads the git-ignored `app/src/main/assets/local-accounts.properties` (`sbm.suffix=`, `axis.suffix=`); without it only Cash is created and bank accounts are added in Accounts.
- Backups are verified by reading the written file back (`Backup.writeVerified`). Restore first writes an encrypted safety copy of the current data to `files/backup-safety/pre-restore.plbackup` (same password as the restore file) and aborts if that fails.
- The 15-minute SMS reconcile is scheduled only while capture is enabled (`ReconcileWork`, kept in sync by a preference listener). Bulk imports of more than three uncategorized entries post one summary notification with no financial text.

## Widget and device-test notes

- `WidgetRenderingTest` renders its matrix at 100% font scale; the reference fixture uses the device font scale; the former 200% case was removed on request, so widget text-fit and clipping checks at large font scales are now manual (see the optional visual review above).
- RemoteViews cannot call `setSelected`; widget colours are set directly at render time. Keep the `WidgetRenderingTest` assertions in step with `LedgerWidgets.monthlyViews` and `budgetViews`.
- `WidgetLogic.kt` holds the pure widget helpers (`amount`, `comparison`, `showContext`, `monthTitle`, `progress`, `overspent`, `nextBoundary`, `buckets`) and is covered by `WidgetLogicTest` on the JVM.
