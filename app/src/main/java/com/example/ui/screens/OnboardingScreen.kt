package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.EnvelopeGroup
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: MainViewModel,
    onOnboardingFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableStateOf(1) }
    val scope = rememberCoroutineScope()
    val userId = viewModel.currentUserId

    // Temporary list of accounts during onboarding
    val tempAccounts = remember { mutableStateListOf<Account>() }
    var showAddAccountDialog by remember { mutableStateOf(false) }

    // Temporary income configuration
    var monthlyIncome by remember { mutableStateOf("") }

    // Helper function to insert the pre-filled envelope groups & categories
    fun insertTemplateEnvelopes() {
        scope.launch {
            val db = viewModel.repository
            // Necessidades
            val id1 = db.insertEnvelopeGroup(
                EnvelopeGroup(name = "Necessidades", sort_order = 1, budget_rule_type = "NECESSIDADE", userId = userId)
            ).toInt()
            db.insertCategory(Category(envelope_group_id = id1, name = "Aluguel / Prestação", userId = userId))
            db.insertCategory(Category(envelope_group_id = id1, name = "Energia / Água / Gás", userId = userId))
            db.insertCategory(Category(envelope_group_id = id1, name = "Internet / Celular", userId = userId))
            db.insertCategory(Category(envelope_group_id = id1, name = "Alimentação / Supermercado", userId = userId))
            db.insertCategory(Category(envelope_group_id = id1, name = "Transporte / Combustível", userId = userId))

            // Desejos
            val id2 = db.insertEnvelopeGroup(
                EnvelopeGroup(name = "Desejos", sort_order = 2, budget_rule_type = "DESEJO", userId = userId)
            ).toInt()
            db.insertCategory(Category(envelope_group_id = id2, name = "Lazer / Passeios", userId = userId))
            db.insertCategory(Category(envelope_group_id = id2, name = "Restaurantes / Delivery", userId = userId))
            db.insertCategory(Category(envelope_group_id = id2, name = "Compras / Vestuário", userId = userId))
            db.insertCategory(Category(envelope_group_id = id2, name = "Assinaturas / Streaming", userId = userId))

            // Poupança
            val id3 = db.insertEnvelopeGroup(
                EnvelopeGroup(name = "Poupança", sort_order = 3, budget_rule_type = "POUPANCA", userId = userId)
            ).toInt()
            db.insertCategory(Category(envelope_group_id = id3, name = "Reserva de Emergência", userId = userId))
            db.insertCategory(Category(envelope_group_id = id3, name = "Investimentos", userId = userId))
        }
    }

    // Save final accounts configured
    fun saveAccounts() {
        scope.launch {
            val db = viewModel.repository
            for (acc in tempAccounts) {
                db.insertAccount(acc.copy(userId = userId))
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Passo $currentStep de 7",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    if (currentStep > 1) {
                        IconButton(onClick = { currentStep-- }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                actions = {
                    if (currentStep in 2..7) {
                        TextButton(
                            onClick = {
                                if (currentStep == 2) saveAccounts()
                                if (currentStep == 4) { /* Skip template */ }
                                if (currentStep == 7) {
                                    viewModel.setOnboardingCompleted(true)
                                    onOnboardingFinished()
                                } else {
                                    currentStep++
                                }
                            }
                        ) {
                            Text("Pular")
                        }
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Interactive Wizard Body with nice transitions
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "step_transition"
                ) { step ->
                    when (step) {
                        1 -> WelcomeStep { currentStep++ }
                        2 -> AccountsStep(
                            tempAccounts = tempAccounts,
                            onAddAccountClick = { showAddAccountDialog = true },
                            onNext = {
                                saveAccounts()
                                currentStep++
                            }
                        )
                        3 -> IncomeStep(
                            income = monthlyIncome,
                            onIncomeChange = { monthlyIncome = it },
                            onNext = { currentStep++ }
                        )
                        4 -> EnvelopesStep(
                            onUseTemplate = {
                                insertTemplateEnvelopes()
                                currentStep++
                            },
                            onNext = { currentStep++ }
                        )
                        5 -> FirstAllocationStep { currentStep++ }
                        6 -> InitialGoalStep { currentStep++ }
                        7 -> SecurityStep {
                            viewModel.setOnboardingCompleted(true)
                            onOnboardingFinished()
                        }
                    }
                }
            }

            // Progress Bar / Indicator Dots at the bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                (1..7).forEach { dotIndex ->
                    val isSelected = dotIndex == currentStep
                    val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    val width = if (isSelected) 24.dp else 8.dp
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(height = 8.dp, width = width)
                            .background(color, RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }

    // Dialog for adding temporary account
    if (showAddAccountDialog) {
        var accName by remember { mutableStateOf("") }
        var accType by remember { mutableStateOf("CONTA_CORRENTE") } // DINHEIRO, CONTA_CORRENTE, CARTAO_CREDITO
        var accBalance by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddAccountDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Nova Conta / Cartão",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = accName,
                        onValueChange = { accName = it },
                        label = { Text("Nome da Conta (ex: Carteira, Itaú)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Type selection
                    Column {
                        Text(
                            text = "Tipo da Conta",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val types = listOf(
                            "DINHEIRO" to "Dinheiro em Espécie",
                            "CONTA_CORRENTE" to "Conta Corrente",
                            "CARTAO_CREDITO" to "Cartão de Crédito"
                        )
                        types.forEach { (typeKey, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (accType == typeKey) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        else MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(8.dp)
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = accType == typeKey,
                                    onClick = { accType = typeKey }
                                )
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(start = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = accBalance,
                        onValueChange = { accBalance = it },
                        label = { Text("Saldo Inicial / Limite do Cartão") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showAddAccountDialog = false }) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (accName.isNotBlank()) {
                                    val balance = accBalance.toDoubleOrNull() ?: 0.0
                                    tempAccounts.add(
                                        Account(
                                            name = accName,
                                            type = accType,
                                            initial_balance = balance,
                                            userId = userId
                                        )
                                    )
                                    showAddAccountDialog = false
                                }
                            }
                        ) {
                            Text("Adicionar")
                        }
                    }
                }
            }
        }
    }
}

// STEP 1: Welcome
@Composable
fun WelcomeStep(onStart: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.AccountBalanceWallet,
            contentDescription = "Finance",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Bem-vindo ao Meu Financeiro 2.0!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Organize suas finanças de forma simples e intuitiva usando a clássica metodologia de envelopes. Aloque limites em cada envelope e acompanhe seus gastos mês a mês sem complicação.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Começar Onboarding", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// STEP 2: Accounts Config
@Composable
fun AccountsStep(
    tempAccounts: List<Account>,
    onAddAccountClick: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Cadastrar suas Contas e Cartões",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Cadastre suas carteiras, contas bancárias ou limites de cartão de crédito para ter o saldo inicial total.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onAddAccountClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Adicionar conta")
            Spacer(modifier = Modifier.width(4.dp))
            Text("Adicionar Nova Conta", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            if (tempAccounts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Nenhuma conta cadastrada ainda.\nClique no botão acima para adicionar.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tempAccounts) { acc ->
                        val icon = when (acc.type) {
                            "CARTAO_CREDITO" -> Icons.Default.CreditCard
                            "DINHEIRO" -> Icons.Default.Payments
                            else -> Icons.Default.AccountBalance
                        }
                        ListItem(
                            headlineContent = { Text(acc.name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text(acc.type.replace("_", " ")) },
                            trailingContent = {
                                Text(
                                    text = "R$ %.2f".format(acc.initial_balance),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Salvar e Continuar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// STEP 3: Monthly Income
@Composable
fun IncomeStep(
    income: String,
    onIncomeChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.MonetizationOn, contentDescription = "Renda", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(60.dp))
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Qual é sua Renda Mensal aproximada?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "A renda é usada para comparar com suas alocações de orçamento seguindo a regra 50/30/20.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = income,
            onValueChange = onIncomeChange,
            label = { Text("Renda Mensal Estável (R$)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.85f),
            prefix = { Text("R$ ") }
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Confirmar e Continuar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// STEP 4: Envelopes Template
@Composable
fun EnvelopesStep(
    onUseTemplate: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Criar seus Envelopes Orçamentários",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Envelopes são grupos que reúnem suas categorias de custos. Oferecemos um template excelente e pronto baseado nas melhores práticas financeiras:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "1. Necessidades (Fixas & Essenciais)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Aluguel, Água/Energia, Internet, Supermercado, Transporte.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }

                item {
                    Text(
                        text = "2. Desejos (Variáveis & Conforto)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Lazer, Restaurantes/Delivery, Compras, Assinaturas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }

                item {
                    Text(
                        text = "3. Poupança (Investimento & Segurança)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Reserva de Emergência, Investimentos de Longo Prazo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onUseTemplate,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Importar Template Pronto", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Criar Envelopes do Zero", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// STEP 5: First Allocation
@Composable
fun FirstAllocationStep(onNext: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Savings, contentDescription = "Alocação", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(70.dp))
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Sua Primeira Alocação de Saldo",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "O sistema calculará automaticamente o seu valor 'Pronto para Atribuir' que é a soma de todos os seus saldos.\n\nAtravés da tela de envelopes você distribuirá todo esse dinheiro nos limites mensais correspondentes, garantindo que cada real tenha um destino pré-estabelecido antes de ser gasto.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Entendi, Próximo Passo", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// STEP 6: Initial Goal
@Composable
fun InitialGoalStep(onNext: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.EmojiEvents, contentDescription = "Metas", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(70.dp))
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Crie Suas Metas Financeiras",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Metas são objetivos de poupança (ex: comprar um carro, viagem de fim de ano, reserva de emergência).\n\nVocê poderá destinar fatias mensais diretamente de seu orçamento 'Pronto para Atribuir' ou de sobras de envelopes para acumular e acompanhar o progresso de suas metas em tempo real.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Excelente, Último Passo", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// STEP 7: Security
@Composable
fun SecurityStep(onFinish: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Security, contentDescription = "Segurança", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(70.dp))
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Segurança e Proteção de Dados",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Seus dados financeiros são valiosos. Oferecemos a opção de proteger o acesso ao app com biometria facial/digital ou PIN de segurança local.\n\nVocê poderá configurar e ativar estes mecanismos de bloqueio a qualquer momento diretamente na aba Ajustes -> Segurança.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Finalizar Configuração", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
