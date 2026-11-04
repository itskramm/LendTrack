package com.lendtrack.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Enumerations
// ─────────────────────────────────────────────────────────────────────────────

enum class InstallmentStatus(val label: String) {
    UPCOMING("Upcoming"),
    DUE_SOON("Due Soon"),
    OVERDUE("Overdue"),
    PAID("Paid"),
    PARTIAL("Partial"),
}

enum class LoanStatus { ACTIVE, COMPLETED, CANCELLED }

enum class PaymentFrequency(val label: String, val days: Int) {
    WEEKLY("Weekly", 7),
    BIWEEKLY("Bi-weekly", 14),
    MONTHLY("Monthly", 30),
    SEMIMONTHLY("Semi-monthly", 15),
    CUSTOM("Custom", 0),
}



/**
 * Payment channels used in the Philippines.
 * Matches real channels seen in the debtors records:
 * GCash, Cash, GoTyme, China Bank, Maribank, Maya, Bank Transfer.
 */
enum class PaymentMethod(val label: String) {
    CASH("Cash"),
    GCASH("GCash"),
    MAYA("Maya"),
    GOTYME("GoTyme"),
    CHINA_BANK("China Bank"),
    MARIBANK("Maribank"),
    UNION_BANK("Union Bank"),
    METRO_BANK("Metro Bank"),
    BANK_TRANSFER("Bank Transfer"),
    OTHER("Other"),
}

enum class SyncStatus { SYNCED, PENDING, SYNCING, FAILED, REQUIRES_ATTENTION }

enum class SyncOperationType {
    CREATE_BORROWER, UPDATE_BORROWER, ARCHIVE_BORROWER, DELETE_BORROWER,
    CREATE_LOAN, UPDATE_LOAN, CANCEL_LOAN, DELETE_LOAN,
    RECORD_PAYMENT, REVERSE_PAYMENT, DELETE_PAYMENT,
    CREATE_ADJUSTMENT,
    CREATE_MONTHLY_CHARGE,
    CREATE_LATE_PENALTY,
}

enum class ActivityType { PAYMENT, WARNING, NEW_LOAN, NEW_BORROWER, ADJUSTMENT, SYSTEM }

// ─────────────────────────────────────────────────────────────────────────────
// Borrower
//
// Fields derived from real debtors records (DEBTORS🌸.pdf):
// - name, contactNumber            → required
// - preferredPaymentChannel        → their usual way of paying (GCash, Cash, etc.)
// - secondaryContactName/Number    → guarantor or emergency contact
// - address, notes                 → optional free text
// - governmentIdType/Number        → optional identity verification
//
// All fields except name and contactNumber are optional — the system works
// without them. They are collected when available to assist with collections.
// ─────────────────────────────────────────────────────────────────────────────

