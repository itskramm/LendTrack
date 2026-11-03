# Implementation Plan: 6 Gap Fixes

This plan fixes 6 specific gaps identified during design comparison. Each item includes the exact file paths, line numbers where possible, and specific code changes needed.

---

## G-6: Ledger balance fallback (MEDIUM)

**File:** `app/src/main/java/com/lendtrack/app/feature/ledger/BalanceLedgerScreen.kt`

**Problem:** Line 89 has the wrong fallback calculation:
```kotlin
val displayBalance = installments?.sumOf { it.remaining } ?: ls.loan.totalPayable
```

The fallback should be `totalPayable - totalApplied`, not just `totalPayable`.

**What to do:**
1. Locate the `LedgerRow` composable function (starts around line 75)
2. Find the `displayBalance` calculation (line 89)
3. Replace the entire line with:
   ```kotlin
   val displayBalance = installments?.sumOf { it.remaining } ?: (ls.loan.totalPayable - ls.totalApplied)
   ```

**Files to modify:**
- `app/src/main/java/com/lendtrack/app/feature/ledger/BalanceLedgerScreen.kt`

**Verify:**
Run `./gradlew assembleDebug` — build succeeds without errors. Open the Balance Ledger screen in the app and confirm that when you tap a row to expand it, the balance matches the sum of installment remainings; when collapsed (before installments load), the balance equals `totalPayable - totalApplied`.

---

## G-7: Applied column missing from ledger table (LOW)

**File:** `app/src/main/java/com/lendtrack/app/feature/ledger/BalanceLedgerScreen.kt`

**Problem:** The table header (line 49-54) and the `LedgerRow` composable (line 95-106) are missing an "Applied" column. The `LoanSummary.totalApplied` field exists in the domain model but is never displayed.

**What to do:**
1. In `BalanceLedgerScreen` function, find the table header Row (starts line 49)
2. After the "Interest" Text (line 52), add:
   ```kotlin
   Text("Applied",   style = MaterialTheme.typography.titleMedium, color = LtBurgundy, modifier = Modifier.weight(1f))
   ```
3. Adjust the existing "Payable" and "Balance" columns to keep the total weights balanced (change each from `weight(1f)` to `weight(0.9f)` if needed, or leave as-is if horizontal space permits)
4. In the `LedgerRow` composable (line 95-106), after the line:
   ```kotlin
   Text(ls.loan.interestAmount.peso(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
   ```
   Add:
   ```kotlin
   Text(ls.totalApplied.peso(),        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
   ```
5. Adjust the "Payable" and "Balance" column weights to match the header changes

**Files to modify:**
- `app/src/main/java/com/lendtrack/app/feature/ledger/BalanceLedgerScreen.kt`

**Verify:**
Run `./gradlew assembleDebug` and open the Balance Ledger screen. Confirm that the table header shows: Borrower | Principal | Interest | **Applied** | Payable | Balance, and that each row displays the `totalApplied` amount in the new column.

---

## G-8: Calendar borrower name not tappable (LOW)

**File:** `app/src/main/java/com/lendtrack/app/feature/calendar/PaymentCalendarScreen.kt`

**Problem:** In the selected-day installment list (around line 105), the borrower name is plain Text, not a clickable link. The screen needs an `onBorrowerClick` parameter to navigate to the borrower detail screen.

**What to do:**
1. **Modify the `PaymentCalendarScreen` composable signature** (line 19):
   ```kotlin
   @Composable
   fun PaymentCalendarScreen(
       onRecordPayment: (loanId: String) -> Unit = {},
       onBorrowerClick: (borrowerId: String) -> Unit = {}, // ADD THIS LINE
       viewModel:       CalendarViewModel = hiltViewModel(),
   ) {
   ```

2. **Wire the click handler in the installment list** (around line 111-135):
   - Find the Row that displays each installment (starts line 116)
   - Locate the Text displaying `iwb.borrower.name` (line 127)
   - Wrap it with `.clickable { onBorrowerClick(iwb.borrower.id) }`
   - The updated Text should be:
     ```kotlin
     Text(
         iwb.borrower.name,
         style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = LtBurgundy),
         modifier = Modifier.clickable { onBorrowerClick(iwb.borrower.id) }
     )
     ```

