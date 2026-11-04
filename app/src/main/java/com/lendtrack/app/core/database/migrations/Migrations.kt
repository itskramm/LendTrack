package com.lendtrack.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * MIGRATION 1 → 2
 *
 * Adds the new optional borrower fields derived from real debtors data:
 * - preferred_payment_channel (GCash, Cash, GoTyme, China Bank, Maribank…)
 * - secondary_contact_name, secondary_contact_number, secondary_contact_relationship
 * - government_id_type, government_id_number
 * - barangay, occupation
 *
 * All columns use empty string defaults so existing rows are unaffected.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE borrowers ADD COLUMN preferred_payment_channel TEXT NOT NULL DEFAULT 'GCASH'")
        db.execSQL("ALTER TABLE borrowers ADD COLUMN secondary_contact_name TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE borrowers ADD COLUMN secondary_contact_number TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE borrowers ADD COLUMN secondary_contact_relationship TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE borrowers ADD COLUMN government_id_type TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE borrowers ADD COLUMN government_id_number TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE borrowers ADD COLUMN barangay TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE borrowers ADD COLUMN occupation TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * MIGRATION 2 → 3
 *
 * Simplifies loan interest to amount-based only by removing percentage-based fields:
 * - Removes interest_rate column
 * - Removes interest_method column
 * - Keeps interest_amount (the actual fixed interest charge)
 * - Maps num_installments column to number_of_installments
 *
 * All existing loan data preserved. SQLite doesn't support DROP COLUMN directly,
 * so we create a new table, copy data, drop old table, and rename.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create new loans table without interest_rate and interest_method columns
        db.execSQL("""
            CREATE TABLE loans_new (
                id TEXT PRIMARY KEY NOT NULL,
                borrower_id TEXT NOT NULL,
                principal REAL NOT NULL,
                interest_amount REAL NOT NULL,
                loan_date TEXT NOT NULL,
                first_due_date TEXT NOT NULL,
                number_of_installments INTEGER NOT NULL,
                payment_frequency TEXT NOT NULL,
                custom_frequency_days INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL,
                notes TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                sync_status TEXT NOT NULL,
                server_id TEXT,
                FOREIGN KEY(borrower_id) REFERENCES borrowers(id) ON DELETE CASCADE
            )
        """)
        
        // Copy all loan data (mapping num_installments to number_of_installments)
        db.execSQL("""
            INSERT INTO loans_new (
                id, borrower_id, principal, interest_amount,
                loan_date, first_due_date, number_of_installments,
                payment_frequency, custom_frequency_days, status, notes,
                created_at, updated_at, sync_status, server_id
            )
            SELECT 
                id, borrower_id, principal, interest_amount,
                loan_date, first_due_date, num_installments,
                payment_frequency, custom_frequency_days, status, notes,
                created_at, updated_at, sync_status, server_id
            FROM loans
        """)
        
        // Drop old table
        db.execSQL("DROP TABLE loans")
        
        // Rename new table to loans
        db.execSQL("ALTER TABLE loans_new RENAME TO loans")
        
        // Recreate indices
        db.execSQL("CREATE INDEX index_loans_borrower_id ON loans(borrower_id)")
        db.execSQL("CREATE INDEX index_loans_status ON loans(status)")
        db.execSQL("CREATE INDEX index_loans_sync_status ON loans(sync_status)")
    }
}

/**
 * MIGRATION 3 → 4
 *
 * Forces Room schema validation refresh on disk for version 4.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Schema is identical to v3; this bump forces Room to validate and upgrade cleanly
    }
}

/**
 * MIGRATION 4 → 5
 *
 * Adds monthly recurring interest charges and late penalty tracking:
 * - Adds monthly_interest_amount, late_penalty_amount, late_penalty_grace_days to loans table
 * - Creates monthly_interest_charges table for recurring monthly charges
 * - Creates late_penalties table for overdue installment penalties
 *
 * All existing loan data preserved with default values (0.0, 0.0, 0).
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add new columns to loans table
        db.execSQL("ALTER TABLE loans ADD COLUMN monthly_interest_amount REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE loans ADD COLUMN late_penalty_amount REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE loans ADD COLUMN late_penalty_grace_days INTEGER NOT NULL DEFAULT 0")

        // Create monthly_interest_charges table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS monthly_interest_charges (
                id TEXT PRIMARY KEY NOT NULL,
                loan_id TEXT NOT NULL,
                borrower_id TEXT NOT NULL,
                amount REAL NOT NULL,
                charge_date TEXT NOT NULL,
                is_paid INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL,
                sync_status TEXT NOT NULL,
                FOREIGN KEY(loan_id) REFERENCES loans(id) ON DELETE CASCADE
            )
        """)

        // Create late_penalties table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS late_penalties (
                id TEXT PRIMARY KEY NOT NULL,
                loan_id TEXT NOT NULL,
                borrower_id TEXT NOT NULL,
                installment_id TEXT NOT NULL,
                amount REAL NOT NULL,
                penalty_date TEXT NOT NULL,
                reason TEXT NOT NULL,
                is_paid INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL,
                sync_status TEXT NOT NULL,
                FOREIGN KEY(loan_id) REFERENCES loans(id) ON DELETE CASCADE,
                FOREIGN KEY(installment_id) REFERENCES installments(id) ON DELETE CASCADE
            )
        """)

        // Create indices for performance
        db.execSQL("CREATE INDEX index_monthly_interest_charges_loan_id ON monthly_interest_charges(loan_id)")
        db.execSQL("CREATE INDEX index_monthly_interest_charges_borrower_id ON monthly_interest_charges(borrower_id)")
        db.execSQL("CREATE INDEX index_monthly_interest_charges_charge_date ON monthly_interest_charges(charge_date)")

        db.execSQL("CREATE INDEX index_late_penalties_loan_id ON late_penalties(loan_id)")
        db.execSQL("CREATE INDEX index_late_penalties_borrower_id ON late_penalties(borrower_id)")
        db.execSQL("CREATE INDEX index_late_penalties_installment_id ON late_penalties(installment_id)")
        db.execSQL("CREATE INDEX index_late_penalties_penalty_date ON late_penalties(penalty_date)")
    }
}

/**
 * MIGRATION 5 → 6
 *
 * Adds payment allocation tracking for monthly charges and late penalties:
 * - Adds amount_paid column to monthly_interest_charges table
 * - Adds amount_paid column to late_penalties table
 *
 * All existing records default to 0.0 (unpaid).
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add amount_paid to monthly_interest_charges
        db.execSQL("ALTER TABLE monthly_interest_charges ADD COLUMN amount_paid REAL NOT NULL DEFAULT 0.0")
        
        // Add amount_paid to late_penalties
        db.execSQL("ALTER TABLE late_penalties ADD COLUMN amount_paid REAL NOT NULL DEFAULT 0.0")
    }
}

/**
 * MIGRATION 6 → 7
 *
 * Adds document storage support to borrowers table (Phase 4):
 * - id_document_uri: local device URI for ID document photo
 * - billing_address_document_uri: local device URI for billing address document
 * - id_document_firebase_path: Firebase Storage path for synced ID document
 * - billing_address_firebase_path: Firebase Storage path for synced billing address document
 *
 * All columns are nullable as documents are optional.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE borrowers ADD COLUMN id_document_uri TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE borrowers ADD COLUMN billing_address_document_uri TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE borrowers ADD COLUMN id_document_firebase_path TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE borrowers ADD COLUMN billing_address_firebase_path TEXT DEFAULT NULL")
    }
}
