package com.lendtrack.app.data.repository

import androidx.room.withTransaction
import com.lendtrack.app.core.database.LendTrackDatabase
import com.lendtrack.app.core.database.dao.*
import com.lendtrack.app.core.database.entities.ActivityLogEntity
import com.lendtrack.app.core.database.entities.BorrowerEntity
import com.lendtrack.app.core.database.entities.SyncOperationEntity
import com.lendtrack.app.core.sync.SyncTrigger
import com.lendtrack.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BorrowerRepository @Inject constructor(
    private val db:             LendTrackDatabase,
    private val borrowerDao:    BorrowerDao,
    private val loanDao:        LoanDao,
    private val installmentDao: InstallmentDao,
    private val paymentDao:     PaymentDao,
    private val syncDao:        SyncDao,
    private val syncTrigger:    SyncTrigger,
) {
    // ── Observation ───────────────────────────────────────────────────────────

    fun observeActiveBorrowers(): Flow<List<Borrower>> =
        borrowerDao.observeActive().map { it.map { e -> e.toDomain() } }

    fun observeAllBorrowers(): Flow<List<Borrower>> =
        borrowerDao.observeAll().map { it.map { e -> e.toDomain() } }

    fun searchBorrowers(query: String): Flow<List<Borrower>> =
        borrowerDao.search(query).map { it.map { e -> e.toDomain() } }

    fun observeBorrower(id: String): Flow<Borrower?> =
        borrowerDao.observeById(id).map { it?.toDomain() }

    fun observeActiveCount(): Flow<Int> =
        borrowerDao.observeActiveCount()

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * Add a new borrower.
     * Only [name] and [contactNumber] are required.
     * All other fields are optional and default to empty string.
     */
    suspend fun addBorrower(
        name:                         String,
        contactNumber:                String,
        preferredPaymentChannel:      PaymentMethod = PaymentMethod.GCASH,
        secondaryContactName:         String = "",
        secondaryContactNumber:       String = "",
        secondaryContactRelationship: String = "",
        governmentIdType:             String = "",
        governmentIdNumber:           String = "",
        address:                      String = "",
        barangay:                     String = "",
        occupation:                   String = "",
        notes:                        String = "",
        idDocumentUri:                String? = null,
        billingAddressDocumentUri:    String? = null,
        idDocumentFirebasePath:       String? = null,
        billingAddressFirebasePath:   String? = null,
    ): String {
        val trimmedName    = name.trim()
        val trimmedContact = contactNumber.trim()

        if (trimmedName.isBlank())    throw BorrowerValidationException("Full name is required.")
        if (trimmedContact.isBlank()) throw BorrowerValidationException("Contact number is required.")

        if (borrowerDao.countByName(trimmedName) > 0) {
            throw BorrowerValidationException(
                "A borrower named \"$trimmedName\" already exists. " +
                "Check the existing record before adding a duplicate."
            )
        }

        val now = LocalDateTime.now()
        val id  = UUID.randomUUID().toString()

        val entity = BorrowerEntity(
            id                            = id,
            name                          = trimmedName,
            contactNumber                 = trimmedContact,
            preferredPaymentChannel       = preferredPaymentChannel,
            secondaryContactName          = secondaryContactName.trim(),
            secondaryContactNumber        = secondaryContactNumber.trim(),
            secondaryContactRelationship  = secondaryContactRelationship.trim(),
            governmentIdType              = governmentIdType.trim(),
            governmentIdNumber            = governmentIdNumber.trim(),
            address                       = address.trim(),
            barangay                      = barangay.trim(),
            occupation                    = occupation.trim(),
            notes                         = notes.trim(),
            idDocumentUri                 = idDocumentUri,
            billingAddressDocumentUri     = billingAddressDocumentUri,
            idDocumentFirebasePath        = idDocumentFirebasePath,
            billingAddressFirebasePath    = billingAddressFirebasePath,
            isArchived                    = false,
            createdAt                     = now,
            updatedAt                     = now,
            syncStatus                    = SyncStatus.PENDING,
        )
        val syncOp = SyncOperationEntity(
            id             = UUID.randomUUID().toString(),
            operationType  = SyncOperationType.CREATE_BORROWER,
            entityId       = id,
            payload        = buildBorrowerPayload(entity),
            idempotencyKey = UUID.randomUUID().toString(),
            createdAt      = now,
        )
        val activity = ActivityLogEntity(
            id              = UUID.randomUUID().toString(),
            type            = ActivityType.NEW_BORROWER,
            description     = "New borrower added — $trimmedName",
            relatedEntityId = id,
            occurredAt      = now,
        )

        borrowerDao.insert(entity)
        syncDao.insertOperation(syncOp)
        syncDao.insertActivity(activity)
        syncTrigger.onLocalWrite()

        return id
    }

    /**
     * Update an existing borrower. All fields are editable except [id] and [createdAt].
     */
    suspend fun updateBorrower(
        id:                           String,
        name:                         String,
        contactNumber:                String,
        preferredPaymentChannel:      PaymentMethod = PaymentMethod.GCASH,
        secondaryContactName:         String = "",
        secondaryContactNumber:       String = "",
        secondaryContactRelationship: String = "",
        governmentIdType:             String = "",
        governmentIdNumber:           String = "",
        address:                      String = "",
        barangay:                     String = "",
        occupation:                   String = "",
        notes:                        String = "",
        idDocumentUri:                String? = null,
        billingAddressDocumentUri:    String? = null,
        idDocumentFirebasePath:       String? = null,
        billingAddressFirebasePath:   String? = null,
    ) {
        val existing = borrowerDao.getById(id)
            ?: throw BorrowerValidationException("Borrower not found.")
        if (name.isBlank())          throw BorrowerValidationException("Full name is required.")
        if (contactNumber.isBlank()) throw BorrowerValidationException("Contact number is required.")

        val now = LocalDateTime.now()
        borrowerDao.update(
            existing.copy(
                name                          = name.trim(),
                contactNumber                 = contactNumber.trim(),
                preferredPaymentChannel       = preferredPaymentChannel,
                secondaryContactName          = secondaryContactName.trim(),
                secondaryContactNumber        = secondaryContactNumber.trim(),
                secondaryContactRelationship  = secondaryContactRelationship.trim(),
                governmentIdType              = governmentIdType.trim(),
                governmentIdNumber            = governmentIdNumber.trim(),
                address                       = address.trim(),
                barangay                      = barangay.trim(),
                occupation                    = occupation.trim(),
                notes                         = notes.trim(),
                idDocumentUri                 = idDocumentUri,
                billingAddressDocumentUri     = billingAddressDocumentUri,
                idDocumentFirebasePath        = idDocumentFirebasePath,
                billingAddressFirebasePath    = billingAddressFirebasePath,
                updatedAt                     = now,
                syncStatus                    = SyncStatus.PENDING,
            )
        )
        syncDao.insertOperation(
            SyncOperationEntity(
                id             = UUID.randomUUID().toString(),
                operationType  = SyncOperationType.UPDATE_BORROWER,
                entityId       = id,
                payload        = """{"id":"$id","name":"${name.trim()}"}""",
                idempotencyKey = UUID.randomUUID().toString(),
                createdAt      = now,
            )
        )
        syncTrigger.onLocalWrite()
    }

    suspend fun archiveBorrower(id: String, activeLoansCount: Int) {
        if (activeLoansCount > 0)
            throw BorrowerValidationException(
                "Cannot archive a borrower with active loans. Close or cancel all loans first."
            )
        val existing = borrowerDao.getById(id)
            ?: throw BorrowerValidationException("Borrower not found.")
        val now = LocalDateTime.now()
        borrowerDao.update(existing.copy(isArchived = true, updatedAt = now, syncStatus = SyncStatus.PENDING))
        syncDao.insertOperation(
            SyncOperationEntity(
                id             = UUID.randomUUID().toString(),
                operationType  = SyncOperationType.ARCHIVE_BORROWER,
                entityId       = id,
                payload        = """{"id":"$id"}""",
                idempotencyKey = UUID.randomUUID().toString(),
                createdAt      = now,
            )
        )
        syncTrigger.onLocalWrite()
    }

    /**
     * Delete a borrower permanently.
     * Deletes all dependent allocations, payments, adjustments, installments, loans, and borrower.
     */
    suspend fun deleteBorrower(id: String, reason: String) {
        if (reason.isBlank())
            throw BorrowerValidationException("A reason is required to delete a borrower.")

        val existing = borrowerDao.getById(id)
            ?: throw BorrowerValidationException("Borrower not found.")

        val now = LocalDateTime.now()

        // Atomic transaction: delete dependent allocations, payments, adjustments, installments, loans, then borrower
        db.withTransaction {
            paymentDao.deleteAllocationsByBorrower(id)
            paymentDao.deleteByBorrower(id)
            loanDao.deleteAdjustmentsByBorrower(id)
            installmentDao.deleteByBorrower(id)
            loanDao.deleteByBorrower(id)
            borrowerDao.deleteById(id)

            syncDao.insertOperation(
                SyncOperationEntity(
                    id             = UUID.randomUUID().toString(),
                    operationType  = SyncOperationType.DELETE_BORROWER,
                    entityId       = id,
                    payload        = """{"id":"$id","reason":"$reason"}""",
                    idempotencyKey = UUID.randomUUID().toString(),
                    createdAt      = now,
                )
            )

            syncDao.insertActivity(
                ActivityLogEntity(
                    id              = UUID.randomUUID().toString(),
                    type            = ActivityType.SYSTEM,
                    description     = "Borrower deleted — ${existing.name} — $reason",
                    relatedEntityId = id,
                    occurredAt      = now,
                )
            )
        }

        syncTrigger.onLocalWrite()
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private fun BorrowerEntity.toDomain() = Borrower(
        id                            = id,
        name                          = name,
        contactNumber                 = contactNumber,
        preferredPaymentChannel       = preferredPaymentChannel,
        secondaryContactName          = secondaryContactName,
        secondaryContactNumber        = secondaryContactNumber,
        secondaryContactRelationship  = secondaryContactRelationship,
        governmentIdType              = governmentIdType,
        governmentIdNumber            = governmentIdNumber,
        address                       = address,
        barangay                      = barangay,
        occupation                    = occupation,
        notes                         = notes,
        idDocumentUri                 = idDocumentUri,
        billingAddressDocumentUri     = billingAddressDocumentUri,
        idDocumentFirebasePath        = idDocumentFirebasePath,
        billingAddressFirebasePath    = billingAddressFirebasePath,
        isArchived                    = isArchived,
        createdAt                     = createdAt,
        updatedAt                     = updatedAt,
        syncStatus                    = syncStatus,
        serverId                      = serverId,
    )

    private fun buildBorrowerPayload(e: BorrowerEntity): String =
        """{"id":"${e.id}","name":"${e.name}","contact":"${e.contactNumber}","preferredChannel":"${e.preferredPaymentChannel.name}","address":"${e.address}","barangay":"${e.barangay}","occupation":"${e.occupation}","secondaryContactName":"${e.secondaryContactName}","secondaryContactNumber":"${e.secondaryContactNumber}","secondaryContactRelationship":"${e.secondaryContactRelationship}","govIdType":"${e.governmentIdType}","govIdNumber":"${e.governmentIdNumber}","notes":"${e.notes}","idDocumentFirebasePath":"${e.idDocumentFirebasePath.orEmpty()}","billingAddressFirebasePath":"${e.billingAddressFirebasePath.orEmpty()}"}"""
}

class BorrowerValidationException(message: String) : Exception(message)

