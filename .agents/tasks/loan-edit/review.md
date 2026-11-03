# Semantic Code Review: Loan Editing Feature

## Summary

This feature adds the ability to edit active loans in LendTrack. Users can modify loan parameters (principal, interest amount, payment frequency, installment count, dates, notes) through an EditLoanDialog. The system regenerates the installment schedule and re-applies existing payments using FIFO allocation. All changes run in an atomic Room transaction and sync to Firebase via the existing `pushLoan()` infrastructure.

The implementation is complete and follows the existing patterns in the codebase. All acceptance criteria are met: edit UI with preview, atomic transaction with payment preservation, and Firebase sync integration. The build succeeds, tests were updated to match the new API (fixed interest amounts instead of percentages), and integration verification confirms all three features work together correctly.

**Watch for:** None. This is a clean implementation with no blocking concerns.

**Verdict**: APPROVED

---

## High-level view

The edit dialog reuses `LoanFormFields` from AddLoanViewModel and shows a warning banner when loans have existing payments. The preview calculation uses `LoanCalculator.generateInstallments` to show users the regenerated schedule before committing. The `updateLoan()` repository method validates that the new loan total is not less than already-paid amounts, preventing data loss. All mutations (loan update, installment deletion, installment regeneration, payment re-application) run in a single Room transaction, ensuring consistency even if the app crashes mid-operation. The sync layer already had `UPDATE_LOAN` handling in `FirebaseSyncManager.syncOperation()` calling `pushLoan()`, which syncs both the loan entity and all installments to Firebase RTDB. Payment re-application uses `LoanCalculator.applyPayment` FIFO for each payment, detecting overpayment scenarios and rolling back with descriptive errors.

---

<details>
<summary>Issues (0)</summary>

None.

</details>

---

<details>
<summary>Details</summary>

## Edit button placement and dialog triggering

The edit button appears in `LoanDetailCard` next to the "Record Payment" button, visible only when `loan.status == ACTIVE`. The button is styled as a small ghost button with "✎ Edit" text, matching the pattern used for borrower editing. Clicking it calls `viewModel.openLoanEdit(loan.id)`, which sets `editLoanId` and `showEditLoanDialog = true`. The dialog state management follows the existing `showEditDialog` pattern for borrower editing, with separate state for loan ID, error messages, and dismiss/save actions.

## Dialog form structure and payment warning

`EditLoanDialog` takes `initialFields: LoanFormFields`, `existingPaymentCount: Int`, `totalPaid: Double`, `errorMessage: String?`, and callbacks for dismiss/save. The dialog displays all editable fields: principal, interest amount, payment frequency, custom days (conditional), installment count, loan date, first due date, and notes. When `existingPaymentCount > 0`, a warning banner appears at the top with LtAmber background: "This loan has {count} payment(s) totaling {amount}. Editing will regenerate the installment schedule and re-apply payments. Review the preview carefully." The form layout matches AddLoanScreen, with fields arranged in rows for compact display.

## Preview calculation with LoanCalculator

The "Calculate Preview" button validates form inputs (principal, interest, installment count, dates), then calls `LoanCalculator.generateInstallments` with the new parameters. The preview panel shows totals (principal, interest, total payable, per-installment) and the complete installment schedule table with due dates and amounts. The preview uses the same structure as AddLoanScreen's `LoanPreview`, displayed in a collapsible panel below the form. Effective rate is shown as a computed value (interest / principal * 100) for user reference, but is not stored—only the fixed interest amount is persisted.

## Transaction atomicity in updateLoan

`LoanRepository.updateLoan()` wraps all mutations in `db.withTransaction {}`. Inside the transaction: (1) update `LoanEntity` with new values and `syncStatus = PENDING`, (2) delete all existing installments via `installmentDao.deleteByLoan(loanId)`, (3) generate new installments using `LoanCalculator.generateInstallments`, (4) insert new `InstallmentEntity` list, (5) re-apply each non-reversed payment FIFO, (6) queue `UPDATE_LOAN` sync operation, (7) log activity. If any step throws an exception, Room rolls back the entire transaction automatically. The sync trigger `syncTrigger.onLocalWrite()` is called after the transaction commits, ensuring the operation is queued only if all mutations succeeded.

## Payment preservation validation preventing data loss

Before starting the transaction, `updateLoan()` loads all payments for the loan via `paymentDao.getByLoan(loanId)`, filters out reversed payments, and calculates `totalPaid`. It compares this against `newTotalPayable = principal + interestAmount`. If `totalPaid > newTotalPayable + 0.01`, it throws `LoanValidationException` with a descriptive message: "Cannot reduce loan below already-paid amount. Total paid: ₱{totalPaid}, new total: ₱{newTotal}." This check runs before the transaction starts, preventing any mutations if the edit would lose payment data. The 0.01 tolerance handles floating-point rounding.

## Payment re-application with FIFO allocation

After regenerating installments, `updateLoan()` iterates through non-reversed payments sorted by `paymentDate`, then `createdAt` (chronological FIFO order). For each payment: load current installment states from the database, call `LoanCalculator.applyPayment(payment.id, payment.amount, installments)` to get `AllocationResult`, delete old allocations for this payment via `paymentDao.deleteAllocations(payment.id)`, insert new `PaymentAllocationEntity` records from `result.allocations`, and update installment `amountPaid` values via `installmentDao.updateAll()`. If any payment's `result.overpayment > 0.01`, the method throws `LoanValidationException` with details: "After edit, payment {paymentId} overpays by ₱{overpayment}. Reduce principal or increase installments." This causes the transaction to roll back, leaving the loan unchanged. The FIFO order ensures payments apply to the earliest unpaid installments first, matching the original allocation logic.