3. **Update the nav graph** in `app/src/main/java/com/lendtrack/app/navigation/LendTrackNavGraph.kt` (around line 68):
   - Find the `composable(Screen.PaymentCalendar.route)` block
   - Add the `onBorrowerClick` parameter:
     ```kotlin
     composable(Screen.PaymentCalendar.route) {
         PaymentCalendarScreen(
             onRecordPayment = { loanId ->
                 navController.navigate("payments/record?loanId=$loanId")
             },
             onBorrowerClick = { borrowerId ->
                 navController.navigate("borrowers/$borrowerId")
             },
         )
     }
     ```

**Files to modify:**
- `app/src/main/java/com/lendtrack/app/feature/calendar/PaymentCalendarScreen.kt`
- `app/src/main/java/com/lendtrack/app/navigation/LendTrackNavGraph.kt`

**Verify:**
Run `./gradlew assembleDebug` and open the Payment Calendar screen. Select a day that has installments. Tap a borrower name in the list and confirm that it navigates to the borrower detail screen.

---

## G-9: Edit dialog doesn't prefill borrower data (MEDIUM)

**Files:** `app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailScreen.kt`, `app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowersScreen.kt`

**Problem:** The `AddBorrowerDialog` composable (defined in `BorrowersScreen.kt` starting line 127) does not accept an `initialFields` parameter, so when called from the edit flow in `BorrowerDetailScreen.kt` (line 37), the form starts blank instead of showing the borrower's current data.

**What to do:**

### 1. Modify `AddBorrowerDialog` in `BorrowersScreen.kt` (line 127)
   - Add an `initialFields` parameter to the function signature:
     ```kotlin
     @Composable
     fun AddBorrowerDialog(
         initialFields: BorrowerFormFields = BorrowerFormFields(), // ADD THIS
         errorMessage: String?,
         onDismiss:    () -> Unit,
         onSave:       (BorrowerFormFields) -> Unit,
     ) {
     ```
   - Change the `remember` initialization (line 133) from:
     ```kotlin
     var fields by remember { mutableStateOf(BorrowerFormFields()) }
     ```
     to:
     ```kotlin
     var fields by remember(initialFields) { mutableStateOf(initialFields) }
     ```

### 2. Update the call site in `BorrowerDetailScreen.kt` (line 37)
   - The current invocation is:
     ```kotlin
     AddBorrowerDialog(
         errorMessage = viewModel.editError,
         onDismiss    = { viewModel.dismissEdit() },
         onSave       = { fields -> viewModel.saveEdit(fields) },
     )
     ```
   - Change it to:
     ```kotlin
     AddBorrowerDialog(
         initialFields = initial,
         errorMessage  = viewModel.editError,
         onDismiss     = { viewModel.dismissEdit() },
         onSave        = { fields -> viewModel.saveEdit(fields) },
     )
     ```
   - The `initial` variable is already computed on line 36 via `viewModel.currentBorrowerAsFields()`

### 3. Update the call site in `BorrowersScreen.kt` (line 88)
   - The current invocation already omits `initialFields`, so no change needed — the default value `BorrowerFormFields()` will be used for the "Add" flow

**Files to modify:**
- `app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowersScreen.kt` (function signature + remember line)
- `app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailScreen.kt` (call site only)

**Verify:**
Run `./gradlew assembleDebug` and open a borrower detail screen. Tap the "✎ Edit" button. Confirm that the dialog opens with all fields pre-filled with the borrower's current data. Make a change, save, and confirm the update persists.

---

## G-2: Dashboard installment row tap is a stub (LOW)

**File:** `app/src/main/java/com/lendtrack/app/feature/dashboard/DashboardScreen.kt`

**Problem:** The `InstallmentRow` composable (line 350) has `.clickable { /* open installment detail */ }` — nothing happens when you tap a row.

**What to do:**

### 1. Create the `InstallmentDetailSheet` composable
Add this new composable at the end of `DashboardScreen.kt` (after line 463, before the file-end extensions):

