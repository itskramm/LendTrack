# Implementation Plan — Loan Editing Feature

## Overview

Add the ability to edit active loans in LendTrack. Users can modify loan parameters (principal, interest amount, payment frequency, installment count, dates, notes). The system regenerates the installment schedule and re-applies existing payments using FIFO allocation. All changes are atomic and sync to Firebase.

## Architecture Decisions

**Why this approach:**
- **Installment regeneration:** Deleting and regenerating installments is simpler and more correct than trying to patch existing installments. The existing `LoanCalculator.generateInstallments` ensures the schedule follows the same rules as loan creation.
- **Payment preservation:** Re-applying payments via `LoanCalculator.applyPayment` FIFO ensures consistent allocation logic and prevents manual calculation errors.
- **Atomic transaction:** All mutations (loan update, installment delete, installment insert, payment re-allocation) in one Room transaction ensures consistency even if the app crashes mid-operation.
- **Validation before commit:** Checking totalPaid vs new totalPayable before transaction prevents data loss — users cannot shrink a loan below what's already been paid.
- **Sync reuse:** `UPDATE_LOAN` uses the same `pushLoan()` as `CREATE_LOAN` because the sync representation is identical — loan entity + all installments.

**No schema migration needed:** This feature only modifies existing data via updateLoan(). Room database remains at version 3.

---

## Feature Decomposition

This work decomposes into 3 features executed sequentially:

1. **FEAT-001:** Edit Loan UI (dialog, preview, warnings) — UI layer only
2. **FEAT-002:** updateLoan() repository method with transaction logic — core business logic
3. **FEAT-003:** Firebase sync for UPDATE_LOAN — sync layer

Each feature leaves the codebase in a working state. Features are independent in design but must execute sequentially due to shared worktree.

---

## File-by-File Changes

### 1. **app/src/main/java/com/lendtrack/app/domain/model/DomainModels.kt**

**Changes:**
- Add `UPDATE_LOAN` to `SyncOperationType` enum after `CREATE_LOAN`
- Verify `PaymentMethod.CASH` exists (already present as first enum entry)

**Rationale:** UPDATE_LOAN is a new sync operation type. PaymentMethod.CASH already exists and is used in RecordPaymentScreen via `PaymentMethod.values()`, so no changes needed there.

---

### 2. **app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailScreen.kt**

**Changes:**
- Add "✎ Edit" button next to each loan card's "Record Payment" button
- Button visible only when `loan.status == ACTIVE`
- onClick calls `viewModel.openLoanEdit(loan.id)`

**Location:** Inside `LoanDetailCard` composable, in the bottom action row alongside the existing "Record Payment" button.

**UI pattern:** Matches the "✎ Edit" button for borrowers at screen top — ghost button, small size.

---

### 3. **app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailViewModel.kt**

**Changes:**
- Add dialog state fields: `showEditLoanDialog: Boolean`, `editLoanId: String?`, `editLoanError: String?`
- Add methods: `openLoanEdit(loanId: String)`, `dismissLoanEdit()`, `saveLoanEdit(fields: LoanFormFields)`
- Add helper: `currentLoanAsFields(loanId: String): LoanFormFields` to pre-fill the edit form
- Implement `saveLoanEdit()`: parse fields, call `loanRepo.updateLoan()`, handle errors, dismiss on success

**Pattern:** Matches existing `showEditDialog` / `openEdit()` / `saveEdit()` pattern for borrower editing.

---

### 4. **app/src/main/java/com/lendtrack/app/feature/loans/EditLoanDialog.kt** *(new file)*

**Purpose:** Dialog for editing a loan. Reuses `LoanFormFields` from AddLoanViewModel.

**Structure:**
- AlertDialog containing form fields identical to AddLoanScreen: principal, interest amount, payment frequency, custom days (if custom), installment count, loan date, first due date, notes
- "Calculate Preview" button triggers `LoanCalculator.generateInstallments` and shows preview panel below form
- Preview panel shows: totals (principal, interest, total payable, per-installment), full installment schedule table, expected end date
- If `existingPaymentCount > 0`, display warning banner at top: "This loan has {count} payment(s) totaling ₱{amount}. Editing will regenerate the installment schedule and re-apply payments. Review the preview carefully." (LtAmber background, warning icon)
- Confirm button calls `onSave(fields)`, Cancel button calls `onDismiss()`

**Parameters:**
```kotlin
@Composable
fun EditLoanDialog(
    initialFields: LoanFormFields,
    existingPaymentCount: Int,
    totalPaid: Double,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (LoanFormFields) -> Unit,
)
```

