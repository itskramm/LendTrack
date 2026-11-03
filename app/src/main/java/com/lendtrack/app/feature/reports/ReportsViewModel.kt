package com.lendtrack.app.feature.reports

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lendtrack.app.data.repository.BorrowerRepository
import com.lendtrack.app.data.repository.LoanRepository
import com.lendtrack.app.data.repository.PaymentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ReportSummary(
    val totalPrincipal:     Double = 0.0,
    val totalOutstanding:   Double = 0.0,
    val totalOverdue:       Double = 0.0,
    val overdueCount:       Int    = 0,
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loanRepo:     LoanRepository,
    private val paymentRepo:  PaymentRepository,
    private val borrowerRepo: BorrowerRepository,
) : ViewModel() {

    val summary: StateFlow<ReportSummary> = combine(
        loanRepo.observeAllLoans(),
        loanRepo.observeTotalOutstanding(),
        loanRepo.observeOverdueInstallments(LocalDate.now().toString()),
    ) { loans, outstanding, overdueList ->
        ReportSummary(
            totalPrincipal   = loans.sumOf { it.principal },
            totalOutstanding = outstanding,
            totalOverdue     = overdueList.sumOf { it.remaining },
            overdueCount     = overdueList.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportSummary())

    suspend fun getCollectedInRange(from: String, to: String): Double =
        paymentRepo.getCollectedInRange(from, to)

    /**
     * Generates a CSV of all payments and shares it via the Android ShareSheet.
     * The CSV is written to the app's cache directory and shared using FileProvider.
     *
     * Columns: Date, Borrower, Loan ID, Amount, Method, Reference, Reversed
     */
    fun exportCsv(from: String, to: String) {
        viewModelScope.launch {
            try {
                val payments  = paymentRepo.observeAllPayments().first()
                val borrowers = borrowerRepo.observeAllBorrowers().first()
                val borrowerMap = borrowers.associateBy { it.id }

                val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

                val filtered = payments.filter { p ->
                    val date = p.paymentDate.toString()
                    date >= from && date <= to
                }

                val sb = StringBuilder()
                sb.appendLine("Date,Borrower,Loan ID,Amount (₱),Method,Reference,Reversed")
                filtered.forEach { p ->
                    val borrowerName = borrowerMap[p.borrowerId]?.name ?: "Unknown"
                    fun esc(s: String) = if (s.contains(",") || s.contains("\"")) "\"${s.replace("\"", "\"\"")}\"" else s
                    sb.appendLine(
                        "${p.paymentDate}," +
                        "${esc(borrowerName)}," +
                        "${p.loanId}," +
                        "${"%.2f".format(p.amount)}," +
                        "${p.paymentMethod.label}," +
                        "${esc(p.referenceNumber)}," +
                        "${p.isReversed}"
                    )
                }

                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                val file = File(context.cacheDir, "lendtrack_payments_$timestamp.csv")
                file.writeText(sb.toString())

                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type      = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "LendTrack Payments Export $from to $to")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Export CSV").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })

            } catch (e: Exception) {
                android.util.Log.e("ReportsVM", "CSV export failed: ${e.message}", e)
            }
        }
    }
}
