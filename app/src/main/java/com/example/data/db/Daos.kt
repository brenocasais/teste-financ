package com.example.data.db

import androidx.room.*
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.EnvelopeGroup
import com.example.data.model.Subcategory
import com.example.data.model.Transaction
import com.example.data.model.BudgetAllocation
import com.example.data.model.AllocationMovement
import com.example.data.model.InstallmentPlan
import com.example.data.model.RecurrenceRule
import com.example.data.model.Goal
import com.example.data.model.NotificationLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY name ASC")
    fun getAccountsFlow(userId: String): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY name ASC")
    suspend fun getAllAccounts(userId: String): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Int): Account?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<Account>)

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)

    @Query("DELETE FROM accounts WHERE userId = :userId")
    suspend fun clearAll(userId: String)
}

@Dao
interface EnvelopeGroupDao {
    @Query("SELECT * FROM envelope_groups WHERE userId = :userId ORDER BY sort_order ASC, name ASC")
    fun getEnvelopeGroupsFlow(userId: String): Flow<List<EnvelopeGroup>>

    @Query("SELECT * FROM envelope_groups WHERE userId = :userId")
    suspend fun getAllEnvelopeGroups(userId: String): List<EnvelopeGroup>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(envelopeGroup: EnvelopeGroup): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<EnvelopeGroup>)

    @Update
    suspend fun update(envelopeGroup: EnvelopeGroup)

    @Delete
    suspend fun delete(envelopeGroup: EnvelopeGroup)

    @Query("DELETE FROM envelope_groups WHERE userId = :userId")
    suspend fun clearAll(userId: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY name ASC")
    fun getCategoriesFlow(userId: String): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE userId = :userId")
    suspend fun getAllCategories(userId: String): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("DELETE FROM categories WHERE userId = :userId")
    suspend fun clearAll(userId: String)
}

@Dao
interface SubcategoryDao {
    @Query("SELECT * FROM subcategories WHERE userId = :userId ORDER BY name ASC")
    fun getSubcategoriesFlow(userId: String): Flow<List<Subcategory>>

    @Query("SELECT * FROM subcategories WHERE userId = :userId")
    suspend fun getAllSubcategories(userId: String): List<Subcategory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subcategory: Subcategory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subcategories: List<Subcategory>)

    @Update
    suspend fun update(subcategory: Subcategory)

    @Delete
    suspend fun delete(subcategory: Subcategory)

    @Query("DELETE FROM subcategories WHERE userId = :userId")
    suspend fun clearAll(userId: String)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC, id DESC")
    fun getTransactionsFlow(userId: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC, id DESC")
    suspend fun getAllTransactions(userId: String): List<Transaction>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Int): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<Transaction>)

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun clearAll(userId: String)
}

@Dao
interface BudgetAllocationDao {
    @Query("SELECT * FROM budget_allocations WHERE userId = :userId")
    fun getBudgetAllocationsFlow(userId: String): Flow<List<BudgetAllocation>>

    @Query("SELECT * FROM budget_allocations WHERE userId = :userId AND month = :month")
    fun getBudgetAllocationsForMonthFlow(userId: String, month: String): Flow<List<BudgetAllocation>>

    @Query("SELECT * FROM budget_allocations WHERE userId = :userId")
    suspend fun getAllBudgetAllocations(userId: String): List<BudgetAllocation>

    @Query("SELECT * FROM budget_allocations WHERE userId = :userId AND month = :month")
    suspend fun getBudgetAllocationsForMonth(userId: String, month: String): List<BudgetAllocation>

    @Query("SELECT * FROM budget_allocations WHERE category_id = :categoryId AND subcategory_id = :subcategoryId AND month = :month AND userId = :userId LIMIT 1")
    suspend fun getBudgetAllocationWithSub(categoryId: Int, subcategoryId: Int, month: String, userId: String): BudgetAllocation?

    @Query("SELECT * FROM budget_allocations WHERE category_id = :categoryId AND subcategory_id IS NULL AND month = :month AND userId = :userId LIMIT 1")
    suspend fun getBudgetAllocationWithoutSub(categoryId: Int, month: String, userId: String): BudgetAllocation?

