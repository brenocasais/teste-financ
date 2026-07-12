package com.example

import android.app.Application
import com.google.firebase.FirebaseApp
import com.example.data.auth.AuthManager
import com.example.data.db.AppDatabase
import com.example.data.pref.UserPreferences
import com.example.data.repository.FinanceRepository

class MeuFinanceiroApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: FinanceRepository
        private set

    lateinit var userPreferences: UserPreferences
        private set

    lateinit var authManager: AuthManager
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        database = AppDatabase.getDatabase(this)
        repository = FinanceRepository(
            accountDao = database.accountDao(),
            envelopeGroupDao = database.envelopeGroupDao(),
            categoryDao = database.categoryDao(),
            subcategoryDao = database.subcategoryDao(),
            transactionDao = database.transactionDao(),
            budgetAllocationDao = database.budgetAllocationDao(),
            allocationMovementDao = database.allocationMovementDao()
        )
        userPreferences = UserPreferences(this)
        authManager = AuthManager()
    }
}
