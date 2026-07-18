package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.viewmodel.MainViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val userId = viewModel.currentUserId
    val context = LocalContext.current
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    // Collect data streams from repository
    val transactions by viewModel.repository.getTransactionsFlow(userId).collectAsStateWithLifecycle(emptyList())
    val categories by viewModel.repository.getCategoriesFlow(userId).collectAsStateWithLifecycle(emptyList())
    val subcategories by viewModel.repository.getSubcategoriesFlow(userId).collectAsStateWithLifecycle(emptyList())
    val goals by viewModel.repository.getGoalsFlow(userId).collectAsStateWithLifecycle(emptyList())
    val allocationMovements by viewModel.repository.getAllocationMovementsFlow(userId).collectAsStateWithLifecycle(emptyList())
    val budgetAllocations by viewModel.repository.getBudgetAllocationsFlow(userId).collectAsStateWithLifecycle(emptyList())
    val accounts by viewModel.repository.getAccountsFlow(userId).collectAsStateWithLifecycle(emptyList())
    val recurrenceRules by viewModel.repository.getRecurrenceRulesFlow(userId).collectAsStateWithLifecycle(emptyList())
    val installmentPlans by viewModel.repository.getInstallmentPlansFlow(userId).collectAsStateWithLifecycle(emptyList())

    val selectedMonthCalendar by viewModel.selectedMonthCalendar.collectAsStateWithLifecycle()

    // State for period selector: "MÊS", "TRIMESTRE", "ANO", "CUSTOMIZADO"
    var selectedPeriodType by remember { mutableStateOf("MÊS") }
    
    // Calendar month states
    val currentCal = Calendar.getInstance()
    val sdfMonth = SimpleDateFormat("yyyy-MM", Locale.US)
    val displaySdf = SimpleDateFormat("MMMM yyyy", Locale("pt", "BR"))
    
    var customStartCal by remember { 
        mutableStateOf(Calendar.getInstance().apply { add(Calendar.MONTH, -3) }) 
    }
    var customEndCal by remember { 
        mutableStateOf(Calendar.getInstance()) 
    }
    
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // Calculate active date range (startMonthStr and endMonthStr)
    val (startMonthStr, endMonthStr) = remember(selectedPeriodType, customStartCal, customEndCal, selectedMonthCalendar) {
        val anchor = selectedMonthCalendar.clone() as Calendar
        when (selectedPeriodType) {
            "MÊS" -> {
                val m = sdfMonth.format(anchor.time)
                Pair(m, m)
            }
            "TRIMESTRE" -> {
                val end = sdfMonth.format(anchor.time)
                val startCal = anchor.clone() as Calendar
                startCal.add(Calendar.MONTH, -2)
                val start = sdfMonth.format(startCal.time)
                Pair(start, end)
            }
            "ANO" -> {
                val end = sdfMonth.format(anchor.time)
                val startCal = anchor.clone() as Calendar
                startCal.add(Calendar.MONTH, -11)
                val start = sdfMonth.format(startCal.time)
                Pair(start, end)
            }
            else -> {
                val start = sdfMonth.format(customStartCal.time)
                val end = sdfMonth.format(customEndCal.time)
                if (start <= end) Pair(start, end) else Pair(end, start)
            }
        }
    }

    // Helper to check if a date string YYYY-MM-DD falls in range
    fun isDateInRange(dateStr: String): Boolean {
        if (dateStr.length < 7) return false
        val itemMonth = dateStr.substring(0, 7)
        return itemMonth >= startMonthStr && itemMonth <= endMonthStr
    }

    // Filtered data sets
    val rangeTransactions = remember(transactions, startMonthStr, endMonthStr) {
        transactions.filter { isDateInRange(it.date) }
    }

    // Category Filter state for detailed analysis
    var filteredCategoryId by remember { mutableStateOf<Int?>(null) }
    val filteredCategoryName = remember(filteredCategoryId, categories) {
        categories.find { it.id == filteredCategoryId }?.name ?: "Todas"
    }

    // --- KPIs calculation ---
    val totalRevenue = remember(rangeTransactions) {
        rangeTransactions.filter { it.type == "RECEITA" }.sumOf { it.value }
    }
    val totalExpense = remember(rangeTransactions) {
        rangeTransactions.filter { it.type == "DESPESA" }.sumOf { it.value }
    }
    val netSavings = remember(totalRevenue, totalExpense) {
        totalRevenue - totalExpense
    }

    // Previous Period KPIs for Comparison
    val prevPeriodKPIs = remember(selectedPeriodType, transactions, startMonthStr, endMonthStr, selectedMonthCalendar) {
        val today = selectedMonthCalendar.clone() as Calendar
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        
        val (pStart, pEnd) = when (selectedPeriodType) {
            "MÊS" -> {
                val cal = today.clone() as Calendar
                cal.add(Calendar.MONTH, -1)
                val m = sdf.format(cal.time)
                Pair(m, m)
            }
            "TRIMESTRE" -> {
                val calEnd = today.clone() as Calendar
                calEnd.add(Calendar.MONTH, -3)
                val end = sdf.format(calEnd.time)
                val calStart = calEnd.clone() as Calendar
                calStart.add(Calendar.MONTH, -2)
                val start = sdf.format(calStart.time)
                Pair(start, end)
            }
            "ANO" -> {
                val calEnd = today.clone() as Calendar
                calEnd.add(Calendar.YEAR, -1)
                val end = sdf.format(calEnd.time)
                val calStart = calEnd.clone() as Calendar
                calStart.add(Calendar.MONTH, -11)
                val start = sdf.format(calStart.time)
                Pair(start, end)
            }
            else -> { // Custom: subtract the same number of months
                val startMonthCal = Calendar.getInstance().apply {
                    val parts = startMonthStr.split("-")
                    set(Calendar.YEAR, parts[0].toInt())
                    set(Calendar.MONTH, parts[1].toInt() - 1)
                }
                val endMonthCal = Calendar.getInstance().apply {
                    val parts = endMonthStr.split("-")
                    set(Calendar.YEAR, parts[0].toInt())
                    set(Calendar.MONTH, parts[1].toInt() - 1)
                }
                val diffMonths = ((endMonthCal.get(Calendar.YEAR) - startMonthCal.get(Calendar.YEAR)) * 12) +
                        (endMonthCal.get(Calendar.MONTH) - startMonthCal.get(Calendar.MONTH)) + 1
                
                val prevEndCal = startMonthCal.clone() as Calendar
                prevEndCal.add(Calendar.MONTH, -1)
                val prevStartCal = prevEndCal.clone() as Calendar
                prevStartCal.add(Calendar.MONTH, -(diffMonths - 1))
                
                Pair(sdf.format(prevStartCal.time), sdf.format(prevEndCal.time))
            }
        }

        val prevTxs = transactions.filter {
            if (it.date.length < 7) false
            else {
                val m = it.date.substring(0, 7)
                m >= pStart && m <= pEnd
            }
        }
        val rev = prevTxs.filter { it.type == "RECEITA" }.sumOf { it.value }
        val exp = prevTxs.filter { it.type == "DESPESA" }.sumOf { it.value }
        Pair(rev, exp)
    }

    val prevRevenue = prevPeriodKPIs.first
    val prevExpense = prevPeriodKPIs.second

    val revenueDeltaPercent = if (prevRevenue > 0) ((totalRevenue - prevRevenue) / prevRevenue * 100).toInt() else 0
    val expenseDeltaPercent = if (prevExpense > 0) ((totalExpense - prevExpense) / prevExpense * 100).toInt() else 0

    // Spending by Category calculation
    val categorySpendingList = remember(rangeTransactions, categories) {
        val map = rangeTransactions.filter { it.type == "DESPESA" && it.category_id != null }
            .groupBy { it.category_id!! }
            .mapValues { entry -> entry.value.sumOf { it.value } }
            
        categories.map { cat ->
            val total = map[cat.id] ?: 0.0
            Pair(cat, total)
        }.filter { it.second > 0.0 }.sortedByDescending { it.second }
    }

    val numMonthsSelected = remember(startMonthStr, endMonthStr) {
        try {
            val partsStart = startMonthStr.split("-")
            val partsEnd = endMonthStr.split("-")
            val yearStart = partsStart[0].toInt()
            val monthStart = partsStart[1].toInt()
            val yearEnd = partsEnd[0].toInt()
            val monthEnd = partsEnd[1].toInt()
            
            val diff = ((yearEnd - yearStart) * 12) + (monthEnd - monthStart) + 1
            diff.coerceAtLeast(1)
        } catch (e: Exception) {
            1
        }
    }

    val averageCategorySpendingList = remember(categorySpendingList, numMonthsSelected) {
        categorySpendingList.map { (cat, total) ->
            Pair(cat, total / numMonthsSelected)
        }
    }

    // Weekly Analysis (total spent per week)
    val weeklySpendingList = remember(rangeTransactions) {
        val weekMap = TreeMap<Int, Double>()
        val cal = Calendar.getInstance()
        val sdfParse = SimpleDateFormat("yyyy-MM-DD", Locale.US)
        rangeTransactions.filter { it.type == "DESPESA" }.forEach { tx ->
            try {
                val date = sdfParse.parse(tx.date)
                if (date != null) {
                    cal.time = date
                    val weekOfYear = cal.get(Calendar.WEEK_OF_YEAR)
                    weekMap[weekOfYear] = (weekMap[weekOfYear] ?: 0.0) + tx.value
                }
            } catch (e: Exception) {
                // Fallback: estimate week of month
                val day = if (tx.date.length >= 10) tx.date.substring(8, 10).toIntOrNull() ?: 1 else 1
                val estWeek = (day / 7).coerceIn(1, 4)
                weekMap[estWeek] = (weekMap[estWeek] ?: 0.0) + tx.value
            }
        }
        weekMap.entries.toList()
    }

    // Top N Biggest Expenses (e.g. top 5)
    val topExpenses = remember(rangeTransactions, categories, subcategories) {
        rangeTransactions.filter { it.type == "DESPESA" }
            .sortedByDescending { it.value }
            .take(5)
    }

    // Monthly revenues and expenses side-by-side (last 12 months timeline)
    val last12MonthsData = remember(transactions, selectedMonthCalendar) {
        val today = selectedMonthCalendar.clone() as Calendar
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        val months = (0..11).map { i ->
            val c = today.clone() as Calendar
            c.add(Calendar.MONTH, -i)
            sdf.format(c.time)
        }.reversed()

        months.map { m ->
            val txsInMonth = transactions.filter { it.date.length >= 7 && it.date.substring(0, 7) == m }
            val rev = txsInMonth.filter { it.type == "RECEITA" }.sumOf { it.value }
            val exp = txsInMonth.filter { it.type == "DESPESA" }.sumOf { it.value }
            Triple(m, rev, exp)
        }
    }

    // Net Worth Evolution timeline (last 6 months)
    val netWorthTimeline = remember(transactions, accounts, selectedMonthCalendar) {
        val today = selectedMonthCalendar.clone() as Calendar
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        val months = (0..5).map { i ->
            val c = today.clone() as Calendar
            c.add(Calendar.MONTH, -i)
            sdf.format(c.time)
        }.reversed()

        val initialBalanceSum = accounts.sumOf { it.initial_balance }

        months.map { m ->
            val txsUpToMonth = transactions.filter { it.date.length >= 7 && it.date.substring(0, 7) <= m }
            val totalRev = txsUpToMonth.filter { it.type == "RECEITA" }.sumOf { it.value }
            val totalExp = txsUpToMonth.filter { it.type == "DESPESA" }.sumOf { it.value }
            val balance = initialBalanceSum + totalRev - totalExp
            Pair(m, balance)
        }
    }

    // Historical Goals Progress Timeline
    val historicalGoalsTimeline = remember(goals, allocationMovements, selectedMonthCalendar) {
        val today = selectedMonthCalendar.clone() as Calendar
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        val months = (0..5).map { i ->
            val c = today.clone() as Calendar
            c.add(Calendar.MONTH, -i)
            sdf.format(c.time)
        }.reversed()

        months.map { m ->
            val allocationsInMonth = budgetAllocations.filter { it.month <= m }.associate { it.id to it.month }
            
            // For each goal, aggregate net allocations up to this month
            val goalBalancesAtM = goals.associate { goal ->
                val destSum = allocationMovements.filter { 
                    it.dest_goal_id == goal.id && 
                    sdfMonth.format(Date(it.moved_at)) <= m 
                }.sumOf { it.amount }
                val sourceSum = allocationMovements.filter { 
                    it.source_goal_id == goal.id && 
                    sdfMonth.format(Date(it.moved_at)) <= m 
                }.sumOf { it.amount }
                goal.id to (destSum - sourceSum)
            }
            Pair(m, goalBalancesAtM)
        }
    }

    // Current goal balances
    val goalBalances = remember(goals, allocationMovements) {
        goals.associate { goal ->
            val destSum = allocationMovements.filter { it.dest_goal_id == goal.id }.sumOf { it.amount }
            val sourceSum = allocationMovements.filter { it.source_goal_id == goal.id }.sumOf { it.amount }
            goal.id to (destSum - sourceSum)
        }
    }

    // --- "What-If" Simulator State ---
    var simulatorTargetGoalId by remember { mutableStateOf<Int?>(null) }
    var simulatorCategoryId by remember { mutableStateOf<Int?>(null) }
    var simulatorReductionPercent by remember { mutableStateOf(20f) }

    val selectedSimGoal = remember(simulatorTargetGoalId, goals) {
        goals.find { it.id == simulatorTargetGoalId } ?: goals.firstOrNull()
    }
    val selectedSimCategory = remember(simulatorCategoryId, categories) {
        categories.find { it.id == simulatorCategoryId } ?: categories.firstOrNull()
    }

    // Period-over-period category spending map
    val prevCategorySpendingMap = remember(transactions, selectedPeriodType, categories, startMonthStr, endMonthStr, selectedMonthCalendar) {
        val today = selectedMonthCalendar.clone() as Calendar
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        val (pStart, pEnd) = when (selectedPeriodType) {
            "MÊS" -> {
                val cal = today.clone() as Calendar
                cal.add(Calendar.MONTH, -1)
                val m = sdf.format(cal.time)
                Pair(m, m)
            }
            "TRIMESTRE" -> {
                val calEnd = today.clone() as Calendar
                calEnd.add(Calendar.MONTH, -3)
                val end = sdf.format(calEnd.time)
                val calStart = calEnd.clone() as Calendar
                calStart.add(Calendar.MONTH, -2)
                val start = sdf.format(calStart.time)
                Pair(start, end)
            }
            "ANO" -> {
                val calEnd = today.clone() as Calendar
                calEnd.add(Calendar.YEAR, -1)
                val end = sdf.format(calEnd.time)
                val calStart = calEnd.clone() as Calendar
                calStart.add(Calendar.MONTH, -11)
                val start = sdf.format(calStart.time)
                Pair(start, end)
            }
            else -> {
                try {
                    val startMonthCal = Calendar.getInstance().apply {
                        val parts = startMonthStr.split("-")
                        set(Calendar.YEAR, parts[0].toInt())
                        set(Calendar.MONTH, parts[1].toInt() - 1)
                    }
                    val endMonthCal = Calendar.getInstance().apply {
                        val parts = endMonthStr.split("-")
                        set(Calendar.YEAR, parts[0].toInt())
                        set(Calendar.MONTH, parts[1].toInt() - 1)
                    }
                    val diffMonths = ((endMonthCal.get(Calendar.YEAR) - startMonthCal.get(Calendar.YEAR)) * 12) +
                            (endMonthCal.get(Calendar.MONTH) - startMonthCal.get(Calendar.MONTH)) + 1
                    
                    val prevEndCal = startMonthCal.clone() as Calendar
                    prevEndCal.add(Calendar.MONTH, -1)
                    val prevStartCal = prevEndCal.clone() as Calendar
                    prevStartCal.add(Calendar.MONTH, -(diffMonths - 1))
                    Pair(sdf.format(prevStartCal.time), sdf.format(prevEndCal.time))
                } catch (e: Exception) {
                    Pair(startMonthStr, endMonthStr)
                }
            }
        }

        val prevTxs = transactions.filter {
            if (it.date.length < 7) false
            else {
                val m = it.date.substring(0, 7)
                m >= pStart && m <= pEnd
            }
        }
        val map = prevTxs.filter { it.type == "DESPESA" && it.category_id != null }
            .groupBy { it.category_id!! }
            .mapValues { entry -> entry.value.sumOf { it.value } }
        categories.associate { cat ->
            cat.id to (map[cat.id] ?: 0.0)
        }
    }

    // Planned vs Allocated Trend for the selected category
    val selectedCategoryForTrend = filteredCategoryId ?: categories.firstOrNull()?.id
    val plannedVsAllocatedTimeline = remember(selectedCategoryForTrend, budgetAllocations, allocationMovements) {
        if (selectedCategoryForTrend == null) emptyList()
        else {
            val today = Calendar.getInstance()
            val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
            val months = (0..5).map { i ->
                val c = today.clone() as Calendar
                c.add(Calendar.MONTH, -i)
                sdf.format(c.time)
            }.reversed()

            months.map { m ->
                val allocInMonth = budgetAllocations.filter { it.category_id == selectedCategoryForTrend && it.month == m }
                val plannedVal = allocInMonth.sumOf { it.planned_value }
                
                val netAlloc = allocationMovements.filter { mov ->
                    val sourceAlloc = budgetAllocations.find { it.id == mov.source_budget_allocation_id }
                    val destAlloc = budgetAllocations.find { it.id == mov.dest_budget_allocation_id }
                    
                    val matchesSource = sourceAlloc?.category_id == selectedCategoryForTrend && sourceAlloc.month == m
                    val matchesDest = destAlloc?.category_id == selectedCategoryForTrend && destAlloc.month == m
                    
                    matchesSource || matchesDest
                }.sumOf { mov ->
                    val sourceAlloc = budgetAllocations.find { it.id == mov.source_budget_allocation_id }
                    val isDest = sourceAlloc?.category_id != selectedCategoryForTrend
                    if (isDest) mov.amount else -mov.amount
                }
                
                Triple(m, plannedVal, plannedVal + netAlloc)
            }
        }
    }

    // Committed installments maturing in the current real-world month
    val currentMonthStr = remember { SimpleDateFormat("yyyy-MM", Locale.US).format(Date()) }
    val activeInstallmentsThisMonth = remember(installmentPlans, currentMonthStr) {
        installmentPlans.map { plan ->
            try {
                val firstMonthStr = plan.first_installment_month
                val firstMonthCal = Calendar.getInstance().apply {
                    val parts = firstMonthStr.split("-")
                    set(Calendar.YEAR, parts[0].toInt())
                    set(Calendar.MONTH, parts[1].toInt() - 1)
                }
                val currentMonthCal = Calendar.getInstance().apply {
                    val parts = currentMonthStr.split("-")
                    set(Calendar.YEAR, parts[0].toInt())
                    set(Calendar.MONTH, parts[1].toInt() - 1)
                }
                val monthsDiff = ((currentMonthCal.get(Calendar.YEAR) - firstMonthCal.get(Calendar.YEAR)) * 12) +
                        (currentMonthCal.get(Calendar.MONTH) - firstMonthCal.get(Calendar.MONTH))
                
                if (monthsDiff in 0 until plan.installments_count) {
                    val installmentValue = plan.total_value / plan.installments_count
                    Pair(plan.description + " (${monthsDiff + 1}/${plan.installments_count})", installmentValue)
                } else null
            } catch (e: Exception) { null }
        }.filterNotNull()
    }
    val totalCommittedInstallmentsThisMonth = remember(activeInstallmentsThisMonth) {
        activeInstallmentsThisMonth.sumOf { it.second }
    }

    // Calculate Average monthly spending in selected simulator category Y
    val averageSimCategorySpend = remember(selectedSimCategory, transactions) {
        if (selectedSimCategory == null) 0.0
        else {
            val txsInCat = transactions.filter { it.type == "DESPESA" && it.category_id == selectedSimCategory.id }
            val uniqueMonthsCount = txsInCat.map { if (it.date.length >= 7) it.date.substring(0, 7) else "" }.distinct().filter { it.isNotEmpty() }.size.coerceAtLeast(1)
            txsInCat.sumOf { it.value } / uniqueMonthsCount
        }
    }

    // What-if simulator output card text
    val simulatorSavingsAmount = averageSimCategorySpend * (simulatorReductionPercent / 100.0)
    val simulatorMonthsRequiredText = remember(selectedSimGoal, simulatorSavingsAmount, goalBalances) {
        if (selectedSimGoal == null || simulatorSavingsAmount <= 0.0) {
            "Selecione uma meta e uma categoria com gastos históricos para simular."
        } else {
            val currentVal = goalBalances[selectedSimGoal.id] ?: 0.0
            val remainingVal = (selectedSimGoal.target_value - currentVal).coerceAtLeast(0.0)
            if (remainingVal <= 0.0) {
                "Sua meta '${selectedSimGoal.name}' já foi alcançada! 🎉"
            } else {
                val impactPct = if (currentVal > 0.0) (simulatorSavingsAmount / currentVal) * 100.0 else 100.0
                val impactPctFormatted = String.format(Locale("pt", "BR"), "+%.1f%%", impactPct)
                
                val currentValFormatted = currencyFormatter.format(currentVal)
                val simulatedCurrentValFormatted = currencyFormatter.format(currentVal + simulatorSavingsAmount)
                val simulatedRemainingValFormatted = currencyFormatter.format((selectedSimGoal.target_value - (currentVal + simulatorSavingsAmount)).coerceAtLeast(0.0))
                val targetValFormatted = currencyFormatter.format(selectedSimGoal.target_value)
                
                "Isso representa $impactPctFormatted do que você já tem guardado nessa meta hoje ($currentValFormatted → $simulatedCurrentValFormatted). Ainda faltariam $simulatedRemainingValFormatted pra bater a meta de $targetValFormatted."
            }
        }
    }

    // --- Unified general movements list ---
    val auditHistoryList = remember(allocationMovements, categories, subcategories, goals) {
        allocationMovements.map { m ->
            val isAporte = m.dest_goal_id != null
            val isRetirada = m.source_goal_id != null
            val isTransferencia = m.source_budget_allocation_id != null && m.dest_budget_allocation_id != null
            
            val typeStr = when {
                isAporte -> "Meta: Aporte 🎯"
                isRetirada -> "Meta: Retirada 📤"
                isTransferencia -> "Transferência de Envelope 🔄"
                else -> "Ajuste de Planejamento ⚡"
            }
            
            val details = getAuditMovementsText(
                movement = m,
                categories = categories,
                subcategories = subcategories,
                budgetAllocations = budgetAllocations,
                goals = goals
            )
            
            Triple(m, typeStr, details)
        }.sortedByDescending { it.first.moved_at }
    }

    // --- CC Invoice & Fixed Bill Projection (Next 3 months) ---
    val projectedInvoicesList = remember(recurrenceRules, installmentPlans, selectedMonthCalendar) {
        val today = selectedMonthCalendar.clone() as Calendar
        val sdfYearMonth = SimpleDateFormat("yyyy-MM", Locale.US)
        
        val futureMonths = (1..3).map { i ->
            val c = today.clone() as Calendar
            c.add(Calendar.MONTH, i)
            sdfYearMonth.format(c.time)
        }

        futureMonths.map { monthStr ->
            // 1. Installments projected
            val installmentsSum = installmentPlans.sumOf { plan ->
                val firstMonthStr = plan.first_installment_month
                try {
                    val firstMonthCal = Calendar.getInstance().apply {
                        val parts = firstMonthStr.split("-")
                        set(Calendar.YEAR, parts[0].toInt())
                        set(Calendar.MONTH, parts[1].toInt() - 1)
                    }
                    val targetMonthCal = Calendar.getInstance().apply {
                        val parts = monthStr.split("-")
                        set(Calendar.YEAR, parts[0].toInt())
                        set(Calendar.MONTH, parts[1].toInt() - 1)
                    }
                    val monthsDiff = ((targetMonthCal.get(Calendar.YEAR) - firstMonthCal.get(Calendar.YEAR)) * 12) +
                            (targetMonthCal.get(Calendar.MONTH) - firstMonthCal.get(Calendar.MONTH))
                    
                    if (monthsDiff in 0 until plan.installments_count) {
                        plan.total_value / plan.installments_count
                    } else 0.0
                } catch (e: Exception) { 0.0 }
            }

            // 2. Recurrence Rules projected
            val recurrenceSum = recurrenceRules.filter { it.active }.sumOf { rule ->
                // Basic monthly/annual check
                try {
                    val startParts = rule.start_date.split("-") // YYYY-MM-DD
                    val ruleStartMonth = "${startParts[0]}-${startParts[1]}"
                    
                    if (monthStr >= ruleStartMonth && (rule.end_month == null || monthStr <= rule.end_month)) {
                        rule.value
                    } else 0.0
                } catch (e: Exception) { 0.0 }
            }

            Triple(monthStr, installmentsSum, recurrenceSum)
        }
    }

    // --- MAIN SCREEN CONTENT ---
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Title heading
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Análise Financeira 📊",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Gráficos, projeções e inteligência de envelopes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Period Comparison Selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Período de Análise",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("MÊS", "TRIMESTRE", "ANO", "CUSTOMIZADO").forEach { pType ->
                            val isSelected = selectedPeriodType == pType
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedPeriodType = pType },
                                label = { Text(pType, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (selectedPeriodType == "CUSTOMIZADO") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Start month field
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showStartPicker = true }
                            ) {
                                OutlinedTextField(
                                    value = displaySdf.format(customStartCal.time).replaceFirstChar { it.uppercase() },
                                    onValueChange = {},
                                    enabled = false,
                                    label = { Text("Mês Inicial") },
                                    shape = RoundedCornerShape(10.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                            
                            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))

                            // End month field
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showEndPicker = true }
                            ) {
                                OutlinedTextField(
                                    value = displaySdf.format(customEndCal.time).replaceFirstChar { it.uppercase() },
                                    onValueChange = {},
                                    enabled = false,
                                    label = { Text("Mês Final") },
                                    shape = RoundedCornerShape(10.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }
                    } else {
                        // Display resolved date interval label
                        Text(
                            text = "De: ${formatMonthPortuguese(startMonthStr)} até ${formatMonthPortuguese(endMonthStr)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Financial KPI Cards (Revenue vs Expense comparison)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Receipts card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.ArrowCircleUp, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                            Text("Receitas", style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            currencyFormatter.format(totalRevenue),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (revenueDeltaPercent >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = if (revenueDeltaPercent >= 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${if (revenueDeltaPercent >= 0) "+" else ""}$revenueDeltaPercent% vs ant.",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (revenueDeltaPercent >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }
                }

                // Expenses card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.ArrowCircleDown, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                            Text("Despesas", style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            currencyFormatter.format(totalExpense),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (expenseDeltaPercent <= 0) Icons.Default.TrendingDown else Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = if (expenseDeltaPercent <= 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${if (expenseDeltaPercent >= 0) "+" else ""}$expenseDeltaPercent% vs ant.",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (expenseDeltaPercent <= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }
                }
            }
        }

        // Total Net Savings Progress Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(enabled = true),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Saldo Líquido no Período", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = currencyFormatter.format(netSavings),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if (netSavings >= 0.0) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = if (netSavings >= 0.0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (netSavings >= 0.0) Icons.Default.Savings else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (netSavings >= 0.0) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }
            }
        }

        // CATEGORY SPENDING ANALYSIS SECTION (Interactive Bars with Click Filtering)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(enabled = true)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Gastos por Categoria 🛍️",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (filteredCategoryId != null) {
                            TextButton(
                                onClick = { filteredCategoryId = null },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Limpar filtro", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    CategorySpendingChart(
                        data = categorySpendingList,
                        currencyFormatter = currencyFormatter,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    if (categorySpendingList.isEmpty()) {
                        Text(
                            "Nenhum gasto registrado neste período.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        )
                    } else {
                        val maxSpend = categorySpendingList.maxOfOrNull { it.second } ?: 1.0
                        
                        categorySpendingList.forEach { (cat, amount) ->
                            val isFilterApplied = filteredCategoryId == cat.id
                            val pctOfMax = (amount / maxSpend).toFloat().coerceIn(0.01f, 1.0f)
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        filteredCategoryId = if (isFilterApplied) null else cat.id 
                                    }
                                    .background(
                                        color = if (isFilterApplied) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cat.name + (if (isFilterApplied) " (Ativo 📌)" else ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isFilterApplied) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isFilterApplied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = currencyFormatter.format(amount),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                // Beautiful styled horizontal distribution bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(pctOfMax)
                                            .fillMaxHeight()
                                            .background(
                                                brush = Brush.horizontalGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                        MaterialTheme.colorScheme.primary
                                                    )
                                                ),
                                                shape = RoundedCornerShape(5.dp)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // MÉDIA DE GASTOS POR CATEGORIA (Novo Card debaixo do primeiro)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(enabled = true)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            "Média Mensal por Categoria 📊",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val periodLabel = if (numMonthsSelected > 1) {
                            "Média mensal baseada em $numMonthsSelected meses selecionados"
                        } else {
                            "Média mensal (período de 1 mês)"
                        }
                        Text(
                            periodLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    CategorySpendingChart(
                        data = averageCategorySpendingList,
                        currencyFormatter = currencyFormatter,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        centerLabel = "Média"
                    )

                    if (averageCategorySpendingList.isEmpty()) {
                        Text(
                            "Nenhum gasto registrado neste período.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        )
                    } else {
                        val maxAverage = averageCategorySpendingList.maxOfOrNull { it.second } ?: 1.0
                        
                        averageCategorySpendingList.forEach { (cat, amount) ->
                            val pctOfMax = (amount / maxAverage).toFloat().coerceIn(0.01f, 1.0f)
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cat.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = currencyFormatter.format(amount),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(pctOfMax)
                                            .fillMaxHeight()
                                            .background(
                                                brush = Brush.horizontalGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                                                        MaterialTheme.colorScheme.secondary
                                                    )
                                                ),
                                                shape = RoundedCornerShape(5.dp)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // COMPARATIVO ENTRE PERÍODOS POR CATEGORIA (Card 5)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(enabled = true)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Comparativo entre Períodos por Categoria 🔄",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Comparação dos gastos do período selecionado contra o período anterior equivalente",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    PeriodComparisonChart(
                        categories = categories,
                        currentSpending = categorySpendingList,
                        prevSpendingMap = prevCategorySpendingMap,
                        currencyFormatter = currencyFormatter,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // MONTHLY REVENUE VS EXPENSE COMPARISON (Last 12 Months Visual Comparative Grid)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(enabled = true)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Entradas vs Saídas Mensais (12 meses) 📈",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    RevenueExpenseChart(
                        data = last12MonthsData,
                        currencyFormatter = currencyFormatter,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // WEEKLY EXPENSE ANALYSIS CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(enabled = true)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Análise Semanal (Gasto Total) 🗓️",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    WeeklySpendingChart(
                        data = weeklySpendingList,
                        currencyFormatter = currencyFormatter,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (weeklySpendingList.isEmpty()) {
                        Text(
                            "Nenhum gasto registrado neste período.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        weeklySpendingList.forEach { (week, amount) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                    Text("Semana $week", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                                Text(
                                    currencyFormatter.format(amount),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // TOP EXPENSES SECTION
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(enabled = true)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Maiores Despesas do Período 💸",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (topExpenses.isEmpty()) {
                        Text(
                            "Nenhuma despesa registrada no período.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        topExpenses.forEach { tx ->
                            val catName = categories.find { it.id == tx.category_id }?.name ?: "Sem Categoria"
                            val subName = subcategories.find { it.id == tx.subcategory_id }?.name
                            val label = if (subName != null) "$catName › $subName" else catName
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tx.description, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text("$label • ${tx.date}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                                Text(
                                    "-${currencyFormatter.format(tx.value)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFC62828)
                                )
                            }
                        }
                    }
                }
            }
        }

        // NET WORTH EVOLUTION (Cumulative Accounts Trend)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(enabled = true)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Evolução do Patrimônio Líquido 🏦",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Saldo cumulativo de todas as suas contas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    NetWorthTimelineChart(
                        data = netWorthTimeline,
                        currencyFormatter = currencyFormatter,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    netWorthTimeline.forEach { (month, balance) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(formatMonthPortuguese(month), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text(
                                currencyFormatter.format(balance),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // VISÃO TEMPORAL DE PLANEJADO VS ALOCADO POR ENVELOPE (Detailed envelope allocations)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(enabled = true)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Planejado vs Alocado Mensal por Categoria 📁",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Acompanhe se as alocações planejadas estão subindo ou descendo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    PlannedVsAllocatedChart(
                        data = plannedVsAllocatedTimeline,
                        currencyFormatter = currencyFormatter,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val recentMonths = budgetAllocations.map { it.month }.distinct().sorted().takeLast(3)

                    if (categories.isEmpty() || recentMonths.isEmpty()) {
                        Text(
                            "Nenhum planejamento registrado nos envelopes ainda.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    } else {
                        categories.take(4).forEach { cat ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(cat.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                
                                recentMonths.forEach { m ->
                                    val allocInMonth = budgetAllocations.filter { it.category_id == cat.id && it.month == m }
                                    val plannedVal = allocInMonth.sumOf { it.planned_value }
                                    
                                    // Net alocated up to month
                                    val netAlloc = allocationMovements.filter { mov ->
                                        // Filter movements involving this category
                                        val sourceAlloc = budgetAllocations.find { it.id == mov.source_budget_allocation_id }
                                        val destAlloc = budgetAllocations.find { it.id == mov.dest_budget_allocation_id }
                                        
                                        val matchesSource = sourceAlloc?.category_id == cat.id && sourceAlloc.month == m
                                        val matchesDest = destAlloc?.category_id == cat.id && destAlloc.month == m
                                        
                                        matchesSource || matchesDest
                                    }.sumOf { mov ->
                                        val sourceAlloc = budgetAllocations.find { it.id == mov.source_budget_allocation_id }
                                        val isDest = sourceAlloc?.category_id != cat.id
                                        if (isDest) mov.amount else -mov.amount
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(formatMonthPortuguese(m), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                                        Text(
                                            "Plan: ${currencyFormatter.format(plannedVal)} | Alocado: ${currencyFormatter.format(plannedVal + netAlloc)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // HISTORICAL GOALS PROGRESS OVER TIME (Metas timeline trend)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(enabled = true)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Progresso das Metas na Linha do Tempo 🎯",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    GoalsProgressTimelineChart(
                        goals = goals,
                        timelineData = historicalGoalsTimeline,
                        currencyFormatter = currencyFormatter,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (goals.isEmpty()) {
                        Text(
                            "Nenhuma meta cadastrada ainda.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    } else {
                        historicalGoalsTimeline.forEach { (m, goalBalancesMap) ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    formatMonthPortuguese(m),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                goals.forEach { goal ->
                                    val valAtM = goalBalancesMap[goal.id] ?: 0.0
                                    val progressPct = if (goal.target_value > 0) (valAtM / goal.target_value * 100).toInt() else 0
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(goal.name, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, maxLines = 1)
                                        Text(
                                            "${currencyFormatter.format(valAtM)} ($progressPct%)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // CONSOLIDATED CREDIT CARD & FIXED BILL PROJECTION (Upcoming invoice forecasts)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(enabled = true)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Projeção de Faturas e Contas Fixas 💳",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Previsão consolidada para os próximos 3 meses (recorrências e parcelas futuras)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    ProjectedInvoicesChart(
                        data = projectedInvoicesList,
                        currencyFormatter = currencyFormatter,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    projectedInvoicesList.forEach { (monthStr, installments, recurrence) ->
                        val totalProjected = installments + recurrence
                        
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatMonthPortuguese(monthStr),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = currencyFormatter.format(totalProjected),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Compras Parceladas (Faturas):", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(currencyFormatter.format(installments), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Despesas Fixas Recorrentes:", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(currencyFormatter.format(recurrence), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // "WHAT-IF" SIMULATOR CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(enabled = true)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Simulador Financeiro \"What-If\" 🧠",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Descubra como pequenas reduções de gasto aceleram suas metas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    // Target Goal Dropdown Selector
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Selecione sua Meta Alvo:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(goals) { goal ->
                                val isSelected = simulatorTargetGoalId == goal.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { simulatorTargetGoalId = goal.id },
                                    label = { Text(goal.name) }
                                )
                            }
                        }
                    }

                    // Target Category Dropdown Selector
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Categoria de Gasto para Economizar:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(categories) { cat ->
                                val isSelected = simulatorCategoryId == cat.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { simulatorCategoryId = cat.id },
                                    label = { Text(cat.name) }
                                )
                            }
                        }
                    }

                    // Slider for percentage reduction
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Porcentagem de Redução:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text("${simulatorReductionPercent.toInt()}% de redução", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = simulatorReductionPercent,
                            onValueChange = { simulatorReductionPercent = it },
                            valueRange = 5f..80f,
                            steps = 14
                        )
                    }

                    // Output results card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Resultado da Simulação", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                text = simulatorMonthsRequiredText,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // AUDIT AND MOVEMENT HISTORY LIST (Aportes, Retiradas, Transferências)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(enabled = true)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Histórico Geral de Movimentações 📜",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Auditoria completa de transferências de envelopes, aportes e retiradas de metas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    if (auditHistoryList.isEmpty()) {
                        Text(
                            "Nenhuma movimentação realizada ainda.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        auditHistoryList.take(20).forEach { (movement, typeStr, details) ->
                            val dateStr = remember(movement.moved_at) {
                                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(movement.moved_at))
                            }
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = typeStr,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = currencyFormatter.format(movement.amount),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Text(
                                    text = details,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                if (!movement.note.isNullOrBlank()) {
                                    Text(
                                        text = "Obs: \"${movement.note}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(top = 6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Picker Dialogs for Custom range
    if (showStartPicker) {
        MonthYearPickerDialog(
            currentCalendar = customStartCal,
            onDismiss = { showStartPicker = false },
            onSelected = { year, month ->
                customStartCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                }
                showStartPicker = false
            }
        )
    }

    if (showEndPicker) {
        MonthYearPickerDialog(
            currentCalendar = customEndCal,
            onDismiss = { showEndPicker = false },
            onSelected = { year, month ->
                customEndCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                }
                showEndPicker = false
            }
        )
    }
}

private fun getAuditMovementsText(
    movement: AllocationMovement,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    budgetAllocations: List<BudgetAllocation>,
    goals: List<Goal>
): String {
    val allocationMap = budgetAllocations.associateBy { it.id }

    fun getAllocationLabel(allocId: Int?): String {
        if (allocId == null) return "Pronto para Atribuir"
        val alloc = allocationMap[allocId] ?: return "Envelope"
        val cat = categories.firstOrNull { it.id == alloc.category_id }?.name ?: "Envelope"
        val sub = subcategories.firstOrNull { it.id == alloc.subcategory_id }?.name
        return if (sub != null) "$cat › $sub" else cat
    }

    fun getGoalLabel(goalId: Int?): String {
        if (goalId == null) return "Pronto para Atribuir"
        return goals.firstOrNull { it.id == goalId }?.name ?: "Meta"
    }

    val fromLabel = if (movement.source_goal_id != null) {
        getGoalLabel(movement.source_goal_id)
    } else {
        getAllocationLabel(movement.source_budget_allocation_id)
    }

    val toLabel = if (movement.dest_goal_id != null) {
        getGoalLabel(movement.dest_goal_id)
    } else {
        getAllocationLabel(movement.dest_budget_allocation_id)
    }

    return "Origem: $fromLabel ➔ Destino: $toLabel"
}

@Composable
fun CategorySpendingChart(
    data: List<Pair<Category, Double>>,
    currencyFormatter: NumberFormat,
    modifier: Modifier = Modifier,
    centerLabel: String = "Total"
) {
    val total = remember(data) { data.sumOf { it.second } }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val labelStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
    val centerAmountStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
    val centerLabelStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (data.isEmpty() || total <= 0.0) {
            Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                Text("Sem dados de gastos para exibir no período.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Box(
                modifier = Modifier.size(130.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val colors = listOf(
                        Color(0xFF2196F3), Color(0xFFE91E63), Color(0xFF4CAF50), Color(0xFFFF9800),
                        Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFFEB3B), Color(0xFF795548)
                    )
                    var startAngle = -90f
                    data.forEachIndexed { index, (_, amount) ->
                        val sweepAngle = (amount / total * 360f).toFloat()
                        drawArc(
                            color = colors[index % colors.size],
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true
                        )
                        startAngle += sweepAngle
                    }
                    
                    drawCircle(
                        color = surfaceColor,
                        radius = size.minDimension / 3.2f
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(centerLabel, style = centerLabelStyle)
                    Text(
                        currencyFormatter.format(total).replace("R$", "").trim(),
                        style = centerAmountStyle
                    )
                }
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val colors = listOf(
                    Color(0xFF2196F3), Color(0xFFE91E63), Color(0xFF4CAF50), Color(0xFFFF9800),
                    Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFFEB3B), Color(0xFF795548)
                )
                data.take(5).forEachIndexed { index, (cat, amount) ->
                    val pct = (amount / total * 100).toInt()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(colors[index % colors.size], RoundedCornerShape(2.dp))
                        )
                        Text(
                            text = "${cat.name} ($pct%)",
                            style = labelStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (data.size > 5) {
                    Text("+ ${data.size - 5} categorias", style = centerLabelStyle, modifier = Modifier.padding(start = 14.dp))
                }
            }
        }
    }
}

@Composable
fun RevenueExpenseChart(
    data: List<Triple<String, Double, Double>>,
    currencyFormatter: NumberFormat,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
    val textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    
    val gridColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val revenueColor = Color(0xFF2E7D32)
    val expenseColor = Color(0xFFC62828)
    
    val maxVal = remember(data) {
        val maxInList = data.maxOfOrNull { Math.max(it.second, it.third) } ?: 1.0
        if (maxInList <= 0) 1000.0 else maxInList
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(10.dp).background(revenueColor, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Receitas", style = labelStyle)
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.size(10.dp).background(expenseColor, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Despesas", style = labelStyle)
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            val barWidth = 12.dp
            val monthWidth = 64.dp
            val canvasWidth = monthWidth * data.size
            
            Canvas(
                modifier = Modifier
                    .width(canvasWidth)
                    .height(170.dp)
                    .padding(horizontal = 8.dp)
            ) {
                val height = size.height
                val width = size.width
                val bottomY = height - 25f
                val topY = 20f
                val drawHeight = bottomY - topY
                
                val gridLinesCount = 3
                for (i in 0..gridLinesCount) {
                    val y = bottomY - (drawHeight * i / gridLinesCount)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                    
                    val labelValue = maxVal * i / gridLinesCount
                    drawText(
                        textMeasurer = textMeasurer,
                        text = currencyFormatter.format(labelValue).replace("R$", "").trim(),
                        style = textStyle,
                        topLeft = Offset(5f, y - 12f)
                    )
                }
                
                data.forEachIndexed { index, (month, rev, exp) ->
                    val xCenter = (index * monthWidth.toPx()) + (monthWidth.toPx() / 2)
                    
                    val revHeight = (rev / maxVal * drawHeight).toFloat().coerceAtLeast(2f)
                    val expHeight = (exp / maxVal * drawHeight).toFloat().coerceAtLeast(2f)
                    
                    val revX = xCenter - barWidth.toPx() - 2f
                    drawRoundRect(
                        color = revenueColor,
                        topLeft = Offset(revX, bottomY - revHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth.toPx(), revHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )
                    
                    val expX = xCenter + 2f
                    drawRoundRect(
                        color = expenseColor,
                        topLeft = Offset(expX, bottomY - expHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth.toPx(), expHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )
                    
                    val monthLabel = try {
                        val parts = month.split("-")
                        val monthIdx = parts[1].toInt() - 1
                        val monthsAbbr = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
                        "${monthsAbbr[monthIdx]}/${parts[0].substring(2)}"
                    } catch (e: Exception) { month }
                    
                    val textLayoutResult = textMeasurer.measure(monthLabel, textStyle)
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(xCenter - (textLayoutResult.size.width / 2), bottomY + 4f)
                    )
                }
            }
        }
    }
}

@Composable
fun WeeklySpendingChart(
    data: List<Map.Entry<Int, Double>>,
    currencyFormatter: NumberFormat,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val barColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    
    val maxVal = remember(data) {
        val maxInList = data.maxOfOrNull { it.value } ?: 1.0
        if (maxInList <= 0) 100.0 else maxInList
    }
    
    if (data.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
            Text("Nenhum gasto registrado nesta semana.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = 8.dp)
        ) {
            val height = size.height
            val width = size.width
            val bottomY = height - 25f
            val topY = 15f
            val drawHeight = bottomY - topY
            
            val gridLinesCount = 3
            for (i in 0..gridLinesCount) {
                val y = bottomY - (drawHeight * i / gridLinesCount)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
                
                val labelValue = maxVal * i / gridLinesCount
                drawText(
                    textMeasurer = textMeasurer,
                    text = currencyFormatter.format(labelValue).replace("R$", "").trim(),
                    style = textStyle,
                    topLeft = Offset(5f, y - 12f)
                )
            }
            
            val colWidth = width / data.size
            data.forEachIndexed { index, entry ->
                val xCenter = (index * colWidth) + (colWidth / 2)
                val barHeight = (entry.value / maxVal * drawHeight).toFloat().coerceAtLeast(2f)
                val barW = (colWidth * 0.4f).coerceIn(12f, 40f)
                
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(xCenter - (barW / 2), bottomY - barHeight),
                    size = androidx.compose.ui.geometry.Size(barW, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
                
                val label = "Sem ${entry.key}"
                val textLayoutResult = textMeasurer.measure(label, textStyle)
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(xCenter - (textLayoutResult.size.width / 2), bottomY + 4f)
                )
            }
        }
    }
}

@Composable
fun NetWorthTimelineChart(
    data: List<Pair<String, Double>>,
    currencyFormatter: NumberFormat,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    
    val minVal = remember(data) {
        val minInList = data.minOfOrNull { it.second } ?: 0.0
        (minInList * 0.9).coerceAtLeast(0.0)
    }
    val maxVal = remember(data) {
        val maxInList = data.maxOfOrNull { it.second } ?: 1000.0
        if (maxInList <= 0) 1000.0 else maxInList * 1.1
    }
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .padding(horizontal = 8.dp)
    ) {
        val height = size.height
        val width = size.width
        val bottomY = height - 25f
        val topY = 20f
        val drawHeight = bottomY - topY
        
        val gridLinesCount = 3
        for (i in 0..gridLinesCount) {
            val y = bottomY - (drawHeight * i / gridLinesCount)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            
            val labelValue = minVal + (maxVal - minVal) * i / gridLinesCount
            drawText(
                textMeasurer = textMeasurer,
                text = currencyFormatter.format(labelValue).replace("R$", "").trim(),
                style = textStyle,
                topLeft = Offset(5f, y - 12f)
            )
        }
        
        if (data.size >= 2) {
            val colWidth = width / (data.size - 1)
            val points = data.mapIndexed { index, (_, value) ->
                val x = index * colWidth
                val pct = if (maxVal > minVal) (value - minVal) / (maxVal - minVal) else 0.5
                val y = bottomY - (pct * drawHeight).toFloat()
                Offset(x, y)
            }
            
            val linePath = Path()
            val fillPath = Path()
            
            linePath.moveTo(points.first().x, points.first().y)
            fillPath.moveTo(points.first().x, bottomY)
            fillPath.lineTo(points.first().x, points.first().y)
            
            for (i in 1 until points.size) {
                val pPrev = points[i - 1]
                val pCurr = points[i]
                val cp1 = Offset(pPrev.x + (pCurr.x - pPrev.x) / 2, pPrev.y)
                val cp2 = Offset(pPrev.x + (pCurr.x - pPrev.x) / 2, pCurr.y)
                
                linePath.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, pCurr.x, pCurr.y)
                fillPath.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, pCurr.x, pCurr.y)
            }
            
            fillPath.lineTo(points.last().x, bottomY)
            fillPath.close()
            
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.3f),
                        lineColor.copy(alpha = 0.0f)
                    ),
                    startY = topY,
                    endY = bottomY
                )
            )
            
            drawPath(
                path = linePath,
                color = lineColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
            )
            
            points.forEachIndexed { index, offset ->
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = offset
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.dp.toPx(),
                    center = offset
                )
                
                val monthLabel = try {
                    val parts = data[index].first.split("-")
                    val monthIdx = parts[1].toInt() - 1
                    val monthsAbbr = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
                    "${monthsAbbr[monthIdx]}/${parts[0].substring(2)}"
                } catch (e: Exception) { data[index].first }
                
                val textLayoutResult = textMeasurer.measure(monthLabel, textStyle)
                val labelX = if (index == 0) 0f else if (index == points.size - 1) offset.x - textLayoutResult.size.width else offset.x - (textLayoutResult.size.width / 2)
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(labelX.coerceAtLeast(0f), bottomY + 4f)
                )
            }
        }
    }
}

@Composable
fun PeriodComparisonChart(
    categories: List<Category>,
    currentSpending: List<Pair<Category, Double>>,
    prevSpendingMap: Map<Int, Double>,
    currencyFormatter: NumberFormat,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
    val textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    
    val currColor = MaterialTheme.colorScheme.primary
    val prevColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
    val gridColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    
    val combinedData = remember(currentSpending, prevSpendingMap) {
        currentSpending.take(5).map { (cat, currAmt) ->
            val prevAmt = prevSpendingMap[cat.id] ?: 0.0
            Triple(cat.name, currAmt, prevAmt)
        }
    }
    
    val maxVal = remember(combinedData) {
        val maxInList = combinedData.maxOfOrNull { Math.max(it.second, it.third) } ?: 1.0
        if (maxInList <= 0) 100.0 else maxInList * 1.1
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(10.dp).background(currColor, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Período Atual", style = labelStyle)
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.size(10.dp).background(prevColor, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Período Anterior", style = labelStyle)
        }
        
        if (combinedData.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                Text("Sem dados comparativos suficientes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(horizontal = 8.dp)
            ) {
                val height = size.height
                val width = size.width
                val bottomY = height - 25f
                val topY = 20f
                val drawHeight = bottomY - topY
                
                val gridLinesCount = 3
                for (i in 0..gridLinesCount) {
                    val y = bottomY - (drawHeight * i / gridLinesCount)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                    
                    val labelValue = maxVal * i / gridLinesCount
                    drawText(
                        textMeasurer = textMeasurer,
                        text = currencyFormatter.format(labelValue).replace("R$", "").trim(),
                        style = textStyle,
                        topLeft = Offset(5f, y - 12f)
                    )
                }
                
                val colWidth = width / combinedData.size
                val barW = (colWidth * 0.25f).coerceIn(8f, 24f)
                
                combinedData.forEachIndexed { index, (catName, curr, prev) ->
                    val xCenter = (index * colWidth) + (colWidth / 2)
                    
                    val currH = (curr / maxVal * drawHeight).toFloat().coerceAtLeast(2f)
                    val prevH = (prev / maxVal * drawHeight).toFloat().coerceAtLeast(2f)
                    
                    drawRoundRect(
                        color = currColor,
                        topLeft = Offset(xCenter - barW - 2f, bottomY - currH),
                        size = androidx.compose.ui.geometry.Size(barW, currH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )
                    
                    drawRoundRect(
                        color = prevColor,
                        topLeft = Offset(xCenter + 2f, bottomY - prevH),
                        size = androidx.compose.ui.geometry.Size(barW, prevH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )
                    
                    val labelText = if (catName.length > 8) catName.substring(0, 7) + ".." else catName
                    val textLayoutResult = textMeasurer.measure(labelText, textStyle)
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(xCenter - (textLayoutResult.size.width / 2), bottomY + 4f)
                    )
                }
            }
        }
    }
}

@Composable
fun PlannedVsAllocatedChart(
    data: List<Triple<String, Double, Double>>,
    currencyFormatter: NumberFormat,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
    val textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    
    val planColor = MaterialTheme.colorScheme.primary
    val allocColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    
    val maxVal = remember(data) {
        val maxInList = data.maxOfOrNull { Math.max(it.second, it.third) } ?: 1.0
        if (maxInList <= 0) 100.0 else maxInList * 1.1
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(10.dp).background(planColor, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Planejado", style = labelStyle)
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.size(10.dp).background(allocColor, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Alocado", style = labelStyle)
        }
        
        if (data.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                Text("Sem dados de planejamento para esta categoria.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(horizontal = 8.dp)
            ) {
                val height = size.height
                val width = size.width
                val bottomY = height - 25f
                val topY = 20f
                val drawHeight = bottomY - topY
                
                val gridLinesCount = 3
                for (i in 0..gridLinesCount) {
                    val y = bottomY - (drawHeight * i / gridLinesCount)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                    
                    val labelValue = maxVal * i / gridLinesCount
                    drawText(
                        textMeasurer = textMeasurer,
                        text = currencyFormatter.format(labelValue).replace("R$", "").trim(),
                        style = textStyle,
                        topLeft = Offset(5f, y - 12f)
                    )
                }
                
                val colWidth = width / (data.size - 1).coerceAtLeast(1)
                
                val planPoints = data.mapIndexed { index, (_, planVal, _) ->
                    val x = index * colWidth
                    val y = bottomY - (planVal / maxVal * drawHeight).toFloat()
                    Offset(x, y)
                }
                
                val allocPoints = data.mapIndexed { index, (_, _, allocVal) ->
                    val x = index * colWidth
                    val y = bottomY - (allocVal / maxVal * drawHeight).toFloat()
                    Offset(x, y)
                }
                
                val planPath = Path()
                planPath.moveTo(planPoints.first().x, planPoints.first().y)
                for (i in 1 until planPoints.size) {
                    planPath.lineTo(planPoints[i].x, planPoints[i].y)
                }
                drawPath(
                    path = planPath,
                    color = planColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
                
                val allocPath = Path()
                allocPath.moveTo(allocPoints.first().x, allocPoints.first().y)
                for (i in 1 until allocPoints.size) {
                    allocPath.lineTo(allocPoints[i].x, allocPoints[i].y)
                }
                drawPath(
                    path = allocPath,
                    color = allocColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
                
                data.forEachIndexed { index, (month, _, _) ->
                    val x = index * colWidth
                    
                    val monthLabel = try {
                        val parts = month.split("-")
                        val monthIdx = parts[1].toInt() - 1
                        val monthsAbbr = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
                        "${monthsAbbr[monthIdx]}/${parts[0].substring(2)}"
                    } catch (e: Exception) { month }
                    
                    val textLayoutResult = textMeasurer.measure(monthLabel, textStyle)
                    val labelX = if (index == 0) 0f else if (index == data.size - 1) x - textLayoutResult.size.width else x - (textLayoutResult.size.width / 2)
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(labelX.coerceAtLeast(0f), bottomY + 4f)
                    )
                }
            }
        }
    }
}

@Composable
fun GoalsProgressTimelineChart(
    goals: List<Goal>,
    timelineData: List<Pair<String, Map<Int, Double>>>,
    currencyFormatter: NumberFormat,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface)
    val textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    
    val colors = listOf(
        Color(0xFF2196F3), Color(0xFFE91E63), Color(0xFF4CAF50), Color(0xFFFF9800),
        Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFFEB3B), Color(0xFF795548)
    )
    
    val maxVal = remember(timelineData, goals) {
        val maxInList = timelineData.flatMap { it.second.values }.maxOrNull() ?: 1.0
        if (maxInList <= 0) 100.0 else maxInList * 1.15
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            goals.take(4).forEachIndexed { idx, goal ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(colors[idx % colors.size], RoundedCornerShape(2.dp)))
                    Text(goal.name, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        
        if (goals.isEmpty() || timelineData.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                Text("Nenhuma meta cadastrada para exibir progresso.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(horizontal = 8.dp)
            ) {
                val height = size.height
                val width = size.width
                val bottomY = height - 25f
                val topY = 20f
                val drawHeight = bottomY - topY
                
                val gridLinesCount = 3
                for (i in 0..gridLinesCount) {
                    val y = bottomY - (drawHeight * i / gridLinesCount)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                    
                    val labelValue = maxVal * i / gridLinesCount
                    drawText(
                        textMeasurer = textMeasurer,
                        text = currencyFormatter.format(labelValue).replace("R$", "").trim(),
                        style = textStyle,
                        topLeft = Offset(5f, y - 12f)
                    )
                }
                
                val colWidth = width / (timelineData.size - 1).coerceAtLeast(1)
                
                goals.forEachIndexed { goalIdx, goal ->
                    val points = timelineData.mapIndexed { monthIdx, (_, balancesMap) ->
                        val balance = balancesMap[goal.id] ?: 0.0
                        val x = monthIdx * colWidth
                        val y = bottomY - (balance / maxVal * drawHeight).toFloat()
                        Offset(x, y)
                    }
                    
                    val linePath = Path()
                    linePath.moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        linePath.lineTo(points[i].x, points[i].y)
                    }
                    
                    drawPath(
                        path = linePath,
                        color = colors[goalIdx % colors.size],
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                    
                    val lastPoint = points.last()
                    drawCircle(
                        color = colors[goalIdx % colors.size],
                        radius = 4.dp.toPx(),
                        center = lastPoint
                    )
                }
                
                timelineData.forEachIndexed { index, (month, _) ->
                    val x = index * colWidth
                    val monthLabel = try {
                        val parts = month.split("-")
                        val monthIdx = parts[1].toInt() - 1
                        val monthsAbbr = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
                        "${monthsAbbr[monthIdx]}/${parts[0].substring(2)}"
                    } catch (e: Exception) { month }
                    
                    val textLayoutResult = textMeasurer.measure(monthLabel, textStyle)
                    val labelX = if (index == 0) 0f else if (index == timelineData.size - 1) x - textLayoutResult.size.width else x - (textLayoutResult.size.width / 2)
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(labelX.coerceAtLeast(0f), bottomY + 4f)
                    )
                }
            }
        }
    }
}

@Composable
fun ProjectedInvoicesChart(
    data: List<Triple<String, Double, Double>>,
    currencyFormatter: NumberFormat,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
    val textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    
    val installmentsColor = MaterialTheme.colorScheme.primary
    val recurrenceColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    
    val maxVal = remember(data) {
        val maxInList = data.maxOfOrNull { it.second + it.third } ?: 1.0
        if (maxInList <= 0) 100.0 else maxInList * 1.15
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(10.dp).background(installmentsColor, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Parcelas de Cartão", style = labelStyle)
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.size(10.dp).background(recurrenceColor, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Despesas Fixas", style = labelStyle)
        }
        
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = 8.dp)
        ) {
            val height = size.height
            val width = size.width
            val bottomY = height - 25f
            val topY = 15f
            val drawHeight = bottomY - topY
            
            val gridLinesCount = 3
            for (i in 0..gridLinesCount) {
                val y = bottomY - (drawHeight * i / gridLinesCount)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
                
                val labelValue = maxVal * i / gridLinesCount
                drawText(
                    textMeasurer = textMeasurer,
                    text = currencyFormatter.format(labelValue).replace("R$", "").trim(),
                    style = textStyle,
                    topLeft = Offset(5f, y - 12f)
                )
            }
            
            val colWidth = width / data.size
            val barW = (colWidth * 0.4f).coerceIn(16f, 48f)
            
            data.forEachIndexed { index, (month, instVal, recVal) ->
                val xCenter = (index * colWidth) + (colWidth / 2)
                
                val instH = (instVal / maxVal * drawHeight).toFloat()
                val recH = (recVal / maxVal * drawHeight).toFloat()
                
                if (instH > 0f) {
                    drawRoundRect(
                        color = installmentsColor,
                        topLeft = Offset(xCenter - (barW / 2), bottomY - instH),
                        size = androidx.compose.ui.geometry.Size(barW, instH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )
                }
                
                if (recH > 0f) {
                    drawRoundRect(
                        color = recurrenceColor,
                        topLeft = Offset(xCenter - (barW / 2), bottomY - instH - recH),
                        size = androidx.compose.ui.geometry.Size(barW, recH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )
                }
                
                val monthLabel = try {
                    val parts = month.split("-")
                    val monthIdx = parts[1].toInt() - 1
                    val monthsAbbr = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
                    "${monthsAbbr[monthIdx]}/${parts[0].substring(2)}"
                } catch (e: Exception) { month }
                
                val textLayoutResult = textMeasurer.measure(monthLabel, textStyle)
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(xCenter - (textLayoutResult.size.width / 2), bottomY + 4f)
                )
            }
        }
    }
}

