# LendTrack Enhancement Implementation Plan

**Date:** 2026-11-03  
**Project:** LendTrack Android App  
**Scope:** Dark mode, UI improvements, Firebase sync fixes, late penalty system, document uploads, bank additions

---

## Executive Summary

This plan covers **8 major feature sets** to be implemented in LendTrack:

1. **Dark Mode Toggle** - User-selectable theme with system setting
2. **Immersive Display** - Hide status/navigation bars (full-screen mode)
3. **Branding Update** - Add "Zelliyah" branding to top bar
4. **Firebase Sync Fix** - Debug and repair sync functionality
5. **Late Penalty System** - Monthly interest + late penalties tracking
6. **Document Upload** - ID and billing address image uploads
7. **Bank Additions** - Union Bank and Metro Bank payment methods
8. **Balance Reflection** - Ensure penalties/monthly fees reflect in totals

**Estimated Complexity:** High  
**Estimated Implementation Time:** 12-16 hours across multiple features  
**Testing Required:** Comprehensive (theme switching, sync, calculations, image handling)

---

## Feature 1: Dark Mode Implementation

### Current State
- Only light color scheme exists (`LendTrackColorScheme`)
- No theme preference storage
- No toggle UI

### Requirements
- User can toggle between Light/Dark/System Default
- Preference persists across sessions
- All screens support both themes
- Smooth transition without flicker

### Implementation Plan

#### 1.1 Create Dark Color Scheme
**File:** `app/src/main/java/com/lendtrack/app/core/ui/theme/Theme.kt`

```kotlin
// Add dark colors
private val DarkColorScheme = darkColorScheme(
    primary = LtRose.copy(alpha = 0.8f),
    onPrimary = Color.White,
    secondary = LtDustyRose,
    onSecondary = Color.White,
    background = Color(0xFF1A1A1A),      // Dark gray
    onBackground = Color(0xFFE5E5E5),    // Light gray text
    surface = Color(0xFF2A2A2A),         // Slightly lighter
    onSurface = Color(0xFFE5E5E5),
    error = LtRed,
    onError = Color.White,
)

// Update LendTrackTheme composable
@Composable
fun LendTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LendTrackColorScheme
    }
    // ... rest of theme setup
}
```

#### 1.2 Theme Preference Storage
**File:** `app/src/main/java/com/lendtrack/app/core/preferences/ThemePreferences.kt` (NEW)

```kotlin
@Singleton
class ThemePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
    }
    
    enum class ThemeMode {
        LIGHT, DARK, SYSTEM
    }
    
    val themeMode: Flow<ThemeMode> = dataStore.data
        .map { prefs ->
            when (prefs[THEME_MODE]) {
                "DARK" -> ThemeMode.DARK
                "LIGHT" -> ThemeMode.LIGHT
                else -> ThemeMode.SYSTEM
            }
        }
    
    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode.name
        }
    }
}
```

#### 1.3 Settings Screen Toggle
**File:** `app/src/main/java/com/lendtrack/app/feature/settings/SettingsScreen.kt`

Add theme selector section:
```kotlin
// In SettingsScreen composable
LtPanel(title = "Appearance") {
    Column {
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeModeButton("Light", selected = themeMode == ThemeMode.LIGHT) {
                viewModel.setThemeMode(ThemeMode.LIGHT)
            }
            ThemeModeButton("Dark", selected = themeMode == ThemeMode.DARK) {
                viewModel.setThemeMode(ThemeMode.DARK)
            }
            ThemeModeButton("System", selected = themeMode == ThemeMode.SYSTEM) {
                viewModel.setThemeMode(ThemeMode.SYSTEM)
            }
        }
    }
}
```

#### 1.4 Update Component Colors
**Files to update:**
- `LtComponents.kt` - Update `LtBackground`, `LtSurface`, `LtText` to use `MaterialTheme.colorScheme`
- All screen files - Replace hardcoded colors with theme colors

**Color Mapping:**
```kotlin
// Light Theme → Dark Theme mapping
LtBackground (0xFFFAF9F8) → Color(0xFF1A1A1A)
LtSurface (0xFFFFFBF8) → Color(0xFF2A2A2A)
LtText (0xFF4A3428) → Color(0xFFE5E5E5)
LtMuted (0xFF9B8B7E) → Color(0xFF9B9B9B)
```

### Testing Checklist
- [ ] Toggle switches between themes instantly
- [ ] Preference persists after app restart
- [ ] All screens render correctly in dark mode
- [ ] No color contrast issues (text readable)
- [ ] Images/icons visible in both themes

---

## Feature 2: Immersive Display (Hide System Bars)

### Current State
- Standard Android status bar visible
- Navigation bar visible
- No full-screen mode

### Requirements
- Hide status bar (clock, battery, notifications)
- Hide navigation bar (back, home buttons)
- Edge-to-edge content display
- Toggle option in settings

### Implementation Plan

