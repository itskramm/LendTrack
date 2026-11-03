# LendTrack Gap Analysis — HTML Prototype vs. Kotlin Implementation

**Date:** 2025  
**Prototype:** `LendTrack — Your Lending, Organized (3).html`  
**Codebase root:** `app/src/main/java/com/lendtrack/app/`

---

## Summary

The Kotlin implementation is substantially complete and in many respects **exceeds** the HTML prototype (real Firebase auth, Room persistence, WorkManager notifications, FIFO payment allocation preview, sync status, borrower detail with edit). However **7 concrete gaps** remain:

1. **Notification bell in the top bar is completely unwired** — `onClick = {}` stub, no in-app notification panel shown.
2. **`InstallmentRow` click is a stub** — tapping a payment row in "Upcoming Payments" does nothing (`/* open installment detail */` comment).
3. **`PaymentTableRow` click is a stub** — tapping a payment in the Payments list does nothing (`/* open payment detail */` comment).
4. **Ledger `displayBalance` falls back to `loan.totalPayable`** when installments aren't loaded yet — shows wrong (full-gross) balance until the user expands a row.
5. **`PaymentsViewModel` is missing `onSearch`** — the function is called from `PaymentsScreen` but does not exist in `PaymentsViewModel`; filtering is done inline in the composable against the stale full list.
6. **Date picker in Payments filter is a plain text field**, not a date picker — inconsistent with the prototype's `<input type="date">` and the rest of the app's `LtDatePickerField`.
7. **Borrower Detail `AddBorrowerDialog` re-uses its `initial` snapshot incorrectly** — `remember(viewModel.showEditDialog)` does not re-read the borrower when the dialog opens because the composable key changes only when the `Boolean` flips, not when data loads.

No features are wholesale missing — CSV import (prototype-only), the Borrowers screen, the full reports/export, the auth flow, and notifications are all implemented.

---

## Screen-by-Screen Evidence

### 1. Dashboard

**Prototype renders:**
- 4 stat cards: Total Outstanding, Collected This Month, Due Today, Overdue Payments
- Upcoming payments table with search + status filter, borrower-name link, "Pay" button per row
- Mini calendar (clickable days with color-coded event chips)
- Balance overview donut chart with legend
- Recent Activity feed
- Quick Actions: Add Borrower, Record Payment, View Calendar

**Kotlin implementation (`DashboardScreen.kt`, `DashboardViewModel.kt`):**
- ✅ All 4 stat cards rendered via `StatCardsGrid`, data from live Room flows
- ✅ Upcoming payments table with search + status dropdown, `onBorrowerClick` and `onRecordPayment` wired
- ✅ Balance overview donut via `CircularProgressIndicator` (correct semantics, not CSS conic-gradient)
- ✅ Recent Activity panel (`ActivityRow`) wired to `ActivityLogEntity` flow
- ✅ Quick Actions wired to navigation callbacks
- ✅ Tablet-landscape two-column layout
- ⚠️ **Mini calendar is absent from the Dashboard** — the prototype embeds `<Cal>` in the right column of the dashboard; the Kotlin layout replaces it with `RecentActivityPanel` + `QuickActionsPanel`. The calendar is only on the separate PaymentCalendar screen. This is a deliberate design change (not a regression), but worth noting.
- ❌ **Notification bell `onClick = {}`** — `LtTopBar` in `LendTrackApp.kt` line ~178 has `onClick = {}` with no notification panel shown. Prototype shows a popover with unread count, mark-all-read, and per-notification "Read" buttons.

**File:** `ui/LendTrackApp.kt` — `LtTopBar` composable, notification `IconButton`.

---

### 2. Borrowers

**Prototype renders:**
- Table: Name, Contact, Loans, Outstanding, Status
- Search field
- "Add Borrower" button → modal with name*, contact*, address, notes
- Clicking a row → "Borrower Detail" modal: contact info, outstanding, loan list, payment history, "Record payment" button

