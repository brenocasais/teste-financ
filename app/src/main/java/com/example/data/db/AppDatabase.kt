package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Subcategory
import com.example.data.model.Transaction
import com.example.data.model.BudgetAllocation
import com.example.data.model.AllocationMovement
import com.example.data.model.InstallmentPlan
import com.example.data.model.RecurrenceRule
import com.example.data.model.Goal
import com.example.data.model.NotificationLog

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Drop envelope_groups table
        db.execSQL("DROP TABLE IF EXISTS `envelope_groups` ")

        // 2. Re-create categories table without envelope_group_id, adding icon and budget_rule_type
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `categories_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `archived` INTEGER NOT NULL,
                `userId` TEXT NOT NULL,
                `icon` TEXT,
                `budget_rule_type` TEXT
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `categories_new` (`id`, `name`, `archived`, `userId`)
            SELECT `id`, `name`, `archived`, `userId` FROM `categories`
        """.trimIndent())

        db.execSQL("DROP TABLE `categories` ")
        db.execSQL("ALTER TABLE `categories_new` RENAME TO `categories` ")

        // 3. Add icon column to subcategories
        db.execSQL("ALTER TABLE `subcategories` ADD COLUMN `icon` TEXT")

        // 4. Add is_paused column to goals
        db.execSQL("ALTER TABLE `goals` ADD COLUMN `is_paused` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Remove budget_rule_type column from categories table while preserving all existing data
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `categories_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `archived` INTEGER NOT NULL,
                `userId` TEXT NOT NULL,
                `icon` TEXT
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `categories_new` (`id`, `name`, `archived`, `userId`, `icon`)
            SELECT `id`, `name`, `archived`, `userId`, `icon` FROM `categories`
        """.trimIndent())

        db.execSQL("DROP TABLE `categories` ")
        db.execSQL("ALTER TABLE `categories_new` RENAME TO `categories` ")
    }
}

@Database(
    entities = [
        Account::class,
        Category::class,
        Subcategory::class,
        Transaction::class,
        BudgetAllocation::class,
        AllocationMovement::class,
        InstallmentPlan::class,
        RecurrenceRule::class,
        Goal::class,
        NotificationLog::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun subcategoryDao(): SubcategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetAllocationDao(): BudgetAllocationDao
    abstract fun allocationMovementDao(): AllocationMovementDao
    abstract fun installmentPlanDao(): InstallmentPlanDao
    abstract fun recurrenceRuleDao(): RecurrenceRuleDao
    abstract fun goalDao(): GoalDao
    abstract fun notificationLogDao(): NotificationLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meu_financeiro_database"
                )
                .addMigrations(MIGRATION_9_10, MIGRATION_10_11)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