#### 2.1 Enable Edge-to-Edge Display
**File:** `app/src/main/java/com/lendtrack/app/MainActivity.kt`

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Enable immersive mode
    WindowCompat.setDecorFitsSystemWindows(window, false)
    
    // Hide system bars
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.apply {
            hide(WindowInsets.Type.statusBars())
            hide(WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }
    
    setContent {
        // ... existing content
    }
}
```

#### 2.2 Add Immersive Mode Preference
**File:** `app/src/main/java/com/lendtrack/app/core/preferences/ThemePreferences.kt`

```kotlin
private val IMMERSIVE_MODE = booleanPreferencesKey("immersive_mode")

val immersiveMode: Flow<Boolean> = dataStore.data
    .map { prefs -> prefs[IMMERSIVE_MODE] ?: false }

suspend fun setImmersiveMode(enabled: Boolean) {
    dataStore.edit { prefs ->
        prefs[IMMERSIVE_MODE] = enabled
    }
}
```

#### 2.3 Settings Toggle
**File:** `app/src/main/java/com/lendtrack/app/feature/settings/SettingsScreen.kt`

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Column {
        Text("Full Screen Mode", style = MaterialTheme.typography.titleMedium)
        Text("Hide status and navigation bars", 
             style = MaterialTheme.typography.bodySmall, 
             color = LtMuted)
    }
    Switch(
        checked = immersiveMode,
        onCheckedChange = { viewModel.setImmersiveMode(it) }
    )
}
```

### Testing Checklist
- [ ] Status bar hidden in immersive mode
- [ ] Navigation bar hidden in immersive mode
- [ ] Swipe from edge shows bars temporarily
- [ ] Content doesn't overlap with system UI
- [ ] Toggle persists across app restarts

---

## Feature 3: Add "Zelliyah" Branding

### Current State
- Top bar shows generic greeting
- No custom branding

### Requirements
- Display "Zelliyah" prominently in top bar
- Position: Left side or center
- Style: Should match app aesthetic

### Implementation Plan

#### 3.1 Update Top Bar
**File:** `app/src/main/java/com/lendtrack/app/ui/LendTrackApp.kt`

Find `LtTopBar` composable and update:

```kotlin
@Composable
private fun LtTopBar(/* ... */) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LtSurface)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Branding
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Zelliyah",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = GreatVibesFont,  // Existing fancy font
                    fontSize = 32.sp,
                    color = LtRose
                ),
                modifier = Modifier.padding(end = 16.dp)
            )
            
            // Optional subtitle
            Text(
                text = "Lending Management",
                style = MaterialTheme.typography.bodySmall,
                color = LtMuted
            )
        }
        
        // Right: Existing greeting + notification bell
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(greeting, style = MaterialTheme.typography.bodyMedium)
            // ... notification bell
        }
    }
}
```

### Testing Checklist
- [ ] "Zelliyah" displays in top bar
- [ ] Font style matches app aesthetic
- [ ] Visible in both light and dark themes
- [ ] Doesn't overlap with other elements
- [ ] Responsive on different screen sizes

---

## Feature 4: Firebase Sync Diagnostic & Fix

### Current State
- Firebase sync implemented but not working
- Data not reflecting in Firebase console
- No error reporting visible to user

### Requirements
- Diagnose why sync is failing
- Fix connection/authentication issues
- Add sync status indicators
- Implement retry mechanism

### Implementation Plan

#### 4.1 Add Sync Diagnostics
**File:** `app/src/main/java/com/lendtrack/app/core/sync/FirebaseSyncManager.kt`

Add comprehensive logging:

```kotlin
private suspend fun pushBorrower(id: String): Boolean {
    Log.d(TAG, "pushBorrower: Starting sync for borrower $id")
    
    val firebaseDb = db ?: run {
        Log.e(TAG, "pushBorrower: Firebase database instance is null")
        return false
    }
    
    val entity = borrowerDao.getById(id) ?: run {
        Log.w(TAG, "pushBorrower: Borrower $id not found in local database")
        return true
    }
    
    val uid = ownerUid() ?: run {
        Log.e(TAG, "pushBorrower: User not authenticated (uid is null)")
        return false
    }
    
    Log.d(TAG, "pushBorrower: User UID = $uid")
    
    try {
        val ref = firebaseDb.getReference("$ROOT/$uid/borrowers/${entity.id}")
        Log.d(TAG, "pushBorrower: Writing to path: $ROOT/$uid/borrowers/${entity.id}")
        
        val data = mapOf(/* ... */)
        ref.setValue(data).await()
        
        Log.i(TAG, "pushBorrower: Successfully synced borrower ${entity.name}")
        return true
    } catch (e: Exception) {
        Log.e(TAG, "pushBorrower: Failed to sync borrower $id", e)
        return false
    }
}
```

#### 4.2 Check Firebase Configuration
**File:** `app/google-services.json`

Verify:
- Correct project ID
- Correct database URL
- API keys are valid

**File:** `app/build.gradle.kts`

Verify Firebase dependencies:
```kotlin
implementation("com.google.firebase:firebase-database-ktx:20.3.0")
implementation("com.google.firebase:firebase-auth-ktx:22.3.0")
```

