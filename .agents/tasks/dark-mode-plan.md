# Implementation Plan: Dark Mode & Tablet UI/UX Optimizations

## Overview
Implement dark theme with near-black background (#0D0D0D) and hot-pink (#E91E63) accents, user-selectable theme preference (Light/Dark/System Auto) stored in DataStore, responsive layouts optimized for 10"+ tablets, larger typography scale for tablets, and WCAG AA accessibility compliance.

## Design Decisions

### Theme Mode Storage
**Decision:** Create a separate `ThemePreferences` class following the existing `SyncPreferences` pattern, with its own DataStore instance named `"lendtrack_theme_prefs"`. This avoids mixing theme and sync concerns while maintaining consistency with the existing architecture.

**Rationale:** The codebase already uses `SyncPreferences` in `app/src/main/java/com/lendtrack/app/core/sync/SyncPreferences.kt` with a dedicated DataStore. A parallel `ThemePreferences` class keeps preferences logically separated and follows the established pattern.

### Theme Mode Enum Placement
**Decision:** Add `ThemeMode` enum to `DomainModels.kt` alongside existing enums like `InstallmentStatus`, `SyncStatus`, etc.

**Rationale:** Domain models file already contains UI-relevant enums (`SyncStatus`, `InstallmentStatus`) that cross architectural boundaries. `ThemeMode` fits this pattern.

### Dark Color Palette
**Decision:** Use #0D0D0D (near-black) as the dark background, #E91E63 (hot pink / Material Pink 500) as the primary accent, and derive dark equivalents of the light palette that maintain WCAG AA contrast ratios (4.5:1 for normal text, 3:1 for large text, 3:1 for UI components).

**Rationale:** The user provided a screenshot showing this specific color scheme. Near-black backgrounds reduce eye strain compared to pure black and avoid OLED burn-in concerns. Hot pink maintains the warm, friendly brand identity while providing high contrast.

### Responsive Layout Strategy
**Decision:** Create a `ResponsiveLayout.kt` utility with `ScreenSize` enum (COMPACT, MEDIUM, EXPANDED) based on Material 3 window size classes, and a `rememberResponsiveSpacing()` composable that returns scaled spacing/typography values.

**Rationale:** Material 3 window size classes (< 600dp = compact, 600-840dp = medium, >= 840dp = expanded) provide standardized breakpoints. Current code uses manual `LocalConfiguration.current.screenWidthDp >= 900` checks in `DashboardScreen.kt` and `LendTrackApp.kt` — centralizing this logic improves consistency.

### Accessibility Compliance
**Decision:** Implement WCAG AA contrast checking in the plan, ensuring all dark mode colors meet 4.5:1 for body text, 3:1 for large text (18sp+), and 3:1 for interactive UI components. Document that full validation requires manual testing with assistive technologies.

**Rationale:** Automated contrast checks catch most issues, but WCAG compliance requires human verification of focus indicators, screen reader announcements, and keyboard navigation — which are beyond the scope of this implementation.

---

## Implementation Steps

- [ ] 1. **Add `ThemeMode` enum to domain models**
      Define `enum class ThemeMode { LIGHT, DARK, SYSTEM }` in `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/domain/model/DomainModels.kt` after the existing enums.
      
      **Files:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/domain/model/DomainModels.kt`
      
      **Verify:** Build the project with `./gradlew assembleDebug` and confirm no compilation errors.

- [ ] 2. **Create `ThemePreferences` DataStore class**
      Create `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/ui/theme/ThemePreferences.kt` following the exact pattern of `SyncPreferences.kt`. Use `preferencesDataStore(name = "lendtrack_theme_prefs")` with a single `stringPreferencesKey("theme_mode")` key. Provide `themeMode: Flow<ThemeMode>` (defaults to `ThemeMode.SYSTEM`) and `suspend fun setThemeMode(mode: ThemeMode)`. Use `@Singleton` and inject `@ApplicationContext`.
      
      **Files:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/ui/theme/ThemePreferences.kt` (new file)
      
      **Verify:** Build the project with `./gradlew assembleDebug` and confirm no compilation errors.

- [ ] 3. **Provide `ThemePreferences` in Hilt module**
      Add `@Provides @Singleton fun provideThemePreferences(@ApplicationContext context: Context): ThemePreferences = ThemePreferences(context)` to `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/sync/SyncModule.kt`. (Note: Keep it in `SyncModule` to avoid creating a new module and potential duplicate binding issues. The module is already installed in `SingletonComponent`.)
      
      **Files:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/sync/SyncModule.kt`
      
      **Verify:** Build the project with `./gradlew assembleDebug` and confirm Hilt can resolve `ThemePreferences`.

- [ ] 4. **Add dark color palette to `Color.kt`**
      Add dark equivalents after the existing light colors in `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/ui/theme/Color.kt`:
      - `LtDarkBackground = Color(0xFF0D0D0D)` — near-black background
      - `LtDarkSurface = Color(0xFF1A1A1A)` — elevated surface (slightly lighter)
      - `LtDarkSurfaceVariant = Color(0xFF2A2A2A)` — cards, panels
      - `LtHotPink = Color(0xFFE91E63)` — primary accent (Material Pink 500)
      - `LtDarkText = Color(0xFFE8E8E8)` — primary text (high contrast on dark)
      - `LtDarkMuted = Color(0xFFB0B0B0)` — secondary text
      - Dark badge colors: `StatusPaidDark = Color(0xFF81C784)` (lighter sage), `StatusDueSoonDark = Color(0xFFFFB74D)` (lighter amber), `StatusOverdueDark = Color(0xFFE57373)` (lighter red), `StatusUpcomingDark = Color(0xFFCE93D8)` (lighter dusty), `StatusPendingDark = Color(0xFFB39DDB)` (lighter purple)
      
      **Files:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/ui/theme/Color.kt`
      
      **Verify:** Build the project with `./gradlew assembleDebug` and confirm no compilation errors.

- [ ] 5. **Add dark color scheme and update `LendTrackTheme` composable in `Theme.kt`**
      In `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/ui/theme/Theme.kt`:
      - Create `LendTrackDarkColorScheme = darkColorScheme(...)` mapping the new dark colors to Material 3 roles (primary = LtHotPink, background = LtDarkBackground, surface = LtDarkSurface, onSurface = LtDarkText, etc.).
      - Change `LendTrackTheme` signature to `@Composable fun LendTrackTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`.
      - Inside the composable, select `val colorScheme = if (darkTheme) LendTrackDarkColorScheme else LendTrackColorScheme`.
      - Pass `colorScheme` to `MaterialTheme(colorScheme = colorScheme, ...)`.
      
      **Files:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/ui/theme/Theme.kt`
      
      **Verify:** Build the project with `./gradlew assembleDebug` and run the app on an emulator/device. Force dark theme by hardcoding `LendTrackTheme(darkTheme = true)` temporarily in `MainActivity.kt` and confirm the dark palette appears.

- [ ] 6. **Update `MainActivity.kt` to observe theme preference and pass to `LendTrackTheme`**
      In `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/MainActivity.kt`:
      - Inject `ThemePreferences` using `@Inject lateinit var themePreferences: ThemePreferences` (requires making `MainActivity` field-injectable or moving to a ViewModel).
      - **Decision:** Create a minimal `MainActivityViewModel` (Hilt `@HiltViewModel`) that exposes `val themeMode: StateFlow<ThemeMode>` by collecting from `themePreferences.themeMode`. This avoids field injection complexity.
      - In `onCreate`, obtain the ViewModel: `val mainViewModel: MainActivityViewModel = viewModels()`.
      - Collect `themeMode` as state: `val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()`.
      - Compute `val darkTheme = when (themeMode) { ThemeMode.LIGHT -> false; ThemeMode.DARK -> true; ThemeMode.SYSTEM -> isSystemInDarkTheme() }`.
      - Pass `darkTheme` to `LendTrackTheme(darkTheme = darkTheme) { ... }`.
      
      **Files:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/MainActivity.kt`, `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/MainActivityViewModel.kt` (new file)
      
      **Verify:** Run the app and confirm it respects the system dark mode setting (test by toggling system dark mode on the device/emulator). Check logcat for any crashes or missing dependencies.

- [ ] 7. **Add theme switcher UI to `SettingsViewModel` and `SettingsScreen`**
      In `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/settings/SettingsViewModel.kt`:
      - Inject `ThemePreferences` via constructor.
      - Expose `val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode.stateIn(...)`.
      - Add `fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { themePreferences.setThemeMode(mode) } }`.
      
      In `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/settings/SettingsScreen.kt`:
      - After the "Synchronization" panel, add a new `LtPanel(title = "Appearance")` section.
      - Collect `val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()`.
      - Display three radio buttons or segmented buttons (Light / Dark / System Auto) using `LtChip` or Material 3 `SegmentedButton`.
      - Call `viewModel.setThemeMode(...)` on selection.
      
      **Files:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/settings/SettingsViewModel.kt`, `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/settings/SettingsScreen.kt`
      
      **Verify:** Run the app, navigate to Settings, and confirm the theme switcher appears. Toggle between Light/Dark/System and verify the theme changes immediately without restarting the app. Test that the selection persists after closing and reopening the app.

- [ ] 8. **Create `ResponsiveLayout.kt` utility with `ScreenSize` and responsive spacing**
      Create `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/ui/theme/ResponsiveLayout.kt`:
      - Define `enum class ScreenSize { COMPACT, MEDIUM, EXPANDED }`.
      - Add `@Composable fun rememberScreenSize(): ScreenSize` that reads `LocalConfiguration.current.screenWidthDp` and returns `COMPACT` (< 600dp), `MEDIUM` (600-839dp), or `EXPANDED` (>= 840dp).
      - Add `@Composable fun rememberResponsiveSpacing(screenSize: ScreenSize = rememberScreenSize()): Spacing` that returns scaled spacing (e.g., COMPACT uses default `Spacing()`, MEDIUM scales by 1.1x, EXPANDED scales by 1.25x).
      - Add `@Composable fun rememberResponsiveTypography(screenSize: ScreenSize = rememberScreenSize()): Typography` that returns scaled typography (e.g., body text 14sp → 15sp → 16sp, headlines scale similarly).
      
      **Files:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/ui/theme/ResponsiveLayout.kt` (new file)
      
      **Verify:** Build the project with `./gradlew assembleDebug`. In `DashboardScreen.kt`, add `val screenSize = rememberScreenSize()` and log it or display it temporarily to confirm it returns `EXPANDED` on 10"+ tablets and `COMPACT` on phones.

- [ ] 9. **Update `DashboardScreen.kt` to use responsive layout utility**
      In `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/dashboard/DashboardScreen.kt`:
      - Replace `val isTabletLandscape = LocalConfiguration.current.screenWidthDp >= 900` with `val screenSize = rememberScreenSize()` and `val isTabletLandscape = screenSize == ScreenSize.EXPANDED`.
      - In `StatCardsGrid`, change the 2×2 grid for portrait to a single column on `COMPACT`, 2×2 on `MEDIUM`, and 1×4 row on `EXPANDED`.
      - Use `rememberResponsiveSpacing()` to scale panel padding and card gaps (e.g., `spacedBy(spacing.md)` where `spacing` comes from `rememberResponsiveSpacing()`).
      
      **Files:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/dashboard/DashboardScreen.kt`
      
      **Verify:** Run the app on emulators of different sizes (phone ~400dp, 7" tablet ~600dp, 10" tablet ~840dp+). Confirm the dashboard layout adapts correctly: single column on phone, 2×2 stats on 7", 1×4 stats row on 10"+. Take screenshots and compare against the light theme version to ensure consistency.

- [ ] 10. **Update `LtStatusBadge` in `LtComponents.kt` to be dark-aware**
       In `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/ui/components/LtComponents.kt`:
       - Change `LtStatusBadge` to read `val colorScheme = MaterialTheme.colorScheme` and use `when (status)` to select colors from `StatusPaidDark` vs `StatusPaid` based on `colorScheme.background == LtDarkBackground`.
       - Alternatively, define semantic color roles in the theme (`val statusPaid = if (darkTheme) StatusPaidDark else StatusPaid`) and reference those.
       - **Decision:** Use dynamic color selection inside the composable for simplicity: `val isPaidColor = if (MaterialTheme.colorScheme.background == LtDarkBackground) StatusPaidDark else StatusPaid`.
       
       **Files:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/ui/components/LtComponents.kt`
       
       **Verify:** Run the app in dark mode and navigate to the dashboard. Confirm status badges (Paid, Due Soon, Overdue, etc.) use the lighter, higher-contrast dark-mode colors and remain readable. Check WCAG contrast ratios using a tool like [WebAIM Contrast Checker](https://webaim.org/resources/contrastchecker/) (simulate by checking badge background vs. white text).

- [ ] 11. **Ensure `LtButton` meets 48dp minimum touch target (WCAG AA)**
       In `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/ui/components/LtComponents.kt`:
       - Verify `LtButton` uses `contentPadding` that results in at least 48dp height for non-small buttons. Current code: `PaddingValues(horizontal = 14.dp, vertical = 8.dp)` — measure actual height (likely ~40dp with text).
       - Add `modifier = modifier.heightIn(min = 48.dp)` to the `Button` composable to enforce minimum touch target.
       - For `small = true` buttons (used in tables), keep the existing smaller padding but apply `modifier.heightIn(min = 44.dp)` (acceptable for dense UI per Material guidelines).
       
       **Files:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/ui/components/LtComponents.kt`
       
       **Verify:** Run the app and use Android Studio's Layout Inspector to measure button heights. Confirm all buttons are >= 48dp tall (or 44dp for small/dense buttons). Test touch targets by tapping buttons at their edges and corners.

- [ ] 12. **Fix dot-texture background in `LendTrackApp.kt` to be dark-aware**
       In `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/ui/LendTrackApp.kt`:
       - The `dotColor = LtDusty.copy(alpha = 0.13f)` hardcoded in `drawBehind` will not work in dark mode (dusty pink dots on near-black are nearly invisible).
       - Change to `val dotColor = if (MaterialTheme.colorScheme.background == LtDarkBackground) LtHotPink.copy(alpha = 0.08f) else LtDusty.copy(alpha = 0.13f)`.
       - This makes dots subtle hot-pink in dark mode, maintaining the decorative texture without overpowering the content.
       
       **Files:** `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/ui/LendTrackApp.kt`
       
       **Verify:** Run the app in dark mode and confirm the dot texture is visible but subtle. Compare to light mode and ensure both look intentional, not accidental.

- [ ] 13. **Update all remaining screens to use responsive spacing and dark-aware colors**
       Audit and update the following screens to replace hardcoded color references (e.g., `LtBackground`, `LtSurface`, `LtText`) with `MaterialTheme.colorScheme` equivalents (e.g., `colorScheme.background`, `colorScheme.surface`, `colorScheme.onSurface`):
       - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/borrowers/BorrowersScreen.kt`
       - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/calendar/CalendarScreen.kt`
       - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/ledger/LedgerScreen.kt`
       - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/payments/PaymentsScreen.kt`
       - `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/feature/reports/ReportsScreen.kt`
       
       For each screen:
       - Replace `.background(LtBackground)` with `.background(MaterialTheme.colorScheme.background)`.
       - Replace hardcoded `LtText` with `MaterialTheme.colorScheme.onSurface`.
       - Replace hardcoded `LtSurface` with `MaterialTheme.colorScheme.surface`.
       - Use `rememberScreenSize()` to adapt layout (e.g., single-column on COMPACT, multi-column on EXPANDED).
       
       **Files:** All screen files listed above
       
       **Verify:** Run the app in dark mode and navigate to each screen (Borrowers, Calendar, Ledger, Payments, Reports). Confirm each screen renders correctly in dark mode with readable text, appropriate contrast, and responsive layout on different screen sizes. Take screenshots and compare against light mode for consistency.

- [ ] 14. **Test WCAG AA contrast ratios and document findings**
       Using a contrast checker tool (e.g., [WebAIM Contrast Checker](https://webaim.org/resources/contrastchecker/) or Android Studio's Accessibility Scanner):
       - **Body text:** Verify `LtDarkText` (#E8E8E8) on `LtDarkBackground` (#0D0D0D) achieves >= 4.5:1 contrast.
       - **Large text (18sp+):** Verify >= 3:1 contrast for headings.
       - **UI components:** Verify `LtHotPink` (#E91E63) on `LtDarkSurface` (#1A1A1A) achieves >= 3:1 contrast for buttons and interactive elements.
       - **Status badges:** Verify each dark badge color (StatusPaidDark, StatusDueSoonDark, StatusOverdueDark) with white text achieves >= 4.5:1 contrast.
       
       Document findings in `/Users/mark/LendTrack/.agents/tasks/dark-mode-accessibility-report.md`:
       - List each color pair tested and its contrast ratio.
       - Note any failing combinations and proposed fixes.
       - Include a disclaimer: "Full WCAG AA compliance requires manual testing with assistive technologies (TalkBack, Switch Access) and expert accessibility review. Automated contrast checks catch most issues but are not exhaustive."
       
       **Files:** `/Users/mark/LendTrack/.agents/tasks/dark-mode-accessibility-report.md` (new file, documentation only)
       
       **Verify:** Review the report and confirm all critical color pairs pass WCAG AA. If any fail, adjust colors in `Color.kt` and re-test until all pass. Run Android Accessibility Scanner on the app in both light and dark modes and address any warnings.

- [ ] 15. **Run full build and test suite to confirm no regressions**
       Execute the project's build and verification commands:
       - `./gradlew assembleDebug` — confirm clean build with no compilation errors.
       - `./gradlew testDebugUnitTest` — confirm all existing unit tests pass (if any exist).
       - `./gradlew connectedDebugAndroidTest` — confirm all instrumented tests pass (if any exist).
       - Manual testing: Launch the app on a 10"+ tablet emulator (e.g., Pixel Tablet or Pixel C), toggle between Light/Dark/System theme modes, test all screens (Dashboard, Borrowers, Calendar, Ledger, Payments, Reports, Settings), and confirm:
         - Theme changes apply immediately without requiring app restart.
         - All text is readable in both themes.
         - Status badges and UI components maintain correct colors.
         - Layouts adapt correctly from compact (phone) to medium (7" tablet) to expanded (10"+ tablet).
         - No visual glitches or layout clipping.
       
       **Files:** No file changes, verification only
       
       **Verify:** All build commands complete successfully. All screens render correctly in both themes on multiple screen sizes. Document any issues found and fix them before marking this step complete.

---

## Key Notes for the Coder

### Existing DataStore Setup
- The project already uses DataStore in `/Users/mark/LendTrack/app/src/main/java/com/lendtrack/app/core/sync/SyncPreferences.kt` with the name `"lendtrack_sync_prefs"`.
- `SyncModule.kt` provides a `provideDataStore` function — **do not create a duplicate binding**. Instead, add `provideThemePreferences` to the same module, which will instantiate its own DataStore via the extension property pattern (`private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "lendtrack_theme_prefs")`).

### Hilt Injection Patterns
- ViewModels use constructor injection with `@HiltViewModel`.
- `SyncPreferences` uses `@Singleton` and `@Inject constructor(@ApplicationContext context: Context)`.
- Follow the same pattern for `ThemePreferences`.

### Typography Scaling for Tablets
- Current typography uses Playfair Display for headings (serif) and Inter for body/UI (sans-serif).
- When creating `rememberResponsiveTypography()`, scale font sizes proportionally (e.g., 10-15% larger on MEDIUM, 20-30% larger on EXPANDED) while preserving the font families.

### Build Commands
- **Assemble debug APK:** `./gradlew assembleDebug`
- **Run unit tests:** `./gradlew testDebugUnitTest` (if tests exist)
- **Run instrumented tests:** `./gradlew connectedDebugAndroidTest` (requires connected device/emulator)
- **Clean build:** `./gradlew clean assembleDebug`

### Verification Evidence
After each step, the coder must provide:
- **Build output:** Confirm `./gradlew assembleDebug` succeeds.
- **Screenshots:** For UI changes, include screenshots in both light and dark modes on compact, medium, and expanded screens.
- **Contrast ratios:** For step 14, document actual measured ratios.
- **Manual test results:** For step 15, provide a checklist confirming each screen and theme mode combination works.

---

## Dependencies
All required dependencies are already in `build.gradle.kts`:
- `androidx.datastore.preferences` — for theme preference storage
- `androidx.compose.material3` — for Material 3 theming and components
- `androidx.compose.ui` — for composables and theming
- `hilt.android` — for dependency injection

No new dependencies are required.

---

## Accessibility Compliance Note
This plan implements WCAG AA contrast requirements through automated checks. Full WCAG AA compliance requires:
- Manual testing with TalkBack (Android screen reader)
- Keyboard/switch navigation testing
- Focus indicator verification
- Content structure and heading hierarchy review
- Expert accessibility audit

These manual checks are beyond the scope of this implementation and should be performed by a qualified accessibility specialist or QA team before production release.
