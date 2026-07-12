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
                if (state is AuthManager.AuthState.Authenticated) {
                    addSyncLog("Usuário autenticado. Iniciando sincronização...")
                    triggerPull()
                } else if (state is AuthManager.AuthState.Guest) {
                    addSyncLog("Modo convidado ativo. Sincronização offline desativada.")
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

    // --- ALLOCATION MOVEMENT ACTIONS ---
    fun insertAllocationMovement(movement: AllocationMovement, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.insertAllocationMovement(movement)
            triggerPush()
            onComplete()
        }
    }

    fun moveMoney(
        sourceCategoryId: Int?,
        sourceSubcategoryId: Int?,
        destCategoryId: Int?,
        destSubcategoryId: Int?,
        month: String,
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
                val existing = repository.getBudgetAllocation(destCategoryId, destSubcategoryId, month, userId)
                existing?.id ?: repository.insertBudgetAllocation(
                    BudgetAllocation(category_id = destCategoryId, subcategory_id = destSubcategoryId, month = month, planned_value = 0.0, userId = userId)
                ).toInt()
            } else {
                null
            }

            val movement = AllocationMovement(
                source_budget_allocation_id = sourceId,
                source_goal_id = null,
                dest_budget_allocation_id = destId,
                dest_goal_id = null,
                amount = amount,
                note = note,
                moved_at = System.currentTimeMillis(),
                userId = userId
            )
            repository.insertAllocationMovement(movement)
            triggerPush()
            onComplete()
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
