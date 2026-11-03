package com.lendtrack.app.feature.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lendtrack.app.core.database.dao.InstallmentDao
import com.lendtrack.app.data.repository.BorrowerRepository
import com.lendtrack.app.data.repository.LoanRepository
import com.lendtrack.app.data.repository.PaymentRepository
import com.lendtrack.app.domain.model.LoanSummary
import com.lendtrack.app.feature.dashboard.PaymentWithBorrower
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val loanRepo:       LoanRepository,
    private val borrowerRepo:   BorrowerRepository,
    private val paymentRepo:    PaymentRepository,
    private val installmentDao: InstallmentDao,
) : ViewModel() {

    private val _query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val loanSummaries: StateFlow<List<LoanSummary>> = _query
        .debounce(300)
        .flatMapLatest { query ->
            combine(
                loanRepo.observeAllLoans(),
                borrowerRepo.observeAllBorrowers(),
            ) { loans, borrowers ->
                val borrowerMap = borrowers.associateBy { it.id }
                loans
                    .filter { loan ->
                        query.isBlank() ||
                        borrowerMap[loan.borrowerId]?.name?.contains(query, ignoreCase = true) == true
                    }
                    .mapNotNull { loan ->
                        val borrower = borrowerMap[loan.borrowerId] ?: return@mapNotNull null
                        // Compute real totalApplied from installment amountPaid sums
                        // This is a coroutine suspend call inside a Flow map — safe here
                        // because flatMapLatest already runs on a coroutine context.
                        val totalPaid = installmentDao.getTotalPaid(loan.id)
                        LoanSummary(
                            loan         = loan,
                            borrower     = borrower,
                            installments = emptyList(),   // lazy-loaded on expand
                            totalApplied = totalPaid,
                        )
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentPayments: StateFlow<List<PaymentWithBorrower>> = combine(
        paymentRepo.observeAllPayments(),
        borrowerRepo.observeAllBorrowers(),
    ) { payments, borrowers ->
        val map = borrowers.associateBy { it.id }
        payments.mapNotNull { p ->
            val b = map[p.borrowerId] ?: return@mapNotNull null
            PaymentWithBorrower(p, b)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearch(query: String) { _query.value = query }

    suspend fun getInstallmentsForLoan(loanId: String) =
        loanRepo.observeInstallments(loanId).first()

    suspend fun deleteLoan(loanId: String, reason: String): Result<Unit> {
        return try {
            loanRepo.deleteLoan(loanId, reason)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
