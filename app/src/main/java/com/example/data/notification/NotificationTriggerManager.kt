package com.example.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.Manifest
import com.example.MainActivity
import com.example.data.model.*
import com.example.data.pref.UserPreferences
import com.example.data.repository.FinanceRepository
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object NotificationHelper {
    const val CHANNEL_ID = "finance_notifications"
    const val CHANNEL_NAME = "Alertas Financeiros"
    const val CHANNEL_DESC = "Notificações de alertas, faturas, parcelas e metas."

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(
        context: Context,
        id: Int,
        title: String,
        message: String,
        type: String,
        referenceId: String?,
        referenceMonth: String?
    ) {
        try {
            createNotificationChannel(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("notification_type", type)
                putExtra("reference_id", referenceId)
                putExtra("reference_month", referenceMonth)
            }

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                id,
                intent,
                pendingIntentFlags
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permission = Manifest.permission.POST_NOTIFICATIONS
                if (ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(id, builder.build())
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

object NotificationTriggerManager {

    suspend fun checkAndTriggerNotifications(
        context: Context,
        repository: FinanceRepository,
        userPreferences: UserPreferences,
        userId: String
    ) {
        if (userId.isBlank()) return

        try {
            val notifyLimits = userPreferences.notifyLimitsFlow.first()
        val notifyCreditCard = userPreferences.notifyCreditCardFlow.first()
        val notifyInstallment = userPreferences.notifyInstallmentFlow.first()
        val notifyGoal = userPreferences.notifyGoalFlow.first()
        val notifyWeeklyReview = userPreferences.notifyWeeklyReviewFlow.first()

        val calendar = Calendar.getInstance()
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.US).format(calendar.time)
        val d = calendar.get(Calendar.DAY_OF_MONTH)

        // Fetch data
        val accounts = repository.getAllAccounts(userId)
        val transactions = repository.getAllTransactions(userId)
        val budgetAllocations = repository.getAllBudgetAllocations(userId)
        val allocationMovements = repository.getAllAllocationMovements(userId)
        val goals = repository.getAllGoals(userId)
        val categories = repository.getAllCategories(userId)
        val subcategories = repository.getAllSubcategories(userId)

        // 1. LIMIT ALERTS (CATEGORIA_80 / CATEGORIA_100)
        if (notifyLimits) {
            val subAllocated = mutableMapOf<Pair<Int, Int>, Double>()
            val subSpent = mutableMapOf<Pair<Int, Int>, Double>()
            val catAllocated = mutableMapOf<Int, Double>()
            val catSpent = mutableMapOf<Int, Double>()

            // spent up to current month (despesa)
            val allExpenses = transactions.filter { it.type == "DESPESA" && it.date.length >= 7 && it.date.substring(0, 7) <= currentMonthStr }
            for (tx in allExpenses) {
                val catId = tx.category_id ?: continue
                val subId = tx.subcategory_id
                if (subId != null) {
                    val key = Pair(catId, subId)
                    subSpent[key] = (subSpent[key] ?: 0.0) + tx.value
                } else {
                    catSpent[catId] = (catSpent[catId] ?: 0.0) + tx.value
                }
            }

            // allocated up to current month (movements)
            val relevantAllocs = budgetAllocations.filter { it.month <= currentMonthStr }
            for (alloc in relevantAllocs) {
                val catId = alloc.category_id
                val subId = alloc.subcategory_id
                
                val destSum = allocationMovements.filter { it.dest_budget_allocation_id == alloc.id }.sumOf { it.amount }
                val sourceSum = allocationMovements.filter { it.source_budget_allocation_id == alloc.id }.sumOf { it.amount }
                val netAllocated = destSum - sourceSum

                if (subId != null) {
                    val key = Pair(catId, subId)
                    subAllocated[key] = (subAllocated[key] ?: 0.0) + netAllocated
                } else {
                    catAllocated[catId] = (catAllocated[catId] ?: 0.0) + netAllocated
                }
            }

            // check each category / subcategory
            for (cat in categories) {
                val catId = cat.id
                val subs = subcategories.filter { it.category_id == catId }
                
                if (subs.isNotEmpty()) {
                    for (sub in subs) {
                        val subKey = Pair(catId, sub.id)
                        val subAlloc = subAllocated[subKey] ?: 0.0
                        val subSp = subSpent[subKey] ?: 0.0
                        
                        if (subAlloc > 0.0) {
                            val ratio = subSp / subAlloc
                            val subRefId = "sub_${sub.id}"
                            if (ratio >= 1.0) {
                                triggerLimitAlert(context, repository, userId, "CATEGORIA_100", subRefId, currentMonthStr, "Limite de Categoria Atingido (100%) 🚨", "Você gastou 100% do planejado para a subcategoria ${sub.name}.")
                            } else if (ratio >= 0.8) {
                                triggerLimitAlert(context, repository, userId, "CATEGORIA_80", subRefId, currentMonthStr, "Limite de Categoria Próximo (80%) ⚠️", "Você gastou 80% do planejado para a subcategoria ${sub.name}.")
                            }
                        }
                    }
                    
                    // Parent Category consolidated
                    val catAllocSum = subs.sumOf { subAllocated[Pair(catId, it.id)] ?: 0.0 }
                    val catSpentSum = subs.sumOf { subSpent[Pair(catId, it.id)] ?: 0.0 }
                    if (catAllocSum > 0.0) {
                        val ratio = catSpentSum / catAllocSum
                        val catRefId = "cat_${cat.id}"
                        if (ratio >= 1.0) {
                            triggerLimitAlert(context, repository, userId, "CATEGORIA_100", catRefId, currentMonthStr, "Limite de Categoria Atingido (100%) 🚨", "Você gastou 100% do planejado para a categoria ${cat.name}.")
                        } else if (ratio >= 0.8) {
                            triggerLimitAlert(context, repository, userId, "CATEGORIA_80", catRefId, currentMonthStr, "Limite de Categoria Próximo (80%) ⚠️", "Você gastou 80% do planejado para a categoria ${cat.name}.")
                        }
                    }
                } else {
                    val catAllocVal = catAllocated[catId] ?: 0.0
                    val catSpentVal = catSpent[catId] ?: 0.0
                    if (catAllocVal > 0.0) {
                        val ratio = catSpentVal / catAllocVal
                        val catRefId = "cat_${cat.id}"
                        if (ratio >= 1.0) {
                            triggerLimitAlert(context, repository, userId, "CATEGORIA_100", catRefId, currentMonthStr, "Limite de Categoria Atingido (100%) 🚨", "Você gastou 100% do planejado para a categoria ${cat.name}.")
                        } else if (ratio >= 0.8) {
                            triggerLimitAlert(context, repository, userId, "CATEGORIA_80", catRefId, currentMonthStr, "Limite de Categoria Próximo (80%) ⚠️", "Você gastou 80% do planejado para a categoria ${cat.name}.")
                        }
                    }
                }
            }
        }

        // 2. CREDIT CARD DUE ALERTS (FATURA_VENCENDO)
        if (notifyCreditCard) {
            val ccAccounts = accounts.filter { it.type == "CARTAO_CREDITO" && !it.archived }
            val ccDaysBefore = userPreferences.creditCardDaysBeforeFlow.first()
            
            for (acc in ccAccounts) {
                val dueDay = 10 // default standard credit card due day
                val startDay = dueDay - ccDaysBefore
                if (d in startDay..dueDay) {
                    val daysLeft = dueDay - d
                    val msg = if (daysLeft == 0) {
                        "A fatura do seu cartão ${acc.name} vence HOJE!"
                    } else {
                        "A fatura do seu cartão ${acc.name} vence em $daysLeft dias!"
                    }
                    
                    triggerLimitAlert(
                        context = context,
                        repository = repository,
                        userId = userId,
                        type = "FATURA_VENCENDO",
                        referenceId = acc.id.toString(),
                        month = currentMonthStr,
                        title = "Fatura Vencendo 💳",
                        message = msg
                    )
                }
            }
        }

        // 3. INSTALLMENT DUE ALERTS (PARCELA_VENCENDO)
        if (notifyInstallment) {
            val plans = repository.getAllInstallmentPlans(userId)
            for (plan in plans) {
                val planCal = Calendar.getInstance().apply { timeInMillis = plan.created_at }
                val createdDay = planCal.get(Calendar.DAY_OF_MONTH)
                
                if (createdDay >= d && (createdDay - d) <= 3) {
                    val daysLeft = createdDay - d
                    val msg = if (daysLeft == 0) {
                        "A parcela de '${plan.description}' vence hoje!"
                    } else {
                        "A parcela de '${plan.description}' vence em $daysLeft dias!"
                    }
                    
                    triggerLimitAlert(
                        context = context,
                        repository = repository,
                        userId = userId,
                        type = "PARCELA_VENCENDO",
                        referenceId = plan.id.toString(),
                        month = currentMonthStr,
                        title = "Parcela Vencendo 📅",
                        message = msg
                    )
                }
            }
        }

        // 4. GOAL COMPLETED ALERTS (META_BATIDA)
        if (notifyGoal) {
            val movements = repository.getAllAllocationMovements(userId)
            for (goal in goals) {
                if (goal.archived) continue
                val destSum = movements.filter { it.dest_goal_id == goal.id }.sumOf { it.amount }
                val sourceSum = movements.filter { it.source_goal_id == goal.id }.sumOf { it.amount }
                val currentValue = destSum - sourceSum
                
                if (currentValue >= goal.target_value) {
                    triggerLimitAlert(
                        context = context,
                        repository = repository,
                        userId = userId,
                        type = "META_BATIDA",
                        referenceId = goal.id.toString(),
                        month = currentMonthStr,
                        title = "Meta Batida! 🎯",
                        message = "Parabéns! Você alcançou o objetivo de '${goal.name}' de R$ %.2f!".format(goal.target_value)
                    )
                }
            }
        }

        // 5. WEEKLY REVIEW ALERTS (REVISAO_SEMANAL)
        if (notifyWeeklyReview) {
            val reviewDay = userPreferences.weeklyReviewDayFlow.first()
            val reviewTimeStr = userPreferences.weeklyReviewTimeFlow.first()
            val parts = reviewTimeStr.split(":")
            val reviewHour = parts.getOrNull(0)?.toIntOrNull() ?: 20
            val reviewMin = parts.getOrNull(1)?.toIntOrNull() ?: 0

            val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMin = calendar.get(Calendar.MINUTE)

            if (currentDayOfWeek == reviewDay) {
                if (currentHour > reviewHour || (currentHour == reviewHour && currentMin >= reviewMin)) {
                    val weekStr = "${calendar.get(Calendar.YEAR)}-W${calendar.get(Calendar.WEEK_OF_YEAR)}"
                    val existing = repository.getNotificationLog(userId, "REVISAO_SEMANAL", "weekly_review", weekStr)
                    if (existing == null) {
                        // Calculate categories that are overspent
                        var overSpentCount = 0
                        
                        // Check subcategories spending
                        val subAllocated = mutableMapOf<Pair<Int, Int>, Double>()
                        val subSpent = mutableMapOf<Pair<Int, Int>, Double>()
                        val catAllocated = mutableMapOf<Int, Double>()
                        val catSpent = mutableMapOf<Int, Double>()

                        val allExpenses = transactions.filter { it.type == "DESPESA" && it.date.length >= 7 && it.date.substring(0, 7) <= currentMonthStr }
                        for (tx in allExpenses) {
                            val catId = tx.category_id ?: continue
                            val subId = tx.subcategory_id
                            if (subId != null) {
                                val key = Pair(catId, subId)
                                subSpent[key] = (subSpent[key] ?: 0.0) + tx.value
                            } else {
                                catSpent[catId] = (catSpent[catId] ?: 0.0) + tx.value
                            }
                        }

                        val relevantAllocs = budgetAllocations.filter { it.month <= currentMonthStr }
                        for (alloc in relevantAllocs) {
                            val catId = alloc.category_id
                            val subId = alloc.subcategory_id
                            
                            val destSum = allocationMovements.filter { it.dest_budget_allocation_id == alloc.id }.sumOf { it.amount }
                            val sourceSum = allocationMovements.filter { it.source_budget_allocation_id == alloc.id }.sumOf { it.amount }
                            val netAllocated = destSum - sourceSum

                            if (subId != null) {
                                val key = Pair(catId, subId)
                                subAllocated[key] = (subAllocated[key] ?: 0.0) + netAllocated
                            } else {
                                catAllocated[catId] = (catAllocated[catId] ?: 0.0) + netAllocated
                            }
                        }

                        for (cat in categories) {
                            val catId = cat.id
                            val subs = subcategories.filter { it.category_id == catId }
                            if (subs.isNotEmpty()) {
                                for (sub in subs) {
                                    val k = Pair(catId, sub.id)
                                    val alloc = subAllocated[k] ?: 0.0
                                    val spent = subSpent[k] ?: 0.0
                                    if (alloc > 0.0 && spent > alloc) {
                                        overSpentCount++
                                    }
                                }
                            } else {
                                val alloc = catAllocated[catId] ?: 0.0
                                val spent = catSpent[catId] ?: 0.0
                                if (alloc > 0.0 && spent > alloc) {
                                    overSpentCount++
                                }
                            }
                        }

                        // Calculate ready to assign
                        val pronto = calculateProntoParaAtribuirForMonth(
                            accounts, transactions, budgetAllocations, allocationMovements, goals, currentMonthStr
                        )

                        val msg = if (overSpentCount > 0) {
                            "Você tem $overSpentCount categorias estouradas esta semana. Pronto para Atribuir: R$ %.2f. Venha revisar!".format(pronto)
                        } else {
                            "Tudo sob controle! Nenhuma categoria estourada. Pronto para Atribuir: R$ %.2f.".format(pronto)
                        }

                        val notifId = ("REVISAO_SEMANAL".hashCode() + weekStr.hashCode()).coerceIn(1000, 99999)
                        NotificationHelper.showNotification(
                            context = context,
                            id = notifId,
                            title = "Revisão Semanal 📊",
                            message = msg,
                            type = "REVISAO_SEMANAL",
                            referenceId = "weekly_review",
                            referenceMonth = weekStr
                        )

                        repository.insertNotificationLog(
                            NotificationLog(
                                type = "REVISAO_SEMANAL",
                                reference_id = "weekly_review",
                                reference_month = weekStr,
                                userId = userId
                            )
                        )
                    }
                }
            }
        }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun triggerLimitAlert(
        context: Context,
        repository: FinanceRepository,
        userId: String,
        type: String,
        referenceId: String,
        month: String,
        title: String,
        message: String
    ) {
        val existing = repository.getNotificationLog(userId, type, referenceId, month)
        if (existing == null) {
            val notifId = (type.hashCode() + referenceId.hashCode() + month.hashCode()).coerceIn(1000, 99999)
            NotificationHelper.showNotification(
                context = context,
                id = notifId,
                title = title,
                message = message,
                type = type,
                referenceId = referenceId,
                referenceMonth = month
            )
            repository.insertNotificationLog(
                NotificationLog(
                    type = type,
                    reference_id = referenceId,
                    reference_month = month,
                    userId = userId
                )
            )
        }
    }

    private fun calculateProntoParaAtribuirForMonth(
        accounts: List<Account>,
        transactions: List<Transaction>,
        budgetAllocations: List<BudgetAllocation>,
        allocationMovements: List<AllocationMovement>,
        goals: List<Goal>,
        monthStr: String
    ): Double {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        val totalAccountBalance = accounts.sumOf { account ->
            val creditos = transactions.filter {
                it.date.length >= 7 && it.date.substring(0, 7) <= monthStr && (
                    (it.type == "RECEITA" && it.account_id == account.id) ||
                    (it.type == "TRANSFERENCIA" && it.to_account_id == account.id)
                )
            }.sumOf { it.value }

            val debitos = transactions.filter {
                it.date.length >= 7 && it.date.substring(0, 7) <= monthStr && (
                    (it.type == "DESPESA" && it.account_id == account.id) ||
                    (it.type == "TRANSFERENCIA" && it.account_id == account.id)
                )
            }.sumOf { it.value }

            account.initial_balance + creditos - debitos
        }

        val allocsInMonth = budgetAllocations.filter { it.month <= monthStr }
        val totalDisponivel = allocsInMonth.sumOf { alloc ->
            val alocado = allocationMovements.filter { it.dest_budget_allocation_id == alloc.id }.sumOf { it.amount } -
                          allocationMovements.filter { it.source_budget_allocation_id == alloc.id }.sumOf { it.amount }

            val gasto = transactions.filter {
                it.type == "DESPESA" &&
                it.date.startsWith(alloc.month) &&
                it.category_id == alloc.category_id &&
                it.subcategory_id == alloc.subcategory_id
            }.sumOf { it.value }

            alocado - gasto
        }

        val totalGoalsCurrentValue = goals.sumOf { goal ->
            val destSum = allocationMovements.filter {
                it.dest_goal_id == goal.id &&
                sdf.format(java.util.Date(it.moved_at)) <= monthStr
            }.sumOf { it.amount }

            val sourceSum = allocationMovements.filter {
                it.source_goal_id == goal.id &&
                sdf.format(java.util.Date(it.moved_at)) <= monthStr
            }.sumOf { it.amount }

            destSum - sourceSum
        }

        return totalAccountBalance - totalDisponivel - totalGoalsCurrentValue
    }
}
