package com.lendtrack.app.feature.payments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lendtrack.app.data.repository.BorrowerRepository
import com.lendtrack.app.data.repository.LoanRepository
import com.lendtrack.app.data.repository.PaymentPreview
import com.lendtrack.app.data.repository.PaymentRepository
import com.lendtrack.app.data.repository.PaymentValidationException
import com.lendtrack.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed class RecordPaymentUiState {
    data object Idle    : RecordPaymentUiState()
    data object Loading : RecordPaymentUiState()
    data class  Preview(val preview: PaymentPreview) : RecordPaymentUiState()
    data class  Error(val message: String)           : RecordPaymentUiState()
    data class  Success(val paymentId: String)       : RecordPaymentUiState()
}

data class PaymentFormFields(
    val selectedLoanId:  String       = "",
    val amountText:      String       = "",
    val paymentDate:     String       = LocalDate.now().toString(),
    val paymentMethod:   PaymentMethod = PaymentMethod.CASH,
    val referenceNumber: String       = "",
    val notes:           String       = "",
)

data class LoanSelectionItem(
    val loanId:       String,
    val borrowerName: String,
    val balance:      Double,
    val label:        String,
)

@HiltViewModel
class RecordPaymentViewModel @Inject constructor(
    savedStateHandle:  SavedStateHandle,
    private val paymentRepo:  PaymentRepository,
    private val loanRepo:     LoanRepository,
    private val borrowerRepo: BorrowerRepository,
) : ViewModel() {

    // Pre-select a loan if navigated from loan/borrower detail
    private val presetLoanId: String? = savedStateHandle["loanId"]

    private val _fields = MutableStateFlow(
        PaymentFormFields(selectedLoanId = presetLoanId ?: "")
    )
    val fields: StateFlow<PaymentFormFields> = _fields.asStateFlow()

    private val _uiState = MutableStateFlow<RecordPaymentUiState>(RecordPaymentUiState.Idle)
    val uiState: StateFlow<RecordPaymentUiState> = _uiState.asStateFlow()

    /** Active loans enriched with borrower name for the dropdown */
    val loanOptions: StateFlow<List<LoanSelectionItem>> = combine(
        loanRepo.observeActiveLoans(),
        borrowerRepo.observeAllBorrowers(),
    ) { loans, borrowers ->
        val map = borrowers.associateBy { it.id }
        loans.mapNotNull { loan ->
            val borrower = map[loan.borrowerId] ?: return@mapNotNull null
            val balance  = loan.totalPayable   // approximation; real balance from installments
            LoanSelectionItem(
                loanId       = loan.id,
                borrowerName = borrower.name,
                balance      = balance,
                label        = "${borrower.name} — ₱${"%.2f".format(balance)} balance",
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateField(update: PaymentFormFields.() -> PaymentFormFields) {
        _fields.value = _fields.value.update()
        if (_uiState.value is RecordPaymentUiState.Error) {
            _uiState.value = RecordPaymentUiState.Idle
        }
    }

    /**
     * Preview allocation without saving.
     */
    fun previewPayment() {
        val f = _fields.value
        _uiState.value = RecordPaymentUiState.Loading

        viewModelScope.launch {
            try {
                if (f.selectedLoanId.isBlank())
                    throw PaymentValidationException("Select a loan.")
                val amount = f.amountText.toDoubleOrNull()
                    ?: throw PaymentValidationException("Enter a valid payment amount.")

                val preview = paymentRepo.previewPaymentAllocation(f.selectedLoanId, amount)
                _uiState.value = RecordPaymentUiState.Preview(preview)

            } catch (e: PaymentValidationException) {
                _uiState.value = RecordPaymentUiState.Error(e.message ?: "Validation error.")
            }
        }
    }

    /**
     * Confirm and record the payment.
     */
    fun confirmPayment() {
        val f = _fields.value
        _uiState.value = RecordPaymentUiState.Loading

        viewModelScope.launch {
            try {
                val amount = f.amountText.toDouble()
                val date   = runCatching { LocalDate.parse(f.paymentDate) }.getOrElse { LocalDate.now() }

                // Find borrowerId from selected loan
                val loan = loanRepo.observeLoan(f.selectedLoanId).first()
                    ?: throw PaymentValidationException("Loan not found.")

                val paymentId = paymentRepo.recordPayment(
                    loanId          = f.selectedLoanId,
                    borrowerId      = loan.borrowerId,
                    amount          = amount,
                    paymentDate     = date,
                    paymentMethod   = f.paymentMethod,
                    referenceNumber = f.referenceNumber,
                    notes           = f.notes,
                )
                _uiState.value = RecordPaymentUiState.Success(paymentId)

            } catch (e: Exception) {
                _uiState.value = RecordPaymentUiState.Error(e.message ?: "Failed to record payment.")
            }
        }
    }

    fun resetState() { _uiState.value = RecordPaymentUiState.Idle }
}
