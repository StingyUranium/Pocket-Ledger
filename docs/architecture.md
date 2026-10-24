# Codebase map

All Kotlin filenames below are relative to `app/src/main/java/in/thomas/pocketledger/`.

## Ownership

| Area | Starting points |
|---|---|
| Application and persistence | `Repository.kt` (`LedgerApp`, `Repository`), `Database.kt` (entities, DAO, migration) |
| Activity, routes, permissions, launch intents | `MainActivity.kt` |
| Snapshot reads and background calculations | `LedgerUiState.kt`, `Reports.kt` |
| Screens and editors | `LedgerScreens.kt`, `SmsScreens.kt`, `Editors.kt`, `GraphsScreen.kt` |
| Theme, motion, adaptive layout | `DesignSystem.kt`, `Interaction.kt`, `ScrollingHeader.kt`, `FluidMotion.kt`, `MetricTransition.kt`, `FoldAwareWindow.kt` |
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

`Categories.kt` owns separate income and expense suggestions. Editors retain a draft category for
each type; filters include saved custom/legacy categories only for the selected type, while budgets
offer expense categories. Category discovery runs off-thread. Existing entries are never relabeled. Category pickers use an explicit category presentation in `Editors.kt`, with immediate draft selection, a single Close control and a separate optional add callback; non-category pickers keep the generic choice sheet. Category rows resolve recognizable glyphs through `categoryIconName` and the existing `AppIcon` vectors, with a neutral receipt for custom categories. Names and stored categories are unchanged.
Remembered payee rules use `(merchant, type)` as the Room v3 key, so the same payee can have independent
income and spending rules. Migration 2→3 infers each old rule's type from the latest matching entry,
falling back to an income-only category or expense. Backup format 2 stores rule types and accepts
format 1 restores using the same inference. Settings removes only the selected rule's type.

Notifications use a dedicated channel and an explicit, immutable pending intent with a distinct entry URI. Text contains no amount or merchant. `MainActivity` waits for a snapshot containing the requested live entry (including a newly committed entry that has not reached the current snapshot), resolves the entry ID, opens Activity and its editor, and consumes the request. Saving a category clears the reminder. Permission denial leaves ingestion working; there is no reminder backlog replay when permission is later granted.

## UI and state

Compact root destinations are Overview, Activity, Budgets, and Inbox. Overview exposes Insights; the labeled Manage action exposes Accounts, Income plan, and Settings. `Destination` uses stable names with an explicit legacy numeric restoration mapping. Transaction detail and Settings routes animate separately from root tabs. Keep the selected month, drafts, and saved page state intact across navigation and resizing.

Overview and metric details share a background-computed `OverviewReport`. Navigation settling and predictive back must share one transition owner so cancellation does not leave pages overlapping. Shared metric cards and route motion use the same duration. See [UI implementation notes](ui-redesign.md) before changing these transitions.

`ScrollingLedgerHeader` is an overlay, not a Scaffold top bar. A pinned compact bar (back, inline title,
add/settings) sits above a collapsible large title and month picker, driven by Material
exit-until-collapsed nested scrolling; the title and picker return only at the top of a list. Pages
reserve the header's full height through `LocalLedgerHeaderInset` (and the tab bar's through
`LocalLedgerBottomInset`), which `FluidList` adds to its content padding, so scrolling never resizes a
list's viewport. Both bars blur the page beneath them with Haze (`hazeSource` on the page, `hazeBlur` on
the bars) and fall back to a translucent fill. Destination changes reveal controls without resetting
lists; reduced motion and touch exploration pin the header.

Room flows feed `ledgerSnapshots`; calculations and I/O belong off the main thread, including the selected-month income-plan summary. The calculation cache retains at most 32 call-site results, evicting the least recently used result so visiting many custom categories cannot retain every historical input snapshot. `LedgerApp` observes the current and previous months' entries and budgets to refresh installed widgets while any exist.

Widgets remain Android RemoteViews. The monthly widget uses its own opaque charcoal resources in
both device themes, underlined Income/Expenses tabs and two summary columns. Its current-month total
is compared with the full previous calendar month in Asia/Kolkata; both summaries are retained in
the cache for tab switching and stale fallback. Visible monthly amounts omit the currency symbol;
accessibility retains full amounts and spoken comparison direction. The preferred size is 4 by 2
launcher cells, with compact resizing supported. Monthly selection remains in `widget_tabs` per ID.
Budget retains its theme-aware frosted resources and reveals month context and usage at 144dp,
scaled upward with font scale. Options changes refresh installed widgets; surfaces do not capture
or blur wallpaper.


`LedgerSheet` shares its content `ScrollableState` with `FluidModalSheet`: the default form state or
an explicitly supplied lazy-list state. Category pickers supply their own list state. Scrolled content
retains gesture ownership through its fling, including reaching the top, so a fast downward reversal
cannot start dragging the sheet. Material sheet dragging becomes available again once the content settles at the top;
Cancel, back and outside-tap dismissal remain available while the form is scrolled.

