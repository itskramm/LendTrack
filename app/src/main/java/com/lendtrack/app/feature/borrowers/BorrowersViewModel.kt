package com.lendtrack.app.feature.borrowers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lendtrack.app.data.repository.BorrowerRepository
import com.lendtrack.app.data.repository.BorrowerValidationException
import com.lendtrack.app.data.repository.LoanRepository
import com.lendtrack.app.domain.model.Borrower
import com.lendtrack.app.domain.model.LoanStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BorrowerWithSummary(
    val borrower:           Borrower,
    val loanCount:          Int,
    val activeLoanCount:    Int,
    val outstandingBalance: Double,
)

@HiltViewModel
class BorrowersViewModel @Inject constructor(
    private val borrowerRepo: BorrowerRepository,
    private val loanRepo:     LoanRepository,
) : ViewModel() {

    private val _searchQuery  = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)

    var showAddDialog by mutableStateOf(false)
        private set
    var showEditDialog by mutableStateOf<String?>(null)
        private set

    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredBorrowers: StateFlow<List<BorrowerWithSummary>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) borrowerRepo.observeActiveBorrowers()
            else borrowerRepo.searchBorrowers(query)
        }
        .combine(loanRepo.observeAllLoans()) { borrowers, loans ->
            borrowers.map { borrower ->
                val borrowerLoans = loans.filter { it.borrowerId == borrower.id }
                val activeLoans   = borrowerLoans.filter { it.status == LoanStatus.ACTIVE }
                BorrowerWithSummary(
                    borrower           = borrower,
                    loanCount          = borrowerLoans.size,
                    activeLoanCount    = activeLoans.size,
                    outstandingBalance = activeLoans.sumOf { it.totalPayable },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchQuery(query: String) { _searchQuery.value = query }
    fun openAddDialog()              { showAddDialog = true }
    fun openEditDialog(id: String)   { showEditDialog = id }
    fun dismissDialog()              { showAddDialog = false; showEditDialog = null; _errorMessage.value = null }
    fun clearError()                 { _errorMessage.value = null }

    /** Called from AddBorrowerDialog with the full form fields */
    fun addBorrowerFull(fields: BorrowerFormFields) {
        viewModelScope.launch {
            try {
                borrowerRepo.addBorrower(
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
                    idDocumentUri                = fields.idDocumentUri,
                    billingAddressDocumentUri    = fields.billingAddressDocumentUri,
                    idDocumentFirebasePath       = fields.idDocumentFirebasePath,
                    billingAddressFirebasePath   = fields.billingAddressFirebasePath,
                )
                dismissDialog()
            } catch (e: BorrowerValidationException) {
                _errorMessage.value = e.message
            }
        }
    }

    fun updateBorrowerFull(id: String, fields: BorrowerFormFields) {
        viewModelScope.launch {
            try {
                borrowerRepo.updateBorrower(
                    id                           = id,
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
                    idDocumentUri                = fields.idDocumentUri,
                    billingAddressDocumentUri    = fields.billingAddressDocumentUri,
                    idDocumentFirebasePath       = fields.idDocumentFirebasePath,
                    billingAddressFirebasePath   = fields.billingAddressFirebasePath,
                )
                dismissDialog()
            } catch (e: BorrowerValidationException) {
                _errorMessage.value = e.message
            }
        }
    }
}
