package com.lendtrack.app.core.database

import com.lendtrack.app.core.database.entities.*

/**
 * Seed data cleared — database starts empty for manual user input.
 */
object SeedData {
    val borrowers = emptyList<BorrowerEntity>()
    val loans = emptyList<LoanEntity>()
    fun generateInstallments(): List<InstallmentEntity> = emptyList()
    val payments = emptyList<PaymentEntity>()
    val activities = emptyList<ActivityLogEntity>()
}
