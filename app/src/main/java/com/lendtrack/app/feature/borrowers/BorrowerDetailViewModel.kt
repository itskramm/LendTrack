package com.lendtrack.app.feature.borrowers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lendtrack.app.data.repository.BorrowerRepository
import com.lendtrack.app.data.repository.BorrowerValidationException
import com.lendtrack.app.data.repository.LoanRepository
import com.lendtrack.app.data.repository.PaymentRepository
import com.lendtrack.app.domain.model.*
import com.lendtrack.app.feature.loans.LoanFormFields
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BorrowerDetailUiState(
    val borrower:         Borrower? = null,
    val loans:            List<LoanWithBalance> = emptyList(),
    val payments:         List<Payment> = emptyList(),
    val totalOutstanding: Double = 0.0,
    val isLoading:        Boolean = true,
)

data class LoanWithBalance(
    val loan:         Loan,
    val installments: List<Installment>,
    val totalApplied: Double,
) {
    val balance:          Double get() = loan.totalPayable - totalApplied
    val remainingBalance: Double get() = installments.sumOf { it.remaining }
}

@HiltViewModel
class BorrowerDetailViewModel @Inject constructor(
    savedStateHandle:     SavedStateHandle,
    private val borrowerRepo: BorrowerRepository,
    private val loanRepo:     LoanRepository,
    private val paymentRepo:  PaymentRepository,
) : ViewModel() {

    val borrowerId: String = checkNotNull(savedStateHandle["borrowerId"])

    val uiState: StateFlow<BorrowerDetailUiState> = combine(
        borrowerRepo.observeBorrower(borrowerId),
        loanRepo.observeLoansByBorrower(borrowerId),
        paymentRepo.observePaymentsByBorrower(borrowerId),
    ) { borrower, loans, payments ->
        val enrichedLoans = loans.map { loan ->
            val installments = loanRepo.observeInstallments(loan.id).first()
            val applied      = installments.sumOf { it.amountPaid }
            LoanWithBalance(loan, installments, applied)
        }
        BorrowerDetailUiState(
            borrower         = borrower,
            loans            = enrichedLoans,
            payments         = payments,
            totalOutstanding = enrichedLoans.sumOf { it.remainingBalance },
            isLoading        = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BorrowerDetailUiState())

    // ── Edit dialog state ──────────────────────────────────────────────────
    var showEditDialog by mutableStateOf(false)
        private set
    var editError by mutableStateOf<String?>(null)
        private set

    fun openEdit()       { showEditDialog = true;  editError = null }
    fun dismissEdit()    { showEditDialog = false; editError = null }

    fun saveEdit(fields: BorrowerFormFields) {
        viewModelScope.launch {
            try {
                borrowerRepo.updateBorrower(
                    id                           = borrowerId,
                    name                         = fields.name,
                    contactNumber                = fields.contactNumber,
                    preferredPaymentChannel      = fields.preferredPaymentChannel,
                    secondaryContactName         = fields.secondaryContactName,
                    secondaryContactNumber       = fields.secondaryContactNumber,
                    secondaryContactRelationship = fields.secondaryContactRelationship,
                    governmentIdType             = fields.governmentIdType,
                    governmentIdNumber           = fields.governmentIdNumber,
                    address                      = fields.address,
                    barangay                     = fields.barangay,
                    occupation                   = fields.occupation,
                    notes                        = fields.notes,
                )
                dismissEdit()
            } catch (e: BorrowerValidationException) {
                editError = e.message
            }
        }
    }

    // ── Edit loan dialog state ─────────────────────────────────────────────
    var showEditLoanDialog by mutableStateOf(false)
        private set
    var editLoanId by mutableStateOf<String?>(null)
        private set
    var editLoanError by mutableStateOf<String?>(null)
        private set

    fun openLoanEdit(loanId: String) {
        editLoanId = loanId
        showEditLoanDialog = true
        editLoanError = null
    }
    fun dismissLoanEdit() {
        showEditLoanDialog = false
        editLoanId = null
        editLoanError = null
    }
    fun saveLoanEdit(fields: LoanFormFields) {
        val loanId = editLoanId
        if (loanId == null) {
            editLoanError = "No loan selected for editing."
            return
        }

        viewModelScope.launch {
            try {
                // Parse and validate fields
                val principal = fields.principalText.toDoubleOrNull()
                    ?: throw com.lendtrack.app.data.repository.LoanValidationException("Principal must be a valid number.")
                val interestAmount = fields.interestAmountText.toDoubleOrNull()
                    ?: throw com.lendtrack.app.data.repository.LoanValidationException("Interest amount must be a valid number.")
                val numberOfInstallments = fields.numberOfInstallments.toIntOrNull()
                    ?: throw com.lendtrack.app.data.repository.LoanValidationException("Number of installments must be a whole number.")
                val loanDate = runCatching { java.time.LocalDate.parse(fields.loanDateText) }.getOrElse {
                    throw com.lendtrack.app.data.repository.LoanValidationException("Loan date is not valid (YYYY-MM-DD).")
                }
                val firstDueDate = runCatching { java.time.LocalDate.parse(fields.firstDueDateText) }.getOrElse {
                    throw com.lendtrack.app.data.repository.LoanValidationException("First due date is not valid (YYYY-MM-DD).")
                }
                val customFrequencyDays = if (fields.paymentFrequency == PaymentFrequency.CUSTOM) {
                    fields.customFrequencyDays.toIntOrNull()
                        ?: throw com.lendtrack.app.data.repository.LoanValidationException("Custom frequency days must be a whole number.")
                } else {
                    0
                }

                // Call repository updateLoan
                loanRepo.updateLoan(
                    loanId               = loanId,
                    principal            = principal,
                    interestAmount       = interestAmount,
                    loanDate             = loanDate,
                    firstDueDate         = firstDueDate,
                    numberOfInstallments = numberOfInstallments,
                    paymentFrequency     = fields.paymentFrequency,
                    customFrequencyDays  = customFrequencyDays,
                    notes                = fields.notes,
                )

                // Success - dismiss dialog and clear error
                dismissLoanEdit()
            } catch (e: com.lendtrack.app.data.repository.LoanValidationException) {
                editLoanError = e.message
            }
        }
    }

    // ── Delete dialog state ────────────────────────────────────────────────
    var showDeleteDialog by mutableStateOf(false)
        private set
    var deleteReason by mutableStateOf("")
        private set
    var deleteError by mutableStateOf<String?>(null)
        private set

    fun openDelete()     { showDeleteDialog = true;  deleteReason = ""; deleteError = null }
    fun dismissDelete()  { showDeleteDialog = false; deleteReason = ""; deleteError = null }
    fun updateDeleteReason(reason: String) { deleteReason = reason }

    fun deleteBorrower(onSuccess: () -> Unit) {
        if (deleteReason.isBlank()) {
            deleteError = "A reason is required to delete a borrower."
            return
        }

        val activeLoansCount = uiState.value.loans.count { it.loan.status == LoanStatus.ACTIVE }
        if (activeLoansCount > 0) {
            deleteError = "Cannot delete borrower with $activeLoansCount active loan(s). Complete or cancel loans first."
            return
        }

        viewModelScope.launch {
            try {
                borrowerRepo.deleteBorrower(borrowerId, deleteReason)
                dismissDelete()
                onSuccess()
            } catch (e: BorrowerValidationException) {
                deleteError = e.message
            }
        }
    }

    /** Pre-fill the edit form with current borrower data */
    fun currentBorrowerAsFields(): BorrowerFormFields {
        val b = uiState.value.borrower ?: return BorrowerFormFields()
        return BorrowerFormFields(
            name                         = b.name,
            contactNumber                = b.contactNumber,
            preferredPaymentChannel      = b.preferredPaymentChannel,
            secondaryContactName         = b.secondaryContactName,
            secondaryContactNumber       = b.secondaryContactNumber,
            secondaryContactRelationship = b.secondaryContactRelationship,
            governmentIdType             = b.governmentIdType,
            governmentIdNumber           = b.governmentIdNumber,
            address                      = b.address,
            barangay                     = b.barangay,
            occupation                   = b.occupation,
            notes                        = b.notes,
        )
    }

    /** Pre-fill the edit loan form with current loan data */
    fun currentLoanAsFields(loanId: String?): LoanFormFields {
        if (loanId == null) return LoanFormFields()
        val lwb = uiState.value.loans.find { it.loan.id == loanId } ?: return LoanFormFields()
        val loan = lwb.loan
        return LoanFormFields(
            borrowerId           = loan.borrowerId,
            principalText        = loan.principal.toString(),
            interestAmountText   = loan.interestAmount.toString(),
            paymentFrequency     = loan.paymentFrequency,
            customFrequencyDays  = loan.customFrequencyDays.toString(),
            numberOfInstallments = loan.numberOfInstallments.toString(),
            loanDateText         = loan.loanDate.toString(),
            firstDueDateText     = loan.firstDueDate.toString(),
            notes                = loan.notes,
        )
    }
}
