# Interest Simplification - Remaining Changes

## Status

✅ **Completed:**
1. Domain model updated (Loan - removed interestRate, interestMethod)
2. InterestMethod enum removed
3. Database entities updated (LoanEntity simplified)
4. Type converters updated
5. Migration created (MIGRATION_2_3)
6. Database version updated to 3
7. Migration added to DatabaseModule
8. LoanRepository.createLoan() signature updated
9. LoanRepository mapper updated
10. LoanCalculator simplified (now uses fixed interest amount only)
11. FirebaseSyncManager.pushLoan() updated

## ⚠️ Remaining Files to Fix (35 errors)

### 1. AddLoanViewModel.kt
**File:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/loans/AddLoanViewModel.kt`

**Changes needed:**
- Line 38: Change `val interestMethod: InterestMethod` to REMOVE this field
- Line 39: Change `val annualRateText: String` to `val interestAmountText: String`
- Line 90-100: Remove `LoanCalculator.calculateTotals()` call - no longer exists
- Update `calculatePreview()` method to use fixed interest amount
- Update `createLoan()` method signature to pass `interestAmount` instead of `annualRate` and `interestMethod`

**New structure:**
```kotlin
data class LoanFormFields(
    val borrowerId:           String = "",
    val principalText:        String = "",
    val interestAmountText:   String = "",  // Changed from annualRateText
    // REMOVED: val interestMethod
    val paymentFrequency:     PaymentFrequency  = PaymentFrequency.BIWEEKLY,
    val customFrequencyDays:  String = "",
    val numberOfInstallments: String = "",
    val loanDateText:         String = LocalDate.now().toString(),
    val firstDueDateText:     String = LocalDate.now().plusDays(14).toString(),
    val notes:                String = "",
)

fun calculatePreview() {
    // ...
    val principal = f.principalText.toDoubleOrNull() ?: throw...
    val interestAmount = f.interestAmountText.toDoubleOrNull() ?: throw...
    val n = f.numberOfInstallments.toIntOrNull() ?: throw...
    
    val totalPayable = principal + interestAmount
    val installmentAmount = totalPayable / n
    
    val installments = LoanCalculator.generateInstallments(
        loanId = "preview",
        principal = principal,
        interestAmount = interestAmount,  // Direct amount
        numberOfInstallments = n,
        paymentFrequency = f.paymentFrequency,
        customFrequencyDays = customDays,
        firstDueDate = firstDue,
    )
    
    // Create preview with totalPayable, installmentAmount, sample installments
}

fun createLoan(onSuccess: (String) -> Unit) {
    // ...
    loanRepo.createLoan(
        borrowerId = f.borrowerId,
        principal = principal,
        interestAmount = interestAmount,  // Not annualRate
        loanDate = loanDate,
        firstDueDate = firstDue,
        numberOfInstallments = n,
        paymentFrequency = f.paymentFrequency,
        customFrequencyDays = customDays,
        notes = f.notes,
    )
}
```

### 2. AddLoanScreen.kt
**File:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/loans/AddLoanScreen.kt`

**Changes needed (11 errors):**
- Line 95: Remove `InterestMethod` dropdown selector
- Line 96: Remove `rememberIntere stMethodOptions()`
- Line 110: Remove `.label` reference (no longer exists)
- Line 112-114: Remove `when(interestMethod)` cases
- Line 268-271: Remove `when(interestMethod)` preview text
- Replace interest rate input with interest amount input

**New structure:**
```kotlin
// Replace interest rate field with direct amount field:
OutlinedTextField(
    value = fields.interestAmountText,
    onValueChange = { viewModel.updateField { copy(interestAmountText = it) } },
    label = { Text("Interest Amount (₱)") },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    supportingText = {
        if (fields.principalText.isNotBlank() && fields.interestAmountText.isNotBlank()) {
            val principal = fields.principalText.toDoubleOrNull() ?: 0.0
            val interest = fields.interestAmountText.toDoubleOrNull() ?: 0.0
            if (principal > 0 && interest > 0) {
                val effectiveRate = (interest / principal) * 100
                Text("Effective rate: %.1f%%".format(effectiveRate))
            }
        }
    }
)

// REMOVE the interest method dropdown entirely

// Update preview section:
when (val state = uiState) {
    is AddLoanUiState.Preview -> {
        Text("Total Payable: ${(state.preview.principal + state.preview.interestAmount).peso()}")
        Text("Installment Amount: ${state.preview.installmentAmount.peso()}")
        // Show installment schedule
    }
}
```

### 3. DashboardViewModel.kt
**File:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/dashboard/DashboardViewModel.kt`

**Changes needed (2 errors):**
- Line 160: Remove `interestRate = 0.10` parameter
- Line 161: Remove `interestMethod = InterestMethod.FLAT_RATE` parameter

**Fix:**
```kotlin
// Change from:
loanRepo.createLoan(
    borrowerId = borrowerId,
    principal = amount,
    annualRate = 0.10,
    interestMethod = InterestMethod.FLAT_RATE,
    // ...
)

// To:
loanRepo.createLoan(
    borrowerId = borrowerId,
    principal = amount,
    interestAmount = amount * 0.10,  // 10% as fixed amount
    // ...
)
```

### 4. BorrowerDetailScreen.kt
**File:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailScreen.kt`

**Changes needed (1 error):**
- Line 306: Remove `.interestMethod.label` display

**Fix:**
```kotlin
// Change from:
Text("Interest: ${loan.interestRate * 100}% (${loan.interestMethod.label})")

// To:
Text("Interest: ${loan.interestAmount.peso()}")
// Optional: show effective rate
// Text("Interest: ${loan.interestAmount.peso()} (${loan.effectiveRate?.format(1)}%)")
```

## Summary

**Total changes needed:**
- 2 ViewModels (AddLoanViewModel, DashboardViewModel)
- 2 UI Screens (AddLoanScreen, BorrowerDetailScreen)

**Key changes:**
1. Replace `annualRate` + `interestMethod` parameters with `interestAmount` parameter
2. Remove `InterestMethod` enum references
3. Remove `LoanCalculator.calculateTotals()` calls (method no longer exists)
4. Simplify UI to direct interest amount entry
5. Update display logic to show interest as amount, not percentage

## Testing After Completion

1. Build: `./gradlew assembleDebug` - must succeed with no errors
2. Test migration: Launch app with existing loans, verify data preserved
3. Create new loan: Principal ₱1,500, Interest ₱150 → Total ₱1,650
4. Verify installments generate correctly
5. Record payment and verify FIFO allocation works
6. Check Firebase sync - verify loan syncs without interestRate/interestMethod fields
