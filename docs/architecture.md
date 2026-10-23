# Codebase map

All Kotlin filenames below are relative to `app/src/main/java/in/thomas/pocketledger/`.

## Ownership

| Area | Starting points |
|---|---|
| Application and persistence | `Repository.kt` (`LedgerApp`, `Repository`), `Database.kt` (entities, DAO, migration) |
| Activity, routes, permissions, launch intents | `MainActivity.kt` |
| Snapshot reads and background calculations | `LedgerUiState.kt`, `Reports.kt` |
| Screens and editors | `LedgerScreens.kt`, `SmsScreens.kt`, `Editors.kt`, `GraphsScreen.kt` |
| Theme, motion, adaptive layout | `DesignSystem.kt`, `FluidMotion.kt`, `MetricTransition.kt`, `FoldAwareWindow.kt` |
| Draft persistence | `DraftSavers.kt` |
| SMS recognition and delivery | `SmsParser.kt`, `SmsCapture.kt` |
| Category reminders | `CategoryNotifications.kt` |
| Widgets and backups | `LedgerWidgets.kt`, `Backup.kt` |
| Specialized UI | `DailySpendingChart.kt`, `MessageSwipe.kt`, `IncomeAllocationPlan.kt` |

## SMS to transaction

1. `SmsReceiver` checks capture preferences, joins SMS parts, and calls `SmsParser.parseSelected`.
2. `Repository.ingest` serializes ingestion with a mutex and performs database changes in a Room transaction. Existing message identities, legacy body identities, conflicts, and dismissal tombstones prevent unsafe duplicate creation.
3. Only complete alerts with a trusted sender, mapped account, and valid dates auto-save. Other selected alerts remain in Inbox review. `processReview` rechecks eligible messages after sender confirmation.
4. `saveParsed` applies a remembered merchant category or uses `Uncategorized`. `matchTransfers` links eligible opposite entries before notifications are selected.
5. After commit, newly created, live, non-transfer SMS entries with `Uncategorized` produce reminders. This also covers reconciliation and review processing; historical alerts can notify when they become saved transactions. Existing entries do not notify again during rescans.
6. `SmsWorker` periodically reconciles provider messages and recovers missed deliveries. App startup/resume also reconciles; Android scheduling can delay background work.

Notifications use a dedicated channel and an explicit, immutable pending intent with a distinct entry URI. Text contains no amount or merchant. `MainActivity` waits for its initial snapshot, resolves the entry ID, opens Activity and its editor, and consumes the request. Saving a category clears the reminder. Permission denial leaves ingestion working; there is no reminder backlog replay when permission is later granted.

## UI and state

Compact root destinations are Overview, Activity, Budgets, and More. More exposes Accounts, Inbox, and Graphs. Transaction detail and Settings routes animate separately from root tabs. Keep the selected month, drafts, and saved page state intact across navigation and resizing.

Overview and metric details share a background-computed `OverviewReport`. Navigation settling and predictive back must share one transition owner so cancellation does not leave pages overlapping. Shared metric cards and route motion use the same duration. See [UI implementation notes](ui-redesign.md) before changing these transitions.

Room flows feed `ledgerSnapshots`; calculations and I/O belong off the main thread. `LedgerApp` observes entries and budgets to refresh installed widgets.

## Data invariants

- Money is integer paise; transaction and month boundaries use Asia/Kolkata.
- Income/spending reports exclude deleted entries and transfers. Account balances include transfer effects.
- Recurring category budgets apply to each selected month. Overspending retains a negative remaining amount.
- The 50/30/20 income allocation is a planning tool, separate from budgets and actual savings.
- Preserve SMS keys, manual corrections, tombstones, backup compatibility, and Room migrations. Schemas are tracked under `app/schemas/`.
- Restoring replaces validated data and disables capture until explicitly enabled again.
- Processing stays local; the application declares no internet permission.

## Tests and performance

JVM tests cover parsing, reports, ledger behavior, and reminder eligibility. Device tests cover repository/database behavior and Compose journeys using the validation package. The benchmark module and benchmark-only app fixtures are isolated from the personal package. Functional UI results do not establish startup or frame-time performance; see [development](development.md) and [performance notes](ui-redesign.md).
