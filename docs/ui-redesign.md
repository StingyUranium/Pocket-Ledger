# Ledger UI redesign

The redesign keeps financial storage, backup formats, category budgets and SMS identities compatible. It builds on the existing 50/30/20 plan and widget work.

Implemented:

- Compact Overview / Activity / Budgets / More navigation, with Accounts, Inbox and Graphs in More; navigation rail at 600dp; independent destination scroll/filter state.
- At 840dp and ordinary font scaling, transaction editing moves beside the ledger. Movable content preserves the draft when changing presentation. Large fonts use a single pane.
- Dynamic color, static fallback, light/dark appearance, high contrast and reduced-motion preferences; 48dp icon controls; scalable transaction rows; tonal summary cards and a static header that avoids per-scroll header remeasurement.
- Material modal sheets, standard list stretch/fling, seekable predictive back and a shared income/spending container. Root tabs do not push onto each other's back stack.
- Opening Overview's Income/Spent cards moves and expands the tapped card into its detail header, while Overview stays stationary behind the fading detail page. Back reverses the card movement. Other detail and Settings pages slide up when opening and down when returning. Shared metric bounds and page motion use the same 280ms timeline; one settling effect owns navigation after completed or cancelled predictive back gestures. The Overview report stays loaded while a detail page is open so back previews do not recreate its loading skeleton.
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

The subsequent navigation overlap fix passes the debug build, all 50 JVM tests, lint, and Android test compilation. It adds regression journeys for repeated Spent/Income navigation and predictive back cancellation/completion. These require another isolated device run; no device was connected for this fix, and the earlier device results do not validate it.

The slide-up follow-up passes the debug build, lint, and both isolated device regression journeys (repeated Spent/Income navigation and predictive back cancellation/completion) on the Motorola Edge 70. The personal APK was rebuilt without the validation property and installed as an update. These functional checks do not measure frame deadlines.

The subsequent card-to-header refinement passes lint and all six isolated UI journeys on the Motorola Edge 70, including an assertion that Overview's position stays fixed during predictive back previews. Income/Spent navigation uses the shared card movement and a stationary page reveal; other routes retain the vertical slide.

## Interaction refinement ? 23 October 2026

Audit: the existing snapshot/repository architecture, Room schema, SMS pipeline, encrypted backups,
root state holders, draft savers, shared metric transitions and Material sheet gestures remain the
foundation. There is no network/authentication stack or dependency injection framework to replace.
The installed toolchain is AGP 9.4.1, Kotlin Compose plugin 2.4.21, Compose BOM 2026.09.00 and
Activity Compose 1.13.0; this refinement adds no dependencies.

Changes:

- `Interaction.kt` centralizes the spacing rhythm and responsive/gentle motion families. Press effects
  observe the clickable control's own interaction source, preserving native cancellation, scrolling,
  accessibility and immediate business events. Icon controls, metric cards, transaction rows and More
  destinations use different, restrained travel. Reduced motion removes scaling and animated tone.
- Compact navigation uses a lighter intrinsic-height row with native selectable tab semantics,
  safe bottom insets, animated color, press feedback and selection-only haptics. Multiline labels
  can grow with text scaling. Root destinations still own independent saved state.
- Segmented controls move one background behind all options, use shared multiline height and
  animate position in layout rather than reading each animation frame in composition. Budgets uses
  the same control as Inbox and transaction type. Toggle haptics only follow user events.
- Static fallback themes use layered neutral surfaces, including explicit Material surface container
  roles; dynamic color and high contrast remain available. Headline weights are quieter. More groups
  its related destinations in one surface and empty states no longer introduce nested cards.
- Search has a persistent accessible label and clear action. Form focus gets a visible accent,
  Next traversal and a Done action for the final transaction note. Backup password fields have
  Show/Hide, contextual IME actions and inline error semantics; secrets remain ephemeral.
- Non-metric push/pop routes now use reversible fades and restrained vertical travel (1/12 of a
  page entering, 1/24 for the parent) rather than full-screen travel. Shared metric geometry and its
  single predictive-back settling owner remain intact. Transaction list changes use native item
  placement animation unless motion is reduced. Activity groups transactions into date sections with
  inset dividers instead of one card per entry. Page and section titles expose heading semantics.
  Custom account, budget, chart, picker and settings rows share the same native-cancellable press
  abstraction; Material buttons retain their native feedback.

