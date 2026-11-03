package com.lendtrack.app.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lendtrack.app.data.repository.BorrowerRepository
import com.lendtrack.app.data.repository.LoanRepository
import com.lendtrack.app.domain.model.InstallmentWithBorrower
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val loanRepo:     LoanRepository,
    private val borrowerRepo: BorrowerRepository,
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _currentMonth = MutableStateFlow(YearMonth.now())
    private val _statusFilter = MutableStateFlow("All")

    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()
    val statusFilter: StateFlow<String>    = _statusFilter.asStateFlow()

    val monthInstallments: StateFlow<Map<LocalDate, List<InstallmentWithBorrower>>> =
        _currentMonth.flatMapLatest { month ->
            val from = month.atDay(1).toString()
            val to   = month.atEndOfMonth().toString()
            combine(
                loanRepo.observeInstallmentsInRange(from, to),
                loanRepo.observeAllLoans(),
                borrowerRepo.observeAllBorrowers(),
            ) { installments, loans, borrowers ->
                val loanMap     = loans.associateBy { it.id }
                val borrowerMap = borrowers.associateBy { it.id }
                installments
                    .mapNotNull { inst ->
                        val loan     = loanMap[inst.loanId]         ?: return@mapNotNull null
                        val borrower = borrowerMap[loan.borrowerId] ?: return@mapNotNull null
                        InstallmentWithBorrower(inst, loan, borrower)
                    }
                    .groupBy { it.installment.dueDate }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val selectedDayInstallments: StateFlow<List<InstallmentWithBorrower>> = combine(
        _selectedDate,
        monthInstallments,
    ) { date, map -> map[date] ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDate(date: LocalDate) { _selectedDate.value = date }
    fun previousMonth() { _currentMonth.value = _currentMonth.value.minusMonths(1) }
    fun nextMonth()     { _currentMonth.value = _currentMonth.value.plusMonths(1) }
    fun setStatusFilter(f: String) { _statusFilter.value = f }
}
