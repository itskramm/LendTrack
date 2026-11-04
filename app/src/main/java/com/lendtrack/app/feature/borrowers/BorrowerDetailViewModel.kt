package com.lendtrack.app.feature.borrowers

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lendtrack.app.core.util.DocumentCaptureHelper
import com.lendtrack.app.data.repository.BorrowerRepository
import com.lendtrack.app.data.repository.BorrowerValidationException
import com.lendtrack.app.data.repository.DocumentStorageRepository
import com.lendtrack.app.data.repository.DownloadState
import com.lendtrack.app.data.repository.LoanRepository
import com.lendtrack.app.data.repository.PaymentRepository
import com.lendtrack.app.data.repository.UploadState
import com.lendtrack.app.domain.model.*
import com.lendtrack.app.feature.loans.LoanFormFields
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
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
    private val documentStorageRepo: DocumentStorageRepository,
    private val documentCaptureHelper: DocumentCaptureHelper,
) : ViewModel() {

    val borrowerId: String = checkNotNull(savedStateHandle["borrowerId"])

    // ── Document upload state ──────────────────────────────────────────────
    private val _uploadState = MutableStateFlow<UploadState?>(null)
    val uploadState: StateFlow<UploadState?> = _uploadState.asStateFlow()

    private val _cameraPermissionGranted = MutableStateFlow(false)
    val cameraPermissionGranted: StateFlow<Boolean> = _cameraPermissionGranted.asStateFlow()

    init {
        // Download missing documents on initialization (multi-device sync)
        downloadMissingDocuments()
    }

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
            idDocumentUri                = b.idDocumentUri,
            billingAddressDocumentUri    = b.billingAddressDocumentUri,
            idDocumentFirebasePath       = b.idDocumentFirebasePath,
            billingAddressFirebasePath   = b.billingAddressFirebasePath,
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

    // ── Document management ────────────────────────────────────────────────

    /**
     * Upload ID document to Firebase Storage and update borrower record.
     */
    fun uploadIdDocument(uri: String) {
        viewModelScope.launch {
            documentStorageRepo.uploadDocument(
                localUri = uri,
                borrowerId = borrowerId,
                documentType = DocumentCaptureHelper.ID_DOCUMENT
            ).collect { state ->
                _uploadState.value = state
                
                when (state) {
                    is UploadState.Success -> {
                        // Update borrower with both local URI and Firebase path
                        val borrower = uiState.value.borrower
                        if (borrower != null) {
                            borrowerRepo.updateBorrower(
                                id = borrowerId,
                                name = borrower.name,
                                contactNumber = borrower.contactNumber,
                                preferredPaymentChannel = borrower.preferredPaymentChannel,
                                secondaryContactName = borrower.secondaryContactName,
                                secondaryContactNumber = borrower.secondaryContactNumber,
                                secondaryContactRelationship = borrower.secondaryContactRelationship,
                                governmentIdType = borrower.governmentIdType,
                                governmentIdNumber = borrower.governmentIdNumber,
                                address = borrower.address,
                                barangay = borrower.barangay,
                                occupation = borrower.occupation,
                                notes = borrower.notes,
                                idDocumentUri = uri,
                                billingAddressDocumentUri = borrower.billingAddressDocumentUri,
                                idDocumentFirebasePath = state.firebasePath,
                                billingAddressFirebasePath = borrower.billingAddressFirebasePath,
                            )
                        }
                        _uploadState.value = null
                    }
                    is UploadState.Failed -> {
                        // Error state is already set, UI can show retry button
                    }
                    is UploadState.Uploading -> {
                        // Progress is tracked in state
                    }
                }
            }
        }
    }

    /**
     * Upload billing address document to Firebase Storage and update borrower record.
     */
    fun uploadBillingAddressDocument(uri: String) {
        viewModelScope.launch {
            documentStorageRepo.uploadDocument(
                localUri = uri,
                borrowerId = borrowerId,
                documentType = DocumentCaptureHelper.BILLING_ADDRESS_DOCUMENT
            ).collect { state ->
                _uploadState.value = state
                
                when (state) {
                    is UploadState.Success -> {
                        val borrower = uiState.value.borrower
                        if (borrower != null) {
                            borrowerRepo.updateBorrower(
                                id = borrowerId,
                                name = borrower.name,
                                contactNumber = borrower.contactNumber,
                                preferredPaymentChannel = borrower.preferredPaymentChannel,
                                secondaryContactName = borrower.secondaryContactName,
                                secondaryContactNumber = borrower.secondaryContactNumber,
                                secondaryContactRelationship = borrower.secondaryContactRelationship,
                                governmentIdType = borrower.governmentIdType,
                                governmentIdNumber = borrower.governmentIdNumber,
                                address = borrower.address,
                                barangay = borrower.barangay,
                                occupation = borrower.occupation,
                                notes = borrower.notes,
                                idDocumentUri = borrower.idDocumentUri,
                                billingAddressDocumentUri = uri,
                                idDocumentFirebasePath = borrower.idDocumentFirebasePath,
                                billingAddressFirebasePath = state.firebasePath,
                            )
                        }
                        _uploadState.value = null
                    }
                    is UploadState.Failed -> {
                        // Error state tracked
                    }
                    is UploadState.Uploading -> {
                        // Progress tracked
                    }
                }
            }
        }
    }

    /**
     * Remove ID document: delete local file and Firebase Storage file, update borrower record.
     */
    fun removeIdDocument() {
        viewModelScope.launch {
            val borrower = uiState.value.borrower ?: return@launch
            
            // Delete local file
            borrower.idDocumentUri?.let { uri ->
                val file = File(Uri.parse(uri).path ?: "")
                if (file.exists()) file.delete()
            }
            
            // Delete Firebase Storage file
            borrower.idDocumentFirebasePath?.let { path ->
                documentStorageRepo.deleteDocument(path)
            }
            
            // Update borrower record
            borrowerRepo.updateBorrower(
                id = borrowerId,
                name = borrower.name,
                contactNumber = borrower.contactNumber,
                preferredPaymentChannel = borrower.preferredPaymentChannel,
                secondaryContactName = borrower.secondaryContactName,
                secondaryContactNumber = borrower.secondaryContactNumber,
                secondaryContactRelationship = borrower.secondaryContactRelationship,
                governmentIdType = borrower.governmentIdType,
                governmentIdNumber = borrower.governmentIdNumber,
                address = borrower.address,
                barangay = borrower.barangay,
                occupation = borrower.occupation,
                notes = borrower.notes,
                idDocumentUri = null,
                billingAddressDocumentUri = borrower.billingAddressDocumentUri,
                idDocumentFirebasePath = null,
                billingAddressFirebasePath = borrower.billingAddressFirebasePath,
            )
        }
    }

    /**
     * Remove billing address document.
     */
    fun removeBillingAddressDocument() {
        viewModelScope.launch {
            val borrower = uiState.value.borrower ?: return@launch
            
            // Delete local file
            borrower.billingAddressDocumentUri?.let { uri ->
                val file = File(Uri.parse(uri).path ?: "")
                if (file.exists()) file.delete()
            }
            
            // Delete Firebase Storage file
            borrower.billingAddressFirebasePath?.let { path ->
                documentStorageRepo.deleteDocument(path)
            }
            
            // Update borrower record
            borrowerRepo.updateBorrower(
                id = borrowerId,
                name = borrower.name,
                contactNumber = borrower.contactNumber,
                preferredPaymentChannel = borrower.preferredPaymentChannel,
                secondaryContactName = borrower.secondaryContactName,
                secondaryContactNumber = borrower.secondaryContactNumber,
                secondaryContactRelationship = borrower.secondaryContactRelationship,
                governmentIdType = borrower.governmentIdType,
                governmentIdNumber = borrower.governmentIdNumber,
                address = borrower.address,
                barangay = borrower.barangay,
                occupation = borrower.occupation,
                notes = borrower.notes,
                idDocumentUri = borrower.idDocumentUri,
                billingAddressDocumentUri = null,
                idDocumentFirebasePath = borrower.idDocumentFirebasePath,
                billingAddressFirebasePath = null,
            )
        }
    }

    /**
     * Replace ID document with a new one.
     */
    fun replaceIdDocument(uri: String) {
        removeIdDocument()
        uploadIdDocument(uri)
    }

    /**
     * Replace billing address document with a new one.
     */
    fun replaceBillingAddressDocument(uri: String) {
        removeBillingAddressDocument()
        uploadBillingAddressDocument(uri)
    }

    /**
     * Download missing documents from Firebase Storage to recreate local files.
     * This handles multi-device sync scenario where borrower has Firebase path but no local URI.
     */
    private fun downloadMissingDocuments() {
        viewModelScope.launch {
            uiState.collect { state ->
                val borrower = state.borrower ?: return@collect
                
                // Download ID document if Firebase path exists but local URI doesn't
                if (borrower.idDocumentFirebasePath != null && borrower.idDocumentUri == null) {
                    val targetFile = documentCaptureHelper.getDocumentFile(
                        borrowerId = borrowerId,
                        documentType = DocumentCaptureHelper.ID_DOCUMENT,
                        createIfMissing = true
                    )
                    
                    documentStorageRepo.downloadDocument(
                        firebasePath = borrower.idDocumentFirebasePath,
                        localTargetPath = targetFile.absolutePath
                    ).collect { downloadState ->
                        when (downloadState) {
                            is DownloadState.Success -> {
                                // Update borrower with local URI
                                borrowerRepo.updateBorrower(
                                    id = borrowerId,
                                    name = borrower.name,
                                    contactNumber = borrower.contactNumber,
                                    preferredPaymentChannel = borrower.preferredPaymentChannel,
                                    secondaryContactName = borrower.secondaryContactName,
                                    secondaryContactNumber = borrower.secondaryContactNumber,
                                    secondaryContactRelationship = borrower.secondaryContactRelationship,
                                    governmentIdType = borrower.governmentIdType,
                                    governmentIdNumber = borrower.governmentIdNumber,
                                    address = borrower.address,
                                    barangay = borrower.barangay,
                                    occupation = borrower.occupation,
                                    notes = borrower.notes,
                                    idDocumentUri = Uri.fromFile(targetFile).toString(),
                                    billingAddressDocumentUri = borrower.billingAddressDocumentUri,
                                    idDocumentFirebasePath = borrower.idDocumentFirebasePath,
                                    billingAddressFirebasePath = borrower.billingAddressFirebasePath,
                                )
                            }
                            else -> {
                                // Handle download failure silently for background sync
                            }
                        }
                    }
                }
                
                // Download billing address document if Firebase path exists but local URI doesn't
                if (borrower.billingAddressFirebasePath != null && borrower.billingAddressDocumentUri == null) {
                    val targetFile = documentCaptureHelper.getDocumentFile(
                        borrowerId = borrowerId,
                        documentType = DocumentCaptureHelper.BILLING_ADDRESS_DOCUMENT,
                        createIfMissing = true
                    )
                    
                    documentStorageRepo.downloadDocument(
                        firebasePath = borrower.billingAddressFirebasePath,
                        localTargetPath = targetFile.absolutePath
                    ).collect { downloadState ->
                        when (downloadState) {
                            is DownloadState.Success -> {
                                borrowerRepo.updateBorrower(
                                    id = borrowerId,
                                    name = borrower.name,
                                    contactNumber = borrower.contactNumber,
                                    preferredPaymentChannel = borrower.preferredPaymentChannel,
                                    secondaryContactName = borrower.secondaryContactName,
                                    secondaryContactNumber = borrower.secondaryContactNumber,
                                    secondaryContactRelationship = borrower.secondaryContactRelationship,
                                    governmentIdType = borrower.governmentIdType,
                                    governmentIdNumber = borrower.governmentIdNumber,
                                    address = borrower.address,
                                    barangay = borrower.barangay,
                                    occupation = borrower.occupation,
                                    notes = borrower.notes,
                                    idDocumentUri = borrower.idDocumentUri,
                                    billingAddressDocumentUri = Uri.fromFile(targetFile).toString(),
                                    idDocumentFirebasePath = borrower.idDocumentFirebasePath,
                                    billingAddressFirebasePath = borrower.billingAddressFirebasePath,
                                )
                            }
                            else -> {
                                // Handle download failure silently
                            }
                        }
                    }
                }
            }
        }
    }

    fun updateCameraPermission(granted: Boolean) {
        _cameraPermissionGranted.value = granted
    }
}
