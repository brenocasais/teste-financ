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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.auth.AuthManager
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Subcategory
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val userId = viewModel.currentUserId

    // Room DB Flows
    val accounts by viewModel.repository.getAccountsFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by viewModel.repository.getCategoriesFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val subcategories by viewModel.repository.getSubcategoriesFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())

    // UI States
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()

    // Dialog Control States
    var activeDialog by remember { mutableStateOf<SettingsDialog?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 11.1 PROFILE CARD
        item {
            ProfileCard(
                authState = authState,
                onLogoutClick = {
                    viewModel.logout { }
                }
            )
        }

        // 11.2 THEME / APPEARANCE
        item {
            AppearanceCard(
                currentTheme = themeMode,
                onThemeSelected = { viewModel.setThemeMode(it) }
            )
        }

        // 11.3 ACCOUNTS CRUD
        item {
            AccountsCrudCard(
                accounts = accounts,
                onAddAccount = { activeDialog = SettingsDialog.AddAccount },
                onEditAccount = { activeDialog = SettingsDialog.EditAccount(it) },
                onDeleteAccount = {
                    scope.launch {
                        viewModel.repository.deleteAccount(it)
                        viewModel.triggerPush()
                    }
                }
            )
        }

        // 11.5 CATEGORIES & SUBCATEGORIES CRUD
        item {
            CategoriesCrudCard(
                categories = categories,
                subcategories = subcategories,
                onAddCategory = { activeDialog = SettingsDialog.AddCategory },
                onEditCategory = { activeDialog = SettingsDialog.EditCategory(it) },
                onDeleteCategory = {
                    scope.launch {
                        viewModel.repository.deleteCategory(it)
                        viewModel.triggerPush()
                    }
                },
                onAddSubcategory = { activeDialog = SettingsDialog.AddSubcategory },
                onEditSubcategory = { activeDialog = SettingsDialog.EditSubcategory(it) },
                onDeleteSubcategory = {
                    scope.launch {
                        viewModel.repository.deleteSubcategory(it)
                        viewModel.triggerPush()
                    }
                }
            )
        }

        // 11.6 NOTIFICATIONS SETTINGS
        item {
            NotificationsCard(
                viewModel = viewModel
            )
        }

        // 11.9 SYNC SETTINGS
        item {
            SyncSettingsCard(
                viewModel = viewModel,
                syncState = syncState,
                syncLogs = syncLogs
            )
        }

        // 11.10 BACKUP E EXPORTAÇÃO
        item {
            BackupSettingsCard(
                viewModel = viewModel
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Handle Active Dialogs
    when (val dialog = activeDialog) {
        SettingsDialog.AddAccount -> {
            AccountFormDialog(
                account = null,
                onDismiss = { activeDialog = null },
                onSave = { acc ->
                    scope.launch {
                        viewModel.repository.insertAccount(acc.copy(userId = userId))
                        activeDialog = null
                        viewModel.triggerPush()
                    }
                }
            )
        }
        is SettingsDialog.EditAccount -> {
            AccountFormDialog(
                account = dialog.account,
                onDismiss = { activeDialog = null },
                onSave = { acc ->
                    scope.launch {
                        viewModel.repository.updateAccount(acc.copy(userId = userId))
                        activeDialog = null
                        viewModel.triggerPush()
                    }
                }
            )
        }
        SettingsDialog.AddCategory -> {
            CategoryFormDialog(
                category = null,
                onDismiss = { activeDialog = null },
                onSave = { cat, subName ->
                    scope.launch {
                        val catId = viewModel.repository.insertCategory(cat.copy(userId = userId))
                        if (subName != null && subName.isNotBlank()) {
                            viewModel.repository.insertSubcategory(
                                Subcategory(
                                    category_id = catId.toInt(),
                                    name = subName,
                                    userId = userId
                                )
                            )
                        }
                        activeDialog = null
                        viewModel.triggerPush()
                    }
                }
            )
        }
        is SettingsDialog.EditCategory -> {
            CategoryFormDialog(
                category = dialog.category,
                onDismiss = { activeDialog = null },
                onSave = { cat, _ ->
                    scope.launch {
                        viewModel.repository.updateCategory(cat.copy(userId = userId))
                        activeDialog = null
                        viewModel.triggerPush()
                    }
                }
            )
        }
        SettingsDialog.AddSubcategory -> {
            SubcategoryFormDialog(
                subcategory = null,
                categories = categories,
                onDismiss = { activeDialog = null },
                onSave = { sub ->
                    scope.launch {
                        viewModel.repository.insertSubcategory(sub.copy(userId = userId))
                        activeDialog = null
                        viewModel.triggerPush()
                    }
                }
            )
        }
        is SettingsDialog.EditSubcategory -> {
            SubcategoryFormDialog(
                subcategory = dialog.subcategory,
                categories = categories,
                onDismiss = { activeDialog = null },
                onSave = { sub ->
                    scope.launch {
                        viewModel.repository.updateSubcategory(sub.copy(userId = userId))
                        activeDialog = null
                        viewModel.triggerPush()
                    }
                }
            )
        }
        null -> {}
    }
}

// Sealed class to represent active configuration dialogs
sealed class SettingsDialog {
    object AddAccount : SettingsDialog()
    data class EditAccount(val account: Account) : SettingsDialog()
    object AddCategory : SettingsDialog()
    data class EditCategory(val category: Category) : SettingsDialog()
    object AddSubcategory : SettingsDialog()
    data class EditSubcategory(val subcategory: Subcategory) : SettingsDialog()
}

// --- SUB-COMPONENTS ---

@Composable
fun ProfileCard(
    authState: AuthManager.AuthState,
    onLogoutClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Perfil do Usuário", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                val email = when (authState) {
                    is AuthManager.AuthState.Authenticated -> authState.user.email ?: "Autenticado via Google"
                    is AuthManager.AuthState.Guest -> "Modo Convidado (Local)"
                    else -> "Não autenticado"
                }
                Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            IconButton(
                onClick = onLogoutClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Logout, contentDescription = "Sair")
            }
        }
    }
}

