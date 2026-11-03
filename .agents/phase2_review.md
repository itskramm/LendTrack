# Phase 2: Dark Mode + Immersive Display + Firebase Sync Fix

Phase 2 ships theme infrastructure, immersive mode, and enhanced Firebase diagnostics. The implementation compiles successfully and follows Android patterns. The theme toggle UI is functional, immersive mode properly controls system bars, and Firebase logging is comprehensive. Dark mode is implemented at the theme layer but won't work in practice because nearly every screen uses hardcoded color constants (LtBackground, LtBlush, LtText) instead of MaterialTheme.colorScheme references. Switching to dark theme will change the top bar and settings screen while leaving all other screens in light colors, creating a broken visual experience. This gap blocks dark mode from being usable and must be fixed before shipping.

**Watch for:** Dark mode broken on all screens except settings (**confirmed** — 13 screens use .background(LtBackground) instead of MaterialTheme.colorScheme.background), immersive mode resets on every configuration change (**likely** — applyImmersiveMode runs on collect which doesn't preserve state through rotation), theme preference collection in MainActivity creates unnecessary recomposition on every state change (**confirmed** — collectAsStateWithLifecycle triggers recomposition even when theme hasn't changed).

**Verdict**: NEEDS_CHANGES

---

## High-level view

The ThemePreferences class stores theme mode (LIGHT/DARK/SYSTEM) and immersive mode preference in DataStore. MainActivity observes theme mode and applies it to LendTrackTheme, observes immersive mode and toggles system bar visibility. The Theme.kt file defines a complete DarkColorScheme with appropriate dark backgrounds and light text colors. Settings adds theme selection buttons and an immersive mode toggle. Firebase sync enhancements add network/auth checks at entry points, a testFirebaseConnection diagnostic method, and better retry logic with attempt counting up to 5 failures before marking operations REQUIRES_ATTENTION.

The critical problem: screens don't reference the MaterialTheme color scheme. They use Lt* color constants directly (.background(LtBackground), color = LtText, etc.). These constants are defined once in Color.kt and never change regardless of theme. When a user switches to dark mode, MaterialTheme.colorScheme updates to DarkColorScheme, but every screen continues rendering with the light-themed LtBackground (#FFF5F0) and LtText (#4B3035) because those constants are used directly. Dark mode only works where MaterialTheme.colorScheme colors are used (top bar greeting text, some badges) — everywhere else stays light.

Immersive mode implementation covers both modern (WindowInsetsController) and legacy (systemUiVisibility flags) Android APIs. The toggle works and persists. The collection in MainActivity launches a coroutine that re-applies immersive mode on every emission, which will reapply correctly after rotation but uses more resources than a state-preserving approach.

Firebase sync enhancements add early-exit guards (offline check, auth check) to onLocalWrite, a test connection method that writes/reads/deletes a test node, and processSyncOperation tracks attempt count and moves operations to REQUIRES_ATTENTION after 5 failures. These are solid improvements over Phase 1.

---

<details>
<summary>Issues (5)</summary>

1. **Dark mode broken on all screens** — Every feature screen uses hardcoded Lt* colors instead of MaterialTheme.colorScheme. Switching to dark theme leaves 13 screens with light backgrounds and dark text on dark text in some areas. Replace .background(LtBackground) with .background(MaterialTheme.colorScheme.background), color = LtText with color = MaterialTheme.colorScheme.onBackground, LtSurface with surface, LtBlush with surfaceVariant, etc. Status badge colors (LtSage, LtAmber, LtRed) and brand colors (LtRose, LtBurgundy) can remain direct since they're semantic and should stay consistent across themes, but backgrounds and text must reference the theme.

2. **Immersive mode reapplied on every preference emission** — MainActivity launches a coroutine that collects immersiveMode flow and calls applyImmersiveMode on every emission. This works but will re-trigger the apply logic on configuration changes (rotation) even when the preference value hasn't changed. Use collectAsStateWithLifecycle and invoke applyImmersiveMode only when the Boolean value changes (LaunchedEffect(immersiveModeState) { ... }).

3. **Theme preference causes unnecessary MainActivity recomposition** — collectAsStateWithLifecycle on themeMode triggers recomposition of MainActivity's setContent block whenever theme mode changes, but also potentially on other state emissions. The darkTheme boolean is derived inside setContent, which is correct, but the pattern could be more efficient by hoisting the theme state to a remember block or using derivedStateOf to avoid recalculating when theme hasn't changed. This is a minor performance issue, not a functional blocker.

4. **ThemeModeButton border color doesn't adapt to dark theme** — The unselected border uses MaterialTheme.colorScheme.outline, which adapts correctly, but the selected border uses LtRose directly. In dark mode, LtRose at full opacity may have insufficient contrast against dark surfaces. DarkColorScheme already defines a lighter primary (LtRose.copy(alpha = 0.85f)), but the button border bypasses this and uses the original LtRose. Use MaterialTheme.colorScheme.primary for the selected border color so it adapts.

