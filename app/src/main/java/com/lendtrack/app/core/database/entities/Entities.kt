package com.lendtrack.app.core.database.entities

import androidx.room.*
import com.lendtrack.app.domain.model.*
import java.time.LocalDate
import java.time.LocalDateTime

// ─────────────────────────────────────────────────────────────────────────────
// Room type converters — LocalDate and LocalDateTime stored as ISO-8601 strings
// ─────────────────────────────────────────────────────────────────────────────

class Converters {
    @TypeConverter fun fromLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }
    @TypeConverter fun toLocalDate(date: LocalDate?): String? = date?.toString()
    @TypeConverter fun fromLocalDateTime(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it) }
    @TypeConverter fun toLocalDateTime(dt: LocalDateTime?): String? = dt?.toString()
    @TypeConverter fun fromSyncStatus(value: String?): SyncStatus? = value?.let { SyncStatus.valueOf(it) }
    @TypeConverter fun toSyncStatus(s: SyncStatus?): String? = s?.name
    @TypeConverter fun fromLoanStatus(value: String?): LoanStatus? = value?.let { LoanStatus.valueOf(it) }
    @TypeConverter fun toLoanStatus(s: LoanStatus?): String? = s?.name
    @TypeConverter fun fromPaymentFrequency(value: String?): PaymentFrequency? = value?.let { PaymentFrequency.valueOf(it) }
    @TypeConverter fun toPaymentFrequency(f: PaymentFrequency?): String? = f?.name
    @TypeConverter fun fromPaymentMethod(value: String?): PaymentMethod? = value?.let { PaymentMethod.valueOf(it) }
    @TypeConverter fun toPaymentMethod(m: PaymentMethod?): String? = m?.name
    @TypeConverter fun fromSyncOperationType(value: String?): SyncOperationType? = value?.let { SyncOperationType.valueOf(it) }
    @TypeConverter fun toSyncOperationType(t: SyncOperationType?): String? = t?.name
    @TypeConverter fun fromActivityType(value: String?): ActivityType? = value?.let { ActivityType.valueOf(it) }
    @TypeConverter fun toActivityType(t: ActivityType?): String? = t?.name
}

// ─────────────────────────────────────────────────────────────────────────────
// Entities
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "borrowers",
    indices = [
        Index(value = ["name"]),
        Index(value = ["sync_status"]),
    ])
data class BorrowerEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "contact_number")  val contactNumber: String,

    // Preferred payment channel — from real debtors data (GCash, Cash, GoTyme…)
    @ColumnInfo(name = "preferred_payment_channel") val preferredPaymentChannel: PaymentMethod = PaymentMethod.GCASH,

    // Secondary contact / guarantor (all optional)
    @ColumnInfo(name = "secondary_contact_name")         val secondaryContactName:         String = "",
    @ColumnInfo(name = "secondary_contact_number")       val secondaryContactNumber:       String = "",
    @ColumnInfo(name = "secondary_contact_relationship") val secondaryContactRelationship: String = "",

    // Identity (optional)
    @ColumnInfo(name = "government_id_type")   val governmentIdType:   String = "",
    @ColumnInfo(name = "government_id_number") val governmentIdNumber: String = "",

    // Location and occupation (optional)
    val address:    String = "",
    val barangay:   String = "",
    val occupation: String = "",

    val notes: String = "",

    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at")  val createdAt:  LocalDateTime,
    @ColumnInfo(name = "updated_at")  val updatedAt:  LocalDateTime,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "server_id")   val serverId:   String? = null,
)

@Entity(tableName = "loans",
    foreignKeys = [
        ForeignKey(
            entity = BorrowerEntity::class,
            parentColumns = ["id"],
            childColumns  = ["borrower_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["borrower_id"]),
        Index(value = ["status"]),
        Index(value = ["sync_status"]),
    ])
data class LoanEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "borrower_id") val borrowerId: String,
    val principal: Double,
    @ColumnInfo(name = "interest_amount") val interestAmount: Double,
    @ColumnInfo(name = "loan_date")       val loanDate: LocalDate,
    @ColumnInfo(name = "first_due_date")  val firstDueDate: LocalDate,
    @ColumnInfo(name = "number_of_installments") val numberOfInstallments: Int,
    @ColumnInfo(name = "payment_frequency") val paymentFrequency: PaymentFrequency,
    @ColumnInfo(name = "custom_frequency_days") val customFrequencyDays: Int = 0,
    @ColumnInfo(name = "monthly_interest_amount") val monthlyInterestAmount: Double = 0.0,
    @ColumnInfo(name = "late_penalty_amount") val latePenaltyAmount: Double = 0.0,
    @ColumnInfo(name = "late_penalty_grace_days") val latePenaltyGraceDays: Int = 0,
    val status: LoanStatus = LoanStatus.ACTIVE,
    val notes: String = "",
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime,
    @ColumnInfo(name = "updated_at") val updatedAt: LocalDateTime,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "server_id")   val serverId: String? = null,
)

