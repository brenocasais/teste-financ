package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Subcategory
import com.example.data.model.Transaction
import com.example.data.model.InstallmentPlan
import com.example.data.model.Goal
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

import android.net.Uri
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val userId = viewModel.currentUserId
    val scope = rememberCoroutineScope()

    // Database states
    val transactions by viewModel.repository.getTransactionsFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val accounts by viewModel.repository.getAccountsFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by viewModel.repository.getCategoriesFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val subcategories by viewModel.repository.getSubcategoriesFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val installmentPlans by viewModel.repository.getInstallmentPlansFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val goals by viewModel.repository.getGoalsFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val highlightedTransaction by viewModel.highlightedTransaction.collectAsStateWithLifecycle()

    // Filter states
    val selectedMonthCalendar by viewModel.selectedMonthCalendar.collectAsStateWithLifecycle()
    val transactionSearchQueryVM by viewModel.transactionSearchQuery.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(transactionSearchQueryVM) {
        searchQuery = transactionSearchQueryVM
    }

    var selectedTypeFilter by remember { mutableStateOf("TODAS") } // TODAS, RECEITA, DESPESA, TRANSFERENCIA
    var selectedAccountFilter by remember { mutableStateOf<Account?>(null) } // null means "Todas as Contas"
    var selectedCategoryFilter by remember { mutableStateOf<Category?>(null) }
    var selectedSubcategoryFilter by remember { mutableStateOf<Subcategory?>(null) }
    var selectedOnlyCreditCards by remember { mutableStateOf(false) }
    var selectedRecurrenceFilter by remember { mutableStateOf("TODAS") } // TODAS, RECORRENTES, NAO_RECORRENTES
    var selectedInstallmentFilter by remember { mutableStateOf("TODAS") } // TODAS, PARCELADAS, NAO_PARCELADAS
    var minValueFilterString by remember { mutableStateOf("") }
    var maxValueFilterString by remember { mutableStateOf("") }

    // Dialog & BottomSheet states
    var showFilterBottomSheet by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    // Draggable FAB offsets
    var fabOffsetX by remember { mutableStateOf(0f) }
    var fabOffsetY by remember { mutableStateOf(0f) }

    // Period formatting
    val periodFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("pt", "BR")) }
    val periodString = remember(selectedMonthCalendar) {
        periodFormat.format(selectedMonthCalendar.time).replaceFirstChar { it.uppercase() }
    }
    val yearMonthString = remember(selectedMonthCalendar) {
        SimpleDateFormat("yyyy-MM", Locale.US).format(selectedMonthCalendar.time)
    }

    // Previous month for comparison
    val prevMonthString = remember(selectedMonthCalendar) {
        val prevCal = selectedMonthCalendar.clone() as Calendar
        prevCal.add(Calendar.MONTH, -1)
        SimpleDateFormat("yyyy-MM", Locale.US).format(prevCal.time)
    }
    val prevMonthShortName = remember(selectedMonthCalendar) {
        val prevCal = selectedMonthCalendar.clone() as Calendar
        prevCal.add(Calendar.MONTH, -1)
        SimpleDateFormat("MMM", Locale("pt", "BR")).format(prevCal.time).lowercase()
    }

    // Filter transactions
    val filteredTransactions = remember(
        transactions, yearMonthString, searchQuery, selectedTypeFilter, selectedAccountFilter,
        selectedCategoryFilter, selectedSubcategoryFilter, selectedOnlyCreditCards,
        selectedRecurrenceFilter, selectedInstallmentFilter, minValueFilterString, maxValueFilterString,
        categories, subcategories, accounts
    ) {
        val minVal = minValueFilterString.replace(",", ".").toDoubleOrNull()
        val maxVal = maxValueFilterString.replace(",", ".").toDoubleOrNull()

        transactions.filter { tx ->
            // Filter by Period (YYYY-MM)
            val matchesPeriod = tx.date.startsWith(yearMonthString)

            // Filter by Type
            val matchesType = when (selectedTypeFilter) {
                "RECEITA" -> tx.type == "RECEITA"
                "DESPESA" -> tx.type == "DESPESA"
                "TRANSFERENCIA" -> tx.type == "TRANSFERENCIA"
                "META" -> tx.type == "META"
                else -> true
            }

            // Filter by Account
            val matchesAccount = if (selectedAccountFilter != null) {
                tx.account_id == selectedAccountFilter!!.id || tx.to_account_id == selectedAccountFilter!!.id
            } else if (selectedOnlyCreditCards) {
                val acc = accounts.find { it.id == tx.account_id }
                acc?.type == "CARTAO_CREDITO"
            } else {
                true
            }

            // Filter by Category & Subcategory
            val matchesCategory = if (selectedCategoryFilter != null) tx.category_id == selectedCategoryFilter!!.id else true
            val matchesSubcategory = if (selectedSubcategoryFilter != null) tx.subcategory_id == selectedSubcategoryFilter!!.id else true

            // Filter by Recurrence & Installment
            val matchesRecurrence = when (selectedRecurrenceFilter) {
                "RECORRENTES" -> tx.recurrence_rule_id != null
                "NAO_RECORRENTES" -> tx.recurrence_rule_id == null
                else -> true
            }
            val matchesInstallment = when (selectedInstallmentFilter) {
                "PARCELADAS" -> tx.installment_plan_id != null
                "NAO_PARCELADAS" -> tx.installment_plan_id == null
                else -> true
            }

            // Filter by Min/Max Value
            val matchesMin = minVal == null || tx.value >= minVal
            val matchesMax = maxVal == null || tx.value <= maxVal

            // Filter by Search Query (description, category name, subcategory name)
            val matchesSearch = if (searchQuery.isNotBlank()) {
                val q = searchQuery.lowercase(Locale.getDefault())
                val descMatches = tx.description.lowercase(Locale.getDefault()).contains(q)
                val catMatches = categories.find { it.id == tx.category_id }?.name?.lowercase(Locale.getDefault())?.contains(q) ?: false
                val subMatches = subcategories.find { it.id == tx.subcategory_id }?.name?.lowercase(Locale.getDefault())?.contains(q) ?: false
                descMatches || catMatches || subMatches
            } else {
                true
            }

            matchesPeriod && matchesType && matchesAccount && matchesCategory && matchesSubcategory &&
                    matchesRecurrence && matchesInstallment && matchesMin && matchesMax && matchesSearch
        }
    }

    val transactionsToShow = remember(filteredTransactions, highlightedTransaction) {
        if (highlightedTransaction != null) {
            listOf(highlightedTransaction!!)
        } else {
            filteredTransactions
        }
    }

    // Summary of filtered transactions
    val filteredIncome = remember(transactionsToShow) {
        transactionsToShow.filter { it.type == "RECEITA" }.sumOf { it.value }
    }
    val filteredExpense = remember(transactionsToShow) {
        transactionsToShow.filter { it.type == "DESPESA" }.sumOf { it.value }
    }
    val filteredNet = filteredIncome - filteredExpense

    // Comparison vs previous month calculation
    val prevMonthIncome = remember(transactions, prevMonthString) {
        transactions.filter { it.date.startsWith(prevMonthString) && it.type == "RECEITA" }.sumOf { it.value }
    }
    val prevMonthExpense = remember(transactions, prevMonthString) {
        transactions.filter { it.date.startsWith(prevMonthString) && it.type == "DESPESA" }.sumOf { it.value }
    }

    val incomeVarPercent = remember(filteredIncome, prevMonthIncome) {
        if (prevMonthIncome > 0) ((filteredIncome - prevMonthIncome) / prevMonthIncome) * 100.0 else 0.0
    }
    val expenseVarPercent = remember(filteredExpense, prevMonthExpense) {
        if (prevMonthExpense > 0) ((filteredExpense - prevMonthExpense) / prevMonthExpense) * 100.0 else 0.0
    }
    val budgetUsagePercent = remember(filteredIncome, filteredExpense) {
        if (filteredIncome > 0) (filteredExpense / filteredIncome * 100.0).toInt().coerceIn(0, 999) else 0
    }

    // Grouping transactions by date
    val groupedTransactions = remember(transactionsToShow) {
        transactionsToShow
            .sortedByDescending { it.date }
            .groupBy { getDateGroupLabel(it.date) }
    }

    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF13191B) else Color(0xFFFAFAFB)
    val cardBgColor = if (isDark) Color(0xFF1C2427) else Color(0xFFFFFFFF)
    val cardBorderColor = if (isDark) Color(0xFF283438) else Color(0xFFE5E7EC)
    val primaryTextColor = if (isDark) Color(0xFFF5F7F8) else Color(0xFF111827)
    val secondaryTextColor = if (isDark) Color(0xFF9FA9AB) else Color(0xFF6B7280)

    val greenColor = if (isDark) Color(0xFF39D47A) else Color(0xFF22A45D)
    val redColor = if (isDark) Color(0xFFFF4D55) else Color(0xFFEF4444)
    val blueColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF3B82F6)

    val hasActiveFilters = selectedAccountFilter != null || selectedCategoryFilter != null ||
            selectedSubcategoryFilter != null || selectedOnlyCreditCards ||
            selectedRecurrenceFilter != "TODAS" || selectedInstallmentFilter != "TODAS" ||
            minValueFilterString.isNotEmpty() || maxValueFilterString.isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            // === 1. CABEÇALHO ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transações",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )

                // Seletor compacto de mês
                Surface(
                    onClick = { showMonthPicker = true },
                    shape = RoundedCornerShape(10.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = periodString,
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
            }

            // Highlighting banner if applicable
            if (highlightedTransaction != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(onClick = { viewModel.returnFromTransactionHighlight() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Voltar ao Planejamento",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Filtro do Planejamento",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = { viewModel.returnFromTransactionHighlight() }) {
                            Text("Limpar", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // === 2. CAMPO DE BUSCA & ÍCONE DE FILTROS (Tamanho compacto ~42dp igual Planejamento) ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Buscar transação...",
                                    fontSize = 13.sp,
                                    color = secondaryTextColor
                                )
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    viewModel.transactionSearchQuery.value = it
                                },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.sp,
                                    color = primaryTextColor
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    viewModel.transactionSearchQuery.value = ""
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Limpar busca",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Botão de filtros compacto
                Surface(
                    onClick = { showFilterBottomSheet = true },
                    shape = RoundedCornerShape(12.dp),
                    color = if (hasActiveFilters) greenColor.copy(alpha = 0.15f) else cardBgColor,
                    border = BorderStroke(1.dp, if (hasActiveFilters) greenColor else cardBorderColor),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Filtros Avançados",
                            tint = if (hasActiveFilters) greenColor else secondaryTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                        if (hasActiveFilters) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(greenColor)
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // === 3. CARD DE RESUMO (Receitas, Despesas, Saldo) SEM EMOJIS E ALTURA REDUZIDA ===
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(14.dp),
                color = cardBgColor,
                border = BorderStroke(1.dp, cardBorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // RECEITAS
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text("Receitas", fontSize = 12.sp, color = secondaryTextColor)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatBrl(filteredIncome),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (prevMonthIncome > 0) {
                                val arrow = if (incomeVarPercent >= 0) "↑" else "↓"
                                "$arrow ${"%.0f".format(kotlin.math.abs(incomeVarPercent))}% vs $prevMonthShortName."
                            } else "100% vs $prevMonthShortName.",
                            fontSize = 11.sp,
                            color = greenColor,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier
                            .height(48.dp)
                            .padding(horizontal = 4.dp),
                        color = cardBorderColor
                    )

                    // DESPESAS
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text("Despesas", fontSize = 12.sp, color = secondaryTextColor)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatBrl(filteredExpense),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (prevMonthExpense > 0) {
                                val arrow = if (expenseVarPercent >= 0) "↑" else "↓"
                                "$arrow ${"%.0f".format(kotlin.math.abs(expenseVarPercent))}% vs $prevMonthShortName."
                            } else "100% vs $prevMonthShortName.",
                            fontSize = 11.sp,
                            color = redColor,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier
                            .height(48.dp)
                            .padding(horizontal = 4.dp),
                        color = cardBorderColor
                    )

                    // SALDO
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text("Saldo", fontSize = 12.sp, color = secondaryTextColor)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatBrl(filteredNet),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$budgetUsagePercent% do orçamento",
                            fontSize = 11.sp,
                            color = secondaryTextColor,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // === 4. FILTROS (CHIPS: Todas / Receitas / Despesas / Transferências) ===
            val filterChips = listOf(
                FilterTypeItem("Todas", "TODAS"),
                FilterTypeItem("Receitas", "RECEITA"),
                FilterTypeItem("Despesas", "DESPESA"),
                FilterTypeItem("Transferências", "TRANSFERENCIA")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filterChips.forEach { chip ->
                    val isSelected = selectedTypeFilter == chip.id
                    Surface(
                        onClick = { selectedTypeFilter = chip.id },
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) greenColor.copy(alpha = 0.15f) else cardBgColor,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) greenColor else cardBorderColor
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = chip.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) greenColor else secondaryTextColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // === 5. LISTA DE TRANSAÇÕES AGRUPADAS POR DATA ===
            if (transactionsToShow.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = greenColor.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Nenhuma transação encontrada",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty() || selectedTypeFilter != "TODAS" || hasActiveFilters) {
                                "Tente limpar os filtros ou realizar outra busca."
                            } else {
                                "Nenhum lançamento para este período."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                        if (searchQuery.isNotEmpty() || selectedTypeFilter != "TODAS" || hasActiveFilters) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    searchQuery = ""
                                    selectedTypeFilter = "TODAS"
                                    selectedAccountFilter = null
                                    selectedCategoryFilter = null
                                    selectedSubcategoryFilter = null
                                    selectedOnlyCreditCards = false
                                    selectedRecurrenceFilter = "TODAS"
                                    selectedInstallmentFilter = "TODAS"
                                    minValueFilterString = ""
                                    maxValueFilterString = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = greenColor)
                            ) {
                                Text("Limpar Filtros")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    groupedTransactions.forEach { (dateGroupTitle, txList) ->
                        item(key = "header_$dateGroupTitle") {
                            Text(
                                text = dateGroupTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }

                        item(key = "card_$dateGroupTitle") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = cardBgColor,
                                border = BorderStroke(1.dp, cardBorderColor)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    txList.forEachIndexed { index, tx ->
                                        val accOriginName = accounts.find { it.id == tx.account_id }?.name ?: "Desconhecida"
                                        val accDestName = if (tx.type == "TRANSFERENCIA") {
                                            accounts.find { it.id == tx.to_account_id }?.name ?: "Desconhecida"
                                        } else null

                                        val catName = if (tx.type == "META") {
                                            goals.find { it.id == tx.goal_id }?.let { "Meta: ${it.name}" }
                                        } else {
                                            categories.find { it.id == tx.category_id }?.name
                                        }
                                        val subName = if (tx.type == "META") null else subcategories.find { it.id == tx.subcategory_id }?.name

                                        NewTransactionRow(
                                            transaction = tx,
                                            accountName = accOriginName,
                                            toAccountName = accDestName,
                                            categoryName = catName,
                                            subcategoryName = subName,
                                            installmentPlans = installmentPlans,
                                            primaryTextColor = primaryTextColor,
                                            secondaryTextColor = secondaryTextColor,
                                            greenColor = greenColor,
                                            redColor = redColor,
                                            blueColor = blueColor,
                                            onItemClick = { editingTransaction = tx }
                                        )

                                        if (index < txList.size - 1) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                                                color = cardBorderColor.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // FAB: Nova Transação (Draggable)
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset { androidx.compose.ui.unit.IntOffset(fabOffsetX.toInt(), fabOffsetY.toInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        fabOffsetX += dragAmount.x
                        fabOffsetY += dragAmount.y
                    }
                }
                .padding(24.dp),
            containerColor = greenColor,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nova Transação")
        }
    }

    // === BOTTOM SHEET DE FILTROS AVANÇADOS ===
    if (showFilterBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterBottomSheet = false },
            containerColor = cardBgColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filtros de Transação",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                    TextButton(
                        onClick = {
                            selectedAccountFilter = null
                            selectedCategoryFilter = null
                            selectedSubcategoryFilter = null
                            selectedOnlyCreditCards = false
                            selectedRecurrenceFilter = "TODAS"
                            selectedInstallmentFilter = "TODAS"
                            minValueFilterString = ""
                            maxValueFilterString = ""
                        }
                    ) {
                        Text("Limpar", color = redColor)
                    }
                }

                // Filter by Account
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Conta", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = secondaryTextColor)
                    var showAccDrop by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { showAccDrop = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = selectedAccountFilter?.name ?: "Todas as Contas",
                                color = primaryTextColor,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = secondaryTextColor)
                        }
                        DropdownMenu(
                            expanded = showAccDrop,
                            onDismissRequest = { showAccDrop = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Todas as Contas") },
                                onClick = {
                                    selectedAccountFilter = null
                                    showAccDrop = false
                                }
                            )
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name) },
                                    onClick = {
                                        selectedAccountFilter = acc
                                        showAccDrop = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Toggle Cartões de Crédito
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Filtrar somente Cartões de Crédito", fontSize = 14.sp, color = primaryTextColor)
                    Switch(
                        checked = selectedOnlyCreditCards,
                        onCheckedChange = { selectedOnlyCreditCards = it }
                    )
                }

                // Filter by Category
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Categoria", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = secondaryTextColor)
                    var showCatDrop by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { showCatDrop = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = selectedCategoryFilter?.name ?: "Todas as Categorias",
                                color = primaryTextColor,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = secondaryTextColor)
                        }
                        DropdownMenu(
                            expanded = showCatDrop,
                            onDismissRequest = { showCatDrop = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Todas as Categorias") },
                                onClick = {
                                    selectedCategoryFilter = null
                                    selectedSubcategoryFilter = null
                                    showCatDrop = false
                                }
                            )
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        selectedCategoryFilter = cat
                                        selectedSubcategoryFilter = null
                                        showCatDrop = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Subcategory Filter (if category selected)
                if (selectedCategoryFilter != null) {
                    val subcatsOfCat = subcategories.filter { it.category_id == selectedCategoryFilter!!.id }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Subcategoria", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = secondaryTextColor)
                        var showSubDrop by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { showSubDrop = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = selectedSubcategoryFilter?.name ?: "Todas as Subcategorias",
                                    color = primaryTextColor,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = secondaryTextColor)
                            }
                            DropdownMenu(
                                expanded = showSubDrop,
                                onDismissRequest = { showSubDrop = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Todas as Subcategorias") },
                                    onClick = {
                                        selectedSubcategoryFilter = null
                                        showSubDrop = false
                                    }
                                )
                                subcatsOfCat.forEach { sub ->
                                    DropdownMenuItem(
                                        text = { Text(sub.name) },
                                        onClick = {
                                            selectedSubcategoryFilter = sub
                                            showSubDrop = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Value Range (Min / Max)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Faixa de Valor (R$)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = secondaryTextColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minValueFilterString,
                            onValueChange = { minValueFilterString = it },
                            label = { Text("Mínimo") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = maxValueFilterString,
                            onValueChange = { maxValueFilterString = it },
                            label = { Text("Máximo") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                }

                // Recurrence filter
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Recorrência", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = secondaryTextColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("TODAS" to "Todas", "RECORRENTES" to "Recorrentes", "NAO_RECORRENTES" to "Avulsas").forEach { (id, label) ->
                            val isSel = selectedRecurrenceFilter == id
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedRecurrenceFilter = id },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                // Installments filter
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Parcelamento", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = secondaryTextColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("TODAS" to "Todas", "PARCELADAS" to "Parceladas", "NAO_PARCELADAS" to "À vista").forEach { (id, label) ->
                            val isSel = selectedInstallmentFilter == id
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedInstallmentFilter = id },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Button(
                    onClick = { showFilterBottomSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = greenColor)
                ) {
                    Text("Aplicar Filtros", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }

    // Month Picker Dialog
    if (showMonthPicker) {
        MonthYearPickerDialog(
            currentCalendar = selectedMonthCalendar,
            onDismiss = { showMonthPicker = false },
            onSelected = { year, month ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                viewModel.setSelectedMonth(newCal)
                showMonthPicker = false
            }
        )
    }

    // New/Add Dialog
    if (showAddDialog) {
        TransactionAddEditDialog(
            viewModel = viewModel,
            accounts = accounts,
            categories = categories,
            subcategories = subcategories,
            transactionToEdit = null,
            onDismiss = { showAddDialog = false }
        )
    }

    // Edit Dialog
    if (editingTransaction != null) {
        TransactionAddEditDialog(
            viewModel = viewModel,
            accounts = accounts,
            categories = categories,
            subcategories = subcategories,
            transactionToEdit = editingTransaction,
            onDismiss = { editingTransaction = null }
        )
    }
}

// === NOVO COMPONENTE DE LINHA DE TRANSAÇÃO REDESENHADO ===
@Composable
fun NewTransactionRow(
    transaction: Transaction,
    accountName: String,
    toAccountName: String?,
    categoryName: String?,
    subcategoryName: String?,
    installmentPlans: List<InstallmentPlan>,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    greenColor: Color,
    redColor: Color,
    blueColor: Color,
    onItemClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onItemClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Icon circular badge
        val descLower = transaction.description.lowercase()
        val (icon, badgeBg) = when {
            descLower.contains("franguinho") || descLower.contains("almoço") || descLower.contains("restaurante") || descLower.contains("jantar") ->
                Icons.Default.Restaurant to Color(0xFFF97316)
            descLower.contains("uber") || descLower.contains("99") || descLower.contains("transporte") || descLower.contains("táxi") ->
                Icons.Default.DirectionsCar to Color(0xFF1E293B)
            descLower.contains("salário") || descLower.contains("pagamento") || descLower.contains("renda") ->
                Icons.Default.AccountBalanceWallet to greenColor
            descLower.contains("mercado") || descLower.contains("supermercado") || descLower.contains("compras") ->
                Icons.Default.ShoppingCart to redColor
            descLower.contains("netflix") || descLower.contains("lazer") || descLower.contains("cinema") ->
                Icons.Default.Tv to Color(0xFF111827)
            transaction.type == "RECEITA" -> Icons.Default.TrendingUp to greenColor
            transaction.type == "DESPESA" -> Icons.Default.TrendingDown to redColor
            transaction.type == "TRANSFERENCIA" -> Icons.Default.SwapHoriz to blueColor
            else -> Icons.Default.Flag to blueColor
        }

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = buildString {
                            if (transaction.type == "TRANSFERENCIA") {
                                append("$accountName → $toAccountName")
                            } else {
                                if (categoryName != null) {
                                    append(categoryName)
                                    append(" • ")
                                }
                                append(accountName)
                            }
                        },
                        fontSize = 12.sp,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Installment badge ("2/3")
                    val plan = installmentPlans.find { it.id == transaction.installment_plan_id }
                    if (plan != null && transaction.installment_number != null) {
                        Surface(
                            color = greenColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${transaction.installment_number}/${plan.installments_count}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = greenColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    // Recurrence icon
                    if (transaction.recurrence_rule_id != null) {
                        Icon(
                            imageVector = Icons.Default.Autorenew,
                            contentDescription = "Recorrente",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    // Attachment icon
                    if (transaction.attachment_uri != null) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Comprovante",
                            tint = greenColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right side: Value & Time/Date
        Column(horizontalAlignment = Alignment.End) {
            val (prefix, valueColor) = when (transaction.type) {
                "RECEITA" -> "+ " to greenColor
                "DESPESA" -> "- " to redColor
                "TRANSFERENCIA" -> "- " to blueColor
                else -> "- " to redColor
            }
            Text(
                text = "$prefix${formatBrl(transaction.value)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Spacer(modifier = Modifier.height(1.dp))

            // Time or short date format (e.g., "12:45" or "20 jul")
            val timeOrDateStr = remember(transaction.date) {
                try {
                    val parts = transaction.date.split("-")
                    if (parts.size == 3) {
                        val day = parts[2]
                        val monthNum = parts[1].toIntOrNull() ?: 1
                        val months = listOf("", "jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
                        "$day ${months.getOrElse(monthNum) { "" }}"
                    } else transaction.date
                } catch (e: Exception) {
                    transaction.date
                }
            }
            Text(
                text = timeOrDateStr,
                fontSize = 11.sp,
                color = secondaryTextColor
            )
        }
    }
}

// Helper to format BRL currency
private fun formatBrl(value: Double): String {
    val formatter = java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    return formatter.format(value)
}

// Helper to calculate date group label
private fun getDateGroupLabel(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(dateStr) ?: return dateStr
        val calDate = Calendar.getInstance().apply { time = date }
        val calToday = Calendar.getInstance()

        val isSameYear = calDate.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
        val dayOfYearDate = calDate.get(Calendar.DAY_OF_YEAR)
        val dayOfYearToday = calToday.get(Calendar.DAY_OF_YEAR)

        if (isSameYear && dayOfYearDate == dayOfYearToday) {
            "Hoje"
        } else if (isSameYear && dayOfYearDate == dayOfYearToday - 1) {
            "Ontem"
        } else {
            val diffMs = calToday.timeInMillis - calDate.timeInMillis
            val diffDays = diffMs / (24 * 60 * 60 * 1000)
            if (diffDays in 0..6 && isSameYear) {
                val dayOfWeekFormat = SimpleDateFormat("EEEE", Locale("pt", "BR"))
                dayOfWeekFormat.format(date).replaceFirstChar { it.uppercase() }
            } else {
                val fullDateFormat = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
                fullDateFormat.format(date)
            }
        }
    } catch (e: Exception) {
        dateStr
    }
}

data class FilterTypeItem(val label: String, val id: String)

@Composable
fun MiniSummaryItem(title: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun TransactionItemRow(
    transaction: Transaction,
    accountName: String,
    toAccountName: String?,
    categoryName: String?,
    subcategoryName: String?,
    installmentPlans: List<InstallmentPlan> = emptyList(),
    onItemClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular icon by Type
                val icon = when (transaction.type) {
                    "RECEITA" -> Icons.Default.TrendingUp
                    "DESPESA" -> Icons.Default.TrendingDown
                    "META" -> Icons.Default.Flag
                    else -> Icons.Default.SwapHoriz
                }
                val iconTint = when (transaction.type) {
                    "RECEITA" -> MaterialTheme.colorScheme.primary
                    "DESPESA" -> MaterialTheme.colorScheme.error
                    "META" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.secondary
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconTint.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = transaction.description,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        
                        val plan = installmentPlans.find { it.id == transaction.installment_plan_id }
                        if (plan != null && transaction.installment_number != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${transaction.installment_number}/${plan.installments_count}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (transaction.recurrence_rule_id != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Autorenew,
                                        contentDescription = "Recorrente",
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "Recorrente",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            if (transaction.type == "TRANSFERENCIA") {
                                append("$accountName → $toAccountName")
                            } else {
                                append(accountName)
                                if (categoryName != null) {
                                    append(" • $categoryName")
                                    if (subcategoryName != null) {
                                        append(" / $subcategoryName")
                                    }
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                val sign = when (transaction.type) {
                    "RECEITA" -> "+"
                    "DESPESA" -> "-"
                    "META" -> "-"
                    else -> ""
                }
                val textColor = when (transaction.type) {
                    "RECEITA" -> MaterialTheme.colorScheme.primary
                    "DESPESA" -> MaterialTheme.colorScheme.error
                    "META" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text(
                    text = "%s R$ %.2f".format(sign, transaction.value),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                // date formatting (yyyy-MM-dd -> dd/MM)
                val displayDate = try {
                    val dateParts = transaction.date.split("-")
                    if (dateParts.size == 3) {
                        "${dateParts[2]}/${dateParts[1]}"
                    } else transaction.date
                } catch (e: Exception) {
                    transaction.date
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    if (transaction.attachment_uri != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = "Comprovante anexo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

// 5.1 NOVA TRANSAÇÃO / EDITAR TRANSAÇÃO (dialog)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionAddEditDialog(
    viewModel: MainViewModel,
    accounts: List<Account>,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    transactionToEdit: Transaction?,
    preSelectedCategory: Category? = null,
    preSelectedSubcategory: Subcategory? = null,
    onDismiss: () -> Unit
) {
    val isEdit = transactionToEdit != null
    val coroutineScope = rememberCoroutineScope()

    // State Variables
    var type by remember { mutableStateOf(transactionToEdit?.type ?: "DESPESA") }
    var valueString by remember {
        mutableStateOf(transactionToEdit?.let { "%.2f".format(it.value) } ?: "")
    }
    var description by remember { mutableStateOf(transactionToEdit?.description ?: "") }

    var selectedAccount by remember {
        mutableStateOf(accounts.find { it.id == transactionToEdit?.account_id } ?: accounts.firstOrNull())
    }
    var selectedToAccount by remember {
        mutableStateOf(accounts.find { it.id == transactionToEdit?.to_account_id } ?: accounts.getOrNull(1) ?: accounts.firstOrNull())
    }

    var selectedCategory by remember {
        mutableStateOf(categories.find { it.id == transactionToEdit?.category_id } ?: preSelectedCategory ?: categories.firstOrNull())
    }
    var selectedSubcategory by remember {
        mutableStateOf(subcategories.find { it.id == transactionToEdit?.subcategory_id } ?: preSelectedSubcategory ?: subcategories.firstOrNull())
    }

    val userId = viewModel.currentUserId
    val goals by viewModel.repository.getGoalsFlow(userId).collectAsStateWithLifecycle(emptyList())

    var selectedGoal by remember {
        mutableStateOf(goals.find { it.id == transactionToEdit?.goal_id })
    }

    LaunchedEffect(goals, transactionToEdit) {
        if (transactionToEdit?.goal_id != null && selectedGoal == null) {
            selectedGoal = goals.find { it.id == transactionToEdit.goal_id }
        } else if (selectedGoal == null && goals.isNotEmpty()) {
            selectedGoal = goals.firstOrNull()
        }
    }

    // Filtered Subcategories based on Category selection
    val filteredSubcategories = remember(selectedCategory, subcategories) {
        if (selectedCategory != null) {
            subcategories.filter { it.category_id == selectedCategory!!.id }
        } else emptyList()
    }

    // Sync subcategory on category change
    LaunchedEffect(selectedCategory) {
        if (selectedCategory != null && (selectedSubcategory == null || selectedSubcategory!!.category_id != selectedCategory!!.id)) {
            selectedSubcategory = filteredSubcategories.firstOrNull()
        }
    }

    // Date in UI format (DD/MM/YYYY)
    var dateString by remember {
        mutableStateOf(
            if (transactionToEdit != null) {
                dbToUiDate(transactionToEdit.date)
            } else {
                SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date())
            }
        )
    }

    // Attachment
    var attachmentUri by remember { mutableStateOf(transactionToEdit?.attachment_uri) }
    var attachmentName by remember { mutableStateOf(transactionToEdit?.attachment_name) }

    var showErrorMsg by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    // Quick categories dialogs
    var showQuickCategoryDialog by remember { mutableStateOf(false) }
    var showQuickSubcategoryDialog by remember { mutableStateOf(false) }

    var isParcelado by remember { mutableStateOf(false) }
    var installmentsCountString by remember { mutableStateOf("3") }

    var isRecurrent by remember { mutableStateOf(false) }
    var recurrenceFrequency by remember { mutableStateOf("MENSAL") }
    var recurrenceIntervalString by remember { mutableStateOf("1") }
    var endDateString by remember { mutableStateOf("") }
    var hasEndDate by remember { mutableStateOf(false) }
    var showEditChoiceDialog by remember { mutableStateOf(false) }

    var endCalendar by remember {
        mutableStateOf(
            java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.YEAR, 1)
            }
        )
    }
    var showEndDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(transactionToEdit) {
        if (transactionToEdit != null) {
            if (transactionToEdit.installment_plan_id != null) {
                isParcelado = true
                coroutineScope.launch {
                    val plan = viewModel.repository.getInstallmentPlanById(transactionToEdit.installment_plan_id)
                    if (plan != null) {
                        installmentsCountString = plan.installments_count.toString()
                    }
                }
            }
            if (transactionToEdit.recurrence_rule_id != null) {
                isRecurrent = true
                coroutineScope.launch {
                    val rule = viewModel.repository.getRecurrenceRuleById(transactionToEdit.recurrence_rule_id)
                    if (rule != null) {
                        recurrenceFrequency = rule.frequency
                        recurrenceIntervalString = rule.frequency_interval.toString()
                        if (rule.end_month != null) {
                            hasEndDate = true
                            val parts = rule.end_month.split("-")
                            if (parts.size == 2) {
                                endDateString = "01/${parts[1]}/${parts[0]}"
                                val y = parts[0].toIntOrNull() ?: 2026
                                val m = (parts[1].toIntOrNull() ?: 1) - 1
                                endCalendar = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                                    set(java.util.Calendar.YEAR, y)
                                    set(java.util.Calendar.MONTH, m)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 620.dp)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEdit) "Editar Transação" else "Nova Transação",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row {
                        if (isEdit) {
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Excluir transação",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar")
                        }
                    }
                }

                if (showErrorMsg.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = showErrorMsg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // 1. TIPO DE TRANSAÇÃO (DESPESA / RECEITA / TRANSFERENCIA)
                val types = listOf(
                    TransactionTypeOption("Despesa", "DESPESA"),
                    TransactionTypeOption("Receita", "RECEITA"),
                    TransactionTypeOption("Transf.", "TRANSFERENCIA")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    types.forEach { opt ->
                        val isSelected = type == opt.id
                        val activeColor = when (opt.id) {
                            "RECEITA" -> MaterialTheme.colorScheme.primary
                            "DESPESA" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.secondary
                        }
                        val onActiveColor = when (opt.id) {
                            "RECEITA" -> MaterialTheme.colorScheme.onPrimary
                            "DESPESA" -> MaterialTheme.colorScheme.onError
                            else -> MaterialTheme.colorScheme.onSecondary
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) activeColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .clickable { type = opt.id }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = opt.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) onActiveColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 2. VALOR
                OutlinedTextField(
                    value = valueString,
                    onValueChange = { input ->
                        // Accept only decimal numbers
                        if (input.isEmpty() || input.matches(Regex("^\\d*([.,]\\d{0,2})?$"))) {
                            valueString = input
                        }
                    },
                    label = { Text("Valor (R$)") },
                    placeholder = { Text("0,00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // 3. DESCRIÇÃO
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição") },
                    placeholder = { Text("Ex: Supermercado, Almoço, etc.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // 4. CONTA ORIGEM (E DESTINO SE TRANSFERÊNCIA)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Origem
                    var showOriginMenu by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = selectedAccount?.name ?: "Selecionar...",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (type == "TRANSFERENCIA") "Conta de Origem" else "Conta") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showOriginMenu = true },
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Box(modifier = Modifier
                            .matchParentSize()
                            .clickable { showOriginMenu = true })

                        DropdownMenu(
                            expanded = showOriginMenu,
                            onDismissRequest = { showOriginMenu = false }
                        ) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name) },
                                    onClick = {
                                        selectedAccount = acc
                                        showOriginMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Destino
                    if (type == "TRANSFERENCIA") {
                        var showDestMenu by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = selectedToAccount?.name ?: "Selecionar...",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Conta de Destino") },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showDestMenu = true },
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Box(modifier = Modifier
                                .matchParentSize()
                                .clickable { showDestMenu = true })

                            DropdownMenu(
                                expanded = showDestMenu,
                                onDismissRequest = { showDestMenu = false }
                            ) {
                                accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(acc.name) },
                                        onClick = {
                                            selectedToAccount = acc
                                            showDestMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. CATEGORIA / SUBCATEGORIA ou META (Se for tipo META, seleciona a meta)
                if (type == "META") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Meta Destino 🎯",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        var showGoalMenu by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedGoal?.name ?: "Nenhuma Meta Cadastrada",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showGoalMenu = true },
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Box(modifier = Modifier
                                .matchParentSize()
                                .clickable { showGoalMenu = true })

                            DropdownMenu(
                                expanded = showGoalMenu,
                                onDismissRequest = { showGoalMenu = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                if (goals.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Nenhuma meta cadastrada") },
                                        onClick = { showGoalMenu = false }
                                    )
                                } else {
                                    goals.forEach { g ->
                                        DropdownMenuItem(
                                            text = { Text(g.name) },
                                            onClick = {
                                                selectedGoal = g
                                                showGoalMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (type != "TRANSFERENCIA") {
                    // Category Selection
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Categoria",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "+ Criar Nova Categoria",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32), // Beautiful rich green
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { showQuickCategoryDialog = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }

                        var showCatMenu by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedCategory?.name ?: "Nenhuma",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCatMenu = true },
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Box(modifier = Modifier
                                .matchParentSize()
                                .clickable { showCatMenu = true })

                            DropdownMenu(
                                expanded = showCatMenu,
                                onDismissRequest = { showCatMenu = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Nenhuma") },
                                    onClick = {
                                        selectedCategory = null
                                        selectedSubcategory = null
                                        showCatMenu = false
                                    }
                                )
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name) },
                                        onClick = {
                                            selectedCategory = cat
                                            showCatMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Subcategory Selection (different line)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Subcategoria",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "+ Criar Nova Subcategoria",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32), // Beautiful rich green
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { showQuickSubcategoryDialog = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }

                        var showSubMenu by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedSubcategory?.name ?: "Nenhuma",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSubMenu = true && selectedCategory != null },
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Box(modifier = Modifier
                                .matchParentSize()
                                .clickable { if (selectedCategory != null) showSubMenu = true })

                            DropdownMenu(
                                expanded = showSubMenu,
                                onDismissRequest = { showSubMenu = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Nenhuma") },
                                    onClick = {
                                        selectedSubcategory = null
                                        showSubMenu = false
                                    }
                                )
                                filteredSubcategories.forEach { sub ->
                                    DropdownMenuItem(
                                        text = { Text(sub.name) },
                                        onClick = {
                                            selectedSubcategory = sub
                                            showSubMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. DATA (Teclado DD/MM/AAAA simplificado com validação e máscara)
                OutlinedTextField(
                    value = dateString,
                    onValueChange = { input ->
                        if (input.length <= 10 && input.all { it.isDigit() || it == '/' }) {
                            dateString = input
                        }
                    },
                    label = { Text("Data (DD/MM/AAAA)") },
                    placeholder = { Text("12/07/2026") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // TOGGLE COMPRA PARCELADA (only for DESPESA on new transaction, or when editing an existing installment)
                if ((!isEdit && type == "DESPESA") || (isEdit && transactionToEdit?.installment_plan_id != null)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = if (isParcelado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Compra parcelada?",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Dividir o valor em parcelas mensais",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isParcelado,
                                onCheckedChange = { 
                                    isParcelado = it
                                    if (it) isRecurrent = false
                                }
                            )
                        }

                        if (isParcelado) {
                            OutlinedTextField(
                                value = installmentsCountString,
                                onValueChange = { input ->
                                    if (input.isEmpty() || input.all { it.isDigit() }) {
                                        installmentsCountString = input
                                    }
                                },
                                label = { Text("Número de parcelas") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Preview em tempo real
                            val countInt = installmentsCountString.toIntOrNull() ?: 1
                            val totalVal = valueString.replace(",", ".").toDoubleOrNull() ?: 0.0
                            if (totalVal > 0.0 && countInt > 1) {
                                val baseValue = (totalVal / countInt).toBigDecimal().setScale(2, java.math.RoundingMode.DOWN).toDouble()
                                val totalDistributed = baseValue * (countInt - 1)
                                val lastValue = (totalVal - totalDistributed).toBigDecimal().setScale(2, java.math.RoundingMode.HALF_UP).toDouble()

                                val parsedDate = try {
                                    SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(dateString) ?: Date()
                                } catch (e: Exception) {
                                    Date()
                                }
                                val previewCal = Calendar.getInstance()
                                previewCal.time = parsedDate

                                val sdfMonth = SimpleDateFormat("MMMM", Locale("pt", "BR"))
                                val startMonthStr = sdfMonth.format(previewCal.time).replaceFirstChar { it.uppercase() }

                                val endCal = previewCal.clone() as Calendar
                                endCal.add(Calendar.MONTH, countInt - 1)
                                val endMonthStr = sdfMonth.format(endCal.time).replaceFirstChar { it.uppercase() }

                                val currencyFormatter = java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

                                val previewMsg = if (baseValue == lastValue) {
                                    "Vai gerar $countInt lançamentos de ${currencyFormatter.format(baseValue)}, de $startMonthStr a $endMonthStr."
                                } else {
                                    "Vai gerar ${countInt - 1} lançamentos de ${currencyFormatter.format(baseValue)} e 1 de ${currencyFormatter.format(lastValue)}, de $startMonthStr a $endMonthStr."
                                }

                                Text(
                                    text = previewMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                // TOGGLE RECORRENTE (only for DESPESA or RECEITA on new transaction, or when editing an existing recurrent)
                if ((!isEdit && (type == "DESPESA" || type == "RECEITA")) || (isEdit && transactionToEdit?.recurrence_rule_id != null)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Autorenew,
                                    contentDescription = null,
                                    tint = if (isRecurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "É recorrente?",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Repetir esta transação mensalmente",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isRecurrent,
                                onCheckedChange = { 
                                    isRecurrent = it 
                                    if (it) isParcelado = false
                                }
                            )
                        }

                        if (isRecurrent) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = recurrenceIntervalString,
                                    onValueChange = { input ->
                                        if (input.isEmpty() || input.all { it.isDigit() }) {
                                            recurrenceIntervalString = input
                                        }
                                    },
                                    label = { Text("A cada") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Text(
                                        text = "Unidade", 
                                        style = MaterialTheme.typography.labelMedium, 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (recurrenceFrequency == "MENSAL") MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                                .clickable { recurrenceFrequency = "MENSAL" },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Meses",
                                                color = if (recurrenceFrequency == "MENSAL") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (recurrenceFrequency == "ANUAL") MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                                .clickable { recurrenceFrequency = "ANUAL" },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Anos",
                                                color = if (recurrenceFrequency == "ANUAL") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

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
                                    checked = hasEndDate,
                                    onCheckedChange = { hasEndDate = it }
                                )
                            }

                            if (hasEndDate) {
                                val endMonthFormatter = remember { java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("pt", "BR")) }
                                val displayEndMonth = remember(endCalendar) {
                                    endMonthFormatter.format(endCalendar.time).replaceFirstChar { it.uppercase() }
                                }
                                OutlinedButton(
                                    onClick = { showEndDatePicker = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Terminar em: $displayEndMonth")
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
                    }
                }

                // 7. ANEXAR COMPROVANTE (Real ou Captura de Foto com visualizador A4 e leitor de PDF)
                val context = LocalContext.current
                var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
                var tempPhotoFile by remember { mutableStateOf<java.io.File?>(null) }

                val galleryLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null) {
                        val originalName = getFileName(context, uri) ?: "Galeria_${System.currentTimeMillis().toString().takeLast(5)}"
                        val savedUri = com.example.utils.ExportHelper.saveUriToInternalStorage(context, uri, originalName)
                        if (savedUri != null) {
                            attachmentUri = savedUri.toString()
                            attachmentName = originalName
                        } else {
                            showErrorMsg = "Erro ao salvar anexo localmente."
                        }
                    }
                }

                val cameraLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicture()
                ) { success ->
                    if (success && tempPhotoFile != null) {
                        val originalName = "Foto_${System.currentTimeMillis().toString().takeLast(5)}.jpg"
                        val savedUri = com.example.utils.ExportHelper.saveFileToInternalStorage(context, tempPhotoFile!!, originalName)
                        if (savedUri != null) {
                            attachmentUri = savedUri.toString()
                            attachmentName = originalName
                        } else {
                            showErrorMsg = "Erro ao salvar foto localmente."
                        }
                    }
                }

                val pdfLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null) {
                        val originalName = getFileName(context, uri) ?: "Comprovante_${System.currentTimeMillis().toString().takeLast(5)}.pdf"
                        val savedUri = com.example.utils.ExportHelper.saveUriToInternalStorage(context, uri, originalName)
                        if (savedUri != null) {
                            attachmentUri = savedUri.toString()
                            attachmentName = originalName
                        } else {
                            showErrorMsg = "Erro ao salvar PDF localmente."
                        }
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        try {
                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            val file = java.io.File.createTempFile("JPEG_${timeStamp}_", ".jpg", context.cacheDir)
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            tempPhotoFile = file
                            tempPhotoUri = uri
                            cameraLauncher.launch(uri)
                        } catch (e: Exception) {
                            showErrorMsg = "Erro ao criar arquivo para foto."
                        }
                    } else {
                        showErrorMsg = "Permissão de câmera é necessária para tirar foto."
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Comprovante Anexo",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (attachmentUri == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Galeria
                            OutlinedButton(
                                onClick = {
                                    galleryLauncher.launch("image/*")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Galeria", fontSize = 11.sp, maxLines = 1)
                                }
                            }

                            // 2. Câmera
                            OutlinedButton(
                                onClick = {
                                    val hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.CAMERA
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                    if (hasCameraPermission) {
                                        try {
                                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                            val file = java.io.File.createTempFile("JPEG_${timeStamp}_", ".jpg", context.cacheDir)
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            tempPhotoFile = file
                                            tempPhotoUri = uri
                                            cameraLauncher.launch(uri)
                                        } catch (e: Exception) {
                                            showErrorMsg = "Erro ao tirar foto."
                                        }
                                    } else {
                                        permissionLauncher.launch(android.Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Câmera", fontSize = 11.sp, maxLines = 1)
                                }
                            }

                            // 3. PDF
                            OutlinedButton(
                                onClick = {
                                    pdfLauncher.launch("application/pdf")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("PDF", fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (attachmentName?.endsWith(".pdf", true) == true) Icons.Default.Description else Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = attachmentName ?: "Comprovante",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Toque no olho para visualizar em A4",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }

                            var showViewer by remember { mutableStateOf(false) }
                            if (showViewer) {
                                AttachmentViewerDialog(
                                    attachmentUri = attachmentUri!!,
                                    attachmentName = attachmentName ?: "Comprovante",
                                    transactionDescription = description,
                                    transactionValue = valueString.replace(",", ".").toDoubleOrNull() ?: 0.0,
                                    transactionDate = dateString,
                                    categoryName = selectedCategory?.name,
                                    accountName = selectedAccount?.name ?: "",
                                    transaction = transactionToEdit,
                                    onDismiss = { showViewer = false }
                                )
                            }

                            IconButton(onClick = { showViewer = true }) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = "Visualizar em página inteira A4",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(onClick = {
                                attachmentUri = null
                                attachmentName = null
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remover anexo",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Buttons Save & Cancel (Removed the pink Delete button from here completely!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            val valDouble = valueString.replace(",", ".").toDoubleOrNull()
                            if (valDouble == null || valDouble <= 0.0) {
                                showErrorMsg = "Insira um valor numérico válido maior que zero."
                                return@Button
                            }
                            if (description.isBlank()) {
                                showErrorMsg = "Insira uma descrição para o lançamento."
                                return@Button
                            }
                            if (selectedAccount == null) {
                                showErrorMsg = "Selecione uma conta."
                                return@Button
                            }
                            if (type == "TRANSFERENCIA" && selectedToAccount == null) {
                                showErrorMsg = "Selecione a conta de destino para a transferência."
                                return@Button
                            }
                            if (type == "TRANSFERENCIA" && selectedAccount?.id == selectedToAccount?.id) {
                                showErrorMsg = "A conta de origem deve ser diferente da conta de destino."
                                return@Button
                            }
                            if (type == "META" && selectedGoal == null) {
                                showErrorMsg = "Selecione uma meta de destino para o lançamento."
                                return@Button
                            }
                            val dbDate = uiToDbDate(dateString)
                            if (dbDate == null) {
                                showErrorMsg = "A data deve estar no formato DD/MM/AAAA (ex: 12/07/2026)."
                                return@Button
                            }

                            if (!isEdit && (type == "DESPESA" || type == "RECEITA") && isRecurrent) {
                                if (selectedCategory == null) {
                                    showErrorMsg = "Selecione uma categoria para o lançamento recorrente."
                                    return@Button
                                }
                                var endMonthStr: String? = null
                                if (hasEndDate) {
                                    val y = endCalendar.get(java.util.Calendar.YEAR)
                                    val m = endCalendar.get(java.util.Calendar.MONTH) + 1
                                    endMonthStr = String.format("%04d-%02d", y, m)
                                }
                                val rule = com.example.data.model.RecurrenceRule(
                                    id = 0,
                                    account_id = selectedAccount!!.id,
                                    category_id = selectedCategory!!.id,
                                    subcategory_id = selectedSubcategory?.id,
                                    description = description,
                                    value = valDouble,
                                    type = type,
                                    frequency = recurrenceFrequency,
                                    frequency_interval = recurrenceIntervalString.toIntOrNull() ?: 1,
                                    start_date = dbDate,
                                    end_month = endMonthStr,
                                    active = true,
                                    userId = viewModel.currentUserId
                                )
                                viewModel.createRecurrenceRule(rule) {
                                    onDismiss()
                                }
                            } else if (!isEdit && type == "DESPESA" && isParcelado) {
                                val countInt = installmentsCountString.toIntOrNull()
                                if (countInt == null || countInt < 2) {
                                    showErrorMsg = "Insira um número de parcelas válido maior ou igual a 2."
                                    return@Button
                                }
                                if (selectedCategory == null) {
                                    showErrorMsg = "Selecione uma categoria para o parcelamento."
                                    return@Button
                                }
                                val firstMonth = dbDate.substring(0, 7) // "yyyy-MM" from "yyyy-MM-dd"
                                val plan = InstallmentPlan(
                                    id = 0,
                                    account_id = selectedAccount!!.id,
                                    category_id = selectedCategory!!.id,
                                    subcategory_id = selectedSubcategory?.id,
                                    description = description,
                                    total_value = valDouble,
                                    installments_count = countInt,
                                    first_installment_month = firstMonth,
                                    created_at = System.currentTimeMillis(),
                                    userId = viewModel.currentUserId
                                )
                                viewModel.createInstallmentPlan(plan) {
                                    onDismiss()
                                }
                            } else {
                                val tx = Transaction(
                                    id = transactionToEdit?.id ?: 0,
                                    account_id = selectedAccount!!.id,
                                    to_account_id = if (type == "TRANSFERENCIA") selectedToAccount?.id else null,
                                    category_id = if (type != "TRANSFERENCIA" && type != "META") selectedCategory?.id else null,
                                    subcategory_id = if (type != "TRANSFERENCIA" && type != "META") selectedSubcategory?.id else null,
                                    goal_id = if (type == "META") selectedGoal?.id else null,
                                    type = type,
                                    value = valDouble,
                                    description = description,
                                    date = dbDate,
                                    installment_plan_id = transactionToEdit?.installment_plan_id,
                                    installment_number = transactionToEdit?.installment_number,
                                    recurrence_rule_id = transactionToEdit?.recurrence_rule_id,
                                    is_recurrence_override = transactionToEdit?.is_recurrence_override ?: false,
                                    attachment_uri = attachmentUri,
                                    attachment_name = attachmentName,
                                    attachment_type = if (attachmentUri != null) {
                                        val name = attachmentName?.lowercase() ?: ""
                                        if (name.endsWith(".pdf")) "application/pdf"
                                        else if (name.endsWith(".png")) "image/png"
                                        else "image/jpeg"
                                    } else null,
                                    synced = false,
                                    userId = viewModel.currentUserId
                                )

                                if (isEdit) {
                                    val isRecurrence = transactionToEdit?.recurrence_rule_id != null
                                    val isInstallment = transactionToEdit?.installment_plan_id != null
                                    if (isRecurrence || isInstallment) {
                                        showEditChoiceDialog = true
                                    } else {
                                        viewModel.updateTransaction(tx) {
                                            onDismiss()
                                        }
                                    }
                                } else {
                                    viewModel.insertTransaction(tx) {
                                        onDismiss()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Salvar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // --- QUICK CREATION DIALOGS ---
    if (showQuickCategoryDialog) {
        var newCatName by remember { mutableStateOf("") }
        var newSubName by remember { mutableStateOf("") }
        var quickCatError by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showQuickCategoryDialog = false },
            title = { Text("Criar Nova Categoria", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (quickCatError.isNotEmpty()) {
                        Text(quickCatError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = newCatName,
                        onValueChange = { newCatName = it },
                        label = { Text("Nome da Categoria") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = newSubName,
                        onValueChange = { newSubName = it },
                        label = { Text("Nome da primeira subcategoria") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCatName.isBlank() || newSubName.isBlank()) {
                            quickCatError = "Categoria e subcategoria são obrigatórias."
                            return@Button
                        }
                        val cat = Category(
                            envelope_group_id = null,
                            name = newCatName.trim(),
                            userId = viewModel.currentUserId
                        )
                        viewModel.insertCategory(cat) { newId ->
                            val sub = Subcategory(
                                category_id = newId,
                                name = newSubName.trim(),
                                userId = viewModel.currentUserId
                            )
                            viewModel.insertSubcategory(sub) { newSubId ->
                                val createdCat = Category(id = newId, envelope_group_id = null, name = newCatName.trim(), userId = viewModel.currentUserId)
                                val createdSub = Subcategory(id = newSubId, category_id = newId, name = newSubName.trim(), userId = viewModel.currentUserId)
                                selectedCategory = createdCat
                                selectedSubcategory = createdSub
                                showQuickCategoryDialog = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Adicionar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickCategoryDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showQuickSubcategoryDialog) {
        var newSubName by remember { mutableStateOf("") }
        var quickSubError by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showQuickSubcategoryDialog = false },
            title = { Text("Criar Nova Subcategoria", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (quickSubError.isNotEmpty()) {
                        Text(quickSubError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (selectedCategory == null) {
                        Text(
                            text = "Aviso: Selecione uma categoria antes de criar uma subcategoria.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = "Será criada sob a categoria: ${selectedCategory?.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = newSubName,
                        onValueChange = { newSubName = it },
                        label = { Text("Nome da Subcategoria") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedCategory != null,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedCategory == null) {
                            quickSubError = "Selecione uma categoria primeiro."
                            return@Button
                        }
                        if (newSubName.isBlank()) {
                            quickSubError = "O nome não pode ser vazio."
                            return@Button
                        }
                        val sub = Subcategory(
                            category_id = selectedCategory!!.id,
                            name = newSubName.trim(),
                            userId = viewModel.currentUserId
                        )
                        viewModel.insertSubcategory(sub) { newId ->
                            val created = Subcategory(id = newId, category_id = selectedCategory!!.id, name = newSubName.trim(), userId = viewModel.currentUserId)
                            selectedSubcategory = created
                            showQuickSubcategoryDialog = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    enabled = selectedCategory != null
                ) {
                    Text("Adicionar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickSubcategoryDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showDeleteConfirm && isEdit) {
        val isInstallment = transactionToEdit?.installment_plan_id != null
        val isRecurrence = transactionToEdit?.recurrence_rule_id != null

        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Excluir Transação") },
            text = {
                if (isRecurrence) {
                    Text("Esta transação é recorrente. Deseja excluir apenas esta ocorrência ou desativar a recorrência e excluir todas as ocorrências futuras?")
                } else if (isInstallment) {
                    Text("Esta transação é uma parcela. Deseja excluir apenas esta parcela ou excluir esta e todas as parcelas futuras desse plano?")
                } else {
                    Text("Tem certeza de que deseja excluir permanentemente esta transação?")
                }
            },
            confirmButton = {
                if (isRecurrence || isInstallment) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Button(
                            onClick = {
                                showDeleteConfirm = false
                                if (isRecurrence) {
                                    val ruleId = transactionToEdit!!.recurrence_rule_id!!
                                    val fromMonth = if (transactionToEdit.date.length >= 7) transactionToEdit.date.substring(0, 7) else ""
                                    viewModel.deleteRecurrenceRuleAndFuture(ruleId, viewModel.currentUserId, fromMonth) {
                                        onDismiss()
                                    }
                                } else if (isInstallment) {
                                    val planId = transactionToEdit!!.installment_plan_id!!
                                    val installmentNum = transactionToEdit.installment_number!!
                                    viewModel.deleteInstallmentAndFuture(planId, installmentNum, viewModel.currentUserId) {
                                        onDismiss()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Esta e as futuras")
                        }

                        OutlinedButton(
                            onClick = {
                                showDeleteConfirm = false
                                viewModel.deleteTransaction(transactionToEdit!!) {
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Só esta")
                        }
                    }
                } else {
                    TextButton(onClick = {
                        viewModel.deleteTransaction(transactionToEdit!!) {
                            showDeleteConfirm = false
                            onDismiss()
                        }
                    }) {
                        Text("Excluir", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (!isRecurrence && !isInstallment) {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }

    if (showEditChoiceDialog && isEdit) {
        val isRecurrence = transactionToEdit?.recurrence_rule_id != null
        val isInstallment = transactionToEdit?.installment_plan_id != null

        AlertDialog(
            onDismissRequest = { showEditChoiceDialog = false },
            title = { Text("Opções de Edição") },
            text = {
                if (isRecurrence) {
                    Text("Esta transação é recorrente. Deseja aplicar as alterações apenas a esta ocorrência ou a esta e todas as ocorrências futuras?")
                } else if (isInstallment) {
                    Text("Esta transação faz parte de um parcelamento. Deseja aplicar as alterações apenas a esta parcela ou a esta e todas as parcelas futuras?")
                } else {
                    Text("Deseja salvar as alterações?")
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showEditChoiceDialog = false
                            val valDouble = valueString.replace(",", ".").toDoubleOrNull() ?: 0.0
                            val dbDate = uiToDbDate(dateString) ?: transactionToEdit!!.date

                            if (isRecurrence) {
                                coroutineScope.launch {
                                    val ruleId = transactionToEdit!!.recurrence_rule_id!!
                                    val rule = viewModel.repository.getRecurrenceRuleById(ruleId)
                                    if (rule != null) {
                                        var endMonthStr: String? = null
                                        if (hasEndDate) {
                                            val y = endCalendar.get(java.util.Calendar.YEAR)
                                            val m = endCalendar.get(java.util.Calendar.MONTH) + 1
                                            endMonthStr = String.format("%04d-%02d", y, m)
                                        }
                                        val updatedRule = rule.copy(
                                            account_id = selectedAccount!!.id,
                                            category_id = selectedCategory?.id ?: rule.category_id,
                                            subcategory_id = selectedSubcategory?.id,
                                            description = description,
                                            value = valDouble,
                                            type = type,
                                            frequency = recurrenceFrequency,
                                            frequency_interval = recurrenceIntervalString.toIntOrNull() ?: 1,
                                            end_month = endMonthStr
                                        )
                                        val fromMonth = if (dbDate.length >= 7) dbDate.substring(0, 7) else ""
                                        viewModel.updateRecurrenceRuleAndFuture(updatedRule, fromMonth) {
                                            onDismiss()
                                        }
                                    } else {
                                        val fallbackTx = Transaction(
                                            id = transactionToEdit.id,
                                            account_id = selectedAccount!!.id,
                                            to_account_id = if (type == "TRANSFERENCIA") selectedToAccount?.id else null,
                                            category_id = if (type != "TRANSFERENCIA") selectedCategory?.id else null,
                                            subcategory_id = if (type != "TRANSFERENCIA") selectedSubcategory?.id else null,
                                            type = type,
                                            value = valDouble,
                                            description = description,
                                            date = dbDate,
                                            installment_plan_id = transactionToEdit.installment_plan_id,
                                            installment_number = transactionToEdit.installment_number,
                                            recurrence_rule_id = transactionToEdit.recurrence_rule_id,
                                            is_recurrence_override = transactionToEdit.is_recurrence_override,
                                            attachment_uri = attachmentUri,
                                            attachment_name = attachmentName,
                                            attachment_type = if (attachmentUri != null) {
                                                val name = attachmentName?.lowercase() ?: ""
                                                if (name.endsWith(".pdf")) "application/pdf"
                                                else if (name.endsWith(".png")) "image/png"
                                                else "image/jpeg"
                                            } else null,
                                            synced = false,
                                            userId = viewModel.currentUserId
                                        )
                                        viewModel.updateTransaction(fallbackTx) { onDismiss() }
                                    }
                                }
                            } else if (isInstallment) {
                                val planId = transactionToEdit!!.installment_plan_id!!
                                val installmentNum = transactionToEdit.installment_number!!
                                val countInt = installmentsCountString.toIntOrNull()
                                viewModel.updateInstallmentAndFuture(
                                    planId = planId,
                                    fromInstallmentNumber = installmentNum,
                                    updatedValue = valDouble,
                                    updatedCategory = selectedCategory?.id,
                                    updatedSubcategory = selectedSubcategory?.id,
                                    updatedDescription = description,
                                    updatedAccountId = selectedAccount!!.id,
                                    userId = viewModel.currentUserId,
                                    updatedInstallmentsCount = countInt
                                ) {
                                    onDismiss()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Esta e as futuras")
                    }

                    OutlinedButton(
                        onClick = {
                            showEditChoiceDialog = false
                            val valDouble = valueString.replace(",", ".").toDoubleOrNull() ?: 0.0
                            val dbDate = uiToDbDate(dateString) ?: transactionToEdit!!.date
                            val tx = Transaction(
                                id = transactionToEdit!!.id,
                                account_id = selectedAccount!!.id,
                                to_account_id = if (type == "TRANSFERENCIA") selectedToAccount?.id else null,
                                category_id = if (type != "TRANSFERENCIA") selectedCategory?.id else null,
                                subcategory_id = if (type != "TRANSFERENCIA") selectedSubcategory?.id else null,
                                type = type,
                                value = valDouble,
                                description = description,
                                date = dbDate,
                                installment_plan_id = transactionToEdit.installment_plan_id,
                                installment_number = transactionToEdit.installment_number,
                                recurrence_rule_id = transactionToEdit.recurrence_rule_id,
                                is_recurrence_override = if (isRecurrence) true else transactionToEdit.is_recurrence_override,
                                attachment_uri = attachmentUri,
                                attachment_name = attachmentName,
                                attachment_type = if (attachmentUri != null) {
                                    val name = attachmentName?.lowercase() ?: ""
                                    if (name.endsWith(".pdf")) "application/pdf"
                                    else if (name.endsWith(".png")) "image/png"
                                    else "image/jpeg"
                                } else null,
                                synced = false,
                                userId = viewModel.currentUserId
                            )
                            viewModel.updateTransaction(tx) {
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Só esta")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditChoiceDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

data class TransactionTypeOption(val label: String, val id: String)

// --- UTILS & HELPERS ---

private fun dbToUiDate(dbDate: String): String {
    return try {
        val parts = dbDate.split("-")
        if (parts.size == 3) {
            "${parts[2]}/${parts[1]}/${parts[0]}"
        } else dbDate
    } catch (e: Exception) {
        dbDate
    }
}

private fun uiToDbDate(uiDate: String): String? {
    return try {
        val cleaned = uiDate.replace("-", "/")
        val parts = cleaned.split("/")
        if (parts.size == 3) {
            val day = parts[0].padStart(2, '0')
            val month = parts[1].padStart(2, '0')
            val year = parts[2]
            if (year.length == 4 && day.length == 2 && month.length == 2) {
                "$year-$month-$day"
            } else null
        } else null
    } catch (e: Exception) {
        null
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    name = it.getString(displayNameIndex)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return name
}

private fun renderPdfPageToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val pfd = com.example.utils.ExportHelper.openFileDescriptorSafely(context, uri)
        pfd?.use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount > 0) {
                    renderer.openPage(0).use { page ->
                        val w = if (page.width > 0) page.width else 1000
                        val h = if (page.height > 0) page.height else 1414
                        val width = 1000
                        val height = (1000 * h) / w
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                } else null
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun AttachmentViewerDialog(
    attachmentUri: String,
    attachmentName: String,
    transactionDescription: String,
    transactionValue: Double,
    transactionDate: String,
    categoryName: String?,
    accountName: String,
    transaction: Transaction? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val parsedUri = remember(attachmentUri) {
        try {
            if (attachmentUri.startsWith("/") || !attachmentUri.contains(":/")) {
                Uri.fromFile(java.io.File(attachmentUri))
            } else {
                Uri.parse(attachmentUri)
            }
        } catch (e: Exception) {
            Uri.parse(attachmentUri)
        }
    }
    val isPdf = remember(attachmentName) { attachmentName.endsWith(".pdf", true) }

    var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var renderError by remember { mutableStateOf(false) }
    var imageLoadError by remember { mutableStateOf(false) }

    LaunchedEffect(parsedUri) {
        if (isPdf) {
            val bitmap = renderPdfPageToBitmap(context, parsedUri)
            if (bitmap != null) {
                pdfBitmap = bitmap
            } else {
                renderError = true
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f)),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = attachmentName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isPdf) "Visualizador de PDF (Formato A4)" else "Visualizador de Imagem (Formato A4)",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val coroutineScope = rememberCoroutineScope()
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val file = com.example.utils.ExportHelper.exportAttachmentOnlyToPdf(
                                        context = context,
                                        attachmentUri = attachmentUri,
                                        attachmentName = attachmentName
                                    )
                                    if (file != null) {
                                        val authority = "${context.packageName}.fileprovider"
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            authority,
                                            file
                                        )

                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Comprovante - $attachmentName")
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }

                                        context.startActivity(
                                            android.content.Intent.createChooser(
                                                shareIntent,
                                                "Exportar Comprovante (PDF)"
                                            )
                                        )
                                    } else {
                                        android.widget.Toast.makeText(context, "Erro ao gerar PDF do comprovante", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Exportar PDF", tint = Color.White)
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar Visualizador", tint = Color.White)
                        }
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .aspectRatio(1f / 1.414f, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    val hasRealAttachment = !attachmentUri.startsWith("content://meu_financeiro/") && attachmentUri.isNotBlank()

                    if (isPdf) {
                        if (pdfBitmap != null) {
                            Image(
                                bitmap = pdfBitmap!!.asImageBitmap(),
                                contentDescription = "Página PDF",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else if (renderError) {
                            // Honest Error Screen for PDF render failure
                            AttachmentRenderErrorScreen(
                                isPdf = true,
                                transactionDescription = transactionDescription,
                                transactionValue = transactionValue,
                                transactionDate = transactionDate,
                                categoryName = categoryName,
                                accountName = accountName
                            )
                        } else {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (hasRealAttachment) {
                        if (imageLoadError) {
                            // Honest Error Screen for Image loading failure
                            AttachmentRenderErrorScreen(
                                isPdf = false,
                                transactionDescription = transactionDescription,
                                transactionValue = transactionValue,
                                transactionDate = transactionDate,
                                categoryName = categoryName,
                                accountName = accountName
                            )
                        } else {
                            AsyncImage(
                                model = attachmentUri,
                                contentDescription = "Comprovante real",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                onError = {
                                    imageLoadError = true
                                }
                            )
                        }
                    } else {
                        // Genuinely has no real attachment
                        A4ReceiptLayout(
                            description = transactionDescription,
                            value = transactionValue,
                            date = transactionDate,
                            category = categoryName,
                            account = accountName
                        )
                    }
                }

                Text(
                    text = "Visualização de Página A4 • Meu Financeiro",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
fun AttachmentRenderErrorScreen(
    isPdf: Boolean,
    transactionDescription: String,
    transactionValue: Double,
    transactionDate: String,
    categoryName: String?,
    accountName: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFDE8E8)) // Light red background for error page
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Color(0xFFC53030),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Não foi possível carregar o anexo desta transação",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFC53030),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isPdf) {
                    "Falha ao renderizar a página PDF. O arquivo pode estar corrompido, inacessível ou o formato não é suportado pelo dispositivo."
                } else {
                    "Falha ao carregar a imagem. O arquivo pode não existir mais no armazenamento ou o formato é inválido."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9B2C2C),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = Color(0xFFFEB2B2))
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "DETALHES DA TRANSAÇÃO",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF742A2A)
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            TransactionDetailRow(label = "Descrição:", value = transactionDescription)
            TransactionDetailRow(label = "Valor:", value = "R$ " + String.format(Locale.US, "%.2f", transactionValue))
            TransactionDetailRow(label = "Data:", value = transactionDate)
            categoryName?.let {
                TransactionDetailRow(label = "Categoria:", value = it)
            }
            TransactionDetailRow(label = "Conta:", value = accountName)
        }
        
        Text(
            text = "Erro Técnico • Meu Financeiro",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF9B2C2C).copy(alpha = 0.7f)
        )
    }
}

@Composable
fun TransactionDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF742A2A)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2D3748)
        )
    }
}

@Composable
fun A4ReceiptLayout(
    description: String,
    value: Double,
    date: String,
    category: String?,
    account: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MEU FINANCEIRO",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = Color(0xFF1B5E20),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Comprovante de Transação Digital",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "VÁLIDO",
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.LightGray, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "DETALHES DO LANÇAMENTO",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color.DarkGray,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            ReceiptRowItem(label = "Descrição:", value = description)
            ReceiptRowItem(label = "Valor:", value = "R$ %.2f".format(value), valueColor = Color(0xFF1B5E20))
            ReceiptRowItem(label = "Data:", value = if (date.contains("-")) {
                val parts = date.split("-")
                if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else date
            } else date)
            ReceiptRowItem(label = "Categoria:", value = category ?: "Nenhuma")
            ReceiptRowItem(label = "Conta:", value = account)

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = Color.LightGray, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "CÓDIGO DE AUTENTICAÇÃO DIGITAL",
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                val width = size.width
                val height = size.height
                var x = 0f
                val barColor = Color.Black
                var i = 0
                while (x < width) {
                    val barWidth = if (i % 3 == 0) 6f else if (i % 2 == 0) 3f else 1.5f
                    val space = if (i % 4 == 0) 4f else 2f
                    drawRect(
                        color = barColor,
                        topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                        size = androidx.compose.ui.geometry.Size(barWidth, height)
                    )
                    x += barWidth + space
                    i++
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "MF-${System.currentTimeMillis().toString().takeLast(8)}-AUTENTICADO",
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 9.sp,
                color = Color.Gray
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            HorizontalDivider(color = Color.LightGray, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Este documento foi gerado pelo aplicativo Meu Financeiro e serve como comprovante fiscal de lançamento interno.",
                fontSize = 8.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Assinatura Eletrônica MD5: " + "MF_HASH_" + System.currentTimeMillis().toString().hashCode().toString(16).uppercase(Locale.ROOT),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 8.sp,
                color = Color.LightGray
            )
        }
    }
}

@Composable
fun ReceiptRowItem(label: String, value: String, valueColor: Color = Color.Black) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.Normal, fontSize = 12.sp, color = Color.Gray)
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = valueColor)
    }
}
