package com.example.data.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MeuFinanceiroApplication

class NotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MeuFinanceiroApplication ?: return Result.failure()
        val userId = app.authManager.currentUserId
        if (userId.isNotBlank()) {
            try {
                NotificationTriggerManager.checkAndTriggerNotifications(
                    context = applicationContext,
                    repository = app.repository,
                    userPreferences = app.userPreferences,
                    userId = userId
                )
                return Result.success()
            } catch (e: Exception) {
                e.printStackTrace()
                return Result.retry()
            }
        }
        return Result.success()
    }
}