    suspend fun getBudgetAllocation(categoryId: Int, subcategoryId: Int?, month: String, userId: String): BudgetAllocation? {
        return if (subcategoryId != null) {
            getBudgetAllocationWithSub(categoryId, subcategoryId, month, userId)
        } else {
            getBudgetAllocationWithoutSub(categoryId, month, userId)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budgetAllocation: BudgetAllocation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgetAllocations: List<BudgetAllocation>)

    @Update
    suspend fun update(budgetAllocation: BudgetAllocation)

    @Delete
    suspend fun delete(budgetAllocation: BudgetAllocation)

    @Query("DELETE FROM budget_allocations WHERE userId = :userId")
    suspend fun clearAll(userId: String)
}

@Dao
interface AllocationMovementDao {
    @Query("SELECT * FROM allocation_movements WHERE userId = :userId ORDER BY moved_at DESC, id DESC")
    fun getAllocationMovementsFlow(userId: String): Flow<List<AllocationMovement>>

    @Query("SELECT * FROM allocation_movements WHERE userId = :userId ORDER BY moved_at DESC, id DESC")
    suspend fun getAllAllocationMovements(userId: String): List<AllocationMovement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(allocationMovement: AllocationMovement): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movements: List<AllocationMovement>)

    @Update
    suspend fun update(allocationMovement: AllocationMovement)

    @Delete
    suspend fun delete(allocationMovement: AllocationMovement)

    @Query("DELETE FROM allocation_movements WHERE userId = :userId")
    suspend fun clearAll(userId: String)
}

@Dao
interface InstallmentPlanDao {
    @Query("SELECT * FROM installment_plans WHERE userId = :userId ORDER BY created_at DESC")
    fun getInstallmentPlansFlow(userId: String): Flow<List<InstallmentPlan>>

    @Query("SELECT * FROM installment_plans WHERE userId = :userId ORDER BY created_at DESC")
    suspend fun getAllInstallmentPlans(userId: String): List<InstallmentPlan>

    @Query("SELECT * FROM installment_plans WHERE id = :id")
    suspend fun getInstallmentPlanById(id: Int): InstallmentPlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(installmentPlan: InstallmentPlan): Long

    @Delete
    suspend fun delete(installmentPlan: InstallmentPlan)

    @Query("DELETE FROM installment_plans WHERE userId = :userId")
    suspend fun clearAll(userId: String)
}

@Dao
interface RecurrenceRuleDao {
    @Query("SELECT * FROM recurrence_rules WHERE userId = :userId ORDER BY id DESC")
    fun getRecurrenceRulesFlow(userId: String): Flow<List<RecurrenceRule>>

    @Query("SELECT * FROM recurrence_rules WHERE userId = :userId ORDER BY id DESC")
    suspend fun getAllRecurrenceRules(userId: String): List<RecurrenceRule>

    @Query("SELECT * FROM recurrence_rules WHERE id = :id")
    suspend fun getRecurrenceRuleById(id: Int): RecurrenceRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recurrenceRule: RecurrenceRule): Long

    @Update
    suspend fun update(recurrenceRule: RecurrenceRule)

    @Delete
    suspend fun delete(recurrenceRule: RecurrenceRule)

    @Query("DELETE FROM recurrence_rules WHERE userId = :userId")
    suspend fun clearAll(userId: String)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE userId = :userId ORDER BY archived ASC, name ASC")
    fun getGoalsFlow(userId: String): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE userId = :userId ORDER BY archived ASC, name ASC")
    suspend fun getAllGoals(userId: String): List<Goal>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoalById(id: Int): Goal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: Goal): Long

    @Update
    suspend fun update(goal: Goal)

    @Delete
    suspend fun delete(goal: Goal)

    @Query("DELETE FROM goals WHERE userId = :userId")
    suspend fun clearAll(userId: String)
}

@Dao
interface NotificationLogDao {
    @Query("SELECT * FROM notification_logs WHERE userId = :userId")
    suspend fun getAllLogs(userId: String): List<NotificationLog>

    @Query("SELECT * FROM notification_logs WHERE userId = :userId AND type = :type AND reference_id = :referenceId AND reference_month = :referenceMonth LIMIT 1")
    suspend fun getLog(userId: String, type: String, referenceId: String?, referenceMonth: String?): NotificationLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: NotificationLog): Long

    @Query("DELETE FROM notification_logs WHERE userId = :userId")
    suspend fun clearAll(userId: String)
}