Overview's breakdown bars show each category's share of total monthly expense spending, with an
explicit percentage label; the budget text beneath them remains a separate limit comparison.
`ProportionBar.kt` draws continuous proportional data bars for breakdowns, budgets and history,
without loading-indicator gaps or a minimum-size zero-value dot. `Percentage.kt` labels tiny positive
shares as less than 0.1%. Large-text rows stack values, and chart totals move outside the doughnut.

## Recent behavior changes

- Incomplete SMS messages are identified by sender plus normalized body only (`SmsParser.stableBodyKey` excludes received time), so the live receiver and the Inbox scan agree and dismissal tombstones hold across both paths.
- SMS bodies longer than 1000 characters are capped before parsing.
- `Repository.saveAccount` validates and saves an account under the ingestion mutex in a transaction, then runs `processReview()`; UI code must not write accounts directly.
- `Backup.parse` validates enums, ranges and link consistency before any data is replaced, and wraps failures in a friendly error.
- Backup header `PKTLGR02` stores the PBKDF2 iteration count so it can be raised later; `PKTLGR01` backups (fixed iterations) remain readable.
- `LedgerViewModel` holds the latest snapshot and the `CalculationCache`, so rotation and resume do not flash an empty ledger or discard cached results.
- Launch extras (route, category-entry request) are honoured only on a fresh start (`savedInstanceState == null`) and are consumed afterwards.
- Room migration 3->4 adds a unique index on `Entry.smsKey` (NULLs allowed) and indices on `date` and `accountId`. Duplicate keys keep every row: one live row keeps the key, the others are marked deleted with a null key, so no money rows are lost.
- Stored SMS bodies are replaced by `purged:` plus a hash of sender and normalized body once a message is saved, linked or dismissed (dismissed rows at the next launch, so undo still works). Keys, parsed fields and tombstones are unchanged; `SmsRecord.semanticKey()` gives the same identity before and after a purge, and the editor hides "Original bank message" for purged rows.
- Ingest and transfer matching use targeted queries and join by reference instead of loading whole tables. More than three new uncategorized entries post one summary reminder with no amounts; the notification channel is created once in `LedgerApp.onCreate`.
- `LedgerApp` collects the current and previous months for widgets (debounced, distinct) and only while widgets exist; the periodic reconcile worker is scheduled only while capture is enabled.
- Backup export is read back and verified after writing, and restore first writes an encrypted safety copy to `files/backup-safety/pre-restore.plbackup`.
- Seeded bank account suffixes come from the git-ignored asset `local-accounts.properties`; without it only Cash is seeded.
- Widgets: `MonthlyWidgetProvider` alone handles date, time and timezone broadcasts and an inexact alarm fired just after Asia/Kolkata midnight (which also covers month boundaries). Tab taps re-render only their own widget. Text colours are resolved when rendering because RemoteViews cannot call `setSelected`, so an application `ComponentCallbacks` re-renders after a font-scale or night-mode change while the process is alive; otherwise the next update picks it up. The budget widget stacks two progress bars with fixed tints and shows the red one when overspent. Failed loads show the cached month as "Not updated" or a "Can't load data" state. Pure helpers live in `WidgetLogic.kt`.
- Overview's report is computed only while Overview, or a metric detail opened from it, is the visible root, so a cold Overview briefly shows its skeleton. After a month change Overview and Transactions keep the previous result until the new one is ready.
- `monthlySummary` delegates to a single-pass `monthlySummaries` that buckets entries by epoch bounds in Asia/Kolkata; results match the previous per-entry implementation.

## Data invariants

- Money is integer paise; transaction and month boundaries use Asia/Kolkata.
- Income/spending reports exclude deleted entries and transfers. Account balances include transfer effects.
- Recurring category budgets apply to each selected month. Overspending retains a negative remaining amount.
- Renaming an existing budget replaces its old category in one transaction; it cannot overwrite a different category's budget.
- The 50/30/20 income allocation is a planning tool, separate from budgets and actual savings.
- Preserve SMS keys, manual corrections, tombstones, backup compatibility, and Room migrations. Schemas are tracked under `app/schemas/`.
- Restoring replaces validated data and disables capture until explicitly enabled again.
- Backup JSON uses explicit format keys, independent of release field obfuscation. Export and restore share a 32 MiB encrypted-file limit. Restore rejects duplicate SMS associations and malformed complete SMS records before replacing data.
- Repository deletion and Inbox dismissal resolve current records under the ingestion lock. Stale actions cannot overwrite newer corrections, recreate missing entries, or change an already saved message back to dismissed. Deleted entries cannot be resurrected by an old editor.
- An unavailable SMS provider cursor fails reconciliation without advancing its scan boundary. Receiver completion is unconditional even if immediate recovery scheduling fails; cancellation is propagated and failure diagnostics contain no SMS contents.
- Processing stays local; the application declares no internet permission.

## Tests and performance

JVM tests cover parsing, reports, ledger behavior, and reminder eligibility. Device tests cover repository/database behavior and Compose journeys using the validation package. The benchmark module and benchmark-only app fixtures are isolated from the personal package. Functional UI results do not establish startup or frame-time performance; see [development](development.md) and [performance notes](ui-redesign.md).

Category-only review resolves the current entry under the repository mutex and updates only its category and optional current-payee rule. Notifications clear after commit. Budget removal deletes only the recurring limit. See [screen specification](review-redesign.md).