## Firebase sync routing for UPDATE_LOAN

`FirebaseSyncManager.syncOperation()` already includes `UPDATE_LOAN` in its when expression (line 173), alongside `CREATE_LOAN` and `CANCEL_LOAN`, all calling `pushLoan(op.entityId)`. The `pushLoan()` method loads the loan via `loanDao.getById(id)`, pushes it to `/lendtrack/{uid}/loans/{id}` in Firebase RTDB, then loads and pushes all installments to `/lendtrack/{uid}/installments/{loanId}/{instId}`. This is exactly what's needed for loan edits—the sync representation is identical to creation. No new sync method was required. When the device goes online, `FirebaseSyncManager` picks up pending `UPDATE_LOAN` operations from the sync queue and pushes the updated loan and regenerated installments to Firebase, overwriting the old data.

## ViewModel field parsing and error handling

`BorrowerDetailViewModel.saveLoanEdit()` extracts values from `LoanFormFields` and parses them to the correct types: `principalText.toDoubleOrNull()`, `interestAmountText.toDoubleOrNull()`, `numberOfInstallments.toIntOrNull()`, `LocalDate.parse(loanDateText)`, etc. Each parse failure throws `LoanValidationException` with a descriptive message ("Principal must be a valid number", "First due date is not valid (YYYY-MM-DD)"). Custom frequency days are parsed only when `paymentFrequency == PaymentFrequency.CUSTOM`, defaulting to 0 otherwise. The method wraps the repository call in a try-catch block: `LoanValidationException` sets `editLoanError` (displayed in the dialog), success calls `dismissLoanEdit()` to close the dialog and clear state. This matches the pattern used in `saveEdit()` for borrower updates.

## PaymentMethod.CASH presence confirmed

`PaymentMethod` enum in `DomainModels.kt` (line 43) includes `CASH("Cash")` as the first entry. `RecordPaymentScreen` (line 107) uses `PaymentMethod.values()` to populate its dropdown, automatically including CASH. No code changes were needed—CASH was already present and functional. The user's requirement to "add option for cash" is satisfied by the existing implementation.

## No schema migration required

This feature does not change the Room database schema. It only modifies existing data via `updateLoan()`: updating `LoanEntity` fields, deleting and recreating `InstallmentEntity` records, and deleting and recreating `PaymentAllocationEntity` records. All tables and column types remain unchanged. The database version stays at 3. No migration logic is required.

## Test updates for new API

The unit tests (`LoanCalculatorTest.kt`, `LoanValidationTest.kt`, `PaymentAllocationTest.kt`) were updated to use the new `interestAmount` parameter instead of the old percentage-based API. All 31 tests pass. The integration verification report confirms build success and test success. Pre-existing test failures (references to removed `InterestMethod`, `calculateTotals`, `annualRate` APIs) were addressed by updating the tests to match the current system design (fixed interest amounts).

</details>

---

<details>
<summary>File Map</summary>

### Modified Files

- **app/src/main/java/com/lendtrack/app/domain/model/DomainModels.kt** — Added `UPDATE_LOAN` to `SyncOperationType` enum (already present)
- **app/src/main/java/com/lendtrack/app/feature/loans/EditLoanDialog.kt** — New file: dialog for editing loans with form fields, preview calculation, payment warning banner
- **app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailScreen.kt** — Added edit button to `LoanDetailCard`, wired `EditLoanDialog` into screen with dialog state
- **app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailViewModel.kt** — Added edit loan dialog state (`showEditLoanDialog`, `editLoanId`, `editLoanError`), `openLoanEdit()`, `dismissLoanEdit()`, `saveLoanEdit()`, `currentLoanAsFields()` methods
- **app/src/main/java/com/lendtrack/app/data/repository/LoanRepository.kt** — Implemented `updateLoan()` method with atomic transaction, validation, installment regeneration, payment re-application
- **app/src/main/java/com/lendtrack/app/core/database/dao/PaymentDao.kt** — Added `suspend fun getByLoan(loanId: String): List<PaymentEntity>` for payment retrieval in transactions
- **app/src/main/java/com/lendtrack/app/core/sync/FirebaseSyncManager.kt** — `UPDATE_LOAN` case already present in `syncOperation()` calling `pushLoan()` (no changes needed)
- **app/src/test/java/com/lendtrack/app/LoanCalculatorTest.kt** — Updated all tests to use `interestAmount` parameter
- **app/src/test/java/com/lendtrack/app/LoanValidationTest.kt** — Updated tests to match new API
- **app/src/test/java/com/lendtrack/app/PaymentAllocationTest.kt** — Updated tests to use `interestAmount` parameter

### Verification Evidence

- **integration-verification.md** — Build succeeds (44 tasks), 31 tests pass, all integration points verified
- **FEAT-001.json** — UI implementation complete, edit button and dialog working
- **FEAT-002.json** — `updateLoan()` repository method complete with full transaction logic
- **FEAT-003.json** — Firebase sync handling confirmed, `PaymentMethod.CASH` present

</details>
