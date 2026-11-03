# Phase 2: Core Features Workflow Brief

## Task: Dark Mode, Immersive Display & Firebase Sync Fix

### Goal
Implement dark mode theme toggle, immersive full-screen display, and fix Firebase sync functionality based on Phase 1 diagnostics.

### Context
- **Prerequisites**: Phase 1 must be complete (sync diagnostics in place)
- **Estimated Time**: 8-12 hours
- **Complexity**: Medium-High
- **Dependencies**: None (can run independently)

### Feature Breakdown

#### 1. Dark Mode Implementation (4-6 hours)

**What to Build:**
- Create dark color scheme
- Add theme preference storage (DataStore)
- Add theme toggle in Settings (Light/Dark/System)
- Update all components to use MaterialTheme colors
- Ensure all screens render correctly in dark mode

**Files to Create:**
- `app/src/main/java/com/lendtrack/app/core/preferences/ThemePreferences.kt`

**Files to Modify:**
- `app/src/main/java/com/lendtrack/app/core/ui/theme/Theme.kt`
- `app/src/main/java/com/lendtrack/app/feature/settings/SettingsScreen.kt`
- `app/src/main/java/com/lendtrack/app/feature/settings/SettingsViewModel.kt`
- `app/src/main/java/com/lendtrack/app/MainActivity.kt`
- All component files using hardcoded colors

**Technical Details:**
```kotlin
// Dark color scheme
background = Color(0xFF1A1A1A)
surface = Color(0xFF2A2A2A)
onBackground = Color(0xFFE5E5E5)
onSurface = Color(0xFFE5E5E5)
```

**Verification:**
- [ ] Can toggle between Light/Dark/System
- [ ] Preference persists after restart
- [ ] All screens readable in dark mode
- [ ] No color contrast issues
- [ ] Smooth theme transition

#### 2. Immersive Display (2 hours)

**What to Build:**
- Enable edge-to-edge display
- Hide status bar (clock, battery, notifications)
- Hide navigation bar (back, home buttons)
- Add toggle in Settings
- Store preference

**Files to Modify:**
- `app/src/main/java/com/lendtrack/app/MainActivity.kt`
- `app/src/main/java/com/lendtrack/app/core/preferences/ThemePreferences.kt`
- `app/src/main/java/com/lendtrack/app/feature/settings/SettingsScreen.kt`

**Technical Details:**
```kotlin
// API 30+
window.insetsController?.hide(WindowInsets.Type.statusBars())
window.insetsController?.hide(WindowInsets.Type.navigationBars())

// Legacy
window.decorView.systemUiVisibility = 
    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
    View.SYSTEM_UI_FLAG_FULLSCREEN or
    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
```

**Verification:**
- [ ] Status bar hidden when enabled
- [ ] Navigation bar hidden when enabled
- [ ] Swipe from edge shows bars temporarily
- [ ] Toggle persists across restarts
- [ ] Content doesn't overlap system UI

#### 3. Firebase Sync Fix (2-4 hours)

**What to Do:**
- Review Phase 1 diagnostic logs
- Identify root cause of sync failure
- Implement fix based on findings
- Add retry mechanism
- Improve error handling
- Add sync status indicators throughout app

**Common Issues to Check:**
- Firebase database URL incorrect
- Authentication not working
- Network connectivity problems
- Firebase rules blocking writes
- Data serialization errors
- Path construction errors

**Files to Modify:**
- `app/src/main/java/com/lendtrack/app/core/sync/FirebaseSyncManager.kt`
- `app/google-services.json` (if needed)
- Firebase Console rules (if needed)

**Verification:**
- [ ] Data appears in Firebase console
- [ ] Sync operations complete successfully
- [ ] Retry works after failure
- [ ] Proper error messages shown
- [ ] Sync indicators accurate

### Build & Test Requirements
- Must build successfully: `./gradlew assembleDebug`
- All screens must render in both themes
- Firebase sync must work end-to-end
- No crashes or ANRs
- Smooth performance

### Questions to Answer During Implementation
1. Should dark mode include "Auto" (system) option? **→ Yes, include all three**
2. Should immersive mode be always-on or toggleable? **→ User toggleable in settings**
3. What is the root cause of Firebase sync failure? **→ Determine from diagnostics**

### Success Criteria
- [ ] Dark mode works across all screens
- [ ] Theme toggle in settings functional
- [ ] Immersive mode hides system bars
- [ ] Firebase sync verified working
- [ ] Data syncs to cloud successfully
- [ ] Build passes
- [ ] All features tested manually
