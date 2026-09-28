package com.example.sparely.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** Current schema version. Bump together with a new migration added to ALL_MIGRATIONS. */
const val SPARELY_DATABASE_VERSION = 46

@Database(
    entities = [
        ExpenseEntity::class,
        SavingsTransferEntity::class,
        CategoryBudgetEntity::class,
        RecurringExpenseEntity::class,
        SavingsChallengeEntity::class,
        ChallengeMilestoneEntity::class,
        AchievementEntity::class,
        SavingsAccountEntity::class,
        SavingsAccountTransactionEntity::class,
        SmartVaultEntity::class,
        VaultScheduleEntity::class,
        VaultContributionEntity::class,
        VaultBalanceAdjustmentEntity::class,
        FrozenFundEntity::class,
        AllocationHistoryEntity::class,
        MainAccountTransactionEntity::class,
        StoreEntity::class,
        PaymentMethodEntity::class,
        CreditCardPaymentEntity::class,
        TransactionVaultContributionCrossRef::class,
        ExpenseItemEntity::class,
        ExpenseRefundEntity::class,
        AssetEntity::class,
        AssetExpenseLinkEntity::class,
        WishlistEntity::class,
        WishlistSavingsEntity::class,
        PendingVariableRecurringExpenseEntity::class,
        RecurringExpensePaidEntity::class
    ],

    version = SPARELY_DATABASE_VERSION,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SparelyDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun transferDao(): SavingsTransferDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun achievementDao(): AchievementDao
    abstract fun savingsAccountDao(): SavingsAccountDao
    abstract fun smartVaultDao(): SmartVaultDao
    abstract fun allocationHistoryDao(): AllocationHistoryDao
    abstract fun mainAccountDao(): MainAccountDao
    abstract fun frozenFundDao(): FrozenFundDao
    abstract fun storeDao(): StoreDao
    abstract fun paymentMethodDao(): PaymentMethodDao
    abstract fun creditCardPaymentDao(): CreditCardPaymentDao
    abstract fun expenseItemDao(): ExpenseItemDao
    abstract fun expenseRefundDao(): ExpenseRefundDao
    abstract fun assetDao(): AssetDao
    abstract fun assetExpenseLinkDao(): AssetExpenseLinkDao
    abstract fun wishlistDao(): WishlistDao
    abstract fun wishlistSavingsDao(): WishlistSavingsDao
    abstract fun savingsAccountTransactionDao(): SavingsAccountTransactionDao
    abstract fun pendingVariableRecurringExpenseDao(): PendingVariableRecurringExpenseDao
    abstract fun recurringExpensePaidDao(): RecurringExpensePaidDao

    companion object {
        const val DATABASE_VERSION = SPARELY_DATABASE_VERSION
        const val DATABASE_NAME = "sparely.db"

        @Volatile
        private var INSTANCE: SparelyDatabase? = null

        fun getInstance(context: Context): SparelyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): SparelyDatabase {
            preserveDatabaseBeforeDowngrade(context)
            return Room.databaseBuilder(
                context,
                SparelyDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(*ALL_MIGRATIONS)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
        }

        /**
         * Room can't open a database written by a newer app version, so on a downgrade it
         * recreates it empty. Copy the existing files aside first so the user's data is never
         * lost: reinstalling the newer version (or support) can recover it from the copy.
         */
        private fun preserveDatabaseBeforeDowngrade(context: Context) {
            try {
                val dbFile = context.getDatabasePath(DATABASE_NAME)
                if (!dbFile.exists()) return
                val storedVersion = android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                ).use { it.version }
                if (storedVersion <= DATABASE_VERSION) return
                val suffix = ".v$storedVersion-${System.currentTimeMillis()}.bak"
                listOf("", "-wal", "-shm").forEach { ext ->
                    val source = java.io.File(dbFile.path + ext)
                    if (source.exists()) source.copyTo(java.io.File(dbFile.path + ext + suffix), overwrite = true)
                }
                android.util.Log.w("SparelyDatabase", "Downgrade from v$storedVersion: data copied to *$suffix")
            } catch (e: Exception) {
                android.util.Log.e("SparelyDatabase", "Could not check/preserve database before opening", e)
            }
        }

        // Migrations from earlier versions to v12
        // Since we don\'t have schema exports for earlier versions and the previous approach
        // used .fallbackToDestructiveMigration(), we\'ll provide migrations that attempt
        // to preserve existing data while adding new columns/tables as needed.
        // All migrations apply the same schema updates to reach v12.

        private val migrateToV12: (androidx.sqlite.db.SupportSQLiteDatabase) -> Unit = { database ->
            // Helper to detect existing columns (PRAGMA table_info)
            fun hasColumn(tableName: String, columnName: String): Boolean {
                val cursor = database.query("PRAGMA table_info($tableName)")
                cursor.use { c ->
                    val nameIndex = c.getColumnIndex("name")
                    while (c.moveToNext()) {
                        val existing = c.getString(nameIndex)
                        if (existing == columnName) return true
                    }
                }
                return false
            }

            fun hasTable(tableName: String): Boolean {
                val cursor = database.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'")
                val exists = cursor.count > 0
                cursor.close()
                return exists
            }

            fun addColumnIfMissing(tableName: String, sql: String, columnName: String) {
                if (!hasColumn(tableName, columnName)) {
                    database.execSQL(sql)
                }
            }

            // Ensure smart_vaults table exists (create if needed for very old versions)
            if (!hasTable("smart_vaults")) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS smart_vaults (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "targetAmount REAL NOT NULL, " +
                        "currentBalance REAL NOT NULL, " +
                        "targetDate INTEGER, " +
                        "startDate INTEGER, " +
                        "endDate INTEGER, " +
                        "monthlyNeed REAL, " +
                        "priorityWeight REAL NOT NULL DEFAULT 1.0, " +
                        "autoSaveEnabled INTEGER NOT NULL DEFAULT 1, " +
                        "priority TEXT, " +
                        "type TEXT, " +
                        "interestRate REAL, " +
                        "allocationMode TEXT, " +
                        "manualAllocationPercent REAL, " +
                        "nextExpectedContribution REAL, " +
                        "lastContributionDate INTEGER, " +
                        "savingTaxRateOverride REAL, " +
                        "archived INTEGER NOT NULL DEFAULT 0, " +
                        "accountType TEXT, " +
                        "accountNumber TEXT, " +
                        "accountNotes TEXT, " +
                        // createdAt defaults to 0 (epoch: Jan 1, 1970) for migrated vaults
                        // This is acceptable as it only affects display/sorting, not functionality
                        "createdAt INTEGER NOT NULL DEFAULT 0)"
                )
            } else {
                // Add missing columns to existing table
                addColumnIfMissing("smart_vaults", "ALTER TABLE smart_vaults ADD COLUMN startDate INTEGER", "startDate")
                addColumnIfMissing("smart_vaults", "ALTER TABLE smart_vaults ADD COLUMN endDate INTEGER", "endDate")
                addColumnIfMissing("smart_vaults", "ALTER TABLE smart_vaults ADD COLUMN monthlyNeed REAL", "monthlyNeed")
                addColumnIfMissing("smart_vaults", "ALTER TABLE smart_vaults ADD COLUMN priorityWeight REAL NOT NULL DEFAULT 1.0", "priorityWeight")
                addColumnIfMissing("smart_vaults", "ALTER TABLE smart_vaults ADD COLUMN autoSaveEnabled INTEGER NOT NULL DEFAULT 1", "autoSaveEnabled")
                addColumnIfMissing("smart_vaults", "ALTER TABLE smart_vaults ADD COLUMN priority TEXT", "priority")
                addColumnIfMissing("smart_vaults", "ALTER TABLE smart_vaults ADD COLUMN type TEXT", "type")
                addColumnIfMissing("smart_vaults", "ALTER TABLE smart_vaults ADD COLUMN interestRate REAL", "interestRate")
                addColumnIfMissing("smart_vaults", "ALTER TABLE smart_vaults ADD COLUMN allocationMode TEXT", "allocationMode")
                addColumnIfMissing("smart_vaults", "ALTER TABLE smart_vaults ADD COLUMN manualAllocationPercent REAL", "manualAllocationPercent")
                addColumnIfMissing("smart_vaults", "ALTER TABLE smart_vaults ADD COLUMN nextExpectedContribution REAL", "nextExpectedContribution")
                addColumnIfMissing("smart_vaults", "ALTER TABLE smart_vaults ADD COLUMN lastContributionDate INTEGER", "lastContributionDate")
                addColumnIfMissing("smart_vaults", "ALTER TABLE smart_vaults ADD COLUMN savingTaxRateOverride REAL", "savingTaxRateOverride")
                addColumnIfMissing("smart_vaults", "ALTER TABLE smart_vaults ADD COLUMN archived INTEGER NOT NULL DEFAULT 0", "archived")
                addColumnIfMissing("smart_vaults", "ALTER TABLE smart_vaults ADD COLUMN accountType TEXT", "accountType")
                addColumnIfMissing("smart_vaults", "ALTER TABLE smart_vaults ADD COLUMN accountNumber TEXT", "accountNumber")
                addColumnIfMissing("smart_vaults", "ALTER TABLE smart_vaults ADD COLUMN accountNotes TEXT", "accountNotes")
                addColumnIfMissing("smart_vaults", "ALTER TABLE smart_vaults ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0", "createdAt")
            }

            // Ensure vault_auto_deposits table exists
            if (!hasTable("vault_auto_deposits")) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS vault_auto_deposits (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "vaultId INTEGER NOT NULL, " +
                        "amount REAL NOT NULL, " +
                        "frequency TEXT NOT NULL, " +
                        "startDate INTEGER NOT NULL, " +
                        "endDate INTEGER, " +
                        "sourceAccountId INTEGER, " +
                        "lastExecutionDate INTEGER, " +
                        "active INTEGER NOT NULL, " +
                        "executeAutomatically INTEGER NOT NULL DEFAULT 0, " +
                        "FOREIGN KEY(vaultId) REFERENCES smart_vaults(id) ON DELETE CASCADE)"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_vault_auto_deposits_vaultId ON vault_auto_deposits(vaultId)")
            } else {
                addColumnIfMissing("vault_auto_deposits", "ALTER TABLE vault_auto_deposits ADD COLUMN executeAutomatically INTEGER NOT NULL DEFAULT 0", "executeAutomatically")
            }

            // Ensure vault_contributions table exists
            if (!hasTable("vault_contributions")) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS vault_contributions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "vaultId INTEGER NOT NULL, " +
                        "amount REAL NOT NULL, " +
                        "date INTEGER NOT NULL, " +
                        "source TEXT NOT NULL, " +
                        "note TEXT, " +
                        "reconciled INTEGER NOT NULL DEFAULT 0, " +
                        "FOREIGN KEY(vaultId) REFERENCES smart_vaults(id) ON DELETE CASCADE)"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_vault_contributions_vaultId ON vault_contributions(vaultId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_vault_contributions_date ON vault_contributions(date)")
            }

            // Ensure vault_balance_adjustments table exists
            if (!hasTable("vault_balance_adjustments")) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS vault_balance_adjustments (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "vaultId INTEGER NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "delta REAL NOT NULL, " +
                        "resultingBalance REAL NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "reason TEXT, " +
                        "FOREIGN KEY(vaultId) REFERENCES smart_vaults(id) ON DELETE CASCADE)"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_vault_balance_adjustments_vaultId ON vault_balance_adjustments(vaultId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_vault_balance_adjustments_createdAt ON vault_balance_adjustments(createdAt)")
            }

            // Add executeAutomatically to recurring_expenses if the table exists
            if (hasTable("recurring_expenses")) {
                addColumnIfMissing("recurring_expenses", "ALTER TABLE recurring_expenses ADD COLUMN executeAutomatically INTEGER NOT NULL DEFAULT 0", "executeAutomatically")
            }

            // Create allocation_history table
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS allocation_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "vaultId INTEGER NOT NULL, " +
                    "amount REAL NOT NULL, " +
                    "date INTEGER NOT NULL, " +
                    "source TEXT, " +
                    "note TEXT, " +
                    "FOREIGN KEY(vaultId) REFERENCES smart_vaults(id) ON DELETE CASCADE)"
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_allocation_history_vaultId ON allocation_history(vaultId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_allocation_history_date ON allocation_history(date)")

            // Create frozen_funds table
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS frozen_funds (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "pendingType TEXT NOT NULL, " +
                    "pendingId INTEGER NOT NULL, " +
                    "amount REAL NOT NULL, " +
                    "createdAt INTEGER NOT NULL, " +
                    "description TEXT)"
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_frozen_funds_pending ON frozen_funds(pendingType, pendingId)")
        }

        val MIGRATION_1_12 = object : androidx.room.migration.Migration(1, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = migrateToV12(db)
        }

        val MIGRATION_2_12 = object : androidx.room.migration.Migration(2, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = migrateToV12(db)
        }

        val MIGRATION_3_12 = object : androidx.room.migration.Migration(3, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = migrateToV12(db)
        }

        val MIGRATION_4_12 = object : androidx.room.migration.Migration(4, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = migrateToV12(db)
        }

        val MIGRATION_5_12 = object : androidx.room.migration.Migration(5, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = migrateToV12(db)
        }

        val MIGRATION_6_12 = object : androidx.room.migration.Migration(6, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = migrateToV12(db)
        }

        val MIGRATION_7_12 = object : androidx.room.migration.Migration(7, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = migrateToV12(db)
        }

        val MIGRATION_8_12 = object : androidx.room.migration.Migration(8, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = migrateToV12(db)
        }

        val MIGRATION_9_12 = object : androidx.room.migration.Migration(9, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = migrateToV12(db)
        }

        val MIGRATION_10_12 = object : androidx.room.migration.Migration(10, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = migrateToV12(db)
        }

        // Migration: add new columns introduced in v12 (SmartVault additions and related fields)
        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = migrateToV12(db)
        }

        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE smart_vaults ADD COLUMN excludedFromAutoAllocation INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE vault_auto_deposits ADD COLUMN dayOfMonth INTEGER")
                db.execSQL("ALTER TABLE vault_auto_deposits ADD COLUMN dayOfWeek INTEGER")
                db.execSQL("ALTER TABLE vault_auto_deposits ADD COLUMN customIntervalDays INTEGER")
                db.execSQL("ALTER TABLE vault_auto_deposits ADD COLUMN nextRunAt INTEGER")
            }
        }

        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                fun hasColumn(tableName: String, columnName: String): Boolean {
                    val cursor = db.query("PRAGMA table_info($tableName)")
                    cursor.use { c ->
                        val nameIndex = c.getColumnIndex("name")
                        while (c.moveToNext()) {
                            if (c.getString(nameIndex) == columnName) return true
                        }
                    }
                    return false
                }

                val hadExcludedColumn = hasColumn("smart_vaults", "excludedFromAutoAllocation")

                if (hasColumn("smart_vaults", "allow_auto_income").not()) {
                    db.execSQL("ALTER TABLE smart_vaults ADD COLUMN allow_auto_income INTEGER NOT NULL DEFAULT 1")
                }
                if (hadExcludedColumn) {
                    db.execSQL(
                        "UPDATE smart_vaults SET allow_auto_income = CASE WHEN excludedFromAutoAllocation = 1 THEN 0 ELSE 1 END"
                    )
                }
                if (hasColumn("smart_vaults", "default_manual_deposit_deduct_main").not()) {
                    db.execSQL("ALTER TABLE smart_vaults ADD COLUMN default_manual_deposit_deduct_main INTEGER NOT NULL DEFAULT 1")
                }
                if (hasColumn("smart_vaults", "default_manual_withdraw_add_main").not()) {
                    db.execSQL("ALTER TABLE smart_vaults ADD COLUMN default_manual_withdraw_add_main INTEGER NOT NULL DEFAULT 1")
                }

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS smart_vaults_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "targetAmount REAL NOT NULL, " +
                        "currentBalance REAL NOT NULL, " +
                        "targetDate INTEGER, " +
                        "startDate INTEGER, " +
                        "endDate INTEGER, " +
                        "monthlyNeed REAL, " +
                        "priorityWeight REAL NOT NULL, " +
                        "autoSaveEnabled INTEGER NOT NULL, " +
                        "allow_auto_income INTEGER NOT NULL DEFAULT 1, " +
                        "priority TEXT NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "interestRate REAL, " +
                        "allocationMode TEXT NOT NULL, " +
                        "manualAllocationPercent REAL, " +
                        "nextExpectedContribution REAL, " +
                        "lastContributionDate INTEGER, " +
                        "savingTaxRateOverride REAL, " +
                        "archived INTEGER NOT NULL, " +
                        "accountType TEXT, " +
                        "accountNumber TEXT, " +
                        "accountNotes TEXT, " +
                        "createdAt INTEGER NOT NULL, " +
                        "default_manual_deposit_deduct_main INTEGER NOT NULL DEFAULT 1, " +
                        "default_manual_withdraw_add_main INTEGER NOT NULL DEFAULT 1" +
                        ")"
                )

                db.execSQL(
                    "INSERT INTO smart_vaults_new (" +
                        "id, name, targetAmount, currentBalance, targetDate, startDate, endDate, monthlyNeed, priorityWeight, autoSaveEnabled, " +
                        "allow_auto_income, priority, type, interestRate, allocationMode, manualAllocationPercent, nextExpectedContribution, " +
                        "lastContributionDate, savingTaxRateOverride, archived, accountType, accountNumber, accountNotes, createdAt, " +
                        "default_manual_deposit_deduct_main, default_manual_withdraw_add_main" +
                        ") " +
                        "SELECT " +
                        "id, name, targetAmount, currentBalance, targetDate, startDate, endDate, monthlyNeed, priorityWeight, autoSaveEnabled, " +
                        "COALESCE(allow_auto_income, 1), priority, type, interestRate, allocationMode, manualAllocationPercent, nextExpectedContribution, " +
                        "lastContributionDate, savingTaxRateOverride, archived, accountType, accountNumber, accountNotes, " +
                        "COALESCE(createdAt, CAST(strftime('%s','now') / 86400 AS INTEGER)), " +
                        "COALESCE(default_manual_deposit_deduct_main, 1), COALESCE(default_manual_withdraw_add_main, 1) " +
                        "FROM smart_vaults"
                )

                db.execSQL("PRAGMA foreign_keys=OFF")
                db.execSQL("DROP TABLE smart_vaults")
                db.execSQL("ALTER TABLE smart_vaults_new RENAME TO smart_vaults")
                db.execSQL("PRAGMA foreign_keys=ON")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS vault_schedules (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "vaultId INTEGER NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "amount REAL, " +
                        "percentage REAL, " +
                        "direction TEXT NOT NULL, " +
                        "dateValue INTEGER, " +
                        "repeatAnnually INTEGER NOT NULL DEFAULT 0, " +
                        "dayOfMonth INTEGER, " +
                        "dayOfWeek INTEGER, " +
                        "weekInterval INTEGER, " +
                        "onlyIfBalanceAvailable INTEGER NOT NULL DEFAULT 1, " +
                        "notifyBefore INTEGER NOT NULL DEFAULT 0, " +
                        "notifyAfter INTEGER NOT NULL DEFAULT 0, " +
                        "notifyOnFailure INTEGER NOT NULL DEFAULT 1, " +
                        "nextRunAt INTEGER, " +
                        "lastRunAt INTEGER, " +
                        "enabled INTEGER NOT NULL DEFAULT 1, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(vaultId) REFERENCES smart_vaults(id) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_schedules_vaultId ON vault_schedules(vaultId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_schedules_enabled ON vault_schedules(enabled)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_schedules_nextRunAt ON vault_schedules(nextRunAt)")

                val nowMillis = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO vault_schedules (" +
                        "vaultId, type, amount, percentage, direction, dateValue, repeatAnnually, dayOfMonth, dayOfWeek, weekInterval, onlyIfBalanceAvailable, notifyBefore, notifyAfter, notifyOnFailure, nextRunAt, lastRunAt, enabled, createdAt, updatedAt" +
                        ") " +
                        "SELECT " +
                        "vaultId, " +
                        "CASE frequency WHEN 'WEEKLY' THEN 'DAY_OF_WEEK' WHEN 'BIWEEKLY' THEN 'DAY_OF_WEEK' ELSE 'DAY_OF_MONTH' END AS type, " +
                        "amount, " +
                        "NULL AS percentage, " +
                        "'MAIN_TO_VAULT' AS direction, " +
                        "NULL AS dateValue, " +
                        "0 AS repeatAnnually, " +
                        "CASE WHEN frequency = 'MONTHLY' THEN COALESCE(dayOfMonth, (startDate % 30) + 1) ELSE NULL END AS dayOfMonth, " +
                        "CASE WHEN frequency IN ('WEEKLY','BIWEEKLY') THEN COALESCE(dayOfWeek, ((startDate + 4) % 7) + 1) ELSE NULL END AS dayOfWeek, " +
                        "CASE WHEN frequency = 'BIWEEKLY' THEN 2 ELSE 1 END AS weekInterval, " +
                        "1 AS onlyIfBalanceAvailable, " +
                        "0 AS notifyBefore, " +
                        "0 AS notifyAfter, " +
                        "1 AS notifyOnFailure, " +
                        "CASE WHEN nextRunAt IS NOT NULL THEN nextRunAt * 86400 ELSE NULL END AS nextRunAt, " +
                        "CASE WHEN lastExecutionDate IS NOT NULL THEN lastExecutionDate * 86400 ELSE NULL END AS lastRunAt, " +
                        "active, " +
                        "CASE WHEN startDate IS NOT NULL THEN startDate * 86400000 ELSE $nowMillis END AS createdAt, " +
                        "CASE WHEN startDate IS NOT NULL THEN startDate * 86400000 ELSE $nowMillis END AS updatedAt " +
                        "FROM vault_auto_deposits"
                )

                db.execSQL("DROP TABLE IF EXISTS vault_auto_deposits")
            }
        }

        val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE smart_vaults ADD COLUMN iconName TEXT")
            }
        }

        val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create stores table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS stores (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "iconName TEXT, " +
                        "createdAt INTEGER NOT NULL" +
                        ")"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stores_name ON stores(name)")

                // Add storeId column to expenses table
                db.execSQL("ALTER TABLE expenses ADD COLUMN storeId INTEGER")
            }
        }

        val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add websiteUrl column to stores table (nullable, preserves existing data)
                db.execSQL("ALTER TABLE stores ADD COLUMN websiteUrl TEXT")
            }
        }

        val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add storeId column to recurring_expenses table for store selection
                db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN storeId INTEGER")
                // Add isRecurring column to expenses table
                db.execSQL("ALTER TABLE expenses ADD COLUMN isRecurring INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create payment_methods table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS payment_methods (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "defaultDeductFromMainAccount INTEGER NOT NULL, " +
                        "isDefault INTEGER NOT NULL, " +
                        "iconName TEXT" +
                        ")"
                )

                // Seed default payment methods
                // Cash: deduct = 0 (false), type = CASH
                db.execSQL(
                    "INSERT INTO payment_methods (name, type, defaultDeductFromMainAccount, isDefault, iconName) " +
                        "VALUES ('Cash', 'CASH', 0, 1, 'payments')"
                )
                // Card: deduct = 1 (true), type = CARD
                db.execSQL(
                    "INSERT INTO payment_methods (name, type, defaultDeductFromMainAccount, isDefault, iconName) " +
                        "VALUES ('Card', 'CARD', 1, 0, 'credit_card')"
                )

                // Add paymentMethodId to expenses table
                db.execSQL("ALTER TABLE expenses ADD COLUMN paymentMethodId INTEGER")

                // Add paymentMethodId to recurring_expenses table
                db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN paymentMethodId INTEGER")
            }
        }

        val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add credit card fields to payment_methods table
                db.execSQL("ALTER TABLE payment_methods ADD COLUMN isCreditCard INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE payment_methods ADD COLUMN creditLimit REAL")
                db.execSQL("ALTER TABLE payment_methods ADD COLUMN currentBalance REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE payment_methods ADD COLUMN billingCycleDay INTEGER")
                db.execSQL("ALTER TABLE payment_methods ADD COLUMN lastPaymentDate INTEGER")
                db.execSQL("ALTER TABLE payment_methods ADD COLUMN lastPaymentAmount REAL")

                // Create credit_card_payments table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS credit_card_payments (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "paymentMethodId INTEGER NOT NULL, " +
                        "amount REAL NOT NULL, " +
                        "date INTEGER NOT NULL, " +
                        "note TEXT, " +
                        "FOREIGN KEY(paymentMethodId) REFERENCES payment_methods(id) ON DELETE CASCADE" +
                        ")"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_credit_card_payments_paymentMethodId ON credit_card_payments(paymentMethodId)")
            }
        }

        val MIGRATION_20_21 = object : androidx.room.migration.Migration(20, 21) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Create the cross-reference table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS transaction_vault_contribution_cross_ref (" +
                        "transactionId INTEGER NOT NULL, " +
                        "contributionId INTEGER NOT NULL, " +
                        "PRIMARY KEY(transactionId, contributionId), " +
                        "FOREIGN KEY(transactionId) REFERENCES main_account_transactions(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(contributionId) REFERENCES vault_contributions(id) ON DELETE CASCADE" +
                        ")"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_txn_vault_ref_txnId ON transaction_vault_contribution_cross_ref(transactionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_txn_vault_ref_contribId ON transaction_vault_contribution_cross_ref(contributionId)")

                // 2. Migrate data from main_account_transactions.relatedVaultContributionIds
                // Use CTE to split comma-separated IDs and insert into cross-ref table
                db.execSQL("""
                    WITH RECURSIVE split(transactionId, contribId, rest) AS (
                      SELECT id, '', relatedVaultContributionIds || ',' FROM main_account_transactions WHERE relatedVaultContributionIds IS NOT NULL AND relatedVaultContributionIds != ''
                      UNION ALL
                      SELECT transactionId,
                             substr(rest, 1, instr(rest, ',') - 1),
                             substr(rest, instr(rest, ',') + 1)
                      FROM split
                      WHERE rest != ''
                    )
                    INSERT INTO transaction_vault_contribution_cross_ref (transactionId, contributionId)
                    SELECT transactionId, CAST(contribId AS INTEGER)
                    FROM split
                    WHERE contribId != '' AND contribId GLOB '[0-9]*';
                """)

                // 3. Remove relatedVaultContributionIds column from main_account_transactions
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS main_account_transactions_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "amount REAL NOT NULL, " +
                        "balanceAfter REAL NOT NULL, " +
                        "timestamp INTEGER NOT NULL, " +
                        "description TEXT NOT NULL, " +
                        "relatedExpenseId INTEGER" +
                        ")"
                )
                
                db.execSQL(
                    "INSERT INTO main_account_transactions_new (id, type, amount, balanceAfter, timestamp, description, relatedExpenseId) " +
                        "SELECT id, type, amount, balanceAfter, timestamp, description, relatedExpenseId FROM main_account_transactions"
                )
                
                db.execSQL("DROP TABLE main_account_transactions")
                db.execSQL("ALTER TABLE main_account_transactions_new RENAME TO main_account_transactions")
            }
        }

        // Migration 21->22: Add missing Foreign Key Constraints to Entities
        val MIGRATION_21_22 = object : androidx.room.migration.Migration(21, 22) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // We'll turn off foreign keys for the duration of the migration to avoid conflict during drop tables
                db.execSQL("PRAGMA foreign_keys=OFF")

                // 1. RECREATE EXPENSES TABLE WITH FOREIGN KEYS
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS expenses_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "description TEXT NOT NULL, " +
                        "amount REAL NOT NULL, " +
                        "category TEXT NOT NULL, " +
                        "date INTEGER NOT NULL, " +
                        "includesTax INTEGER NOT NULL, " +
                        "emergencyAmount REAL NOT NULL, " +
                        "investmentAmount REAL NOT NULL, " +
                        "funAmount REAL NOT NULL, " +
                        "safeInvestmentAmount REAL NOT NULL, " +
                        "highRiskInvestmentAmount REAL NOT NULL, " +
                        "autoRecommended INTEGER NOT NULL, " +
                        "appliedPercentEmergency REAL NOT NULL, " +
                        "appliedPercentInvest REAL NOT NULL, " +
                        "appliedPercentFun REAL NOT NULL, " +
                        "appliedSafeSplit REAL NOT NULL, " +
                        "riskLevelUsed TEXT NOT NULL, " +
                        "deductedFromVaultId INTEGER, " +
                        "storeId INTEGER, " +
                        "paymentMethodId INTEGER, " +
                        "isRecurring INTEGER NOT NULL DEFAULT 0, " +
                        "FOREIGN KEY(deductedFromVaultId) REFERENCES smart_vaults(id) ON DELETE SET NULL, " +
                        "FOREIGN KEY(storeId) REFERENCES stores(id) ON DELETE SET NULL, " +
                        "FOREIGN KEY(paymentMethodId) REFERENCES payment_methods(id) ON DELETE SET NULL" +
                        ")"
                )
                // Copy Data
                db.execSQL("INSERT INTO expenses_new SELECT * FROM expenses")
                db.execSQL("DROP TABLE expenses")
                db.execSQL("ALTER TABLE expenses_new RENAME TO expenses")
                // Recreate Indices
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_deductedFromVaultId ON expenses(deductedFromVaultId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_storeId ON expenses(storeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_paymentMethodId ON expenses(paymentMethodId)")


                // 2. RECREATE RECURRING_EXPENSES TABLE WITH FOREIGN KEYS
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS recurring_expenses_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "description TEXT NOT NULL, " +
                        "amount REAL NOT NULL, " +
                        "category TEXT NOT NULL, " +
                        "frequency TEXT NOT NULL, " +
                        "startDate INTEGER NOT NULL, " +
                        "endDate INTEGER, " +
                        "lastProcessedDate INTEGER, " +
                        "isActive INTEGER NOT NULL, " +
                        "autoLog INTEGER NOT NULL, " +
                        "executeAutomatically INTEGER NOT NULL, " +
                        "reminderDaysBefore INTEGER NOT NULL, " +
                        "merchantName TEXT, " +
                        "notes TEXT, " +
                        "storeId INTEGER, " +
                        "paymentMethodId INTEGER, " +
                        "includesTax INTEGER NOT NULL, " +
                        "deductFromMainAccount INTEGER NOT NULL, " +
                        "deductedFromVaultId INTEGER, " +
                        "manualPercentEmergency REAL, " +
                        "manualPercentInvest REAL, " +
                        "manualPercentFun REAL, " +
                        "manualSafeSplit REAL, " +
                        "FOREIGN KEY(deductedFromVaultId) REFERENCES smart_vaults(id) ON DELETE SET NULL, " +
                        "FOREIGN KEY(storeId) REFERENCES stores(id) ON DELETE SET NULL, " +
                        "FOREIGN KEY(paymentMethodId) REFERENCES payment_methods(id) ON DELETE SET NULL" +
                        ")"
                )
                db.execSQL("INSERT INTO recurring_expenses_new SELECT * FROM recurring_expenses")
                db.execSQL("DROP TABLE recurring_expenses")
                db.execSQL("ALTER TABLE recurring_expenses_new RENAME TO recurring_expenses")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_expenses_deductedFromVaultId ON recurring_expenses(deductedFromVaultId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_expenses_storeId ON recurring_expenses(storeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_expenses_paymentMethodId ON recurring_expenses(paymentMethodId)")


                // 3. RECREATE SAVINGS_TRANSFERS TABLE WITH FOREIGN KEYS
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS savings_transfers_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "category TEXT NOT NULL, " +
                        "amount REAL NOT NULL, " +
                        "date INTEGER NOT NULL, " +
                        "sourceAccountId INTEGER, " +
                        "destinationAccountId INTEGER, " +
                        "note TEXT, " +
                        "FOREIGN KEY(sourceAccountId) REFERENCES savings_accounts(id) ON DELETE SET NULL, " +
                        "FOREIGN KEY(destinationAccountId) REFERENCES savings_accounts(id) ON DELETE SET NULL" +
                        ")"
                )
                db.execSQL("INSERT INTO savings_transfers_new SELECT * FROM savings_transfers")
                db.execSQL("DROP TABLE savings_transfers")
                db.execSQL("ALTER TABLE savings_transfers_new RENAME TO savings_transfers")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_transfers_sourceAccountId ON savings_transfers(sourceAccountId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_transfers_destinationAccountId ON savings_transfers(destinationAccountId)")


                // 4. RECREATE MAIN_ACCOUNT_TRANSACTIONS WITH FOREIGN KEYS
                // Note: The structure must match the ONE CREATED IN MIGRATION 20_21 + the new FK
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS main_account_transactions_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "amount REAL NOT NULL, " +
                        "balanceAfter REAL NOT NULL, " +
                        "timestamp INTEGER NOT NULL, " +
                        "description TEXT NOT NULL, " +
                        "relatedExpenseId INTEGER, " +
                        "FOREIGN KEY(relatedExpenseId) REFERENCES expenses(id) ON DELETE SET NULL" +
                        ")"
                )
                db.execSQL("INSERT INTO main_account_transactions_new SELECT * FROM main_account_transactions")
                // Dropping main_account_transactions might normally trigger CASCADE on its children (transaction_vault_contribution_cross_ref)
                // But we set PRAGMA foreign_keys=OFF so we should be safe.
                db.execSQL("DROP TABLE main_account_transactions")
                db.execSQL("ALTER TABLE main_account_transactions_new RENAME TO main_account_transactions")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_main_account_transactions_relatedExpenseId ON main_account_transactions(relatedExpenseId)")
                
                // Turn foreign keys back on
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        // Migration 22->23: Add notes column to expenses table
        val MIGRATION_22_23 = object : androidx.room.migration.Migration(22, 23) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN notes TEXT")
            }
        }

        // Migration 23->24: Add incomeCategory column to main_account_transactions table
        val MIGRATION_23_24 = object : androidx.room.migration.Migration(23, 24) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE main_account_transactions ADD COLUMN incomeCategory TEXT")
            }
        }

        // Migration 24->25: Add refund fields to expenses and relatedExpenseId to vault_contributions
        val MIGRATION_24_25 = object : androidx.room.migration.Migration(24, 25) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Add columns to expenses table
                db.execSQL("ALTER TABLE expenses ADD COLUMN refundedAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE expenses ADD COLUMN isRefunded INTEGER NOT NULL DEFAULT 0")

                // 2. Add relatedExpenseId to vault_contributions table
                // Since SQLite doesn't support adding columns with foreign keys directly in ALTER TABLE in a standard way that Room always likes,
                // and we want to be safe, we'll recreate the table.
                
                db.execSQL("PRAGMA foreign_keys=OFF")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS vault_contributions_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "vaultId INTEGER NOT NULL, " +
                        "amount REAL NOT NULL, " +
                        "date INTEGER NOT NULL, " +
                        "source TEXT NOT NULL, " +
                        "note TEXT, " +
                        "reconciled INTEGER NOT NULL, " +
                        "relatedExpenseId INTEGER, " +
                        "FOREIGN KEY(vaultId) REFERENCES smart_vaults(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(relatedExpenseId) REFERENCES expenses(id) ON DELETE SET NULL" +
                        ")"
                )
                
                db.execSQL(
                    "INSERT INTO vault_contributions_new (id, vaultId, amount, date, source, note, reconciled) " +
                        "SELECT id, vaultId, amount, date, source, note, reconciled FROM vault_contributions"
                )
                
                db.execSQL("DROP TABLE vault_contributions")
                db.execSQL("ALTER TABLE vault_contributions_new RENAME TO vault_contributions")
                
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_contributions_vaultId ON vault_contributions(vaultId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_contributions_date ON vault_contributions(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_contributions_relatedExpenseId ON vault_contributions(relatedExpenseId)")
                
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        // Migration 25->26: Add expense_items table for line items
        val MIGRATION_25_26 = object : androidx.room.migration.Migration(25, 26) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS expense_items (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "expenseId INTEGER NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "quantity INTEGER NOT NULL DEFAULT 1, " +
                        "unitPrice REAL NOT NULL, " +
                        "totalPrice REAL NOT NULL, " +
                        "FOREIGN KEY(expenseId) REFERENCES expenses(id) ON DELETE CASCADE" +
                        ")"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_items_expenseId ON expense_items(expenseId)")
            }
        }

        // Migration 26->27: Add orderNumber column to expenses
        val MIGRATION_26_27 = object : androidx.room.migration.Migration(26, 27) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN orderNumber TEXT")
            }
        }
        
        // Migration 27->28: Add variable amount support for recurring expenses and high-yield savings for vaults
        val MIGRATION_27_28 = object : androidx.room.migration.Migration(27, 28) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add variable amount fields to recurring_expenses table
                db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN isVariableAmount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN amountHistoryJson TEXT")
                db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN estimatedAmount REAL")
                
                // Add high-yield savings fields to smart_vaults table
                db.execSQL("ALTER TABLE smart_vaults ADD COLUMN isHighYieldAccount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE smart_vaults ADD COLUMN annualPercentageYield REAL")
                db.execSQL("ALTER TABLE smart_vaults ADD COLUMN lastInterestCalculation INTEGER")
                db.execSQL("ALTER TABLE smart_vaults ADD COLUMN accruedInterest REAL NOT NULL DEFAULT 0.0")
            }
        }

        // Migration 28->29: Add nextRunAt column to recurring_expenses
        val MIGRATION_28_29 = object : androidx.room.migration.Migration(28, 29) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN nextRunAt INTEGER")
            }
        }

        // Migration 29->30: Recreate savings_accounts table with HISA-focused schema
        val MIGRATION_29_30 = object : androidx.room.migration.Migration(29, 30) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")
                
                // Create new table with HISA schema
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS savings_accounts_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "currentBalance REAL NOT NULL DEFAULT 0.0, " +
                        "annualPercentageYield REAL NOT NULL DEFAULT 0.0, " +
                        "totalInterestEarned REAL NOT NULL DEFAULT 0.0, " +
                        "lastInterestEntryDate INTEGER, " +
                        "accountNumber TEXT, " +
                        "institution TEXT, " +
                        "accountNotes TEXT, " +
                        "isMainOverflowAccount INTEGER NOT NULL DEFAULT 0, " +
                        "iconName TEXT, " +
                        "createdAt INTEGER NOT NULL, " +
                        "archived INTEGER NOT NULL DEFAULT 0" +
                        ")"
                )
                
                // Migrate existing data (preserve id, name, currentBalance, accountNumber)
                db.execSQL(
                    "INSERT INTO savings_accounts_new (id, name, currentBalance, accountNumber, institution, createdAt) " +
                        "SELECT id, name, currentBalance, accountNumber, institution, " +
                        "CAST(strftime('%s','now') / 86400 AS INTEGER) FROM savings_accounts"
                )
                
                db.execSQL("DROP TABLE savings_accounts")
                db.execSQL("ALTER TABLE savings_accounts_new RENAME TO savings_accounts")
                
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        // Migration 30->31: Add savings_account_transactions table for HISA transaction history
        val MIGRATION_30_31 = object : androidx.room.migration.Migration(30, 31) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS savings_account_transactions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "accountId INTEGER NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "amount REAL NOT NULL, " +
                        "balanceAfter REAL NOT NULL, " +
                        "timestamp INTEGER NOT NULL, " +
                        "description TEXT NOT NULL, " +
                        "relatedMainAccountTransactionId INTEGER, " +
                        "FOREIGN KEY(accountId) REFERENCES savings_accounts(id) ON DELETE CASCADE" +
                        ")"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_account_transactions_accountId ON savings_account_transactions(accountId)")
            }
        }

        // Migration 31->32: Add savingsAccountId to vault_contributions for HISA pending transfers
        val MIGRATION_31_32 = object : androidx.room.migration.Migration(31, 32) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")
                
                // Recreate vault_contributions table with nullable vaultId and new savingsAccountId
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS vault_contributions_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "vaultId INTEGER, " +
                        "amount REAL NOT NULL, " +
                        "date INTEGER NOT NULL, " +
                        "source TEXT NOT NULL, " +
                        "note TEXT, " +
                        "reconciled INTEGER NOT NULL, " +
                        "relatedExpenseId INTEGER, " +
                        "savingsAccountId INTEGER, " +
                        "FOREIGN KEY(relatedExpenseId) REFERENCES expenses(id) ON DELETE SET NULL, " +
                        "FOREIGN KEY(savingsAccountId) REFERENCES savings_accounts(id) ON DELETE CASCADE" +
                        ")"
                )
                
                // Copy existing data
                db.execSQL(
                    "INSERT INTO vault_contributions_new (id, vaultId, amount, date, source, note, reconciled, relatedExpenseId) " +
                        "SELECT id, vaultId, amount, date, source, note, reconciled, relatedExpenseId FROM vault_contributions"
                )
                
                db.execSQL("DROP TABLE vault_contributions")
                db.execSQL("ALTER TABLE vault_contributions_new RENAME TO vault_contributions")
                
                // Recreate indices
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_contributions_vaultId ON vault_contributions(vaultId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_contributions_date ON vault_contributions(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_contributions_relatedExpenseId ON vault_contributions(relatedExpenseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_contributions_savingsAccountId ON vault_contributions(savingsAccountId)")
                
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        // Migration 32->33: Add term/notice/withdrawal rule columns to savings_accounts
        val MIGRATION_32_33 = object : androidx.room.migration.Migration(32, 33) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE savings_accounts ADD COLUMN productType TEXT NOT NULL DEFAULT 'FLEXIBLE'")
                db.execSQL("ALTER TABLE savings_accounts ADD COLUMN termMonths INTEGER")
                db.execSQL("ALTER TABLE savings_accounts ADD COLUMN noticeDays INTEGER")
                db.execSQL("ALTER TABLE savings_accounts ADD COLUMN gracePeriodDays INTEGER")
                db.execSQL("ALTER TABLE savings_accounts ADD COLUMN anniversaryWindowDays INTEGER")
                db.execSQL("ALTER TABLE savings_accounts ADD COLUMN minWithdrawalAmount REAL")
                db.execSQL("ALTER TABLE savings_accounts ADD COLUMN minRemainingBalance REAL")
                db.execSQL("ALTER TABLE savings_accounts ADD COLUMN earlyWithdrawalPenaltyDays INTEGER")
            }
        }

        // Migration 33->34: Add termStartDate to savings_accounts
        val MIGRATION_33_34 = object : androidx.room.migration.Migration(33, 34) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE savings_accounts ADD COLUMN termStartDate INTEGER")
            }
        }

        // Migration 34->35: Add new tables for refunds, assets, and wishlists
        val MIGRATION_34_35 = object : androidx.room.migration.Migration(34, 35) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create expense_refunds table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS expense_refunds (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "expenseId INTEGER NOT NULL, " +
                        "refundedAmount REAL NOT NULL, " +
                        "refundDate INTEGER NOT NULL, " +
                        "refundMethod TEXT, " +
                        "reason TEXT, " +
                        "refundedItemIds TEXT, " +
                        "FOREIGN KEY(expenseId) REFERENCES expenses(id) ON DELETE CASCADE" +
                        ")"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_refunds_expenseId ON expense_refunds(expenseId)")

                // Create assets table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS assets (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "description TEXT, " +
                        "category TEXT NOT NULL, " +
                        "icon TEXT, " +
                        "createdAt INTEGER NOT NULL, " +
                        "archived INTEGER NOT NULL DEFAULT 0, " +
                        "metadata TEXT" +
                        ")"
                )

                // Create asset_expense_links table (junction table)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS asset_expense_links (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "assetId INTEGER NOT NULL, " +
                        "expenseId INTEGER NOT NULL, " +
                        "percentageAllocated REAL NOT NULL, " +
                        "linkedAt INTEGER NOT NULL, " +
                        "UNIQUE(assetId, expenseId), " +
                        "FOREIGN KEY(assetId) REFERENCES assets(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(expenseId) REFERENCES expenses(id) ON DELETE CASCADE" +
                        ")"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_asset_expense_links_assetId_expenseId ON asset_expense_links(assetId, expenseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_expense_links_assetId ON asset_expense_links(assetId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_expense_links_expenseId ON asset_expense_links(expenseId)")

                // Create wishlists table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS wishlists (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "description TEXT NOT NULL, " +
                        "targetAmount REAL NOT NULL, " +
                        "currentSavings REAL NOT NULL DEFAULT 0.0, " +
                        "priority INTEGER NOT NULL DEFAULT 1, " +
                        "createdAt INTEGER NOT NULL, " +
                        "cooldownExpiresAt INTEGER, " +
                        "isReallyNeeded INTEGER NOT NULL DEFAULT 1, " +
                        "category TEXT, " +
                        "notes TEXT, " +
                        "imageData BLOB, " +
                        "imageUrl TEXT, " +
                        "archived INTEGER NOT NULL DEFAULT 0" +
                        ")"
                )

                // Create wishlist_savings table (tracks allocations to wishlists)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS wishlist_savings (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "wishlistId INTEGER NOT NULL, " +
                        "amount REAL NOT NULL, " +
                        "date INTEGER NOT NULL, " +
                        "source TEXT, " +
                        "FOREIGN KEY(wishlistId) REFERENCES wishlists(id) ON DELETE CASCADE" +
                        ")"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_wishlist_savings_wishlistId ON wishlist_savings(wishlistId)")
            }
        }

        // Migration 35->36: Add assetPrice column to assets table
        val MIGRATION_35_36 = object : androidx.room.migration.Migration(35, 36) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assets ADD COLUMN assetPrice REAL NOT NULL DEFAULT 0.0")
            }
        }

        // Migration 36->37: Add type field to expenses, recurring_expenses, and expense_items
        val MIGRATION_36_37 = object : androidx.room.migration.Migration(36, 37) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add type column to expenses table
                db.execSQL("ALTER TABLE expenses ADD COLUMN type TEXT NOT NULL DEFAULT 'PRODUCT'")

                // Add type column to recurring_expenses table
                db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN type TEXT NOT NULL DEFAULT 'PRODUCT'")

                // Add type column to expense_items table
                db.execSQL("ALTER TABLE expense_items ADD COLUMN type TEXT NOT NULL DEFAULT 'PRODUCT'")
            }
        }

        val MIGRATION_37_38 = object : androidx.room.migration.Migration(37, 38) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add assetAllocationsJson column to recurring_expenses table for storing asset links
                db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN assetAllocationsJson TEXT")
            }
        }

        val MIGRATION_38_39 = object : androidx.room.migration.Migration(38, 39) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add creatorExpenseId column to assets table for tracking the purchase that created the asset
                db.execSQL("ALTER TABLE assets ADD COLUMN creatorExpenseId INTEGER")
            }
        }

        val MIGRATION_39_40 = object : androidx.room.migration.Migration(39, 40) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create pending_variable_recurring_expenses table for variable recurring expenses awaiting user input
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pending_variable_recurring_expenses (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "recurringExpenseId INTEGER NOT NULL, " +
                        "predictedAmount REAL NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "UNIQUE(recurringExpenseId), " +
                        "FOREIGN KEY(recurringExpenseId) REFERENCES recurring_expenses(id) ON DELETE CASCADE" +
                        ")"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_variable_recurring_expenses_recurringExpenseId ON pending_variable_recurring_expenses(recurringExpenseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_variable_recurring_expenses_createdAt ON pending_variable_recurring_expenses(createdAt)")
            }
        }

        val MIGRATION_40_41 = object : androidx.room.migration.Migration(40, 41) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create recurring_expenses_paid table to track early payments of recurring expenses
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS recurring_expenses_paid (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "recurringExpenseId INTEGER NOT NULL, " +
                        "expenseId INTEGER, " +
                        "dueDate INTEGER NOT NULL, " +
                        "paidDate INTEGER NOT NULL, " +
                        "amountPaid REAL NOT NULL, " +
                        "notes TEXT, " +
                        "createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(recurringExpenseId) REFERENCES recurring_expenses(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(expenseId) REFERENCES expenses(id) ON DELETE SET NULL" +
                        ")"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_expenses_paid_recurringExpenseId ON recurring_expenses_paid(recurringExpenseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_expenses_paid_expenseId ON recurring_expenses_paid(expenseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_expenses_paid_dueDate ON recurring_expenses_paid(dueDate)")
            }
        }

        val MIGRATION_41_42 = object : androidx.room.migration.Migration(41, 42) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add indices to expenses table
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_date ON expenses(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_category ON expenses(category)")

                // Add indices to recurring_expenses table
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_expenses_nextRunAt ON recurring_expenses(nextRunAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_expenses_isActive ON recurring_expenses(isActive)")
            }
        }

        val MIGRATION_42_43 = object : androidx.room.migration.Migration(42, 43) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add isIgnored column to expenses table
                db.execSQL("ALTER TABLE expenses ADD COLUMN isIgnored INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migration 43->44: Add missing FK (vaultId -> smart_vaults, CASCADE) to vault_contributions
        // so deleting a vault cleanly removes its contribution history instead of orphaning it.
        val MIGRATION_43_44 = object : androidx.room.migration.Migration(43, 44) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")

                // Drop contributions that already point at a non-existent vault (pre-existing
                // orphans from before this FK existed) so the new FK constraint isn't violated.
                db.execSQL(
                    "DELETE FROM vault_contributions WHERE vaultId IS NOT NULL " +
                        "AND vaultId NOT IN (SELECT id FROM smart_vaults)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS vault_contributions_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "vaultId INTEGER, " +
                        "amount REAL NOT NULL, " +
                        "date INTEGER NOT NULL, " +
                        "source TEXT NOT NULL, " +
                        "note TEXT, " +
                        "reconciled INTEGER NOT NULL, " +
                        "relatedExpenseId INTEGER, " +
                        "savingsAccountId INTEGER, " +
                        "FOREIGN KEY(vaultId) REFERENCES smart_vaults(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(relatedExpenseId) REFERENCES expenses(id) ON DELETE SET NULL, " +
                        "FOREIGN KEY(savingsAccountId) REFERENCES savings_accounts(id) ON DELETE CASCADE" +
                        ")"
                )

                db.execSQL(
                    "INSERT INTO vault_contributions_new (id, vaultId, amount, date, source, note, reconciled, relatedExpenseId, savingsAccountId) " +
                        "SELECT id, vaultId, amount, date, source, note, reconciled, relatedExpenseId, savingsAccountId FROM vault_contributions"
                )

                db.execSQL("DROP TABLE vault_contributions")
                db.execSQL("ALTER TABLE vault_contributions_new RENAME TO vault_contributions")

                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_contributions_vaultId ON vault_contributions(vaultId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_contributions_date ON vault_contributions(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_contributions_relatedExpenseId ON vault_contributions(relatedExpenseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_contributions_savingsAccountId ON vault_contributions(savingsAccountId)")

                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        val MIGRATION_44_45 = object : androidx.room.migration.Migration(44, 45) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN necessity TEXT")
            }
        }

        /**
         * Every migration, in order. The builder uses this list and a unit test checks it forms
         * a complete path from every old version to [DATABASE_VERSION], so a forgotten migration
         * fails the build instead of reaching users.
         */
        val ALL_MIGRATIONS: Array<androidx.room.migration.Migration> by lazy {
            arrayOf(
            MIGRATION_1_12,
            MIGRATION_2_12,
            MIGRATION_3_12,
            MIGRATION_4_12,
            MIGRATION_5_12,
            MIGRATION_6_12,
            MIGRATION_7_12,
            MIGRATION_8_12,
            MIGRATION_9_12,
            MIGRATION_10_12,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
            MIGRATION_20_21,
            MIGRATION_21_22,
            MIGRATION_22_23,
            MIGRATION_23_24,
            MIGRATION_24_25,
            MIGRATION_25_26,
            MIGRATION_26_27,
            MIGRATION_27_28,
            MIGRATION_28_29,
            MIGRATION_29_30,
            MIGRATION_30_31,
            MIGRATION_31_32,
            MIGRATION_32_33,
            MIGRATION_33_34,
            MIGRATION_34_35,
            MIGRATION_35_36,
            MIGRATION_36_37,
            MIGRATION_37_38,
            MIGRATION_38_39,
            MIGRATION_39_40,
            MIGRATION_40_41,
            MIGRATION_41_42,
            MIGRATION_42_43,
            MIGRATION_43_44,
            MIGRATION_44_45,
            MIGRATION_45_46
            )
        }

        val MIGRATION_45_46 = object : androidx.room.migration.Migration(45, 46) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vault_balance_adjustments ADD COLUMN relatedExpenseId INTEGER")
            }
        }
    }
}