@Composable
fun AppearanceCard(
    currentTheme: String,
    onThemeSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Aparência e Tema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = when (currentTheme) {
                                "LIGHT" -> "Claro"
                                "DARK" -> "Escuro"
                                else -> "Seguir Sistema"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val options = listOf("SYSTEM" to "Seguir Sistema", "LIGHT" to "Tema Claro", "DARK" to "Tema Escuro")
                    options.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (currentTheme == mode) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else Color.Transparent
                                )
                                .clickable { onThemeSelected(mode) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTheme == mode,
                                onClick = { onThemeSelected(mode) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountsCrudCard(
    accounts: List<Account>,
    onAddAccount: () -> Unit,
    onEditAccount: (Account) -> Unit,
    onDeleteAccount: (Account) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Contas e Cartões", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${accounts.size} cadastradas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onAddAccount,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adicionar Nova Conta")
                    }

                    if (accounts.isEmpty()) {
                        Text(
                            text = "Nenhuma conta cadastrada.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        accounts.forEach { acc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(acc.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = "Tipo: ${acc.type.replace("_", " ")} | Saldo: R$ %.2f".format(acc.initial_balance),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                Row {
                                    IconButton(onClick = { onEditAccount(acc) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { onDeleteAccount(acc) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error)
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

@Composable
fun CategoriesCrudCard(
    categories: List<Category>,
    subcategories: List<Subcategory>,
    onAddCategory: () -> Unit,
    onEditCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onAddSubcategory: () -> Unit,
    onEditSubcategory: (Subcategory) -> Unit,
    onDeleteSubcategory: (Subcategory) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Categorias e Subcategorias", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${categories.size} categorias | ${subcategories.size} subcategorias", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Category list
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Categorias", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = onAddCategory) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Nova Categoria")
                            }
                        }

                        if (categories.isEmpty()) {
                            Text("Nenhuma categoria criada.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        } else {
                            categories.forEach { cat ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cat.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Row {
                                        IconButton(onClick = { onEditCategory(cat) }) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { onDeleteCategory(cat) }) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    // Subcategory list
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Subcategorias", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = onAddSubcategory) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Nova Subcategoria")
                            }
                        }

                        if (subcategories.isEmpty()) {
                            Text("Nenhuma subcategoria criada.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        } else {
                            subcategories.forEach { sub ->
                                val catName = categories.find { it.id == sub.category_id }?.name ?: "Sem categoria"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(sub.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        Text("Categoria: $catName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                    Row {
                                        IconButton(onClick = { onEditSubcategory(sub) }) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { onDeleteSubcategory(sub) }) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
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

@Composable
fun SyncSettingsCard(
    viewModel: MainViewModel,
    syncState: MainViewModel.SyncState,
    syncLogs: List<String>
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Sincronização Cloud", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val statusText = when (syncState) {
                            is MainViewModel.SyncState.Syncing -> "Sincronizando..."
                            is MainViewModel.SyncState.Success -> "Sincronizado"
                            is MainViewModel.SyncState.Error -> "Erro na sincronização"
                            else -> "Pronto"
                        }
                        Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.triggerPush() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            enabled = viewModel.currentUserId != "GUEST"
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Enviar", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { viewModel.triggerPull() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            enabled = viewModel.currentUserId != "GUEST"
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Baixar", fontSize = 12.sp)
                        }
                    }

                    if (viewModel.currentUserId == "GUEST") {
                        Text(
                            text = "Sincronização desativada no modo Convidado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text("Registro de Auditoria (Sync Logs)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        if (syncLogs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Nenhum evento registrado.", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(syncLogs) { log ->
                                    Text(
                                        text = log,
                                        style = MaterialTheme.typography.bodySmall,
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
}

@Composable
fun BackupSettingsCard(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = viewModel.currentUserId

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Backup,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Backup e Exportação",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Cópia de segurança independente",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Gere uma cópia em formato JSON contendo absolutamente todos os seus dados locais (Contas, Transações, Envelopes, Alocações, Planejamentos, Metas, etc.). Você pode salvar este arquivo de forma segura ou compartilhá-lo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val jsonContent = viewModel.exportAllDataJson(userId)
                                    val backupFile = java.io.File(context.cacheDir, "meu_financeiro_backup.json")
                                    backupFile.writeText(jsonContent)

                                    val authority = "${context.packageName}.fileprovider"
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        authority,
                                        backupFile
                                    )

                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Meu Financeiro - Backup de Dados")
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }

                                    context.startActivity(
                                        android.content.Intent.createChooser(
                                            shareIntent,
                                            "Compartilhar arquivo de Backup"
                                        )
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exportar todos os dados (JSON)")
                    }
                }
            }
        }
    }
}

// --- CRUD DIALOGS ---

@Composable
fun AccountFormDialog(
    account: Account?,
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit
) {
    var name by remember { mutableStateOf(account?.name ?: "") }
    var type by remember { mutableStateOf(account?.type ?: "CONTA_CORRENTE") }
    var balance by remember { mutableStateOf(account?.initial_balance?.toString() ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (account == null) "Nova Conta" else "Editar Conta",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da Conta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tipo da Conta", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    val options = listOf(
                        "CONTA_CORRENTE" to "Conta Corrente",
                        "CARTAO_CREDITO" to "Cartão de Crédito",
                        "DINHEIRO" to "Dinheiro em Espécie"
                    )
                    options.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { type = key }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = type == key, onClick = { type = key })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    label = { Text("Saldo Inicial") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val dBalance = balance.toDoubleOrNull() ?: 0.0
                                onSave(
                                    Account(
                                        id = account?.id ?: 0,
                                        name = name,
                                        type = type,
                                        initial_balance = dBalance,
                                        archived = account?.archived ?: false
                                    )
                                )
                            }
                        }
                    ) {
                        Text("Salvar")
                    }
                }
            }
        }
    }
}



