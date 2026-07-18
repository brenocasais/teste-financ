package com.example.ui.screens

import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.auth.AuthManager
import com.example.data.model.Account
import com.example.data.model.InstallmentPlan
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.navigateToTab.collect { tabIndex ->
            selectedTab = tabIndex
        }
    }

    val tabs = listOf(
        NavigationTab("Início", Icons.Default.Home, Icons.Outlined.Home),
        NavigationTab("Transações", Icons.Default.ReceiptLong, Icons.Outlined.ReceiptLong),
        NavigationTab("Planejamento", Icons.Default.FolderOpen, Icons.Outlined.FolderOpen),
        NavigationTab("Métricas", Icons.Default.TrendingUp, Icons.Outlined.TrendingUp),
        NavigationTab("Metas", Icons.Default.EmojiEvents, Icons.Outlined.EmojiEvents),
        NavigationTab("Ajustes", Icons.Default.Settings, Icons.Outlined.Settings)
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == index) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        val userId = viewModel.currentUserId
        val syncState by viewModel.syncState.collectAsStateWithLifecycle()
        val selectedMonthCalendar by viewModel.selectedMonthCalendar.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Global header visible across all screens
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SyncStatusBar(
                    userId = userId,
                    syncState = syncState,
                    onSyncClick = { viewModel.triggerPush() }
                )
                
                GlobalMonthSelector(
                    selectedMonthCalendar = selectedMonthCalendar,
                    onMonthChange = { newCal -> viewModel.setSelectedMonth(newCal) },
                    onCurrentMonthClick = { viewModel.selectCurrentMonth() }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    0 -> DashboardTab(viewModel)
                    1 -> TransactionsScreen(viewModel)
                    2 -> PlanningScreen(viewModel)
                    3 -> MetricsScreen(viewModel)
                    4 -> GoalsScreen(viewModel)
                    5 -> SettingsScreen(viewModel)
                }
            }
        }
    }
}