#### 4.3 Add Manual Sync Button
**File:** `app/src/main/java/com/lendtrack/app/feature/settings/SettingsScreen.kt`

```kotlin
LtButton(
    text = "Force Sync Now",
    onClick = {
        viewModel.forceSyncAll()  // New method
    },
    icon = Icons.Filled.Sync
)

// Show last sync attempt status
if (lastSyncError != null) {
    Text(
        text = "Last sync error: $lastSyncError",
        color = LtRed,
        style = MaterialTheme.typography.bodySmall
    )
}
```

#### 4.4 Add Sync Status Indicator
Show real-time sync progress in UI:

```kotlin
when (syncStatus) {
    SyncStatus.SYNCING -> {
        Row {
            CircularProgressIndicator(modifier = Modifier.size(16.dp))
            Text("Syncing...")
        }
    }
    SyncStatus.FAILED -> {
        Row {
            Icon(Icons.Filled.Error, tint = LtRed)
            Text("Sync failed", color = LtRed)
        }
    }
    SyncStatus.SYNCED -> {
        Row {
            Icon(Icons.Filled.CheckCircle, tint = LtSage)
            Text("Synced", color = LtSage)
        }
    }
}
```

#### 4.5 Test Firebase Connection
Create diagnostic screen:

```kotlin
@Composable
fun FirebaseDiagnosticScreen(viewModel: DiagnosticViewModel) {
    Column {
        Text("Firebase Diagnostics", style = MaterialTheme.typography.headlineMedium)
        
        // Test 1: Database instance
        TestResult("Database Instance", viewModel.hasDatabaseInstance)
        
        // Test 2: Authentication
        TestResult("User Authenticated", viewModel.isAuthenticated)
        
        // Test 3: Write test
        LtButton("Test Write") {
            viewModel.testFirebaseWrite()
        }
        if (viewModel.testWriteResult != null) {
            Text(viewModel.testWriteResult!!)
        }
        
        // Test 4: Read test
        LtButton("Test Read") {
            viewModel.testFirebaseRead()
        }
        
        // Show pending sync operations
        Text("Pending Syncs: ${viewModel.pendingCount}")
    }
}
```

### Investigation Steps
1. Check logcat for Firebase errors
2. Verify `google-services.json` is correct
3. Check Firebase console → Database → Rules
4. Verify authentication is working
5. Test with manual sync button

### Testing Checklist
- [ ] Firebase initialization successful
- [ ] User authentication working
- [ ] Data appears in Firebase console
- [ ] Sync operations complete without errors
- [ ] Retry works after failure
- [ ] Sync status shows accurate state

---

## Feature 5: Monthly Interest & Late Penalty System

### Current State
- Only fixed interest amount at loan creation
- No monthly recurring interest
- No late penalty tracking
- No penalty reflection in balance

### Requirements
- **Monthly Interest**: Recurring charge every month
- **Late Penalty**: Fee charged for overdue installments
- Both should be editable per loan
- Must reflect in borrower's total balance
- Display separately in payment history

### Implementation Plan

#### 5.1 Update Domain Models
**File:** `app/src/main/java/com/lendtrack/app/domain/model/DomainModels.kt`

```kotlin
data class Loan(
    val id: String = UUID.randomUUID().toString(),
    val borrowerId: String,
    val principal: Double,
    val interestAmount: Double,  // One-time interest
    
    // NEW: Monthly recurring interest
    val monthlyInterestAmount: Double = 0.0,  // e.g., ₱150/month
    
    // NEW: Late penalty settings
    val latePenaltyAmount: Double = 0.0,      // e.g., ₱50 per overdue installment
    val latePenaltyGraceDays: Int = 0,        // Days before penalty applies
    
    val loanDate: LocalDate,
    val firstDueDate: LocalDate,
    val numberOfInstallments: Int,
    val paymentFrequency: PaymentFrequency,
    val customFrequencyDays: Int = 0,
    val status: LoanStatus = LoanStatus.ACTIVE,
    val notes: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val serverId: String? = null,
) {
    val totalPayable: Double get() = principal + interestAmount
    val installmentAmount: Double get() = totalPayable / numberOfInstallments
    val effectiveRate: Double? get() = if (principal > 0) (interestAmount / principal) * 100 else null
}

// NEW: Track monthly interest charges
data class MonthlyInterestCharge(
    val id: String = UUID.randomUUID().toString(),
    val loanId: String,
    val borrowerId: String,
    val amount: Double,
    val chargeDate: LocalDate,  // First day of month
    val isPaid: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)

// NEW: Track late penalties
data class LatePenalty(
    val id: String = UUID.randomUUID().toString(),
    val loanId: String,
    val borrowerId: String,
    val installmentId: String,
    val amount: Double,
    val penaltyDate: LocalDate,  // When penalty was applied
    val reason: String = "Overdue payment",
    val isPaid: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)
```

#### 5.2 Update Database Schema
**File:** `app/src/main/java/com/lendtrack/app/core/database/entities/Entities.kt`

