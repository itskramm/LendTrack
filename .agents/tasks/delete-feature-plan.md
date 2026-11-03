# Implementation Plan: Delete Feature with Firebase Sync

## Overview

Add complete delete functionality for Borrowers, Loans, and Payments with automatic Firebase Realtime Database synchronization. The system currently supports CREATE, UPDATE, ARCHIVE, CANCEL, RECORD, and REVERSE operations. This plan adds DELETE operations following the existing transactional patterns.

**Key Architectural Patterns Observed:**
- Room is local source of truth
- All writes use `db.withTransaction { ... }` for atomicity
- Every mutation creates a SyncOperation + ActivityLog entry
- Repositories call `syncTrigger.onLocalWrite()` after commits
- FirebaseSyncManager.syncOperation() handles all sync via pattern matching
- Delete constraints: cannot delete borrower with active loans, must restore allocations when deleting payments
- Foreign keys use RESTRICT (borrowers, loans in payments) and CASCADE (loans → installments)

**Build/Test Commands Discovered:**
- Build: `./gradlew assembleDebug`
- Test: `./gradlew test` (JUnit + MockK available)
- Project uses Kotlin, Jetpack Compose, Room, Firebase RTDB, Hilt DI

---

## Implementation Plan

- [ ] 1. **Add DELETE operation types to SyncOperationType enum**
      
      Add three new enum values: `DELETE_BORROWER`, `DELETE_LOAN`, `DELETE_PAYMENT` to `SyncOperationType` in `DomainModels.kt` (currently at lines 52-56).
      
      **Files:**
      - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/domain/model/DomainModels.kt`
      
      **Verify:** `./gradlew assembleDebug` succeeds without compilation errors.

- [ ] 2. **Add DAO delete methods for all three entities**
      
      Add `suspend fun delete(id: String)` to BorrowerDao, LoanDao, and PaymentDao. Use `@Query("DELETE FROM table_name WHERE id = :id")` pattern. For PaymentDao, also add `deleteAllocations(paymentId: String)` to clean up payment_allocations table.
      
      **Files:**
      - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/database/dao/BorrowerDao.kt`
      - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/database/dao/LoanDao.kt`
      - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/database/dao/PaymentDao.kt`
      
      **Verify:** `./gradlew assembleDebug` succeeds. DAOs are interfaces, no runtime test needed at this step.

- [ ] 3. **Add BorrowerRepository.deleteBorrower(id, reason) method**
      
      Implementation must: (1) Check for active loans via `loanDao.observeByBorrower(id).first()`, throw `BorrowerValidationException` if any loan has `status == ACTIVE`. (2) Use `db.withTransaction { borrowerDao.delete(id) + syncDao.insertOperation(DELETE_BORROWER) + syncDao.insertActivity(SYSTEM) }`. (3) Call `syncTrigger.onLocalWrite()` after transaction. Follow pattern from `archiveBorrower()` but use hard delete. Reason parameter goes into activity log description.
      
      **Files:**
      - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/data/repository/BorrowerRepository.kt`
      
      **Verify:** `./gradlew test --tests BorrowerRepositoryTest` (if test exists, otherwise verify compilation only).

- [ ] 4. **Add LoanRepository.deleteLoan(id, reason) method**
      
      Implementation must: (1) Load loan, verify exists and not already deleted. (2) Use `db.withTransaction { ... }` to: delete all installments via `installmentDao` (they cascade via foreign key but explicit delete is clearer), then `loanDao.delete(id)`, insert SyncOperation (DELETE_LOAN with reason in payload), insert ActivityLog (type: ADJUSTMENT). (3) Restore any payments that were allocated to this loan's installments — load allocations, update their installments' `amountPaid` back to original by subtracting allocated amounts. (4) Call `syncTrigger.onLocalWrite()`.
      
      **Files:**
      - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/data/repository/LoanRepository.kt`
      
      **Verify:** `./gradlew test --tests LoanRepositoryTest` if exists, else `./gradlew assembleDebug`.

