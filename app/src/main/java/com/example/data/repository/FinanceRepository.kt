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
    private val goalDao: GoalDao
) {
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

            val syncData = mapOf(
                "accounts" to accounts,
                "envelope_groups" to groups,
                "categories" to categories,
                "subcategories" to subcategories,
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

                val accountsList = (data["accounts"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { accountFromMap(it.castKeys(), userId) }
                } ?: emptyList()

                val groupsList = (data["envelope_groups"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { envelopeGroupFromMap(it.castKeys(), userId) }
                } ?: emptyList()

                val categoriesList = (data["categories"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { categoryFromMap(it.castKeys(), userId) }
                } ?: emptyList()

                val subcategoriesList = (data["subcategories"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { subcategoryFromMap(it.castKeys(), userId) }
                } ?: emptyList()

                // Overwrite local Room with pull data
                accountDao.clearAll(userId)
                envelopeGroupDao.clearAll(userId)
                categoryDao.clearAll(userId)
                subcategoryDao.clearAll(userId)

                accountDao.insertAll(accountsList)
                envelopeGroupDao.insertAll(groupsList)
                categoryDao.insertAll(categoriesList)
                subcategoryDao.insertAll(subcategoriesList)

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

    @Suppress("UNCHECKED_CAST")
    private fun Map<*, *>.castKeys(): Map<String, Any?> {
        return this.entries.associate { (key, value) ->
            key.toString() to value
        }
    }
}
