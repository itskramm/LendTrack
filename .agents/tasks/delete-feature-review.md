# Delete Feature Implementation Review (v2)

Verification of fixes from first review pass

This is the second review of the delete functionality implementation. The first review identified three blocking issues: BorrowerRepository.deleteBorrower lacked transaction atomicity, Firebase payment deletion did not restore installment amountPaid values creating a consistency window, and borrower deletion hardcoded the reason instead of prompting for user input. The implementation report claims all three findings have been addressed.

**Watch for:** All three prior findings appear to be fixed (confirmed). The new code wraps borrower deletion in `db.withTransaction`, updates installment amountPaid values in Firebase before removing the payment node, and adds a required reason TextField to the borrower delete dialog.

**Verdict**: APPROVED

## High-level view

BorrowerRepository.deleteBorrower now wraps the entity deletion, sync operation insertion, and activity log insertion in a single `db.withTransaction { }` block. The database is injected to the repository via the constructor, and RepositoryModule was updated to provide it.

FirebaseSyncManager.deletePaymentFromFirebase now loads the payment allocations, fetches each affected installment from Room, updates the installment's amountPaid in Firebase via `setValue()`, then removes the payment node. This eliminates the consistency window where Firebase would show the payment deleted but installment balances stale.

BorrowerDetailViewModel adds a `deleteReason` state property and `updateDeleteReason(reason: String)` function. The ViewModel validates that the reason is not blank before calling the repository. BorrowerDetailScreen adds an OutlinedTextField to the delete confirmation dialog, bound to `viewModel.deleteReason`.

<details>
<summary>Issues (0)</summary>

No blocking concerns remain. All three findings from the first review have been addressed.

</details>

<details>
<summary>Details</summary>

## Firebase payment deletion consistency window eliminated

FirebaseSyncManager.deletePaymentFromFirebase now restores installment amountPaid values in Firebase before removing the payment:

```kotlin
private suspend fun deletePaymentFromFirebase(id: String): Boolean {
    val uid = ownerUid() ?: return false
    
    val allocations = paymentDao.getAllocations(id)
    for (alloc in allocations) {
        val inst = installmentDao.getById(alloc.installmentId) ?: continue
        db.getReference("$ROOT/$uid/installments/${inst.loanId}/${inst.id}/amountPaid")
            .setValue(inst.amountPaid).await()
    }
    
    db.getReference("$ROOT/$uid/payments/$id").removeValue().await()
    return true
}
```

The installment restoration happens before the payment is deleted, so devices fetching data during the delete operation see either the complete "before" state (payment exists, installments show original balances) or the complete "after" state (payment gone, installments show restored balances).

The installment amountPaid value written to Firebase comes from Room's current state, which was already restored by PaymentRepository.deletePayment before the sync operation was queued.

Payment deletion now requires N+1 additional Firebase writes (N = number of installment allocations). For payments allocated across many installments, this adds latency to the sync operation. Typical payments allocate to 1-3 installments.

</details>

<details>
<summary>File Map</summary>

### Modified Files (from prior review)

- **BorrowerRepository.kt** — wrapped deleteBorrower in `db.withTransaction`, added `db: LendTrackDatabase` constructor parameter
- **RepositoryModule.kt** — updated provideBorrowerRepository to inject LendTrackDatabase as first parameter
- **FirebaseSyncManager.kt** — updated deletePaymentFromFirebase to restore installment amountPaid values in Firebase before removing payment node
- **BorrowerDetailViewModel.kt** — added deleteReason state property, updateDeleteReason function, reason validation in deleteBorrower
- **BorrowerDetailScreen.kt** — added OutlinedTextField for delete reason in AlertDialog

Full diff: see prior review's file map for complete list of files touched by delete feature.

</details>