@Entity(tableName = "installments",
    foreignKeys = [
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns  = ["loan_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["loan_id"]),
        Index(value = ["due_date"]),
    ])
data class InstallmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "loan_id") val loanId: String,
    @ColumnInfo(name = "installment_number") val installmentNumber: Int,
    @ColumnInfo(name = "due_date") val dueDate: LocalDate,
    val amount: Double,
    @ColumnInfo(name = "amount_paid") val amountPaid: Double = 0.0,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

@Entity(tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns  = ["loan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BorrowerEntity::class,
            parentColumns = ["id"],
            childColumns  = ["borrower_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["loan_id"]),
        Index(value = ["borrower_id"]),
        Index(value = ["payment_date"]),
        Index(value = ["idempotency_key"], unique = true),
        Index(value = ["sync_status"]),
    ])
data class PaymentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "loan_id")     val loanId: String,
    @ColumnInfo(name = "borrower_id") val borrowerId: String,
    val amount: Double,
    @ColumnInfo(name = "payment_date")   val paymentDate: LocalDate,
    @ColumnInfo(name = "payment_method") val paymentMethod: PaymentMethod,
    @ColumnInfo(name = "reference_number") val referenceNumber: String = "",
    val notes: String = "",
    @ColumnInfo(name = "is_reversed")     val isReversed: Boolean = false,
    @ColumnInfo(name = "reversal_reason") val reversalReason: String = "",
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    @ColumnInfo(name = "created_at")      val createdAt: LocalDateTime,
    @ColumnInfo(name = "sync_status")     val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "server_id")       val serverId: String? = null,
)

@Entity(tableName = "payment_allocations",
    foreignKeys = [
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["id"],
            childColumns  = ["payment_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = InstallmentEntity::class,
            parentColumns = ["id"],
            childColumns  = ["installment_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["payment_id"]),
        Index(value = ["installment_id"]),
    ])
data class PaymentAllocationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "payment_id")     val paymentId: String,
    @ColumnInfo(name = "installment_id") val installmentId: String,
    @ColumnInfo(name = "allocated_amount") val allocatedAmount: Double,
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime,
)

@Entity(tableName = "loan_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns  = ["loan_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["loan_id"]),
        Index(value = ["sync_status"]),
    ])
data class LoanAdjustmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "loan_id")     val loanId: String,
    @ColumnInfo(name = "borrower_id") val borrowerId: String,
    val reason: String,
    @ColumnInfo(name = "adjustment_amount") val adjustmentAmount: Double,
    @ColumnInfo(name = "adjusted_by") val adjustedBy: String,
    @ColumnInfo(name = "created_at")  val createdAt: LocalDateTime,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "server_id")   val serverId: String? = null,
)

@Entity(tableName = "sync_operations",
    indices = [
        Index(value = ["entity_id"]),
        Index(value = ["sync_status"]),
        Index(value = ["idempotency_key"], unique = true),
    ])
data class SyncOperationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "operation_type") val operationType: SyncOperationType,
    @ColumnInfo(name = "entity_id")      val entityId: String,
    val payload: String,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    @ColumnInfo(name = "created_at")      val createdAt: LocalDateTime,
    @ColumnInfo(name = "attempt_count")   val attemptCount: Int = 0,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: LocalDateTime? = null,
    @ColumnInfo(name = "sync_status")     val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "error_message")   val errorMessage: String? = null,
)

@Entity(tableName = "activity_log",
    indices = [Index(value = ["occurred_at"])])
data class ActivityLogEntity(
    @PrimaryKey val id: String,
    val type: ActivityType,
    val description: String,
    @ColumnInfo(name = "related_entity_id") val relatedEntityId: String? = null,
    @ColumnInfo(name = "occurred_at")       val occurredAt: LocalDateTime,
)

@Entity(tableName = "monthly_interest_charges",
    foreignKeys = [
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns  = ["loan_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["loan_id"]),
        Index(value = ["borrower_id"]),
        Index(value = ["charge_date"]),
    ])
data class MonthlyInterestChargeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "loan_id")     val loanId: String,
    @ColumnInfo(name = "borrower_id") val borrowerId: String,
    val amount: Double,
    @ColumnInfo(name = "charge_date") val chargeDate: LocalDate,
    @ColumnInfo(name = "is_paid")     val isPaid: Boolean = false,
    @ColumnInfo(name = "created_at")  val createdAt: LocalDateTime,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.PENDING,
)

@Entity(tableName = "late_penalties",
    foreignKeys = [
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns  = ["loan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = InstallmentEntity::class,
            parentColumns = ["id"],
            childColumns  = ["installment_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["loan_id"]),
        Index(value = ["borrower_id"]),
        Index(value = ["installment_id"]),
        Index(value = ["penalty_date"]),
    ])
data class LatePenaltyEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "loan_id")        val loanId: String,
    @ColumnInfo(name = "borrower_id")    val borrowerId: String,
    @ColumnInfo(name = "installment_id") val installmentId: String,
    val amount: Double,
    @ColumnInfo(name = "penalty_date") val penaltyDate: LocalDate,
    val reason: String = "Overdue payment",
    @ColumnInfo(name = "is_paid")      val isPaid: Boolean = false,
    @ColumnInfo(name = "created_at")   val createdAt: LocalDateTime,
    @ColumnInfo(name = "sync_status")  val syncStatus: SyncStatus = SyncStatus.PENDING,
)
