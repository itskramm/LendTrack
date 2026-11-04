package com.lendtrack.app.core.database

import android.content.Context
import androidx.room.Room
import com.lendtrack.app.core.database.migrations.MIGRATION_1_2
import com.lendtrack.app.core.database.migrations.MIGRATION_2_3
import com.lendtrack.app.core.database.migrations.MIGRATION_3_4
import com.lendtrack.app.core.database.migrations.MIGRATION_4_5
import com.lendtrack.app.core.database.migrations.MIGRATION_5_6
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LendTrackDatabase =
        Room.databaseBuilder(
            context,
            LendTrackDatabase::class.java,
            LendTrackDatabase.DATABASE_NAME,
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .addCallback(SeedDatabaseCallback())
            .build()

    @Provides fun provideBorrowerDao(db: LendTrackDatabase)    = db.borrowerDao()
    @Provides fun provideLoanDao(db: LendTrackDatabase)        = db.loanDao()
    @Provides fun provideInstallmentDao(db: LendTrackDatabase) = db.installmentDao()
    @Provides fun providePaymentDao(db: LendTrackDatabase)     = db.paymentDao()
    @Provides fun provideSyncDao(db: LendTrackDatabase)        = db.syncDao()
    @Provides fun provideMonthlyInterestChargeDao(db: LendTrackDatabase) = db.monthlyInterestChargeDao()
    @Provides fun provideLatePenaltyDao(db: LendTrackDatabase) = db.latePenaltyDao()
}

/**
 * Seeds the database with sample data on first creation.
 * Runs in a background thread automatically by Room.
 */
private class SeedDatabaseCallback : androidx.room.RoomDatabase.Callback() {
    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        super.onCreate(db)
    }
}
