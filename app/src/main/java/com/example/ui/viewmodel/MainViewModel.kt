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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MeuFinanceiroApplication
    val repository: FinanceRepository = app.repository
    val userPreferences: UserPreferences = app.userPreferences
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

        // 2. Disponível of all BudgetAllocations up to monthStr (accumulated)
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

    suspend fun exportAllDataJson(userId: String): String {
        return withContext(Dispatchers.IO) {
            val root = org.json.JSONObject()

            // Accounts
            val accountsArr = org.json.JSONArray()
            repository.getAllAccounts(userId).forEach {
                val obj = org.json.JSONObject()
                obj.put("id", it.id)
                obj.put("name", it.name)
                obj.put("type", it.type)
                obj.put("initial_balance", it.initial_balance)
                obj.put("archived", it.archived)
                accountsArr.put(obj)
            }
            root.put("accounts", accountsArr)

            // Envelope Groups
            val groupsArr = org.json.JSONArray()
            repository.getAllEnvelopeGroups(userId).forEach {
                val obj = org.json.JSONObject()
                obj.put("id", it.id)
                obj.put("name", it.name)
                obj.put("sort_order", it.sort_order)
                obj.put("budget_rule_type", it.budget_rule_type ?: org.json.JSONObject.NULL)
                obj.put("archived", it.archived)
                groupsArr.put(obj)
            }
            root.put("envelope_groups", groupsArr)

            // Categories
            val categoriesArr = org.json.JSONArray()
            repository.getAllCategories(userId).forEach {
                val obj = org.json.JSONObject()
                obj.put("id", it.id)
                obj.put("envelope_group_id", it.envelope_group_id ?: org.json.JSONObject.NULL)
                obj.put("name", it.name)
                obj.put("archived", it.archived)
                categoriesArr.put(obj)
            }
            root.put("categories", categoriesArr)

            // Subcategories
            val subcategoriesArr = org.json.JSONArray()
            repository.getAllSubcategories(userId).forEach {
                val obj = org.json.JSONObject()
                obj.put("id", it.id)
                obj.put("category_id", it.category_id)
                obj.put("name", it.name)
                obj.put("archived", it.archived)
                subcategoriesArr.put(obj)
            }
            root.put("subcategories", subcategoriesArr)

            // Transactions
            val transactionsArr = org.json.JSONArray()
            repository.getAllTransactions(userId).forEach {
                val obj = org.json.JSONObject()
                obj.put("id", it.id)
                obj.put("account_id", it.account_id)
                obj.put("to_account_id", it.to_account_id ?: org.json.JSONObject.NULL)
                obj.put("category_id", it.category_id ?: org.json.JSONObject.NULL)
                obj.put("subcategory_id", it.subcategory_id ?: org.json.JSONObject.NULL)
                obj.put("type", it.type)
                obj.put("value", it.value)
                obj.put("description", it.description)
                obj.put("date", it.date)
                obj.put("installment_plan_id", it.installment_plan_id ?: org.json.JSONObject.NULL)
                obj.put("installment_number", it.installment_number ?: org.json.JSONObject.NULL)
                obj.put("recurrence_rule_id", it.recurrence_rule_id ?: org.json.JSONObject.NULL)
                obj.put("is_recurrence_override", it.is_recurrence_override)
                obj.put("attachment_uri", it.attachment_uri ?: org.json.JSONObject.NULL)
                obj.put("attachment_name", it.attachment_name ?: org.json.JSONObject.NULL)
                obj.put("attachment_type", it.attachment_type ?: org.json.JSONObject.NULL)
                obj.put("synced", it.synced)
                obj.put("goal_id", it.goal_id ?: org.json.JSONObject.NULL)
                transactionsArr.put(obj)
            }
            root.put("transactions", transactionsArr)

            // Budget Allocations
            val allocationsArr = org.json.JSONArray()
            repository.getAllBudgetAllocations(userId).forEach {
                val obj = org.json.JSONObject()
                obj.put("id", it.id)
                obj.put("category_id", it.category_id)
                obj.put("subcategory_id", it.subcategory_id ?: org.json.JSONObject.NULL)
                obj.put("month", it.month)
                obj.put("planned_value", it.planned_value)
                allocationsArr.put(obj)
            }
            root.put("budget_allocations", allocationsArr)

            // Allocation Movements
            val movementsArr = org.json.JSONArray()
            repository.getAllAllocationMovements(userId).forEach {
                val obj = org.json.JSONObject()
                obj.put("id", it.id)
                obj.put("source_budget_allocation_id", it.source_budget_allocation_id ?: org.json.JSONObject.NULL)
                obj.put("source_goal_id", it.source_goal_id ?: org.json.JSONObject.NULL)
                obj.put("dest_budget_allocation_id", it.dest_budget_allocation_id ?: org.json.JSONObject.NULL)
                obj.put("dest_goal_id", it.dest_goal_id ?: org.json.JSONObject.NULL)
                obj.put("amount", it.amount)
                obj.put("note", it.note ?: org.json.JSONObject.NULL)
                obj.put("moved_at", it.moved_at)
                movementsArr.put(obj)
            }
            root.put("allocation_movements", movementsArr)

            // Installment Plans
            val plansArr = org.json.JSONArray()
            repository.getAllInstallmentPlans(userId).forEach {
                val obj = org.json.JSONObject()
                obj.put("id", it.id)
                obj.put("account_id", it.account_id)
                obj.put("category_id", it.category_id)
                obj.put("subcategory_id", it.subcategory_id ?: org.json.JSONObject.NULL)
                obj.put("description", it.description)
                obj.put("total_value", it.total_value)
                obj.put("installments_count", it.installments_count)
                obj.put("first_installment_month", it.first_installment_month)
                obj.put("created_at", it.created_at)
                plansArr.put(obj)
            }
            root.put("installment_plans", plansArr)

            // Recurrence Rules
            val rulesArr = org.json.JSONArray()
            repository.getAllRecurrenceRules(userId).forEach {
                val obj = org.json.JSONObject()
                obj.put("id", it.id)
                obj.put("account_id", it.account_id)
                obj.put("category_id", it.category_id)
                obj.put("subcategory_id", it.subcategory_id ?: org.json.JSONObject.NULL)
                obj.put("description", it.description)
                obj.put("value", it.value)
                obj.put("type", it.type)
                obj.put("frequency", it.frequency)
                obj.put("frequency_interval", it.frequency_interval)
                obj.put("start_date", it.start_date)
                obj.put("end_month", it.end_month ?: org.json.JSONObject.NULL)
                obj.put("active", it.active)
                rulesArr.put(obj)
            }
            root.put("recurrence_rules", rulesArr)

            // Goals
            val goalsArr = org.json.JSONArray()
            repository.getAllGoals(userId).forEach {
                val obj = org.json.JSONObject()
                obj.put("id", it.id)
                obj.put("name", it.name)
                obj.put("target_value", it.target_value)
                obj.put("start_date", it.start_date)
                obj.put("deadline", it.deadline)
                obj.put("color", it.color)
                obj.put("archived", it.archived)
                goalsArr.put(obj)
            }
            root.put("goals", goalsArr)

            // Notification Logs
            val logsArr = org.json.JSONArray()
            repository.getAllNotificationLogs(userId).forEach {
                val obj = org.json.JSONObject()
                obj.put("id", it.id)
                obj.put("type", it.type)
                obj.put("reference_id", it.reference_id ?: org.json.JSONObject.NULL)
                obj.put("reference_month", it.reference_month ?: org.json.JSONObject.NULL)
                obj.put("sent_at", it.sent_at)
                logsArr.put(obj)
            }
            root.put("notification_logs", logsArr)

            root.put("exported_at", System.currentTimeMillis())
            root.put("exported_by", userId)

            root.toString(2)
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

    val selectedAccountForDetail = MutableStateFlow<com.example.data.model.Account?>(null)
    val transactionSearchQuery = MutableStateFlow("")

    var previousTabBeforeHighlight: Int? = null

    fun triggerNotificationCheck() {
        val userId = currentUserId
        if (userId.isNotBlank()) {
            viewModelScope.launch {
                com.example.data.notification.NotificationTriggerManager.checkAndTriggerNotifications(
                    context = getApplication(),
                    repository = repository,
                    userPreferences = userPreferences,
                    userId = userId
                )
            }
        }
    }

    fun handleDeepLink(type: String, referenceId: String?, referenceMonth: String?) {
        viewModelScope.launch {
            when (type) {
                "CATEGORIA_80", "CATEGORIA_100" -> {
                    _navigateToTab.emit(2) // Planning tab
                }
                "FATURA_VENCENDO" -> {
                    _navigateToTab.emit(0) // Início tab
                    val accountId = referenceId?.toIntOrNull()
                    if (accountId != null) {
                        val accounts = repository.getAllAccounts(currentUserId)
                        val account = accounts.find { it.id == accountId }
                        if (account != null) {
                            selectedAccountForDetail.value = account
                        }
                    }
                }
                "PARCELA_VENCENDO" -> {
                    _navigateToTab.emit(1) // Transações tab
                    val planId = referenceId?.toIntOrNull()
                    if (planId != null) {
                        val plan = repository.getInstallmentPlanById(planId)
                        if (plan != null) {
                            transactionSearchQuery.value = plan.description
                        }
                    } else {
                        transactionSearchQuery.value = ""
                    }
                }
                "META_BATIDA" -> {
                    _navigateToTab.emit(4) // Metas tab
                }
                "REVISAO_SEMANAL" -> {
                    _navigateToTab.emit(0) // Início tab
                }
            }
        }
    }

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
            triggerNotificationCheck()
            onComplete()
        }
    }

    fun updateTransaction(transaction: Transaction, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
            triggerPush()
            triggerNotificationCheck()
            onComplete()
        }
    }

    fun deleteTransaction(transaction: Transaction, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
            triggerPush()
            triggerNotificationCheck()
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
            triggerNotificationCheck()
            onComplete()
        }
    }

    fun updateGoal(goal: Goal, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateGoal(goal)
            triggerPush()
            triggerNotificationCheck()
            onComplete()
        }
    }

    fun deleteGoal(goal: Goal, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteGoal(goal)
            triggerPush()
            triggerNotificationCheck()
            onComplete()
        }
    }

    // --- ALLOCATION MOVEMENT ACTIONS ---
    fun insertAllocationMovement(movement: AllocationMovement, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.insertAllocationMovement(movement)
            triggerPush()
            triggerNotificationCheck()
            onComplete()
        }
    }

    fun updateAllocationMovement(movement: AllocationMovement, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateAllocationMovement(movement)
            triggerPush()
            triggerNotificationCheck()
            onComplete()
        }
    }

    fun deleteAllocationMovement(movement: AllocationMovement, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteAllocationMovement(movement)
            triggerPush()
            triggerNotificationCheck()
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
