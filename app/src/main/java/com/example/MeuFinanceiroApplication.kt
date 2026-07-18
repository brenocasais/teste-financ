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
            allocationMovementDao = database.allocationMovementDao(),
            installmentPlanDao = database.installmentPlanDao(),
            recurrenceRuleDao = database.recurrenceRuleDao(),
            goalDao = database.goalDao(),
            notificationLogDao = database.notificationLogDao()
        )
        userPreferences = UserPreferences(this)
        authManager = AuthManager()

        // Schedule periodic notification worker (every 1 hour)
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.NOT_REQUIRED)
            .build()

        val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.data.notification.NotificationWorker>(
            1, java.util.concurrent.TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PeriodicNotificationCheck",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
