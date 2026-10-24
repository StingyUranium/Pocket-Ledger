# Spending review design specification

## Navigation and inventory
Overview ? Insights ? category/type transaction list ? editor. Primary tabs are Overview, Activity, Budgets, Inbox. Management opens Accounts, Income plan and Settings. Accounts retains account editing and transaction transfer creation. Widget income/expense/budget routes and notification entry IDs retain their meaning. Legacy numeric destinations restore through an explicit mapping.

## Annotated layouts
Overview: selected month in collapsing header; full-width Spent metric; secondary Income metric and Income remaining (income minus spending); review actions; budget remaining; Insights link; category shares; recent activity. Synthetic example: income ?50,000, spending ?32,000, income remaining ?18,000; this is never labeled account balance.
Activity: scrolling search; type/account/category chips and Needs category action; date sections; transaction rows. Row tap opens full editing; an independent Choose category action opens a focused picker with optional remembered payee rule. Save stays open on failure.
Transaction editor: type, amount, account/destination, payee, category and date first; expandable Details contains optional time and note. Remember category and original source follow; deletion requires confirmation.
Budgets: remaining across budgeted categories, spent/limit comparisons, signed remaining per category; pinned Add; category editor with rename, positive limit and explicit Remove budget confirmation. Removal affects only the recurring limit.
Inbox: unresolved alerts and specific issue first; original messages collapsed; capture, historical import, trusted sender review and possible transfers follow in separate sections. Saved uncategorized entries belong to Activity.
Insights: category shares, daily inspection and monthly history; retain text values and transaction drill-downs.
Accounts: calculated balance, opening balance/date and latest bank-reported balance/date distinguished; account edit and add preserved.
Income plan: separate destination; 50/30/20 targets and income editing; recorded savings never inferred from remaining income.
Settings: grouped appearance, permissions, encrypted backup export/restore, trusted senders and remembered rules. Pickers, date selection, import preview and permission explanation use existing native sheets and grouped fields. Confirm alerts use destructive red actions; backup password fields retain protected input.

## Components and states
Use PocketTheme typography/shapes, LedgerSpace, LedgerMotion, AppIcon, GroupedSection/Row/Item/Field/Divider. At large font scales the compact tab bar uses two rows of two destinations so labels remain readable. One interactive accent, dynamic color, grouped light surfaces and black dark base apply to every destination and sheet. Preserve Haze overlays, header/bottom insets, predictive back, metric transitions, reduced motion and native sheet scrolling.
Loading uses skeletons; initial read failure offers Retry; stale snapshot failure preserves content and offers Retry. Empty Activity offers filter reset; empty budgets offer Add; empty Inbox confirms no pending reviews. Denied capture permission offers explanation/settings without blocking manual entry. Unresolved alerts show their reason. Overspending retains a signed negative remaining amount and textual Over budget indicator. Failed mutations retain drafts and show the operation error.

## Coverage checklist and acceptance matrix
For every destination above and all supporting sheets inspect light/dark, loading/empty/error, uncategorized and permission denial where applicable. Use synthetic data only. Inspect narrow portrait, landscape/adaptive, long labels, large amounts, keyboard and 200% fonts; require 48dp actions and spoken action labels. Exercise categorization, editor recreation, dismissal/Undo, import, budget rename/removal, backup and launch routes. Preserve selected month, per-destination filters/list position and drafts across tab changes/recreation/resizing. Home widgets keep existing design.

This is the intended design and acceptance checklist, not evidence that every state/device combination has passed. Record actual validation separately in ui-redesign.md.

## Screen state specification

| Screen or flow | Empty / initial | Failure / denial | Review / exceptional data |
|---|---|---|---|
| Overview | Zero monthly metrics; account setup and import actions | Shared snapshot Retry; retain previous content on refresh failure | Category-review and alert-review actions; negative income remaining |
| Activity and drill-downs | No matches with Clear filters; loading filter text | Shared read error and retry | Choose category independently of full editor; transfers never offer categorization |
| Budgets and editor | Remaining summary plus Add; blank new limit | Save/remove errors stay in editor | Signed overspending; rename collision rejected; removal confirmation |
| Inbox | No pending alerts explanation; All messages option | SMS permission explanation; refresh failures retain messages | Specific review reason; source collapsed; saved entries link to Activity |
| Insights | No spending explanation and text alternatives | Shared snapshot Retry | Category and monthly amounts accompany charts; transaction drill-down |
| Accounts and editor | Add account; explicit opening date/balance | Invalid suffix/date/mapping shown without closing editor | Calculated and bank-reported values remain distinct |
| Management | Always show Accounts, Income plan, Settings | Shared snapshot Retry | Back returns to the primary tab that opened Manage |
| Income plan | Set monthly income; zero targets | Invalid amount stays in income sheet | Targets remain separate from recorded results and limits |
| Settings | Empty trusted/rule groups have explanatory footers | Permission state and app settings action | Appearance, backups, trusted senders and remembered categories grouped |
| Transaction editor | Type/amount/account/category/date first | Field errors retain draft; operation errors remain visible | Details and source disclosure preserve optional values; delete confirmation |
| Category/account pickers and date | Current selection, Cancel and native date affordance | Invalid custom category/date retains draft | Type-specific categories and distinct transfer destination |
| Import preview | No selected/new candidates clearly distinguished | Permission required before scan; failed import retains preview | Show account mapping and duplicate status before committing |
| Permission and backups | Explain local processing; protected backup password | Denial leaves manual entry available; wrong password preserves data | Restore replaces validated data and disables capture; confirmations use compact alert |

The same state layouts inherit both theme palettes and reduced motion. Raw screenshot evidence uses synthetic ledger data and stays ignored.
