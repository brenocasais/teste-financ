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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val prontoParaAtribuirFlow: StateFlow<Double> = authState.flatMapLatest { auth ->
        val userId = authManager.currentUserId
        combine(
            repository.getAccountsFlow(userId),
            repository.getTransactionsFlow(userId),
            repository.getBudgetAllocationsFlow(userId),
            repository.getAllocationMovementsFlow(userId),
            repository.getGoalsFlow(userId),
            _selectedMonthCalendar
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            val accounts = args[0] as List<com.example.data.model.Account>
            @Suppress("UNCHECKED_CAST")
            val transactions = args[1] as List<com.example.data.model.Transaction>
            @Suppress("UNCHECKED_CAST")
            val budgetAllocations = args[2] as List<com.example.data.model.BudgetAllocation>
            @Suppress("UNCHECKED_CAST")
            val allocationMovements = args[3] as List<com.example.data.model.AllocationMovement>
            @Suppress("UNCHECKED_CAST")
            val goals = args[4] as List<com.example.data.model.Goal>
            val calendar = args[5] as java.util.Calendar

            val monthStr = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(calendar.time)
            calculateProntoParaAtribuirForMonth(
                accounts, transactions, budgetAllocations, allocationMovements, goals, monthStr
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getProntoParaAtribuirForMonth(monthStr: String): Flow<Double> {
        return authState.flatMapLatest { auth ->
            val userId = authManager.currentUserId
            combine(
                repository.getAccountsFlow(userId),
                repository.getTransactionsFlow(userId),
                repository.getBudgetAllocationsFlow(userId),
                repository.getAllocationMovementsFlow(userId),
                repository.getGoalsFlow(userId)
            ) { accounts, transactions, budgetAllocations, allocationMovements, goals ->
                calculateProntoParaAtribuirForMonth(
                    accounts, transactions, budgetAllocations, allocationMovements, goals, monthStr
                )
            }
        }
    }

    fun calculateProntoParaAtribuirForMonth(
        accounts: List<com.example.data.model.Account>,
        transactions: List<com.example.data.model.Transaction>,
        budgetAllocations: List<com.example.data.model.BudgetAllocation>,
        allocationMovements: List<com.example.data.model.AllocationMovement>,
        goals: List<com.example.data.model.Goal>,
        monthStr: String
    ): Double {
        val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)

        // 1. Total balance of all accounts up to monthStr
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

        // 2. Disponível of all BudgetAllocations in monthStr
        val allocsInMonth = budgetAllocations.filter { it.month == monthStr }
        val totalDisponivel = allocsInMonth.sumOf { alloc ->
            val alocado = allocationMovements.filter { it.dest_budget_allocation_id == alloc.id }.sumOf { it.amount } -
                          allocationMovements.filter { it.source_budget_allocation_id == alloc.id }.sumOf { it.amount }

            val gasto = transactions.filter {
                it.type == "DESPESA" &&
                it.date.startsWith(monthStr) &&
                it.category_id == alloc.category_id &&
                it.subcategory_id == alloc.subcategory_id
            }.sumOf { it.value }

            alocado - gasto
        }

        // 3. Current value of all Goals (via AllocationMovement up to monthStr)
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
                // Part 2, Step 5: Clean up any existing "META" type transactions from local DB (Room)
                viewModelScope.launch {
                    val metaTransactions = repository.getAllTransactions(userId).filter { it.type == "META" }
                    if (metaTransactions.isNotEmpty()) {
                        addSyncLog("Limpando ${metaTransactions.size} transações de metas obsoletas...")
                        metaTransactions.forEach { tx ->
                            repository.deleteTransaction(tx)
                        }
                    }
                }
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
            triggerPush()
            onComplete()
        }
    }

    fun updateAllocationMovement(movement: AllocationMovement, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateAllocationMovement(movement)
            triggerPush()
            onComplete()
        }
    }

    fun deleteAllocationMovement(movement: AllocationMovement, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteAllocationMovement(movement)
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
