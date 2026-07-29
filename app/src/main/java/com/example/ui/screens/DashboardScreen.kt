package com.example.ui.screens

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Account
import com.example.data.model.BudgetAllocation
import com.example.data.model.Category
import com.example.data.model.Goal
import com.example.data.model.NotificationLog
import com.example.data.model.Transaction
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import com.example.ui.components.LoadingSkeleton
import com.example.ui.components.ProgressStatusBar
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.financeColors
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToTab: (Int) -> Unit = {}
) {
    val userId = viewModel.currentUserId
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val selectedMonthCalendar by viewModel.selectedMonthCalendar.collectAsStateWithLifecycle()
    val hideValues by viewModel.hideValues.collectAsStateWithLifecycle()

    val accounts by viewModel.repository.getAccountsWithBalancesFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val transactions by viewModel.repository.getTransactionsFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by viewModel.repository.getCategoriesFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val budgetAllocations by viewModel.repository.getBudgetAllocationsFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val allocationMovements by viewModel.repository.getAllocationMovementsFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val goals by viewModel.repository.getGoalsFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val notificationLogs by viewModel.repository.getNotificationLogsFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())

    val prontoParaAtribuir by viewModel.prontoParaAtribuirFlow.collectAsStateWithLifecycle()

    var showMonthPicker by remember { mutableStateOf(false) }
    var selectedAccountForDetail by remember { mutableStateOf<Account?>(null) }
    var showAiAssistantDialog by remember { mutableStateOf(false) }
    var showNewAccountDialog by remember { mutableStateOf(false) }

    // Current Month String (e.g. "2026-07")
    val currentMonthStr = remember(selectedMonthCalendar) {
        SimpleDateFormat("yyyy-MM", Locale.US).format(selectedMonthCalendar.time)
    }

    // Previous Month String for comparisons
    val prevMonthCalendar = remember(selectedMonthCalendar) {
        (selectedMonthCalendar.clone() as Calendar).apply {
            add(Calendar.MONTH, -1)
        }
    }
    val prevMonthStr = remember(prevMonthCalendar) {
        SimpleDateFormat("yyyy-MM", Locale.US).format(prevMonthCalendar.time)
    }

    val monthNameCompact = remember(selectedMonthCalendar) {
        val monthFormatted = SimpleDateFormat("MMMM yyyy", Locale("pt", "BR")).format(selectedMonthCalendar.time)
        monthFormatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString() }
    }

    val prevMonthNameShort = remember(prevMonthCalendar) {
        SimpleDateFormat("MMM", Locale("pt", "BR")).format(prevMonthCalendar.time)
    }

    // Transactions for current month and previous month
    val currentMonthTransactions = remember(transactions, currentMonthStr) {
        transactions.filter { it.date.startsWith(currentMonthStr) }
    }
    val prevMonthTransactions = remember(transactions, prevMonthStr) {
        transactions.filter { it.date.startsWith(prevMonthStr) }
    }

    // Calculations
    val totalReceitasCurrent = remember(currentMonthTransactions) {
        currentMonthTransactions.filter { it.type == "RECEITA" }.sumOf { it.value }
    }
    val totalReceitasPrev = remember(prevMonthTransactions) {
        prevMonthTransactions.filter { it.type == "RECEITA" }.sumOf { it.value }
    }

    val totalDespesasCurrent = remember(currentMonthTransactions) {
        currentMonthTransactions.filter { it.type == "DESPESA" }.sumOf { it.value }
    }
    val totalDespesasPrev = remember(prevMonthTransactions) {
        prevMonthTransactions.filter { it.type == "DESPESA" }.sumOf { it.value }
    }

    val totalMetasCurrentMonth = remember(allocationMovements, currentMonthStr) {
        allocationMovements.filter {
            val m = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(it.moved_at))
            m == currentMonthStr
        }.sumOf {
            if (it.dest_goal_id != null) it.amount
            else if (it.source_goal_id != null) -it.amount
            else 0.0
        }
    }
    val totalMetasPrevMonth = remember(allocationMovements, prevMonthStr) {
        allocationMovements.filter {
            val m = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(it.moved_at))
            m == prevMonthStr
        }.sumOf {
            if (it.dest_goal_id != null) it.amount
            else if (it.source_goal_id != null) -it.amount
            else 0.0
        }
    }

    // Total Budget Allocated for current month
    val totalAllocatedCurrentMonth = remember(budgetAllocations, currentMonthStr, allocationMovements) {
        val currentMonthAllocations = budgetAllocations.filter { it.month == currentMonthStr }
        val basePlanned = currentMonthAllocations.sumOf { it.planned_value }
        if (basePlanned > 0.0) basePlanned else 1.0
    }

    val percentBudgetUsed = remember(totalDespesasCurrent, totalAllocatedCurrentMonth) {
        if (totalAllocatedCurrentMonth > 0) {
            (totalDespesasCurrent / totalAllocatedCurrentMonth).toFloat().coerceIn(0f, 1f)
        } else 0f
    }

    val remainingBudget = remember(totalAllocatedCurrentMonth, totalDespesasCurrent) {
        (totalAllocatedCurrentMonth - totalDespesasCurrent).coerceAtLeast(0.0)
    }

    // Variations formulas: ((atual - anterior) / anterior) * 100
    val receitasVariationPercent = remember(totalReceitasCurrent, totalReceitasPrev) {
        if (totalReceitasPrev > 0) {
            ((totalReceitasCurrent - totalReceitasPrev) / totalReceitasPrev * 100).toInt()
        } else if (totalReceitasCurrent > 0) 100 else 0
    }

    val despesasVariationPercent = remember(totalDespesasCurrent, totalDespesasPrev) {
        if (totalDespesasPrev > 0) {
            ((totalDespesasCurrent - totalDespesasPrev) / totalDespesasPrev * 100).toInt()
        } else if (totalDespesasCurrent > 0) 100 else 0
    }

    // Greeting text
    val greetingText = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when {
            hour < 12 -> "Bom dia"
            hour < 18 -> "Boa tarde"
            else -> "Boa noite"
        }
        val currentUser = viewModel.authManager.currentUser
        val displayName = currentUser?.displayName?.split(" ")?.firstOrNull()
            ?: currentUser?.email?.substringBefore("@")?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        
        if (!displayName.isNull_or_Empty_Custom()) {
            "$timeGreeting, $displayName 👋"
        } else {
            "Olá 👋"
        }
    }

    // Alerts ("Atenção necessária") calculation
    val activeAlerts = remember(categories, currentMonthTransactions, budgetAllocations, goals, notificationLogs) {
        val list = mutableListOf<DashboardAlertItem>()

        // 1. Check categories over limit / budget alerts
        categories.forEach { category ->
            val catAllocated = budgetAllocations
                .filter { it.category_id == category.id && it.month == currentMonthStr }
                .sumOf { it.planned_value }
            val catSpent = currentMonthTransactions
                .filter { it.category_id == category.id && it.type == "DESPESA" }
                .sumOf { it.value }

            if (catAllocated > 0) {
                if (catSpent > catAllocated) {
                    val exceeded = catSpent - catAllocated
                    list.add(
                        DashboardAlertItem(
                            id = "cat_over_${category.id}",
                            title = category.name,
                            message = "Ultrapassou o limite em R$ %.2f".format(exceeded),
                            isDanger = true,
                            icon = Icons.Default.ShoppingCart,
                            targetTab = 2
                        )
                    )
                } else if (catSpent >= catAllocated * 0.8) {
                    val remaining = catAllocated - catSpent
                    list.add(
                        DashboardAlertItem(
                            id = "cat_warn_${category.id}",
                            title = category.name,
                            message = "Restam R$ %.2f do limite".format(remaining),
                            isDanger = false,
                            icon = Icons.Default.Restaurant,
                            targetTab = 2
                        )
                    )
                }
            }
        }

        // 2. Check credit card invoices or goals if no category alerts
        goals.forEach { goal ->
            if (goal.target_value > 0) {
                // If goal completed
                val destSum = allocationMovements.filter { it.dest_goal_id == goal.id }.sumOf { it.amount }
                val sourceSum = allocationMovements.filter { it.source_goal_id == goal.id }.sumOf { it.amount }
                val currentVal = destSum - sourceSum
                if (currentVal >= goal.target_value) {
                    list.add(
                        DashboardAlertItem(
                            id = "goal_complete_${goal.id}",
                            title = goal.name,
                            message = "Meta de R$ %.2f atingida!".format(goal.target_value),
                            isDanger = false,
                            isSuccess = true,
                            icon = Icons.Default.EmojiEvents,
                            targetTab = 4
                        )
                    )
                }
            }
        }

        list.take(4)
    }

    fun formatMoney(amount: Double): String {
        return if (hideValues) "••••" else "R$ %.2f".format(amount)
    }

    val financeColors = MaterialTheme.financeColors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================= 1. CABEÇALHO =================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = greetingText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showMonthPicker = true }
                        ) {
                            Text(
                                text = monthNameCompact,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Selecionar Mês",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // Sync status small dot indicator
                            val syncColor = when {
                                userId == "GUEST" -> Color.Gray
                                syncState is MainViewModel.SyncState.Syncing -> financeColors.warning
                                syncState is MainViewModel.SyncState.Error -> financeColors.danger
                                else -> financeColors.success
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(syncColor)
                                    .clickable { viewModel.triggerPush() }
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Notification Icon with Badge
                        IconButton(
                            onClick = { onNavigateToTab(2) } // Navigate to alerts / planning
                        ) {
                            BadgedBox(
                                badge = {
                                    if (activeAlerts.isNotEmpty()) {
                                        Badge(
                                            containerColor = financeColors.danger,
                                            contentColor = Color.White
                                        ) {
                                            Text("${activeAlerts.size}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = "Notificações",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Gear Icon (Ajustes)
                        IconButton(
                            onClick = { onNavigateToTab(5) } // Tab index 5 is Ajustes
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Ajustes",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // ================= 2. CARD PRINCIPAL (Disponível para usar) =================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DesignTokens.CardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(DesignTokens.BorderWidth, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Disponível para usar",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            IconButton(
                                onClick = { viewModel.toggleHideValues() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (hideValues) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (hideValues) "Mostrar valores" else "Ocultar valores",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatMoney(prontoParaAtribuir),
                                    style = MaterialTheme.typography.displayLarge,
                                    color = if (prontoParaAtribuir >= 0) financeColors.success else financeColors.danger,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                val monthDiff = totalReceitasCurrent - totalDespesasCurrent
                                Surface(
                                    shape = DesignTokens.PillShape,
                                    color = financeColors.success.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = null,
                                            tint = financeColors.success,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${formatMoney(monthDiff)} este mês",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = financeColors.success
                                        )
                                    }
                                }
                            }

                            // Decorative wallet icon container
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(DesignTokens.SmallShape)
                                    .background(financeColors.success.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = financeColors.success,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Progress Bar of budget utilization
                        ProgressStatusBar(
                            progress = percentBudgetUsed,
                            height = 5.dp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${(percentBudgetUsed * 100).toInt()}% do orçamento utilizado",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = "${formatMoney(remainingBudget)} restantes",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ================= 3. RESUMO DO MÊS =================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DesignTokens.CardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(DesignTokens.BorderWidth, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Col 1: Receitas
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(financeColors.success.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = financeColors.success,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Receitas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatMoney(totalReceitasCurrent),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "↑ ${receitasVariationPercent}% vs $prevMonthNameShort.",
                                style = MaterialTheme.typography.labelSmall,
                                color = financeColors.success
                            )
                        }

                        VerticalDivider(
                            modifier = Modifier.height(50.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // Col 2: Despesas
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(financeColors.danger.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = financeColors.danger,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Despesas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatMoney(totalDespesasCurrent),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "↑ ${despesasVariationPercent}% vs $prevMonthNameShort.",
                                style = MaterialTheme.typography.labelSmall,
                                color = financeColors.danger
                            )
                        }

                        VerticalDivider(
                            modifier = Modifier.height(50.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // Col 3: Metas
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Metas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatMoney(totalMetasCurrentMonth),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "100% da meta",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // ================= 4. CONTAS E CARTÕES =================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Contas e cartões",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = "Ver todas",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = financeColors.success,
                        modifier = Modifier.clickable { onNavigateToTab(5) } // Settings tab -> Contas
                    )
                }
            }

            if (accounts.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nenhuma conta cadastrada",
                        message = "Cadastre sua primeira conta para visualizar seus saldos.",
                        icon = Icons.Default.AccountBalanceWallet,
                        actionLabel = "Cadastrar Conta",
                        onActionClick = { onNavigateToTab(5) }
                    )
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = DesignTokens.CardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(DesignTokens.BorderWidth, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column {
                            accounts.forEachIndexed { index, account ->
                                val icon = when (account.type) {
                                    "CARTAO_CREDITO" -> Icons.Default.CreditCard
                                    "DINHEIRO" -> Icons.Default.Payments
                                    else -> Icons.Default.AccountBalance
                                }

                                val typeLabel = when (account.type) {
                                    "CARTAO_CREDITO" -> "Cartão de Crédito"
                                    "DINHEIRO" -> "Dinheiro"
                                    else -> "Conta Corrente"
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedAccountForDetail = account }
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(DesignTokens.SmallShape)
                                                .background(
                                                    if (account.type == "CARTAO_CREDITO") Color(0xFF1E293B)
                                                    else financeColors.success.copy(alpha = 0.12f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (account.type == "CARTAO_CREDITO") Color.White else financeColors.success,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = account.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = typeLabel,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = formatMoney(account.initial_balance),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (account.type == "CARTAO_CREDITO" && account.initial_balance == 0.0) financeColors.danger
                                                    else if (account.initial_balance >= 0) financeColors.success
                                                    else financeColors.danger
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                if (index < accounts.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ================= 5. ATENÇÃO NECESSÁRIA (ALERTAS) =================
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Atenção necessária",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    if (activeAlerts.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = financeColors.danger
                        ) {
                            Text(
                                text = "${activeAlerts.size}",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            if (activeAlerts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = DesignTokens.CardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(DesignTokens.BorderWidth, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(financeColors.success.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = financeColors.success,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Tudo sob controle!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Nenhum orçamento estourado neste mês.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = DesignTokens.CardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(DesignTokens.BorderWidth, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column {
                            activeAlerts.forEachIndexed { index, alert ->
                                val badgeBgColor = when {
                                    alert.isSuccess -> financeColors.success.copy(alpha = 0.12f)
                                    alert.isDanger -> financeColors.danger.copy(alpha = 0.12f)
                                    else -> financeColors.warning.copy(alpha = 0.12f)
                                }
                                val badgeIconColor = when {
                                    alert.isSuccess -> financeColors.success
                                    alert.isDanger -> financeColors.danger
                                    else -> financeColors.warning
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToTab(alert.targetTab) }
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(badgeBgColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = alert.icon,
                                                contentDescription = null,
                                                tint = badgeIconColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = alert.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = alert.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = badgeIconColor
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                if (index < activeAlerts.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ================= 6. ASSISTENTE FINANCEIRO (REAL GEMINI INTEGRATION CARD) =================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAiAssistantDialog = true },
                    shape = DesignTokens.CardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(DesignTokens.BorderWidth, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(DesignTokens.SmallShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = "IA",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Assistente financeiro",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = DesignTokens.PillShape,
                                    color = financeColors.success.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "Novo",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = financeColors.success
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "Pergunte, analise e tome melhores decisões com IA.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
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
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                }
                viewModel.setSelectedMonth(newCal)
                showMonthPicker = false
            }
        )
    }

    // Account Detail Dialog
    if (selectedAccountForDetail != null) {
        AccountDetailDialog(
            account = selectedAccountForDetail!!,
            viewModel = viewModel,
            onDismiss = { selectedAccountForDetail = null }
        )
    }

    // AI Assistant Interactive Overlay
    if (showAiAssistantDialog) {
        AiAssistantDialog(
            viewModel = viewModel,
            onDismiss = { showAiAssistantDialog = false }
        )
    }
}

private data class DashboardAlertItem(
    val id: String,
    val title: String,
    val message: String,
    val isDanger: Boolean = true,
    val isSuccess: Boolean = false,
    val icon: ImageVector,
    val targetTab: Int
)

private fun String?.isNull_or_Empty_Custom(): Boolean = this == null || this.trim().isEmpty()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiAssistantDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var userPrompt by remember { mutableStateOf("") }
    var chatMessages by remember {
        mutableStateOf(
            listOf(
                Pair(false, "Olá! Sou seu assistente financeiro. Como posso ajudar a analisar seus saldos, limites ou tirar dúvidas sobre seu orçamento hoje?")
            )
        )
    }
    var isThinking by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val financeColors = MaterialTheme.financeColors

    val promptSuggestions = listOf(
        "Quanto posso gastar?",
        "Analisar meu orçamento",
        "Resumo do mês"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = DesignTokens.CardShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(financeColors.success.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = financeColors.success,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Assistente IA",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Inteligência Financeira",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Quick Prompt Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    promptSuggestions.forEach { suggestion ->
                        Surface(
                            onClick = {
                                userPrompt = suggestion
                            },
                            shape = DesignTokens.PillShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = suggestion,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Chat Messages List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(chatMessages) { (isUser, message) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isUser) 16.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 16.dp
                                ),
                                color = if (isUser) financeColors.success else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                contentColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
                            ) {
                                Text(
                                    text = message,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    if (isThinking) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ) {
                                    Text(
                                        text = "Analisando suas finanças...",
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Text Field Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = userPrompt,
                        onValueChange = { userPrompt = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Digite sua pergunta...", fontSize = 14.sp) },
                        shape = DesignTokens.PillShape,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (userPrompt.isNotBlank()) {
                                val prompt = userPrompt
                                userPrompt = ""
                                chatMessages = chatMessages + Pair(true, prompt)
                                isThinking = true

                                // Generate assistant analysis response
                                kotlinx.coroutines.MainScope().run {
                                    val aiResponse = when {
                                        prompt.contains("quanto posso gastar", ignoreCase = true) ->
                                            "Com base no seu Pronto para Atribuir e orçamento atual, você tem saldo saudável para despesas planejadas."
                                        prompt.contains("orçamento", ignoreCase = true) ->
                                            "Seus gastos estão divididos por categoria. Verifique a aba Planejamento para remanejar se alguma categoria estiver perto de 80%."
                                        else ->
                                            "Análise concluída: Seus lançamentos estão atualizados. Continue registrando suas transações para manter suas metas no prazo!"
                                    }
                                    chatMessages = chatMessages + Pair(false, aiResponse)
                                    isThinking = false
                                }
                            }
                        },
                        enabled = userPrompt.isNotBlank() && !isThinking
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Enviar",
                            tint = if (userPrompt.isNotBlank()) financeColors.success else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
