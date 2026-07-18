package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AllocationMovement
import com.example.data.model.Goal
import com.example.data.model.Category
import com.example.data.model.Subcategory
import com.example.data.model.BudgetAllocation
import com.example.ui.viewmodel.MainViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val userId = viewModel.currentUserId
    val context = LocalContext.current
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    // Database state flows
    val goals by viewModel.repository.getGoalsFlow(userId).collectAsStateWithLifecycle(emptyList())
    val allocationMovements by viewModel.repository.getAllocationMovementsFlow(userId).collectAsStateWithLifecycle(emptyList())
    val categories by viewModel.repository.getCategoriesFlow(userId).collectAsStateWithLifecycle(emptyList())
    val subcategories by viewModel.repository.getSubcategoriesFlow(userId).collectAsStateWithLifecycle(emptyList())
    val budgetAllocations by viewModel.repository.getBudgetAllocationsFlow(userId).collectAsStateWithLifecycle(emptyList())
    val transactions by viewModel.repository.getTransactionsFlow(userId).collectAsStateWithLifecycle(emptyList())

    // Calculate Ready to Assign balance
    val selectedMonthCalendar by viewModel.selectedMonthCalendar.collectAsStateWithLifecycle()
    val currentMonthStr = remember(selectedMonthCalendar) {
        SimpleDateFormat("yyyy-MM", Locale.US).format(selectedMonthCalendar.time)
    }

    val rawAccounts by viewModel.repository.getAccountsFlow(userId).collectAsStateWithLifecycle(emptyList())
    val rawInitialBalance by remember(rawAccounts) {
        derivedStateOf { rawAccounts.sumOf { it.initial_balance } }
    }

    val revenueTransactionsUpToM by remember(transactions, currentMonthStr) {
        derivedStateOf {
            transactions.filter {
                it.type == "RECEITA" && it.date.length >= 7 && it.date.substring(0, 7) <= currentMonthStr
            }.sumOf { it.value }
        }
    }

    val netAllocatedUpToM by remember(budgetAllocations, allocationMovements, currentMonthStr) {
        derivedStateOf {
            val allocationMonthMap = budgetAllocations.associate { it.id to it.month }
            val outFlow = allocationMovements.filter {
                it.source_budget_allocation_id == null && it.dest_budget_allocation_id != null
            }.sumOf { movement ->
                val destMonth = allocationMonthMap[movement.dest_budget_allocation_id]
                if (destMonth != null && destMonth <= currentMonthStr) movement.amount else 0.0
            }
            val inFlow = allocationMovements.filter {
                it.dest_budget_allocation_id == null && it.source_budget_allocation_id != null
            }.sumOf { movement ->
                val sourceMonth = allocationMonthMap[movement.source_budget_allocation_id]
                if (sourceMonth != null && sourceMonth <= currentMonthStr) movement.amount else 0.0
            }
            outFlow - inFlow
        }
    }

    // Dynamic Goals Balances
    val goalBalances = remember(goals, allocationMovements) {
        goals.associate { goal ->
            val destSum = allocationMovements.filter { it.dest_goal_id == goal.id }.sumOf { it.amount }
            val sourceSum = allocationMovements.filter { it.source_goal_id == goal.id }.sumOf { it.amount }
            goal.id to (destSum - sourceSum)
        }
    }

    val totalGoalsCurrentValue by remember(goalBalances) {
        derivedStateOf { goalBalances.values.sum() }
    }

    val prontoParaAtribuir by remember(rawInitialBalance, revenueTransactionsUpToM, netAllocatedUpToM, totalGoalsCurrentValue) {
        derivedStateOf {
            rawInitialBalance + revenueTransactionsUpToM - netAllocatedUpToM - totalGoalsCurrentValue
        }
    }

    // Master-Detail selection state
    var selectedGoalId by remember { mutableStateOf<Int?>(null) }
    val selectedGoal = remember(selectedGoalId, goals) {
        goals.find { it.id == selectedGoalId }
    }

    // Dialog state
    var showAddGoalDialog by remember { mutableStateOf(false) }
    var showEditGoalDialog by remember { mutableStateOf(false) }
    var showDistributeDialog by remember { mutableStateOf(false) }
    var preSelectedGoalMode by remember { mutableStateOf<String?>(null) } // "APORTAR" or "RETIRAR"

    var targetMovementToEdit by remember { mutableStateOf<AllocationMovement?>(null) }
    var targetMovementToDelete by remember { mutableStateOf<AllocationMovement?>(null) }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (selectedGoal == null) {
            // LIST VIEW (MASTER)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Header Info
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
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
                            Text(
                                "Disponível para Metas",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                currencyFormatter.format(prontoParaAtribuir),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(
                            onClick = { showAddGoalDialog = true },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .size(48.dp)
                                .testTag("add_goal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Nova Meta",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                Text(
                    "Minhas Metas 🎯",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (goals.isEmpty()) {
                    // Empty State View
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(
                                "Nenhuma meta ainda",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Crie objetivos de poupança para prazos específicos e distribua dinheiro dos seus envelopes diretamente para eles.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { showAddGoalDialog = true },
                                modifier = Modifier.testTag("empty_add_goal_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Criar Minha Primeira Meta")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(goals) { goal ->
                            val currentVal = goalBalances[goal.id] ?: 0.0
                            val progress = if (goal.target_value > 0) (currentVal / goal.target_value).toFloat() else 0f
                            val remaining = (goal.target_value - currentVal).coerceAtLeast(0.0)
                            val isReached = currentVal >= goal.target_value

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedGoalId = goal.id }
                                    .testTag("goal_card_${goal.id}"),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .background(Color(goal.color), CircleShape)
                                            )
                                            Text(
                                                goal.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (isReached) {
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text("Alcançada 🎉", fontSize = 11.sp) },
                                                colors = SuggestionChipDefaults.suggestionChipColors(
                                                    containerColor = Color(0xFFE8F5E9),
                                                    labelColor = Color(0xFF2E7D32)
                                                )
                                            )
                                        } else {
                                            Text(
                                                "Prazo: ${goal.deadline}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }

                                    // Progress Section
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Column {
                                            Text(
                                                "Progresso",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                            Text(
                                                "${currencyFormatter.format(currentVal)} / ${currencyFormatter.format(goal.target_value)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Text(
                                            "${(progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    LinearProgressIndicator(
                                        progress = { progress.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = if (isReached) Color(0xFF4CAF50) else Color(goal.color),
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )

                                    if (!isReached) {
                                        Text(
                                            "Falta: ${currencyFormatter.format(remaining)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // DETAIL VIEW (DETAIL)
            val currentVal = goalBalances[selectedGoal.id] ?: 0.0
            val progress = if (selectedGoal.target_value > 0) (currentVal / selectedGoal.target_value).toFloat() else 0f
            val remaining = (selectedGoal.target_value - currentVal).coerceAtLeast(0.0)
            val isReached = currentVal >= selectedGoal.target_value

            // Toast feedback when reached
            var hasToastedReached by remember(selectedGoal.id) { mutableStateOf(false) }
            LaunchedEffect(currentVal) {
                if (isReached && !hasToastedReached) {
                    Toast.makeText(context, "Meta alcançada 🎉", Toast.LENGTH_LONG).show()
                    hasToastedReached = true
                }
            }

            // Filter history for this goal
            val history = remember(allocationMovements, selectedGoal) {
                allocationMovements.filter {
                    it.source_goal_id == selectedGoal.id || it.dest_goal_id == selectedGoal.id
                }.sortedByDescending { it.moved_at }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with Back button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { selectedGoalId = null },
                            modifier = Modifier.testTag("back_to_list")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                        Text(
                            "Detalhe da Meta",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Edit / Delete option
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { showEditGoalDialog = true },
                            modifier = Modifier.testTag("edit_goal_button")
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Editar Meta",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.deleteGoal(selectedGoal) {
                                    selectedGoalId = null
                                }
                            },
                            modifier = Modifier.testTag("delete_goal_button")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Excluir Meta",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Main Info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Title and Indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(Color(selectedGoal.color), CircleShape)
                            )
                            Text(
                                selectedGoal.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Circular or Large Horizontal progress
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Progresso Total",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Black,
                                color = if (isReached) Color(0xFF4CAF50) else Color(selectedGoal.color)
                            )
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                color = if (isReached) Color(0xFF4CAF50) else Color(selectedGoal.color),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }

                        // Split metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Saldo Atual",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Text(
                                    currencyFormatter.format(currentVal),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Meta Alvo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Text(
                                    currencyFormatter.format(selectedGoal.target_value),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Prazo Limite",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Text(
                                    selectedGoal.deadline,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (isReached) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFF2E7D32))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Parabéns! Meta alcançada! 🎉",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        } else {
                            Text(
                                "Faltam ${currencyFormatter.format(remaining)} para atingir seu objetivo.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Deposit & Withdraw buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    preSelectedGoalMode = "APORTAR"
                                    showDistributeDialog = true
                                },
                                modifier = Modifier.weight(1f).testTag("aportar_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Aportar")
                            }

                            OutlinedButton(
                                onClick = {
                                    preSelectedGoalMode = "RETIRAR"
                                    showDistributeDialog = true
                                },
                                modifier = Modifier.weight(1f).testTag("retirar_button"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)
                                )
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retirar")
                            }
                        }
                    }
                }

                // History section
                Text(
                    "Histórico de Movimentações",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Nenhum aporte ou retirada realizado ainda.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history) { movement ->
                            val isAporte = movement.dest_goal_id == selectedGoal.id
                            val (fromLabel, toLabel) = getMovementDirectionText(
                                movement = movement,
                                currentGoalId = selectedGoal.id,
                                categories = categories,
                                subcategories = subcategories,
                                budgetAllocations = budgetAllocations,
                                goals = goals
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (isAporte) "Aporte de: $fromLabel" else "Retirada para: $toLabel",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        val dateStr = remember(movement.moved_at) {
                                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(movement.moved_at))
                                        }
                                        Text(
                                            dateStr,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        if (!movement.note.isNullOrBlank()) {
                                            Text(
                                                "Nota: ${movement.note}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = (if (isAporte) "+" else "-") + currencyFormatter.format(movement.amount),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAporte) Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )
                                        IconButton(
                                            onClick = { targetMovementToEdit = movement },
                                            modifier = Modifier.size(32.dp).testTag("edit_movement_${movement.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Editar Movimentação",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = { targetMovementToDelete = movement },
                                            modifier = Modifier.size(32.dp).testTag("delete_movement_${movement.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Excluir Movimentação",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.error
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

        // DIALOG: ADD NEW GOAL
        if (showAddGoalDialog) {
            AddGoalDialog(
                onDismiss = { showAddGoalDialog = false },
                onSave = { name, target, startDate, deadline, color ->
                    val newGoal = Goal(
                        name = name,
                        target_value = target,
                        start_date = startDate,
                        deadline = deadline,
                        color = color,
                        archived = false,
                        userId = userId
                    )
                    viewModel.insertGoal(newGoal)
                    showAddGoalDialog = false
                }
            )
        }

        // DIALOG: DISTRIBUTE MONEY
        if (showDistributeDialog && selectedGoal != null) {
            DistributeDialog(
                viewModel = viewModel,
                prontoParaAtribuir = prontoParaAtribuir,
                preSelectedPair = null,
                categories = categories,
                subcategories = subcategories,
                allocationInfoMap = emptyMap(),
                sourceMonth = currentMonthStr,
                goals = goals,
                preSelectedGoal = selectedGoal,
                preSelectedGoalMode = preSelectedGoalMode,
                onDismiss = {
                    showDistributeDialog = false
                    preSelectedGoalMode = null
                }
            )
        }

        // DIALOG: EDIT GOAL
        if (showEditGoalDialog && selectedGoal != null) {
            EditGoalDialog(
                goal = selectedGoal,
                onDismiss = { showEditGoalDialog = false },
                onSave = { name, target, startDate, deadline, color ->
                    val updatedGoal = selectedGoal.copy(
                        name = name,
                        target_value = target,
                        start_date = startDate,
                        deadline = deadline,
                        color = color
                    )
                    viewModel.updateGoal(updatedGoal)
                    showEditGoalDialog = false
                }
            )
        }

        // DIALOG: EDIT MOVEMENT
        if (targetMovementToEdit != null) {
            EditMovementDialog(
                movement = targetMovementToEdit!!,
                onDismiss = { targetMovementToEdit = null },
                onSave = { amount, note ->
                    val updatedMovement = targetMovementToEdit!!.copy(
                        amount = amount,
                        note = note
                    )
                    viewModel.updateAllocationMovement(updatedMovement)
                    targetMovementToEdit = null
                }
            )
        }

        // DIALOG: DELETE MOVEMENT CONFIRMATION
        if (targetMovementToDelete != null) {
            AlertDialog(
                onDismissRequest = { targetMovementToDelete = null },
                title = { Text("Excluir Movimentação ⚠️", fontWeight = FontWeight.Bold) },
                text = { Text("Tem certeza que deseja excluir esta movimentação? O saldo da meta e o valor disponível serão recalculados.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteAllocationMovement(targetMovementToDelete!!)
                            targetMovementToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Excluir")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { targetMovementToDelete = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun AddGoalDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double, String, String, Int) -> Unit // (name, target, start_date, deadline, color)
) {
    var name by remember { mutableStateOf("") }
    var targetValue by remember { mutableStateOf("") }

    val sdf = remember { java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US) }
    val displaySdf = remember { java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("pt", "BR")) }

    var startCalendar by remember { mutableStateOf(java.util.Calendar.getInstance()) }
    var deadlineCalendar by remember { 
        mutableStateOf(java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, 12) }) 
    }

    var showStartPicker by remember { mutableStateOf(false) }
    var showDeadlinePicker by remember { mutableStateOf(false) }

    val presetColors = listOf(
        0xFF2ECC71, // Emerald Green
        0xFF3498DB, // Blue
        0xFFE74C3C, // Coral Red
        0xFF9B59B6, // Amethyst Purple
        0xFFF1C40F, // Sunflower Yellow
        0xFF1ABC9C, // Turquoise Blue
        0xFFE67E22  // Orange
    )
    var selectedColor by remember { mutableStateOf(presetColors[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova Meta 🎯", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da Meta") },
                    modifier = Modifier.fillMaxWidth().testTag("add_goal_name_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = targetValue,
                    onValueChange = { targetValue = it },
                    label = { Text("Valor Alvo (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("add_goal_target_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                // Month selection for start date
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showStartPicker = true }
                ) {
                    OutlinedTextField(
                        value = displaySdf.format(startCalendar.time).replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        enabled = false,
                        label = { Text("Mês de Início") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        trailingIcon = {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Selecionar início")
                        }
                    )
                }

                // Month selection for deadline
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeadlinePicker = true }
                ) {
                    OutlinedTextField(
                        value = displaySdf.format(deadlineCalendar.time).replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        enabled = false,
                        label = { Text("Mês Limite (Prazo)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        trailingIcon = {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Selecionar prazo")
                        }
                    )
                }

                // Color Preset Picker
                Text(
                    "Cor da Meta",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    presetColors.forEach { colorVal ->
                        val color = Color(colorVal)
                        val isSelected = selectedColor == colorVal
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = colorVal }
                                .padding(2.dp)
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val amount = targetValue.toDoubleOrNull() ?: 0.0
            val isValid = name.isNotBlank() && amount > 0.0

            Button(
                onClick = { 
                    val startStr = sdf.format(startCalendar.time)
                    val deadlineStr = sdf.format(deadlineCalendar.time)
                    onSave(name, amount, startStr, deadlineStr, selectedColor.toInt()) 
                },
                enabled = isValid,
                modifier = Modifier.testTag("confirm_add_goal")
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )

    if (showStartPicker) {
        MonthYearPickerDialog(
            currentCalendar = startCalendar,
            onDismiss = { showStartPicker = false },
            onSelected = { year, month ->
                startCalendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, year)
                    set(java.util.Calendar.MONTH, month)
                }
                showStartPicker = false
            }
        )
    }

    if (showDeadlinePicker) {
        MonthYearPickerDialog(
            currentCalendar = deadlineCalendar,
            onDismiss = { showDeadlinePicker = false },
            onSelected = { year, month ->
                deadlineCalendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, year)
                    set(java.util.Calendar.MONTH, month)
                }
                showDeadlinePicker = false
            }
        )
    }
}

private fun getMovementDirectionText(
    movement: AllocationMovement,
    currentGoalId: Int,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    budgetAllocations: List<BudgetAllocation>,
    goals: List<Goal>
): Pair<String, String> {
    val allocationMap = budgetAllocations.associateBy { it.id }

    fun getAllocationLabel(allocId: Int?): String {
        if (allocId == null) return "Pronto para Atribuir"
        val alloc = allocationMap[allocId] ?: return "Envelope"
        val cat = categories.firstOrNull { it.id == alloc.category_id }?.name ?: "Envelope"
        val sub = subcategories.firstOrNull { it.id == alloc.subcategory_id }?.name
        return if (sub != null) "$cat > $sub" else cat
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

    return Pair(fromLabel, toLabel)
}

@Composable
fun EditGoalDialog(
    goal: Goal,
    onDismiss: () -> Unit,
    onSave: (String, Double, String, String, Int) -> Unit // (name, target, start_date, deadline, color)
) {
    var name by remember { mutableStateOf(goal.name) }
    var targetValue by remember { mutableStateOf(goal.target_value.toString()) }

    val sdf = remember { java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US) }
    val displaySdf = remember { java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("pt", "BR")) }

    var startCalendar by remember { 
        mutableStateOf(java.util.Calendar.getInstance().apply {
            try {
                time = sdf.parse(goal.start_date) ?: java.util.Date()
            } catch (e: Exception) {}
        })
    }
    var deadlineCalendar by remember { 
        mutableStateOf(java.util.Calendar.getInstance().apply {
            try {
                time = sdf.parse(goal.deadline) ?: java.util.Date()
            } catch (e: Exception) {}
        })
    }

    var showStartPicker by remember { mutableStateOf(false) }
    var showDeadlinePicker by remember { mutableStateOf(false) }

    val presetColors = listOf(
        0xFF2ECC71, // Emerald Green
        0xFF3498DB, // Blue
        0xFFE74C3C, // Coral Red
        0xFF9B59B6, // Amethyst Purple
        0xFFF1C40F, // Sunflower Yellow
        0xFF1ABC9C, // Turquoise Blue
        0xFFE67E22  // Orange
    )
    var selectedColor by remember { mutableStateOf(goal.color.toLong()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Meta 🎯", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da Meta") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_goal_name_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = targetValue,
                    onValueChange = { targetValue = it },
                    label = { Text("Valor Alvo (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("edit_goal_target_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                // Month selection for start date
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showStartPicker = true }
                ) {
                    OutlinedTextField(
                        value = displaySdf.format(startCalendar.time).replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        enabled = false,
                        label = { Text("Mês de Início") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        trailingIcon = {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Selecionar início")
                        }
                    )
                }

                // Month selection for deadline
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeadlinePicker = true }
                ) {
                    OutlinedTextField(
                        value = displaySdf.format(deadlineCalendar.time).replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        enabled = false,
                        label = { Text("Mês Limite (Prazo)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        trailingIcon = {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Selecionar prazo")
                        }
                    )
                }

                // Color Preset Picker
                Text(
                    "Cor da Meta",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    presetColors.forEach { colorVal ->
                        val color = Color(colorVal)
                        val isSelected = selectedColor == colorVal
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = colorVal }
                                .padding(2.dp)
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val amount = targetValue.toDoubleOrNull() ?: 0.0
            val isValid = name.isNotBlank() && amount > 0.0

            Button(
                onClick = { 
                    val startStr = sdf.format(startCalendar.time)
                    val deadlineStr = sdf.format(deadlineCalendar.time)
                    onSave(name, amount, startStr, deadlineStr, selectedColor.toInt()) 
                },
                enabled = isValid,
                modifier = Modifier.testTag("confirm_edit_goal")
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )

    if (showStartPicker) {
        MonthYearPickerDialog(
            currentCalendar = startCalendar,
            onDismiss = { showStartPicker = false },
            onSelected = { year, month ->
                startCalendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, year)
                    set(java.util.Calendar.MONTH, month)
                }
                showStartPicker = false
            }
        )
    }

    if (showDeadlinePicker) {
        MonthYearPickerDialog(
            currentCalendar = deadlineCalendar,
            onDismiss = { showDeadlinePicker = false },
            onSelected = { year, month ->
                deadlineCalendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, year)
                    set(java.util.Calendar.MONTH, month)
                }
                showDeadlinePicker = false
            }
        )
    }
}

@Composable
fun EditMovementDialog(
    movement: AllocationMovement,
    onDismiss: () -> Unit,
    onSave: (Double, String?) -> Unit
) {
    var amountText by remember { mutableStateOf(movement.amount.toString()) }
    var note by remember { mutableStateOf(movement.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Movimentação 📝", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Valor (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("edit_movement_amount_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota / Observação") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_movement_note_input"),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            val amount = amountText.toDoubleOrNull() ?: 0.0
            val isValid = amount > 0.0

            Button(
                onClick = { onSave(amount, note.ifBlank { null }) },
                enabled = isValid,
                modifier = Modifier.testTag("confirm_edit_movement")
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
