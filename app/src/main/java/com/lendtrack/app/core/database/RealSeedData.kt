package com.lendtrack.app.core.database

import com.lendtrack.app.core.database.entities.*

/**
 * Real seed data cleared — database starts empty for manual user input.
 */
object RealSeedData {
    val borrowers = emptyList<BorrowerEntity>()
    val loans = emptyList<LoanEntity>()
    fun generateInstallments(): List<InstallmentEntity> = emptyList()
    val payments = emptyList<PaymentEntity>()
    val activities = emptyList<ActivityLogEntity>()
}