The stable page header remains static: no per-scroll measurement or new nested-scroll owner is
introduced. Native Material sheet physics, IME padding, swipe dismissal with Undo, chart inspection,
loading/read-error presentation and adaptive editor placement remain in place.

Validation evidence for this refinement is recorded below separately from these design decisions.
The exhaustive light/dark, font-scale, foldable, keyboard and physical frame-deadline matrix remains
acceptance work; functional tests must not be read as performance certification.

Device evidence for the refinement (23 October 2026): all 26 isolated device tests passed
(18 repository tests and 8 UI journeys). After correcting More-tab reselection from a child
screen, the final 8 UI journeys passed again. Added coverage exercises segmented selection,
selection retention across root destinations, search clearing, and returning to More by tapping
the selected More tab from Accounts. The startup helper now tolerates the brief absence of a
Compose root while the activity launches. Visual review was stopped at the user's request;
there is no claim of completing the full visual matrix or measuring frame deadlines.

Final host checks for this refinement: `:app:assembleDebug`, all 52 JVM tests and `:app:lintDebug`
passed. The personal APK was rebuilt without the validation property; output metadata confirms
`in.thomas.pocketledger`. A local delivery is in ignored `artifacts/Ledger-ui-refinement.apk`.
The temporary validation installation was removed. After the user requested installation, the
verified personal APK was installed successfully with `adb install -r`, preserving existing data.
Installation is not additional evidence of visual correctness or frame-time performance.

## Scroll-away page controls

The title/actions and month picker now form a single clipped header region, using the installed
Material 3 enter-always nested-scroll behavior for gesture consumption, fling and spring settling.
Scrolling down tucks it above the content; scrolling up reveals it immediately. Every root screen,
Settings and transaction drill-down uses the same owner. The status-bar inset remains fixed and
bottom navigation remains available. Month controls were moved into the shared header, including
transaction drill-down; financial filters and metric content retain their existing behavior.

The wrapper updates its visible height during scroll, while its child is measured with unchanged
constraints. Header height limits update only when the actual content size changes, including font
scaling or resizing. No per-pointer coroutine or header content remeasurement is introduced.
Changing a root destination reveals its controls without resetting list/filter/draft state.
Reduced motion and Android touch exploration pin the controls. Modal editors retain their own
native sheet scrolling and do not drive the page header.

This supersedes the earlier static-header choice. Visual review remains omitted at the user's
request; validation evidence follows after the build and focused gesture regressions.

Scroll-header device validation (23 October 2026): all 10 isolated UI journeys passed, including
new gesture regressions for hiding/revealing Overview title/actions/month together and doing the
same on Settings before returning. Existing predictive-back cancellation/completion, shared metric
navigation, draft recreation, root navigation and segmented/search journeys also passed. These are
functional checks; no visual matrix, refresh-rate matrix or frame-time benchmark was run.

The scroll-header debug build, all 52 JVM tests and lint passed. The personal package ID was
verified and `artifacts/Ledger-scrolling-header.apk` was installed successfully with `adb install -r`.
Existing app data was preserved. Lint reports zero errors; device tests used only the validation package.

## iOS-style redesign (23 October 2026)

This pass changed presentation only; storage, backups, SMS identities and destinations are untouched.

- **Tokens:** an iOS-like type scale (Large Title 34 down to Caption 12), an 8/12/20dp radius scale, a
  16dp `LedgerSpace.margin`, black-based dark surfaces (`#1C1C1E`/`#2C2C2E` groups) and a grouped
  `#F2F2F7` light background. Dynamic color stays on by default with the static fallback. No font is bundled.
- **Navigation chrome:** large collapsing titles with a pinned compact bar and a hairline that appears
  once the title has collapsed; a translucent tab bar without a pill indicator.
- **Grouped lists:** `GroupedSection`, `GroupedRow`, `GroupedItem`, `GroupedField` and `GroupedDivider`
  replace stacks of cards on Overview, Accounts, Budgets, Graphs, Inbox, Settings and the editors.
  Sheets sit on the grouped background so groups stand out.
- **Interaction:** custom controls use the shared scale/opacity press effect with no ripple (Material
  buttons keep theirs). Confirmations are a compact centered alert with a red destructive action and a
  light confirm haptic. Transaction rows swipe to request deletion; the confirmation dialog still decides,
  and a custom accessibility action offers the same request.