**Kotlin implementation (`BorrowersScreen.kt`, `BorrowerDetailScreen.kt`, `BorrowersViewModel.kt`, `BorrowerDetailViewModel.kt`):**
- ✅ Table with Name, Contact, Prefers (extra column), Loans, Outstanding, Status
- ✅ Search field wired via `viewModel.onSearchQuery`
- ✅ "Add Borrower" button opens `AddBorrowerDialog` with full extended form (name, contact, preferred channel, secondary contact, gov ID, address, barangay, occupation, notes) — richer than prototype
- ✅ Row click navigates to dedicated `BorrowerDetailScreen` (full screen, not modal) — richer
- ✅ Borrower detail shows contact info, outstanding balance, loans list with expandable installments, payment history
- ✅ Edit borrower wired: `viewModel.openEdit()` / `viewModel.saveEdit(fields)` — **not present in prototype at all**, it's a net improvement
- ✅ "Add Loan" button on detail wired to `onAddLoan` callback
- ✅ "Record Payment" button on each loan wired to `onRecordPayment`
- ⚠️ **Edit dialog prefill bug**: `BorrowerDetailScreen.kt` line ~44:
  ```kotlin
  val initial = remember(viewModel.showEditDialog) { viewModel.currentBorrowerAsFields() }
  ```
  The `initial` value is captured but **never passed to `AddBorrowerDialog`**. The dialog's `fields` state starts fresh from `BorrowerFormFields()` on every open. The borrower's existing data is not pre-populated in the edit form. `AddBorrowerDialog` does not accept an `initialFields` parameter.

**File:** `feature/borrowers/BorrowerDetailScreen.kt` line ~44; `BorrowersScreen.kt` `AddBorrowerDialog`.

---

### 3. Payment Calendar

**Prototype renders:**
- Status filter chips: All, Paid, Upcoming, Due Soon, Overdue
- Full-month calendar grid with colored event chips (amount labels)
- "Record Payment" button in header
- Below calendar: selected-day list of installments with borrower name link, amount, status badge, "Record payment" per row

**Kotlin implementation (`PaymentCalendarScreen.kt`, `CalendarViewModel.kt`):**
- ✅ Status filter chips wired to `viewModel.setStatusFilter`
- ✅ Calendar grid via `LtCalendarGrid` — correct day-of-week offset, event dots, today highlight, selected highlight
- ✅ Month navigation (previous/next) wired
- ✅ "Record Payment" header button wired to `onRecordPayment("")`
- ✅ Selected-day installment list shown below grid, filtered by status, with "Record payment" per row wired to `onRecordPayment(iwb.loan.id)`
- ✅ Legend row shown
- ⚠️ **Borrower-name is not a tappable link** in the selected-day list — prototype has `<button class="lk">` navigating to the borrower detail. The Kotlin row just shows a `Text` with no `clickable`.

**File:** `feature/calendar/PaymentCalendarScreen.kt` — the day-installments `forEach` block (around line ~105).

---

### 4. Balance Ledger

**Prototype renders:**
- Search field
- Table: Borrower, Principal, Interest, Payable, Applied, Loan Balance
- Clicking row expands installment breakdown inline
- Transaction history section below the table

**Kotlin implementation (`BalanceLedgerScreen.kt`, `LedgerViewModel.kt`):**
- ✅ Search field wired
- ✅ Table with Borrower, Principal, Interest, Payable, Balance columns (Applied column is dropped — see below)
- ✅ Click-to-expand installment breakdown with lazy load via `viewModel.getInstallmentsForLoan`
- ✅ Transaction history section with borrower name, amount, date, method, sync badge
- ❌ **"Applied" column is missing from the table header and rows** — prototype shows `<th>Applied</th>` with `{P(l.applied)}` per row. The Kotlin table omits it entirely; `LoanSummary.totalApplied` exists in the view model but is never displayed in the row.
- ❌ **`displayBalance` fallback is wrong**: `LedgerRow` uses `installments?.sumOf { it.remaining } ?: ls.loan.totalPayable` — before the user expands, it shows gross `totalPayable` as the balance. Should show `ls.loan.totalPayable - ls.totalApplied` (which is computed correctly in `LedgerViewModel` as `totalPaid`).

**File:** `feature/ledger/BalanceLedgerScreen.kt` — `LedgerRow` composable; `LedgerViewModel.kt` — `LoanSummary` is built correctly but `displayBalance` fallback is wrong.

---

### 5. Payments

**Prototype renders:**
- Table: Date, Borrower, Method, Amount
- Filter by borrower (text) and by date (date picker)
- "Record Payment" button in header
- Clicking a row → modal with payment details (borrower, amount, method, date, loan ID)

**Kotlin implementation (`PaymentsScreen.kt`, `PaymentsViewModel.kt`):**
- ✅ Table with Date, Borrower, Method, Amount, and an extra Sync column (richer)
- ✅ Borrower text filter wired (inline in composable against `payments` StateFlow)
- ✅ "Record Payment" header button wired to `onRecordPayment`
- ❌ **`viewModel.onSearch(query)` is called but `PaymentsViewModel` has no `onSearch` function** — the LaunchedEffect on line ~32 calls it but the VM only exposes `payments` directly without a search mechanism. The inline filter in the composable works for display, but the ViewModel call will cause a compile error (or was removed post-investigation). **Confirmed missing**: `PaymentsViewModel.kt` does not define `onSearch`.
- ❌ **Date filter is a plain `LtSearchField`** — prototype uses `<input type="date">`. The Kotlin uses a text field with placeholder "Date (YYYY-MM-DD)". Inconsistent with the `LtDatePickerField` widget used elsewhere in the app.
- ❌ **`PaymentTableRow` click is a dead stub**: `clickable { /* open payment detail */ }` — nothing happens. Prototype shows a payment detail modal.