- [ ] 5. **Add PaymentRepository.deletePayment(id, reason) method**
      
      Implementation must: (1) Load payment, verify not reversed and exists. (2) Load allocations via `paymentDao.getAllocations(id)`. (3) Use `db.withTransaction { ... }` to: restore installment `amountPaid` by subtracting each allocation's amount (same logic as `reversePayment()`), delete allocations via `paymentDao.deleteAllocations(id)`, delete payment via `paymentDao.delete(id)`, check if loan was COMPLETED and reactivate to ACTIVE if balance now > 0, insert SyncOperation (DELETE_PAYMENT), insert ActivityLog (type: ADJUSTMENT). (4) Call `syncTrigger.onLocalWrite()`.
      
      **Files:**
      - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/data/repository/PaymentRepository.kt`
      
      **Verify:** `./gradlew test --tests PaymentRepositoryTest` if exists, else `./gradlew assembleDebug`.

- [ ] 6. **Update FirebaseSyncManager to handle DELETE operations**
      
      In `syncOperation()` method (around line 120), add three new branches to the `when` statement matching DELETE_BORROWER, DELETE_LOAN, DELETE_PAYMENT. Each calls a new private suspend function: `deleteBorrowerFromFirebase(id)`, `deleteLoanFromFirebase(id)`, `deletePaymentFromFirebase(id)`. These functions get `ownerUid()`, then call `db.getReference("$ROOT/$uid/{path}/{id}").removeValue().await()`. For loan deletion, also iterate and delete all child installments under `installments/{loanId}/`. Return true on success.
      
      **Files:**
      - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/sync/FirebaseSyncManager.kt`
      
      **Verify:** `./gradlew assembleDebug` succeeds. Integration test would require Firebase emulator, so verify compilation only.

- [ ] 7. **Add deleteBorrower() to BorrowerDetailViewModel**
      
      Add `var showDeleteDialog by mutableStateOf(false)`, `var deleteError by mutableStateOf<String?>(null)`, and `fun deleteBorrower(onSuccess: () -> Unit)`. The function checks `uiState.value.loans.count { it.loan.status == LoanStatus.ACTIVE }`, sets `deleteError` if count > 0, else launches coroutine calling `borrowerRepo.deleteBorrower(borrowerId, "User deleted from detail screen")`, then calls `onSuccess()` on success to navigate away. Catch BorrowerValidationException and set deleteError.
      
      **Files:**
      - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailViewModel.kt`
      
      **Verify:** `./gradlew assembleDebug` — ViewModel compilation test.

- [ ] 8. **Add UI delete button and confirmation dialog to BorrowerDetailScreen**
      
      In the header row (after Edit button), add a Delete button using `LtButton(text = "🗑 Delete", onClick = { viewModel.showDeleteDialog = true }, ghost = true, small = true)`. Disable or hide button if `state.loans.any { it.loan.status == LoanStatus.ACTIVE }`. Add Material3 AlertDialog when `viewModel.showDeleteDialog == true` with title "Delete Borrower?", text explaining action is permanent, two buttons: "Cancel" (dismisses) and "Delete" (calls `viewModel.deleteBorrower { onNavigateBack() }`). Show `viewModel.deleteError` as Text in dialog if not null.
      
      **Files:**
      - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailScreen.kt`
      
      **Verify:** Run app, navigate to BorrowerDetailScreen, verify delete button appears and dialog shows on click. `./gradlew assembleDebug && adb install app/build/outputs/apk/debug/app-debug.apk` then manual UI test.