```kotlin
@Entity(tableName = "loans")
data class LoanEntity(
    // ... existing fields ...
    
    @ColumnInfo(name = "monthly_interest_amount")
    val monthlyInterestAmount: Double = 0.0,
    
    @ColumnInfo(name = "late_penalty_amount")
    val latePenaltyAmount: Double = 0.0,
    
    @ColumnInfo(name = "late_penalty_grace_days")
    val latePenaltyGraceDays: Int = 0,
)

@Entity(tableName = "monthly_interest_charges")
data class MonthlyInterestChargeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "loan_id") val loanId: String,
    @ColumnInfo(name = "borrower_id") val borrowerId: String,
    val amount: Double,
    @ColumnInfo(name = "charge_date") val chargeDate: String,  // ISO format
    @ColumnInfo(name = "is_paid") val isPaid: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
)

@Entity(tableName = "late_penalties")
data class LatePenaltyEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "loan_id") val loanId: String,
    @ColumnInfo(name = "borrower_id") val borrowerId: String,
    @ColumnInfo(name = "installment_id") val installmentId: String,
    val amount: Double,
    @ColumnInfo(name = "penalty_date") val penaltyDate: String,
    val reason: String,
    @ColumnInfo(name = "is_paid") val isPaid: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
)
```

#### 5.3 Create Database Migration
**File:** `app/src/main/java/com/lendtrack/app/core/database/migrations/Migrations.kt`

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add new columns to loans table
        database.execSQL("""
            ALTER TABLE loans 
            ADD COLUMN monthly_interest_amount REAL NOT NULL DEFAULT 0.0
        """)
        database.execSQL("""
            ALTER TABLE loans 
            ADD COLUMN late_penalty_amount REAL NOT NULL DEFAULT 0.0
        """)
        database.execSQL("""
            ALTER TABLE loans 
            ADD COLUMN late_penalty_grace_days INTEGER NOT NULL DEFAULT 0
        """)
        
        // Create monthly_interest_charges table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS monthly_interest_charges (
                id TEXT PRIMARY KEY NOT NULL,
                loan_id TEXT NOT NULL,
                borrower_id TEXT NOT NULL,
                amount REAL NOT NULL,
                charge_date TEXT NOT NULL,
                is_paid INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL,
                sync_status TEXT NOT NULL,
                FOREIGN KEY(loan_id) REFERENCES loans(id) ON DELETE CASCADE
            )
        """)
        
        // Create late_penalties table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS late_penalties (
                id TEXT PRIMARY KEY NOT NULL,
                loan_id TEXT NOT NULL,
                borrower_id TEXT NOT NULL,
                installment_id TEXT NOT NULL,
                amount REAL NOT NULL,
                penalty_date TEXT NOT NULL,
                reason TEXT NOT NULL,
                is_paid INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL,
                sync_status TEXT NOT NULL,
                FOREIGN KEY(loan_id) REFERENCES loans(id) ON DELETE CASCADE,
                FOREIGN KEY(installment_id) REFERENCES installments(id) ON DELETE CASCADE
            )
        """)
    }
}
```

#### 5.4 Update Add/Edit Loan UI
**File:** `app/src/main/java/com/lendtrack/app/feature/loans/AddLoanScreen.kt`

Add input fields:

```kotlin
// After interest amount field
LtTextField(
    label = "Monthly Interest (₱)",
    value = fields.monthlyInterestText,
    onValueChange = { viewModel.updateField { copy(monthlyInterestText = it) } },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    helperText = "Recurring charge every month (optional)"
)

LtTextField(
    label = "Late Penalty (₱)",
    value = fields.latePenaltyText,
    onValueChange = { viewModel.updateField { copy(latePenaltyText = it) } },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    helperText = "Fee for overdue installments (optional)"
)

LtTextField(
    label = "Penalty Grace Period (days)",
    value = fields.graceDaysText,
    onValueChange = { viewModel.updateField { copy(graceDaysText = it) } },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    helperText = "Days before late penalty applies"
)
```

#### 5.5 Automatic Monthly Interest Charging
**File:** `app/src/main/java/com/lendtrack/app/workers/MonthlyInterestWorker.kt` (NEW)

```kotlin
@HiltWorker
class MonthlyInterestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val loanDao: LoanDao,
    private val monthlyInterestDao: MonthlyInterestChargeDao,
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        val firstOfMonth = today.withDayOfMonth(1)
        
        // Get all active loans with monthly interest
        val activeLoans = loanDao.getActiveLoansWithMonthlyInterest()
        
        activeLoans.forEach { loan ->
            // Check if already charged this month
            val existingCharge = monthlyInterestDao.getChargeForMonth(
                loan.id, 
                firstOfMonth.toString()
            )
            
            if (existingCharge == null && loan.monthlyInterestAmount > 0) {
                // Create monthly interest charge
                val charge = MonthlyInterestChargeEntity(
                    id = UUID.randomUUID().toString(),
                    loanId = loan.id,
                    borrowerId = loan.borrowerId,
                    amount = loan.monthlyInterestAmount,
                    chargeDate = firstOfMonth.toString(),
                    isPaid = false,
                    createdAt = LocalDateTime.now().toString(),
                    syncStatus = SyncStatus.PENDING.name,
                )
                
                monthlyInterestDao.insert(charge)
                Log.i(TAG, "Charged monthly interest for loan ${loan.id}: ₱${loan.monthlyInterestAmount}")
            }
        }
        
        return Result.success()
    }
}
```

Schedule worker in `LendTrackApplication`:
```kotlin
val monthlyInterestWork = PeriodicWorkRequestBuilder<MonthlyInterestWorker>(
    1, TimeUnit.DAYS
).build()