**File:** `feature/payments/PaymentsScreen.kt` — `LaunchedEffect(query)` block and `PaymentTableRow`; `feature/payments/PaymentsViewModel.kt` — missing `onSearch`.

---

### 6. Reports

**Prototype renders:**
- 3 stat cards: Principal Lent, Outstanding, Overdue (with count)
- Date range pickers (From / To)
- "Collections X–Y: ₱Z" line
- "Export CSV" button → modal showing CSV text (copy-only in prototype)

**Kotlin implementation (`ReportsScreen.kt`, `ReportsViewModel.kt`):**
- ✅ 3 stat cards rendered from `ReportSummary` flow
- ✅ Date range pickers using `LtDatePickerField` — correct widget
- ✅ `collectedInRange` computed via `viewModel.getCollectedInRange(from, to)` — accepts from/to params ✅
- ✅ "Export CSV" button calls `viewModel.exportCsv(rangeFrom, rangeTo)` — triggers Android ShareSheet with a real CSV file, richer than prototype's copy-to-textarea
- ✅ `exportCsv` correctly uses `from`/`to` params to filter payments — **fully implemented, no stub**
- ✅ No gaps — Reports is the most complete screen.

---

## Cross-Cutting Checks

### Navigation (`LendTrackNavGraph.kt`)

| Route | Status |
|---|---|
| `dashboard` | ✅ Wired, all callbacks populated |
| `borrowers` | ✅ Wired |
| `borrowers/{borrowerId}` | ✅ Wired with `onNavigateBack`, `onRecordPayment`, `onAddLoan` |
| `loans/add?borrowerId=` | ✅ Wired |
| `calendar` | ✅ Wired, `onRecordPayment` passes loanId |
| `ledger` | ✅ Wired (no callbacks needed) |
| `payments` | ✅ Wired, `onRecordPayment` navigates to record screen |
| `payments/record?loanId=` | ✅ Wired, loanId passed correctly |
| `reports` | ✅ Wired |
| `settings` | ✅ Wired, `onSignOut` passed |

No missing routes. Auth routes (splash/login) are handled in `MainActivity`, not the NavGraph.

### App Shell (`LendTrackApp.kt`)

- ✅ Sidebar persistent on tablet (≥600 dp), drawer on phone — correct
- ✅ Offline banner with `AnimatedVisibility`
- ✅ `requiresAttentionCount` banner with "Review" → Settings nav
- ✅ `LtTopBar` greeting with time-of-day logic
- ❌ **Notification bell `onClick = {}`** — no panel, no unread count badge display (the `Box` wrapping the `IconButton` has no badge child). Prototype has bell icon with unread count badge + popover.

### Auth Screens

- ✅ `SplashScreen.kt` — animated logo, shown during `AuthUiState.Loading`
- ✅ `LoginScreen.kt` — email/password fields, show/hide password toggle, error display, loading spinner, wired to `AuthViewModel`
- ✅ `AuthViewModel.kt` — Firebase `signInWithEmailAndPassword`, `signOut`, `clearError`, friendly error mapping
- ✅ `MainActivity.kt` — correctly routes among `SplashScreen`, `LoginScreen`, `LendTrackApp` based on auth state
- ✅ No landing/auth view stub — this is fully implemented

### Notification Files

- ✅ `NotificationChannels.kt` — channel constant defined
- ✅ `DueDateNotificationScheduler.kt` — `PeriodicWorkRequest` at 8 AM daily, `KEEP` policy, correct initial delay
- ✅ `DueDateNotificationWorker.kt` — checks due today/tomorrow AND overdue, posts per-borrower notifications, correct permission check for Android 13+
- ✅ `LendTrackApplication.kt` — `createNotificationChannels()` called in `onCreate`, `notificationScheduler.scheduleDailyCheck()` called, `HiltWorkerFactory` configured
- ❌ **Android 13 POST_NOTIFICATIONS runtime permission is never requested** — the worker silently skips if the permission is denied, but there is no code in `MainActivity` or `LendTrackApp` that requests `Manifest.permission.POST_NOTIFICATIONS` at runtime. On Android 13+ devices, the user will never see the permission dialog and will receive no notifications.

### SettingsScreen

