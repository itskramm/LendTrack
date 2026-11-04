package com.lendtrack.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lendtrack.app.core.database.dao.*
import com.lendtrack.app.core.database.entities.*

@Database(
    entities = [
        BorrowerEntity::class,
        LoanEntity::class,
        InstallmentEntity::class,
        PaymentEntity::class,
        PaymentAllocationEntity::class,
        LoanAdjustmentEntity::class,
        SyncOperationEntity::class,
        ActivityLogEntity::class,
        MonthlyInterestChargeEntity::class,
        LatePenaltyEntity::class,
    ],
    version = 6,
    exportSchema = true,    // schema exported to app/schemas/ for migration review
)
@TypeConverters(Converters::class)
abstract class LendTrackDatabase : RoomDatabase() {
    abstract fun borrowerDao(): BorrowerDao
    abstract fun loanDao(): LoanDao
    abstract fun installmentDao(): InstallmentDao
    abstract fun paymentDao(): PaymentDao
    abstract fun syncDao(): SyncDao
    abstract fun monthlyInterestChargeDao(): MonthlyInterestChargeDao
    abstract fun latePenaltyDao(): LatePenaltyDao

    companion object {
        const val DATABASE_NAME = "lendtrack.db"
    }
}