**UI components:** LtFormField, LtDropdownField, LtDatePickerField, LtButton, LtNote, RepaymentPreviewPanel (reuse from AddLoanScreen or extract to shared location).

---

### 5. **app/src/main/java/com/lendtrack/app/data/repository/LoanRepository.kt**

**Add method:**
```kotlin
/**
 * Update an existing active loan.
 * 
 * Deletes old installments, regenerates schedule via LoanCalculator.generateInstallments,
 * re-applies existing payments FIFO via LoanCalculator.applyPayment.
 * All mutations run in a single Room transaction.
 * 
 * Validation:
 * - Loan must exist and be ACTIVE
 * - New totalPayable must be >= total already paid (cannot shrink below paid amount)
 * - All payments must fit the new schedule without overpayment
 * 
 * @throws LoanValidationException if validation fails or payments cannot be re-applied
 */
suspend fun updateLoan(
    loanId: String,
    principal: Double,
    interestAmount: Double,
    loanDate: LocalDate,
    firstDueDate: LocalDate,
    numberOfInstallments: Int,
    paymentFrequency: PaymentFrequency,
    customFrequencyDays: Int = 0,
    notes: String = "",
)
```

**Implementation steps:**
1. **Validation:** Check inputs (principal > 0, interestAmount >= 0, numberOfInstallments > 0, firstDueDate >= loanDate, customFrequencyDays > 0 if CUSTOM)
2. **Load existing loan:** `loanDao.getById(loanId)` or throw "Loan not found"
3. **Check status:** `loan.status == ACTIVE` or throw "Only active loans can be edited"
4. **Load payments:** `paymentDao.getByLoan(loanId).filter { !it.isReversed }`
5. **Calculate totalPaid:** Sum of non-reversed payment amounts
6. **Calculate new totalPayable:** `principal + interestAmount`
7. **Validate totalPayable >= totalPaid:** If totalPaid > new totalPayable + 0.01, throw "Cannot reduce loan below already-paid amount..."
8. **Start transaction:** `db.withTransaction { ... }`
9. **Update loan entity:** Set new values, `updatedAt = LocalDateTime.now()`, `syncStatus = PENDING`, call `loanDao.update()`
10. **Delete old installments:** `installmentDao.deleteByLoan(loanId)`
11. **Generate new installments:** `LoanCalculator.generateInstallments(...)`
12. **Insert new installments:** `installmentDao.insertAll(newInstallmentEntities)`
13. **Re-apply payments FIFO:** For each payment (ordered by paymentDate, createdAt):
    - Load current installments: `installmentDao.getByLoan(loanId)`
    - Call `LoanCalculator.applyPayment(payment.id, payment.amount, installments)`
    - Delete old allocations: `paymentDao.deleteAllocationsByPayment(payment.id)`
    - Insert new allocations from result
    - Update installment amountPaid values
    - If result.overpayment > 0.01, throw "Payment overpays by ₱{overpayment}..."
14. **Queue sync operation:** `SyncOperationEntity(operationType = UPDATE_LOAN, entityId = loanId, ...)`
15. **Log activity:** `ActivityLogEntity(type = ADJUSTMENT, description = "Loan edited — ₱{principal} principal, {n} installments")`
16. **End transaction**
17. **Trigger sync:** `syncTrigger.onLocalWrite()`

**Edit-with-existing-payments validation case:** Step 7 prevents shrinking the loan below totalPaid. Step 13 prevents creating a schedule where any payment overpays. Both throw `LoanValidationException` with descriptive messages shown in the UI.

---

### 6. **app/src/main/java/com/lendtrack/app/core/sync/FirebaseSyncManager.kt**

**Changes:**
- Add `UPDATE_LOAN` case to `syncOperation()` when block:
  ```kotlin
  com.lendtrack.app.domain.model.SyncOperationType.UPDATE_LOAN -> pushLoan(op.entityId)
  ```
- Place alongside `CREATE_LOAN` case (they share the same sync logic)

**Rationale:** `pushLoan()` already syncs loan entity + all installments to Firebase RTDB at `/lendtrack/{uid}/loans/{id}` and `/lendtrack/{uid}/installments/{loanId}/{instId}`. This is sufficient for UPDATE_LOAN — no new sync method needed.

---

### 7. **app/src/main/java/com/lendtrack/app/feature/payments/RecordPaymentScreen.kt**

**Changes:** None needed.

**Verification:** Confirm line ~107 uses `PaymentMethod.values()` to populate the dropdown. Since `PaymentMethod.CASH` is the first enum entry in DomainModels.kt, it already appears in the dropdown. Manually test recording a Cash payment to confirm.

