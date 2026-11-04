package com.lendtrack.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lendtrack.app.core.database.dao.LatePenaltyDao
import com.lendtrack.app.core.database.dao.MonthlyInterestChargeDao
import com.lendtrack.app.core.database.dao.SyncDao
import com.lendtrack.app.core.database.entities.*
import com.lendtrack.app.core.preferences.ThemePreferences
import com.lendtrack.app.core.sync.NetworkMonitor
import com.lendtrack.app.data.repository.BorrowerRepository
import com.lendtrack.app.data.repository.LoanRepository
import com.lendtrack.app.data.repository.PaymentRepository
import com.lendtrack.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val borrowerRepo:   BorrowerRepository,
    private val loanRepo:       LoanRepository,
    private val paymentRepo:    PaymentRepository,
    private val syncDao:        SyncDao,
    private val networkMonitor: NetworkMonitor,
    private val monthlyInterestChargeDao: MonthlyInterestChargeDao,
    private val latePenaltyDao: LatePenaltyDao,
    private val themePreferences: ThemePreferences,
) : ViewModel() {

    private val today    = LocalDate.now()
    private val monthStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM"))

    // ── Connectivity ──────────────────────────────────────────────────────────

    /** True when the device has internet access. Used for the offline banner. */
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), networkMonitor.isCurrentlyOnline())

    // ── Sync status ───────────────────────────────────────────────────────────

    val pendingSyncCount: StateFlow<Int> = syncDao
        .observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val requiresAttentionCount: StateFlow<Int> = syncDao
        .observeRequiresAttentionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Stats ─────────────────────────────────────────────────────────────────

    val totalOutstanding: StateFlow<Double> = combine(
        loanRepo.observeTotalOutstanding(),
        monthlyInterestChargeDao.observeTotalUnpaid(),
        latePenaltyDao.observeTotalUnpaid(),
    ) { installments, monthlyCharges, penalties ->
        installments + monthlyCharges + penalties
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val collectedThisMonth: StateFlow<Double> = paymentRepo
        .observeCollectedThisMonth(monthStr)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val activeLoanCount: StateFlow<Int> = loanRepo
        .observeActiveLoanCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val activeBorrowerCount: StateFlow<Int> = borrowerRepo
        .observeActiveCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Enriched installment lists ────────────────────────────────────────────

    val upcomingInstallments: StateFlow<List<InstallmentWithBorrower>> = combine(
        loanRepo.observeInstallmentsInRange(today.toString(), today.plusDays(30).toString()),
        loanRepo.observeAllLoans(),
        borrowerRepo.observeAllBorrowers(),
    ) { installments, loans, borrowers ->
        val loanMap     = loans.associateBy { it.id }
        val borrowerMap = borrowers.associateBy { it.id }
        installments
            .filter { it.remaining > 0 }
            .mapNotNull { inst ->
                val loan     = loanMap[inst.loanId]         ?: return@mapNotNull null
                val borrower = borrowerMap[loan.borrowerId] ?: return@mapNotNull null
                InstallmentWithBorrower(inst, loan, borrower)
            }
            .sortedBy { it.installment.dueDate }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val overdueInstallments: StateFlow<List<InstallmentWithBorrower>> = combine(
        loanRepo.observeOverdueInstallments(today.toString()),
        loanRepo.observeAllLoans(),
        borrowerRepo.observeAllBorrowers(),
    ) { installments, loans, borrowers ->
        val loanMap     = loans.associateBy { it.id }
        val borrowerMap = borrowers.associateBy { it.id }
        installments.mapNotNull { inst ->
            val loan     = loanMap[inst.loanId]         ?: return@mapNotNull null
            val borrower = borrowerMap[loan.borrowerId] ?: return@mapNotNull null
            InstallmentWithBorrower(inst, loan, borrower)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Payments and activity ─────────────────────────────────────────────────

    val recentPayments: StateFlow<List<PaymentWithBorrower>> = combine(
        paymentRepo.observeRecentPayments(10),
        borrowerRepo.observeAllBorrowers(),
    ) { payments, borrowers ->
        val map = borrowers.associateBy { it.id }
        payments.mapNotNull { p ->
            val b = map[p.borrowerId] ?: return@mapNotNull null
            PaymentWithBorrower(p, b)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentActivities: StateFlow<List<ActivityLogEntity>> = syncDao
        .observeRecentActivities(6)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Derived stats ─────────────────────────────────────────────────────────

    val dueTodayAmount: StateFlow<Double> = upcomingInstallments
        .map { list -> list.filter { it.installment.dueDate == today }.sumOf { it.installment.remaining } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val overdueAmount: StateFlow<Double> = overdueInstallments
        .map { list -> list.sumOf { it.installment.remaining } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    // ── Theme ─────────────────────────────────────────────────────────────────

    /** True when dark theme is active, false for light theme. */
    val isDarkTheme: StateFlow<Boolean> = themePreferences.themeMode
        .map { mode ->
            when (mode) {
                ThemePreferences.ThemeMode.DARK -> true
                ThemePreferences.ThemeMode.LIGHT -> false
                ThemePreferences.ThemeMode.SYSTEM -> false // Default to light for SYSTEM
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Toggle between LIGHT and DARK theme modes. */
    fun toggleTheme() {
        viewModelScope.launch {
            val currentMode = themePreferences.themeMode.first()
            val newMode = when (currentMode) {
                ThemePreferences.ThemeMode.DARK -> ThemePreferences.ThemeMode.LIGHT
                else -> ThemePreferences.ThemeMode.DARK
            }
            themePreferences.setThemeMode(newMode)
        }
    }
}

// ── Shared data classes ───────────────────────────────────────────────────────

data class PaymentWithBorrower(val payment: Payment, val borrower: Borrower)

// ── Entity → Domain mappers ───────────────────────────────────────────────────
// These live here temporarily. Repositories return domain models directly;
// these extensions are used by any ViewModel that still reads raw entities
// (e.g. from a DAO injected before repositories were wired).

fun BorrowerEntity.toDomain() = Borrower(
    id                            = id,
    name                          = name,
    contactNumber                 = contactNumber,
    preferredPaymentChannel       = preferredPaymentChannel,
    secondaryContactName          = secondaryContactName,
    secondaryContactNumber        = secondaryContactNumber,
    secondaryContactRelationship  = secondaryContactRelationship,
    governmentIdType              = governmentIdType,
    governmentIdNumber            = governmentIdNumber,
    address                       = address,
    barangay                      = barangay,
    occupation                    = occupation,
    notes                         = notes,
    isArchived                    = isArchived,
    createdAt                     = createdAt,
    updatedAt                     = updatedAt,
    syncStatus                    = syncStatus,
    serverId                      = serverId,
)

fun LoanEntity.toDomain() = Loan(
    id                   = id,
    borrowerId           = borrowerId,
    principal            = principal,
    interestAmount       = interestAmount,
    loanDate             = loanDate,
    firstDueDate         = firstDueDate,
    numberOfInstallments = numberOfInstallments,
    paymentFrequency     = paymentFrequency,
    customFrequencyDays  = customFrequencyDays,
    status               = status,
    notes                = notes,
    createdAt            = createdAt,
    updatedAt            = updatedAt,
    syncStatus           = syncStatus,
    serverId             = serverId,
)

fun InstallmentEntity.toDomain() = Installment(
    id                = id,
    loanId            = loanId,
    installmentNumber = installmentNumber,
    dueDate           = dueDate,
    amount            = amount,
    amountPaid        = amountPaid,
    syncStatus        = syncStatus,
)

fun PaymentEntity.toDomain() = Payment(
    id              = id,
    loanId          = loanId,
    borrowerId      = borrowerId,
    amount          = amount,
    paymentDate     = paymentDate,
    paymentMethod   = paymentMethod,
    referenceNumber = referenceNumber,
    notes           = notes,
    isReversed      = isReversed,
    reversalReason  = reversalReason,
    idempotencyKey  = idempotencyKey,
    createdAt       = createdAt,
    syncStatus      = syncStatus,
    serverId        = serverId,
)