5. **Firebase testFirebaseConnection not exposed to UI** — The diagnostic method exists in FirebaseSyncManager but isn't called from anywhere. SettingsScreen shows a "Force Sync Now" button but doesn't offer a way to run the connection test. Either add a "Test Connection" button that displays the result string in a dialog, or remove the method if it's intended only for developer use in code.

</details>

---

<details>
<summary>Details</summary>

## ThemePreferences and storage

ThemePreferences wraps a DataStore<Preferences> and exposes two flows:

```kotlin
val themeMode: Flow<ThemeMode> = dataStore.data
    .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
    .map { prefs ->
        when (prefs[KEY_THEME_MODE]) {
            "DARK" -> ThemeMode.DARK
            "LIGHT" -> ThemeMode.LIGHT
            else -> ThemeMode.SYSTEM
        }
    }
```

The default is SYSTEM when no preference is set. immersiveMode defaults to false. Both are @Singleton and injected via Hilt.

## DarkColorScheme definition

Theme.kt adds a complete darkColorScheme:

```kotlin
background         = Color(0xFF1A1A1A),
onBackground       = Color(0xFFE5E5E5),
surface            = Color(0xFF2A2A2A),
onSurface          = Color(0xFFE5E5E5),
surfaceVariant     = Color(0xFF3A3A3A),
onSurfaceVariant   = Color(0xFFD0D0D0),
```

