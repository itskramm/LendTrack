package com.lendtrack.app.data.repository

import com.lendtrack.app.core.database.LendTrackDatabase
import com.lendtrack.app.core.database.dao.*
import com.lendtrack.app.core.sync.SyncTrigger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideBorrowerRepository(
        db:             LendTrackDatabase,
        borrowerDao:    BorrowerDao,
        loanDao:        LoanDao,
        installmentDao: InstallmentDao,
        paymentDao:     PaymentDao,
        syncDao:        SyncDao,
        syncTrigger:    SyncTrigger,
    ): BorrowerRepository = BorrowerRepository(db, borrowerDao, loanDao, installmentDao, paymentDao, syncDao, syncTrigger)

    @Provides
    @Singleton
    fun provideLoanRepository(
        db:                      LendTrackDatabase,
        loanDao:                 LoanDao,
        installmentDao:          InstallmentDao,
        paymentDao:              PaymentDao,
        syncDao:                 SyncDao,
        monthlyInterestChargeDao: MonthlyInterestChargeDao,
        latePenaltyDao:          LatePenaltyDao,
        syncTrigger:             SyncTrigger,
    ): LoanRepository = LoanRepository(db, loanDao, installmentDao, paymentDao, syncDao, monthlyInterestChargeDao, latePenaltyDao, syncTrigger)

    @Provides
    @Singleton
    fun providePaymentRepository(
        db:             LendTrackDatabase,
        paymentDao:     PaymentDao,
        loanDao:        LoanDao,
        installmentDao: InstallmentDao,
        syncDao:        SyncDao,
        syncTrigger:    SyncTrigger,
    ): PaymentRepository = PaymentRepository(db, paymentDao, loanDao, installmentDao, syncDao, syncTrigger)
}
