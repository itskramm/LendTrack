package com.lendtrack.app.feature.loans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lendtrack.app.data.repository.BorrowerRepository
import com.lendtrack.app.data.repository.LoanRepository
import com.lendtrack.app.data.repository.LoanValidationException
import com.lendtrack.app.domain.calculation.LoanCalculator
import com.lendtrack.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Simple loan totals for preview */
data class LoanTotals(
    val principal: Double,
    val interestAmount: Double,
    val totalPayable: Double,
    val installmentAmount: Double,
)

/** Represents a live preview of the loan being configured */
data class LoanPreview(
    val totals:             LoanTotals,
    val sampleInstallments: List<Installment>, // first 3 + last for preview
    val expectedEndDate:    LocalDate,
)

sealed class AddLoanUiState {
    data object Idle          : AddLoanUiState()
    data object Loading       : AddLoanUiState()
    data class  Preview(val preview: LoanPreview) : AddLoanUiState()
    data class  Error(val message: String)        : AddLoanUiState()
    data class  Success(val loanId: String)       : AddLoanUiState()
}

// Form fields — plain data class so the UI can hold a copy
data class LoanFormFields(
    val borrowerId:            String = "",
    val principalText:         String = "",
    val interestAmountText:    String = "",
    val monthlyInterestText:   String = "",
    val latePenaltyText:       String = "",
    val graceDaysText:         String = "",
    val paymentFrequency:      PaymentFrequency  = PaymentFrequency.BIWEEKLY,
    val customFrequencyDays:   String = "",
    val numberOfInstallments:  String = "",
    val loanDateText:          String = LocalDate.now().toString(),
    val firstDueDateText:      String = LocalDate.now().plusDays(14).toString(),
    val notes:                 String = "",
)

@HiltViewModel
class AddLoanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val borrowerRepo: BorrowerRepository,
    private val loanRepo:     LoanRepository,
) : ViewModel() {

    // borrowerId may be pre-set from navigation (e.g. from borrower detail)
    private val presetBorrowerId: String? = savedStateHandle["borrowerId"]

    private val _fields = MutableStateFlow(
        LoanFormFields(borrowerId = presetBorrowerId ?: "")
    )
    val fields: StateFlow<LoanFormFields> = _fields.asStateFlow()

    private val _uiState = MutableStateFlow<AddLoanUiState>(AddLoanUiState.Idle)
    val uiState: StateFlow<AddLoanUiState> = _uiState.asStateFlow()

    val borrowers: StateFlow<List<Borrower>> = borrowerRepo
        .observeActiveBorrowers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateField(update: LoanFormFields.() -> LoanFormFields) {
        _fields.value = _fields.value.update()
        // Clear error on any field change
        if (_uiState.value is AddLoanUiState.Error) {
            _uiState.value = AddLoanUiState.Idle
        }
    }

    /**
     * Calculate and display a repayment preview.
     * Does NOT save anything — the user reviews this before confirming.
     */
    fun calculatePreview() {
        val f = _fields.value
        _uiState.value = AddLoanUiState.Loading

        viewModelScope.launch {
            try {
                val principal   = f.principalText.toDoubleOrNull()
                    ?: throw LoanValidationException("Principal must be a valid number.")
                val interestAmount = f.interestAmountText.toDoubleOrNull()
                    ?: throw LoanValidationException("Interest amount must be a valid number.")
                val n           = f.numberOfInstallments.toIntOrNull()
                    ?: throw LoanValidationException("Number of installments must be a whole number.")
                val firstDue    = runCatching { LocalDate.parse(f.firstDueDateText) }.getOrElse {
                    throw LoanValidationException("First due date is not a valid date (YYYY-MM-DD).")
                }
                val customDays  = if (f.paymentFrequency == PaymentFrequency.CUSTOM)
                    f.customFrequencyDays.toIntOrNull() ?: 0 else 0

                val totalPayable = principal + interestAmount
                val installmentAmount = totalPayable / n

                // Generate a sample schedule for preview (show all installments)
                val allInstallments = LoanCalculator.generateInstallments(
                    loanId               = "preview",
                    principal            = principal,
                    interestAmount       = interestAmount,
                    numberOfInstallments = n,
                    paymentFrequency     = f.paymentFrequency,
                    customFrequencyDays  = customDays,
                    firstDueDate         = firstDue,
                )

                val expectedEndDate = allInstallments.last().dueDate

                // Create simple totals for preview
                val totals = LoanTotals(
                    principal = principal,
                    interestAmount = interestAmount,
                    totalPayable = totalPayable,
                    installmentAmount = installmentAmount,
                )

                _uiState.value = AddLoanUiState.Preview(
                    LoanPreview(
                        totals             = totals,
                        sampleInstallments = allInstallments,
                        expectedEndDate    = expectedEndDate,
                    )
                )
            } catch (e: LoanValidationException) {
                _uiState.value = AddLoanUiState.Error(e.message ?: "Validation error.")
            } catch (e: IllegalArgumentException) {
                _uiState.value = AddLoanUiState.Error(e.message ?: "Invalid input.")
            }
        }
    }

    /**
     * Confirm and save the loan.
     * Called only after the user reviews the preview.
     */
    fun confirmAndSave() {
        val f = _fields.value
        _uiState.value = AddLoanUiState.Loading

        viewModelScope.launch {
            try {
                val principal      = f.principalText.toDouble()
                val interestAmount = f.interestAmountText.toDouble()
                val n              = f.numberOfInstallments.toInt()
                val loanDate       = runCatching { LocalDate.parse(f.loanDateText) }.getOrElse { LocalDate.now() }
                val firstDue       = LocalDate.parse(f.firstDueDateText)
                val customDays     = if (f.paymentFrequency == PaymentFrequency.CUSTOM)
                    f.customFrequencyDays.toIntOrNull() ?: 0 else 0

                if (f.borrowerId.isBlank())
                    throw LoanValidationException("Select a borrower.")

                val monthlyInterest = f.monthlyInterestText.toDoubleOrNull() ?: 0.0
                val latePenalty = f.latePenaltyText.toDoubleOrNull() ?: 0.0
                val graceDays = f.graceDaysText.toIntOrNull() ?: 0

                val loanId = loanRepo.createLoan(
                    borrowerId            = f.borrowerId,
                    principal             = principal,
                    interestAmount        = interestAmount,
                    loanDate              = loanDate,
                    firstDueDate          = firstDue,
                    numberOfInstallments  = n,
                    paymentFrequency      = f.paymentFrequency,
                    customFrequencyDays   = customDays,
                    monthlyInterestAmount = monthlyInterest,
                    latePenaltyAmount     = latePenalty,
                    latePenaltyGraceDays  = graceDays,
                    notes                 = f.notes,
                )
                _uiState.value = AddLoanUiState.Success(loanId)

            } catch (e: Exception) {
                _uiState.value = AddLoanUiState.Error(e.message ?: "Failed to save loan.")
            }
        }
    }

    fun resetState() { _uiState.value = AddLoanUiState.Idle }
}