---

## EditLoanDialog State Structure

```kotlin
data class EditLoanState(
    val fields: LoanFormFields,              // form inputs (principal, interest, dates, etc)
    val preview: LoanPreview?,               // calculated preview (totals + installments)
    val existingPaymentCount: Int,           // for warning banner
    val totalPaid: Double,                   // for warning banner
    val errorMessage: String?,               // validation/save errors
    val isCalculating: Boolean,              // loading state for preview
)
```

**Fields in LoanFormFields** (reused from AddLoanViewModel):
- `borrowerId: String` (read-only in edit context)
- `principalText: String`
- `interestAmountText: String`
- `paymentFrequency: PaymentFrequency`
- `customFrequencyDays: String`
- `numberOfInstallments: String`
- `loanDateText: String`
- `firstDueDateText: String`
- `notes: String`

**Preview structure** (reused from AddLoanViewModel):
```kotlin
data class LoanPreview(
    val totals: LoanTotals,                  // principal, interest, totalPayable, installmentAmount
    val sampleInstallments: List<Installment>, // all installments for display
    val expectedEndDate: LocalDate,
)
```

---

## SyncOperationType for Loan Updates

**Decision:** Use `UPDATE_LOAN` (new enum value).

**Rationale:** Semantically distinct from `CREATE_LOAN`. Allows future differentiation in logging, analytics, or conflict resolution. Firebase sync logic is identical (both use `pushLoan()`), but the operation type clarifies intent.

**Alternatives considered:**
- Reuse `CREATE_LOAN` → rejected because it obscures the audit trail and makes sync logs ambiguous
- Use `CANCEL_LOAN` → rejected because cancellation is a separate status change, not an edit

---

## FirebaseSyncManager Changes

**Change:** Add one case to the when expression in `syncOperation()`:
```kotlin
com.lendtrack.app.domain.model.SyncOperationType.UPDATE_LOAN -> pushLoan(op.entityId)
```

**No new method needed.** `pushLoan()` already does:
1. Load loan from Room via `loanDao.getById(id)`
2. Push loan data to `/lendtrack/{uid}/loans/{id}`
3. Load all installments via `installmentDao.getByLoan(id)`
4. Push each installment to `/lendtrack/{uid}/installments/{loanId}/{instId}`
5. Mark loan syncStatus = SYNCED

This is exactly what we need for UPDATE_LOAN.

---

## Testing Strategy

### Unit Tests
- **LoanCalculator:** Already tested in existing tests (generateInstallments, applyPayment)
- **LoanRepository.updateLoan():** Test cases:
  - Successful edit without payments
  - Successful edit with payments, payments re-apply correctly
  - Validation failure: principal <= 0
  - Validation failure: totalPaid > new totalPayable
  - Validation failure: payment overpays after regeneration
  - Loan not found
  - Loan not ACTIVE

### Integration Tests (manual)
1. Create a loan without payments → edit principal and installment count → verify installments regenerate, balance updates
2. Create a loan, record 2 payments → edit installment count → verify payments re-apply FIFO, balances remain correct
3. Create a loan, record payment of ₱5000 → attempt to reduce principal to ₱3000 → verify error shown, no changes saved
4. Edit a loan offline → verify UPDATE_LOAN sync operation queued → go online → verify Firebase RTDB updated

### Build Verification
```bash
./gradlew assembleDebug   # must succeed
./gradlew testDebugUnitTest  # existing tests must pass
```

---

## Implementation Checklist

- [ ] 1. Add UPDATE_LOAN to SyncOperationType enum in DomainModels.kt
      Files: app/src/main/java/com/lendtrack/app/domain/model/DomainModels.kt
      Verify: `grep UPDATE_LOAN DomainModels.kt` shows enum entry

- [ ] 2. Add edit button to BorrowerDetailScreen loan cards
      Files: app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailScreen.kt
      Verify: Build succeeds, button visible in UI next to active loans

- [ ] 3. Add edit loan dialog state to BorrowerDetailViewModel
      Files: app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailViewModel.kt
      Verify: openLoanEdit(), dismissLoanEdit(), saveLoanEdit() methods exist

- [ ] 4. Create EditLoanDialog composable
      Files: app/src/main/java/com/lendtrack/app/feature/loans/EditLoanDialog.kt (new)
      Verify: Build succeeds, dialog displays with all fields, preview calculation works

- [ ] 5. Wire EditLoanDialog into BorrowerDetailScreen
      Files: app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailScreen.kt
      Verify: Opening dialog shows pre-filled values, warning appears if payments exist

