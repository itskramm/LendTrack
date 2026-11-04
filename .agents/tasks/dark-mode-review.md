# Dark Mode Color Implementation Review

## Review Date
2026-11-04

## Task Summary
Review the consistent dark mode color implementation across all LendTrack screens to ensure:
- Pure black backgrounds (#000000)
- Vibrant pink primary color (#E91E63)
- No hardcoded Lt* colors (except status badges)
- All screens using MaterialTheme.colorScheme

## Files Reviewed
- All feature screen files (17 files)
- Core UI components (LtComponents.kt)
- Main app file (LendTrackApp.kt)
- Theme definitions (Theme.kt, Color.kt)

## Build Status
✅ Build reported as successful in commit message

## Findings

### BLOCKING ISSUES

#### 1. LoginScreen.kt - Hardcoded LtRose Colors
**Severity:** HIGH  
**File:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/auth/LoginScreen.kt`  
**Lines:** 96, 177, 211, 217

**Evidence:**
```kotlin
// Line 96 - Tagline color
color = LtRose,

// Line 177 - Button container color
containerColor = LtRose,

// Line 211 - TextField focus border
focusedBorderColor = LtRose,

// Line 217 - TextField cursor color
cursorColor = LtRose,
```

**Impact:** Login screen won't properly display vibrant pink in dark mode. Button, text field borders, and tagline will show the light mode rose color (#C45C72) instead of dark mode primary (#E91E63).

**Required Fix:** Replace all instances with `MaterialTheme.colorScheme.primary`

---

#### 2. SplashScreen.kt - Hardcoded LtRose
**Severity:** HIGH  
**File:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/auth/SplashScreen.kt`  
**Line:** 68

**Evidence:**
```kotlin
style = MaterialTheme.typography.headlineSmall.copy(
    fontFamily = PlayfairDisplay,
    color      = LtRose,
),
```

**Impact:** Splash screen tagline won't display in proper dark mode pink color.

**Required Fix:** Replace with `MaterialTheme.colorScheme.primary`

---

#### 3. BorrowersScreen.kt - Multiple Hardcoded Colors
**Severity:** HIGH  
**File:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowersScreen.kt`  
**Lines:** 117, 119, 183, 315, 319

**Evidence:**
```kotlin
// Line 117 - Status color for "Active"
"Active" -> LtDusty

// Line 119 - Status color fallback
else -> LtMuted

// Line 183 - Dialog container
containerColor = LtSurface,

// Line 315 - TextField focus border
focusedBorderColor = LtRose,

// Line 319 - TextField cursor
cursorColor = LtRose,
```

**Impact:** 
- Status badges won't adapt to dark mode theme colors
- Dialog background won't show proper dark surface (#0A0A0A)
- Text field interactions won't show proper dark mode pink

**Required Fix:** 
- Line 117: `MaterialTheme.colorScheme.secondary`
- Line 119: `MaterialTheme.colorScheme.onSurfaceVariant`
- Line 183: `MaterialTheme.colorScheme.surface`
- Lines 315, 319: `MaterialTheme.colorScheme.primary`

---

#### 4. BorrowerDetailScreen.kt - Hardcoded LtBurgundy
**Severity:** HIGH  
**File:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowerDetailScreen.kt`  
**Lines:** 202, 350, 420

**Evidence:**
```kotlin
// Line 202 - Outstanding balance color
color = if (state.totalOutstanding > 0) LtBurgundy else LtSage,

// Line 350 - Loan remaining balance color
valueColor = if (lwb.remainingBalance > 0) LtBurgundy else LtSage,

// Line 420 - Default parameter for DetailStat
valueColor: Color = LtBurgundy,
```

**Impact:** Outstanding balance amounts won't display with proper dark mode color adaptation. Will show light mode burgundy (#783746) instead of adapting to dark theme.

**Required Fix:** Replace `LtBurgundy` with `MaterialTheme.colorScheme.primary`

---

#### 5. SettingsScreen.kt - Dialog Container and Default Colors
**Severity:** HIGH  
**File:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/settings/SettingsScreen.kt`  
**Lines:** 46, 304

**Evidence:**
```kotlin
// Line 46 - Sign out dialog container
containerColor = LtSurface,

// Line 304 - Default parameter for SettingRow
valueColor: Color = LtBurgundy,
```

**Impact:** 
- Sign-out dialog won't show dark mode surface color
- Settings values won't adapt to dark mode primary color

**Required Fix:**
- Line 46: `MaterialTheme.colorScheme.surface`
- Line 304: `MaterialTheme.colorScheme.primary`

---

#### 6. BalanceLedgerScreen.kt - Hardcoded LtBurgundy
**Severity:** HIGH  
**File:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/ledger/BalanceLedgerScreen.kt`  
**Line:** 248

**Evidence:**
```kotlin
color = if (displayBalance > 0) LtBurgundy else LtSage,
```

**Impact:** Loan balance display in ledger won't show proper dark mode color.

**Required Fix:** Replace `LtBurgundy` with `MaterialTheme.colorScheme.primary`

---

#### 7. PaymentCalendarScreen.kt - Multiple Hardcoded Colors
**Severity:** HIGH  
**File:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/calendar/PaymentCalendarScreen.kt`  
**Lines:** 115, 161, 204, 293

**Evidence:**
```kotlin
// Line 115 - Legend color for "Upcoming"
LtDusty to "Upcoming",

// Line 161 - Borrower name link color
color = LtRose,

// Line 204 - Day of week header color
color = LtRose,

// Line 293 - Installment dot color for non-critical statuses
else -> LtDusty
```

**Impact:** 
- Calendar legend won't adapt to dark mode
- Interactive borrower links won't show proper pink
- Calendar headers won't use dark mode primary color
- Calendar dots won't adapt to theme

**Required Fix:**
- Line 115: `MaterialTheme.colorScheme.secondary`
- Lines 161, 204: `MaterialTheme.colorScheme.primary`
- Line 293: `MaterialTheme.colorScheme.secondary`

---

#### 8. RecordPaymentScreen.kt - Hardcoded LtBurgundy
**Severity:** HIGH  
**File:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/payments/RecordPaymentScreen.kt`  
**Lines:** 189, 300

**Evidence:**
```kotlin
// Line 189 - Balance after color
color = if (preview.balanceAfter <= 0) LtSage else LtBurgundy

// Line 300 - Remaining after allocation color
color = if (item.remainingAfter <= 0) LtSage else LtBurgundy
```

**Impact:** Payment allocation preview and results won't show proper dark mode colors for outstanding balances.

**Required Fix:** Replace `LtBurgundy` with `MaterialTheme.colorScheme.primary`

---

#### 9. DocumentViewerScreen.kt - Unused Import
**Severity:** LOW  
**File:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/borrowers/DocumentViewerScreen.kt`  
**Line:** 21

**Evidence:**
```kotlin
import com.lendtrack.app.core.ui.theme.LtRose
```

**Impact:** No functional impact, but indicates incomplete cleanup. The import is present but the color is not used in the file.

**Required Fix:** Remove unused import

---

### SEMANTIC STATUS COLORS (Allowed)

The following usages of Lt* colors are **APPROVED** as they are semantic status indicators:

- `LtSage` (green) - Used for "Paid" status and positive balance states
- `LtAmber` (yellow) - Used for "Due Soon" warnings and overpayment
- `LtRed` (red) - Used for "Overdue" status and errors
- Status badge color constants (StatusPaid, StatusDueSoon, StatusOverdue, StatusUpcoming)

These are intentionally kept as fixed semantic colors that work in both light and dark modes.

---

## Coverage Analysis

### Screens Reviewed ✅
1. LoginScreen.kt - ❌ ISSUES FOUND
2. SplashScreen.kt - ❌ ISSUES FOUND
3. DashboardScreen.kt - ✅ Clean
4. BorrowersScreen.kt - ❌ ISSUES FOUND
5. BorrowerDetailScreen.kt - ❌ ISSUES FOUND
6. DocumentViewerScreen.kt - ⚠️ Minor cleanup needed
7. AddLoanScreen.kt - ✅ Clean
8. EditLoanDialog.kt - ✅ Clean
9. PaymentsScreen.kt - ✅ Clean
10. RecordPaymentScreen.kt - ❌ ISSUES FOUND
11. PaymentCalendarScreen.kt - ❌ ISSUES FOUND
12. BalanceLedgerScreen.kt - ❌ ISSUES FOUND
13. ReportsScreen.kt - ✅ Clean
14. SettingsScreen.kt - ❌ ISSUES FOUND

### Core Components ✅
- LtComponents.kt - ✅ Clean
- LendTrackApp.kt - ✅ Clean

---

## Summary

**Total Blocker Issues:** 9 (8 HIGH, 1 LOW)

**Files with Blocking Issues:** 8
- LoginScreen.kt (4 instances)
- SplashScreen.kt (1 instance)
- BorrowersScreen.kt (5 instances)
- BorrowerDetailScreen.kt (3 instances)
- SettingsScreen.kt (2 instances)
- BalanceLedgerScreen.kt (1 instance)
- PaymentCalendarScreen.kt (4 instances)
- RecordPaymentScreen.kt (2 instances)
- DocumentViewerScreen.kt (1 minor cleanup)

**Total Hardcoded Lt* Color References:** 23 instances across 8 files

**User-Visible Consequence:** In dark mode, multiple screens will display incorrect colors:
- Auth screens (login/splash) won't show vibrant pink accents
- Borrower management screens will have wrong dialog backgrounds and text field colors
- Calendar and ledger won't properly theme navigation and interactive elements
- Payment recording won't show themed balance indicators

The implementation is **incomplete** and does not meet the requirement of "all screens using MaterialTheme.colorScheme."

---

## Recommendation

**CHANGES REQUESTED** - The implementation requires significant additional work to meet the approved requirements. All hardcoded Lt* color references (except semantic status colors) must be replaced with MaterialTheme.colorScheme equivalents before approval.
