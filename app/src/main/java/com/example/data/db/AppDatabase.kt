package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.EnvelopeGroup
import com.example.data.model.Subcategory
import com.example.data.model.Transaction
import com.example.data.model.BudgetAllocation
import com.example.data.model.AllocationMovement

@Database(
    entities = [
        Account::class,
        EnvelopeGroup::class,
        Category::class,
        Subcategory::class,
        Transaction::class,
        BudgetAllocation::class,
        AllocationMovement::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun envelopeGroupDao(): EnvelopeGroupDao
    abstract fun categoryDao(): CategoryDao
    abstract fun subcategoryDao(): SubcategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetAllocationDao(): BudgetAllocationDao
    abstract fun allocationMovementDao(): AllocationMovementDao

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
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