- [ ] 9. **Add deleteLoan() to LedgerViewModel**
      
      Add `suspend fun deleteLoan(loanId: String, reason: String): Result<Unit>`. Inside, wrap `loanRepo.deleteLoan(loanId, reason)` in try/catch, return `Result.success(Unit)` or `Result.failure(exception)`. LedgerViewModel is simpler than detail VMs — it doesn't manage dialog state, that's in the composable.
      
      **Files:**
      - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/ledger/LedgerViewModel.kt`
      
      **Verify:** `./gradlew assembleDebug` — ViewModel compilation test.

- [ ] 10. **Add delete UI to BalanceLedgerScreen loan rows**
      
      In the `LedgerRow` composable (or its call site in BalanceLedgerScreen around line 60), add a trailing IconButton with `Icons.Default.Delete` icon. On click, show a confirmation AlertDialog with TextField for reason (required), then call `viewModel.deleteLoan(loanId, reason)` in a LaunchedEffect. Display loading state while deleting and error message if deletion fails. After successful delete, dismiss dialog — the loan list will update reactively via Flow.
      
      **Files:**
      - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/ledger/BalanceLedgerScreen.kt`
      
      **Verify:** Run app, go to Balance Ledger, click delete icon, confirm dialog appears, enter reason, verify loan disappears. Manual UI test.

- [ ] 11. **Add deletePayment() to PaymentsViewModel**
      
      Add `suspend fun deletePayment(paymentId: String, reason: String): Result<Unit>`. Implementation wraps `paymentRepo.deletePayment(paymentId, reason)` in try/catch, returning Result. Follow same pattern as LedgerViewModel.
      
      **Files:**
      - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/payments/PaymentsViewModel.kt`
      
      **Verify:** `./gradlew assembleDebug` — ViewModel compilation test.

- [ ] 12. **Add delete UI to PaymentsScreen payment rows**
      
      In the payment row rendering (around line 100 in PaymentsScreen.kt), add a trailing Column with IconButton containing `Icons.Default.Delete`. On click, show confirmation AlertDialog (similar to loan delete) with reason TextField. Call `viewModel.deletePayment(paymentId, reason)` and handle loading/error states. After success, dialog dismisses and payment list updates reactively.
      
      **Files:**
      - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/payments/PaymentsScreen.kt`
      
      **Verify:** Run app, go to Payments, click delete icon on a payment, confirm deletion with reason, verify payment disappears and installment balances update. Manual UI test.

- [ ] 13. **Integration test: Delete workflow end-to-end**
      
      Manual test plan: (1) Create borrower, (2) Create loan for borrower, (3) Record payment, (4) Verify cannot delete borrower (has active loan), (5) Delete payment → verify installment balance restored, (6) Delete loan → verify loan and installments gone, (7) Delete borrower → verify borrower deleted, (8) Check Firebase RTDB (via console or client) → verify all entities removed from cloud. (9) Check activity logs via SettingsScreen or database inspector → verify DELETE activity entries present. (10) Test offline: disconnect, delete entities, reconnect → verify sync pushes deletes to Firebase.
      
      **Files:**
      - Manual testing across app
      
      **Verify:** Execute manual test plan, document results. All steps should succeed without crashes or data inconsistencies.

---

## Notes

- **Constraint enforcement:** Borrower delete checks active loans in repository layer before transaction. Room foreign keys on payments/loans use RESTRICT so they cannot orphan, ensuring referential integrity.
- **Undo allocation:** Both payment deletion and reversal follow same pattern — iterate allocations, subtract from installment.amountPaid, update installments in transaction.
- **Sync patterns:** All three delete operations follow the same sync pattern as existing operations: (1) local delete in transaction, (2) insert SyncOperation, (3) trigger sync, (4) FirebaseSyncManager drains outbox, (5) calls removeValue() on Firebase path.
- **UI patterns:** All delete actions use Material3 AlertDialog for confirmation (following existing app patterns for destructive actions). Reason field is required for audit trail.
- **Testing strategy:** No existing unit tests found for repositories. Focus on compilation verification and manual integration testing. If time permits, add unit tests for repository delete methods using MockK.

