package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.MeuFinanceiroApplication
import com.example.data.auth.AuthManager
import com.example.data.pref.UserPreferences
import com.example.data.repository.FinanceRepository
import com.example.data.model.Transaction
import com.example.data.model.Category
import com.example.data.model.Subcategory
import com.example.data.model.BudgetAllocation
import com.example.data.model.AllocationMovement
import com.example.data.model.Goal
import com.example.data.model.InstallmentPlan
import com.example.data.model.RecurrenceRule
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MeuFinanceiroApplication
    val repository: FinanceRepository = app.repository
    private val userPreferences: UserPreferences = app.userPreferences
    val authManager: AuthManager = app.authManager

    // --- Preferences states ---
    val themeMode: StateFlow<String> = userPreferences.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    val onboardingCompleted: StateFlow<Boolean> = userPreferences.onboardingCompletedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isGuestMode: StateFlow<Boolean> = userPreferences.isGuestModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // --- Authentication states ---
    val authState: StateFlow<AuthManager.AuthState> = authManager.authState

    val currentUserId: String
        get() = authManager.currentUserId

    // --- Synchronization states ---
    sealed class SyncState {
        object Idle : SyncState()
        object Syncing : SyncState()
        data class Success(val message: String) : SyncState()
        data class Error(val error: String) : SyncState()
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private val _syncLogs = MutableStateFlow<List<String>>(emptyList())
    val syncLogs: StateFlow<List<String>> = _syncLogs

    // --- Shared Month/Period State ---
    private val _selectedMonthCalendar = MutableStateFlow<java.util.Calendar>(java.util.Calendar.getInstance())
    val selectedMonthCalendar: StateFlow<java.util.Calendar> = _selectedMonthCalendar

    fun setSelectedMonth(calendar: java.util.Calendar) {
        _selectedMonthCalendar.value = calendar
    }

    fun selectCurrentMonth() {
        _selectedMonthCalendar.value = java.util.Calendar.getInstance()
    }

    init {
        // Observe auth changes to trigger initial pull or clear guest info
        viewModelScope.launch {
            authState.collect { state ->
                val userId = currentUserId
                if (state is AuthManager.AuthState.Authenticated) {
                    addSyncLog("Usuário autenticado. Iniciando sincronização...")
                    triggerPull()
                    repository.materializeRecurrenceTransactions(userId)
                } else if (state is AuthManager.AuthState.Guest) {
                    addSyncLog("Modo convidado ativo. Sincronização offline desativada.")
                    repository.materializeRecurrenceTransactions(userId)
                }
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            userPreferences.setThemeMode(mode)
        }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch {
            userPreferences.setOnboardingCompleted(completed)
        }
    }

    fun setGuestMode(isGuest: Boolean) {
        viewModelScope.launch {
            userPreferences.setGuestMode(isGuest)
            if (isGuest) {
                authManager.loginAsGuest()
            }
        }
    }

    fun triggerPull() {
        val userId = currentUserId
        if (userId.isEmpty() || userId == "GUEST") {
            _syncState.value = SyncState.Error("Não é possível baixar dados no modo Convidado.")
            return
        }
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            addSyncLog("Baixando dados do servidor...")
            val success = repository.syncPull(userId)
            if (success) {
                _syncState.value = SyncState.Success("Dados baixados com sucesso!")
                addSyncLog("Download finalizado com sucesso.")
            } else {
                _syncState.value = SyncState.Error("Falha ao baixar dados do servidor.")
                addSyncLog("Erro: Falha ao baixar dados.")
            }
        }
    }

    fun triggerPush() {
        val userId = currentUserId
        if (userId.isEmpty() || userId == "GUEST") {
            _syncState.value = SyncState.Error("Sincronização desativada no modo Convidado.")
            return
        }
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            addSyncLog("Enviando dados locais para o servidor...")
            val success = repository.syncPush(userId)
            if (success) {
                _syncState.value = SyncState.Success("Sincronização concluída com sucesso!")
                addSyncLog("Upload finalizado com sucesso.")
            } else {
                _syncState.value = SyncState.Error("Falha ao enviar dados para o servidor.")
                addSyncLog("Erro: Falha no upload de dados.")
            }
        }
    }

    private fun addSyncLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _syncLogs.update { current -> listOf("[$timestamp] $message") + current }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            userPreferences.clearAll()
            authManager.logout {
                onComplete()
            }
        }
    }

    // --- TRANSACTION ACTIONS ---
    // --- Navigation routing / highlighting / history dialog states ---
    private val _navigateToTab = MutableSharedFlow<Int>()
    val navigateToTab = _navigateToTab.asSharedFlow()

    private val _highlightedTransaction = MutableStateFlow<Transaction?>(null)
    val highlightedTransaction = _highlightedTransaction.asStateFlow()

    private val _activeHistoryDialog = MutableStateFlow<Pair<Category, Subcategory?>?>(null)
    val activeHistoryDialog = _activeHistoryDialog.asStateFlow()

    var previousTabBeforeHighlight: Int? = null

    fun navigateToTransaction(tx: Transaction) {
        _highlightedTransaction.value = tx
        previousTabBeforeHighlight = 2 // Planning Screen is tab index 2
        viewModelScope.launch {
            _navigateToTab.emit(1) // Transactions Screen is tab index 1
        }
    }

    fun returnFromTransactionHighlight() {
        _highlightedTransaction.value = null
        val prev = previousTabBeforeHighlight
        if (prev != null) {
            previousTabBeforeHighlight = null
            viewModelScope.launch {
                _navigateToTab.emit(prev)
            }
        } else {
            _activeHistoryDialog.value = null
        }
    }

    fun showHistoryDialog(category: Category, subcategory: Subcategory?) {
        _activeHistoryDialog.value = Pair(category, subcategory)
    }

    fun dismissHistoryDialog() {
        _activeHistoryDialog.value = null
    }

    fun insertTransaction(transaction: Transaction, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.insertTransaction(transaction)
            triggerPush()
            onComplete()
        }
    }

    fun updateTransaction(transaction: Transaction, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
            triggerPush()
            onComplete()
        }
    }

    fun deleteTransaction(transaction: Transaction, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
            triggerPush()
            onComplete()
        }
    }

    fun createInstallmentPlan(plan: InstallmentPlan, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.createInstallmentPlan(plan)
            triggerPush()
            onComplete()
        }
    }

    fun updateInstallmentAndFuture(
        planId: Int,
        fromInstallmentNumber: Int,
        updatedValue: Double,
        updatedCategory: Int?,
        updatedSubcategory: Int?,
        updatedDescription: String,
        updatedAccountId: Int,
        userId: String,
        updatedInstallmentsCount: Int? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.updateInstallmentAndFuture(
                planId,
                fromInstallmentNumber,
                updatedValue,
                updatedCategory,
                updatedSubcategory,
                updatedDescription,
                updatedAccountId,
                userId,
                updatedInstallmentsCount
            )
            triggerPush()
            onComplete()
        }
    }

    fun deleteInstallmentAndFuture(planId: Int, fromInstallmentNumber: Int, userId: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteInstallmentAndFuture(planId, fromInstallmentNumber, userId)
            triggerPush()
            onComplete()
        }
    }

    // --- RECURRENCE RULES ---
    fun createRecurrenceRule(rule: RecurrenceRule, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.createRecurrenceRule(rule)
            triggerPush()
            onComplete()
        }
    }

    fun updateRecurrenceRuleAndFuture(rule: RecurrenceRule, fromMonthStr: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateRecurrenceRuleAndFuture(rule, fromMonthStr)
            triggerPush()
            onComplete()
        }
    }

    fun deleteRecurrenceRuleAndFuture(ruleId: Int, userId: String, fromMonthStr: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteRecurrenceRuleAndFuture(ruleId, userId, fromMonthStr)
            triggerPush()
            onComplete()
        }
    }

    fun insertCategory(category: Category, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertCategory(category)
            triggerPush()
            onComplete(id.toInt())
        }
    }

    fun insertSubcategory(subcategory: Subcategory, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertSubcategory(subcategory)
            triggerPush()
            onComplete(id.toInt())
        }
    }

    // --- BUDGET ALLOCATION ACTIONS ---
    fun insertBudgetAllocation(allocation: BudgetAllocation, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.insertBudgetAllocation(allocation)
            triggerPush()
            onComplete()
        }
    }

    fun updateBudgetAllocation(allocation: BudgetAllocation, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateBudgetAllocation(allocation)
            triggerPush()
            onComplete()
        }
    }

    fun deleteBudgetAllocation(allocation: BudgetAllocation, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteBudgetAllocation(allocation)
            triggerPush()
            onComplete()
        }
    }

    // --- GOALS ACTIONS ---
    fun insertGoal(goal: Goal, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.insertGoal(goal)
            triggerPush()
            onComplete()
        }
    }

    fun updateGoal(goal: Goal, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateGoal(goal)
            triggerPush()
            onComplete()
        }
    }

    fun deleteGoal(goal: Goal, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteGoal(goal)
            triggerPush()
            onComplete()
        }
    }

    // --- ALLOCATION MOVEMENT ACTIONS ---
    fun insertAllocationMovement(movement: AllocationMovement, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.insertAllocationMovement(movement)
            
            // Generate goal transaction of type "META"
            val targetGoalId = movement.dest_goal_id ?: movement.source_goal_id
            if (targetGoalId != null) {
                val goalName = repository.getGoalById(targetGoalId)?.name ?: "Meta"
                val accounts = repository.getAllAccounts(movement.userId)
                val accountId = accounts.firstOrNull { !it.archived }?.id ?: accounts.firstOrNull()?.id ?: 1
                val isAllocation = movement.dest_goal_id != null
                val prefix = if (isAllocation) "Alocação" else "Retirada"
                val finalDesc = "$prefix: $goalName" + (if (!movement.note.isNullOrBlank()) " - ${movement.note}" else "")
                val finalValue = if (isAllocation) movement.amount else -movement.amount
                val sdfDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(movement.moved_at))

                val tx = Transaction(
                    account_id = accountId,
                    to_account_id = null,
                    category_id = null,
                    subcategory_id = null,
                    type = "META",
                    value = finalValue,
                    description = finalDesc,
                    date = sdfDate,
                    installment_plan_id = null,
                    installment_number = null,
                    recurrence_rule_id = null,
                    synced = false,
                    userId = movement.userId,
                    goal_id = targetGoalId
                )
                repository.insertTransaction(tx)
            }

            triggerPush()
            onComplete()
        }
    }

    fun updateAllocationMovement(movement: AllocationMovement, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val allMovements = repository.getAllAllocationMovements(movement.userId)
            val oldMovement = allMovements.find { it.id == movement.id }

            repository.updateAllocationMovement(movement)

            if (oldMovement != null) {
                val targetGoalId = oldMovement.dest_goal_id ?: oldMovement.source_goal_id
                if (targetGoalId != null) {
                    val expectedOldValue = if (oldMovement.dest_goal_id != null) oldMovement.amount else -oldMovement.amount
                    val txs = repository.getAllTransactions(movement.userId)
                    val matchingTx = txs.find { tx ->
                        tx.type == "META" &&
                        tx.goal_id == targetGoalId &&
                        Math.abs(tx.value - expectedOldValue) < 0.01
                    }
                    if (matchingTx != null) {
                        val newValue = if (movement.dest_goal_id != null) movement.amount else -movement.amount
                        val goalName = repository.getGoalById(targetGoalId)?.name ?: "Meta"
                        val prefix = if (movement.dest_goal_id != null) "Alocação" else "Retirada"
                        val desc = "$prefix: $goalName" + (if (!movement.note.isNullOrBlank()) " - ${movement.note}" else "")
                        repository.updateTransaction(
                            matchingTx.copy(
                                value = newValue,
                                description = desc
                            )
                        )
                    }
                }
            }
            triggerPush()
            onComplete()
        }
    }

    fun deleteAllocationMovement(movement: AllocationMovement, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteAllocationMovement(movement)
            // Delete associated META transaction
            val targetGoalId = movement.dest_goal_id ?: movement.source_goal_id
            if (targetGoalId != null) {
                val expectedValue = if (movement.dest_goal_id != null) movement.amount else -movement.amount
                val txs = repository.getAllTransactions(movement.userId)
                val matchingTx = txs.find { tx ->
                    tx.type == "META" &&
                    tx.goal_id == targetGoalId &&
                    Math.abs(tx.value - expectedValue) < 0.01 &&
                    (movement.note == null || tx.description.contains(movement.note!!))
                }
                if (matchingTx != null) {
                    repository.deleteTransaction(matchingTx)
                }
            }
            triggerPush()
            onComplete()
        }
    }

    fun moveMoney(
        sourceCategoryId: Int?,
        sourceSubcategoryId: Int?,
        destCategoryId: Int?,
        destSubcategoryId: Int?,
        sourceGoalId: Int? = null,
        destGoalId: Int? = null,
        month: String,
        destMonth: String = month,
        amount: Double,
        note: String?,
        userId: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val sourceId = if (sourceCategoryId != null) {
                val existing = repository.getBudgetAllocation(sourceCategoryId, sourceSubcategoryId, month, userId)
                existing?.id ?: repository.insertBudgetAllocation(
                    BudgetAllocation(category_id = sourceCategoryId, subcategory_id = sourceSubcategoryId, month = month, planned_value = 0.0, userId = userId)
                ).toInt()
            } else {
                null
            }

            val destId = if (destCategoryId != null) {
                val existing = repository.getBudgetAllocation(destCategoryId, destSubcategoryId, destMonth, userId)
                existing?.id ?: repository.insertBudgetAllocation(
                    BudgetAllocation(category_id = destCategoryId, subcategory_id = destSubcategoryId, month = destMonth, planned_value = 0.0, userId = userId)
                ).toInt()
            } else {
                null
            }

            val timestamp = try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val parsedDate = sdf.parse("$month-02")
                parsedDate?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }

            val movement = AllocationMovement(
                source_budget_allocation_id = sourceId,
                source_goal_id = sourceGoalId,
                dest_budget_allocation_id = destId,
                dest_goal_id = destGoalId,
                amount = amount,
                note = note,
                moved_at = timestamp,
                userId = userId
            )
            repository.insertAllocationMovement(movement)

            // --- GENERATE GOAL TRANSACTION OF TYPE "META" ---
            val goalIdForTx = destGoalId ?: sourceGoalId
            if (goalIdForTx != null) {
                val goalName = repository.getGoalById(goalIdForTx)?.name ?: "Meta"
                val accounts = repository.getAllAccounts(userId)
                val accountId = accounts.firstOrNull { !it.archived }?.id ?: accounts.firstOrNull()?.id ?: 1
                val isAllocation = destGoalId != null
                val prefix = if (isAllocation) "Alocação" else "Retirada"
                val finalDesc = "$prefix: $goalName" + (if (!note.isNullOrBlank()) " - $note" else "")
                val finalValue = if (isAllocation) amount else -amount
                val sdfDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(timestamp))

                val tx = Transaction(
                    account_id = accountId,
                    to_account_id = null,
                    category_id = null,
                    subcategory_id = null,
                    type = "META",
                    value = finalValue,
                    description = finalDesc,
                    date = sdfDate,
                    installment_plan_id = null,
                    installment_number = null,
                    recurrence_rule_id = null,
                    synced = false,
                    userId = userId,
                    goal_id = goalIdForTx
                )
                repository.insertTransaction(tx)
            }

            triggerPush()
            onComplete()
        }
    }

    fun isMonthReviewedFlow(userId: String, month: String): Flow<Boolean> =
        userPreferences.isMonthReviewedFlow(userId, month)

    fun setMonthReviewed(userId: String, month: String, reviewed: Boolean) {
        viewModelScope.launch {
            userPreferences.setMonthReviewed(userId, month, reviewed)
        }
    }

    fun setPlannedValue(
        categoryId: Int,
        subcategoryId: Int?,
        month: String,
        plannedValue: Double,
        userId: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val existing = repository.getBudgetAllocation(categoryId, subcategoryId, month, userId)
            if (existing != null) {
                repository.updateBudgetAllocation(existing.copy(planned_value = plannedValue))
            } else {
                repository.insertBudgetAllocation(
                    BudgetAllocation(category_id = categoryId, subcategory_id = subcategoryId, month = month, planned_value = plannedValue, userId = userId)
                )
            }
            triggerPush()
            onComplete()
        }
    }


    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