- [ ] 6. Implement updateLoan() in LoanRepository with full transaction logic
      Files: app/src/main/java/com/lendtrack/app/data/repository/LoanRepository.kt
      Verify: Method signature matches spec, unit tests cover validation and re-application

- [ ] 7. Wire updateLoan() into BorrowerDetailViewModel.saveLoanEdit()
      Files: app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailViewModel.kt
      Verify: Successful edit saves and dismisses dialog, errors shown in UI

- [ ] 8. Add UPDATE_LOAN case to FirebaseSyncManager.syncOperation()
      Files: app/src/main/java/com/lendtrack/app/core/sync/FirebaseSyncManager.kt
      Verify: `grep UPDATE_LOAN FirebaseSyncManager.kt` shows case calling pushLoan()

- [ ] 9. Verify PaymentMethod.CASH in RecordPaymentScreen dropdown
      Files: app/src/main/java/com/lendtrack/app/feature/payments/RecordPaymentScreen.kt
      Verify: Dropdown uses PaymentMethod.values(), CASH appears, recording Cash payment works

- [ ] 10. Manual integration testing
       Verify: Edit loan without payments works, edit loan with payments preserves them, validation errors prevent bad edits, Firebase sync works

---

## Edge Cases and Error Handling

### 1. Total paid exceeds new total payable
**Scenario:** User edits loan from ₱10,000 to ₱5,000, but ₱7,000 already paid.
**Handling:** `updateLoan()` throws `LoanValidationException("Cannot reduce loan below already-paid amount. Total paid: ₱7000.00, new total: ₱5000.00.")` before transaction starts. Error shown in dialog.

### 2. Payment overpays after regeneration
**Scenario:** User reduces installment count, causing one payment to exceed remaining balance on the new schedule.
**Handling:** During payment re-application loop, if `LoanCalculator.applyPayment()` returns `overpayment > 0.01`, throw `LoanValidationException("After edit, payment {paymentId} overpays by ₱{overpayment}...")`. Transaction rolls back, error shown in dialog.

### 3. Loan has no payments
**Scenario:** New loan being edited before any payments recorded.
**Handling:** Skip payment re-application loop (empty list), regenerate installments, save. No warning banner in dialog.

### 4. Loan is COMPLETED or CANCELLED
**Scenario:** User tries to edit a completed loan.
**Handling:** `updateLoan()` throws `LoanValidationException("Only active loans can be edited.")` immediately after loading loan. Edit button not shown in UI for non-ACTIVE loans.

### 5. Concurrent edit (unlikely in single-tablet deployment)
**Scenario:** User opens edit dialog, loan is modified elsewhere, user saves.
**Handling:** Room transaction ensures atomicity. Last write wins (same as current behavior for all edits). Future enhancement: add optimistic locking with version field.

### 6. App crashes during transaction
**Scenario:** Transaction interrupted mid-execution.
**Handling:** Room rolls back entire transaction automatically. Loan remains in pre-edit state. No partial updates.

---

## Future Enhancements (out of scope for this task)

- Optimistic locking: Add `version: Int` to LoanEntity, check in updateLoan()
- Audit trail: Store old loan parameters before edit in a separate `loan_history` table
- Undo capability: Allow reverting loan edit within a time window
- Installment-level editing: Allow editing individual installment due dates without full regeneration
- Partial payment allocation preview: Show user exactly how their payments will be re-allocated before confirming
- Bulk loan editing: Edit multiple loans with same parameters

---

## Completion Criteria

**This feature is complete when:**
1. Edit button appears on active loan cards in BorrowerDetailScreen
2. Tapping Edit opens dialog with current loan values pre-filled
3. Dialog shows preview with regenerated installments and warning if payments exist
4. Confirming edit saves via `updateLoan()` in atomic transaction
5. Installments regenerate via `LoanCalculator.generateInstallments`
6. Payments re-apply via `LoanCalculator.applyPayment` FIFO
7. Validation prevents shrinking loan below paid amount or causing overpayments
8. UPDATE_LOAN sync operation queues and syncs to Firebase RTDB
9. PaymentMethod.CASH verified present in payment recording dropdown
10. Build succeeds: `./gradlew assembleDebug`
11. Existing tests pass: `./gradlew testDebugUnitTest`
12. Manual testing confirms: edit without payments works, edit with payments works, validation errors prevent bad edits, Firebase sync works

---

**Plan authored by:** wf-planner workflow step
**Date:** 2025-01-26
**Codebase version:** Room schema v3, LendTrack Phase 1 (native UI complete, Room persistence active, Firebase sync working)
