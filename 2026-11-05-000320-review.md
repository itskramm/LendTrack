# Top bar greeting relocation and borrower detail crash fix

This change moves the time-based greeting ("Good morning/afternoon/evening, Zelliyah!") from the right side to the left side of the top bar, removes the duplicate "Zelliyah" branding text and "Lending Management" subtitle, and fixes a crash that occurred when navigating to the borrower detail screen. The crash was caused by infinite flow collection in the document download mechanism and potential circular flow dependencies in the ViewModel's state composition. The fix introduces a shared borrower flow, adds `.take(1)` to terminate the download observer, wraps document downloads in try-catch blocks, and adds defensive null handling in installment mapping.

**Watch for:** The shared borrower flow introduces replay behavior that could deliver stale data if the borrower is updated elsewhere during ViewModel lifetime (possible), and the document download now uses `.take(1)` which only observes the first borrower snapshot, potentially missing documents added after ViewModel initialization (likely).

**Verdict**: NEEDS_CHANGES

## High-level view

The top bar changes are purely cosmetic: greeting text moved left, branding and duplicate greeting removed, date moved right. Layout now matches the user's reference design.

The crash fix centers on two problems. First, `downloadMissingDocuments()` was collecting on `uiState`, which itself depends on `observeBorrower()`, creating a circular flow dependency that could hang or crash. The fix extracts a separate `borrowerFlow` shared across both the `uiState` combiner and the download function. Second, the download observer ran indefinitely (`collect` with no terminal operator), so it kept observing borrower changes forever. Adding `.take(1)` limits it to the first snapshot.

The installment mapping change (`.mapNotNull` to `.map`) removes a filter that could drop loans from the UI if installment data was missing, but the comment claims this prevents crashes. No crash mechanism is evident in the diff; the change looks like a safety measure against unexpected null behavior, but loans without installments will now appear in the list where they might have been hidden before.

<details><summary>Details</summary>

### Top bar greeting layout matches user spec except icon

The user specifically requested "a ribbon besides the greeting" and "not a heart", but the icon is still `Icons.Filled.Favorite` (a heart). **confirmed** mismatch with requirements.

### Shared borrower flow breaks document download circular dependency

The original `downloadMissingDocuments()` collected on `uiState`, which internally combines `borrowerRepo.observeBorrower(borrowerId)`, `loanRepo.observeLoansByBorrower(borrowerId)`, and `paymentRepo.observePaymentsByBorrower(borrowerId)`. If the download logic triggers a borrower update (which it does: it calls `borrowerRepo.updateBorrower()` after a successful download), that update emits a new value from `observeBorrower()`, which propagates through `uiState`, which the download function is still collecting on, potentially creating a feedback loop.

The fix introduces a separate `borrowerFlow` that's shared across the `uiState` combiner and `downloadMissingDocuments()`. This `borrowerFlow` uses `.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)`, which multicasts a single upstream subscription to all downstream collectors. The download function no longer depends on the full `uiState` (which includes loans, payments, and installments), so updates to the borrower during download don't trigger recomputation of the entire UI state inside the download observer.

**Stale data risk**: the `shareIn` uses `replay = 1`, so late collectors (like the download function) will receive the last-emitted borrower snapshot even if it's stale. **Possible concern**: if the borrower is updated by another device or process between ViewModel initialization and the download function running, the download function might receive an outdated snapshot. This seems unlikely in practice because `downloadMissingDocuments()` is called in `init {}`, so it should connect to the flow almost immediately, but the replay mechanism creates a timing window.

### Document download now terminates after first borrower snapshot

The old `downloadMissingDocuments()` collected on `uiState` indefinitely. Every time the borrower, loans, payments, or installments changed, the download logic would re-run. If a download succeeds and updates the borrower, that update would trigger another emission, which would re-enter the download logic, potentially starting another download (though the guard `if (borrower.idDocumentFirebasePath != null && borrower.idDocumentUri == null)` should prevent duplicate downloads).

The fix changes `uiState.collect` to `borrowerFlow.take(1).collect`. The download logic runs once, checks for missing documents, downloads them if needed, and stops.

**Trade-off**: the download function now only runs on ViewModel initialization. If a document is added to Firebase after the ViewModel is created (e.g., by another device), the download won't trigger until the ViewModel is recreated (user navigates away and back). Multi-device sync is strictly one-time per screen visit.

### Document downloads now wrapped in try-catch with logging

The old download logic had no exception handling around `documentCaptureHelper.getDocumentFile()` or `documentStorageRepo.downloadDocument().collect()`. If either threw an exception (e.g., filesystem error, network failure, Firebase auth failure), the exception would propagate up and crash the coroutine.

The fix wraps each document download block in a `try { ... } catch (e: Exception) { Log.w(...) }`. Failures are logged but swallowed, so the ViewModel initialization continues normally even if document sync fails.

### Installment mapping removes null filter

The `uiState` combiner changed `.mapNotNull { loan -> ... }` to `.map`. The lambda never returned `null` (it always constructs a `LoanWithBalance`), so `mapNotNull` was functionally equivalent to `.map`. The change removes a misleading operator.

**Unclear motivation**: the diff shows no crash mechanism related to `.mapNotNull`. If the original crash involved loans disappearing from the UI, this change ensures all loans appear even if their installment data is missing (empty list). But that's a UI behavior change, not a crash fix.

</details>

<details>
<summary>Issues (3)</summary>

1. **Heart icon instead of ribbon icon** — The user explicitly requested "not a heart" for the ribbon badge icon, but the code still uses `Icons.Filled.Favorite` (a heart). Replace with a ribbon or award icon from Material Icons (confirmed).

2. **Shared borrower flow replay could deliver stale data** — The `borrowerFlow` uses `replay = 1`, so if the borrower is updated elsewhere before `downloadMissingDocuments()` subscribes, the download function might receive an outdated snapshot. This is unlikely in practice because the download runs in `init {}`, but the replay mechanism creates a timing window. Consider using `replay = 0` if late subscribers should wait for a fresh emission (possible).

3. **Document download only runs once per ViewModel lifecycle** — `.take(1)` limits the download observer to the first borrower snapshot, so documents added to Firebase after ViewModel initialization won't sync until the user navigates away and back. This matches the stated "sync on initialization" intent, but it means multi-device sync is strictly one-time per screen visit. Document the limitation in a comment above `downloadMissingDocuments()` (likely).

</details>

<details>
<summary>File map</summary>

- `app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailViewModel.kt` — Extracted shared `borrowerFlow`, added `.take(1)` to document download observer, wrapped downloads in try-catch, changed `.mapNotNull` to `.map`, added logging.
- `app/src/main/java/com/lendtrack/app/ui/LendTrackApp.kt` — Moved greeting to left side with ribbon badge, removed "Zelliyah" branding and subtitle, removed duplicate greeting and ribbon from right side, kept only date on right.

Full diff: `git diff origin/master`

</details>