data class NavigationTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTab(
    viewModel: MainViewModel
) {
    val userId = viewModel.currentUserId
    val accounts by viewModel.repository.getAccountsWithBalancesFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val transactions by viewModel.repository.getTransactionsFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val budgetAllocations by viewModel.repository.getBudgetAllocationsFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val allocationMovements by viewModel.repository.getAllocationMovementsFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val goals by viewModel.repository.getGoalsFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedAccountForDetail by remember { mutableStateOf<Account?>(null) }

    val totalBalance = remember(accounts) {
        accounts.sumOf { it.initial_balance }
    }

    val selectedMonthCalendar by viewModel.selectedMonthCalendar.collectAsStateWithLifecycle()
    val currentMonth = remember(selectedMonthCalendar) {
        java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(selectedMonthCalendar.time)
    }

    val goalBalances = remember(goals, allocationMovements, transactions) {
        goals.associate { goal ->
            val destSum = allocationMovements.filter { it.dest_goal_id == goal.id }.sumOf { it.amount } +
                          transactions.filter { it.type == "META" && it.goal_id == goal.id }.sumOf { it.value }
            val sourceSum = allocationMovements.filter { it.source_goal_id == goal.id }.sumOf { it.amount }
            goal.id to (destSum - sourceSum)
        }
    }

    val totalGoalsCurrentValue = remember(goalBalances) {
        goalBalances.values.sum()
    }

    val prontoParaAtribuir = remember(totalBalance, budgetAllocations, allocationMovements, currentMonth, totalGoalsCurrentValue) {
        val budgetAllocationsInMonth = budgetAllocations.filter { it.month == currentMonth }
        val totalAlocadoNoMes = budgetAllocationsInMonth.sumOf { alloc ->
            allocationMovements.filter { it.dest_budget_allocation_id == alloc.id }.sumOf { it.amount } -
            allocationMovements.filter { it.source_budget_allocation_id == alloc.id }.sumOf { it.amount }
        }
        totalBalance - totalAlocadoNoMes - totalGoalsCurrentValue
    }

    val monthTransactions = remember(transactions, currentMonth) {
        transactions.filter { it.date.startsWith(currentMonth) }
    }

    val totalReceitas = remember(monthTransactions) {
        monthTransactions.filter { it.type == "RECEITA" }.sumOf { it.value }
    }

    val totalDespesas = remember(monthTransactions) {
        monthTransactions.filter { it.type == "DESPESA" }.sumOf { it.value }
    }

    val totalMetasCurrentMonth = remember(allocationMovements, currentMonth, transactions) {
        val moves = allocationMovements.filter {
            val m = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date(it.moved_at))
            m == currentMonth
        }.sumOf {
            if (it.dest_goal_id != null) it.amount
            else if (it.source_goal_id != null) -it.amount
            else 0.0
        }
        val txs = transactions.filter {
            it.type == "META" && it.date.startsWith(currentMonth)
        }.sumOf { it.value }
        moves + txs
    }

    val saldoLiquido = totalReceitas - totalDespesas - totalMetasCurrentMonth

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. PRONTO PARA ATRIBUIR CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (prontoParaAtribuir >= 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Pronto para Atribuir",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (prontoParaAtribuir >= 0) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "R$ %.2f".format(prontoParaAtribuir),
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (prontoParaAtribuir >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // 3. SUMMARY MONTH CARD (Receitas x Despesas x Metas x Saldo Líquido)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        SummaryItem(
                            title = "Receitas",
                            value = "R$ %.2f".format(totalReceitas),
                            color = MaterialTheme.colorScheme.primary,
                            icon = Icons.Default.TrendingUp
                        )
                    }
                    VerticalDivider(modifier = Modifier.height(30.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        SummaryItem(
                            title = "Despesas",
                            value = "R$ %.2f".format(totalDespesas),
                            color = MaterialTheme.colorScheme.error,
                            icon = Icons.Default.TrendingDown
                        )
                    }
                    VerticalDivider(modifier = Modifier.height(30.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        SummaryItem(
                            title = "Metas",
                            value = "R$ %.2f".format(totalMetasCurrentMonth),
                            color = Color(0xFF9B59B6),
                            icon = Icons.Default.Star
                        )
                    }
                    VerticalDivider(modifier = Modifier.height(30.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Column(modifier = Modifier.weight(1.1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        SummaryItem(
                            title = "Saldo Líq.",
                            value = "R$ %.2f".format(saldoLiquido),
                            color = MaterialTheme.colorScheme.secondary,
                            icon = Icons.Default.Done
                        )
                    }
                }
            }
        }

        // 4. LIST OF ACCOUNTS
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Suas Contas e Cartões",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        if (accounts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Nenhuma conta cadastrada.",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Acesse a aba Ajustes -> Contas para gerenciar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            items(accounts) { account ->
                val icon = when (account.type) {
                    "CARTAO_CREDITO" -> Icons.Default.CreditCard
                    "DINHEIRO" -> Icons.Default.Payments
                    else -> Icons.Default.AccountBalance
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedAccountForDetail = account },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    account.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = account.type.replace("_", " "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Text(
                            text = "R$ %.2f".format(account.initial_balance),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (account.type == "CARTAO_CREDITO") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 5. ASSISTANTE IA CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = "IA",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Assistente IA",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            "Disponível em atualizações futuras para ajudar você a analisar limites e gastos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }

    if (selectedAccountForDetail != null) {
        AccountDetailDialog(
            account = selectedAccountForDetail!!,
            viewModel = viewModel,
            onDismiss = { selectedAccountForDetail = null }
        )
    }
}

@Composable
fun SyncStatusBar(
    userId: String,
    syncState: MainViewModel.SyncState,
    onSyncClick: () -> Unit
) {
    val isGuest = userId == "GUEST"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(enabled = !isGuest) { onSyncClick() }
            .padding(8.dp, 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (isGuest) Color.Gray
                        else when (syncState) {
                            is MainViewModel.SyncState.Success -> MaterialTheme.colorScheme.primary
                            is MainViewModel.SyncState.Syncing -> Color.Yellow
                            is MainViewModel.SyncState.Error -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        },
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isGuest) "Modo Convidado (Offline)"
                else when (syncState) {
                    is MainViewModel.SyncState.Syncing -> "Sincronizando..."
                    is MainViewModel.SyncState.Success -> "Sincronizado"
                    is MainViewModel.SyncState.Error -> "Erro ao Sincronizar"
                    else -> "Sincronizado"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!isGuest) {
            Icon(
                Icons.Default.Sync,
                contentDescription = "Sincronizar agora",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SummaryItem(
    title: String,
    value: String,
    color: Color,
    icon: ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
            Spacer(modifier = Modifier.width(4.dp))
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

// --- PLACEHOLDER TAB FOR FUTURE RELEASES ---
@Composable
fun PlaceholderTab(
    title: String,
    icon: ImageVector,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailDialog(
    account: Account,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val userId = viewModel.currentUserId
    val transactions by viewModel.repository.getTransactionsFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by viewModel.repository.getCategoriesFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val subcategories by viewModel.repository.getSubcategoriesFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val installmentPlans by viewModel.repository.getInstallmentPlansFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())

    val accountTransactions = remember(transactions, account) {
        transactions.filter { it.account_id == account.id || it.to_account_id == account.id }
    }

    val accountPlans = remember(installmentPlans, account) {
        installmentPlans.filter { it.account_id == account.id }
    }

    val currentMonth = remember {
        java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Column {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (account.type) {
                        "CARTAO_CREDITO" -> "Cartão de Crédito"
                        "DINHEIRO" -> "Dinheiro"
                        else -> "Conta Corrente"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (account.type == "CARTAO_CREDITO") {
                    // Credit Card details
                    val currentMonthTx = remember(accountTransactions, currentMonth) {
                        accountTransactions.filter { it.date.startsWith(currentMonth) }
                    }
                    val faturaTotal = remember(currentMonthTx) {
                        currentMonthTx.sumOf { if (it.type == "DESPESA") it.value else -it.value }
                    }

                    // Resumo Fatura
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Fatura de ${java.text.SimpleDateFormat("MMMM", java.util.Locale("pt", "BR")).format(java.util.Date()).replaceFirstChar { it.uppercase() }}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "R$ %.2f".format(faturaTotal),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Lista da Fatura
                    Text("Transações deste mês:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (currentMonthTx.isEmpty()) {
                        Text(
                            "Nenhuma despesa registrada nesta fatura.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            currentMonthTx.forEach { tx ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(tx.description, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
                                            val plan = installmentPlans.find { it.id == tx.installment_plan_id }
                                            if (plan != null && tx.installment_number != null) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "${tx.installment_number}/${plan.installments_count}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                        fontSize = 9.sp
                                                    )
                                                }
                                            }
                                        }
                                        Text(tx.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "R$ %.2f".format(tx.value),
                                        fontWeight = FontWeight.Bold,
                                        color = if (tx.type == "DESPESA") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Regular account: Extrato (all transactions)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Saldo Atual",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "R$ %.2f".format(account.initial_balance),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text("Histórico (Extrato):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (accountTransactions.isEmpty()) {
                        Text(
                            "Nenhuma movimentação para esta conta.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            accountTransactions.forEach { tx ->
                                val catName = categories.find { it.id == tx.category_id }?.name
                                val subName = subcategories.find { it.id == tx.subcategory_id }?.name
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(tx.description, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
                                            val plan = installmentPlans.find { it.id == tx.installment_plan_id }
                                            if (plan != null && tx.installment_number != null) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "${tx.installment_number}/${plan.installments_count}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                        fontSize = 9.sp
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = buildString {
                                                append(tx.date)
                                                if (catName != null) {
                                                    append(" • $catName")
                                                    if (subName != null) append("/$subName")
                                                }
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                    val color = when (tx.type) {
                                        "RECEITA" -> MaterialTheme.colorScheme.primary
                                        "DESPESA" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.secondary
                                    }
                                    val sign = when (tx.type) {
                                        "RECEITA" -> "+"
                                        "DESPESA" -> "-"
                                        else -> ""
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "$sign R$ %.2f".format(tx.value),
                                        fontWeight = FontWeight.Bold,
                                        color = color,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                // PARCELAMENTOS ATIVOS SECTION (for both CREDIT_CARD and regular accounts)
                if (accountPlans.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Parcelamentos ativos:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    accountPlans.forEach { plan ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = plan.description,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val categoryName = categories.find { it.id == plan.category_id }?.name
                                        val subcategoryName = subcategories.find { it.id == plan.subcategory_id }?.name
                                        if (categoryName != null) {
                                            Text(
                                                text = buildString {
                                                    append(categoryName)
                                                    if (subcategoryName != null) append(" / $subcategoryName")
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    
                                    val baseValue = (plan.total_value / plan.installments_count).toBigDecimal().setScale(2, java.math.RoundingMode.HALF_UP).toDouble()
                                    Text(
                                        text = "R$ %.2f / mês".format(baseValue),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Progress Calculation
                                val planTxs = remember(transactions, plan) { transactions.filter { it.installment_plan_id == plan.id } }
                                val currentMonthStr = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
                                val paidCount = planTxs.count { it.date.take(7) <= currentMonthStr }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$paidCount de ${plan.installments_count} pagas",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Total: R$ %.2f".format(plan.total_value),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                val progress = if (plan.installments_count > 0) paidCount.toFloat() / plan.installments_count else 0f
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalMonthSelector(
    selectedMonthCalendar: java.util.Calendar,
    onMonthChange: (java.util.Calendar) -> Unit,
    onCurrentMonthClick: () -> Unit
) {
    var showMonthPickerDialog by remember { mutableStateOf(false) }
    val periodFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("pt", "BR")) }
    val periodString = remember(selectedMonthCalendar) {
        periodFormat.format(selectedMonthCalendar.time).replaceFirstChar { it.uppercase() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Arrow Back
        IconButton(onClick = {
            val cal = Calendar.getInstance().apply {
                time = selectedMonthCalendar.time
                add(Calendar.MONTH, -1)
            }
            onMonthChange(cal)
        }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                contentDescription = "Mês anterior",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // Clickable Month Title and Current Month Shortcut
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showMonthPickerDialog = true }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = periodString,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Toque para alterar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Current Month Quick Shortcut Button ("Mês Atual")
            val isCurrentMonthSelected = remember(selectedMonthCalendar) {
                val today = Calendar.getInstance()
                today.get(Calendar.YEAR) == selectedMonthCalendar.get(Calendar.YEAR) &&
                today.get(Calendar.MONTH) == selectedMonthCalendar.get(Calendar.MONTH)
            }
            
            if (!isCurrentMonthSelected) {
                InputChip(
                    selected = false,
                    onClick = onCurrentMonthClick,
                    label = { Text("Mês Atual", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Ir para mês atual",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = InputChipDefaults.inputChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = null,
                    modifier = Modifier.height(28.dp)
                )
            }
        }

        // Arrow Forward
        IconButton(onClick = {
            val cal = Calendar.getInstance().apply {
                time = selectedMonthCalendar.time
                add(Calendar.MONTH, 1)
            }
            onMonthChange(cal)
        }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward, 
                contentDescription = "Próximo mês",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (showMonthPickerDialog) {
        MonthYearPickerDialog(
            currentCalendar = selectedMonthCalendar,
            onDismiss = { showMonthPickerDialog = false },
            onSelected = { year, month ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                }
                onMonthChange(newCal)
                showMonthPickerDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthYearPickerDialog(
    currentCalendar: Calendar,
    onDismiss: () -> Unit,
    onSelected: (year: Int, month: Int) -> Unit
) {
    var selectedYear by remember { mutableStateOf(currentCalendar.get(Calendar.YEAR)) }
    val monthsShort = listOf(
        "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
        "Jul", "Ago", "Set", "Out", "Nov", "Dez"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedYear-- }) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Ano anterior")
                }
                Text(
                    text = "$selectedYear",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = { selectedYear++ }) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Próximo ano")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Selecione o Mês",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                // 3x4 Grid of months
                val chunkedMonths = remember { monthsShort.chunked(3) }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chunkedMonths.forEachIndexed { rowIndex, rowMonths ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowMonths.forEachIndexed { colIndex, monthName ->
                                val monthIndex = rowIndex * 3 + colIndex
                                val isSelected = currentCalendar.get(Calendar.YEAR) == selectedYear &&
                                        currentCalendar.get(Calendar.MONTH) == monthIndex

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                        .clickable {
                                            onSelected(selectedYear, monthIndex)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = monthName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

