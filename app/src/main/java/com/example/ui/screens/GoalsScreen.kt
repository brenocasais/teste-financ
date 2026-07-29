package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
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

    // Month selection state
    val selectedMonthCalendar by viewModel.selectedMonthCalendar.collectAsStateWithLifecycle()
    val currentMonthStr = remember(selectedMonthCalendar) {
        SimpleDateFormat("yyyy-MM", Locale.US).format(selectedMonthCalendar.time)
    }
    val displayMonthStr = remember(selectedMonthCalendar) {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale("pt", "BR"))
        sdf.format(selectedMonthCalendar.time).replaceFirstChar { it.uppercase() }
    }

    val prontoParaAtribuir by viewModel.prontoParaAtribuirFlow.collectAsStateWithLifecycle()

    // Dynamic Goals Balances (derived ONLY from AllocationMovement)
    val goalBalances = remember(goals, allocationMovements) {
        goals.associate { goal ->
            val destSum = allocationMovements.filter { it.dest_goal_id == goal.id }.sumOf { it.amount }
            val sourceSum = allocationMovements.filter { it.source_goal_id == goal.id }.sumOf { it.amount }
            goal.id to (destSum - sourceSum)
        }
    }

    // Color tokens matching the app's visual system
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bgColor = if (isDark) Color(0xFF0D1315) else Color(0xFFFAFAFB)
    val cardBgColor = if (isDark) Color(0xFF172022) else Color(0xFFFFFFFF)
    val cardBorderColor = if (isDark) Color(0xFF283438) else Color(0xFFECEFF1)
    val primaryTextColor = if (isDark) Color(0xFFF5F7F7) else Color(0xFF111827)
    val secondaryTextColor = if (isDark) Color(0xFFA9B1B1) else Color(0xFF6B7280)
    val greenColor = if (isDark) Color(0xFF39D47A) else Color(0xFF22A45D)
    val redColor = if (isDark) Color(0xFFFF4D55) else Color(0xFFEF4444)
    val grayColor = if (isDark) Color(0xFF8E999B) else Color(0xFF9FA9AB)

    // Filter chip selection state
    var selectedFilter by remember { mutableStateOf("TODAS") }

    // Master-Detail selection state
    var selectedGoalId by remember { mutableStateOf<Int?>(null) }
    val selectedGoal = remember(selectedGoalId, goals) {
        goals.find { it.id == selectedGoalId }
    }

    // Dialog state
    var showAddGoalDialog by remember { mutableStateOf(false) }
    var showEditGoalDialog by remember { mutableStateOf(false) }
    var showDistributeDialog by remember { mutableStateOf(false) }
    var showMonthPickerHeader by remember { mutableStateOf(false) }
    var preSelectedGoalMode by remember { mutableStateOf<String?>(null) } // "APORTAR" or "RETIRAR"

    var targetMovementToEdit by remember { mutableStateOf<AllocationMovement?>(null) }
    var targetMovementToDelete by remember { mutableStateOf<AllocationMovement?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (selectedGoal == null) {
            // LIST VIEW (MASTER)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Header: Title "Metas" + Compact Month Selector on top right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Metas",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )

                    // Seletor compacto do mês (sem card grande)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showMonthPickerHeader = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayMonthStr,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Selecionar mês",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Card de Resumo
                val totalSaved = goalBalances.values.sum()
                val activeGoalsCount = goals.count { !it.archived }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Column {
                            // Linha 1: Título e valor principal
                            Text(
                                text = "Disponível para metas",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = secondaryTextColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currencyFormatter.format(prontoParaAtribuir),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = greenColor
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Variação do mês / indicador
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(greenColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "↑  + R$ 143 este mês",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = greenColor
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Linha Inferior: Info secundária + Botão Nova meta
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(greenColor.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Balance,
                                            contentDescription = null,
                                            tint = greenColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "$activeGoalsCount metas ativas",
                                            fontSize = 12.sp,
                                            color = secondaryTextColor
                                        )
                                        Text(
                                            text = "${currencyFormatter.format(totalSaved)} guardados",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryTextColor
                                        )
                                    }
                                }

                                Button(
                                    onClick = { showAddGoalDialog = true },
                                    modifier = Modifier
                                        .height(42.dp)
                                        .testTag("add_goal_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = greenColor,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Nova meta",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Target graphic / Illustration on top right
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(greenColor.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = "Ilustração de alvo",
                                tint = greenColor,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                // Chips de Filtro
                val filters = listOf(
                    "TODAS" to "Todas",
                    "EM_ANDAMENTO" to "Em andamento",
                    "CONCLUIDAS" to "Concluídas",
                    "PAUSADAS" to "Pausadas"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filters.forEach { (key, label) ->
                        val isSelected = selectedFilter == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(19.dp))
                                .background(if (isSelected) greenColor.copy(alpha = 0.15f) else cardBgColor)
                                .border(
                                    1.dp,
                                    if (isSelected) greenColor else cardBorderColor,
                                    RoundedCornerShape(19.dp)
                                )
                                .clickable { selectedFilter = key },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) greenColor else secondaryTextColor,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Filtragem das metas
                val filteredGoals = remember(goals, goalBalances, selectedFilter, currentMonthStr) {
                    goals.filter { goal ->
                        val currentVal = goalBalances[goal.id] ?: 0.0
                        val isReached = currentVal >= goal.target_value
                        val isPaused = goal.archived

                        when (selectedFilter) {
                            "EM_ANDAMENTO" -> !isReached && !isPaused
                            "CONCLUIDAS" -> isReached
                            "PAUSADAS" -> isPaused
                            else -> true
                        }
                    }
                }

                if (filteredGoals.isEmpty()) {
                    // Estado Vazio
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
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(greenColor.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GpsFixed,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = greenColor
                                )
                            }
                            Text(
                                text = "Você ainda não criou nenhuma meta.",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Crie uma meta para acompanhar seus objetivos financeiros.",
                                fontSize = 13.5.sp,
                                color = secondaryTextColor,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { showAddGoalDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = greenColor),
                                modifier = Modifier.testTag("empty_add_goal_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Criar primeira meta", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Lista de Cards de Meta
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredGoals, key = { it.id }) { goal ->
                            val currentVal = goalBalances[goal.id] ?: 0.0
                            val progress = if (goal.target_value > 0) (currentVal / goal.target_value).toFloat() else 0f
                            val isReached = currentVal >= goal.target_value
                            val isPaused = goal.archived
                            val isPastDeadline = isDeadlinePassed(goal.deadline, currentMonthStr)

                            // Status definition: CONCLUIDA = green, PAUSADA = gray, ATRASADA = RED, EM_ANDAMENTO = green (NEVER BLUE)
                            val (statusText, statusBg, statusTextColor) = when {
                                isReached -> Triple("Concluída", greenColor.copy(alpha = 0.15f), greenColor)
                                isPaused -> Triple("Pausada", grayColor.copy(alpha = 0.15f), grayColor)
                                isPastDeadline -> Triple("Atrasada", redColor.copy(alpha = 0.15f), redColor)
                                else -> Triple("No prazo", greenColor.copy(alpha = 0.15f), greenColor)
                            }

                            val goalIcon = remember(goal.name) { getGoalIcon(goal.name) }
                            val goalColor = remember(goal.color) { Color(goal.color) }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedGoalId = goal.id }
                                    .testTag("goal_card_${goal.id}"),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                border = BorderStroke(1.dp, cardBorderColor)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Linha 1: Ícone + Nome + Prazo + Chip de Status
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(goalColor.copy(alpha = 0.18f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = goalIcon,
                                                    contentDescription = null,
                                                    tint = goalColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Column {
                                                Text(
                                                    text = goal.name,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = primaryTextColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (goal.deadline.isNotBlank()) {
                                                    Text(
                                                        text = formatMonthDeadline(goal.deadline),
                                                        fontSize = 12.5.sp,
                                                        color = secondaryTextColor
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Status Chip
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(statusBg)
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = statusText,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = statusTextColor
                                                )
                                            }

                                            // Botão para Ocultar / Exibir Meta
                                            IconButton(
                                                onClick = {
                                                    val newArchived = !goal.archived
                                                    viewModel.updateGoal(goal.copy(archived = newArchived))
                                                    val msg = if (newArchived) "Meta ocultada/pausada" else "Meta visível/reativada"
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("toggle_hide_goal_${goal.id}")
                                            ) {
                                                Icon(
                                                    imageVector = if (goal.archived) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = if (goal.archived) "Exibir meta" else "Ocultar meta",
                                                    tint = secondaryTextColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Linha 2: Valor Atual / Valor Alvo + Percentual
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(
                                                text = currencyFormatter.format(currentVal),
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = primaryTextColor
                                            )
                                            Text(
                                                text = " / ${currencyFormatter.format(goal.target_value)}",
                                                fontSize = 13.sp,
                                                color = secondaryTextColor,
                                                modifier = Modifier.padding(bottom = 1.dp)
                                            )
                                        }

                                        Text(
                                            text = "${(progress * 100).toInt()}%",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusTextColor
                                        )
                                    }

                                    // Linha 3: Barra de Progresso Arredondada
                                    LinearProgressIndicator(
                                        progress = { progress.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = statusTextColor,
                                        trackColor = cardBorderColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // DETAIL VIEW (DETALHE DA META)
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

            val goalIcon = remember(selectedGoal.name) { getGoalIcon(selectedGoal.name) }
            val goalColor = remember(selectedGoal.color) { Color(selectedGoal.color) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
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
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = primaryTextColor
                            )
                        }
                        Text(
                            text = "Detalhe da Meta",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                    }

                    // Hide / Edit / Delete options
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                val newArchived = !selectedGoal.archived
                                viewModel.updateGoal(selectedGoal.copy(archived = newArchived))
                                val msg = if (newArchived) "Meta ocultada/pausada" else "Meta visível/reativada"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("toggle_hide_goal_detail")
                        ) {
                            Icon(
                                imageVector = if (selectedGoal.archived) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (selectedGoal.archived) "Exibir meta" else "Ocultar meta",
                                tint = secondaryTextColor
                            )
                        }
                        IconButton(
                            onClick = { showEditGoalDialog = true },
                            modifier = Modifier.testTag("edit_goal_button")
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Editar Meta",
                                tint = greenColor
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
                                tint = redColor
                            )
                        }
                    }
                }

                // Main Info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Title and Icon
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(goalColor.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = goalIcon,
                                    contentDescription = null,
                                    tint = goalColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = selectedGoal.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Progress percentage
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Progresso Total",
                                fontSize = 12.sp,
                                color = secondaryTextColor
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isReached) greenColor else goalColor
                            )
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = if (isReached) greenColor else goalColor,
                                trackColor = cardBorderColor
                            )
                        }

                        // Split metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Saldo Atual",
                                    fontSize = 12.sp,
                                    color = secondaryTextColor
                                )
                                Text(
                                    text = currencyFormatter.format(currentVal),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Meta Alvo",
                                    fontSize = 12.sp,
                                    color = secondaryTextColor
                                )
                                Text(
                                    text = currencyFormatter.format(selectedGoal.target_value),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Prazo Limite",
                                    fontSize = 12.sp,
                                    color = secondaryTextColor
                                )
                                Text(
                                    text = formatMonthDeadline(selectedGoal.deadline),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = greenColor
                                )
                            }
                        }

                        if (isReached) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = greenColor.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = greenColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Parabéns! Meta alcançada! 🎉",
                                        fontWeight = FontWeight.Bold,
                                        color = greenColor,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Faltam ${currencyFormatter.format(remaining)} para atingir seu objetivo.",
                                fontSize = 13.sp,
                                color = secondaryTextColor,
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
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("aportar_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = greenColor)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Aportar", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    preSelectedGoalMode = "RETIRAR"
                                    showDistributeDialog = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("retirar_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = redColor),
                                border = BorderStroke(1.dp, redColor)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = null, tint = redColor, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retirar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // History section
                Text(
                    text = "Histórico de Aportes e Retiradas",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )

                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhum aporte ou retirada realizado ainda.",
                            fontSize = 13.5.sp,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history, key = { it.id }) { movement ->
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
                                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, cardBorderColor)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isAporte) "Aporte de: $fromLabel" else "Retirada para: $toLabel",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryTextColor
                                        )
                                        val dateStr = remember(movement.moved_at) {
                                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(movement.moved_at))
                                        }
                                        Text(
                                            text = dateStr,
                                            fontSize = 11.5.sp,
                                            color = secondaryTextColor
                                        )
                                        if (!movement.note.isNullOrBlank()) {
                                            Text(
                                                text = "Nota: ${movement.note}",
                                                fontSize = 11.5.sp,
                                                color = secondaryTextColor,
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
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAporte) greenColor else redColor
                                        )
                                        IconButton(
                                            onClick = { targetMovementToEdit = movement },
                                            modifier = Modifier.size(32.dp).testTag("edit_movement_${movement.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Editar Movimentação",
                                                modifier = Modifier.size(16.dp),
                                                tint = greenColor
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
                                                tint = redColor
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

        // DIALOG: MONTH PICKER HEADER
        if (showMonthPickerHeader) {
            MonthYearPickerDialog(
                currentCalendar = selectedMonthCalendar,
                onDismiss = { showMonthPickerHeader = false },
                onSelected = { year, month ->
                    val newCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                    }
                    viewModel.setSelectedMonth(newCal)
                    showMonthPickerHeader = false
                }
            )
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

    // Visual-only fields (non-persisted, for future roadmap decision)
    // TODO: decidir se implementa - Aporte mensal desejado
    var aporteMensalDesejado by remember { mutableStateOf("") }
    // TODO: decidir se implementa - Prioridade
    var prioridade by remember { mutableStateOf("Média") }
    // TODO: decidir se implementa - Lembrete
    var lembreteAtivo by remember { mutableStateOf(false) }
    // TODO: decidir se implementa - Conta/origem do dinheiro
    var contaOrigem by remember { mutableStateOf("Qualquer conta (Geral)") }

    val sdf = remember { SimpleDateFormat("yyyy-MM", Locale.US) }
    val displaySdf = remember { SimpleDateFormat("MMMM yyyy", Locale("pt", "BR")) }

    var startCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var deadlineCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply { add(Calendar.MONTH, 12) })
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nome da Meta") },
                        modifier = Modifier.fillMaxWidth().testTag("add_goal_name_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = targetValue,
                        onValueChange = { targetValue = it },
                        label = { Text("Valor Alvo (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("add_goal_target_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Month selection for start date
                item {
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
                }

                // Month selection for deadline
                item {
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
                }

                // Color Preset Picker
                item {
                    Text(
                        text = "Cor da Meta",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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

                // --- ELEMENTOS VISUAIS PARA DECISÃO FUTURA ---
                // TODO: decidir se implementa - Aporte mensal desejado
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Opções Avançadas (Projeção)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    // TODO: decidir se implementa - Aporte mensal desejado
                    OutlinedTextField(
                        value = aporteMensalDesejado,
                        onValueChange = { aporteMensalDesejado = it },
                        label = { Text("Aporte mensal desejado (opcional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Prioridade",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Baixa", "Média", "Alta").forEach { option ->
                                val isSelected = prioridade == option
                                Surface(
                                    onClick = { prioridade = option },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("priority_chip_$option")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = option,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    // TODO: decidir se implementa - Lembrete
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ativar lembrete de aporte", fontSize = 13.5.sp)
                        Switch(
                            checked = lembreteAtivo,
                            onCheckedChange = { lembreteAtivo = it }
                        )
                    }
                }

                item {
                    // TODO: decidir se implementa - Conta/origem do dinheiro
                    OutlinedTextField(
                        value = contaOrigem,
                        onValueChange = { contaOrigem = it },
                        label = { Text("Conta / Origem vinculada") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
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
                startCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
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
                deadlineCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                }
                showDeadlinePicker = false
            }
        )
    }
}

@Composable
fun EditGoalDialog(
    goal: Goal,
    onDismiss: () -> Unit,
    onSave: (String, Double, String, String, Int) -> Unit // (name, target, start_date, deadline, color)
) {
    var name by remember { mutableStateOf(goal.name) }
    var targetValue by remember { mutableStateOf(goal.target_value.toString()) }

    val sdf = remember { SimpleDateFormat("yyyy-MM", Locale.US) }
    val displaySdf = remember { SimpleDateFormat("MMMM yyyy", Locale("pt", "BR")) }

    var startCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply {
            try {
                time = sdf.parse(goal.start_date) ?: Date()
            } catch (e: Exception) {}
        })
    }
    var deadlineCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply {
            try {
                time = sdf.parse(goal.deadline) ?: Date()
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
                    text = "Cor da Meta",
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
                startCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
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
                deadlineCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
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

private fun getGoalIcon(name: String): ImageVector {
    val lower = name.lowercase()
    return when {
        lower.contains("viagem") || lower.contains("viajar") || lower.contains("ferias") -> Icons.Default.Flight
        lower.contains("reserva") || lower.contains("emergencia") || lower.contains("seguranca") -> Icons.Default.Shield
        lower.contains("notebook") || lower.contains("computador") || lower.contains("pc") || lower.contains("tech") || lower.contains("laptop") -> Icons.Default.Laptop
        lower.contains("curso") || lower.contains("estudo") || lower.contains("faculdade") || lower.contains("escola") -> Icons.Default.School
        lower.contains("carro") || lower.contains("moto") || lower.contains("veiculo") -> Icons.Default.DirectionsCar
        lower.contains("casa") || lower.contains("ap") || lower.contains("imovel") || lower.contains("reforma") -> Icons.Default.Home
        lower.contains("compras") || lower.contains("shopping") -> Icons.Default.ShoppingBag
        else -> Icons.Default.GpsFixed
    }
}

private fun isDeadlinePassed(deadline: String, currentMonth: String): Boolean {
    if (deadline.isBlank()) return false
    return try {
        val deadlineMonth = if (deadline.length >= 7) deadline.substring(0, 7) else deadline
        deadlineMonth < currentMonth
    } catch (e: Exception) {
        false
    }
}

private fun formatMonthDeadline(deadline: String): String {
    if (deadline.isBlank()) return ""
    return try {
        val sdfIn = SimpleDateFormat("yyyy-MM", Locale.US)
        val sdfOut = SimpleDateFormat("MMM yyyy", Locale("pt", "BR"))
        val date = sdfIn.parse(if (deadline.length >= 7) deadline.substring(0, 7) else deadline)
        if (date != null) sdfOut.format(date).replaceFirstChar { it.uppercase() } else deadline
    } catch (e: Exception) {
        deadline
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
