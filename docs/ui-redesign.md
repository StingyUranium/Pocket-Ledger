# Ledger UI redesign

The redesign keeps financial storage, backup formats, category budgets and SMS identities compatible. It builds on the existing 50/30/20 plan and widget work.

Implemented:

- Compact Overview / Activity / Budgets / More navigation, with Accounts, Inbox and Graphs in More; navigation rail at 600dp; independent destination scroll/filter state.
- At 840dp and ordinary font scaling, transaction editing moves beside the ledger. Movable content preserves the draft when changing presentation. Large fonts use a single pane.
- Dynamic color, static fallback, light/dark appearance, high contrast and reduced-motion preferences; 48dp icon controls; scalable transaction rows; tonal summary cards and a static header that avoids per-scroll header remeasurement.
- Material modal sheets, standard list stretch/fling, seekable predictive back and a shared income/spending container. Root tabs do not push onto each other's back stack.
- Normal/High Compose frame-rate category hints on each window, with a 1.5-second inactivity delay. They do not force a display mode. The connected Motorola exposes 60/90/120Hz and maps High to 90Hz, so its adaptive behavior is not a two-mode 60/120Hz certification.
- Distinct loading and failed-read states, retry, background report/filter/transfer calculations, individually keyed transaction rows and stable list content types.
- Inbox pull-to-check, swipe dismissal with Undo, meaningful haptic feedback, chart day inspection and an accessible daily data list.
- Saveable transaction/account drafts and month/filter state; password contents remain ephemeral. SplashScreen integration, fully-drawn reporting, local debug JankStats, release shrinking and isolated benchmark/profile tooling.

## Validation

Run with JDK 21 and SDK/build tools 37:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat "-Pledger.testApplicationId=in.thomas.pocketledger.validation" :app:connectedDebugAndroidTest
```

Never instrument the personal package. Rebuild without the validation property before personal installation. Device tests include compact navigation, metric drill-down, modal dismissal, appearance controls and recurring budgets, alongside repository/backup/parser tests.

Generate profiles on a physical device with the benchmark-only 10,000-entry synthetic fixture:

```powershell
.\gradlew.bat "-Pledger.testApplicationId=in.thomas.pocketledger.validation" "-Pandroid.testInstrumentationRunnerArguments.class=in.thomas.pocketledger.benchmark.LedgerProfileGenerator" :benchmark:connectedBenchmarkAndroidTest
python tools/import_profiles.py benchmark/build/outputs/connected_android_test_additional_output
```

AGP pulls profile outputs to the host before removing the validation packages. The importer merges application rules; Compose and other libraries supply their own profiles. Review and commit regenerated baseline/startup text after important journey changes. Rebuild before benchmarking with `BaselineProfileMode.Require`:

```powershell
.\gradlew.bat "-Pledger.testApplicationId=in.thomas.pocketledger.validation" "-Pandroid.testInstrumentationRunnerArguments.class=in.thomas.pocketledger.benchmark.LedgerBenchmark" :benchmark:connectedBenchmarkAndroidTest
python tools/check_performance.py artifacts/results.json --hz 120 --frame-timeline artifacts/frames.json --baseline artifacts/previous.json
```

The gate refuses to certify jank without a separately derived FrameTimeline summary and at least 10,000 interaction frames. The summary contains `frames`, `missedDeadlines`, `p99OverrunMs` and `observedHz`. Analyze only measured interaction intervals, exclude idle frames, and obtain these values from Perfetto FrameTimeline, not JankStats heuristics. Adaptive traces must be split by actual observed mode. A baseline must come from the same device/OS/rate/thermal setup.

## Remaining acceptance work

The UI implementation does not establish sub-500ms cold start, sub-frame physical touch latency, zero dropped frames or <0.5% jank. Certify the full screen/gesture matrix on locked 60Hz, locked 120Hz, adaptive, battery saver, and sustained warm mid-range/slow-GPU devices. The current benchmark journeys are startup and overview drill-down; expand them to every screen before claiming full coverage. Separating/occluding folds use a safe single pane (the larger side; reachable lower pane for tabletop where feasible). Dual-pane fold-specific composition and foldable hardware validation remain pending. Large histories still use Room collection snapshots; query paging is a future optimization if the measured matrix requires it. Reduced motion disables app navigation/container motion and presents editors as static, dismissible dialogs instead of travelling sheets. The pinned Material 3 version does not expose public motion-scheme customization; normal sheets keep library-owned spring physics. The system animation scale is also respected.

Official API references used: [frame-rate hints](https://developer.android.com/develop/ui/views/animations/adaptive-refresh-rate), [shared resize modes](https://developer.android.com/reference/kotlin/androidx/compose/animation/SharedTransitionScope.ResizeMode), [predictive back](https://developer.android.com/guide/navigation/custom-back/support-animations), [BaselineProfileRule](https://developer.android.com/reference/kotlin/androidx/benchmark/macro/junit4/BaselineProfileRule).

Recorded validation (23 October 2026): 50 JVM tests, 22 isolated device tests, and 4 UI journeys in each of adaptive/60Hz/120Hz modes passed. Locked-rate UI tests checked the displayed refresh rate; original device settings were restored. Profile generation produced 1,448 application baseline rules and 1,033 startup rules. These functional results do not certify frame deadlines.