workManager.enqueueUniquePeriodicWork(
    "monthly_interest_check",
    ExistingPeriodicWorkPolicy.KEEP,
    monthlyInterestWork
)
```

#### 5.6 Automatic Late Penalty Application
**File:** `app/src/main/java/com/lendtrack/app/workers/LatePenaltyWorker.kt` (NEW)

```kotlin
@HiltWorker
class LatePenaltyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val installmentDao: InstallmentDao,
    private val loanDao: LoanDao,
    private val penaltyDao: LatePenaltyDao,
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        
        // Get all overdue installments
        val overdueInstallments = installmentDao.getOverdueSuspend(today.toString())
        
        overdueInstallments.forEach { installment ->
            val loan = loanDao.getById(installment.loanId) ?: return@forEach
            
            if (loan.latePenaltyAmount <= 0) return@forEach
            
            // Calculate days overdue
            val daysOverdue = today.toEpochDay() - installment.dueDate.toEpochDay()
            
            if (daysOverdue <= loan.latePenaltyGraceDays) return@forEach
            
            // Check if penalty already applied for this installment
            val existingPenalty = penaltyDao.getPenaltyForInstallment(installment.id)
            
            if (existingPenalty == null) {
                // Apply late penalty
                val penalty = LatePenaltyEntity(
                    id = UUID.randomUUID().toString(),
                    loanId = loan.id,
                    borrowerId = loan.borrowerId,
                    installmentId = installment.id,
                    amount = loan.latePenaltyAmount,
                    penaltyDate = today.toString(),
                    reason = "Payment overdue by $daysOverdue days",
                    isPaid = false,
                    createdAt = LocalDateTime.now().toString(),
                    syncStatus = SyncStatus.PENDING.name,
                )
                
                penaltyDao.insert(penalty)
                Log.i(TAG, "Applied late penalty for installment ${installment.id}: ₱${loan.latePenaltyAmount}")
            }
        }
        
        return Result.success()
    }
}
```

#### 5.7 Update Balance Calculation
**File:** `app/src/main/java/com/lendtrack/app/data/repository/LoanRepository.kt`

```kotlin
suspend fun getTotalBalance(loanId: String): LoanBalance {
    val installmentBalance = installmentDao.getRemainingBalance(loanId)
    val unpaidMonthlyCharges = monthlyInterestDao.getUnpaidTotal(loanId)
    val unpaidPenalties = penaltyDao.getUnpaidTotal(loanId)
    
    return LoanBalance(
        installmentBalance = installmentBalance,
        monthlyCharges = unpaidMonthlyCharges,
        penalties = unpaidPenalties,
        totalBalance = installmentBalance + unpaidMonthlyCharges + unpaidPenalties
    )
}