data class Borrower(
    val id: String = UUID.randomUUID().toString(),

    // ── Required ──────────────────────────────────────────────────────────────
    val name:          String,
    val contactNumber: String,

    // ── Preferred payment channel (from debtors PDF) ──────────────────────────
    /** The channel this borrower typically uses to pay (GCash, Cash, GoTyme…) */
    val preferredPaymentChannel: PaymentMethod = PaymentMethod.GCASH,

    // ── Contact reference (optional) ──────────────────────────────────────────
    /** Name of a guarantor, spouse, relative, or employer — for follow-up */
    val secondaryContactName:         String = "",
    /** Phone number of the secondary contact */
    val secondaryContactNumber:       String = "",
    /** Relationship to borrower, e.g. "Spouse", "Parent", "Employer" */
    val secondaryContactRelationship: String = "",

    // ── Identity (optional) ───────────────────────────────────────────────────
    /** e.g. "PhilSys", "SSS", "Driver's License", "Passport", "Voter's ID", "TIN" */
    val governmentIdType:   String = "",
    val governmentIdNumber: String = "",

    // ── Location and occupation (optional) ────────────────────────────────────
    val address:    String = "",
    val barangay:   String = "",
    val occupation: String = "",

    // ── General notes ─────────────────────────────────────────────────────────
    val notes: String = "",

    // ── System fields ─────────────────────────────────────────────────────────
    val isArchived: Boolean = false,
    val createdAt:  LocalDateTime = LocalDateTime.now(),
    val updatedAt:  LocalDateTime = LocalDateTime.now(),
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val serverId:   String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// Loan
// ─────────────────────────────────────────────────────────────────────────────

data class Loan(
    val id:                   String = UUID.randomUUID().toString(),
    val borrowerId:           String,
    val principal:            Double,
    val interestAmount:       Double,
    val loanDate:             LocalDate,
    val firstDueDate:         LocalDate,
    val numberOfInstallments: Int,
    val paymentFrequency:     PaymentFrequency,
    val customFrequencyDays:  Int = 0,
    val monthlyInterestAmount: Double = 0.0,
    val latePenaltyAmount:    Double = 0.0,
    val latePenaltyGraceDays: Int = 0,
    val status:               LoanStatus = LoanStatus.ACTIVE,
    val notes:                String = "",
    val createdAt:            LocalDateTime = LocalDateTime.now(),
    val updatedAt:            LocalDateTime = LocalDateTime.now(),
    val syncStatus:           SyncStatus = SyncStatus.PENDING,
    val serverId:             String? = null,
) {
    val totalPayable:        Double get() = principal + interestAmount
    val installmentAmount:   Double get() = totalPayable / numberOfInstallments
    
    /** Effective interest rate for display purposes only (not used in calculations) */
    val effectiveRate: Double? get() = if (principal > 0) (interestAmount / principal) * 100 else null
}

// ─────────────────────────────────────────────────────────────────────────────
// Installment
// ─────────────────────────────────────────────────────────────────────────────

data class Installment(
    val id:                String = UUID.randomUUID().toString(),
    val loanId:            String,
    val installmentNumber: Int,
    val dueDate:           LocalDate,
    val amount:            Double,
    val amountPaid:        Double = 0.0,
    val syncStatus:        SyncStatus = SyncStatus.SYNCED,
) {
    val remaining: Double get() = maxOf(0.0, amount - amountPaid)

    fun statusOn(today: LocalDate): InstallmentStatus = when {
        remaining <= 0.0                    -> InstallmentStatus.PAID
        amountPaid > 0.0                    -> InstallmentStatus.PARTIAL
        dueDate.isBefore(today)             -> InstallmentStatus.OVERDUE
        !dueDate.isAfter(today.plusDays(1)) -> InstallmentStatus.DUE_SOON
        else                                -> InstallmentStatus.UPCOMING
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Payment
// ─────────────────────────────────────────────────────────────────────────────

data class Payment(
    val id:              String = UUID.randomUUID().toString(),
    val loanId:          String,
    val borrowerId:      String,
    val amount:          Double,
    val paymentDate:     LocalDate,
    val paymentMethod:   PaymentMethod,
    val referenceNumber: String = "",   // GCash ref, bank transaction ID, etc.
    val notes:           String = "",
    val isReversed:      Boolean = false,
    val reversalReason:  String = "",
    val idempotencyKey:  String = UUID.randomUUID().toString(),
    val createdAt:       LocalDateTime = LocalDateTime.now(),
    val syncStatus:      SyncStatus = SyncStatus.PENDING,
    val serverId:        String? = null,
)

data class PaymentAllocation(
    val id:              String = UUID.randomUUID().toString(),
    val paymentId:       String,
    val installmentId:   String,
    val allocatedAmount: Double,
    val createdAt:       LocalDateTime = LocalDateTime.now(),
)

data class PenaltyPaymentAllocation(
    val id:              String = UUID.randomUUID().toString(),
    val paymentId:       String,
    val penaltyId:       String,
    val allocatedAmount: Double,
    val createdAt:       LocalDateTime = LocalDateTime.now(),
)

data class ChargePaymentAllocation(
    val id:              String = UUID.randomUUID().toString(),
    val paymentId:       String,
    val chargeId:        String,
    val allocatedAmount: Double,
    val createdAt:       LocalDateTime = LocalDateTime.now(),
)

data class LoanAdjustment(
    val id:               String = UUID.randomUUID().toString(),
    val loanId:           String,
    val borrowerId:       String,
    val reason:           String,
    val adjustmentAmount: Double,
    val adjustedBy:       String,
    val createdAt:        LocalDateTime = LocalDateTime.now(),
    val syncStatus:       SyncStatus = SyncStatus.PENDING,
    val serverId:         String? = null,
)

data class SyncOperation(
    val id:             String = UUID.randomUUID().toString(),
    val operationType:  SyncOperationType,
    val entityId:       String,
    val payload:        String,
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val createdAt:      LocalDateTime = LocalDateTime.now(),
    val attemptCount:   Int = 0,
    val lastAttemptAt:  LocalDateTime? = null,
    val syncStatus:     SyncStatus = SyncStatus.PENDING,
    val errorMessage:   String? = null,
)

data class ActivityItem(
    val id:              String = UUID.randomUUID().toString(),
    val type:            ActivityType,
    val description:     String,
    val relatedEntityId: String? = null,
    val occurredAt:      LocalDateTime = LocalDateTime.now(),
)

// ─────────────────────────────────────────────────────────────────────────────
// Aggregated view models
// ─────────────────────────────────────────────────────────────────────────────

data class LoanSummary(
    val loan:         Loan,
    val borrower:     Borrower,
    val installments: List<Installment>,
    val totalApplied: Double,
) {
    val balance: Double get() = loan.totalPayable - totalApplied
}

data class DashboardSummary(
    val totalOutstandingBalance: Double,
    val totalCollectedThisMonth: Double,
    val dueTodayAmount:          Double,
    val dueTodayCount:           Int,
    val overdueAmount:           Double,
    val overdueCount:            Int,
    val activeBorrowerCount:     Int,
    val activeLoanCount:         Int,
    val recentPayments:          List<Payment>,
    val recentActivities:        List<ActivityItem>,
    val upcomingInstallments:    List<InstallmentWithBorrower>,
)

data class InstallmentWithBorrower(
    val installment: Installment,
    val loan:        Loan,
    val borrower:    Borrower,
) {
    fun statusOn(today: LocalDate) = installment.statusOn(today)
}

data class CalendarDaySummary(
    val date:         LocalDate,
    val installments: List<InstallmentWithBorrower>,
) {
    val totalDue:    Double  get() = installments.sumOf { it.installment.remaining }
    val hasOverdue:  Boolean get() = installments.any { it.installment.statusOn(LocalDate.now()) == InstallmentStatus.OVERDUE }
    val hasDueSoon:  Boolean get() = installments.any { it.installment.statusOn(LocalDate.now()) == InstallmentStatus.DUE_SOON }
}

// ─────────────────────────────────────────────────────────────────────────────
// Monthly Interest Charge
// ─────────────────────────────────────────────────────────────────────────────

data class MonthlyInterestCharge(
    val id:         String = UUID.randomUUID().toString(),
    val loanId:     String,
    val borrowerId: String,
    val amount:     Double,
    val amountPaid: Double = 0.0,
    val chargeDate: LocalDate,
    val isPaid:     Boolean = false,
    val createdAt:  LocalDateTime = LocalDateTime.now(),
    val syncStatus: SyncStatus = SyncStatus.PENDING,
) {
    val remaining: Double get() = maxOf(0.0, amount - amountPaid)
}

// ─────────────────────────────────────────────────────────────────────────────
// Late Penalty
// ─────────────────────────────────────────────────────────────────────────────

data class LatePenalty(
    val id:            String = UUID.randomUUID().toString(),
    val loanId:        String,
    val borrowerId:    String,
    val installmentId: String,
    val amount:        Double,
    val amountPaid:    Double = 0.0,
    val penaltyDate:   LocalDate,
    val reason:        String = "Overdue payment",
    val isPaid:        Boolean = false,
    val createdAt:     LocalDateTime = LocalDateTime.now(),
    val syncStatus:    SyncStatus = SyncStatus.PENDING,
) {
    val remaining: Double get() = maxOf(0.0, amount - amountPaid)
}

// ─────────────────────────────────────────────────────────────────────────────
// Loan Balance Breakdown
// ─────────────────────────────────────────────────────────────────────────────

data class LoanBalance(
    val installmentBalance: Double,
    val monthlyCharges:     Double,
    val penalties:          Double,
) {
    val totalBalance: Double get() = installmentBalance + monthlyCharges + penalties
}