Primary uses LtRose at 85% opacity, primaryContainer is darker burgundy (#5A2833), error is lighter red (#FFB4AB) for dark backgrounds. LendTrackTheme composable now accepts a darkTheme parameter and selects colorScheme accordingly.

## MainActivity theme integration

MainActivity observes themeMode:

```kotlin
val themeMode by themePreferences.themeMode.collectAsStateWithLifecycle(
    initialValue = ThemePreferences.ThemeMode.SYSTEM
)

val darkTheme = when (themeMode) {
    ThemePreferences.ThemeMode.LIGHT -> false
    ThemePreferences.ThemeMode.DARK -> true
    ThemePreferences.ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

LendTrackTheme(darkTheme = darkTheme) { ... }
```

The initialValue avoids a flash of the wrong theme on first composition before the flow emits.

## Immersive mode implementation

MainActivity.onCreate launches a coroutine:

```kotlin
lifecycleScope.launch {
    themePreferences.immersiveMode.collect { enabled ->
        applyImmersiveMode(enabled)
    }
}
```

applyImmersiveMode branches on enabled. For enabled=true, modern path (R+):

```kotlin
window.insetsController?.apply {
    hide(WindowInsets.Type.statusBars())
    hide(WindowInsets.Type.navigationBars())
    systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}
```

Legacy path:

```kotlin
window.decorView.systemUiVisibility = (
    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    or View.SYSTEM_UI_FLAG_FULLSCREEN
    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
)
```

For enabled=false, modern path calls show() on both inset types, legacy path sets systemUiVisibility to VISIBLE. IMMERSIVE_STICKY ensures bars reappear on swipe and then hide again. BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE is the R+ equivalent.

The collect runs continuously and will re-invoke applyImmersiveMode on every emission. On device rotation, the activity is recreated, the collect restarts, and applyImmersiveMode is called again — correct behavior (system bars need to be hidden again after recreation), but the pattern could be cleaner using collectAsStateWithLifecycle and LaunchedEffect.

## Settings theme UI

SettingsScreen adds an "Appearance" panel at the top. Three ThemeModeButton instances in a Row with weight(1f) each call viewModel.setThemeMode with the corresponding enum value. The selected state is determined by comparing themeMode (StateFlow from ViewModel) with the button's mode.

ThemeModeButton is a private composable:

```kotlin
val backgroundColor = if (selected) LtRose else Color.Transparent
val textColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface

OutlinedButton(
    onClick = onClick,
    colors = ButtonDefaults.outlinedButtonColors(
        containerColor = backgroundColor,
        contentColor = textColor
    ),
    border = BorderStroke(1.dp, if (selected) LtRose else MaterialTheme.colorScheme.outline)
)
```

Unselected buttons use MaterialTheme.colorScheme.onSurface for text and .outline for border — these adapt to dark mode. Selected button uses LtRose for background and border, Color.White for text. The background is fine (brand color should stay consistent), but the border should use MaterialTheme.colorScheme.primary so it picks up the 85%-opacity rose in dark mode.

Below the theme buttons, a Row contains "Full Screen Mode" label and description with a Switch bound to immersiveMode from ViewModel.

## SettingsViewModel theme methods

SettingsViewModel injects ThemePreferences and exposes two StateFlows:

```kotlin
val themeMode: StateFlow<ThemePreferences.ThemeMode> = themePreferences.themeMode
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemePreferences.ThemeMode.SYSTEM
    )

val immersiveMode: StateFlow<Boolean> = themePreferences.immersiveMode
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )
```

setThemeMode and setImmersiveMode launch coroutines in viewModelScope and call the corresponding ThemePreferences methods.

## Firebase sync enhancements

FirebaseSyncManager.onLocalWrite now checks connectivity and authentication before attempting sync:

```kotlin
if (!networkMonitor.isCurrentlyOnline()) {
    Log.d(TAG, "onLocalWrite: Device is offline, sync queued")
    return
}

if (!isAuthenticated()) {
    Log.e(TAG, "onLocalWrite: User not authenticated, cannot sync")
    return
}

scope.launch { drainOutbox() }
```

testFirebaseConnection writes a test node, reads it back, deletes it, and returns a status string. It's defined as a suspend function but not called anywhere. SettingsScreen could add a "Test Connection" button below "Force Sync Now" that shows an AlertDialog with the result.

processSyncOperation tracks attempt count:

```kotlin
if (success) {
    syncDao.markAttempt(id, status = SyncStatus.SYNCED, now, ...)
} else {
    val newAttemptCount = op.attemptCount + 1
    val newStatus = if (newAttemptCount >= 5) {
        SyncStatus.REQUIRES_ATTENTION
    } else {
        SyncStatus.FAILED
    }
    syncDao.markAttempt(id, status = newStatus, now, error = "...")
}
```

FAILED operations will be picked up by the next drainOutbox call. After 5 attempts, the operation moves to REQUIRES_ATTENTION and stops retrying. The retry count is tracked per operation, not globally, so a single broken operation doesn't block others.

## Hardcoded color usage blocking dark mode

DashboardScreen.kt:

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .background(LtBackground)
        ...
)
```

LtBackground is Color(0xFFFFF5F0), defined in Color.kt as a top-level val. It never changes. When the user switches to dark theme, MaterialTheme.colorScheme.background becomes Color(0xFF1A1A1A), but this screen still renders with 0xFFFFF5F0 because it references LtBackground directly.

The same pattern appears in 12 other screens: BorrowersScreen, BorrowerDetailScreen, AddLoanScreen, PaymentsScreen, RecordPaymentScreen, ReportsScreen, BalanceLedgerScreen, PaymentCalendarScreen, SettingsScreen, LoginScreen, SplashScreen, EditLoanDialog.

Text color usage follows the same pattern. LtText is Color(0xFF4B3035), a dark brown. In dark mode, MaterialTheme.colorScheme.onBackground is Color(0xFFE5E5E5) (light gray), but any Text using `color = LtText` will render dark brown. The result is the entire app stays in light colors regardless of theme setting.

The fix: replace every hardcoded color with the corresponding MaterialTheme.colorScheme reference:

- LtBackground → MaterialTheme.colorScheme.background
- LtSurface → MaterialTheme.colorScheme.surface
- LtBlush → MaterialTheme.colorScheme.surfaceVariant (or primaryContainer for accent use)
- LtText → MaterialTheme.colorScheme.onBackground or onSurface depending on the parent
- LtMuted → MaterialTheme.colorScheme.onSurfaceVariant

Brand colors (LtRose, LtBurgundy) and status colors (LtSage, LtAmber, LtRed) should stay as direct references because they carry semantic meaning (primary action, paid, overdue) that should be consistent across themes. DarkColorScheme already adjusts primary to LtRose.copy(alpha = 0.85f) to reduce intensity in dark mode.

Some usages like LtBlush.copy(alpha = 0.5f) for info panel backgrounds would map to MaterialTheme.colorScheme.surfaceVariant or primaryContainer.copy(alpha = 0.5f) depending on the intent. surfaceVariant is defined in both light and dark schemes and will adapt automatically.

This is not a small fix. Thirteen files use LtBackground, many use LtText, LtMuted, LtBlush. Every instance must be reviewed to determine the correct MaterialTheme mapping.

</details>

---

## File map

<details>
<summary>6 files changed</summary>

- `app/src/main/java/com/lendtrack/app/core/preferences/ThemePreferences.kt` — New file, DataStore-based theme and immersive mode preference storage
- `app/src/main/java/com/lendtrack/app/core/ui/theme/Theme.kt` — Added DarkColorScheme, updated LendTrackTheme to accept darkTheme parameter
- `app/src/main/java/com/lendtrack/app/MainActivity.kt` — Injected ThemePreferences, observe theme mode and immersive mode, apply theme and system bar visibility
- `app/src/main/java/com/lendtrack/app/feature/settings/SettingsScreen.kt` — Added Appearance panel with theme selector and immersive mode toggle, ThemeModeButton composable
- `app/src/main/java/com/lendtrack/app/feature/settings/SettingsViewModel.kt` — Exposed themeMode and immersiveMode StateFlows, added setThemeMode and setImmersiveMode methods
- `app/src/main/java/com/lendtrack/app/core/sync/FirebaseSyncManager.kt` — Added network/auth checks to onLocalWrite, testFirebaseConnection diagnostic method, improved retry mechanism with attempt counting

</details>
