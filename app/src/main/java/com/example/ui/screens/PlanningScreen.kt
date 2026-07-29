package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.Dp
import com.example.data.model.Account
import com.example.data.model.AllocationMovement
import com.example.data.model.BudgetAllocation
import com.example.data.model.Category
import com.example.data.model.EnvelopeGroup
import com.example.data.model.Subcategory
import com.example.data.model.Transaction
import com.example.data.model.Goal
import com.example.ui.viewmodel.MainViewModel
import com.example.data.pref.dataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

fun getMonthsInRange(start: String, end: String): List<String> {
    if (start.isBlank() || end.isBlank()) return emptyList()
    val list = mutableListOf<String>()
    try {
        val startParts = start.split("-")
        val endParts = end.split("-")
        if (startParts.size < 2 || endParts.size < 2) return emptyList()
        val startYear = startParts[0].toIntOrNull() ?: 2026
        val startMonth = startParts[1].toIntOrNull() ?: 1
        val endYear = endParts[0].toIntOrNull() ?: 2026
        val endMonth = endParts[1].toIntOrNull() ?: 12

        var currYear = startYear
        var currMonth = startMonth
        while (currYear < endYear || (currYear == endYear && currMonth <= endMonth)) {
            val monthStr = "%d-%02d".format(currYear, currMonth)
            list.add(monthStr)
            currMonth++
            if (currMonth > 12) {
                currMonth = 1
                currYear++
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningScreen(viewModel: MainViewModel) {
    val userId = viewModel.currentUserId
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val selectedMonthCalendar by viewModel.selectedMonthCalendar.collectAsStateWithLifecycle()
    val currentMonthStr = remember(selectedMonthCalendar) {
        java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(selectedMonthCalendar.time)
    }

    // Database state flows
    val categories by viewModel.repository.getCategoriesFlow(userId).collectAsStateWithLifecycle(emptyList())
    val subcategories by viewModel.repository.getSubcategoriesFlow(userId).collectAsStateWithLifecycle(emptyList())
    val transactions by viewModel.repository.getTransactionsFlow(userId).collectAsStateWithLifecycle(emptyList())
    val budgetAllocations by viewModel.repository.getBudgetAllocationsFlow(userId).collectAsStateWithLifecycle(emptyList())
    val allocationMovements by viewModel.repository.getAllocationMovementsFlow(userId).collectAsStateWithLifecycle(emptyList())
    val goals by viewModel.repository.getGoalsFlow(userId).collectAsStateWithLifecycle(emptyList())

    val goalBalances = remember(goals, allocationMovements) {
        goals.associate { goal ->
            val destSum = allocationMovements.filter { it.dest_goal_id == goal.id }.sumOf { it.amount }
            val sourceSum = allocationMovements.filter { it.source_goal_id == goal.id }.sumOf { it.amount }
            goal.id to (destSum - sourceSum)
        }
    }

    val totalGoalsCurrentValue by remember(goalBalances) {
        derivedStateOf {
            goalBalances.values.sum()
        }
    }
    val accounts by viewModel.repository.getAccountsWithBalancesFlow(userId).collectAsStateWithLifecycle(emptyList())

    // Calculations & maps compiled reactively via derivedStateOf
    val totalAccountBalance by remember(accounts) {
        derivedStateOf { accounts.sumOf { it.initial_balance } }
    }

    val budgetAllocationsInMonth by remember(budgetAllocations, currentMonthStr) {
        derivedStateOf { budgetAllocations.filter { it.month == currentMonthStr } }
    }

    // Maps a Pair(categoryId, subcategoryId) to its (planned_value, allocated_value)
    val allocationInfoMap by remember(budgetAllocationsInMonth, allocationMovements) {
        derivedStateOf {
            budgetAllocationsInMonth.associate { alloc ->
                val id = alloc.id
                val allocated = allocationMovements.filter { it.dest_budget_allocation_id == id }.sumOf { it.amount } -
                                allocationMovements.filter { it.source_budget_allocation_id == id }.sumOf { it.amount }
                Pair(alloc.category_id, alloc.subcategory_id) to Pair(alloc.planned_value, allocated)
            }
        }
    }

    // Monthly spent values Map<Pair<categoryId, subcategoryId?>, spent_value>
    val spentInfoMap by remember(transactions, currentMonthStr) {
        derivedStateOf {
            val monthExpenses = transactions.filter { it.type == "DESPESA" && it.date.startsWith(currentMonthStr) }
            
            // Subcategory spent list
            val subMap = monthExpenses.filter { it.subcategory_id != null }.groupBy { Pair(it.category_id, it.subcategory_id) }
                .mapValues { entry -> entry.value.sumOf { it.value } }

            // Category spent list (all transactions in category_id)
            val catMap = monthExpenses.groupBy { Pair(it.category_id, null as Int?) }
                .mapValues { entry -> entry.value.sumOf { it.value } }

            subMap + catMap
        }
    }

    // Previous month calculations for closure
    val prevMonthStr = remember(currentMonthStr) {
        getPreviousMonthStr(currentMonthStr)
    }

    val budgetAllocationsInPrevMonth by remember(budgetAllocations, prevMonthStr) {
        derivedStateOf { budgetAllocations.filter { it.month == prevMonthStr } }
    }

    val allocationInfoMapPrevMonth by remember(budgetAllocationsInPrevMonth, allocationMovements) {
        derivedStateOf {
            budgetAllocationsInPrevMonth.associate { alloc ->
                val id = alloc.id
                val allocated = allocationMovements.filter { it.dest_budget_allocation_id == id }.sumOf { it.amount } -
                                allocationMovements.filter { it.source_budget_allocation_id == id }.sumOf { it.amount }
                Pair(alloc.category_id, alloc.subcategory_id) to Pair(alloc.planned_value, allocated)
            }
        }
    }

    val spentInfoMapPrevMonth by remember(transactions, prevMonthStr) {
        derivedStateOf {
            val monthExpenses = transactions.filter { it.type == "DESPESA" && it.date.startsWith(prevMonthStr) }
            
            val subMap = monthExpenses.filter { it.subcategory_id != null }.groupBy { Pair(it.category_id, it.subcategory_id) }
                .mapValues { entry -> entry.value.sumOf { it.value } }

            val catMap = monthExpenses.groupBy { Pair(it.category_id, null as Int?) }
                .mapValues { entry -> entry.value.sumOf { it.value } }

            subMap + catMap
        }
    }

    // Previous month Ready to Assign computation (Part 1: single source of truth via Flow)
    val prevProntoParaAtribuirFlow = remember(viewModel, prevMonthStr) {
        viewModel.getProntoParaAtribuirForMonth(prevMonthStr)
    }
    val prevProntoParaAtribuir by prevProntoParaAtribuirFlow.collectAsStateWithLifecycle(initialValue = 0.0)

    val hasAllocationInPrevMonth by remember(budgetAllocations, prevMonthStr) {
        derivedStateOf { budgetAllocations.any { it.month == prevMonthStr } }
    }

    val isPrevMonthReviewed by remember(userId, prevMonthStr) {
        viewModel.isMonthReviewedFlow(userId, prevMonthStr)
    }.collectAsStateWithLifecycle(initialValue = false)

    val prevSobraMap by remember(budgetAllocations, allocationMovements, transactions, categories, subcategories, prevMonthStr) {
        derivedStateOf {
            getCumulativeLeftoversMap(
                budgetAllocations = budgetAllocations,
                allocationMovements = allocationMovements,
                transactions = transactions,
                categories = categories,
                subcategories = subcategories,
                upToMonthStr = prevMonthStr
            )
        }
    }

    val currentLeftoversMap by remember(budgetAllocations, allocationMovements, transactions, categories, subcategories, currentMonthStr) {
        derivedStateOf {
            getCumulativeLeftoversMap(
                budgetAllocations = budgetAllocations,
                allocationMovements = allocationMovements,
                transactions = transactions,
                categories = categories,
                subcategories = subcategories,
                upToMonthStr = currentMonthStr
            )
        }
    }

    val realEconomizadoPrevMonth by remember(prevSobraMap) {
        derivedStateOf {
            val economizado = prevSobraMap.values.filter { it > 0.0 }.sum()
            val gastoAMais = prevSobraMap.values.filter { it < 0.0 }.sum()
            economizado + gastoAMais
        }
    }

    // Current month Ready to Assign computation (Part 1: single source of truth via Flow)
    val prontoParaAtribuir by viewModel.prontoParaAtribuirFlow.collectAsStateWithLifecycle()

    val saldoPrevMonthText = remember(realEconomizadoPrevMonth, currencyFormatter) {
        currencyFormatter.format(realEconomizadoPrevMonth)
    }

    val showClosureBanner = false

    var showMonthClosureScreen by remember { mutableStateOf(false) }
    var showMonthClosureDistributeDialog by remember { mutableStateOf(false) }
    var monthClosureSelectedPair by remember { mutableStateOf<Pair<Category, Subcategory?>?>(null) }

    // State of Dialogs
    var showDistributeDialog by remember { mutableStateOf(false) }
    var showNewEnvelopeDialog by remember { mutableStateOf(false) }
    var showRedistributionReportDialog by remember { mutableStateOf(false) }
    var targetEditAllocation by remember { mutableStateOf<Pair<Category, Subcategory?>?>(null) }
    var targetMoveAllocation by remember { mutableStateOf<Pair<Category, Subcategory?>?>(null) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val monthNameStr = remember(selectedMonthCalendar) {
        val sdf = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("pt", "BR"))
        val formatted = sdf.format(selectedMonthCalendar.time)
        formatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale("pt", "BR")) else it.toString() }
    }

    // Active expanded categories inside the tree
    var expandedCategoryIds by remember { mutableStateOf(setOf<Int>()) }

    var searchQuery by remember { mutableStateOf("") }
    val filteredCategories by remember(categories, subcategories, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                categories
            } else {
                categories.filter { cat ->
                    cat.name.contains(searchQuery, ignoreCase = true) ||
                    subcategories.any { sub -> sub.category_id == cat.id && sub.name.contains(searchQuery, ignoreCase = true) }
                }
            }
        }
    }

    var targetAlocarGoal by remember { mutableStateOf<Goal?>(null) }
    var targetAlocarGoalMode by remember { mutableStateOf<String?>(null) }

    val reviewedMonthsMap by remember(userId) {
        context.dataStore.data.map { preferences ->
            preferences.asMap().filterKeys { it.name.startsWith("reviewed_month_${userId}_") }
                .mapKeys { it.key.name.substringAfter("reviewed_month_${userId}_") }
                .mapValues { it.value as? Boolean ?: false }
        }
    }.collectAsStateWithLifecycle(initialValue = emptyMap())

    val goalPlannedValues by remember(goals, currentMonthStr, reviewedMonthsMap, allocationMovements, transactions) {
        derivedStateOf {
            goals.associate { g ->
                val startStr = g.start_date.ifBlank { currentMonthStr }
                val endStr = g.deadline.ifBlank { currentMonthStr }
                val range = getMonthsInRange(startStr, endStr)
                
                if (currentMonthStr < startStr) {
                    val totalMonths = range.size.coerceAtLeast(1)
                    g.id to (g.target_value / totalMonths)
                } else if (currentMonthStr > endStr) {
                    g.id to 0.0
                } else {
                    val firstOpenMonth = range.firstOrNull { reviewedMonthsMap[it] != true } ?: endStr
                    if (currentMonthStr < firstOpenMonth) {
                        val currentIndex = range.indexOf(currentMonthStr)
                        val accumulatedInClosedBefore = if (currentIndex > 0) {
                            val lastClosedMonth = range[currentIndex - 1]
                            val movesVal = allocationMovements.filter {
                                val moveMonth = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date(it.moved_at))
                                moveMonth <= lastClosedMonth
                            }.sumOf {
                                if (it.dest_goal_id == g.id) it.amount
                                else if (it.source_goal_id == g.id) -it.amount
                                else 0.0
                            }
                            val txsVal = transactions.filter {
                                it.type == "META" && it.goal_id == g.id && it.date.length >= 7 && it.date.substring(0, 7) <= lastClosedMonth
                            }.sumOf { it.value }
                            movesVal + txsVal
                        } else {
                            0.0
                        }
                        val remainingMonthsCount = (range.size - currentIndex).coerceAtLeast(1)
                        val plannedValue = ((g.target_value - accumulatedInClosedBefore) / remainingMonthsCount).coerceAtLeast(0.0)
                        g.id to plannedValue
                    } else {
                        val firstOpenIndex = range.indexOf(firstOpenMonth)
                        val accumulatedInClosed = if (firstOpenIndex > 0) {
                            val lastClosedMonth = range[firstOpenIndex - 1]
                            val movesVal = allocationMovements.filter {
                                val moveMonth = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date(it.moved_at))
                                moveMonth <= lastClosedMonth
                            }.sumOf {
                                if (it.dest_goal_id == g.id) it.amount
                                else if (it.source_goal_id == g.id) -it.amount
                                else 0.0
                            }
                            val txsVal = transactions.filter {
                                it.type == "META" && it.goal_id == g.id && it.date.length >= 7 && it.date.substring(0, 7) <= lastClosedMonth
                            }.sumOf { it.value }
                            movesVal + txsVal
                        } else {
                            0.0
                        }
                        val remainingMonthsCount = (range.size - firstOpenIndex).coerceAtLeast(1)
                        val plannedValue = ((g.target_value - accumulatedInClosed) / remainingMonthsCount).coerceAtLeast(0.0)
                        g.id to plannedValue
                    }
                }
            }
        }
    }

    val goalAllocatedValues by remember(goals, currentMonthStr, allocationMovements, transactions) {
        derivedStateOf {
            goals.associate { g ->
                val movesInMonth = allocationMovements.filter {
                    val moveMonth = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date(it.moved_at))
                    moveMonth == currentMonthStr
                }.sumOf {
                    if (it.dest_goal_id == g.id) it.amount
                    else if (it.source_goal_id == g.id) -it.amount
                    else 0.0
                }
                val txsInMonth = transactions.filter {
                    it.type == "META" && it.goal_id == g.id && it.date.startsWith(currentMonthStr)
                }.sumOf { it.value }
                g.id to (movesInMonth + txsInMonth)
            }
        }
    }

    val virtualGoalsCategory = remember(userId) {
        Category(id = -999, name = "Metas 🎯", archived = false, userId = userId)
    }

    val virtualGoalSubcategories by remember(goals, userId) {
        derivedStateOf {
            goals.map { g ->
                Subcategory(id = -1000 - g.id, category_id = -999, name = g.name, archived = g.archived, userId = userId)
            }
        }
    }

    val filteredCategoriesWithGoals by remember(filteredCategories, goals) {
        derivedStateOf {
            if (goals.isNotEmpty()) {
                filteredCategories + virtualGoalsCategory
            } else {
                filteredCategories
            }
        }
    }

    val filteredCategoriesByState by remember(filteredCategoriesWithGoals, selectedFilter, allocationInfoMap, spentInfoMap, prevSobraMap, subcategories, goalPlannedValues, goalAllocatedValues) {
        derivedStateOf {
            if (selectedFilter == "ALL") {
                filteredCategoriesWithGoals
            } else {
                filteredCategoriesWithGoals.filter { cat ->
                    val subs = if (cat.id == -999) virtualGoalSubcategories else subcategories.filter { it.category_id == cat.id }
                    var catPlanejado = 0.0
                    var catAlocado = 0.0
                    var catGasto = 0.0

                    if (cat.id == -999) {
                        subs.forEach { sub ->
                            val gId = -sub.id - 1000
                            catPlanejado += goalPlannedValues[gId] ?: 0.0
                            catAlocado += goalAllocatedValues[gId] ?: 0.0
                        }
                    } else if (subs.isEmpty()) {
                        val info = allocationInfoMap[Pair(cat.id, null)]
                        catPlanejado = info?.first ?: 0.0
                        catAlocado = (info?.second ?: 0.0) + (prevSobraMap[Pair(cat.id, null)] ?: 0.0)
                        catGasto = spentInfoMap[Pair(cat.id, null)] ?: 0.0
                    } else {
                        subs.forEach { sub ->
                            val info = allocationInfoMap[Pair(cat.id, sub.id)]
                            catPlanejado += info?.first ?: 0.0
                            catAlocado += (info?.second ?: 0.0) + (prevSobraMap[Pair(cat.id, sub.id)] ?: 0.0)
                            catGasto += spentInfoMap[Pair(cat.id, sub.id)] ?: 0.0
                        }
                    }

                    when (selectedFilter) {
                        "ALERT" -> catPlanejado != catAlocado || (catAlocado > 0 && catGasto / catAlocado >= 0.8 && catGasto <= catAlocado)
                        "EXCEEDED" -> catGasto > catAlocado || (catAlocado == 0.0 && catGasto > 0.0)
                        "AVAILABLE" -> (catAlocado - catGasto) > 0
                        else -> true
                    }
                }
            }
        }
    }

    // Categories start collapsed by default

    var expandedActionPanelId by remember { mutableStateOf<String?>(null) }
    var showQuickTransactionDialogFor by remember { mutableStateOf<Pair<Category, Subcategory?>?>(null) }
    var targetRenameAllocation by remember { mutableStateOf<Pair<Category, Subcategory?>?>(null) }
    var targetAlocarAllocation by remember { mutableStateOf<Pair<Category, Subcategory?>?>(null) }
    val showTransactionsHistoryFor by viewModel.activeHistoryDialog.collectAsStateWithLifecycle()

    fun quickAlignAlocadoToPlanejado(
        cat: Category,
        sub: Subcategory?,
        planejado: Double,
        alocado: Double
    ) {
        val diff = planejado - alocado
        if (diff == 0.0) return
        viewModel.moveMoney(
            sourceCategoryId = if (diff < 0.0) cat.id else null,
            sourceSubcategoryId = if (diff < 0.0) sub?.id else null,
            destCategoryId = if (diff > 0.0) cat.id else null,
            destSubcategoryId = if (diff > 0.0) sub?.id else null,
            month = currentMonthStr,
            amount = if (diff > 0.0) diff else -diff,
            note = "Ajuste rápido (⚡) - Igualar ao Planejado",
            userId = userId,
            onComplete = {
                val formattedAmount = String.format("%.2f", if (diff > 0.0) diff else -diff)
                val targetName = sub?.name ?: cat.name
                val msg = if (diff > 0.0) {
                    "Alocados R$ $formattedAmount de Pronto para Atribuir para $targetName"
                } else {
                    "Retornados R$ $formattedAmount de $targetName para Pronto para Atribuir"
                }
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    fun quickAlignGoalAlocadoToPlanejado(
        goal: Goal,
        planejado: Double,
        alocado: Double
    ) {
        val diff = planejado - alocado
        if (diff == 0.0) return
        viewModel.moveMoney(
            sourceCategoryId = null,
            sourceSubcategoryId = null,
            destCategoryId = null,
            destSubcategoryId = null,
            sourceGoalId = if (diff < 0.0) goal.id else null,
            destGoalId = if (diff > 0.0) goal.id else null,
            month = currentMonthStr,
            amount = if (diff > 0.0) diff else -diff,
            note = "Ajuste rápido (⚡) - Igualar ao Planejado",
            userId = userId,
            onComplete = {
                val formattedAmount = String.format("%.2f", if (diff > 0.0) diff else -diff)
                val msg = if (diff > 0.0) {
                    "Alocados R$ $formattedAmount de Pronto para Atribuir para a meta ${goal.name}"
                } else {
                    "Retornados R$ $formattedAmount da meta ${goal.name} para Pronto para Atribuir"
                }
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showMonthClosureScreen) {
        MonthClosureContent(
            viewModel = viewModel,
            prevMonthStr = prevMonthStr,
            currentMonthStr = currentMonthStr,
            categories = categories,
            subcategories = subcategories,
            prevAllocationInfoMap = allocationInfoMapPrevMonth,
            currentAllocationInfoMap = allocationInfoMap,
            prevSpentInfoMap = spentInfoMapPrevMonth,
            prevSobraMap = prevSobraMap,
            prontoParaAtribuir = prontoParaAtribuir,
            prevProntoParaAtribuir = prevProntoParaAtribuir,
            isPrevMonthReviewed = isPrevMonthReviewed,
            onBack = { showMonthClosureScreen = false },
            onRedistribute = { cat, sub ->
                monthClosureSelectedPair = Pair(cat, sub)
                showMonthClosureDistributeDialog = true
            }
        )
    } else {
        val isDark = isSystemInDarkTheme()
        val bgColor = if (isDark) Color(0xFF0D1315) else Color(0xFFFAFAFB)
        val cardBgColor = if (isDark) Color(0xFF172022) else Color(0xFFFFFFFF)
        val cardBorderColor = if (isDark) Color(0xFF283438) else Color(0xFFE6E9EC)
        val primaryTextColor = if (isDark) Color(0xFFF5F7F8) else Color(0xFF111827)
        val secondaryTextColor = if (isDark) Color(0xFF9FA9AB) else Color(0xFF6B7280)
        val greenColor = if (isDark) Color(0xFF39D47A) else Color(0xFF22A45D)
        val redColor = if (isDark) Color(0xFFFF4D55) else Color(0xFFEF4444)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(horizontal = 10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header (Título "Planejamento" + Month selector compacto "Julho 2026")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Planejamento",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showMonthPicker = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = monthNameStr,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Selecionar Mês",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (showClosureBanner) {
                    MonthClosureBanner(
                        monthName = formatMonthPortuguese(prevMonthStr),
                        isReviewed = isPrevMonthReviewed,
                        saldoPrevMonthText = saldoPrevMonthText,
                        onClick = { showMonthClosureScreen = true }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Card de Resumo (Altura reduzida em 20%)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "Pronto para atribuir",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = secondaryTextColor
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = currencyFormatter.format(prontoParaAtribuir),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (prontoParaAtribuir >= 0) greenColor else redColor,
                                    modifier = Modifier.testTag("pronto_para_atribuir_value")
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .width(1.dp)
                                    .background(cardBorderColor)
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "Sobra do mês anterior",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = secondaryTextColor
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = currencyFormatter.format(realEconomizadoPrevMonth),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (realEconomizadoPrevMonth >= 0) greenColor else redColor
                                )
                            }
                        }

                        // Três Ações: Distribuir, Nova Categoria, Relatório (Botões compactos)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { showDistributeDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = greenColor.copy(alpha = 0.2f),
                                    contentColor = greenColor
                                ),
                                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp)
                            ) {
                                Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Distribuir", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { showNewEnvelopeDialog = true },
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(34.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                                border = BorderStroke(1.dp, cardBorderColor),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryTextColor)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Nova Categoria", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { showRedistributionReportDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                                border = BorderStroke(1.dp, cardBorderColor),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryTextColor)
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Relatório", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Campo de busca compacto
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(cardBgColor, RoundedCornerShape(10.dp))
                            .border(1.dp, cardBorderColor, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar",
                                modifier = Modifier.size(18.dp),
                                tint = secondaryTextColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text("Buscar categoria ou subcategoria", fontSize = 13.sp, color = secondaryTextColor)
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 13.sp,
                                        color = primaryTextColor,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    cursorBrush = SolidColor(greenColor),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Limpar",
                                    tint = secondaryTextColor,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { searchQuery = "" }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Chips de filtro por estado compactos e distribuídos igualmente
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val filters = listOf(
                        "ALL" to "Todas",
                        "ALERT" to "Alertas",
                        "EXCEEDED" to "Excedidas",
                        "AVAILABLE" to "Disponíveis"
                    )

                    filters.forEach { (key, label) ->
                        val isSelected = selectedFilter == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) greenColor.copy(alpha = 0.15f) else cardBgColor)
                                .border(
                                    BorderStroke(1.dp, if (isSelected) greenColor else cardBorderColor),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedFilter = key },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) greenColor else secondaryTextColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // List Content (Category -> Subcategory hierarchy)
                Box(modifier = Modifier.weight(1f)) {
                    if (filteredCategoriesByState.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    tint = secondaryTextColor.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = if (searchQuery.isNotEmpty() || selectedFilter != "ALL") "Nenhuma categoria encontrada" else "Nenhuma categoria cadastrada",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryTextColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredCategoriesByState, key = { "cat_${it.id}" }) { cat ->
                                val isCatExpanded = if (searchQuery.isNotEmpty()) true else expandedCategoryIds.contains(cat.id)
                                val subsInCat = if (cat.id == -999) {
                                    virtualGoalSubcategories
                                } else if (searchQuery.isEmpty()) {
                                    subcategories.filter { it.category_id == cat.id }
                                } else {
                                    subcategories.filter { sub ->
                                        sub.category_id == cat.id && (
                                            cat.name.contains(searchQuery, ignoreCase = true) ||
                                            sub.name.contains(searchQuery, ignoreCase = true)
                                        )
                                    }
                                }

                                var catPlanejado = 0.0
                                var catAlocado = 0.0
                                var catGasto = 0.0

                                if (cat.id == -999) {
                                    virtualGoalSubcategories.forEach { sub ->
                                        val gId = -sub.id - 1000
                                        catPlanejado += goalPlannedValues[gId] ?: 0.0
                                        catAlocado += goalAllocatedValues[gId] ?: 0.0
                                    }
                                } else if (subsInCat.isEmpty()) {
                                    val subInfo = allocationInfoMap[Pair(cat.id, null)]
                                    catPlanejado = subInfo?.first ?: 0.0
                                    catAlocado = (subInfo?.second ?: 0.0) + (prevSobraMap[Pair(cat.id, null)] ?: 0.0)
                                    catGasto = spentInfoMap[Pair(cat.id, null)] ?: 0.0
                                } else {
                                    subsInCat.forEach { sub ->
                                        val subInfo = allocationInfoMap[Pair(cat.id, sub.id)]
                                        catPlanejado += subInfo?.first ?: 0.0
                                        catAlocado += (subInfo?.second ?: 0.0) + (prevSobraMap[Pair(cat.id, sub.id)] ?: 0.0)
                                        catGasto += spentInfoMap[Pair(cat.id, sub.id)] ?: 0.0
                                    }
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                    border = BorderStroke(1.dp, cardBorderColor),
                                    elevation = CardDefaults.cardElevation(0.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp)
                                    ) {
                                        CategoryRowItem(
                                            category = cat,
                                            planejado = catPlanejado,
                                            alocado = catAlocado,
                                            gasto = catGasto,
                                            isExpanded = isCatExpanded,
                                            onToggle = {
                                                expandedCategoryIds = if (isCatExpanded) {
                                                    expandedCategoryIds - cat.id
                                                } else {
                                                    expandedCategoryIds + cat.id
                                                }
                                            },
                                            onRowClick = {
                                                // CLICAR NA CATEGORIA SEMPRE EXPANDE/RECOLHE AS SUBCATEGORIAS
                                                expandedCategoryIds = if (isCatExpanded) {
                                                    expandedCategoryIds - cat.id
                                                } else {
                                                    expandedCategoryIds + cat.id
                                                }
                                            },
                                            onRowLongClick = {
                                                if (cat.id != -999) {
                                                    viewModel.showHistoryDialog(cat, null)
                                                }
                                            }
                                        )

                                        if (isCatExpanded) {
                                            if (subsInCat.isEmpty()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "Nenhuma subcategoria.",
                                                    fontSize = 12.sp,
                                                    color = secondaryTextColor,
                                                    modifier = Modifier.padding(start = 52.dp)
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                subsInCat.forEachIndexed { index, sub ->
                                                    val isActionPanelExpanded = expandedActionPanelId == "sub_${sub.id}"
                                                    val isFirst = index == 0
                                                    val isLast = index == subsInCat.size - 1

                                                    if (cat.id == -999) {
                                                        val gId = -sub.id - 1000
                                                        val goal = goals.firstOrNull { it.id == gId }
                                                        val subPlanejado = goalPlannedValues[gId] ?: 0.0
                                                        val subAlocadoWithRollover = goalAllocatedValues[gId] ?: 0.0
                                                        val subGasto = 0.0

                                                        SubcategoryRowItem(
                                                            category = cat,
                                                            subcategory = sub,
                                                            planejado = subPlanejado,
                                                            alocado = subAlocadoWithRollover,
                                                            gasto = subGasto,
                                                            isFirst = isFirst,
                                                            isLast = isLast,
                                                            isActionPanelExpanded = isActionPanelExpanded,
                                                            onRowClick = {
                                                                expandedActionPanelId = if (isActionPanelExpanded) null else "sub_${sub.id}"
                                                            },
                                                            onRowLongClick = {},
                                                            onEditPlanned = {},
                                                            onEditAllocated = {
                                                                targetAlocarGoal = goal
                                                                targetAlocarGoalMode = "APORTAR"
                                                            },
                                                            onMoveMoney = {
                                                                targetAlocarGoal = goal
                                                                targetAlocarGoalMode = "RETIRAR"
                                                            },
                                                            onNewTransaction = {},
                                                            onQuickAlign = {
                                                                if (goal != null) {
                                                                    quickAlignGoalAlocadoToPlanejado(goal, subPlanejado, subAlocadoWithRollover)
                                                                }
                                                            }
                                                        )
                                                    } else {
                                                        val subInfo = allocationInfoMap[Pair(cat.id, sub.id)]
                                                        val subPlanejado = subInfo?.first ?: 0.0
                                                        val subAlocado = subInfo?.second ?: 0.0
                                                        val subGasto = spentInfoMap[Pair(cat.id, sub.id)] ?: 0.0
                                                        val prevSobra = prevSobraMap[Pair(cat.id, sub.id)] ?: 0.0
                                                        val subAlocadoWithRollover = subAlocado + prevSobra

                                                        SubcategoryRowItem(
                                                            category = cat,
                                                            subcategory = sub,
                                                            planejado = subPlanejado,
                                                            alocado = subAlocadoWithRollover,
                                                            gasto = subGasto,
                                                            isFirst = isFirst,
                                                            isLast = isLast,
                                                            isActionPanelExpanded = isActionPanelExpanded,
                                                            onRowClick = {
                                                                expandedActionPanelId = if (isActionPanelExpanded) null else "sub_${sub.id}"
                                                            },
                                                            onRowLongClick = {
                                                                viewModel.showHistoryDialog(cat, sub)
                                                            },
                                                            onEditPlanned = { targetEditAllocation = Pair(cat, sub) },
                                                            onEditAllocated = { targetAlocarAllocation = Pair(cat, sub) },
                                                            onMoveMoney = { targetMoveAllocation = Pair(cat, sub) },
                                                            onNewTransaction = { showQuickTransactionDialogFor = Pair(cat, sub) },
                                                            onQuickAlign = {
                                                                quickAlignAlocadoToPlanejado(cat, sub, subPlanejado, subAlocadoWithRollover)
                                                            }
                                                        )
                                                    }

                                                    if (!isLast) {
                                                        Spacer(modifier = Modifier.height(10.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMonthPicker) {
        MonthYearPickerDialog(
            currentCalendar = selectedMonthCalendar,
            onDismiss = { showMonthPicker = false },
            onSelected = { year, month ->
                val newCal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, year)
                    set(java.util.Calendar.MONTH, month)
                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                }
                viewModel.setSelectedMonth(newCal)
                showMonthPicker = false
            }
        )
    }

    // --- DIALOGS IMPLEMENTATION ---

    // 1. Distribuir Dinheiro (Move money between buckets / Pronto para Atribuir)
    if (showDistributeDialog || targetMoveAllocation != null || targetAlocarGoal != null) {
        DistributeDialog(
            viewModel = viewModel,
            prontoParaAtribuir = prontoParaAtribuir,
            preSelectedPair = targetMoveAllocation,
            categories = categories,
            subcategories = subcategories,
            allocationInfoMap = allocationInfoMap,
            sourceMonth = currentMonthStr,
            sourceLeftovers = currentLeftoversMap,
            destLeftovers = currentLeftoversMap,
            goals = goals,
            preSelectedGoal = targetAlocarGoal,
            preSelectedGoalMode = targetAlocarGoalMode,
            onDismiss = {
                showDistributeDialog = false
                targetMoveAllocation = null
                targetAlocarGoal = null
                targetAlocarGoalMode = null
            }
        )
    }

    if (showMonthClosureDistributeDialog && monthClosureSelectedPair != null) {
        DistributeDialog(
            viewModel = viewModel,
            prontoParaAtribuir = prontoParaAtribuir,
            sourceProntoParaAtribuir = prevProntoParaAtribuir,
            preSelectedPair = monthClosureSelectedPair,
            categories = categories,
            subcategories = subcategories,
            allocationInfoMap = allocationInfoMapPrevMonth,
            destAllocationInfoMap = allocationInfoMap,
            sourceMonth = prevMonthStr,
            destMonth = currentMonthStr,
            sourceLeftovers = prevSobraMap,
            destLeftovers = currentLeftoversMap,
            onDismiss = {
                showMonthClosureDistributeDialog = false
                monthClosureSelectedPair = null
            }
        )
    }

    if (showRedistributionReportDialog) {
        RedistributionReportDialog(
            viewModel = viewModel,
            currentMonthStr = currentMonthStr,
            prevMonthStr = prevMonthStr,
            categories = categories,
            subcategories = subcategories,
            budgetAllocations = budgetAllocations,
            allocationMovements = allocationMovements,
            onDismiss = { showRedistributionReportDialog = false }
        )
    }

    // 2. Nova Categoria (Create new Category with required Subcategory)
    if (showNewEnvelopeDialog) {
        var catName by remember { mutableStateOf("") }
        var subName by remember { mutableStateOf("") }
        var errorText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewEnvelopeDialog = false },
            title = { Text("Nova Categoria", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (errorText.isNotEmpty()) {
                        Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = catName,
                        onValueChange = { catName = it },
                        label = { Text("Nome da Categoria") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = subName,
                        onValueChange = { subName = it },
                        label = { Text("Nome da primeira subcategoria") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (catName.isBlank() || subName.isBlank()) {
                            errorText = "Categoria e Subcategoria são obrigatórias."
                        } else {
                            coroutineScope.launch {
                                val catId = viewModel.repository.insertCategory(
                                    Category(
                                        name = catName.trim(),
                                        userId = userId
                                    )
                                )
                                viewModel.repository.insertSubcategory(
                                    Subcategory(
                                        category_id = catId.toInt(),
                                        name = subName.trim(),
                                        userId = userId
                                    )
                                )
                                viewModel.triggerPush()
                            }
                            showNewEnvelopeDialog = false
                            catName = ""
                            subName = ""
                            errorText = ""
                        }
                    },
                    enabled = catName.isNotBlank() && subName.isNotBlank()
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewEnvelopeDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showTransactionsHistoryFor != null) {
        val pair = showTransactionsHistoryFor!!
        val cat = pair.first
        val sub = pair.second
        val filteredTxs = remember(transactions, cat, sub, currentMonthStr) {
            transactions.filter { tx ->
                val matchesCategory = tx.category_id == cat.id
                val matchesSubcategory = if (sub != null) tx.subcategory_id == sub.id else true
                val matchesMonth = tx.date.startsWith(currentMonthStr)
                matchesCategory && matchesSubcategory && matchesMonth
            }
        }
        CategoryTransactionsHistoryDialog(
            category = cat,
            subcategory = sub,
            monthStr = currentMonthStr,
            transactions = filteredTxs,
            accounts = accounts,
            onDismiss = { viewModel.dismissHistoryDialog() },
            onTransactionClick = { tx ->
                viewModel.navigateToTransaction(tx)
            }
        )
    }

    // 3. Atribuir Planejado (Edit monthly planned target)
    if (targetEditAllocation != null) {
        val pair = targetEditAllocation!!
        val cat = pair.first
        val sub = pair.second
        val currentInfo = allocationInfoMap[Pair(cat.id, sub?.id)]
        val currentPlanejado = currentInfo?.first ?: 0.0
        var planejadoInput by remember { mutableStateOf("") }
        var recurrenceOption by remember { mutableStateOf(0) } // 0 = Apenas este mês, 1 = Recorrente (Personalizado)
        var repeatIntervalStr by remember { mutableStateOf("1") }
        var isUnitYears by remember { mutableStateOf(false) }
        var endOption by remember { mutableStateOf(0) } // 0 = Nunca, 1 = Até uma data
        var endCalendar by remember {
            mutableStateOf(
                java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.YEAR, 1)
                }
            )
        }
        var showEndDatePicker by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { targetEditAllocation = null },
            title = {
                Text(
                    text = "Definir Planejado\n(${if (sub != null) "${cat.name} > ${sub.name}" else cat.name})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Valor planejado atual: ${currencyFormatter.format(currentPlanejado)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = planejadoInput,
                        onValueChange = { planejadoInput = it },
                        label = { Text("Novo Valor Planejado") },
                        placeholder = { Text(currentPlanejado.toString()) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        prefix = { Text("R$ ") }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Autorenew,
                                contentDescription = null,
                                tint = if (recurrenceOption == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Repetir planejamento?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tornar este planejamento recorrente",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = recurrenceOption == 1,
                            onCheckedChange = { recurrenceOption = if (it) 1 else 0 }
                        )
                    }

                    if (recurrenceOption == 1) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 28.dp, top = 4.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Repetir a cada", style = MaterialTheme.typography.bodyMedium)
                                OutlinedTextField(
                                    value = repeatIntervalStr,
                                    onValueChange = { input ->
                                        if (input.all { it.isDigit() } && input.length <= 2) {
                                            repeatIntervalStr = input
                                        }
                                    },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                                    modifier = Modifier.width(54.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Text(
                                        text = "Meses",
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (!isUnitYears) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { isUnitYears = false }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (!isUnitYears) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Anos",
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isUnitYears) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { isUnitYears = true }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isUnitYears) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Definir mês de término?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Switch(
                                    checked = endOption == 1,
                                    onCheckedChange = { endOption = if (it) 1 else 0 }
                                )
                            }

                            if (endOption == 1) {
                                val endMonthFormatter = remember { java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("pt", "BR")) }
                                val displayEndMonth = remember(endCalendar) {
                                    endMonthFormatter.format(endCalendar.time).replaceFirstChar { it.uppercase() }
                                }
                                OutlinedButton(
                                    onClick = { showEndDatePicker = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Terminar em: $displayEndMonth")
                                }
                            }
                        }
                    }

                    if (showEndDatePicker) {
                        MonthYearPickerDialog(
                            currentCalendar = endCalendar,
                            onDismiss = { showEndDatePicker = false },
                            onSelected = { year, month ->
                                endCalendar = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                                    set(java.util.Calendar.YEAR, year)
                                    set(java.util.Calendar.MONTH, month)
                                }
                                showEndDatePicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                val isInputValid = planejadoInput.isNotBlank() && planejadoInput.toDoubleOrNull() != null
                Button(
                    onClick = {
                        val value = planejadoInput.toDoubleOrNull() ?: 0.0
                        if (recurrenceOption == 0) {
                            viewModel.setPlannedValue(
                                categoryId = cat.id,
                                subcategoryId = sub?.id,
                                month = currentMonthStr,
                                plannedValue = value,
                                userId = userId,
                                onComplete = { targetEditAllocation = null }
                            )
                        } else {
                            val interval = repeatIntervalStr.toIntOrNull() ?: 1
                            val futureMonths = getCustomRecurrenceMonths(
                                startMonthStr = currentMonthStr,
                                repeatInterval = interval,
                                isUnitYears = isUnitYears,
                                endOption = endOption,
                                endCalendar = if (endOption == 1) endCalendar else null
                            )
                            var completedCount = 0
                            futureMonths.forEach { m ->
                                viewModel.setPlannedValue(
                                    categoryId = cat.id,
                                    subcategoryId = sub?.id,
                                    month = m,
                                    plannedValue = value,
                                    userId = userId,
                                    onComplete = {
                                        completedCount++
                                        if (completedCount == futureMonths.size) {
                                            targetEditAllocation = null
                                        }
                                    }
                                )
                            }
                        }
                    },
                    enabled = isInputValid
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { targetEditAllocation = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // 4. Renomear Categoria/Subcategoria
    if (targetRenameAllocation != null) {
        val pair = targetRenameAllocation!!
        val cat = pair.first
        val sub = pair.second
        var nameInput by remember { mutableStateOf(sub?.name ?: cat.name) }

        AlertDialog(
            onDismissRequest = { targetRenameAllocation = null },
            title = {
                Text(
                    text = "Renomear ${if (sub != null) "Subcategoria" else "Categoria"}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Novo Nome") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            coroutineScope.launch {
                                if (sub != null) {
                                    viewModel.repository.updateSubcategory(sub.copy(name = nameInput, userId = userId))
                                } else {
                                    viewModel.repository.updateCategory(cat.copy(name = nameInput, userId = userId))
                                }
                                viewModel.triggerPush()
                                targetRenameAllocation = null
                            }
                        }
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { targetRenameAllocation = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // 5. Quick Transaction Dialog (Complete dialog from Transactions screen)
    if (showQuickTransactionDialogFor != null) {
        val pair = showQuickTransactionDialogFor!!
        val cat = pair.first
        val sub = pair.second

        TransactionAddEditDialog(
            viewModel = viewModel,
            accounts = accounts,
            categories = categories,
            subcategories = subcategories,
            transactionToEdit = null,
            preSelectedCategory = cat,
            preSelectedSubcategory = sub,
            onDismiss = { showQuickTransactionDialogFor = null }
        )
    }

    // 6. Alocar Dinheiro diretamente
    if (targetAlocarAllocation != null) {
        val pair = targetAlocarAllocation!!
        val cat = pair.first
        val sub = pair.second
        val currentInfo = allocationInfoMap[Pair(cat.id, sub?.id)]
        val currentPlanejado = currentInfo?.first ?: 0.0
        val currentAlocado = currentInfo?.second ?: 0.0
        var alocadoInput by remember { mutableStateOf("") }
        var recurrenceOption by remember { mutableStateOf(0) } // 0 = Apenas este mês, 1 = Recorrente (Personalizado)
        var repeatIntervalStr by remember { mutableStateOf("1") }
        var isUnitYears by remember { mutableStateOf(false) }
        var endOption by remember { mutableStateOf(0) } // 0 = Nunca, 1 = Até uma data
        var endCalendar by remember {
            mutableStateOf(
                java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.YEAR, 1)
                }
            )
        }
        var showEndDatePicker by remember { mutableStateOf(false) }

        // Helper to check allocation in future months
        val getAllocatedValueInMonth = remember(budgetAllocations, allocationMovements) {
            { catId: Int, subId: Int?, m: String ->
                val alloc = budgetAllocations.find { it.category_id == catId && it.subcategory_id == subId && it.month == m }
                if (alloc == null) 0.0
                else {
                    allocationMovements.filter { it.dest_budget_allocation_id == alloc.id }.sumOf { it.amount } -
                    allocationMovements.filter { it.source_budget_allocation_id == alloc.id }.sumOf { it.amount }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { targetAlocarAllocation = null },
            title = {
                Text(
                    text = "Alocar Dinheiro\n(${if (sub != null) "${cat.name} > ${sub.name}" else cat.name})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Saldo disponível para alocar: ${currencyFormatter.format(prontoParaAtribuir)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Text(
                        text = "Valor Planejado para este mês: ${currencyFormatter.format(currentPlanejado)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Text(
                        text = "Valor atualmente alocado: ${currencyFormatter.format(currentAlocado)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = alocadoInput,
                        onValueChange = { alocadoInput = it },
                        label = { Text("Novo Valor Alocado Total") },
                        placeholder = { Text(currentAlocado.toString()) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        prefix = { Text("R$ ") }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Autorenew,
                                contentDescription = null,
                                tint = if (recurrenceOption == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Repetir planejamento?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tornar este planejamento recorrente",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = recurrenceOption == 1,
                            onCheckedChange = { recurrenceOption = if (it) 1 else 0 }
                        )
                    }

                    if (recurrenceOption == 1) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 28.dp, top = 4.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Repetir a cada", style = MaterialTheme.typography.bodyMedium)
                                OutlinedTextField(
                                    value = repeatIntervalStr,
                                    onValueChange = { input ->
                                        if (input.all { it.isDigit() } && input.length <= 2) {
                                            repeatIntervalStr = input
                                        }
                                    },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                                    modifier = Modifier.width(54.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Text(
                                        text = "Meses",
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (!isUnitYears) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { isUnitYears = false }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (!isUnitYears) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Anos",
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isUnitYears) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { isUnitYears = true }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isUnitYears) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Definir mês de término?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Switch(
                                    checked = endOption == 1,
                                    onCheckedChange = { endOption = if (it) 1 else 0 }
                                )
                            }

                            if (endOption == 1) {
                                val endMonthFormatter = remember { java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("pt", "BR")) }
                                val displayEndMonth = remember(endCalendar) {
                                    endMonthFormatter.format(endCalendar.time).replaceFirstChar { it.uppercase() }
                                }
                                OutlinedButton(
                                    onClick = { showEndDatePicker = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Terminar em: $displayEndMonth")
                                }
                            }
                        }
                    }

                    if (showEndDatePicker) {
                        MonthYearPickerDialog(
                            currentCalendar = endCalendar,
                            onDismiss = { showEndDatePicker = false },
                            onSelected = { year, month ->
                                endCalendar = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                                    set(java.util.Calendar.YEAR, year)
                                    set(java.util.Calendar.MONTH, month)
                                }
                                showEndDatePicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                val isInputValid = alocadoInput.isNotBlank() && alocadoInput.toDoubleOrNull() != null
                Button(
                    onClick = {
                        val newValue = alocadoInput.toDoubleOrNull() ?: 0.0
                        if (recurrenceOption == 0) {
                            val diff = newValue - currentAlocado
                            if (diff != 0.0) {
                                viewModel.moveMoney(
                                    sourceCategoryId = if (diff < 0.0) cat.id else null,
                                    sourceSubcategoryId = if (diff < 0.0) sub?.id else null,
                                    destCategoryId = if (diff > 0.0) cat.id else null,
                                    destSubcategoryId = if (diff > 0.0) sub?.id else null,
                                    month = currentMonthStr,
                                    amount = if (diff > 0.0) diff else -diff,
                                    note = "Alocação manual direta",
                                    userId = userId,
                                    onComplete = { targetAlocarAllocation = null }
                                )
                            } else {
                                targetAlocarAllocation = null
                            }
                        } else {
                            val interval = repeatIntervalStr.toIntOrNull() ?: 1
                            val futureMonths = getCustomRecurrenceMonths(
                                startMonthStr = currentMonthStr,
                                repeatInterval = interval,
                                isUnitYears = isUnitYears,
                                endOption = endOption,
                                endCalendar = if (endOption == 1) endCalendar else null
                            )
                            var completedCount = 0
                            futureMonths.forEach { m ->
                                val futureAlocado = getAllocatedValueInMonth(cat.id, sub?.id, m)
                                val diff = newValue - futureAlocado
                                if (diff != 0.0) {
                                    viewModel.moveMoney(
                                        sourceCategoryId = if (diff < 0.0) cat.id else null,
                                        sourceSubcategoryId = if (diff < 0.0) sub?.id else null,
                                        destCategoryId = if (diff > 0.0) cat.id else null,
                                        destSubcategoryId = if (diff > 0.0) sub?.id else null,
                                        month = m,
                                        amount = if (diff > 0.0) diff else -diff,
                                        note = "Alocação recorrente",
                                        userId = userId,
                                        onComplete = {
                                            completedCount++
                                            if (completedCount == futureMonths.size) {
                                                targetAlocarAllocation = null
                                            }
                                        }
                                    )
                                } else {
                                    completedCount++
                                    if (completedCount == futureMonths.size) {
                                        targetAlocarAllocation = null
                                    }
                                }
                            }
                        }
                    },
                    enabled = isInputValid
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { targetAlocarAllocation = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TableRowLayout(
    indentation: Dp,
    nameContent: @Composable RowScope.() -> Unit,
    planejado: Double,
    alocado: Double,
    gasto: Double,
    disponivel: Double,
    onRowClick: () -> Unit,
    onRowLongClick: (() -> Unit)? = null,
    showProgress: Boolean,
    progressColor: Color,
    progress: Float,
    isMeta: Boolean = false,
    actionPanelContent: @Composable () -> Unit = {}
) {
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onRowClick,
                onLongClick = onRowLongClick
            )
    ) {
        // Line 1: Name content (full width of screen, indented)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = indentation, top = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            nameContent()
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Line 2: Values (4 Columns: Planejado, Alocado, Gasto, Disponível)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Plan column
            Text(
                text = currencyFormatter.format(planejado).replace("R$", "").trim(),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Aloc column
            Text(
                text = currencyFormatter.format(alocado).replace("R$", "").trim(),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Gasto column
            if (isMeta) {
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Text(
                    text = currencyFormatter.format(gasto).replace("R$", "").trim(),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Disp column
            Text(
                text = currencyFormatter.format(disponivel).replace("R$", "").trim(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (disponivel >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1.1f)
                    .padding(horizontal = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Line 3: Progress Bar & Percentage text
        if (showProgress) {
            val remainingPct = if (alocado > 0.0) {
                (((alocado - gasto) / alocado) * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indentation, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    text = "$remainingPct%",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = progressColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Thin divider between rows
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // Action panel if expanded
        actionPanelContent()
    }
}

fun getCategoryIcon(name: String): ImageVector {
    val lower = name.lowercase()
    return when {
        lower.contains("alimenta") || lower.contains("comida") || lower.contains("restaurante") -> Icons.Default.Restaurant
        lower.contains("almoço") || lower.contains("almoco") -> Icons.Default.RiceBowl
        lower.contains("janta") || lower.contains("refeição") -> Icons.Default.DinnerDining
        lower.contains("supermercado") || lower.contains("mercado") || lower.contains("feira") -> Icons.Default.ShoppingCart
        lower.contains("transporte") || lower.contains("carro") || lower.contains("uber") || lower.contains("combustivel") -> Icons.Default.DirectionsCar
        lower.contains("moradia") || lower.contains("casa") || lower.contains("aluguel") -> Icons.Default.Home
        lower.contains("saude") || lower.contains("saúde") || lower.contains("farmacia") || lower.contains("médico") -> Icons.Default.LocalHospital
        lower.contains("lazer") || lower.contains("viagem") || lower.contains("entretenimento") -> Icons.Default.SportsEsports
        lower.contains("educa") || lower.contains("curso") || lower.contains("escola") -> Icons.Default.School
        lower.contains("meta") -> Icons.Default.Flag
        else -> Icons.Default.Category
    }
}

private fun formatPlanningMoney(value: Double): String {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    return currencyFormatter.format(value)
}

private fun getPlanningAvailabilityInfo(alocado: Double, gasto: Double, isDark: Boolean = false): Pair<String, Color> {
    val disponivel = alocado - gasto
    val formatted = formatPlanningMoney(Math.abs(disponivel))
    val greenColor = if (isDark) Color(0xFF39D47A) else Color(0xFF22A45D)
    val redColor = if (isDark) Color(0xFFFF4D55) else Color(0xFFEF4444)
    return if (disponivel >= 0) {
        Pair("$formatted disponíveis", greenColor)
    } else {
        Pair("$formatted acima", redColor)
    }
}

private fun getPlanningProgressColor(planejado: Double, alocado: Double, gasto: Double, isDark: Boolean = false): Color {
    val greenColor = if (isDark) Color(0xFF39D47A) else Color(0xFF22A45D)
    val redColor = if (isDark) Color(0xFFFF4D55) else Color(0xFFEF4444)
    val orangeColor = if (isDark) Color(0xFFFF9F1C) else Color(0xFFF59E0B)
    val grayColor = Color(0xFF9FA9AB)

    return when {
        planejado == 0.0 && alocado == 0.0 && gasto == 0.0 -> grayColor
        gasto > alocado || (alocado == 0.0 && gasto > 0.0) -> redColor
        gasto > planejado && planejado > 0.0 -> orangeColor
        else -> greenColor
    }
}

@Composable
fun BudgetValuesGrid(
    planejado: Double,
    alocado: Double,
    gasto: Double,
    isMeta: Boolean = false,
    pctInt: Int? = null,
    progressColor: Color = Color.Unspecified
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color(0xFFF5F7F8) else Color(0xFF111827)
    val labelColor = if (isDark) Color(0xFF9FA9AB) else Color(0xFF6B7280)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Coluna 1: Planejado
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Planejado",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = labelColor
            )
            Text(
                text = formatPlanningMoney(planejado),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }

        // Coluna 2: Alocado
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Alocado",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = labelColor
            )
            Text(
                text = formatPlanningMoney(alocado),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }

        // Coluna 3: Gasto
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            if (!isMeta) {
                Text(
                    text = "Gasto",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = labelColor
                )
                Text(
                    text = formatPlanningMoney(gasto),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
            }
        }

        // Porcentagem alinhada à direita na mesma linha de cima
        if (pctInt != null) {
            Text(
                text = "$pctInt%",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = progressColor,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
fun BudgetProgressIndicator(
    progressFloat: Float,
    progressColor: Color
) {
    val isDark = isSystemInDarkTheme()
    val trackColor = if (isDark) Color(0xFF202B2E) else Color(0xFFEAEAEA)

    LinearProgressIndicator(
        progress = { progressFloat },
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = progressColor,
        trackColor = trackColor
    )
}

@Composable
fun TableHeader() {
    // Legacy fixed table header replaced by modern self-contained cards
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryRowItem(
    category: Category,
    planejado: Double,
    alocado: Double,
    gasto: Double,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    showToggle: Boolean = true,
    onRowClick: () -> Unit,
    onRowLongClick: (() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val primaryTextColor = if (isDark) Color(0xFFF5F7F8) else Color(0xFF111827)
    val secondaryTextColor = if (isDark) Color(0xFF9FA9AB) else Color(0xFF6B7280)

    val isMeta = category.id == -999
    val (statusText, statusColor) = getPlanningAvailabilityInfo(if (isMeta) alocado else alocado, if (isMeta) 0.0 else gasto, isDark = isDark)

    val progressFloat = if (alocado > 0) (gasto / alocado).toFloat().coerceIn(0f, 1f) else 0f
    val pctInt = if (alocado > 0) ((gasto / alocado) * 100).toInt() else 0
    val progressColor = getPlanningProgressColor(planejado, alocado, gasto, isDark = isDark)

    val alertRed = if (isDark) Color(0xFFFF4D55) else Color(0xFFEF4444)
    val alertGreen = if (isDark) Color(0xFF39D47A) else Color(0xFF22A45D)
    val iconBgColor = if (progressColor == alertRed) (if (isDark) Color(0xFF5C1D24) else Color(0xFFFEE2E2)) else (if (isDark) Color(0xFF1B3C2A) else Color(0xFFE6F4EA))
    val iconTint = if (progressColor == alertRed) alertRed else alertGreen

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 36.dp)
            .combinedClickable(
                onClick = onRowClick,
                onLongClick = onRowLongClick
            )
            .padding(vertical = 1.dp)
    ) {
        // Line 1: Icon + Name + Toggle Arrow + Warning + Status + Chevron
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCategoryIcon(category.name),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(13.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = category.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (showToggle) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onToggle() }
                            .padding(2.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                            contentDescription = "Expandir/Recolher",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (planejado != alocado || (gasto > 0.0 && alocado == 0.0)) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Aviso",
                        tint = if (isDark) Color(0xFFFF9F1C) else Color(0xFFF59E0B),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = if (isMeta) "${formatPlanningMoney(alocado)} acumulados" else statusText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isMeta) (if (isDark) Color(0xFF39D47A) else Color(0xFF22A45D)) else statusColor,
                textAlign = TextAlign.End
            )

            Spacer(modifier = Modifier.width(2.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = secondaryTextColor,
                modifier = Modifier.size(14.dp)
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Line 2: Values Summary + Percentage
        Box(modifier = Modifier.padding(start = 30.dp)) {
            BudgetValuesGrid(
                planejado = planejado,
                alocado = alocado,
                gasto = gasto,
                isMeta = isMeta,
                pctInt = if (!isMeta) pctInt else null,
                progressColor = progressColor
            )
        }

        // Line 3: Progress Bar
        if (!isMeta) {
            Spacer(modifier = Modifier.height(3.dp))
            Box(modifier = Modifier.padding(start = 30.dp)) {
                BudgetProgressIndicator(
                    progressFloat = progressFloat,
                    progressColor = progressColor
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubcategoryRowItem(
    category: Category,
    subcategory: Subcategory,
    planejado: Double,
    alocado: Double,
    gasto: Double,
    isActionPanelExpanded: Boolean,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onRowClick: () -> Unit,
    onRowLongClick: (() -> Unit)? = null,
    onEditPlanned: () -> Unit,
    onEditAllocated: () -> Unit,
    onMoveMoney: () -> Unit,
    onNewTransaction: () -> Unit,
    onQuickAlign: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val primaryTextColor = if (isDark) Color(0xFFF5F7F8) else Color(0xFF111827)
    val secondaryTextColor = if (isDark) Color(0xFF9FA9AB) else Color(0xFF6B7280)

    val isMeta = category.id == -999
    val (statusText, statusColor) = getPlanningAvailabilityInfo(if (isMeta) alocado else alocado, if (isMeta) 0.0 else gasto, isDark = isDark)

    val progressFloat = if (alocado > 0) (gasto / alocado).toFloat().coerceIn(0f, 1f) else 0f
    val pctInt = if (alocado > 0) ((gasto / alocado) * 100).toInt() else 0
    val progressColor = getPlanningProgressColor(planejado, alocado, gasto, isDark = isDark)

    val alertRed = if (isDark) Color(0xFFFF4D55) else Color(0xFFEF4444)
    val alertGreen = if (isDark) Color(0xFF39D47A) else Color(0xFF22A45D)
    val iconBgColor = if (progressColor == alertRed) (if (isDark) Color(0xFF5C1D24) else Color(0xFFFEE2E2)) else (if (isDark) Color(0xFF1B3C2A) else Color(0xFFE6F4EA))
    val iconTint = if (progressColor == alertRed) alertRed else alertGreen

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 32.dp)
            .combinedClickable(
                onClick = onRowClick,
                onLongClick = onRowLongClick
            )
            .padding(vertical = 1.dp)
            .drawBehind {
                val strokeWidth = 1.5.dp.toPx()
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                val connectorColor = if (isDark) Color(0xFF38474A) else Color(0xFFC0C7CE)

                val lineX = 12.dp.toPx()
                val subIconX = 30.dp.toPx()
                val iconCenterY = 10.dp.toPx()

                val lineTop = if (isFirst) -14.dp.toPx() else 0f
                val lineBottom = if (isLast) iconCenterY else size.height

                // Continuous vertical dashed line from Category icon center down to last subcategory icon
                drawLine(
                    color = connectorColor,
                    start = Offset(x = lineX, y = lineTop),
                    end = Offset(x = lineX, y = lineBottom),
                    strokeWidth = strokeWidth,
                    pathEffect = dashEffect
                )
                // Horizontal dashed branch connecting to subcategory icon at subIconX (30.dp)
                drawLine(
                    color = connectorColor,
                    start = Offset(x = lineX, y = iconCenterY),
                    end = Offset(x = subIconX, y = iconCenterY),
                    strokeWidth = strokeWidth,
                    pathEffect = dashEffect
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 30.dp)
        ) {
            // Line 1: Subcategory Icon + Name + Status + Chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(subcategory.name),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(11.dp)
                    )
                }

                Spacer(modifier = Modifier.width(5.dp))

                Text(
                    text = subcategory.name,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = if (isMeta) "${formatPlanningMoney(alocado)} acumulados" else statusText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isMeta) (if (isDark) Color(0xFF39D47A) else Color(0xFF22A45D)) else statusColor,
                    textAlign = TextAlign.End
                )

                Spacer(modifier = Modifier.width(2.dp))

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(13.dp)
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Line 2: Values in 3 clean columns + Percentage
            Box(modifier = Modifier.padding(start = 25.dp)) {
                BudgetValuesGrid(
                    planejado = planejado,
                    alocado = alocado,
                    gasto = gasto,
                    isMeta = isMeta,
                    pctInt = if (!isMeta) pctInt else null,
                    progressColor = progressColor
                )
            }

            // Line 3: Progress bar alone
            if (!isMeta) {
                Spacer(modifier = Modifier.height(3.dp))
                Box(modifier = Modifier.padding(start = 25.dp)) {
                    BudgetProgressIndicator(
                        progressFloat = progressFloat,
                        progressColor = progressColor
                    )
                }
            }

            // Action Panel ONLY for Subcategory if expanded
            AnimatedVisibility(
                visible = isActionPanelExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, start = 25.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 1.dp, horizontal = 1.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onEditPlanned,
                        modifier = Modifier.testTag("action_planejar_${subcategory.id}"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Planejar", fontSize = 10.sp)
                    }

                    TextButton(
                        onClick = onEditAllocated,
                        modifier = Modifier.testTag("action_alocar_${subcategory.id}"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Alocar", fontSize = 10.sp)
                    }

                    TextButton(
                        onClick = onMoveMoney,
                        modifier = Modifier.testTag("action_mover_${subcategory.id}"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Mover", fontSize = 10.sp)
                    }

                    if (!isMeta) {
                        TextButton(
                            onClick = onNewTransaction,
                            modifier = Modifier.testTag("action_nova_transacao_${subcategory.id}"),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Nova Transação", fontSize = 10.sp)
                        }
                    }

                    TextButton(
                        onClick = onQuickAlign,
                        modifier = Modifier.testTag("action_ajuste_${subcategory.id}"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Ajuste", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// --- COMPLEX DISTRIBUTE DIALOG ---
@Composable
fun DistributeDialog(
    viewModel: MainViewModel,
    prontoParaAtribuir: Double,
    sourceProntoParaAtribuir: Double = prontoParaAtribuir,
    preSelectedPair: Pair<Category, Subcategory?>?,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    allocationInfoMap: Map<Pair<Int, Int?>, Pair<Double, Double>>,
    destAllocationInfoMap: Map<Pair<Int, Int?>, Pair<Double, Double>> = allocationInfoMap,
    sourceMonth: String,
    destMonth: String = sourceMonth,
    sourceLeftovers: Map<Pair<Int, Int?>, Double> = emptyMap(),
    destLeftovers: Map<Pair<Int, Int?>, Double> = emptyMap(),
    goals: List<Goal> = emptyList(),
    preSelectedGoal: Goal? = null,
    preSelectedGoalMode: String? = null, // "APORTAR" or "RETIRAR"
    onDismiss: () -> Unit
) {
    val userId = viewModel.currentUserId
    val allocationMovements by viewModel.repository.getAllocationMovementsFlow(userId).collectAsStateWithLifecycle(emptyList())

    var valueInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }

    // Source selection: 0 for Pronto para Atribuir, 1 for Envelope, 2 for Meta
    var sourceMode by remember { mutableStateOf(0) }
    var sourceCategory by remember { mutableStateOf<Category?>(null) }
    var sourceSubcategory by remember { mutableStateOf<Subcategory?>(null) }
    var sourceGoal by remember { mutableStateOf<Goal?>(null) }
    var showSourceDropdown by remember { mutableStateOf(false) }
    var showSourceGoalDropdown by remember { mutableStateOf(false) }

    // Destination selection: 0 for Envelope, 1 for Pronto para Atribuir, 2 for Meta
    var destMode by remember { mutableStateOf(0) }
    var destCategory by remember { mutableStateOf<Category?>(null) }
    var destSubcategory by remember { mutableStateOf<Subcategory?>(null) }
    var destGoal by remember { mutableStateOf<Goal?>(null) }
    var showDestDropdown by remember { mutableStateOf(false) }
    var showDestGoalDropdown by remember { mutableStateOf(false) }

    // Apply pre-selection
    LaunchedEffect(preSelectedPair, preSelectedGoal, preSelectedGoalMode) {
        if (preSelectedGoal != null) {
            if (preSelectedGoalMode == "APORTAR") {
                destMode = 2
                destGoal = preSelectedGoal
                sourceMode = 0
            } else if (preSelectedGoalMode == "RETIRAR") {
                sourceMode = 2
                sourceGoal = preSelectedGoal
                destMode = 1
            }
        } else if (preSelectedPair != null) {
            sourceMode = 1
            sourceCategory = preSelectedPair.first
            sourceSubcategory = preSelectedPair.second
            destMode = 0
            destCategory = null
            destSubcategory = null
        } else {
            sourceMode = 0
            destMode = 0
        }
    }

    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Distribuir Dinheiro", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val sourceBalance = remember(sourceMode, sourceCategory, sourceSubcategory, sourceGoal, sourceProntoParaAtribuir, sourceLeftovers, allocationMovements) {
                    when (sourceMode) {
                        0 -> sourceProntoParaAtribuir
                        1 -> {
                            if (sourceCategory == null) 0.0
                            else {
                                val leftover = sourceLeftovers[Pair(sourceCategory!!.id, sourceSubcategory?.id)] ?: 0.0
                                leftover.coerceAtLeast(0.0)
                            }
                        }
                        2 -> {
                            if (sourceGoal == null) 0.0
                            else {
                                val destSum = allocationMovements.filter { it.dest_goal_id == sourceGoal!!.id }.sumOf { it.amount }
                                val sourceSum = allocationMovements.filter { it.source_goal_id == sourceGoal!!.id }.sumOf { it.amount }
                                destSum - sourceSum
                            }
                        }
                        else -> 0.0
                    }
                }
                val amountValue = valueInput.toDoubleOrNull() ?: 0.0
                val isInsufficient = amountValue > sourceBalance

                // VALUE INPUT
                OutlinedTextField(
                    value = valueInput,
                    onValueChange = { valueInput = it },
                    label = { Text("Valor a Transferir") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    prefix = { Text("R$ ") },
                    isError = isInsufficient,
                    supportingText = {
                        Column {
                            val sourceName = when (sourceMode) {
                                0 -> "Pronto para Atribuir"
                                1 -> {
                                    if (sourceCategory != null) {
                                        if (sourceSubcategory != null) sourceSubcategory!!.name else sourceCategory!!.name
                                    } else "origem selecionada"
                                }
                                2 -> {
                                    if (sourceGoal != null) sourceGoal!!.name else "meta de origem"
                                }
                                else -> "origem"
                            }
                            Text(
                                text = "Saldo disponível em $sourceName: ${currencyFormatter.format(sourceBalance)}",
                                color = if (isInsufficient) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            if (isInsufficient) {
                                Text(
                                    text = "Não é possível transferir pois não há saldo suficiente na origem.",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                )

                // ORIGEM SELECTION
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Origem", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (preSelectedPair == null && preSelectedGoal == null) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                RadioButton(selected = sourceMode == 0, onClick = { sourceMode = 0 })
                                Text("Pronto para Atribuir (${currencyFormatter.format(sourceProntoParaAtribuir)})", fontSize = 12.sp, modifier = Modifier.clickable { sourceMode = 0 })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                RadioButton(selected = sourceMode == 1, onClick = { sourceMode = 1 })
                                Text("Um Envelope", fontSize = 12.sp, modifier = Modifier.clickable { sourceMode = 1 })
                            }
                            if (goals.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    RadioButton(selected = sourceMode == 2, onClick = { sourceMode = 2 })
                                    Text("Uma Meta", fontSize = 12.sp, modifier = Modifier.clickable { sourceMode = 2 })
                                }
                            }
                        } else {
                            val selectionLabel = if (preSelectedGoal != null) {
                                "Meta: ${preSelectedGoal.name}"
                            } else {
                                "Envelope selecionado"
                            }
                            Text(selectionLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (sourceMode == 1) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box {
                                OutlinedButton(
                                    onClick = { showSourceDropdown = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    val sourceText = if (sourceCategory != null) {
                                        if (sourceSubcategory != null) "${sourceCategory!!.name} > ${sourceSubcategory!!.name}" else sourceCategory!!.name
                                    } else "Selecione a Categoria/Subcategoria"
                                    Text(sourceText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                DropdownMenu(
                                    expanded = showSourceDropdown,
                                    onDismissRequest = { showSourceDropdown = false },
                                    modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 250.dp)
                                ) {
                                    categories.forEach { cat ->
                                        val catSobra = sourceLeftovers[Pair(cat.id, null)] ?: 0.0
                                        val statusText = if (catSobra >= 0) "Sobra: ${currencyFormatter.format(catSobra)}" else "Falta: ${currencyFormatter.format(catSobra)}"
                                        DropdownMenuItem(
                                            text = { Text("${cat.name} ($statusText)", fontWeight = FontWeight.Bold) },
                                            onClick = {
                                                sourceCategory = cat
                                                sourceSubcategory = null
                                                showSourceDropdown = false
                                            }
                                        )

                                        val subs = subcategories.filter { it.category_id == cat.id }
                                        subs.forEach { sub ->
                                            val subSobra = sourceLeftovers[Pair(cat.id, sub.id)] ?: 0.0
                                            val subStatusText = if (subSobra >= 0) "Sobra: ${currencyFormatter.format(subSobra)}" else "Falta: ${currencyFormatter.format(subSobra)}"
                                            DropdownMenuItem(
                                                text = { Text("  ↳ ${sub.name} ($subStatusText)") },
                                                onClick = {
                                                    sourceCategory = cat
                                                    sourceSubcategory = sub
                                                    showSourceDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (sourceMode == 2) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box {
                                OutlinedButton(
                                    onClick = { showSourceGoalDropdown = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    val sourceText = if (sourceGoal != null) sourceGoal!!.name else "Selecione a Meta"
                                    Text(sourceText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                DropdownMenu(
                                    expanded = showSourceGoalDropdown,
                                    onDismissRequest = { showSourceGoalDropdown = false },
                                    modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 250.dp)
                                ) {
                                    goals.forEach { g ->
                                        val gDest = allocationMovements.filter { it.dest_goal_id == g.id }.sumOf { it.amount }
                                        val gSrc = allocationMovements.filter { it.source_goal_id == g.id }.sumOf { it.amount }
                                        val gBal = gDest - gSrc
                                        DropdownMenuItem(
                                            text = { Text("${g.name} (${currencyFormatter.format(gBal)})") },
                                            onClick = {
                                                sourceGoal = g
                                                showSourceGoalDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // DESTINO SELECTION
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Destino", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (preSelectedGoal == null) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                RadioButton(selected = destMode == 0, onClick = { destMode = 0 })
                                Text("Um Envelope", fontSize = 12.sp, modifier = Modifier.clickable { destMode = 0 })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                RadioButton(selected = destMode == 1, onClick = { destMode = 1 })
                                Text("Pronto para Atribuir", fontSize = 12.sp, modifier = Modifier.clickable { destMode = 1 })
                            }
                            if (goals.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    RadioButton(selected = destMode == 2, onClick = { destMode = 2 })
                                    Text("Uma Meta", fontSize = 12.sp, modifier = Modifier.clickable { destMode = 2 })
                                }
                            }
                        } else {
                            val selectionLabel = if (preSelectedGoal != null && preSelectedGoalMode == "APORTAR") {
                                "Meta: ${preSelectedGoal.name}"
                            } else {
                                "Pronto para Atribuir"
                            }
                            Text(selectionLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (destMode == 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box {
                                OutlinedButton(
                                    onClick = { showDestDropdown = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    val destText = if (destCategory != null) {
                                        if (destSubcategory != null) "${destCategory!!.name} > ${destSubcategory!!.name}" else destCategory!!.name
                                    } else "Selecione a Categoria/Subcategoria"
                                    Text(destText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                DropdownMenu(
                                    expanded = showDestDropdown,
                                    onDismissRequest = { showDestDropdown = false },
                                    modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 250.dp)
                                ) {
                                    categories.forEach { cat ->
                                        val catSobra = destLeftovers[Pair(cat.id, null)] ?: 0.0
                                        val statusText = if (catSobra >= 0) "Sobra: ${currencyFormatter.format(catSobra)}" else "Falta: ${currencyFormatter.format(catSobra)}"
                                        DropdownMenuItem(
                                            text = { Text("${cat.name} ($statusText)", fontWeight = FontWeight.Bold) },
                                            onClick = {
                                                destCategory = cat
                                                destSubcategory = null
                                                showDestDropdown = false
                                            }
                                        )

                                        val subs = subcategories.filter { it.category_id == cat.id }
                                        subs.forEach { sub ->
                                            val subSobra = destLeftovers[Pair(cat.id, sub.id)] ?: 0.0
                                            val subStatusText = if (subSobra >= 0) "Sobra: ${currencyFormatter.format(subSobra)}" else "Falta: ${currencyFormatter.format(subSobra)}"
                                            DropdownMenuItem(
                                                text = { Text("  ↳ ${sub.name} ($subStatusText)") },
                                                onClick = {
                                                    destCategory = cat
                                                    destSubcategory = sub
                                                    showDestDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (destMode == 2) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box {
                                OutlinedButton(
                                    onClick = { showDestGoalDropdown = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    val destText = if (destGoal != null) destGoal!!.name else "Selecione a Meta"
                                    Text(destText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                DropdownMenu(
                                    expanded = showDestGoalDropdown,
                                    onDismissRequest = { showDestGoalDropdown = false },
                                    modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 250.dp)
                                ) {
                                    goals.forEach { g ->
                                        DropdownMenuItem(
                                            text = { Text(g.name) },
                                            onClick = {
                                                destGoal = g
                                                showDestGoalDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Opcional Note
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Nota (Opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            val amount = valueInput.toDoubleOrNull() ?: 0.0
            val isValid = remember(amount, sourceMode, sourceCategory, sourceSubcategory, sourceGoal, destMode, destCategory, destSubcategory, destGoal, sourceProntoParaAtribuir, allocationInfoMap, sourceMonth, destMonth) {
                if (amount <= 0.0) return@remember false
                
                // Validate source balance
                val sourceBalance = when (sourceMode) {
                    0 -> sourceProntoParaAtribuir
                    1 -> {
                        if (sourceCategory == null) return@remember false
                        val info = allocationInfoMap[Pair(sourceCategory!!.id, sourceSubcategory?.id)]
                        info?.second ?: 0.0
                    }
                    2 -> {
                        if (sourceGoal == null) return@remember false
                        val destSum = allocationMovements.filter { it.dest_goal_id == sourceGoal!!.id }.sumOf { it.amount }
                        val sourceSum = allocationMovements.filter { it.source_goal_id == sourceGoal!!.id }.sumOf { it.amount }
                        destSum - sourceSum
                    }
                    else -> 0.0
                }
                
                if (sourceBalance < amount) return@remember false

                // Validate destination is selected
                if (destMode == 0 && destCategory == null) return@remember false
                if (destMode == 2 && destGoal == null) return@remember false

                // Validate they are not the exact same
                if (sourceMonth == destMonth) {
                    if (sourceMode == 0 && destMode == 1) return@remember false
                    if (sourceMode == 1 && destMode == 0 && sourceCategory?.id == destCategory?.id && sourceSubcategory?.id == destSubcategory?.id) return@remember false
                    if (sourceMode == 2 && destMode == 2 && sourceGoal?.id == destGoal?.id) return@remember false
                }

                true
            }

            Button(
                onClick = {
                    val finalSourceCat = if (sourceMode == 1) sourceCategory?.id else null
                    val finalSourceSub = if (sourceMode == 1) sourceSubcategory?.id else null
                    val finalSourceGoal = if (sourceMode == 2) sourceGoal?.id else null

                    val finalDestCat = if (destMode == 0) destCategory?.id else null
                    val finalDestSub = if (destMode == 0) destSubcategory?.id else null
                    val finalDestGoal = if (destMode == 2) destGoal?.id else null

                    viewModel.moveMoney(
                        sourceCategoryId = finalSourceCat,
                        sourceSubcategoryId = finalSourceSub,
                        destCategoryId = finalDestCat,
                        destSubcategoryId = finalDestSub,
                        sourceGoalId = finalSourceGoal,
                        destGoalId = finalDestGoal,
                        month = sourceMonth,
                        destMonth = destMonth,
                        amount = amount,
                        note = noteInput.takeIf { it.isNotBlank() },
                        userId = userId,
                        onComplete = onDismiss
                    )
                },
                enabled = isValid
            ) {
                Text("Transferir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

fun getFutureMonths(startMonthStr: String, count: Int = 12): List<String> {
    val list = mutableListOf<String>()
    try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
        val date = sdf.parse(startMonthStr) ?: java.util.Date()
        val cal = java.util.Calendar.getInstance()
        cal.time = date
        for (i in 0 until count) {
            list.add(sdf.format(cal.time))
            cal.add(java.util.Calendar.MONTH, 1)
        }
    } catch (e: Exception) {
        list.add(startMonthStr)
    }
    return list
}

fun getCumulativeLeftoversMap(
    budgetAllocations: List<BudgetAllocation>,
    allocationMovements: List<AllocationMovement>,
    transactions: List<Transaction>,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    upToMonthStr: String
): Map<Pair<Int, Int?>, Double> {
    val sobraMap = mutableMapOf<Pair<Int, Int?>, Double>()
    try {
        val monthsSet = mutableSetOf<String>()
        budgetAllocations.forEach { monthsSet.add(it.month) }
        transactions.forEach {
            if (it.date.length >= 7) {
                monthsSet.add(it.date.substring(0, 7))
            }
        }
        monthsSet.add(upToMonthStr)
        
        val sortedMonths = monthsSet.filter { it <= upToMonthStr }.sorted()
        
        categories.forEach { cat ->
            sobraMap[Pair(cat.id, null as Int?)] = 0.0
        }
        subcategories.forEach { sub ->
            sobraMap[Pair(sub.category_id, sub.id)] = 0.0
        }
        
        sortedMonths.forEach { month ->
            val allocsInMonth = budgetAllocations.filter { it.month == month }
            val monthExpenses = transactions.filter { it.type == "DESPESA" && it.date.startsWith(month) }
            
            val subSpent = monthExpenses.filter { it.subcategory_id != null }
                .groupBy { Pair(it.category_id, it.subcategory_id) }
                .mapValues { entry -> entry.value.sumOf { it.value } }
                
            val catSpent = monthExpenses.groupBy { Pair(it.category_id, null as Int?) }
                .mapValues { entry -> entry.value.sumOf { it.value } }
                
            subcategories.forEach { sub ->
                val key = Pair(sub.category_id, sub.id)
                val alloc = allocsInMonth.find { it.category_id == sub.category_id && it.subcategory_id == sub.id }
                val allocatedVal = if (alloc != null) {
                    allocationMovements.filter { it.dest_budget_allocation_id == alloc.id }.sumOf { it.amount } -
                    allocationMovements.filter { it.source_budget_allocation_id == alloc.id }.sumOf { it.amount }
                } else 0.0
                
                val prevSobra = sobraMap[key] ?: 0.0
                val spentVal = subSpent[key] ?: 0.0
                
                sobraMap[key] = prevSobra + allocatedVal - spentVal
            }
            
            categories.forEach { cat ->
                val hasSubs = subcategories.any { it.category_id == cat.id }
                if (!hasSubs) {
                    val key = Pair(cat.id, null as Int?)
                    val alloc = allocsInMonth.find { it.category_id == cat.id && it.subcategory_id == null }
                    val allocatedVal = if (alloc != null) {
                        allocationMovements.filter { it.dest_budget_allocation_id == alloc.id }.sumOf { it.amount } -
                        allocationMovements.filter { it.source_budget_allocation_id == alloc.id }.sumOf { it.amount }
                    } else 0.0
                    
                    val prevSobra = sobraMap[key] ?: 0.0
                    val spentVal = catSpent[key] ?: 0.0
                    
                    sobraMap[key] = prevSobra + allocatedVal - spentVal
                }
            }
        }
    } catch (e: Exception) {
        // Fallback
    }
    return sobraMap
}

fun getCustomRecurrenceMonths(
    startMonthStr: String,
    repeatInterval: Int,
    isUnitYears: Boolean,
    endOption: Int,
    endCalendar: java.util.Calendar?
): List<String> {
    val list = mutableListOf<String>()
    try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
        val startDate = sdf.parse(startMonthStr) ?: java.util.Date()
        val cal = java.util.Calendar.getInstance()
        cal.time = startDate

        val limitCal = java.util.Calendar.getInstance()
        if (endOption == 1 && endCalendar != null) {
            limitCal.time = endCalendar.time
            limitCal.set(java.util.Calendar.DAY_OF_MONTH, limitCal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
        } else {
            // Default "Nunca" to 24 months (2 years) limit
            limitCal.time = startDate
            limitCal.add(java.util.Calendar.YEAR, 2)
        }

        val interval = maxOf(1, repeatInterval)
        var safetyCount = 0
        while (!cal.after(limitCal) && safetyCount < 100) {
            list.add(sdf.format(cal.time))
            if (isUnitYears) {
                cal.add(java.util.Calendar.YEAR, interval)
            } else {
                cal.add(java.util.Calendar.MONTH, interval)
            }
            safetyCount++
        }
    } catch (e: Exception) {
        list.add(startMonthStr)
    }
    return list
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTransactionsHistoryDialog(
    category: Category,
    subcategory: Subcategory?,
    monthStr: String,
    transactions: List<Transaction>,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onTransactionClick: (Transaction) -> Unit
) {
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val displayMonth = remember(monthStr) {
        try {
            val sdfInput = java.text.SimpleDateFormat("yyyy-MM", Locale.US)
            val date = sdfInput.parse(monthStr)
            val sdfOutput = java.text.SimpleDateFormat("MMMM yyyy", Locale("pt", "BR"))
            if (date != null) sdfOutput.format(date).replaceFirstChar { it.uppercase() } else monthStr
        } catch (e: Exception) {
            monthStr
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Histórico de Transações",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (subcategory != null) "${category.name} > ${subcategory.name}" else category.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = displayMonth,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                if (transactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhuma transação registrada neste mês para este envelope.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(transactions, key = { it.id }) { tx ->
                            val accountName = accounts.find { it.id == tx.account_id }?.name ?: "Conta Desconhecida"
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTransactionClick(tx) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val icon = when (tx.type) {
                                            "RECEITA" -> Icons.Default.TrendingUp
                                            "DESPESA" -> Icons.Default.TrendingDown
                                            else -> Icons.Default.SwapHoriz
                                        }
                                        val iconTint = when (tx.type) {
                                            "RECEITA" -> MaterialTheme.colorScheme.primary
                                            "DESPESA" -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.secondary
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(iconTint.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tx.description,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Text(
                                                text = accountName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column(horizontalAlignment = Alignment.End) {
                                        val sign = when (tx.type) {
                                            "RECEITA" -> "+"
                                            "DESPESA" -> "-"
                                            else -> ""
                                        }
                                        val textColor = when (tx.type) {
                                            "RECEITA" -> MaterialTheme.colorScheme.primary
                                            "DESPESA" -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                        Text(
                                            text = "%s R$ %.2f".format(sign, tx.value),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = textColor
                                        )
                                        Spacer(modifier = Modifier.height(1.dp))
                                        val displayDate = try {
                                            val parts = tx.date.split("-")
                                            if (parts.size == 3) "${parts[2]}/${parts[1]}" else tx.date
                                        } catch (e: Exception) {
                                            tx.date
                                        }
                                        Text(
                                            text = displayDate,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

fun getPreviousMonthStr(monthStr: String): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
    return try {
        val date = sdf.parse(monthStr) ?: return ""
        val cal = java.util.Calendar.getInstance()
        cal.time = date
        cal.add(java.util.Calendar.MONTH, -1)
        sdf.format(cal.time)
    } catch (e: Exception) {
        ""
    }
}

fun formatMonthPortuguese(monthStr: String): String {
    val sdfInput = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
    val sdfOutput = java.text.SimpleDateFormat("MMMM/yyyy", java.util.Locale("pt", "BR"))
    return try {
        val date = sdfInput.parse(monthStr) ?: return monthStr
        sdfOutput.format(date).replaceFirstChar { it.uppercase() }
    } catch (e: Exception) {
        monthStr
    }
}

@Composable
fun MonthClosureContent(
    viewModel: MainViewModel,
    prevMonthStr: String,
    currentMonthStr: String,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    prevAllocationInfoMap: Map<Pair<Int, Int?>, Pair<Double, Double>>,
    currentAllocationInfoMap: Map<Pair<Int, Int?>, Pair<Double, Double>>,
    prevSpentInfoMap: Map<Pair<Int?, Int?>, Double>,
    prevSobraMap: Map<Pair<Int, Int?>, Double>,
    prontoParaAtribuir: Double,
    prevProntoParaAtribuir: Double,
    isPrevMonthReviewed: Boolean,
    onBack: () -> Unit,
    onRedistribute: (Category, Subcategory?) -> Unit
) {
    val userId = viewModel.currentUserId
    val budgetAllocations by viewModel.repository.getBudgetAllocationsFlow(userId).collectAsStateWithLifecycle(emptyList())
    val allocationMovements by viewModel.repository.getAllocationMovementsFlow(userId).collectAsStateWithLifecycle(emptyList())

    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Calculate savings and overspending totals
    val totalEconomizado = remember(prevSobraMap, categories, subcategories) {
        var sum = 0.0
        categories.forEach { cat ->
            val subs = subcategories.filter { it.category_id == cat.id }
            if (subs.isEmpty()) {
                val sobra = prevSobraMap[Pair(cat.id, null)] ?: 0.0
                if (sobra > 0.0) sum += sobra
            } else {
                subs.forEach { sub ->
                    val sobra = prevSobraMap[Pair(cat.id, sub.id)] ?: 0.0
                    if (sobra > 0.0) sum += sobra
                }
            }
        }
        sum
    }

    val totalGastoAMais = remember(prevSobraMap, categories, subcategories) {
        var sum = 0.0
        categories.forEach { cat ->
            val subs = subcategories.filter { it.category_id == cat.id }
            if (subs.isEmpty()) {
                val sobra = prevSobraMap[Pair(cat.id, null)] ?: 0.0
                if (sobra < 0.0) sum += kotlin.math.abs(sobra)
            } else {
                subs.forEach { sub ->
                    val sobra = prevSobraMap[Pair(cat.id, sub.id)] ?: 0.0
                    if (sobra < 0.0) sum += kotlin.math.abs(sobra)
                }
            }
        }
        sum
    }

    val realEconomizado = totalEconomizado - totalGastoAMais

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Row: Back button and Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Voltar"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Fechamento de ${formatMonthPortuguese(prevMonthStr)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Informative Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Ajuste os saldos parados do mês anterior redistribuindo-os para as categorias do mês atual (${formatMonthPortuguese(currentMonthStr)}) ou de volta para o Pronto para Atribuir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Summary Dashboard Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Resumo do Mês Anterior",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Economizado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = currencyFormatter.format(totalEconomizado),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gasto a mais",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = currencyFormatter.format(totalGastoAMais),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Column(modifier = Modifier.weight(1.1f)) {
                        Text(
                            text = "Real Economizado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = currencyFormatter.format(realEconomizado),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (realEconomizado >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // List of categories and subcategories
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            categories.forEach { cat ->
                val subs = subcategories.filter { it.category_id == cat.id }

                item(key = "hdr_${cat.id}") {
                    Text(
                        text = cat.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }

                if (subs.isEmpty()) {
                    val prevInfo = prevAllocationInfoMap[Pair(cat.id, null)]
                    val planejado = prevInfo?.first ?: 0.0
                    val gasto = prevSpentInfoMap[Pair(cat.id, null)] ?: 0.0
                    val sobra = prevSobraMap[Pair(cat.id, null)] ?: 0.0
                    val alocado = sobra + gasto

                    item(key = "cat_item_${cat.id}") {
                        ClosureItemRow(
                            name = "Geral",
                            planejado = planejado,
                            alocado = alocado,
                            gasto = gasto,
                            currencyFormatter = currencyFormatter,
                            isReviewed = isPrevMonthReviewed,
                            onRedistribute = { onRedistribute(cat, null) }
                        )
                    }
                } else {
                    items(subs, key = { "sub_item_${it.id}" }) { sub ->
                        val prevInfo = prevAllocationInfoMap[Pair(cat.id, sub.id)]
                        val planejado = prevInfo?.first ?: 0.0
                        val gasto = prevSpentInfoMap[Pair(cat.id, sub.id)] ?: 0.0
                        val sobra = prevSobraMap[Pair(cat.id, sub.id)] ?: 0.0
                        val alocado = sobra + gasto

                        ClosureItemRow(
                            name = sub.name,
                            planejado = planejado,
                            alocado = alocado,
                            gasto = gasto,
                            currencyFormatter = currencyFormatter,
                            isReviewed = isPrevMonthReviewed,
                            onRedistribute = { onRedistribute(cat, sub) }
                        )
                    }
                }
            }

            val closureMovements = allocationMovements.filter { mov ->
                val sourceAlloc = budgetAllocations.find { it.id == mov.source_budget_allocation_id }
                val destAlloc = budgetAllocations.find { it.id == mov.dest_budget_allocation_id }
                
                (sourceAlloc != null && sourceAlloc.month == prevMonthStr) ||
                (destAlloc != null && destAlloc.month == prevMonthStr)
            }.sortedByDescending { it.moved_at }

            if (closureMovements.isNotEmpty()) {
                item(key = "hdr_closure_redistributions") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Redistribuições deste Fechamento",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                items(closureMovements, key = { "closure_mov_${it.id}" }) { mov ->
                    RedistributionItemRow(
                        movement = mov,
                        budgetAllocations = budgetAllocations,
                        categories = categories,
                        subcategories = subcategories,
                        currencyFormatter = currencyFormatter
                    )
                }
            }
        }

        // Concluir Fechamento / Reabrir Fechamento buttons
        if (isPrevMonthReviewed) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.setMonthReviewed(viewModel.currentUserId, prevMonthStr, false)
                        android.widget.Toast.makeText(context, "Fechamento reaberto com sucesso!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reabrir Fechamento", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Concluir", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Button(
                onClick = {
                    viewModel.setMonthReviewed(viewModel.currentUserId, prevMonthStr, true)
                    android.widget.Toast.makeText(context, "Fechamento concluído com sucesso!", android.widget.Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("concluir_fechamento_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Concluir Fechamento", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ClosureItemRow(
    name: String,
    planejado: Double,
    alocado: Double,
    gasto: Double,
    currencyFormatter: NumberFormat,
    isReviewed: Boolean = false,
    onRedistribute: () -> Unit
) {
    val sobra = alocado - gasto
    val desvio = planejado - alocado

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row with Name and Desvio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "Desvio: ${currencyFormatter.format(desvio)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Columns row (Planejado, Alocado, Gasto)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Planejado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(currencyFormatter.format(planejado), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Alocado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(currencyFormatter.format(alocado), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Gasto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(currencyFormatter.format(gasto), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Footer row with Sobra and Redistribute button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Sobra para redistribuir",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currencyFormatter.format(sobra),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (sobra >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = onRedistribute,
                    enabled = !isReviewed,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("redistribute_button_${name.lowercase().replace(" ", "_")}")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallSplit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Redistribuir", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun MonthClosureBanner(
    monthName: String,
    isReviewed: Boolean,
    saldoPrevMonthText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("month_closure_banner"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isReviewed) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            }
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.tertiary.copy(alpha = if (isReviewed) 0.2f else 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isReviewed) Icons.Default.CheckCircle else Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isReviewed) "Fechamento de $monthName concluído" else "Fechamento de $monthName disponível",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Saldo do mês anterior: $saldoPrevMonthText. Clique para editar ou revisar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Editar fechamento",
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun RedistributionReportDialog(
    viewModel: MainViewModel,
    currentMonthStr: String,
    prevMonthStr: String,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    budgetAllocations: List<BudgetAllocation>,
    allocationMovements: List<AllocationMovement>,
    onDismiss: () -> Unit
) {
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    var filterMode by remember { mutableStateOf(0) } // 0: Mês Atual, 1: Mês Anterior, 2: Tudo

    val filteredMovements = remember(allocationMovements, budgetAllocations, filterMode, currentMonthStr, prevMonthStr) {
        val list = when (filterMode) {
            0 -> {
                allocationMovements.filter { mov ->
                    val sourceAlloc = budgetAllocations.find { it.id == mov.source_budget_allocation_id }
                    val destAlloc = budgetAllocations.find { it.id == mov.dest_budget_allocation_id }
                    (sourceAlloc != null && sourceAlloc.month == currentMonthStr) ||
                    (destAlloc != null && destAlloc.month == currentMonthStr)
                }
            }
            1 -> {
                allocationMovements.filter { mov ->
                    val sourceAlloc = budgetAllocations.find { it.id == mov.source_budget_allocation_id }
                    val destAlloc = budgetAllocations.find { it.id == mov.dest_budget_allocation_id }
                    (sourceAlloc != null && sourceAlloc.month == prevMonthStr) ||
                    (destAlloc != null && destAlloc.month == prevMonthStr)
                }
            }
            else -> allocationMovements
        }
        list.sortedByDescending { it.moved_at }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Relatório de Redistribuições",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Filter Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val monthsShort = mapOf(
                        0 to "Este Mês",
                        1 to "Mês Passado",
                        2 to "Tudo"
                    )
                    monthsShort.forEach { (index, label) ->
                        val isSelected = filterMode == index
                        Button(
                            onClick = { filterMode = index },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = if (isSelected) {
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (filteredMovements.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Nenhuma movimentação neste período.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(filteredMovements, key = { "report_mov_${it.id}" }) { mov ->
                            RedistributionItemRow(
                                movement = mov,
                                budgetAllocations = budgetAllocations,
                                categories = categories,
                                subcategories = subcategories,
                                currencyFormatter = currencyFormatter
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

@Composable
fun RedistributionItemRow(
    movement: AllocationMovement,
    budgetAllocations: List<BudgetAllocation>,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    currencyFormatter: NumberFormat
) {
    val sourceName = remember(movement.source_budget_allocation_id, budgetAllocations, categories, subcategories) {
        resolveAllocationName(movement.source_budget_allocation_id, budgetAllocations, categories, subcategories)
    }
    val destName = remember(movement.dest_budget_allocation_id, budgetAllocations, categories, subcategories) {
        resolveAllocationName(movement.dest_budget_allocation_id, budgetAllocations, categories, subcategories)
    }
    val formattedDate = remember(movement.moved_at) {
        formatMovementDate(movement.moved_at)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                
                Text(
                    text = currencyFormatter.format(movement.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingFlat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "De: $sourceName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF2E7D32), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Para: $destName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            if (!movement.note.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Nota: ${movement.note}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}

fun resolveAllocationName(
    allocationId: Int?,
    budgetAllocations: List<BudgetAllocation>,
    categories: List<Category>,
    subcategories: List<Subcategory>
): String {
    if (allocationId == null) return "Pronto para Atribuir"
    val alloc = budgetAllocations.find { it.id == allocationId } ?: return "Envelope desconhecido"
    val cat = categories.find { it.id == alloc.category_id }
    val sub = subcategories.find { it.id == alloc.subcategory_id }
    
    val catName = cat?.name ?: "Categoria ${alloc.category_id}"
    val monthName = formatMonthPortuguese(alloc.month)
    
    return if (sub != null) {
        "$catName > ${sub.name} ($monthName)"
    } else {
        "$catName ($monthName)"
    }
}

fun formatMovementDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("pt", "BR"))
    return sdf.format(java.util.Date(timestamp))
}