- ✅ `onSignOut` is wired: the "Sign Out" button sets `showSignOutDialog = true` → confirm dialog → calls `onSignOut()`. **Fully implemented.**

### ReportsViewModel `exportCsv`

- ✅ Accepts `from: String, to: String` parameters, filters payments by date range. **Fully implemented.**

### BorrowerDetailScreen — Duplicate Block

- ✅ No duplicate block found. The previous reported duplicate `AddBorrowerDialog` invocation at the top is the *edit* dialog, not a duplicate add-borrower section. The screen has one dialog block for editing.
- ⚠️ The prefill bug remains (see Borrowers section above).

### PaymentsViewModel — Dead `_query`

- ✅ No `_query` state flow exists in the current `PaymentsViewModel.kt`. However, `onSearch` is still called from `PaymentsScreen.kt` line ~32 (`LaunchedEffect(query) { viewModel.onSearch(query) }`), and `onSearch` does not exist in the ViewModel. **This is a compile error.** Either `onSearch` was removed from the VM without updating the screen, or it was never added.

---

## Consolidated Gap List

| # | Screen / Component | Gap | Severity |
|---|---|---|---|
| G-1 | Top Bar (`LendTrackApp.kt`) | Notification bell `onClick = {}`, no badge, no in-app panel | Medium |
| G-2 | Dashboard — `InstallmentRow` | Row tap `clickable { /* open installment detail */ }` is a stub | Low |
| G-3 | Payments — `PaymentTableRow` | Row tap `clickable { /* open payment detail */ }` is a stub | Low |
| G-4 | Payments — `PaymentsViewModel` | `onSearch` called from screen but does not exist in VM — compile error | **High** |
| G-5 | Payments — Date Filter | Plain text field, not `LtDatePickerField` | Low |
| G-6 | Balance Ledger — `LedgerRow` | `displayBalance` falls back to `loan.totalPayable` instead of `loan.totalPayable - ls.totalApplied` | Medium |
| G-7 | Balance Ledger — Table | "Applied" column missing from header and rows | Low |
| G-8 | Calendar — Day Installment List | Borrower name is plain `Text`, not tappable link | Low |
| G-9 | Borrower Detail — Edit Dialog | `AddBorrowerDialog` has no `initialFields` param; edit form opens blank instead of pre-filled | Medium |
| G-10 | Notifications — Permission | `POST_NOTIFICATIONS` runtime permission never requested on Android 13+ | **High** |

---

## Recommendations

### Fix Immediately (compile-breaking / silent failure)

**G-4 — `PaymentsViewModel.onSearch` missing:**  
Add `fun onSearch(query: String) {}` to `PaymentsViewModel` (no-op is fine since filtering is done in the composable), or remove the `LaunchedEffect` call from `PaymentsScreen`. The current state will not compile.

**G-10 — POST_NOTIFICATIONS permission:**  
In `MainActivity.onCreate` or a first-run `LaunchedEffect` in `LendTrackApp`, call `ActivityCompat.requestPermissions` for `Manifest.permission.POST_NOTIFICATIONS` on API 33+. Without this, the notification worker will silently do nothing on Android 13+ devices.

### Fix Soon (UX regression vs. prototype)

**G-1 — Notification bell:**  
Add an unread-count badge (use `BadgedBox` from Material3) and a `Popup`/`DropdownMenu` panel showing pending notifications. The `AppShellViewModel` or a new `NotificationsViewModel` should supply the data.

**G-6 — Ledger balance fallback:**  
Change `LedgerRow.displayBalance` to `installments?.sumOf { it.remaining } ?: (ls.loan.totalPayable - ls.totalApplied)` so the pre-expand value is correct.

**G-9 — Edit dialog prefill:**  
Add `initialFields: BorrowerFormFields = BorrowerFormFields()` to `AddBorrowerDialog` and initialize `var fields by remember { mutableStateOf(initialFields) }`. In `BorrowerDetailScreen`, pass `initial` to the dialog.

### Fix When Convenient (polish)

**G-2 / G-3 — Dead row taps:**  
Create a lightweight installment detail and payment detail bottom sheet or dialog. The data models are already available in scope.

**G-5 — Date filter widget:**  
Replace the plain `LtSearchField` for the date filter in `PaymentsScreen` with `LtDatePickerField`.

**G-7 — Applied column in Ledger:**  
Add `Text("Applied", ...)` to the `LedgerRow` header and render `ls.totalApplied.peso()` in the row. Weight adjustments required.

**G-8 — Calendar borrower link:**  
Wrap the borrower `Text` in the selected-day list with `.clickable { onBorrowerClick(iwb.borrower.id) }` and add an `onBorrowerClick: (String) -> Unit` param to `PaymentCalendarScreen`.