- **Motion:** non-metric push/pop routes slide horizontally with a quarter-width parallax on the parent.
  The Income/Spent card-to-header move is unchanged. Reduced motion removes both.
- **States:** a failed first read shows a centered message with Retry; empty states use a quiet icon,
  one line of context and, where relevant, one action.

Decisions that differ from the original brief: dynamic color remains on; the historical scroll-performance
rules in `AGENTS.md` were set aside for this pass at the user's request, so blur and the overlay header are
accepted costs. Context menus on rows and a bundled font were not added.

Validation: the debug build, JVM tests, lint and the isolated device journeys pass on the Motorola Edge 70.
Header tests now expect back, add and settings to stay pinned while the title and month collapse. These are
functional checks; there is no visual matrix and no frame-time evidence for blur or the overlay header.

## Fast downward gestures in editors (24 October 2026)

Scrollable `LedgerSheet` forms now share their scroll state with the Material sheet. Sheet dragging
is disabled while the form is below its top, and stays disabled through a gesture/fling that began
below the top. After the form settles at the top, Material swipe dismissal is available again.
Cancel, back and outside-tap dismissal remain available. Native form scrolling and sheet physics
are retained; there is no custom pointer detector or replacement fling animation.

Device validation on the Motorola Edge 70: three isolated sheet tests pass, covering repeated fast
downward flings to the form top, preventing a sheet drag while scrolled, and swipe dismissal at the
top. The drag-while-scrolled regression fails with the gesture gate disabled. Debug assembly, all
52 JVM tests and lint pass. All ten existing isolated navigation/editor UI journeys also pass.
The personal APK was rebuilt without the validation property and its application ID verified;
`artifacts/Ledger-sheet-fling-fix.apk` is the local delivery. These functional checks do not establish
frame-time performance.

## Separate income and spending categories (24 October 2026)

The transaction editor offers 30 spending and 15 income suggestions, with separate saveable category
drafts when changing type. Existing names remain editable, and custom category suggestions follow
the type of their saved entries. Transaction filters follow the selected type and reset the category
when type changes; budgets offer spending categories. Transfers retain their own category.
Payee rules are independent by type, shown as Income/Spending in Settings; Room schema 3 and backup
format 2 preserve this distinction. Existing records are not recategorized; old databases and format 1
backups infer an old rule's type from its latest matching correction, with a category-based fallback.

Device validation: all 35 isolated tests pass on the Motorola Edge 70, including migration validation,
old-backup restore, typed rule ingestion/backup/removal, category selection and activity recreation,
and the existing navigation and sheet regressions. These are functional checks, not frame-time evidence.
Host validation: debug assembly, all 55 JVM tests, lint and benchmark-variant Kotlin compilation pass;
no benchmark was run. The personal package ID was verified and `artifacts/Ledger-categories.apk`
was installed successfully as an update with `adb install -r`, preserving existing app data.

## Visual review and data bars (24 October 2026)

The spending breakdown continues to show share of total monthly spending, as requested. Each row
now labels that share explicitly, separately from its budget remaining text. Continuous data bars
replace loading-style indicators in the breakdown, budgets and six-month view. Zero values have no
colored dot, and tiny positive categories are labeled `<0.1%` rather than `0%`. Dark charts use a
brighter palette. Large text stacks account balances, grouped values, budget amounts and editor
choices; chart totals move below the doughnut. Tab labels ellipsize rather than leaving a final
letter on its own line, and bank abbreviation artwork retains its icon size.

Reviewed 48 final physical-device captures on the Motorola Edge 70, using a synthetic ledger in the
validation package: light/dark at system font scales 1.0 and 2.0, covering Overview, Activity, Budgets,
More, Accounts, Graphs, Inbox, Settings, the transaction editor and selected scrolled rows. Earlier
captures exposed squeezed account names, split doughnut totals, split editor choices and clipped bank
artwork; the final captures verify those fixes. The original font-scale setting was restored in
`finally`. Raw captures and logs remain ignored. This portrait review does not cover the full keyboard,
landscape, foldable or accessibility matrix and does not establish frame-time performance.

Validation: debug assembly, lint and all 56 JVM tests pass. The final isolated instrumentation run
passes all 36 functional tests, including pixel checks for proportional fill, a continuous track and
zero-value bars; the opt-in visual fixture is skipped in that run and was run separately for the two
font scales. The complete run reports 37 tests including that assumption-skipped fixture.