data class LoanBalance(
    val installmentBalance: Double,
    val monthlyCharges: Double,
    val penalties: Double,
    val totalBalance: Double
)
```

#### 5.8 Display in Borrower Detail Screen
**File:** `app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailScreen.kt`

```kotlin
// In loan card, show breakdown
Column {
    Text("Balance Breakdown", style = MaterialTheme.typography.labelSmall)
    Row {
        Text("Installments:", Modifier.weight(1f))
        Text(loanBalance.installmentBalance.peso())
    }
    if (loanBalance.monthlyCharges > 0) {
        Row {
            Text("Monthly Interest:", Modifier.weight(1f), color = LtOrange)
            Text(loanBalance.monthlyCharges.peso(), color = LtOrange)
        }
    }
    if (loanBalance.penalties > 0) {
        Row {
            Text("Late Penalties:", Modifier.weight(1f), color = LtRed)
            Text(loanBalance.penalties.peso(), color = LtRed)
        }
    }
    Divider()
    Row(modifier = Modifier.padding(top = 4.dp)) {
        Text("Total Balance:", Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text(loanBalance.totalBalance.peso(), fontWeight = FontWeight.Bold)
    }
}
```

### Testing Checklist
- [ ] Monthly interest field appears in loan form
- [ ] Late penalty field appears in loan form
- [ ] Monthly charges applied automatically
- [ ] Late penalties applied after grace period
- [ ] Balance reflects all charges
- [ ] Payments can be allocated to charges/penalties
- [ ] Sync to Firebase includes new fields

---

## Feature 6: Document Upload (ID & Billing Address)

### Current State
- No image upload functionality
- No document storage

### Requirements
- Upload borrower's government ID (photo)
- Upload billing address proof (photo)
- Store images separately from borrower data
- Display images in borrower detail screen
- Compress images before upload
- Support JPG, PNG formats

### Implementation Plan

#### 6.1 Add Document Domain Models
**File:** `app/src/main/java/com/lendtrack/app/domain/model/DomainModels.kt`

```kotlin
enum class DocumentType {
    GOVERNMENT_ID,
    BILLING_ADDRESS,
    OTHER
}

data class BorrowerDocument(
    val id: String = UUID.randomUUID().toString(),
    val borrowerId: String,
    val documentType: DocumentType,
    val fileName: String,
    val localFilePath: String?,      // Local cache
    val firebaseStoragePath: String?, // Cloud path
    val uploadedAt: LocalDateTime = LocalDateTime.now(),
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)
```

#### 6.2 Update Database Schema
**File:** `app/src/main/java/com/lendtrack/app/core/database/entities/Entities.kt`

```kotlin
@Entity(tableName = "borrower_documents")
data class BorrowerDocumentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "borrower_id") val borrowerId: String,
    @ColumnInfo(name = "document_type") val documentType: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "local_file_path") val localFilePath: String?,
    @ColumnInfo(name = "firebase_storage_path") val firebaseStoragePath: String?,
    @ColumnInfo(name = "uploaded_at") val uploadedAt: String,
    @ColumnInfo(name = "sync_status") val syncStatus: String,
)
```

#### 6.3 Create Migration
**File:** `app/src/main/java/com/lendtrack/app/core/database/migrations/Migrations.kt`

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS borrower_documents (
                id TEXT PRIMARY KEY NOT NULL,
                borrower_id TEXT NOT NULL,
                document_type TEXT NOT NULL,
                file_name TEXT NOT NULL,
                local_file_path TEXT,
                firebase_storage_path TEXT,
                uploaded_at TEXT NOT NULL,
                sync_status TEXT NOT NULL,
                FOREIGN KEY(borrower_id) REFERENCES borrowers(id) ON DELETE CASCADE
            )
        """)
    }
}
```

#### 6.4 Add Firebase Storage Configuration
**File:** `build.gradle.kts` (app level)

```kotlin
dependencies {
    // ... existing dependencies ...
    implementation("com.google.firebase:firebase-storage-ktx:20.3.0")
}
```

#### 6.5 Create Document Upload Service
**File:** `app/src/main/java/com/lendtrack/app/core/storage/DocumentStorageManager.kt` (NEW)

```kotlin
@Singleton
class DocumentStorageManager @Inject constructor(
    private val context: Context,
    private val documentDao: BorrowerDocumentDao,
) {
    private val storage by lazy {
        FirebaseStorage.getInstance()
    }
    
    private val auth by lazy {
        FirebaseAuth.getInstance()
    }
    
    suspend fun uploadDocument(
        borrowerId: String,
        documentType: DocumentType,
        imageUri: Uri
    ): Result<BorrowerDocument> = withContext(Dispatchers.IO) {
        try {
            val uid = auth.currentUser?.uid 
                ?: return@withContext Result.failure(Exception("Not authenticated"))
            
            // Compress image
            val compressedFile = compressImage(imageUri)
            
            // Generate storage path
            val fileName = "${documentType.name}_${System.currentTimeMillis()}.jpg"
            val storagePath = "lendtrack/$uid/borrowers/$borrowerId/documents/$fileName"
            
            // Upload to Firebase Storage
            val storageRef = storage.reference.child(storagePath)
            storageRef.putFile(Uri.fromFile(compressedFile)).await()
            
            // Get download URL
            val downloadUrl = storageRef.downloadUrl.await()
            
            // Save to local database
            val document = BorrowerDocument(
                id = UUID.randomUUID().toString(),
                borrowerId = borrowerId,
                documentType = documentType,
                fileName = fileName,
                localFilePath = compressedFile.absolutePath,
                firebaseStoragePath = storagePath,
                uploadedAt = LocalDateTime.now(),
                syncStatus = SyncStatus.SYNCED
            )
            
            documentDao.insert(document.toEntity())
            
            Result.success(document)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload document", e)
            Result.failure(e)
        }
    }
    
    private suspend fun compressImage(uri: Uri): File {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        
        // Compress to max 1024x1024, 80% quality
        val maxDimension = 1024
        val ratio = minOf(
            maxDimension.toFloat() / bitmap.width,
            maxDimension.toFloat() / bitmap.height
        )
        
        val scaledBitmap = if (ratio < 1) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true
            )
        } else {
            bitmap
        }
        
        val outputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outputFile).use { out ->
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        
        return outputFile
    }
    
    suspend fun deleteDocument(documentId: String): Result<Unit> {
        val document = documentDao.getById(documentId) ?: return Result.success(Unit)
        
        try {
            // Delete from Firebase Storage
            if (document.firebaseStoragePath != null) {
                storage.reference.child(document.firebaseStoragePath).delete().await()
            }
            
            // Delete local file
            if (document.localFilePath != null) {
                File(document.localFilePath).delete()
            }
            
            // Delete from database
            documentDao.delete(document.id)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete document", e)
            Result.failure(e)
        }
    }
}
```

