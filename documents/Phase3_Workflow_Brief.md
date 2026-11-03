# Phase 3: Monthly Interest & Late Penalty System Workflow Brief

## Task: Implement Monthly Interest Charges & Late Penalty Tracking

### Goal
Add monthly recurring interest charges and late penalty system with automatic application, balance reflection, and payment allocation.

### Context
- **Prerequisites**: Phases 1 & 2 complete
- **Estimated Time**: 8-10 hours
- **Complexity**: High
- **Database Changes**: Yes (migrations required)

### Feature Breakdown

#### 1. Database Schema Updates (1 hour)

**New Tables to Create:**
1. `monthly_interest_charges` - Track recurring monthly interest
2. `late_penalties` - Track late payment fees

**Loan Table Updates:**
- Add `monthly_interest_amount` column (REAL, default 0.0)
- Add `late_penalty_amount` column (REAL, default 0.0)
- Add `late_penalty_grace_days` column (INTEGER, default 0)

**Migration Required:**
- Create MIGRATION_3_4
- Preserve all existing data
- Default new columns to 0

**Files to Create:**
- `MonthlyInterestChargeEntity.kt`
- `LatePenaltyEntity.kt`
- `MonthlyInterestChargeDao.kt`
- `LatePenaltyDao.kt`

**Files to Modify:**
- `LoanEntity.kt`
- `Migrations.kt`
- `LendTrackDatabase.kt`

#### 2. Domain Models (30 min)

**Add to DomainModels.kt:**
```kotlin
data class MonthlyInterestCharge(
    val id: String,
    val loanId: String,
    val borrowerId: String,
    val amount: Double,
    val chargeDate: LocalDate,
    val isPaid: Boolean = false,
    val createdAt: LocalDateTime,
    val syncStatus: SyncStatus
)

data class LatePenalty(
    val id: String,
    val loanId: String,
    val borrowerId: String,
    val installmentId: String,
    val amount: Double,
    val penaltyDate: LocalDate,
    val reason: String,
    val isPaid: Boolean = false,
    val createdAt: LocalDateTime,
    val syncStatus: SyncStatus
)

data class LoanBalance(
    val installmentBalance: Double,
    val monthlyCharges: Double,
    val penalties: Double,
    val totalBalance: Double
)
```

**Update Loan model:**
```kotlin
val monthlyInterestAmount: Double = 0.0
val latePenaltyAmount: Double = 0.0
val latePenaltyGraceDays: Int = 0
```

#### 3. Background Workers (2-3 hours)

**Create Workers:**
1. **MonthlyInterestWorker** - Runs daily, charges interest on 1st of month
2. **LatePenaltyWorker** - Runs daily, applies penalties to overdue installments

**Files to Create:**
- `app/src/main/java/com/lendtrack/app/workers/MonthlyInterestWorker.kt`
- `app/src/main/java/com/lendtrack/app/workers/LatePenaltyWorker.kt`

**Files to Modify:**
- `LendTrackApplication.kt` - Schedule workers

**Worker Logic:**
```kotlin
// MonthlyInterestWorker
- Check if today is 1st of month
- Get all active loans with monthlyInterestAmount > 0
- Check if already charged this month
- Create MonthlyInterestCharge records
- Queue sync operations

// LatePenaltyWorker
- Get all overdue installments
- Get loan's penalty settings
- Check grace period
- Check if penalty already applied
- Create LatePenalty records
- Queue sync operations
```

#### 4. Repository Updates (1-2 hours)

**Add to LoanRepository:**
```kotlin
suspend fun getTotalBalance(loanId: String): LoanBalance
suspend fun getMonthlyCharges(loanId: String): List<MonthlyInterestCharge>
suspend fun getLatePenalties(loanId: String): List<LatePenalty>
```

**Add to PaymentRepository:**
```kotlin
suspend fun allocateToChargesAndPenalties(
    paymentId: String,
    amount: Double,
    loanId: String
): AllocationResult
```

**Files to Modify:**
- `LoanRepository.kt`
- `PaymentRepository.kt`

#### 5. UI Updates (2-3 hours)

**Add/Edit Loan Screen:**
- Add "Monthly Interest (₱)" input field
- Add "Late Penalty (₱)" input field
- Add "Penalty Grace Period (days)" input field
- Update preview calculation
- Update validation

**Files to Modify:**
- `AddLoanScreen.kt`
- `AddLoanViewModel.kt`
- `EditLoanDialog.kt`
- `LoanFormFields.kt`

**Borrower Detail Screen:**
- Show balance breakdown:
  - Installments: ₱X
  - Monthly Interest: ₱Y (in orange)
  - Late Penalties: ₱Z (in red)
  - **Total Balance: ₱(X+Y+Z)** (bold)

**Files to Modify:**
- `BorrowerDetailScreen.kt`
- `BorrowerDetailViewModel.kt`

**Dashboard:**
- Update "Total Outstanding" to include all charges

**Files to Modify:**
- `DashboardViewModel.kt`

#### 6. Firebase Sync (1 hour)

**Update Sync Manager:**
- Add sync operations for monthly charges
- Add sync operations for penalties
- Push to Firebase paths:
  - `/lendtrack/{uid}/monthly_charges/{id}`
  - `/lendtrack/{uid}/penalties/{id}`

**Files to Modify:**
- `FirebaseSyncManager.kt`
- `SyncOperationType.kt` (add new enum values)

#### 7. Payment Allocation (1 hour)

**Update Payment Logic:**
- When recording payment, allocate in order:
  1. First to late penalties (oldest first)
  2. Then to monthly charges (oldest first)
  3. Finally to installments (FIFO)

**Files to Modify:**
- `LoanCalculator.kt`
- `PaymentRepository.kt`

### Testing Requirements

**Unit Tests:**
- Monthly interest calculation
- Late penalty application logic
- Balance calculation with all components
- Payment allocation priority

**Integration Tests:**
- Worker execution
- Database migrations
- Payment flow end-to-end

**Manual Tests:**
- Create loan with monthly interest
- Wait for monthly charge (or simulate)
- Create overdue installment
- Verify penalty applied after grace period
- Record payment, verify allocation
- Check balance updates correctly

### Questions to Answer
1. **Monthly Interest**: Charge on 1st of every month ✓
2. **Late Penalties**: One penalty per overdue installment (not daily compounding) ✓
3. **Grace Period**: Configurable per loan ✓
4. **Payment Priority**: Penalties → Monthly Charges → Installments ✓

### Success Criteria
- [ ] Database migration successful
- [ ] Monthly interest fields in loan form
- [ ] Late penalty fields in loan form
- [ ] Workers run automatically
- [ ] Monthly charges apply on 1st of month
- [ ] Late penalties apply after grace period
- [ ] Balance shows all components
- [ ] Payments allocate correctly
- [ ] Dashboard reflects total correctly
- [ ] Firebase sync includes new data
- [ ] Build successful
- [ ] All tests pass

### Risk Mitigation
- **Worker Reliability**: Use WorkManager (guaranteed execution)
- **Data Consistency**: All operations in transactions
- **Migration Safety**: Test with existing loan data
- **Performance**: Index on charge_date and penalty_date