```kotlin
// ─────────────────────────────────────────────────────────────────────────────
// Installment detail bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun InstallmentDetailSheet(
    iwb:       InstallmentWithBorrower,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = LtSurface,
        shape            = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Installment Detail", style = MaterialTheme.typography.headlineMedium, color = LtBurgundy)
            LtDivider()

            DetailRow("Borrower",     iwb.borrower.name)
            DetailRow("Loan ID",      iwb.loan.id.take(8))
            DetailRow("Installment",  "#${iwb.installment.installmentNumber} of ${iwb.loan.numberOfInstallments}")
            DetailRow("Due Date",     iwb.installment.dueDate.formatFull())
            DetailRow("Amount Due",   iwb.installment.amount.peso())
            DetailRow("Amount Paid",  iwb.installment.amountPaid.peso())
            DetailRow("Remaining",    iwb.installment.remaining.peso())

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Status", style = MaterialTheme.typography.bodySmall, color = LtMuted, modifier = Modifier.weight(1f))
                LtStatusBadge(iwb.installment.statusOn(today))
            }

            Spacer(Modifier.height(8.dp))
            LtButton(text = "Close", onClick = onDismiss, ghost = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = LtMuted)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = LtText)
    }
}
```

### 2. Wire the sheet state in `DashboardScreen` composable
At the top of the `DashboardScreen` function (after the `collectAsStateWithLifecycle()` calls, around line 273):
```kotlin
var selectedInstallment by remember { mutableStateOf<InstallmentWithBorrower?>(null) }
```

### 3. Update the `InstallmentRow` clickable (line 350)
Change:
```kotlin
.clickable { /* open installment detail */ }
```
to:
```kotlin
.clickable { selectedInstallment = iwb }
```

### 4. Show the sheet when selectedInstallment is not null
At the end of the `DashboardScreen` Column (after the closing brace of the if/else for landscape/portrait, before the final `}` of the DashboardScreen function):
```kotlin
    // Show installment detail sheet
    selectedInstallment?.let { iwb ->
        InstallmentDetailSheet(
            iwb       = iwb,
            onDismiss = { selectedInstallment = null },
        )
    }
```

**Files to modify:**
- `app/src/main/java/com/lendtrack/app/feature/dashboard/DashboardScreen.kt`

**Verify:**
Run `./gradlew assembleDebug` and open the Dashboard. Tap an installment row in the "Upcoming Payments" panel. Confirm that a bottom sheet appears showing: borrower name, loan ID, installment number, due date, amount, amount paid, remaining, and status badge. Tap "Close" or swipe down to dismiss.

**Additional imports needed:**
At the top of `DashboardScreen.kt`, add:
```kotlin
import androidx.compose.material3.ModalBottomSheet
```

---

## G-1: Notification bell has no badge and no dropdown (MEDIUM)

**File:** `app/src/main/java/com/lendtrack/app/ui/LendTrackApp.kt`

**Problem:** The notification bell `IconButton` (line 180) has `onClick = {}` and no unread count badge. It should show a badge with `requiresAttentionCount` (already available from `AppShellViewModel`) and open a dropdown listing overdue/due-today installments.

**What to do:**

### 1. Wrap the notification bell icon in a `BadgedBox` (line 178-185)
Replace the entire notification bell Box block:
```kotlin
// Before:
Box {
    IconButton(
        onClick  = {},
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(LtSurface)
            .border(1.dp, LtDusty, RoundedCornerShape(50)),
    ) {
        Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = LtBurgundy)
    }
}
```