#### 6.6 Add Upload UI to Borrower Detail Screen
**File:** `app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailScreen.kt`

```kotlin
// Add document section
LtPanel(title = "Documents") {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Government ID section
        DocumentUploadSection(
            label = "Government ID",
            documentType = DocumentType.GOVERNMENT_ID,
            existingDocument = state.documents.find { 
                it.documentType == DocumentType.GOVERNMENT_ID 
            },
            onUpload = { uri -> 
                viewModel.uploadDocument(DocumentType.GOVERNMENT_ID, uri) 
            },
            onDelete = { docId -> 
                viewModel.deleteDocument(docId) 
            }
        )
        
        // Billing address section
        DocumentUploadSection(
            label = "Billing Address Proof",
            documentType = DocumentType.BILLING_ADDRESS,
            existingDocument = state.documents.find { 
                it.documentType == DocumentType.BILLING_ADDRESS 
            },
            onUpload = { uri -> 
                viewModel.uploadDocument(DocumentType.BILLING_ADDRESS, uri) 
            },
            onDelete = { docId -> 
                viewModel.deleteDocument(docId) 
            }
        )
    }
}

@Composable
fun DocumentUploadSection(
    label: String,
    documentType: DocumentType,
    existingDocument: BorrowerDocument?,
    onUpload: (Uri) -> Unit,
    onDelete: (String) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onUpload(it) }
    }
    
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        
        if (existingDocument != null) {
            // Show uploaded document
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable { /* open full screen */ }
            ) {
                Box {
                    // Load and display image
                    AsyncImage(
                        model = existingDocument.localFilePath,
                        contentDescription = label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Delete button overlay
                    IconButton(
                        onClick = { onDelete(existingDocument.id) },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Filled.Delete, "Delete", tint = Color.White)
                    }
                }
            }
        } else {
            // Upload button
            OutlinedButton(
                onClick = { launcher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Upload, "Upload")
                Spacer(Modifier.width(8.dp))
                Text("Upload $label")
            }
        }
    }
}
```

#### 6.7 Add Coil Image Loading
**File:** `build.gradle.kts` (app level)

```kotlin
dependencies {
    implementation("io.coil-kt:coil-compose:2.5.0")
}
```

### Testing Checklist
- [ ] Can select image from gallery
- [ ] Image compresses before upload
- [ ] Upload to Firebase Storage succeeds
- [ ] Image displays in borrower detail screen
- [ ] Can delete uploaded document
- [ ] Multiple documents supported per borrower
- [ ] Works offline (stores locally, syncs later)

---

## Feature 7: Add Union Bank & Metro Bank

### Current State
Payment methods:
- Cash, GCash, Maya, GoTyme, China Bank, Maribank, Bank Transfer, Other

### Requirements
- Add "Union Bank" option
- Add "Metro Bank" option

### Implementation Plan

#### 7.1 Update PaymentMethod Enum
**File:** `app/src/main/java/com/lendtrack/app/domain/model/DomainModels.kt`

```kotlin
enum class PaymentMethod(val label: String) {
    CASH("Cash"),
    GCASH("GCash"),
    MAYA("Maya"),
    GOTYME("GoTyme"),
    CHINA_BANK("China Bank"),
    MARIBANK("Maribank"),
    UNION_BANK("Union Bank"),      // NEW
    METRO_BANK("Metro Bank"),      // NEW
    BANK_TRANSFER("Bank Transfer"),
    OTHER("Other"),
}
```

#### 7.2 Update Database TypeConverter
**File:** `app/src/main/java/com/lendtrack/app/core/database/entities/Entities.kt`

Verify TypeConverter handles new values (should be automatic):

```kotlin
class Converters {
    @TypeConverter
    fun toPaymentMethod(value: String): PaymentMethod =
        PaymentMethod.valueOf(value)
    
    @TypeConverter
    fun fromPaymentMethod(method: PaymentMethod): String =
        method.name
}
```

No migration needed - enum values stored as strings.

### Testing Checklist
- [ ] Union Bank appears in payment method dropdown
- [ ] Metro Bank appears in payment method dropdown
- [ ] Can select when adding borrower
- [ ] Can select when recording payment
- [ ] Displays correctly in payment history
- [ ] Syncs to Firebase correctly

---

## Feature 8: Balance Reflection

### Current State
- Installment balances calculated
- Payment allocations tracked

### Requirements
- Ensure monthly charges reflect in total balance
- Ensure late penalties reflect in total balance
- Display separate line items
- Include in "Outstanding" summary

### Implementation Plan

#### 8.1 Update Balance Calculation (Already covered in Feature 5.7)

#### 8.2 Update Dashboard Total Outstanding
**File:** `app/src/main/java/com/lendtrack/app/feature/dashboard/DashboardViewModel.kt`