@Composable
fun CategoryFormDialog(
    category: Category?,
    onDismiss: () -> Unit,
    onSave: (Category, String?) -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var subcategoryName by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (category == null) "Nova Categoria" else "Editar Categoria",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da Categoria") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (category == null) {
                    OutlinedTextField(
                        value = subcategoryName,
                        onValueChange = { subcategoryName = it },
                        label = { Text("Nome da primeira subcategoria") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = showError && subcategoryName.isBlank()
                    )
                    if (showError && subcategoryName.isBlank()) {
                        Text(
                            text = "A primeira subcategoria é obrigatória.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                return@Button
                            }
                            if (category == null && subcategoryName.isBlank()) {
                                showError = true
                                return@Button
                            }
                            onSave(
                                Category(
                                    id = category?.id ?: 0,
                                    name = name.trim(),
                                    archived = category?.archived ?: false
                                ),
                                if (category == null) subcategoryName.trim() else null
                            )
                        }
                    ) {
                        Text("Salvar")
                    }
                }
            }
        }
    }
}

@Composable
fun SubcategoryFormDialog(
    subcategory: Subcategory?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Subcategory) -> Unit
) {
    var name by remember { mutableStateOf(subcategory?.name ?: "") }
    var selectedCat by remember { mutableStateOf(subcategory?.category_id ?: categories.firstOrNull()?.id) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (subcategory == null) "Nova Subcategoria" else "Editar Subcategoria",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da Subcategoria") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Categoria Relacionada (Obrigatória)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    val activeCatText = categories.find { it.id == selectedCat }?.name ?: "Selecione uma categoria"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { dropdownExpanded = true }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(activeCatText)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        selectedCat = cat.id
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && selectedCat != null) {
                                onSave(
                                    Subcategory(
                                        id = subcategory?.id ?: 0,
                                        name = name,
                                        category_id = selectedCat!!,
                                        archived = subcategory?.archived ?: false
                                    )
                                )
                            }
                        }
                    ) {
                        Text("Salvar")
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsCard(
    viewModel: MainViewModel
) {
    val scope = rememberCoroutineScope()
    
    val notifyLimits by viewModel.userPreferences.notifyLimitsFlow.collectAsStateWithLifecycle(initialValue = true)
    val notifyCreditCard by viewModel.userPreferences.notifyCreditCardFlow.collectAsStateWithLifecycle(initialValue = true)
    val notifyInstallment by viewModel.userPreferences.notifyInstallmentFlow.collectAsStateWithLifecycle(initialValue = true)
    val notifyGoal by viewModel.userPreferences.notifyGoalFlow.collectAsStateWithLifecycle(initialValue = true)
    val notifyWeeklyReview by viewModel.userPreferences.notifyWeeklyReviewFlow.collectAsStateWithLifecycle(initialValue = true)
    
    val creditCardDaysBefore by viewModel.userPreferences.creditCardDaysBeforeFlow.collectAsStateWithLifecycle(initialValue = 3)
    val weeklyReviewDay by viewModel.userPreferences.weeklyReviewDayFlow.collectAsStateWithLifecycle(initialValue = 1)
    val weeklyReviewTime by viewModel.userPreferences.weeklyReviewTimeFlow.collectAsStateWithLifecycle(initialValue = "20:00")

    var cardDaysBeforeInput by remember(creditCardDaysBefore) { mutableStateOf(creditCardDaysBefore.toString()) }
    var weeklyReviewTimeInput by remember(weeklyReviewTime) { mutableStateOf(weeklyReviewTime) }
    var dayDropdownExpanded by remember { mutableStateOf(false) }

    val daysOfWeek = listOf(
        "Domingo", "Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira", "Sexta-feira", "Sábado"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(enabled = true)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Notificações e Alertas 🔔",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Gerencie como e quando deseja receber alertas e notificações em tempo real.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 1. Limits toggle
            NotificationToggleRow(
                title = "Limites de Categorias (80% / 100%)",
                subtitle = "Alerta quando uma categoria atinge ou ultrapassa os limites planejados",
                checked = notifyLimits,
                onCheckedChange = { scope.launch { viewModel.userPreferences.setNotifyLimits(it) } }
            )

            // 2. Credit card toggle
            NotificationToggleRow(
                title = "Faturas de Cartão",
                subtitle = "Alerta quando uma fatura de cartão está próxima do vencimento",
                checked = notifyCreditCard,
                onCheckedChange = { scope.launch { viewModel.userPreferences.setNotifyCreditCard(it) } }
            )

            if (notifyCreditCard) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Dias antes do vencimento:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = cardDaysBeforeInput,
                        onValueChange = { newValue ->
                            cardDaysBeforeInput = newValue
                            val parsed = newValue.toIntOrNull()
                            if (parsed != null && parsed > 0) {
                                scope.launch { viewModel.userPreferences.setCreditCardDaysBefore(parsed) }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(80.dp),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // 3. Installments toggle
            NotificationToggleRow(
                title = "Parcelas a Vencer",
                subtitle = "Notifica parcelas que vencem nos próximos 3 dias",
                checked = notifyInstallment,
                onCheckedChange = { scope.launch { viewModel.userPreferences.setNotifyInstallment(it) } }
            )

            // 4. Goals toggle
            NotificationToggleRow(
                title = "Metas Alcançadas",
                subtitle = "Parabeniza você instantaneamente ao atingir ou superar uma meta",
                checked = notifyGoal,
                onCheckedChange = { scope.launch { viewModel.userPreferences.setNotifyGoal(it) } }
            )

            // 5. Weekly Review toggle
            NotificationToggleRow(
                title = "Revisão Semanal",
                subtitle = "Resumo semanal contendo estatísticas de categorias e Pronto para Atribuir",
                checked = notifyWeeklyReview,
                onCheckedChange = { scope.launch { viewModel.userPreferences.setNotifyWeeklyReview(it) } }
            )

            if (notifyWeeklyReview) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Day selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Dia da Semana:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Box {
                            val currentDayName = daysOfWeek.getOrNull(weeklyReviewDay - 1) ?: "Selecione"
                            Button(
                                onClick = { dayDropdownExpanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Text(currentDayName)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = dayDropdownExpanded,
                                onDismissRequest = { dayDropdownExpanded = false }
                            ) {
                                daysOfWeek.forEachIndexed { index, day ->
                                    DropdownMenuItem(
                                        text = { Text(day) },
                                        onClick = {
                                            scope.launch { viewModel.userPreferences.setWeeklyReviewDay(index + 1) }
                                            dayDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Time selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Horário (HH:mm):",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = weeklyReviewTimeInput,
                            onValueChange = { newValue ->
                                weeklyReviewTimeInput = newValue
                                if (newValue.matches(Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$"))) {
                                    scope.launch { viewModel.userPreferences.setWeeklyReviewTime(newValue) }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.width(100.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            placeholder = { Text("20:00") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag("toggle_${title.replace(" ", "_").lowercase()}")
        )
    }
}
