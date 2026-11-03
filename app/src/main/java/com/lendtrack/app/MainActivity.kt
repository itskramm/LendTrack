package com.lendtrack.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lendtrack.app.core.preferences.ThemePreferences
import com.lendtrack.app.core.ui.theme.LendTrackTheme
import com.lendtrack.app.feature.auth.AuthUiState
import com.lendtrack.app.feature.auth.AuthViewModel
import com.lendtrack.app.feature.auth.LoginScreen
import com.lendtrack.app.feature.auth.SplashScreen
import com.lendtrack.app.ui.LendTrackApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themePreferences: ThemePreferences

    // G-10: Request POST_NOTIFICATIONS at runtime on Android 13+
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission granted or denied — notifications will work if granted,
        // silently skipped if denied. No further action needed here.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on first launch (Android 13+ / API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        enableEdgeToEdge()
        setContent {
            val themeMode by themePreferences.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemePreferences.ThemeMode.SYSTEM
            )

            val darkTheme = when (themeMode) {
                ThemePreferences.ThemeMode.LIGHT -> false
                ThemePreferences.ThemeMode.DARK -> true
                ThemePreferences.ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            val immersiveModeEnabled by themePreferences.immersiveMode.collectAsStateWithLifecycle(
                initialValue = false
            )

            // Apply immersive mode when preference changes
            LaunchedEffect(immersiveModeEnabled) {
                applyImmersiveMode(immersiveModeEnabled)
            }

            LendTrackTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val authViewModel: AuthViewModel = hiltViewModel()
                    val authState by authViewModel.state.collectAsStateWithLifecycle()

                    when (authState) {
                        is AuthUiState.Loading -> SplashScreen()

                        is AuthUiState.Authenticated -> LendTrackApp(
                            onSignOut = { authViewModel.signOut() }
                        )

                        is AuthUiState.Unauthenticated,
                        is AuthUiState.Error -> LoginScreen(
                            uiState      = authState,
                            onSignIn     = authViewModel::signIn,
                            onClearError = authViewModel::clearError,
                        )
                    }
                }
            }
        }
    }

    private fun applyImmersiveMode(enabled: Boolean) {
        if (enabled) {
            // Enable immersive mode
            WindowCompat.setDecorFitsSystemWindows(window, false)

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
        } else {
            // Disable immersive mode
            WindowCompat.setDecorFitsSystemWindows(window, true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.apply {
                    show(WindowInsets.Type.statusBars())
                    show(WindowInsets.Type.navigationBars())
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }
}
