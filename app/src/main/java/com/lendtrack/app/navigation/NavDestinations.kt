package com.lendtrack.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

// ─────────────────────────────────────────────────────────────────────────────
// Navigation destinations
// ─────────────────────────────────────────────────────────────────────────────

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    // Primary nav rail items
    data object Dashboard       : Screen("dashboard",       "Dashboard",        Icons.Filled.Home)
    data object Borrowers       : Screen("borrowers",       "Borrowers",        Icons.Filled.People)
    data object PaymentCalendar : Screen("calendar",        "Payment Calendar", Icons.Filled.CalendarMonth)
    data object BalanceLedger   : Screen("ledger",          "Balance Ledger",   Icons.Filled.Bookmarks)
    data object Payments        : Screen("payments",        "Payments",         Icons.Filled.Payments)
    data object Reports         : Screen("reports",         "Reports",          Icons.Filled.BarChart)
    data object Settings        : Screen("settings",        "Settings",         Icons.Filled.Settings)
}

// Primary navigation items shown in the sidebar
val primaryNavItems = listOf(
    Screen.Dashboard,
    Screen.Borrowers,
    Screen.PaymentCalendar,
    Screen.BalanceLedger,
    Screen.Payments,
    Screen.Reports,
    Screen.Settings,
)