```kotlin
val totalOutstanding: Flow<Double> = combine(
    installmentDao.observeTotalOutstanding(),
    monthlyInterestDao.observeTotalUnpaid(),
    penaltyDao.observeTotalUnpaid()
) { installments, monthly, penalties ->
    installments + monthly + penalties
}
```

#### 8.3 Update Borrower Detail Outstanding
**File:** `app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailViewModel.kt`

```kotlin
private fun calculateBorrowerOutstanding(borrowerId: String): Flow<Double> {
    return combine(
        loanRepo.observeLoansByBorrower(borrowerId),
        monthlyInterestDao.observeByBorrower(borrowerId),
        penaltyDao.observeByBorrower(borrowerId)
    ) { loans, charges, penalties ->
        val installmentTotal = loans.sumOf { it.remainingBalance }
        val chargesTotal = charges.filter { !it.isPaid }.sumOf { it.amount }
        val penaltiesTotal = penalties.filter { !it.isPaid }.sumOf { it.amount }
        
        installmentTotal + chargesTotal + penaltiesTotal
    }
}
```

### Testing Checklist
- [ ] Dashboard shows correct total outstanding
- [ ] Borrower detail shows correct outstanding
- [ ] Balance includes installments + charges + penalties
- [ ] Payment allocation reduces correct amounts
- [ ] Reports include all balance components

---

## Implementation Order & Timeline

### Phase 1: Quick Wins (Day 1-2)
1. **Add Union Bank & Metro Bank** (30 min)
2. **Add "Zelliyah" Branding** (1 hour)
3. **Firebase Sync Diagnostic** (2 hours)

### Phase 2: Core Features (Day 3-5)
4. **Dark Mode Implementation** (4-6 hours)
5. **Immersive Display** (2 hours)
6. **Firebase Sync Fix** (2-4 hours based on findings)

### Phase 3: Advanced Features (Day 6-9)
7. **Monthly Interest & Late Penalty System** (8-10 hours)
   - Database changes
   - Workers
   - UI updates
   - Testing

### Phase 4: Document Management (Day 10-12)
8. **Document Upload System** (6-8 hours)
   - Firebase Storage setup
   - Image compression
   - Upload/download
   - UI integration

### Phase 5: Integration & Testing (Day 13-14)
9. **Balance Reflection** (2 hours)
10. **End-to-end testing** (4-6 hours)
11. **Bug fixes** (2-4 hours)

**Total Estimated Time:** 33-47 hours of development work

---

## Risk Assessment

### High Risk
- **Firebase sync issues** - May require extensive debugging
- **Image upload performance** - Need proper compression/optimization
- **Monthly interest worker** - Must be reliable and accurate

### Medium Risk
- **Dark mode color adjustments** - May need multiple iterations for readability
- **Balance calculation complexity** - Multiple sources of charges
- **Migration testing** - Existing user data must be preserved

### Low Risk
- Bank additions - Simple enum update
- Branding - Straightforward UI change
- Immersive mode - Standard Android API

---

## Testing Strategy

### Unit Tests
- Loan balance calculations
- Monthly interest logic
- Late penalty logic
- Payment allocation with charges/penalties

### Integration Tests
- Firebase sync end-to-end
- Document upload/download
- Worker execution
- Database migrations

### Manual Testing
- Theme switching on multiple screens
- Image upload with various sizes
- Offline sync behavior
- Balance accuracy across all screens

### Performance Testing
- Image compression time
- Large document handling
- Sync queue processing
- Worker battery impact

---

## Success Criteria

✅ **Feature Complete:**
- [ ] Dark/Light mode toggle works smoothly
- [ ] Immersive mode hides system bars
- [ ] "Zelliyah" displays prominently
- [ ] Firebase sync verified working
- [ ] Monthly interest charges automatically
- [ ] Late penalties apply correctly
- [ ] Documents upload and display
- [ ] Union Bank & Metro Bank selectable
- [ ] All balances reflect correctly

✅ **Quality Gates:**
- [ ] No compilation errors
- [ ] All migrations tested
- [ ] No data loss
- [ ] Sync indicators accurate
- [ ] Performance acceptable (<3s for operations)
- [ ] No crashes on tested devices
- [ ] User experience smooth and intuitive

---

## Next Steps

1. **Review and approve this plan**
2. **Prioritize features** (can implement in different order if needed)
3. **Begin implementation** starting with Phase 1
4. **Incremental testing** after each phase
5. **User acceptance testing** before final release

---

## Questions for Clarification

1. **Monthly Interest**: Should it be charged on the first of every month, or monthly from loan creation date?
2. **Late Penalties**: Should penalty compound (one per day) or apply once per overdue installment?
3. **Documents**: Should we support multiple documents per type (e.g., 2 IDs)?
4. **Dark Mode**: Should we include an "Auto" option that follows system theme?
5. **Immersive Mode**: Should this be always-on or user-toggleable?

---

**Plan Status:** ✅ Ready for Review  
**Next Action:** Await approval and clarification of questions above