With:
```kotlin
var showNotifications by remember { mutableStateOf(false) }
Box {
    BadgedBox(
        badge = {
            if (attentionCount > 0) {
                Badge(
                    containerColor = LtRed,
                    contentColor   = Color.White,
                ) {
                    Text(attentionCount.toString(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                }
            }
        }
    ) {
        IconButton(
            onClick  = { showNotifications = !showNotifications },
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(LtSurface)
                .border(1.dp, LtDusty, RoundedCornerShape(50)),
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = LtBurgundy)
        }
    }

    // Dropdown menu showing overdue/due-today installments
    DropdownMenu(
        expanded         = showNotifications,
        onDismissRequest = { showNotifications = false },
        containerColor   = LtSurface,
        modifier         = Modifier.widthIn(min = 280.dp, max = 400.dp),
    ) {
        if (attentionCount > 0) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Requires Attention", style = MaterialTheme.typography.titleMedium, color = LtBurgundy)
                        Text("$attentionCount operation${if (attentionCount != 1) "s" else ""} failed to sync", style = MaterialTheme.typography.bodySmall, color = LtMuted)
                    }
                },
                onClick = {
                    showNotifications = false
                    navigate(Screen.Settings.route)
                },
                leadingIcon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = LtRed) },
            )
            HorizontalDivider(color = LtDivider)
        }
        DropdownMenuItem(
            text = {
                Column {
                    Text("View Calendar", style = MaterialTheme.typography.bodyMedium, color = LtBurgundy)
                    Text("See all upcoming payments", style = MaterialTheme.typography.bodySmall, color = LtMuted)
                }
            },
            onClick = {
                showNotifications = false
                navigate(Screen.PaymentCalendar.route)
            },
            leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = LtDusty) },
        )
    }
}
```

### 2. Add a navigate helper function inside `LtTopBar` (right after the composable signature, line 166)
```kotlin
@Composable
fun LtTopBar(showMenuButton: Boolean, onMenuClick: () -> Unit) {
    val navController = rememberNavController() // ADD THIS LINE to get access to navigation
    
    fun navigate(route: String) {
        navController.navigate(route)
    }
    
    // ... rest of function
}
```

**Wait — this approach won't work because `LtTopBar` doesn't have access to the navController from `LendTrackApp`.**

### Better approach: Pass a navigation callback from `LendTrackApp` to `LtTopBar`

**Change 1:** Update `LtTopBar` signature (line 166):
```kotlin
@Composable
fun LtTopBar(
    showMenuButton: Boolean,
    onMenuClick:    () -> Unit,
    onNavigate:     (String) -> Unit, // ADD THIS
)
```

**Change 2:** Update the `LtTopBar` call in `LendTrackApp` (line 146):
```kotlin
LtTopBar(
    showMenuButton = !isTablet,
    onMenuClick    = { sidebarOpen = true },
    onNavigate     = ::navigate, // ADD THIS
)
```

**Change 3:** Replace the notification bell Box in `LtTopBar` (line 178-185) with the BadgedBox + DropdownMenu code above, using `onNavigate(Screen.Settings.route)` instead of `navigate(...)`.

**Files to modify:**
- `app/src/main/java/com/lendtrack/app/ui/LendTrackApp.kt`

**Verify:**
Run `./gradlew assembleDebug` and open the app. If there are any operations with `REQUIRES_ATTENTION` status, confirm that the notification bell shows a red badge with the count. Tap the bell and confirm a dropdown appears with:
1. A "Requires Attention" item (if count > 0) that navigates to Settings
2. A "View Calendar" item that navigates to the Payment Calendar

Tap outside the dropdown or select an item to dismiss it.

**Additional imports needed:**
At the top of `LendTrackApp.kt`, ensure these are present:
```kotlin
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.widthIn
```

---

## Summary

This plan fixes:
1. **G-6** — Ledger balance fallback uses correct calculation
2. **G-7** — Ledger table shows "Applied" column
3. **G-8** — Calendar borrower names are tappable links
4. **G-9** — Edit borrower dialog prefills existing data
5. **G-2** — Dashboard installment rows open a detail sheet
6. **G-1** — Notification bell shows badge and dropdown

Each gap includes exact file paths, line numbers, and specific code changes. The implementer can apply these changes sequentially and verify each one independently.

**Build and test command:**
```bash
./gradlew assembleDebug
```

**Full verification:**
After all changes, run the app on a tablet emulator or device and manually test each gap fix:
- Ledger screen: tap a row, verify balance calculation
- Ledger screen: verify "Applied" column appears
- Calendar screen: tap a borrower name, verify navigation
- Borrower detail: tap "Edit", verify fields are pre-filled
- Dashboard: tap an installment row, verify detail sheet opens
- Top bar: verify notification bell badge appears and dropdown works
