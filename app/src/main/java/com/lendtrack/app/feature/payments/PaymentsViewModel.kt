package com.lendtrack.app.feature.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lendtrack.app.data.repository.BorrowerRepository
import com.lendtrack.app.data.repository.PaymentRepository
import com.lendtrack.app.feature.dashboard.PaymentWithBorrower
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PaymentsViewModel @Inject constructor(
    private val paymentRepo:  PaymentRepository,
    private val borrowerRepo: BorrowerRepository,
) : ViewModel() {

    /** All payments enriched with borrower name — filtering is done in the UI composable */
    val payments: StateFlow<List<PaymentWithBorrower>> = combine(
        paymentRepo.observeAllPayments(),
        borrowerRepo.observeAllBorrowers(),
    ) { payments, borrowers ->
        val map = borrowers.associateBy { it.id }
        payments.mapNotNull { p ->
            val b = map[p.borrowerId] ?: return@mapNotNull null
            PaymentWithBorrower(p, b)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun deletePayment(paymentId: String, reason: String): Result<Unit> {
        return try {
            paymentRepo.deletePayment(paymentId, reason)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
