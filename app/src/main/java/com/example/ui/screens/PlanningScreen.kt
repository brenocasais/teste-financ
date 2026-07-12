package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.data.model.BudgetAllocation
import com.example.data.model.Category
import com.example.data.model.EnvelopeGroup
import com.example.data.model.Subcategory
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningScreen(viewModel: MainViewModel) {
    val userId = viewModel.currentUserId
    val coroutineScope = rememberCoroutineScope()
    val selectedMonthCalendar by viewModel.selectedMonthCalendar.collectAsStateWithLifecycle()
    val currentMonthStr = remember(selectedMonthCalendar) {
        java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(selectedMonthCalendar.time)
    }

    // Database state flows
    val envelopeGroups by viewModel.repository.getEnvelopeGroupsFlow(userId).collectAsStateWithLifecycle(emptyList())
    val categories by viewModel.repository.getCategoriesFlow(userId).collectAsStateWithLifecycle(emptyList())
    val subcategories by viewModel.repository.getSubcategoriesFlow(userId).collectAsStateWithLifecycle(emptyList())
    val transactions by viewModel.repository.getTransactionsFlow(userId).collectAsStateWithLifecycle(emptyList())
    val budgetAllocations by viewModel.repository.getBudgetAllocationsFlow(userId).collectAsStateWithLifecycle(emptyList())
    val allocationMovements by viewModel.repository.getAllocationMovementsFlow(userId).collectAsStateWithLifecycle(emptyList())
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

    // "Pronto para Atribuir" computation
    val totalAlocadoNoMes by remember(allocationInfoMap) {
        derivedStateOf { allocationInfoMap.values.sumOf { it.second } }
    }

    val prontoParaAtribuir by remember(totalAccountBalance, totalAlocadoNoMes) {
        derivedStateOf { totalAccountBalance - totalAlocadoNoMes }
    }

    // State of Dialogs
    var showDistributeDialog by remember { mutableStateOf(false) }
    var showNewEnvelopeDialog by remember { mutableStateOf(false) }
    var targetEditAllocation by remember { mutableStateOf<Pair<Category, Subcategory?>?>(null) }
    var targetMoveAllocation by remember { mutableStateOf<Pair<Category, Subcategory?>?>(null) }

    // Active expanded categories inside the tree
    var expandedGroupIds by remember { mutableStateOf(setOf<Int>()) }
    var expandedCategoryIds by remember { mutableStateOf(setOf<Int>()) }

    var selectedTab by remember { mutableStateOf(0) } // 0: Envelopes, 1: Categorias, 2: Subcategorias

    // Helper formatter
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // "Pronto para Atribuir" Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (prontoParaAtribuir >= 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Pronto para Atribuir",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (prontoParaAtribuir >= 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )

                    Text(
                        text = currencyFormatter.format(prontoParaAtribuir),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = if (prontoParaAtribuir >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("pronto_para_atribuir_value")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showDistributeDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Distribuir", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { showNewEnvelopeDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (prontoParaAtribuir >= 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Novo Envelope", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Envelopes") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Categorias") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Subcategorias") }
                )
            }

            // List Content based on Selected Tab
            Box(modifier = Modifier.weight(1f)) {
                if (envelopeGroups.isEmpty()) {
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
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "Nenhum envelope cadastrado",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    when (selectedTab) {
                        0 -> {
                            // Tree Hierarchical view
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                envelopeGroups.forEach { group ->
                                    val isGroupExpanded = expandedGroupIds.contains(group.id)
                                    val categoriesInGroup = categories.filter { it.envelope_group_id == group.id }

                                    // Compute group-level totals
                                    var groupPlanejado = 0.0
                                    var groupAlocado = 0.0
                                    var groupGasto = 0.0

                                    categoriesInGroup.forEach { cat ->
                                        // Category level directly
                                        val catInfo = allocationInfoMap[Pair(cat.id, null)]
                                        groupPlanejado += catInfo?.first ?: 0.0
                                        groupAlocado += catInfo?.second ?: 0.0
                                        groupGasto += spentInfoMap[Pair(cat.id, null)] ?: 0.0

                                        // Subcategories under this category
                                        val subs = subcategories.filter { it.category_id == cat.id }
                                        subs.forEach { sub ->
                                            val subInfo = allocationInfoMap[Pair(cat.id, sub.id)]
                                            groupPlanejado += subInfo?.first ?: 0.0
                                            groupAlocado += subInfo?.second ?: 0.0
                                            groupGasto += spentInfoMap[Pair(cat.id, sub.id)] ?: 0.0
                                        }
                                    }

                                    item(key = "group_${group.id}") {
                                        GroupHeaderItem(
                                            name = group.name,
                                            planejado = groupPlanejado,
                                            alocado = groupAlocado,
                                            gasto = groupGasto,
                                            isExpanded = isGroupExpanded,
                                            onToggle = {
                                                expandedGroupIds = if (isGroupExpanded) {
                                                    expandedGroupIds - group.id
                                                } else {
                                                    expandedGroupIds + group.id
                                                }
                                            }
                                        )
                                    }

                                    if (isGroupExpanded) {
                                        if (categoriesInGroup.isEmpty()) {
                                            item {
                                                Text(
                                                    text = "  Nenhuma categoria neste grupo.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 4.dp)
                                                )
                                            }
                                        } else {
                                            categoriesInGroup.forEach { cat ->
                                                val isCatExpanded = expandedCategoryIds.contains(cat.id)
                                                val subsInCat = subcategories.filter { it.category_id == cat.id }

                                                // Compute category totals (direct + subs)
                                                val catDirectInfo = allocationInfoMap[Pair(cat.id, null)]
                                                var catPlanejado = catDirectInfo?.first ?: 0.0
                                                var catAlocado = catDirectInfo?.second ?: 0.0
                                                var catGasto = spentInfoMap[Pair(cat.id, null)] ?: 0.0

                                                subsInCat.forEach { sub ->
                                                    val subInfo = allocationInfoMap[Pair(cat.id, sub.id)]
                                                    catPlanejado += subInfo?.first ?: 0.0
                                                    catAlocado += subInfo?.second ?: 0.0
                                                    catGasto += spentInfoMap[Pair(cat.id, sub.id)] ?: 0.0
                                                }

                                                item(key = "cat_${cat.id}") {
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
                                                        onEditPlanned = { targetEditAllocation = Pair(cat, null) },
                                                        onMoveMoney = { targetMoveAllocation = Pair(cat, null) }
                                                    )
                                                }

                                                if (isCatExpanded) {
                                                    if (subsInCat.isEmpty()) {
                                                        item {
                                                            Text(
                                                                text = "    Nenhuma subcategoria.",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                                modifier = Modifier.padding(start = 36.dp, top = 4.dp, bottom = 4.dp)
                                                            )
                                                        }
                                                    } else {
                                                        items(subsInCat, key = { "sub_${it.id}" }) { sub ->
                                                            val subInfo = allocationInfoMap[Pair(cat.id, sub.id)]
                                                            val subPlanejado = subInfo?.first ?: 0.0
                                                            val subAlocado = subInfo?.second ?: 0.0
                                                            val subGasto = spentInfoMap[Pair(cat.id, sub.id)] ?: 0.0

                                                            SubcategoryRowItem(
                                                                name = sub.name,
                                                                planejado = subPlanejado,
                                                                alocado = subAlocado,
                                                                gasto = subGasto,
                                                                onEditPlanned = { targetEditAllocation = Pair(cat, sub) },
                                                                onMoveMoney = { targetMoveAllocation = Pair(cat, sub) }
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
                        1 -> {
                            // Flat list of Categories
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(categories, key = { "flat_cat_${it.id}" }) { cat ->
                                    val catDirectInfo = allocationInfoMap[Pair(cat.id, null)]
                                    val catPlanejado = catDirectInfo?.first ?: 0.0
                                    val catAlocado = catDirectInfo?.second ?: 0.0
                                    val catGasto = spentInfoMap[Pair(cat.id, null)] ?: 0.0

                                    CategoryRowItem(
                                        category = cat,
                                        planejado = catPlanejado,
                                        alocado = catAlocado,
                                        gasto = catGasto,
                                        isExpanded = false,
                                        onToggle = {},
                                        showToggle = false,
                                        onEditPlanned = { targetEditAllocation = Pair(cat, null) },
                                        onMoveMoney = { targetMoveAllocation = Pair(cat, null) }
                                    )
                                }
                            }
                        }
                        2 -> {
                            // Flat list of Subcategories
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(subcategories, key = { "flat_sub_${it.id}" }) { sub ->
                                    val parentCat = categories.find { it.id == sub.category_id } ?: return@items
                                    val subInfo = allocationInfoMap[Pair(parentCat.id, sub.id)]
                                    val subPlanejado = subInfo?.first ?: 0.0
                                    val subAlocado = subInfo?.second ?: 0.0
                                    val subGasto = spentInfoMap[Pair(parentCat.id, sub.id)] ?: 0.0

                                    SubcategoryRowItem(
                                        name = "${parentCat.name} > ${sub.name}",
                                        planejado = subPlanejado,
                                        alocado = subAlocado,
                                        gasto = subGasto,
                                        onEditPlanned = { targetEditAllocation = Pair(parentCat, sub) },
                                        onMoveMoney = { targetMoveAllocation = Pair(parentCat, sub) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS IMPLEMENTATION ---

    // 1. Distribuir Dinheiro (Move money between buckets / Pronto para Atribuir)
    if (showDistributeDialog || targetMoveAllocation != null) {
        val initialSource = if (targetMoveAllocation != null) {
            // Pre-select target as destination or source
            "envelope"
        } else "pronto"

        DistributeDialog(
            viewModel = viewModel,
            prontoParaAtribuir = prontoParaAtribuir,
            preSelectedPair = targetMoveAllocation,
            categories = categories,
            subcategories = subcategories,
            allocationInfoMap = allocationInfoMap,
            onDismiss = {
                showDistributeDialog = false
                targetMoveAllocation = null
            }
        )
    }

    // 2. Novo Envelope (Create new EnvelopeGroup)
    if (showNewEnvelopeDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewEnvelopeDialog = false },
            title = { Text("Novo Envelope") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Grupo de Envelope") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            coroutineScope.launch {
                                viewModel.repository.insertEnvelopeGroup(
                                    EnvelopeGroup(
                                        name = name,
                                        sort_order = envelopeGroups.size + 1,
                                        budget_rule_type = null,
                                        userId = userId
                                    )
                                )
                                viewModel.triggerPush()
                            }
                            showNewEnvelopeDialog = false
                        }
                    },
                    enabled = name.isNotBlank()
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

    // 3. Atribuir Planejado (Edit monthly planned target)
    if (targetEditAllocation != null) {
        val pair = targetEditAllocation!!
        val cat = pair.first
        val sub = pair.second
        val currentInfo = allocationInfoMap[Pair(cat.id, sub?.id)]
        var planejadoInput by remember { mutableStateOf(currentInfo?.first?.toString() ?: "") }

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
                OutlinedTextField(
                    value = planejadoInput,
                    onValueChange = { planejadoInput = it },
                    label = { Text("Valor Planejado") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    prefix = { Text("R$ ") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val value = planejadoInput.toDoubleOrNull() ?: 0.0
                        viewModel.setPlannedValue(
                            categoryId = cat.id,
                            subcategoryId = sub?.id,
                            month = currentMonthStr,
                            plannedValue = value,
                            userId = userId,
                            onComplete = { targetEditAllocation = null }
                        )
                    }
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
}

@Composable
fun GroupHeaderItem(
    name: String,
    planejado: Double,
    alocado: Double,
    gasto: Double,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val disponivel = alocado - gasto
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Discrepancy indicator
                if (planejado != alocado) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Planejado difere de Alocado",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Text(
                    text = "Disp: ${currencyFormatter.format(disponivel)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (disponivel >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle totals
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Plan: ${currencyFormatter.format(planejado)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Text(
                    text = "Aloc: ${currencyFormatter.format(alocado)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Text(
                    text = "Gasto: ${currencyFormatter.format(gasto)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun CategoryRowItem(
    category: Category,
    planejado: Double,
    alocado: Double,
    gasto: Double,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    showToggle: Boolean = true,
    onEditPlanned: () -> Unit,
    onMoveMoney: () -> Unit
) {
    val disponivel = alocado - gasto
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val progress = if (alocado > 0) (gasto / alocado).toFloat().coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showToggle) {
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (planejado != alocado) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Planejado e Alocado diferem",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Text(
                            text = currencyFormatter.format(disponivel),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (disponivel >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Plan: ${currencyFormatter.format(planejado)} | Aloc: ${currencyFormatter.format(alocado)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "Gasto: ${currencyFormatter.format(gasto)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (disponivel >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onEditPlanned,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Definir Planejado",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onMoveMoney,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CompareArrows,
                        contentDescription = "Mover Dinheiro",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun SubcategoryRowItem(
    name: String,
    planejado: Double,
    alocado: Double,
    gasto: Double,
    onEditPlanned: () -> Unit,
    onMoveMoney: () -> Unit
) {
    val disponivel = alocado - gasto
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val progress = if (alocado > 0) (gasto / alocado).toFloat().coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (planejado != alocado) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Planejado e Alocado diferem",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Text(
                    text = currencyFormatter.format(disponivel),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (disponivel >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Plan: ${currencyFormatter.format(planejado)} | Aloc: ${currencyFormatter.format(alocado)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Text(
                    text = "Gasto: ${currencyFormatter.format(gasto)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = if (disponivel >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onEditPlanned,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Definir Planejado",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onMoveMoney,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CompareArrows,
                        contentDescription = "Mover Dinheiro",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
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
    preSelectedPair: Pair<Category, Subcategory?>?,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    allocationInfoMap: Map<Pair<Int, Int?>, Pair<Double, Double>>,
    onDismiss: () -> Unit
) {
    val userId = viewModel.currentUserId
    val selectedMonthCalendar by viewModel.selectedMonthCalendar.collectAsStateWithLifecycle()
    val currentMonthStr = remember(selectedMonthCalendar) {
        java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(selectedMonthCalendar.time)
    }

    var valueInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }

    // Source selection: 0 for Pronto para Atribuir, 1 for Envelope
    var sourceMode by remember { mutableStateOf(0) }
    var sourceCategory by remember { mutableStateOf<Category?>(null) }
    var sourceSubcategory by remember { mutableStateOf<Subcategory?>(null) }
    var showSourceDropdown by remember { mutableStateOf(false) }

    // Destination selection: 0 for Envelope, 1 for Pronto para Atribuir
    var destMode by remember { mutableStateOf(0) }
    var destCategory by remember { mutableStateOf<Category?>(null) }
    var destSubcategory by remember { mutableStateOf<Subcategory?>(null) }
    var showDestDropdown by remember { mutableStateOf(false) }

    // Apply pre-selection
    LaunchedEffect(preSelectedPair) {
        if (preSelectedPair != null) {
            // By default, pre-select as destination from "Pronto para Atribuir"
            destMode = 0
            destCategory = preSelectedPair.first
            destSubcategory = preSelectedPair.second
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
                // VALUE INPUT
                OutlinedTextField(
                    value = valueInput,
                    onValueChange = { valueInput = it },
                    label = { Text("Valor a Transferir") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    prefix = { Text("R$ ") }
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
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(selected = sourceMode == 0, onClick = { sourceMode = 0 })
                            Text("Pronto para Atribuir (${currencyFormatter.format(prontoParaAtribuir)})", fontSize = 12.sp, modifier = Modifier.clickable { sourceMode = 0 })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(selected = sourceMode == 1, onClick = { sourceMode = 1 })
                            Text("Um Envelope", fontSize = 12.sp, modifier = Modifier.clickable { sourceMode = 1 })
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
                                        val catInfo = allocationInfoMap[Pair(cat.id, null)]
                                        val catAlocado = catInfo?.second ?: 0.0
                                        DropdownMenuItem(
                                            text = { Text("${cat.name} (Aloc: ${currencyFormatter.format(catAlocado)})", fontWeight = FontWeight.Bold) },
                                            onClick = {
                                                sourceCategory = cat
                                                sourceSubcategory = null
                                                showSourceDropdown = false
                                            }
                                        )

                                        val subs = subcategories.filter { it.category_id == cat.id }
                                        subs.forEach { sub ->
                                            val subInfo = allocationInfoMap[Pair(cat.id, sub.id)]
                                            val subAlocado = subInfo?.second ?: 0.0
                                            DropdownMenuItem(
                                                text = { Text("  ↳ ${sub.name} (Aloc: ${currencyFormatter.format(subAlocado)})") },
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
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(selected = destMode == 0, onClick = { destMode = 0 })
                            Text("Um Envelope", fontSize = 12.sp, modifier = Modifier.clickable { destMode = 0 })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(selected = destMode == 1, onClick = { destMode = 1 })
                            Text("Pronto para Atribuir", fontSize = 12.sp, modifier = Modifier.clickable { destMode = 1 })
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
                                        val catInfo = allocationInfoMap[Pair(cat.id, null)]
                                        val catAlocado = catInfo?.second ?: 0.0
                                        DropdownMenuItem(
                                            text = { Text("${cat.name} (Aloc: ${currencyFormatter.format(catAlocado)})", fontWeight = FontWeight.Bold) },
                                            onClick = {
                                                destCategory = cat
                                                destSubcategory = null
                                                showDestDropdown = false
                                            }
                                        )

                                        val subs = subcategories.filter { it.category_id == cat.id }
                                        subs.forEach { sub ->
                                            val subInfo = allocationInfoMap[Pair(cat.id, sub.id)]
                                            val subAlocado = subInfo?.second ?: 0.0
                                            DropdownMenuItem(
                                                text = { Text("  ↳ ${sub.name} (Aloc: ${currencyFormatter.format(subAlocado)})") },
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
            val isValid = remember(amount, sourceMode, sourceCategory, sourceSubcategory, destMode, destCategory, destSubcategory, prontoParaAtribuir, allocationInfoMap) {
                if (amount <= 0.0) return@remember false
                
                // Validate source balance
                val sourceBalance = if (sourceMode == 0) {
                    prontoParaAtribuir
                } else {
                    if (sourceCategory == null) return@remember false
                    val info = allocationInfoMap[Pair(sourceCategory!!.id, sourceSubcategory?.id)]
                    info?.second ?: 0.0
                }
                
                if (sourceBalance < amount) return@remember false

                // Validate destination is selected
                if (destMode == 0 && destCategory == null) return@remember false

                // Validate they are not the exact same
                if (sourceMode == 0 && destMode == 1) return@remember false
                if (sourceMode == 1 && destMode == 0 && sourceCategory?.id == destCategory?.id && sourceSubcategory?.id == destSubcategory?.id) return@remember false

                true
            }

            Button(
                onClick = {
                    val finalSourceCat = if (sourceMode == 1) sourceCategory?.id else null
                    val finalSourceSub = if (sourceMode == 1) sourceSubcategory?.id else null

                    val finalDestCat = if (destMode == 0) destCategory?.id else null
                    val finalDestSub = if (destMode == 0) destSubcategory?.id else null

                    viewModel.moveMoney(
                        sourceCategoryId = finalSourceCat,
                        sourceSubcategoryId = finalSourceSub,
                        destCategoryId = finalDestCat,
                        destSubcategoryId = finalDestSub,
                        month = currentMonthStr,
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
