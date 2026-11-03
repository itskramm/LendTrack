package com.lendtrack.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lendtrack.app.feature.borrowers.BorrowerDetailScreen
import com.lendtrack.app.feature.borrowers.BorrowersScreen
import com.lendtrack.app.feature.calendar.PaymentCalendarScreen
import com.lendtrack.app.feature.dashboard.DashboardScreen
import com.lendtrack.app.feature.ledger.BalanceLedgerScreen
import com.lendtrack.app.feature.loans.AddLoanScreen
import com.lendtrack.app.feature.payments.PaymentsScreen
import com.lendtrack.app.feature.payments.RecordPaymentScreen
import com.lendtrack.app.feature.reports.ReportsScreen
import com.lendtrack.app.feature.settings.SettingsScreen

@Composable
fun LendTrackNavGraph(
    navController: NavHostController,
    onSignOut:     () -> Unit = {},
) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Dashboard.route,
    ) {
        // ── Dashboard ──────────────────────────────────────────────────────
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToBorrowers = { navController.navigate(Screen.Borrowers.route) },
                onNavigateToCalendar  = { navController.navigate(Screen.PaymentCalendar.route) },
                onNavigateToPayments  = { navController.navigate("payments/record") },
                onBorrowerClick       = { id -> navController.navigate("borrowers/$id") },
            )
        }

        // ── Borrowers ──────────────────────────────────────────────────────
        composable(Screen.Borrowers.route) {
            BorrowersScreen(
                onBorrowerClick = { id -> navController.navigate("borrowers/$id") },
                onAddLoan       = { borrowerId ->
                    navController.navigate("loans/add?borrowerId=$borrowerId")
                },
            )
        }

        composable(
            route     = "borrowers/{borrowerId}",
            arguments = listOf(navArgument("borrowerId") { type = NavType.StringType }),
        ) {
            BorrowerDetailScreen(
                onNavigateBack  = { navController.popBackStack() },
                onRecordPayment = { loanId ->
                    navController.navigate("payments/record?loanId=$loanId")
                },
                onAddLoan = { borrowerId ->
                    navController.navigate("loans/add?borrowerId=$borrowerId")
                },
            )
        }

        // ── Add Loan ───────────────────────────────────────────────────────
        composable(
            route     = "loans/add?borrowerId={borrowerId}",
            arguments = listOf(
                navArgument("borrowerId") {
                    type         = NavType.StringType
                    nullable     = true
                    defaultValue = null
                },
            ),
        ) {
            AddLoanScreen(
                onNavigateBack = { navController.popBackStack() },
                onLoanCreated  = { navController.popBackStack() },
            )
        }

        // ── Payment Calendar ───────────────────────────────────────────────
        composable(Screen.PaymentCalendar.route) {
            PaymentCalendarScreen(
                onRecordPayment = { loanId ->
                    navController.navigate("payments/record?loanId=$loanId")
                },
                onBorrowerClick = { borrowerId ->
                    navController.navigate("borrowers/$borrowerId")
                },
            )
        }

        // ── Balance Ledger ─────────────────────────────────────────────────
        composable(Screen.BalanceLedger.route) {
            BalanceLedgerScreen()
        }

        // ── Payments list ──────────────────────────────────────────────────
        composable(Screen.Payments.route) {
            PaymentsScreen(
                onRecordPayment = { navController.navigate("payments/record") },
            )
        }

        // ── Record Payment ─────────────────────────────────────────────────
        composable(
            route     = "payments/record?loanId={loanId}",
            arguments = listOf(
                navArgument("loanId") {
                    type         = NavType.StringType
                    nullable     = true
                    defaultValue = null
                },
            ),
        ) {
            RecordPaymentScreen(
                onNavigateBack    = { navController.popBackStack() },
                onPaymentRecorded = { navController.popBackStack() },
            )
        }

        // ── Reports ────────────────────────────────────────────────────────
        composable(Screen.Reports.route) {
            ReportsScreen()
        }

        // ── Settings ───────────────────────────────────────────────────────
        composable(Screen.Settings.route) {
            SettingsScreen(onSignOut = onSignOut)
        }
    }
}
