package com.example.data.repository

import android.util.Log
import com.example.data.db.*
import com.example.data.model.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import java.util.Date

class FinanceRepository(
    private val accountDao: AccountDao,
    private val envelopeGroupDao: EnvelopeGroupDao,
    private val categoryDao: CategoryDao,
    private val subcategoryDao: SubcategoryDao,
    private val transactionDao: TransactionDao,
    private val budgetAllocationDao: BudgetAllocationDao,
    private val allocationMovementDao: AllocationMovementDao,
    private val installmentPlanDao: InstallmentPlanDao,
    private val recurrenceRuleDao: RecurrenceRuleDao,
    private val goalDao: GoalDao,
    private val notificationLogDao: NotificationLogDao
) {
    // --- NOTIFICATION LOGS ---
    suspend fun getNotificationLog(userId: String, type: String, referenceId: String?, referenceMonth: String?): NotificationLog? =
        notificationLogDao.getLog(userId, type, referenceId, referenceMonth)

    suspend fun insertNotificationLog(log: NotificationLog) =
        notificationLogDao.insert(log)

    suspend fun getAllNotificationLogs(userId: String): List<NotificationLog> =
        notificationLogDao.getAllLogs(userId)

    // --- RECURRENCE RULES ---
    fun getRecurrenceRulesFlow(userId: String): Flow<List<RecurrenceRule>> = recurrenceRuleDao.getRecurrenceRulesFlow(userId)
    suspend fun getAllRecurrenceRules(userId: String): List<RecurrenceRule> = recurrenceRuleDao.getAllRecurrenceRules(userId)
    suspend fun getRecurrenceRuleById(id: Int): RecurrenceRule? = recurrenceRuleDao.getRecurrenceRuleById(id)
    suspend fun insertRecurrenceRule(rule: RecurrenceRule): Long = recurrenceRuleDao.insert(rule)
    suspend fun updateRecurrenceRule(rule: RecurrenceRule) = recurrenceRuleDao.update(rule)
    suspend fun deleteRecurrenceRule(rule: RecurrenceRule) = recurrenceRuleDao.delete(rule)

    suspend fun createRecurrenceRule(rule: RecurrenceRule): Long {
        val ruleId = recurrenceRuleDao.insert(rule)
        val ruleWithId = rule.copy(id = ruleId.toInt())
        materializeTransactionsForRule(ruleWithId)
        return ruleId
    }

    suspend fun materializeRecurrenceTransactions(userId: String) {
        val activeRules = recurrenceRuleDao.getAllRecurrenceRules(userId).filter { it.active }
        for (rule in activeRules) {
            materializeTransactionsForRule(rule)
        }
    }

    private suspend fun materializeTransactionsForRule(rule: RecurrenceRule) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
        val sdfDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        
        // Get the window of months to materialize (e.g. from 2 months ago to 36 months in the future)
        val windowMonths = getMaterializationWindowMonths()
        
        // Get existing materialized transactions for this rule
        val existingTxs = transactionDao.getAllTransactions(rule.userId)
            .filter { it.recurrence_rule_id == rule.id }
            
        // We extract the exact day of month from start_date
        if (rule.start_date.length < 10) return
        val startYear = rule.start_date.substring(0, 4).toIntOrNull() ?: 2026
        val startMonth = rule.start_date.substring(5, 7).toIntOrNull() ?: 1
        val day = rule.start_date.substring(8, 10).toIntOrNull() ?: 1
        
        val transactionsToInsert = mutableListOf<Transaction>()
        
        val ruleStartMonthStr = rule.start_date.substring(0, 7) // YYYY-MM
        
        for (monthStr in windowMonths) {
            // Check boundaries
            if (monthStr < ruleStartMonthStr) continue
            if (rule.end_month != null && monthStr > rule.end_month) continue
            
            // Check if this month matches the recurrence frequency and interval
            val currentYear = monthStr.substring(0, 4).toIntOrNull() ?: 2026
            val currentMonth = monthStr.substring(5, 7).toIntOrNull() ?: 1
            
            val diffMonths = (currentYear - startYear) * 12 + (currentMonth - startMonth)
            if (diffMonths < 0) continue
            
            val matches = when (rule.frequency) {
                "ANUAL" -> diffMonths % (rule.frequency_interval * 12) == 0
                else -> diffMonths % rule.frequency_interval == 0 // Default to MENSAL
            }
            if (!matches) continue
            
            // Check if already materialized for this month
            val alreadyExists = existingTxs.any { it.date.startsWith(monthStr) }
            if (alreadyExists) continue
            
            // Construct the date
            val cal = java.util.Calendar.getInstance()
            try {
                val parsedDate = sdf.parse(monthStr) ?: java.util.Date()
                cal.time = parsedDate
            } catch (e: Exception) {
                // ignore
            }
            cal.set(java.util.Calendar.DAY_OF_MONTH, day)
            val maxDay = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            if (day > maxDay) {
                cal.set(java.util.Calendar.DAY_OF_MONTH, maxDay)
            }
            
            val transactionDateStr = sdfDate.format(cal.time)
            
            transactionsToInsert.add(
                Transaction(
                    account_id = rule.account_id,
                    to_account_id = null,
                    category_id = rule.category_id,
                    subcategory_id = rule.subcategory_id,
                    type = rule.type,
                    value = rule.value,
                    description = rule.description,
                    date = transactionDateStr,
                    installment_plan_id = null,
                    installment_number = null,
                    recurrence_rule_id = rule.id,
                    is_recurrence_override = false,
                    userId = rule.userId
                )
            )
        }
        
        if (transactionsToInsert.isNotEmpty()) {
            transactionDao.insertAll(transactionsToInsert)
        }
    }

    private fun getMaterializationWindowMonths(): List<String> {
        val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
        val cal = java.util.Calendar.getInstance()
        val months = mutableListOf<String>()
        cal.add(java.util.Calendar.MONTH, -2) // include past 2 months
        for (i in 0..38) { // from -2 months to +36 months (3 years) ahead
            months.add(sdf.format(cal.time))
            cal.add(java.util.Calendar.MONTH, 1)
        }
        return months
    }

    // --- INSTALLMENT PLANS ---
    fun getInstallmentPlansFlow(userId: String): Flow<List<InstallmentPlan>> = installmentPlanDao.getInstallmentPlansFlow(userId)
    suspend fun getAllInstallmentPlans(userId: String): List<InstallmentPlan> = installmentPlanDao.getAllInstallmentPlans(userId)
    suspend fun getInstallmentPlanById(id: Int): InstallmentPlan? = installmentPlanDao.getInstallmentPlanById(id)
    suspend fun insertInstallmentPlan(plan: InstallmentPlan): Long = installmentPlanDao.insert(plan)
    suspend fun deleteInstallmentPlan(plan: InstallmentPlan) = installmentPlanDao.delete(plan)

    suspend fun createInstallmentPlan(plan: InstallmentPlan): Long {
        val planId = installmentPlanDao.insert(plan)
        val count = plan.installments_count
        if (count <= 0) return planId

        val baseValue = (plan.total_value / count).toBigDecimal().setScale(2, java.math.RoundingMode.DOWN).toDouble()
        val totalDistributed = baseValue * (count - 1)
        val lastValue = (plan.total_value - totalDistributed).toBigDecimal().setScale(2, java.math.RoundingMode.HALF_UP).toDouble()

        val transactions = mutableListOf<Transaction>()
        val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
        val cal = java.util.Calendar.getInstance()
        try {
            val date = sdf.parse(plan.first_installment_month) ?: java.util.Date()
            cal.time = date
        } catch (e: Exception) {
            // ignore
        }

        val dayCal = java.util.Calendar.getInstance()
        dayCal.timeInMillis = plan.created_at
        val day = dayCal.get(java.util.Calendar.DAY_OF_MONTH)

        for (i in 1..count) {
            val value = if (i == count) lastValue else baseValue
            
            cal.set(java.util.Calendar.DAY_OF_MONTH, day)
            val maxDay = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            if (day > maxDay) {
                cal.set(java.util.Calendar.DAY_OF_MONTH, maxDay)
            }

            val transactionDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)

            transactions.add(
                Transaction(
                    account_id = plan.account_id,
                    to_account_id = null,
                    category_id = plan.category_id,
                    subcategory_id = plan.subcategory_id,
                    type = "DESPESA",
                    value = value,
                    description = "${plan.description} (${i}/${count})",
                    date = transactionDateStr,
                    installment_plan_id = planId.toInt(),
                    installment_number = i,
                    recurrence_rule_id = null,
                    is_recurrence_override = false,
                    userId = plan.userId
                )
            )
            cal.add(java.util.Calendar.MONTH, 1)
        }

        transactionDao.insertAll(transactions)
        return planId
    }

    // --- BUDGET ALLOCATIONS ---
    fun getBudgetAllocationsFlow(userId: String): Flow<List<BudgetAllocation>> = budgetAllocationDao.getBudgetAllocationsFlow(userId)
    fun getBudgetAllocationsForMonthFlow(userId: String, month: String): Flow<List<BudgetAllocation>> = budgetAllocationDao.getBudgetAllocationsForMonthFlow(userId, month)
    suspend fun getAllBudgetAllocations(userId: String): List<BudgetAllocation> = budgetAllocationDao.getAllBudgetAllocations(userId)
    suspend fun getBudgetAllocationsForMonth(userId: String, month: String): List<BudgetAllocation> = budgetAllocationDao.getBudgetAllocationsForMonth(userId, month)
    suspend fun getBudgetAllocation(categoryId: Int, subcategoryId: Int?, month: String, userId: String): BudgetAllocation? = budgetAllocationDao.getBudgetAllocation(categoryId, subcategoryId, month, userId)
    suspend fun insertBudgetAllocation(budgetAllocation: BudgetAllocation): Long = budgetAllocationDao.insert(budgetAllocation)
    suspend fun updateBudgetAllocation(budgetAllocation: BudgetAllocation) = budgetAllocationDao.update(budgetAllocation)
    suspend fun deleteBudgetAllocation(budgetAllocation: BudgetAllocation) = budgetAllocationDao.delete(budgetAllocation)

    // --- ALLOCATION MOVEMENTS ---
    fun getAllocationMovementsFlow(userId: String): Flow<List<AllocationMovement>> = allocationMovementDao.getAllocationMovementsFlow(userId)
    suspend fun getAllAllocationMovements(userId: String): List<AllocationMovement> = allocationMovementDao.getAllAllocationMovements(userId)
    suspend fun insertAllocationMovement(movement: AllocationMovement): Long = allocationMovementDao.insert(movement)
    suspend fun updateAllocationMovement(movement: AllocationMovement) = allocationMovementDao.update(movement)
    suspend fun deleteAllocationMovement(movement: AllocationMovement) = allocationMovementDao.delete(movement)

    // --- GOALS ---
    fun getGoalsFlow(userId: String): Flow<List<Goal>> = goalDao.getGoalsFlow(userId)
    suspend fun getAllGoals(userId: String): List<Goal> = goalDao.getAllGoals(userId)
    suspend fun getGoalById(id: Int): Goal? = goalDao.getGoalById(id)
    suspend fun insertGoal(goal: Goal): Long = goalDao.insert(goal)
    suspend fun updateGoal(goal: Goal) = goalDao.update(goal)
    suspend fun deleteGoal(goal: Goal) = goalDao.delete(goal)

    // --- ACCOUNTS ---
    fun getAccountsFlow(userId: String): Flow<List<Account>> = accountDao.getAccountsFlow(userId)
    suspend fun getAllAccounts(userId: String): List<Account> = accountDao.getAllAccounts(userId)
    suspend fun insertAccount(account: Account): Long = accountDao.insert(account)
    suspend fun updateAccount(account: Account) = accountDao.update(account)
    suspend fun deleteAccount(account: Account) = accountDao.delete(account)

    // --- TRANSACTIONS ---
    fun getTransactionsFlow(userId: String): Flow<List<Transaction>> = transactionDao.getTransactionsFlow(userId)
    suspend fun getAllTransactions(userId: String): List<Transaction> = transactionDao.getAllTransactions(userId)
    suspend fun getTransactionById(id: Int): Transaction? = transactionDao.getTransactionById(id)
    suspend fun insertTransaction(transaction: Transaction): Long = transactionDao.insert(transaction)
    suspend fun updateTransaction(transaction: Transaction) = transactionDao.update(transaction)
    suspend fun deleteTransaction(transaction: Transaction) = transactionDao.delete(transaction)

    suspend fun updateRecurrenceRuleAndFuture(rule: RecurrenceRule, fromMonthStr: String) {
        recurrenceRuleDao.update(rule)
        
        val txs = transactionDao.getAllTransactions(rule.userId)
            .filter { it.recurrence_rule_id == rule.id }
            
        val toDelete = txs.filter { 
            val txMonth = it.date.take(7)
            txMonth >= fromMonthStr && !it.is_recurrence_override
        }
        
        for (tx in toDelete) {
            transactionDao.delete(tx)
        }
        
        materializeTransactionsForRule(rule)
    }

    suspend fun deleteRecurrenceRuleAndFuture(ruleId: Int, userId: String, fromMonthStr: String) {
        val rule = recurrenceRuleDao.getRecurrenceRuleById(ruleId) ?: return
        recurrenceRuleDao.update(rule.copy(active = false))
        
        val txs = transactionDao.getAllTransactions(userId)
            .filter { it.recurrence_rule_id == ruleId }
            
        val toDelete = txs.filter { 
            val txMonth = it.date.take(7)
            txMonth >= fromMonthStr && !it.is_recurrence_override
        }
        
        for (tx in toDelete) {
            transactionDao.delete(tx)
        }
    }

    suspend fun updateInstallmentAndFuture(
        planId: Int, 
        fromInstallmentNumber: Int, 
        updatedValue: Double, 
        updatedCategory: Int?, 
        updatedSubcategory: Int?, 
        updatedDescription: String, 
        updatedAccountId: Int,
        userId: String,
        updatedInstallmentsCount: Int? = null
    ) {
        val plan = installmentPlanDao.getInstallmentPlanById(planId) ?: return
        val currentCount = plan.installments_count
        val finalCount = updatedInstallmentsCount ?: currentCount
        
        // Update the plan itself
        val updatedPlan = plan.copy(
            category_id = updatedCategory ?: plan.category_id,
            subcategory_id = updatedSubcategory,
            description = updatedDescription,
            account_id = updatedAccountId,
            installments_count = finalCount
        )
        installmentPlanDao.insert(updatedPlan)
        
        val txs = transactionDao.getAllTransactions(userId)
            .filter { it.installment_plan_id == planId }
            
        // 1. Delete any installments with index > finalCount (if count decreased)
        val toDelete = txs.filter { it.installment_number != null && it.installment_number > finalCount }
        for (tx in toDelete) {
            transactionDao.delete(tx)
        }
        
        // 2. Update remaining installments >= fromInstallmentNumber
        val toUpdate = txs.filter { it.installment_number != null && it.installment_number <= finalCount && it.installment_number >= fromInstallmentNumber }
        for (tx in toUpdate) {
            val formattedDesc = "${updatedDescription} (${tx.installment_number}/$finalCount)"
            transactionDao.update(
                tx.copy(
                    value = updatedValue,
                    category_id = updatedCategory,
                    subcategory_id = updatedSubcategory,
                    description = formattedDesc,
                    account_id = updatedAccountId
                )
            )
        }
        
        // 3. For any installments < fromInstallmentNumber, update their suffix if finalCount changed
        if (finalCount != currentCount) {
            val pastTxs = txs.filter { it.installment_number != null && it.installment_number < fromInstallmentNumber }
            for (tx in pastTxs) {
                // Keep its old base description, but change suffix
                val cleanDesc = tx.description.substringBeforeLast(" (")
                transactionDao.update(
                    tx.copy(
                        description = "$cleanDesc (${tx.installment_number}/$finalCount)"
                    )
                )
            }
        }
        
        // 4. Generate new installments if finalCount increased
        if (finalCount > currentCount) {
            val baseValue = updatedValue // use the newly updated value for new installments
            val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
            val cal = java.util.Calendar.getInstance()
            try {
                val date = sdf.parse(plan.first_installment_month) ?: java.util.Date()
                cal.time = date
            } catch (e: Exception) {
                // ignore
            }
            
            val newTransactions = mutableListOf<Transaction>()
            for (i in (currentCount + 1)..finalCount) {
                val instCal = cal.clone() as java.util.Calendar
                instCal.add(java.util.Calendar.MONTH, i - 1)
                
                // Keep the same day of month as first installment if possible
                val monthStr = sdf.format(instCal.time)
                val fullDateStr = "$monthStr-10" // default to 10th or similar
                
                newTransactions.add(
                    Transaction(
                        account_id = updatedAccountId,
                        to_account_id = null,
                        category_id = updatedCategory,
                        subcategory_id = updatedSubcategory,
                        type = "DESPESA",
                        value = baseValue,
                        description = "${updatedDescription} ($i/$finalCount)",
                        date = fullDateStr,
                        installment_plan_id = planId,
                        installment_number = i,
                        recurrence_rule_id = null,
                        is_recurrence_override = false,
                        userId = userId
                    )
                )
            }
            if (newTransactions.isNotEmpty()) {
                transactionDao.insertAll(newTransactions)
            }
        }
    }

    suspend fun deleteInstallmentAndFuture(planId: Int, fromInstallmentNumber: Int, userId: String) {
        val txs = transactionDao.getAllTransactions(userId)
            .filter { it.installment_plan_id == planId && it.installment_number != null && it.installment_number >= fromInstallmentNumber }
            
        for (tx in txs) {
            transactionDao.delete(tx)
        }
    }

    // --- DERIVED ACCOUNT BALANCE FLOW ---
    fun getAccountsWithBalancesFlow(userId: String): Flow<List<Account>> {
        return combine(
            getAccountsFlow(userId),
            getTransactionsFlow(userId)
        ) { accountsList, transactionsList ->
            accountsList.map { account ->
                val creditos = transactionsList.filter { it.type == "RECEITA" && it.account_id == account.id }
                    .sumOf { it.value } +
                    transactionsList.filter { it.type == "TRANSFERENCIA" && it.to_account_id == account.id }
                    .sumOf { it.value }

                val debitos = transactionsList.filter { it.type == "DESPESA" && it.account_id == account.id }
                    .sumOf { it.value } +
                    transactionsList.filter { it.type == "TRANSFERENCIA" && it.account_id == account.id }
                    .sumOf { it.value }

                account.copy(initial_balance = account.initial_balance + creditos - debitos)
            }
        }
    }

    suspend fun getAccountWithBalance(accountId: Int, userId: String): Account? {
        val account = accountDao.getAccountById(accountId) ?: return null
        val transactions = transactionDao.getAllTransactions(userId)
        val creditos = transactions.filter { it.type == "RECEITA" && it.account_id == account.id }
            .sumOf { it.value } +
            transactions.filter { it.type == "TRANSFERENCIA" && it.to_account_id == account.id }
            .sumOf { it.value }

        val debitos = transactions.filter { it.type == "DESPESA" && it.account_id == account.id }
            .sumOf { it.value } +
            transactions.filter { it.type == "TRANSFERENCIA" && it.account_id == account.id }
            .sumOf { it.value }

        return account.copy(initial_balance = account.initial_balance + creditos - debitos)
    }


    // --- ENVELOPE GROUPS ---
    fun getEnvelopeGroupsFlow(userId: String): Flow<List<EnvelopeGroup>> = envelopeGroupDao.getEnvelopeGroupsFlow(userId)
    suspend fun getAllEnvelopeGroups(userId: String): List<EnvelopeGroup> = envelopeGroupDao.getAllEnvelopeGroups(userId)
    suspend fun insertEnvelopeGroup(group: EnvelopeGroup): Long = envelopeGroupDao.insert(group)
    suspend fun updateEnvelopeGroup(group: EnvelopeGroup) = envelopeGroupDao.update(group)
    suspend fun deleteEnvelopeGroup(group: EnvelopeGroup) = envelopeGroupDao.delete(group)

    // --- CATEGORIES ---
    fun getCategoriesFlow(userId: String): Flow<List<Category>> = categoryDao.getCategoriesFlow(userId)
    suspend fun getAllCategories(userId: String): List<Category> = categoryDao.getAllCategories(userId)
    suspend fun insertCategory(category: Category): Long = categoryDao.insert(category)
    suspend fun updateCategory(category: Category) = categoryDao.update(category)
    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)

    // --- SUBCATEGORIES ---
    fun getSubcategoriesFlow(userId: String): Flow<List<Subcategory>> = subcategoryDao.getSubcategoriesFlow(userId)
    suspend fun getAllSubcategories(userId: String): List<Subcategory> = subcategoryDao.getAllSubcategories(userId)
    suspend fun insertSubcategory(subcategory: Subcategory): Long = subcategoryDao.insert(subcategory)
    suspend fun updateSubcategory(subcategory: Subcategory) = subcategoryDao.update(subcategory)
    suspend fun deleteSubcategory(subcategory: Subcategory) = subcategoryDao.delete(subcategory)

    // --- FIRESTORE SYNC ---
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun syncPush(userId: String): Boolean {
        if (userId.isEmpty() || userId == "GUEST") return false
        return try {
            val accounts = getAllAccounts(userId).map { it.toMap() }
            val groups = getAllEnvelopeGroups(userId).map { it.toMap() }
            val categories = getAllCategories(userId).map { it.toMap() }
            val subcategories = getAllSubcategories(userId).map { it.toMap() }
            val transactions = getAllTransactions(userId).map { it.toMap() }
            val budgetAllocations = getAllBudgetAllocations(userId).map { it.toMap() }
            val allocationMovements = getAllAllocationMovements(userId).map { it.toMap() }
            val installmentPlans = getAllInstallmentPlans(userId).map { it.toMap() }
            val recurrenceRules = getAllRecurrenceRules(userId).map { it.toMap() }
            val goals = getAllGoals(userId).map { it.toMap() }
            val notificationLogs = notificationLogDao.getAllLogs(userId).map { it.toMap() }

            val syncData = mapOf(
                "accounts" to accounts,
                "envelope_groups" to groups,
                "categories" to categories,
                "subcategories" to subcategories,
                "transactions" to transactions,
                "budget_allocations" to budgetAllocations,
                "allocation_movements" to allocationMovements,
                "installment_plans" to installmentPlans,
                "recurrence_rules" to recurrenceRules,
                "goals" to goals,
                "notification_logs" to notificationLogs,
                "last_updated" to Date()
            )

            firestore.collection("users").document(userId).set(syncData).await()
            Log.d("FinanceRepository", "Push sync succeeded for $userId")
            true
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Push sync failed", e)
            false
        }
    }

    suspend fun syncPull(userId: String): Boolean {
        if (userId.isEmpty() || userId == "GUEST") return false
        return try {
            val document = firestore.collection("users").document(userId).get().await()
            if (document.exists()) {
                val data = document.data ?: return false

                // Process Accounts
                val accountsList = (data["accounts"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { accountFromMap(it.castKeys(), userId) }
                }
                if (accountsList != null) {
                    accountDao.insertAll(accountsList)
                } else {
                    Log.w("FinanceRepository", "Pull: 'accounts' field is null or missing. Skipping accounts sync.")
                }

                // Process Envelope Groups
                val groupsList = (data["envelope_groups"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { envelopeGroupFromMap(it.castKeys(), userId) }
                }
                if (groupsList != null) {
                    envelopeGroupDao.insertAll(groupsList)
                } else {
                    Log.w("FinanceRepository", "Pull: 'envelope_groups' field is null or missing. Skipping envelope groups sync.")
                }

                // Process Categories
                val categoriesList = (data["categories"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { categoryFromMap(it.castKeys(), userId) }
                }
                if (categoriesList != null) {
                    categoryDao.insertAll(categoriesList)
                } else {
                    Log.w("FinanceRepository", "Pull: 'categories' field is null or missing. Skipping categories sync.")
                }

                // Process Subcategories
                val subcategoriesList = (data["subcategories"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { subcategoryFromMap(it.castKeys(), userId) }
                }
                if (subcategoriesList != null) {
                    subcategoryDao.insertAll(subcategoriesList)
                } else {
                    Log.w("FinanceRepository", "Pull: 'subcategories' field is null or missing. Skipping subcategories sync.")
                }

                // Process Transactions
                val transactionsList = (data["transactions"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { transactionFromMap(it.castKeys(), userId) }
                }
                if (transactionsList != null) {
                    transactionDao.insertAll(transactionsList)
                } else {
                    Log.w("FinanceRepository", "Pull: 'transactions' field is null or missing. Skipping transactions sync.")
                }

                // Process Budget Allocations
                val allocationsList = (data["budget_allocations"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { budgetAllocationFromMap(it.castKeys(), userId) }
                }
                if (allocationsList != null) {
                    budgetAllocationDao.insertAll(allocationsList)
                } else {
                    Log.w("FinanceRepository", "Pull: 'budget_allocations' field is null or missing. Skipping budget allocations sync.")
                }

                // Process Allocation Movements
                val movementsList = (data["allocation_movements"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { allocationMovementFromMap(it.castKeys(), userId) }
                }
                if (movementsList != null) {
                    allocationMovementDao.insertAll(movementsList)
                } else {
                    Log.w("FinanceRepository", "Pull: 'allocation_movements' field is null or missing. Skipping allocation movements sync.")
                }

                // Process Installment Plans
                val plansList = (data["installment_plans"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { installmentPlanFromMap(it.castKeys(), userId) }
                }
                if (plansList != null) {
                    installmentPlanDao.insertAll(plansList)
                } else {
                    Log.w("FinanceRepository", "Pull: 'installment_plans' field is null or missing. Skipping installment plans sync.")
                }

                // Process Recurrence Rules
                val rulesList = (data["recurrence_rules"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { recurrenceRuleFromMap(it.castKeys(), userId) }
                }
                if (rulesList != null) {
                    recurrenceRuleDao.insertAll(rulesList)
                } else {
                    Log.w("FinanceRepository", "Pull: 'recurrence_rules' field is null or missing. Skipping recurrence rules sync.")
                }

                // Process Goals
                val goalsList = (data["goals"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { goalFromMap(it.castKeys(), userId) }
                }
                if (goalsList != null) {
                    goalDao.insertAll(goalsList)
                } else {
                    Log.w("FinanceRepository", "Pull: 'goals' field is null or missing. Skipping goals sync.")
                }

                // Process Notification Logs
                val logsList = (data["notification_logs"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { notificationLogFromMap(it.castKeys(), userId) }
                }
                if (logsList != null) {
                    notificationLogDao.insertAll(logsList)
                } else {
                    Log.w("FinanceRepository", "Pull: 'notification_logs' field is null or missing. Skipping notification logs sync.")
                }

                Log.d("FinanceRepository", "Pull sync succeeded for $userId")
                true
            } else {
                Log.d("FinanceRepository", "No remote data found to pull for $userId")
                // No document exists, so we might want to upload our current local data
                syncPush(userId)
                true
            }
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Pull sync failed", e)
            false
        }
    }

    // --- EXTENSION / CONVERSION HELPERS ---
    private fun Account.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "type" to type,
        "initial_balance" to initial_balance,
        "archived" to archived
    )

    private fun accountFromMap(map: Map<String, Any?>, userId: String): Account = Account(
        id = (map["id"] as? Long)?.toInt() ?: (map["id"] as? Int) ?: 0,
        name = map["name"] as? String ?: "",
        type = map["type"] as? String ?: "CONTA_CORRENTE",
        initial_balance = (map["initial_balance"] as? Double) ?: (map["initial_balance"] as? Long)?.toDouble() ?: 0.0,
        archived = map["archived"] as? Boolean ?: false,
        userId = userId
    )

    private fun EnvelopeGroup.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "sort_order" to sort_order,
        "budget_rule_type" to budget_rule_type,
        "archived" to archived
    )

    private fun envelopeGroupFromMap(map: Map<String, Any?>, userId: String): EnvelopeGroup = EnvelopeGroup(
        id = (map["id"] as? Long)?.toInt() ?: (map["id"] as? Int) ?: 0,
        name = map["name"] as? String ?: "",
        sort_order = (map["sort_order"] as? Long)?.toInt() ?: (map["sort_order"] as? Int) ?: 0,
        budget_rule_type = map["budget_rule_type"] as? String,
        archived = map["archived"] as? Boolean ?: false,
        userId = userId
    )

    private fun Category.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "envelope_group_id" to envelope_group_id,
        "name" to name,
        "archived" to archived
    )

    private fun categoryFromMap(map: Map<String, Any?>, userId: String): Category = Category(
        id = (map["id"] as? Long)?.toInt() ?: (map["id"] as? Int) ?: 0,
        envelope_group_id = (map["envelope_group_id"] as? Long)?.toInt() ?: (map["envelope_group_id"] as? Int),
        name = map["name"] as? String ?: "",
        archived = map["archived"] as? Boolean ?: false,
        userId = userId
    )

    private fun Subcategory.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "category_id" to category_id,
        "name" to name,
        "archived" to archived
    )

    private fun subcategoryFromMap(map: Map<String, Any?>, userId: String): Subcategory = Subcategory(
        id = (map["id"] as? Long)?.toInt() ?: (map["id"] as? Int) ?: 0,
        category_id = (map["category_id"] as? Long)?.toInt() ?: (map["category_id"] as? Int) ?: 0,
        name = map["name"] as? String ?: "",
        archived = map["archived"] as? Boolean ?: false,
        userId = userId
    )

    private fun Transaction.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "account_id" to account_id,
        "to_account_id" to to_account_id,
        "category_id" to category_id,
        "subcategory_id" to subcategory_id,
        "type" to type,
        "value" to value,
        "description" to description,
        "date" to date,
        "installment_plan_id" to installment_plan_id,
        "installment_number" to installment_number,
        "recurrence_rule_id" to recurrence_rule_id,
        "is_recurrence_override" to is_recurrence_override,
        "attachment_uri" to attachment_uri,
        "attachment_name" to attachment_name,
        "attachment_type" to attachment_type,
        "synced" to synced,
        "goal_id" to goal_id
    )

    private fun transactionFromMap(map: Map<String, Any?>, userId: String): Transaction = Transaction(
        id = (map["id"] as? Long)?.toInt() ?: (map["id"] as? Int) ?: 0,
        account_id = (map["account_id"] as? Long)?.toInt() ?: (map["account_id"] as? Int) ?: 0,
        to_account_id = (map["to_account_id"] as? Long)?.toInt() ?: (map["to_account_id"] as? Int),
        category_id = (map["category_id"] as? Long)?.toInt() ?: (map["category_id"] as? Int),
        subcategory_id = (map["subcategory_id"] as? Long)?.toInt() ?: (map["subcategory_id"] as? Int),
        type = map["type"] as? String ?: "DESPESA",
        value = (map["value"] as? Double) ?: (map["value"] as? Long)?.toDouble() ?: 0.0,
        description = map["description"] as? String ?: "",
        date = map["date"] as? String ?: "",
        installment_plan_id = (map["installment_plan_id"] as? Long)?.toInt() ?: (map["installment_plan_id"] as? Int),
        installment_number = (map["installment_number"] as? Long)?.toInt() ?: (map["installment_number"] as? Int),
        recurrence_rule_id = (map["recurrence_rule_id"] as? Long)?.toInt() ?: (map["recurrence_rule_id"] as? Int),
        is_recurrence_override = map["is_recurrence_override"] as? Boolean ?: false,
        attachment_uri = map["attachment_uri"] as? String,
        attachment_name = map["attachment_name"] as? String,
        attachment_type = map["attachment_type"] as? String,
        synced = map["synced"] as? Boolean ?: false,
        userId = userId,
        goal_id = (map["goal_id"] as? Long)?.toInt() ?: (map["goal_id"] as? Int)
    )

    private fun BudgetAllocation.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "category_id" to category_id,
        "subcategory_id" to subcategory_id,
        "month" to month,
        "planned_value" to planned_value
    )

    private fun budgetAllocationFromMap(map: Map<String, Any?>, userId: String): BudgetAllocation = BudgetAllocation(
        id = (map["id"] as? Long)?.toInt() ?: (map["id"] as? Int) ?: 0,
        category_id = (map["category_id"] as? Long)?.toInt() ?: (map["category_id"] as? Int) ?: 0,
        subcategory_id = (map["subcategory_id"] as? Long)?.toInt() ?: (map["subcategory_id"] as? Int),
        month = map["month"] as? String ?: "",
        planned_value = (map["planned_value"] as? Double) ?: (map["planned_value"] as? Long)?.toDouble() ?: 0.0,
        userId = userId
    )

    private fun AllocationMovement.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "source_budget_allocation_id" to source_budget_allocation_id,
        "source_goal_id" to source_goal_id,
        "dest_budget_allocation_id" to dest_budget_allocation_id,
        "dest_goal_id" to dest_goal_id,
        "amount" to amount,
        "note" to note,
        "moved_at" to moved_at
    )

    private fun allocationMovementFromMap(map: Map<String, Any?>, userId: String): AllocationMovement = AllocationMovement(
        id = (map["id"] as? Long)?.toInt() ?: (map["id"] as? Int) ?: 0,
        source_budget_allocation_id = (map["source_budget_allocation_id"] as? Long)?.toInt() ?: (map["source_budget_allocation_id"] as? Int),
        source_goal_id = (map["source_goal_id"] as? Long)?.toInt() ?: (map["source_goal_id"] as? Int),
        dest_budget_allocation_id = (map["dest_budget_allocation_id"] as? Long)?.toInt() ?: (map["dest_budget_allocation_id"] as? Int),
        dest_goal_id = (map["dest_goal_id"] as? Long)?.toInt() ?: (map["dest_goal_id"] as? Int),
        amount = (map["amount"] as? Double) ?: (map["amount"] as? Long)?.toDouble() ?: 0.0,
        note = map["note"] as? String,
        moved_at = (map["moved_at"] as? Long) ?: (map["moved_at"] as? Int)?.toLong() ?: System.currentTimeMillis(),
        userId = userId
    )

    private fun InstallmentPlan.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "account_id" to account_id,
        "category_id" to category_id,
        "subcategory_id" to subcategory_id,
        "description" to description,
        "total_value" to total_value,
        "installments_count" to installments_count,
        "first_installment_month" to first_installment_month,
        "created_at" to created_at
    )

    private fun installmentPlanFromMap(map: Map<String, Any?>, userId: String): InstallmentPlan = InstallmentPlan(
        id = (map["id"] as? Long)?.toInt() ?: (map["id"] as? Int) ?: 0,
        account_id = (map["account_id"] as? Long)?.toInt() ?: (map["account_id"] as? Int) ?: 0,
        category_id = (map["category_id"] as? Long)?.toInt() ?: (map["category_id"] as? Int) ?: 0,
        subcategory_id = (map["subcategory_id"] as? Long)?.toInt() ?: (map["subcategory_id"] as? Int),
        description = map["description"] as? String ?: "",
        total_value = (map["total_value"] as? Double) ?: (map["total_value"] as? Long)?.toDouble() ?: 0.0,
        installments_count = (map["installments_count"] as? Long)?.toInt() ?: (map["installments_count"] as? Int) ?: 1,
        first_installment_month = map["first_installment_month"] as? String ?: "",
        created_at = (map["created_at"] as? Long) ?: (map["created_at"] as? Int)?.toLong() ?: System.currentTimeMillis(),
        userId = userId
    )

    private fun RecurrenceRule.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "account_id" to account_id,
        "category_id" to category_id,
        "subcategory_id" to subcategory_id,
        "description" to description,
        "value" to value,
        "type" to type,
        "frequency" to frequency,
        "frequency_interval" to frequency_interval,
        "start_date" to start_date,
        "end_month" to end_month,
        "active" to active
    )

    private fun recurrenceRuleFromMap(map: Map<String, Any?>, userId: String): RecurrenceRule = RecurrenceRule(
        id = (map["id"] as? Long)?.toInt() ?: (map["id"] as? Int) ?: 0,
        account_id = (map["account_id"] as? Long)?.toInt() ?: (map["account_id"] as? Int) ?: 0,
        category_id = (map["category_id"] as? Long)?.toInt() ?: (map["category_id"] as? Int) ?: 0,
        subcategory_id = (map["subcategory_id"] as? Long)?.toInt() ?: (map["subcategory_id"] as? Int),
        description = map["description"] as? String ?: "",
        value = (map["value"] as? Double) ?: (map["value"] as? Long)?.toDouble() ?: 0.0,
        type = map["type"] as? String ?: "DESPESA",
        frequency = map["frequency"] as? String ?: "MENSAL",
        frequency_interval = (map["frequency_interval"] as? Long)?.toInt() ?: (map["frequency_interval"] as? Int) ?: 1,
        start_date = map["start_date"] as? String ?: "",
        end_month = map["end_month"] as? String,
        active = map["active"] as? Boolean ?: true,
        userId = userId
    )

    private fun Goal.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "target_value" to target_value,
        "start_date" to start_date,
        "deadline" to deadline,
        "color" to color,
        "archived" to archived
    )

    private fun goalFromMap(map: Map<String, Any?>, userId: String): Goal = Goal(
        id = (map["id"] as? Long)?.toInt() ?: (map["id"] as? Int) ?: 0,
        name = map["name"] as? String ?: "",
        target_value = (map["target_value"] as? Double) ?: (map["target_value"] as? Long)?.toDouble() ?: 0.0,
        start_date = map["start_date"] as? String ?: "",
        deadline = map["deadline"] as? String ?: "",
        color = (map["color"] as? Long)?.toInt() ?: (map["color"] as? Int) ?: 0,
        archived = map["archived"] as? Boolean ?: false,
        userId = userId
    )

    private fun NotificationLog.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "type" to type,
        "reference_id" to reference_id,
        "reference_month" to reference_month,
        "sent_at" to sent_at
    )

    private fun notificationLogFromMap(map: Map<String, Any?>, userId: String): NotificationLog = NotificationLog(
        id = (map["id"] as? Long)?.toInt() ?: (map["id"] as? Int) ?: 0,
        type = map["type"] as? String ?: "",
        reference_id = map["reference_id"] as? String,
        reference_month = map["reference_month"] as? String,
        sent_at = (map["sent_at"] as? Long) ?: (map["sent_at"] as? Int)?.toLong() ?: System.currentTimeMillis(),
        userId = userId
    )

    @Suppress("UNCHECKED_CAST")
    private fun Map<*, *>.castKeys(): Map<String, Any?> {
        return this.entries.associate { (key, value) ->
            key.toString() to value
        }
    }
}
