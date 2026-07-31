package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.auth.AuthManager
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.RecurrenceRule
import com.example.data.model.Subcategory
import com.example.ui.components.CategoryTemplateSelectorDialog
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.ExportHelper
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = viewModel.currentUserId

    // Room DB Flows
    val accounts by viewModel.repository.getAccountsFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by viewModel.repository.getCategoriesFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val subcategories by viewModel.repository.getSubcategoriesFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val recurrenceRules by viewModel.repository.getRecurrenceRulesFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())
    val transactions by viewModel.repository.getTransactionsFlow(userId).collectAsStateWithLifecycle(initialValue = emptyList())

    // UI States
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val hideValues by viewModel.hideValues.collectAsStateWithLifecycle()
    val securityEnabled by viewModel.securityEnabled.collectAsStateWithLifecycle()
    val authMethod by viewModel.authMethod.collectAsStateWithLifecycle()

    // App Version
    val versionName = remember(context) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "v${packageInfo.versionName ?: "1.0.0"}"
        } catch (e: Exception) {
            "v1.0.0"
        }
    }

    // Color System (Redesign Tokens Section 0.1)
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF0D1315) else Color(0xFFFAFAFB)
    val cardBgColor = if (isDark) Color(0xFF172021) else Color(0xFFFFFFFF)
    val borderColor = if (isDark) Color(0xFF263233) else Color(0xFFECEFF1)
    val primaryTextColor = if (isDark) Color(0xFFF5F7F7) else Color(0xFF111827)
    val secondaryTextColor = if (isDark) Color(0xFFA9B1B1) else Color(0xFF6B7280)
    val greenColor = if (isDark) Color(0xFF39D47A) else Color(0xFF22A45D)
    val redColor = if (isDark) Color(0xFFFF4D55) else Color(0xFFEF4444)

    // Active Dialog Control
    var activeDialog by remember { mutableStateOf<SettingsDialog?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // --- CABEÇALHO ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Ajustes",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
                Text(
                    text = "Gerencie sua conta e preferências",
                    fontSize = 13.sp,
                    color = secondaryTextColor
                )
            }
        }

        // --- CARD DE PERFIL ---
        item {
            val currentAuth = authState
            val emailText = when (currentAuth) {
                is AuthManager.AuthState.Authenticated -> currentAuth.user.email ?: "Autenticado via Google"
                is AuthManager.AuthState.Guest -> "Modo Convidado (Local)"
                else -> "Não autenticado"
            }
            val nameText = when (currentAuth) {
                is AuthManager.AuthState.Authenticated -> currentAuth.user.displayName ?: currentAuth.user.email?.substringBefore("@") ?: "Usuário"
                is AuthManager.AuthState.Guest -> "Convidado"
                else -> "Visitante"
            }
            val initials = nameText.take(2).uppercase()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeDialog = SettingsDialog.EditProfile },
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, borderColor),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(greenColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = greenColor
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = nameText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryTextColor
                            )
                            Text(
                                text = emailText,
                                fontSize = 13.sp,
                                color = secondaryTextColor
                            )
                        }
                    }

                    IconButton(
                        onClick = { activeDialog = SettingsDialog.EditProfile },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Editar Perfil",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // --- SEÇÃO PREFERÊNCIAS ---
        item {
            SectionHeader("PREFERÊNCIAS", secondaryTextColor)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, borderColor),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column {
                    val themeSubtitle = when (themeMode) {
                        "LIGHT" -> "Claro"
                        "DARK" -> "Escuro"
                        else -> "Seguir Sistema"
                    }
                    SettingsItemRow(
                        icon = Icons.Default.Palette,
                        title = "Aparência",
                        subtitle = themeSubtitle,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        iconTint = greenColor,
                        onClick = { activeDialog = SettingsDialog.Appearance }
                    )
                    HorizontalDivider(color = borderColor)
                    SettingsItemRow(
                        icon = Icons.Default.Notifications,
                        title = "Notificações",
                        subtitle = "Alertas e preferências de aviso",
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        iconTint = greenColor,
                        onClick = { activeDialog = SettingsDialog.Notifications }
                    )
                    HorizontalDivider(color = borderColor)
                    SettingsItemRow(
                        icon = Icons.Default.Language,
                        title = "Idioma",
                        subtitle = "Português (Brasil)",
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        iconTint = greenColor,
                        onClick = { }
                    )
                    HorizontalDivider(color = borderColor)
                    SettingsItemRow(
                        icon = Icons.Default.AttachMoney,
                        title = "Moeda padrão",
                        subtitle = "Real (R$)",
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        iconTint = greenColor,
                        onClick = { }
                    )
                }
            }
        }

        // --- SEÇÃO FINANÇAS E ORGANIZAÇÃO ---
        item {
            SectionHeader("FINANÇAS E ORGANIZAÇÃO", secondaryTextColor)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, borderColor),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column {
                    SettingsItemRow(
                        icon = Icons.Default.AccountBalance,
                        title = "Contas e cartões",
                        subtitle = "${accounts.size} cadastradas",
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        iconTint = greenColor,
                        onClick = { activeDialog = SettingsDialog.AccountsCrud }
                    )
                    HorizontalDivider(color = borderColor)
                    SettingsItemRow(
                        icon = Icons.Default.Category,
                        title = "Categorias e subcategorias",
                        subtitle = "${categories.size} categorias | ${subcategories.size} subcategorias",
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        iconTint = greenColor,
                        onClick = { activeDialog = SettingsDialog.CategoriesCrud }
                    )
                    HorizontalDivider(color = borderColor)
                    val activeRecurrenceRules = recurrenceRules.filter { it.active }
                    SettingsItemRow(
                        icon = Icons.Default.Repeat,
                        title = "Transações recorrentes",
                        subtitle = "${activeRecurrenceRules.size} regras ativas",
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        iconTint = greenColor,
                        onClick = { activeDialog = SettingsDialog.RecurrenceRules }
                    )
                }
            }
        }

        // --- SEÇÃO DADOS E BACKUP ---
        item {
            SectionHeader("DADOS E BACKUP", secondaryTextColor)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, borderColor),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column {
                    SettingsItemRow(
                        icon = Icons.Default.Sync,
                        title = "Backup e sincronização",
                        subtitle = "Sincronização cloud e auditoria",
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        iconTint = greenColor,
                        onClick = { activeDialog = SettingsDialog.SyncSettings }
                    )
                    HorizontalDivider(color = borderColor)
                    SettingsItemRow(
                        icon = Icons.Default.Share,
                        title = "Exportar dados",
                        subtitle = "JSON, CSV, XLSX e PDF",
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        iconTint = greenColor,
                        onClick = { activeDialog = SettingsDialog.ExportData }
                    )
                    HorizontalDivider(color = borderColor)
                    SettingsItemRow(
                        icon = Icons.Default.DeleteForever,
                        title = "Limpar dados",
                        subtitle = "Ação destrutiva - apaga todos os dados locais",
                        primaryTextColor = redColor,
                        secondaryTextColor = secondaryTextColor,
                        iconTint = redColor,
                        onClick = { activeDialog = SettingsDialog.ClearData }
                    )
                }
            }
        }

        // --- SEÇÃO SEGURANÇA ---
        item {
            SectionHeader("SEGURANÇA", secondaryTextColor)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, borderColor),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column {
                    val secSubtitle = if (securityEnabled) "Proteção ativa (${if (authMethod == "BIOMETRIC") "Biometria" else "PIN"})" else "Proteção desativada"
                    SettingsItemRow(
                        icon = Icons.Default.Security,
                        title = "Proteger com senha/biometria",
                        subtitle = secSubtitle,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        iconTint = greenColor,
                        onClick = { activeDialog = SettingsDialog.SecuritySettings }
                    )
                    HorizontalDivider(color = borderColor)
                    SettingsItemRow(
                        icon = Icons.Default.Visibility,
                        title = "Ocultar valores",
                        subtitle = "Oculta saldos e quantias na tela inicial",
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        iconTint = greenColor,
                        onClick = {
                            scope.launch { viewModel.userPreferences.setHideValues(!hideValues) }
                        },
                        trailingContent = {
                            Switch(
                                checked = hideValues,
                                onCheckedChange = { scope.launch { viewModel.userPreferences.setHideValues(it) } },
                                modifier = Modifier.testTag("toggle_hide_values")
                            )
                        }
                    )
                }
            }
        }

        // --- SEÇÃO SOBRE ---
        item {
            SectionHeader("SOBRE", secondaryTextColor)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, borderColor),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column {
                    SettingsItemRow(
                        icon = Icons.Default.Info,
                        title = "Sobre o app",
                        subtitle = "Versão $versionName",
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        iconTint = greenColor,
                        onClick = { }
                    )
                    HorizontalDivider(color = borderColor)
                    SettingsItemRow(
                        icon = Icons.Default.HelpOutline,
                        title = "Central de ajuda",
                        subtitle = "Perguntas frequentes e suporte",
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        iconTint = greenColor,
                        onClick = { activeDialog = SettingsDialog.HelpCenter }
                    )
                }
            }
        }

        // --- SAIR DA CONTA ---
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, redColor.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                SettingsItemRow(
                    icon = Icons.Default.Logout,
                    title = "Sair da conta",
                    subtitle = "Encerrar sessão no aplicativo",
                    primaryTextColor = redColor,
                    secondaryTextColor = secondaryTextColor,
                    iconTint = redColor,
                    onClick = { activeDialog = SettingsDialog.LogoutConfirmation }
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // --- MANUSEIO DOS DIÁLOGOS ATIVOS ---
    when (val dialog = activeDialog) {
        SettingsDialog.EditProfile -> {
            EditProfileDialog(
                authState = authState,
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialog.Appearance -> {
            AppearanceDialog(
                currentTheme = themeMode,
                onThemeSelected = {
                    viewModel.setThemeMode(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialog.Notifications -> {
            NotificationsDialog(
                viewModel = viewModel,
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialog.RecurrenceRules -> {
            RecurrenceRulesDialog(
                rules = recurrenceRules,
                categories = categories,
                viewModel = viewModel,
                onDismiss = { activeDialog = null },
                onEditRule = { rule -> activeDialog = SettingsDialog.EditRecurrenceRule(rule) }
            )
        }
        is SettingsDialog.EditRecurrenceRule -> {
            EditRecurrenceRuleDialog(
                rule = dialog.rule,
                categories = categories,
                onDismiss = { activeDialog = SettingsDialog.RecurrenceRules },
                onSave = { updatedRule ->
                    scope.launch {
                        viewModel.repository.updateRecurrenceRule(updatedRule)
                        activeDialog = SettingsDialog.RecurrenceRules
                        viewModel.triggerPush()
                    }
                }
            )
        }
        SettingsDialog.AccountsCrud -> {
            AccountsCrudDialog(
                accounts = accounts,
                onDismiss = { activeDialog = null },
                onAddAccount = { activeDialog = SettingsDialog.AddAccount },
                onEditAccount = { acc -> activeDialog = SettingsDialog.EditAccount(acc) },
                onDeleteAccount = { acc ->
                    scope.launch {
                        viewModel.repository.deleteAccount(acc)
                        viewModel.triggerPush()
                    }
                }
            )
        }
        SettingsDialog.AddAccount -> {
            AccountFormDialog(
                account = null,
                onDismiss = { activeDialog = SettingsDialog.AccountsCrud },
                onSave = { acc ->
                    scope.launch {
                        viewModel.repository.insertAccount(acc.copy(userId = userId))
                        activeDialog = SettingsDialog.AccountsCrud
                        viewModel.triggerPush()
                    }
                }
            )
        }
        is SettingsDialog.EditAccount -> {
            AccountFormDialog(
                account = dialog.account,
                onDismiss = { activeDialog = SettingsDialog.AccountsCrud },
                onSave = { acc ->
                    scope.launch {
                        viewModel.repository.updateAccount(acc.copy(userId = userId))
                        activeDialog = SettingsDialog.AccountsCrud
                        viewModel.triggerPush()
                    }
                }
            )
        }
        SettingsDialog.CategoriesCrud -> {
            CategoriesCrudDialog(
                categories = categories,
                subcategories = subcategories,
                onDismiss = { activeDialog = null },
                onOpenTemplateSelector = { activeDialog = SettingsDialog.CategoryTemplateSelector },
                onToggleArchiveCategory = { cat ->
                    scope.launch {
                        viewModel.repository.updateCategory(cat.copy(archived = !cat.archived, userId = userId))
                        viewModel.triggerPush()
                    }
                },
                onToggleArchiveSubcategory = { sub ->
                    scope.launch {
                        viewModel.repository.updateSubcategory(sub.copy(archived = !sub.archived, userId = userId))
                        viewModel.triggerPush()
                    }
                },
                onAddCategory = { activeDialog = SettingsDialog.AddCategory },
                onEditCategory = { cat -> activeDialog = SettingsDialog.EditCategory(cat) },
                onDeleteCategory = { cat ->
                    scope.launch {
                        viewModel.repository.deleteCategory(cat)
                        viewModel.triggerPush()
                    }
                },
                onAddSubcategory = { activeDialog = SettingsDialog.AddSubcategory },
                onEditSubcategory = { sub -> activeDialog = SettingsDialog.EditSubcategory(sub) },
                onDeleteSubcategory = { sub ->
                    scope.launch {
                        viewModel.repository.deleteSubcategory(sub)
                        viewModel.triggerPush()
                    }
                }
            )
        }
        SettingsDialog.CategoryTemplateSelector -> {
            CategoryTemplateSelectorDialog(
                existingCategories = categories,
                existingSubcategories = subcategories,
                onDismiss = { activeDialog = SettingsDialog.CategoriesCrud },
                onConfirm = { selections ->
                    viewModel.insertCategoryTemplates(selections) {
                        activeDialog = SettingsDialog.CategoriesCrud
                    }
                }
            )
        }
        SettingsDialog.AddCategory -> {
            CategoryFormDialog(
                category = null,
                onDismiss = { activeDialog = SettingsDialog.CategoriesCrud },
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
                        activeDialog = SettingsDialog.CategoriesCrud
                        viewModel.triggerPush()
                    }
                }
            )
        }
        is SettingsDialog.EditCategory -> {
            CategoryFormDialog(
                category = dialog.category,
                onDismiss = { activeDialog = SettingsDialog.CategoriesCrud },
                onSave = { cat, _ ->
                    scope.launch {
                        viewModel.repository.updateCategory(cat.copy(userId = userId))
                        activeDialog = SettingsDialog.CategoriesCrud
                        viewModel.triggerPush()
                    }
                }
            )
        }
        SettingsDialog.AddSubcategory -> {
            SubcategoryFormDialog(
                subcategory = null,
                categories = categories,
                onDismiss = { activeDialog = SettingsDialog.CategoriesCrud },
                onSave = { sub ->
                    scope.launch {
                        viewModel.repository.insertSubcategory(sub.copy(userId = userId))
                        activeDialog = SettingsDialog.CategoriesCrud
                        viewModel.triggerPush()
                    }
                }
            )
        }
        is SettingsDialog.EditSubcategory -> {
            SubcategoryFormDialog(
                subcategory = dialog.subcategory,
                categories = categories,
                onDismiss = { activeDialog = SettingsDialog.CategoriesCrud },
                onSave = { sub ->
                    scope.launch {
                        viewModel.repository.updateSubcategory(sub.copy(userId = userId))
                        activeDialog = SettingsDialog.CategoriesCrud
                        viewModel.triggerPush()
                    }
                }
            )
        }
        SettingsDialog.SyncSettings -> {
            SyncSettingsDialog(
                viewModel = viewModel,
                syncState = syncState,
                syncLogs = syncLogs,
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialog.ExportData -> {
            ExportDataDialog(
                viewModel = viewModel,
                accounts = accounts,
                transactions = transactions,
                categories = categories,
                subcategories = subcategories,
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialog.ClearData -> {
            ClearDataDialog(
                onDismiss = { activeDialog = null },
                onConfirmClear = {
                    viewModel.clearAllUserData {
                        activeDialog = null
                        Toast.makeText(context, "Dados apagados com sucesso.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        SettingsDialog.SecuritySettings -> {
            SecuritySettingsDialog(
                viewModel = viewModel,
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialog.HelpCenter -> {
            HelpCenterDialog(
                onDismiss = { activeDialog = null }
            )
        }
        SettingsDialog.LogoutConfirmation -> {
            LogoutConfirmationDialog(
                onDismiss = { activeDialog = null },
                onConfirmLogout = {
                    activeDialog = null
                    viewModel.logout { }
                }
            )
        }
        null -> {}
    }
}

// Sealed class to represent active configuration dialogs
sealed class SettingsDialog {
    object EditProfile : SettingsDialog()
    object Appearance : SettingsDialog()
    object Notifications : SettingsDialog()
    object RecurrenceRules : SettingsDialog()
    data class EditRecurrenceRule(val rule: RecurrenceRule) : SettingsDialog()
    object AccountsCrud : SettingsDialog()
    object CategoriesCrud : SettingsDialog()
    object CategoryTemplateSelector : SettingsDialog()
    object SyncSettings : SettingsDialog()
    object ExportData : SettingsDialog()
    object ClearData : SettingsDialog()
    object SecuritySettings : SettingsDialog()
    object HelpCenter : SettingsDialog()
    object LogoutConfirmation : SettingsDialog()

    object AddAccount : SettingsDialog()
    data class EditAccount(val account: Account) : SettingsDialog()
    object AddCategory : SettingsDialog()
    data class EditCategory(val category: Category) : SettingsDialog()
    object AddSubcategory : SettingsDialog()
    data class EditSubcategory(val subcategory: Subcategory) : SettingsDialog()
}

// --- SUB-COMPONENTS ---

@Composable
fun SectionHeader(title: String, color: Color) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = secondaryTextColor
                    )
                }
            }
        }

        if (trailingContent != null) {
            trailingContent()
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = secondaryTextColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// --- FORM DIALOGS ---

@Composable
fun AccountFormDialog(
    account: Account?,
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit
) {
    var name by remember { mutableStateOf(account?.name ?: "") }
    var type by remember { mutableStateOf(account?.type ?: "CORRENTE") }
    var balanceStr by remember { mutableStateOf((account?.initial_balance ?: 0.0).toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (account == null) "Nova Conta" else "Editar Conta",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da Conta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = balanceStr,
                    onValueChange = { balanceStr = it },
                    label = { Text("Saldo Inicial (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Tipo de Conta", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                val types = listOf("CORRENTE" to "Conta Corrente", "POUPANCA" to "Poupança", "INVESTIMENTO" to "Investimento", "DINHEIRO" to "Dinheiro")
                Column {
                    types.forEach { (tKey, tLabel) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { type = tKey }
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(selected = type == tKey, onClick = { type = tKey })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tLabel, fontSize = 13.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val bal = balanceStr.toDoubleOrNull() ?: 0.0
                            val targetAcc = account?.copy(name = name, type = type, initial_balance = bal)
                                ?: Account(name = name, type = type, initial_balance = bal)
                            onSave(targetAcc)
                        },
                        enabled = name.isNotBlank()
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
    var icon by remember { mutableStateOf(category?.icon ?: "") }
    var subcategoryName by remember { mutableStateOf("") }

    val commonEmojis = remember {
        listOf("🏦", "💳", "🛒", "🍔", "🛵", "📺", "🎬", "🎮", "🚗", "⛽", "🏠", "💡", "📱", "✈️", "🎓", "💰", "💵", "🏥", "📈", "🧾", "🎁", "🏋️", "🐾", "👔")
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (category == null) "Nova Categoria" else "Editar Categoria",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da Categoria") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Emoji Icon Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ícone (Emoji)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = icon,
                        onValueChange = { icon = it },
                        label = { Text("Emoji da categoria") },
                        placeholder = { Text("Ex: 🍽️") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        commonEmojis.forEach { em ->
                            FilterChip(
                                selected = icon == em,
                                onClick = { icon = em },
                                label = { Text(em, fontSize = 16.sp) }
                            )
                        }
                    }
                }

                if (category == null) {
                    OutlinedTextField(
                        value = subcategoryName,
                        onValueChange = { subcategoryName = it },
                        label = { Text("Subcategoria Inicial (Opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val iconVal = icon.trim().ifEmpty { null }
                            val targetCat = category?.copy(
                                name = name,
                                icon = iconVal
                            ) ?: Category(
                                name = name,
                                icon = iconVal
                            )
                            onSave(targetCat, subcategoryName)
                        },
                        enabled = name.isNotBlank()
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
    var icon by remember { mutableStateOf(subcategory?.icon ?: "") }
    var selectedCatId by remember { mutableStateOf(subcategory?.category_id ?: categories.firstOrNull()?.id ?: 0) }

    val commonEmojis = remember {
        listOf("🏦", "💳", "🛒", "🍔", "🛵", "📺", "🎬", "🎮", "🚗", "⛽", "🏠", "💡", "📱", "✈️", "🎓", "💰", "💵", "🏥", "📈", "🧾", "🎁", "🏋️", "🐾", "👔")
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (subcategory == null) "Nova Subcategoria" else "Editar Subcategoria",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da Subcategoria") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Emoji Icon Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ícone (Emoji)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = icon,
                        onValueChange = { icon = it },
                        label = { Text("Emoji da subcategoria") },
                        placeholder = { Text("Ex: 🍔") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        commonEmojis.forEach { em ->
                            FilterChip(
                                selected = icon == em,
                                onClick = { icon = em },
                                label = { Text(em, fontSize = 16.sp) }
                            )
                        }
                    }
                }

                Text("Categoria Pai", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Column {
                    categories.filter { !it.archived || it.id == selectedCatId }.forEach { cat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedCatId = cat.id }
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(selected = selectedCatId == cat.id, onClick = { selectedCatId = cat.id })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${cat.icon ?: ""} ${cat.name}".trim(), fontSize = 13.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val iconVal = icon.trim().ifEmpty { null }
                            val targetSub = subcategory?.copy(name = name, category_id = selectedCatId, icon = iconVal)
                                ?: Subcategory(name = name, category_id = selectedCatId, icon = iconVal)
                            onSave(targetSub)
                        },
                        enabled = name.isNotBlank() && selectedCatId != 0
                    ) {
                        Text("Salvar")
                    }
                }
            }
        }
    }
}

@Composable
fun PinSetupDialog(
    isChange: Boolean,
    onDismiss: () -> Unit,
    onSavePin: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isChange) "Alterar PIN de Acesso" else "Cadastrar PIN de Acesso",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Digite um PIN numérico de 4 a 6 dígitos para proteger o aplicativo.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) pin = it },
                    label = { Text("Novo PIN (4-6 dígitos)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) confirmPin = it },
                    label = { Text("Confirmar Novo PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (pin.length < 4) {
                                errorMsg = "O PIN deve ter pelo menos 4 dígitos."
                            } else if (pin != confirmPin) {
                                errorMsg = "Os PINs digitados não coincidem."
                            } else {
                                onSavePin(pin)
                            }
                        },
                        enabled = pin.isNotBlank() && confirmPin.isNotBlank()
                    ) {
                        Text("Salvar")
                    }
                }
            }
        }
    }
}

// --- DIALOGS DE EDICAO E CONFIGURACAO ---

@Composable
fun EditProfileDialog(
    authState: AuthManager.AuthState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentAuth = authState
    val emailText = when (currentAuth) {
        is AuthManager.AuthState.Authenticated -> currentAuth.user.email ?: "Autenticado via Google"
        is AuthManager.AuthState.Guest -> "Modo Convidado (Local)"
        else -> "Visitante"
    }
    var nameInput by remember {
        mutableStateOf(
            when (currentAuth) {
                is AuthManager.AuthState.Authenticated -> currentAuth.user.displayName ?: currentAuth.user.email?.substringBefore("@") ?: "Usuário"
                else -> "Convidado"
            }
        )
    }
    var phoneInput by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    // TODO: decidir se implementa (campos foto, nome, telefone visuais sem persistência no banco nesta versão)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Editar Perfil",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Nome completo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = emailText,
                    onValueChange = {},
                    label = { Text("E-mail (não editável)") },
                    enabled = false,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = { Text("Telefone (opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                Text(
                    text = "Alterar Senha",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Senha atual") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Nova senha") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                            Toast.makeText(context, "Perfil atualizado!", Toast.LENGTH_SHORT).show()
                            onDismiss()
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
fun AppearanceDialog(
    currentTheme: String,
    onThemeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Aparência e Tema",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                val options = listOf(
                    "SYSTEM" to "Seguir Sistema",
                    "LIGHT" to "Tema Claro",
                    "DARK" to "Tema Escuro"
                )

                options.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onThemeSelected(mode) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTheme == mode,
                            onClick = { onThemeSelected(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Fechar") }
                }
            }
        }
    }
}

@Composable
fun NotificationsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val notifyLimits by viewModel.userPreferences.notifyLimitsFlow.collectAsStateWithLifecycle(initialValue = true)
    val notifyCreditCard by viewModel.userPreferences.notifyCreditCardFlow.collectAsStateWithLifecycle(initialValue = true)
    val notifyInstallment by viewModel.userPreferences.notifyInstallmentFlow.collectAsStateWithLifecycle(initialValue = true)
    val notifyGoal by viewModel.userPreferences.notifyGoalFlow.collectAsStateWithLifecycle(initialValue = true)
    val notifyWeeklyReview by viewModel.userPreferences.notifyWeeklyReviewFlow.collectAsStateWithLifecycle(initialValue = true)
    val notifySyncFailure by viewModel.userPreferences.notifySyncFailureFlow.collectAsStateWithLifecycle(initialValue = true)

    val creditCardDaysBefore by viewModel.userPreferences.creditCardDaysBeforeFlow.collectAsStateWithLifecycle(initialValue = 3)
    val weeklyReviewDay by viewModel.userPreferences.weeklyReviewDayFlow.collectAsStateWithLifecycle(initialValue = 1)
    val weeklyReviewTime by viewModel.userPreferences.weeklyReviewTimeFlow.collectAsStateWithLifecycle(initialValue = "20:00")

    var cardDaysBeforeInput by remember(creditCardDaysBefore) { mutableStateOf(creditCardDaysBefore.toString()) }
    var weeklyReviewTimeInput by remember(weeklyReviewTime) { mutableStateOf(weeklyReviewTime) }
    var dayDropdownExpanded by remember { mutableStateOf(false) }

    val daysOfWeek = listOf("Domingo", "Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira", "Sexta-feira", "Sábado")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Notificações e Alertas",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Text(
                    text = "Escolha quais notificações deseja receber em tempo real.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                // 1. Limites
                NotificationToggleRow(
                    title = "Limites de Categorias (80% / 100%)",
                    subtitle = "Alerta ao atingir ou ultrapassar limites orçamentários",
                    checked = notifyLimits,
                    onCheckedChange = { scope.launch { viewModel.userPreferences.setNotifyLimits(it) } }
                )

                // 2. Cartão de crédito
                NotificationToggleRow(
                    title = "Faturas de Cartão",
                    subtitle = "Alerta faturas próximas do vencimento",
                    checked = notifyCreditCard,
                    onCheckedChange = { scope.launch { viewModel.userPreferences.setNotifyCreditCard(it) } }
                )

                if (notifyCreditCard) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Dias antes do vencimento:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                            modifier = Modifier.width(70.dp),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // 3. Parcelas
                NotificationToggleRow(
                    title = "Parcelas a Vencer",
                    subtitle = "Notifica parcelas vencendo nos próximos 3 dias",
                    checked = notifyInstallment,
                    onCheckedChange = { scope.launch { viewModel.userPreferences.setNotifyInstallment(it) } }
                )

                // 4. Metas
                NotificationToggleRow(
                    title = "Metas Alcançadas",
                    subtitle = "Parabeniza ao atingir ou superar uma meta",
                    checked = notifyGoal,
                    onCheckedChange = { scope.launch { viewModel.userPreferences.setNotifyGoal(it) } }
                )

                // 5. Revisão Semanal
                NotificationToggleRow(
                    title = "Revisão Semanal",
                    subtitle = "Resumo semanal contendo estatísticas financeiras",
                    checked = notifyWeeklyReview,
                    onCheckedChange = { scope.launch { viewModel.userPreferences.setNotifyWeeklyReview(it) } }
                )

                if (notifyWeeklyReview) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Dia:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Box {
                                val currentDayName = daysOfWeek.getOrNull(weeklyReviewDay - 1) ?: "Selecione"
                                Button(
                                    onClick = { dayDropdownExpanded = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                ) {
                                    Text(currentDayName, fontSize = 12.sp)
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

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Horário (HH:mm):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = weeklyReviewTimeInput,
                                onValueChange = { newValue ->
                                    weeklyReviewTimeInput = newValue
                                    if (newValue.matches(Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$"))) {
                                        scope.launch { viewModel.userPreferences.setWeeklyReviewTime(newValue) }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.width(90.dp),
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // 6. Falha de sincronização (Novo)
                NotificationToggleRow(
                    title = "Falhas de Sincronização",
                    subtitle = "Notifica caso ocorra erro ao sincronizar dados com a nuvem",
                    checked = notifySyncFailure,
                    onCheckedChange = { scope.launch { viewModel.userPreferences.setNotifySyncFailure(it) } }
                )

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Salvar e Fechar")
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
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag("toggle_${title.replace(" ", "_").lowercase()}")
        )
    }
}

@Composable
fun RecurrenceRulesDialog(
    rules: List<RecurrenceRule>,
    categories: List<Category>,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onEditRule: (RecurrenceRule) -> Unit
) {
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transações Recorrentes",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Text(
                    text = "Lançamentos automáticos configurados para se repetirem periodicamente.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val activeRules = rules.filter { it.active }

                if (activeRules.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhuma transação recorrente ativa.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(activeRules, key = { it.id }) { rule ->
                            val catName = categories.find { it.id == rule.category_id }?.name ?: "Sem categoria"
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = rule.description.ifBlank { "Transação Recorrente" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Categoria: $catName • Frequência: ${rule.frequency}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "R$ %.2f | Início: %s | Fim: %s".format(
                                                rule.value,
                                                rule.start_date,
                                                rule.end_month ?: "Indefinido"
                                            ),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (rule.type == "RECEITA") Color(0xFF22A45D) else Color(0xFFEF4444)
                                        )
                                    }

                                    Row {
                                        IconButton(onClick = { onEditRule(rule) }) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Editar",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    viewModel.repository.updateRecurrenceRule(rule.copy(active = false))
                                                    viewModel.triggerPush()
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Desativar",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Concluído")
                }
            }
        }
    }
}

@Composable
fun EditRecurrenceRuleDialog(
    rule: RecurrenceRule,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (RecurrenceRule) -> Unit
) {
    var description by remember { mutableStateOf(rule.description) }
    var valueStr by remember { mutableStateOf(rule.value.toString()) }
    var endMonth by remember { mutableStateOf(rule.end_month ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Editar Regra Recorrente",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = valueStr,
                    onValueChange = { valueStr = it },
                    label = { Text("Valor (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = endMonth,
                    onValueChange = { endMonth = it },
                    label = { Text("Mês Final (AAAA-MM) ou Vazio") },
                    placeholder = { Text("Ex: 2026-12") },
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
                            val v = valueStr.toDoubleOrNull() ?: rule.value
                            onSave(
                                rule.copy(
                                    description = description,
                                    value = v,
                                    end_month = endMonth.ifBlank { null }
                                )
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
fun AccountsCrudDialog(
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onAddAccount: () -> Unit,
    onEditAccount: (Account) -> Unit,
    onDeleteAccount: (Account) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gerenciar Contas", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Button(
                    onClick = onAddAccount,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(accounts, key = { it.id }) { acc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(acc.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = "Tipo: ${acc.type.replace("_", " ")} | Saldo: R$ %.2f".format(acc.initial_balance),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                Row {
                                    IconButton(onClick = { onEditAccount(acc) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { onDeleteAccount(acc) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Concluído")
                }
            }
        }
    }
}

@Composable
fun CategoriesCrudDialog(
    categories: List<Category>,
    subcategories: List<Subcategory>,
    onDismiss: () -> Unit,
    onOpenTemplateSelector: () -> Unit,
    onToggleArchiveCategory: (Category) -> Unit,
    onToggleArchiveSubcategory: (Subcategory) -> Unit,
    onAddCategory: () -> Unit,
    onEditCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onAddSubcategory: () -> Unit,
    onEditSubcategory: (Subcategory) -> Unit,
    onDeleteSubcategory: (Subcategory) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Categorias e Subcategorias", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                OutlinedButton(
                    onClick = onOpenTemplateSelector,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Adicionar do modelo sugerido", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                // Category Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Categorias", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    TextButton(onClick = onAddCategory) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nova Categoria", fontSize = 12.sp)
                    }
                }

                if (categories.isEmpty()) {
                    Text("Nenhuma categoria criada.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    categories.forEach { cat ->
                        val isArchived = cat.archived
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isArchived) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val displayName = if (!cat.icon.isNullOrBlank()) "${cat.icon} ${cat.name}" else cat.name
                                    Text(
                                        displayName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = if (isArchived) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isArchived) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                "Arquivada",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onToggleArchiveCategory(cat) }) {
                                    Icon(
                                        imageVector = if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                        contentDescription = if (isArchived) "Desarquivar" else "Arquivar",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isArchived) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onEditCategory(cat) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { onDeleteCategory(cat) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Subcategory Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Subcategorias", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    TextButton(onClick = onAddSubcategory) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nova Subcategoria", fontSize = 12.sp)
                    }
                }

                if (subcategories.isEmpty()) {
                    Text("Nenhuma subcategoria criada.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    subcategories.forEach { sub ->
                        val isArchived = sub.archived
                        val catName = categories.find { it.id == sub.category_id }?.name ?: "Sem categoria"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isArchived) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val subDisplayName = if (!sub.icon.isNullOrBlank()) "${sub.icon} ${sub.name}" else sub.name
                                    Text(
                                        subDisplayName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = if (isArchived) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isArchived) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                "Arquivada",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text("Categoria: $catName", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onToggleArchiveSubcategory(sub) }) {
                                    Icon(
                                        imageVector = if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                        contentDescription = if (isArchived) "Desarquivar" else "Arquivar",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isArchived) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onEditSubcategory(sub) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { onDeleteSubcategory(sub) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Concluído")
                }
            }
        }
    }
}

@Composable
fun SyncSettingsDialog(
    viewModel: MainViewModel,
    syncState: MainViewModel.SyncState,
    syncLogs: List<String>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Backup e Sincronização", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                val statusText = when (syncState) {
                    is MainViewModel.SyncState.Syncing -> "Sincronizando..."
                    is MainViewModel.SyncState.Success -> "Sincronizado"
                    is MainViewModel.SyncState.Error -> "Erro na sincronização"
                    else -> "Pronto"
                }
                Text("Status atual: $statusText", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.triggerPush() },
                        modifier = Modifier.weight(1f),
                        enabled = viewModel.currentUserId != "GUEST"
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Enviar", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { viewModel.triggerPull() },
                        modifier = Modifier.weight(1f),
                        enabled = viewModel.currentUserId != "GUEST"
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Baixar", fontSize = 12.sp)
                    }
                }

                if (viewModel.currentUserId == "GUEST") {
                    Text(
                        text = "Sincronização desativada no modo Convidado.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("Registro de Auditoria (Sync Logs)", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    if (syncLogs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Nenhum evento registrado.", fontSize = 12.sp)
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
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Fechar")
                }
            }
        }
    }
}

@Composable
fun ExportDataDialog(
    viewModel: MainViewModel,
    accounts: List<Account>,
    transactions: List<com.example.data.model.Transaction>,
    categories: List<Category>,
    subcategories: List<Subcategory>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = viewModel.currentUserId

    val currentMonth = remember {
        java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
    }

    var exportAllMonths by remember { mutableStateOf(true) }
    var startMonth by remember { mutableStateOf(currentMonth) }
    var endMonth by remember { mutableStateOf(currentMonth) }
    var showStartMonthPicker by remember { mutableStateOf(false) }
    var showEndMonthPicker by remember { mutableStateOf(false) }

    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var selectedSubcategory by remember { mutableStateOf<Subcategory?>(null) }

    var expandedCategoryDropdown by remember { mutableStateOf(false) }
    var expandedSubcategoryDropdown by remember { mutableStateOf(false) }

    val startCal = remember(startMonth) {
        Calendar.getInstance().apply {
            try {
                val parts = startMonth.split("-")
                set(Calendar.YEAR, parts[0].toInt())
                set(Calendar.MONTH, parts[1].toInt() - 1)
            } catch (e: Exception) {}
        }
    }

    val endCal = remember(endMonth) {
        Calendar.getInstance().apply {
            try {
                val parts = endMonth.split("-")
                set(Calendar.YEAR, parts[0].toInt())
                set(Calendar.MONTH, parts[1].toInt() - 1)
            } catch (e: Exception) {}
        }
    }

    if (showStartMonthPicker) {
        com.example.ui.screens.MonthYearPickerDialog(
            currentCalendar = startCal,
            onDismiss = { showStartMonthPicker = false },
            onSelected = { y, m ->
                startMonth = "%04d-%02d".format(y, m + 1)
                showStartMonthPicker = false
            }
        )
    }

    if (showEndMonthPicker) {
        com.example.ui.screens.MonthYearPickerDialog(
            currentCalendar = endCal,
            onDismiss = { showEndMonthPicker = false },
            onSelected = { y, m ->
                endMonth = "%04d-%02d".format(y, m + 1)
                showEndMonthPicker = false
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Exportar Dados", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Text(
                    text = "Gere relatórios e comprovantes em PDF, planilhas CSV/Excel ou backup completo em JSON.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // --- FILTRO DE PERÍODO ---
                Text("Filtro de Período", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportAllMonths = !exportAllMonths }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = exportAllMonths,
                        onCheckedChange = { exportAllMonths = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Exportar todo o período (Todos os meses)", fontSize = 13.sp)
                }

                if (!exportAllMonths) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showStartMonthPicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("De: ${ExportHelper.formatMonthPtBr(startMonth)}", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { showEndMonthPicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Até: ${ExportHelper.formatMonthPtBr(endMonth)}", fontSize = 11.sp)
                        }
                    }
                }

                HorizontalDivider()

                // --- FILTRO DE CATEGORIA E SUBCATEGORIA ---
                Text("Filtro por Categoria e Subcategoria", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                // Dropdown Categoria
                Column {
                    Text("Categoria", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        OutlinedButton(
                            onClick = { expandedCategoryDropdown = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedCategory?.name ?: "Todas as Categorias",
                                    fontSize = 13.sp
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(
                            expanded = expandedCategoryDropdown,
                            onDismissRequest = { expandedCategoryDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Todas as Categorias", fontWeight = if (selectedCategory == null) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    selectedCategory = null
                                    selectedSubcategory = null
                                    expandedCategoryDropdown = false
                                }
                            )
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name, fontWeight = if (selectedCategory?.id == cat.id) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        selectedCategory = cat
                                        if (selectedSubcategory != null && selectedSubcategory?.category_id != cat.id) {
                                            selectedSubcategory = null
                                        }
                                        expandedCategoryDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Dropdown Subcategoria
                val filteredSubcategories = remember(selectedCategory, subcategories) {
                    if (selectedCategory == null) subcategories
                    else subcategories.filter { it.category_id == selectedCategory?.id }
                }

                Column {
                    Text("Subcategoria", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        OutlinedButton(
                            onClick = { expandedSubcategoryDropdown = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedSubcategory?.name ?: "Todas as Subcategorias",
                                    fontSize = 13.sp
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(
                            expanded = expandedSubcategoryDropdown,
                            onDismissRequest = { expandedSubcategoryDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Todas as Subcategorias", fontWeight = if (selectedSubcategory == null) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    selectedSubcategory = null
                                    expandedSubcategoryDropdown = false
                                }
                            )
                            filteredSubcategories.forEach { sub ->
                                DropdownMenuItem(
                                    text = { Text(sub.name, fontWeight = if (selectedSubcategory?.id == sub.id) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        selectedSubcategory = sub
                                        expandedSubcategoryDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // --- OPÇÕES DE EXPORTAÇÃO ---
                Text("Opções de Exportação", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                val effectiveStartMonth = if (exportAllMonths) "" else startMonth
                val effectiveEndMonth = if (exportAllMonths) "" else endMonth

                // Exportar PDF (Comprovantes / Relatório)
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val file = ExportHelper.exportToPdf(
                                    context = context,
                                    startMonth = effectiveStartMonth,
                                    endMonth = effectiveEndMonth,
                                    selectedCategory = selectedCategory,
                                    selectedSubcategory = selectedSubcategory,
                                    transactions = transactions,
                                    categories = categories,
                                    subcategories = subcategories
                                )
                                if (file != null) {
                                    val authority = "${context.packageName}.fileprovider"
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Comprovantes e Extrato PDF")
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Exportar PDF"))
                                } else {
                                    Toast.makeText(context, "Nenhum comprovante/transação encontrada para os filtros selecionados.", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "Erro ao gerar PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exportar PDF (Comprovantes)")
                }

                // Exportar CSV
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val file = ExportHelper.exportToCsv(
                                    context = context,
                                    startMonth = effectiveStartMonth,
                                    endMonth = effectiveEndMonth,
                                    accounts = accounts,
                                    transactions = transactions,
                                    categories = categories,
                                    subcategories = subcategories
                                )
                                if (file != null) {
                                    val authority = "${context.packageName}.fileprovider"
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Exportar CSV"))
                                } else {
                                    Toast.makeText(context, "Nenhum dado encontrado para exportação.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Exportar CSV (Planilha)")
                }

                // Exportar Backup JSON
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                val jsonContent = viewModel.exportAllDataJson(userId)
                                val backupFile = File(context.cacheDir, "meu_financeiro_backup.json")
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
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Meu Financeiro - Backup JSON")
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }

                                context.startActivity(
                                    android.content.Intent.createChooser(shareIntent, "Compartilhar Backup JSON")
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Exportar Tudo (Backup JSON)")
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Fechar")
                }
            }
        }
    }
}

@Composable
fun ClearDataDialog(
    onDismiss: () -> Unit,
    onConfirmClear: () -> Unit
) {
    var confirmationInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Limpar Todos os Dados",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Atenção: esta ação é irreversível. Todas as suas contas, transações, categorias, subcategorias, metas e planejamentos serão apagados do seu dispositivo local.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Para confirmar, digite 'LIMPAR' no campo abaixo:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = confirmationInput,
                    onValueChange = { confirmationInput = it },
                    singleLine = true,
                    placeholder = { Text("LIMPAR") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmClear,
                enabled = confirmationInput.trim().equals("LIMPAR", ignoreCase = true),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Apagar Tudo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun SecuritySettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val securityEnabled by viewModel.securityEnabled.collectAsStateWithLifecycle()
    val authMethod by viewModel.authMethod.collectAsStateWithLifecycle()
    val isBiometricAvailable = remember { viewModel.isBiometricAvailable() }

    var showPinSetupDialog by remember { mutableStateOf(false) }
    var pendingAuthMethodSelection by remember { mutableStateOf<String?>(null) }

    if (showPinSetupDialog) {
        PinSetupDialog(
            isChange = viewModel.securityManager.hasPin(),
            onDismiss = {
                showPinSetupDialog = false
                pendingAuthMethodSelection = null
            },
            onSavePin = { newPin ->
                viewModel.setPin(newPin)
                viewModel.setSecurityEnabled(true)
                if (pendingAuthMethodSelection != null) {
                    viewModel.setAuthMethod(pendingAuthMethodSelection!!)
                    pendingAuthMethodSelection = null
                }
                showPinSetupDialog = false
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Segurança e Acesso", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Proteger com senha/biometria", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Exige autenticação ao abrir o aplicativo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = securityEnabled,
                        onCheckedChange = { enable ->
                            if (enable) {
                                if (!viewModel.securityManager.hasPin()) {
                                    showPinSetupDialog = true
                                } else {
                                    viewModel.setSecurityEnabled(true)
                                }
                            } else {
                                viewModel.setSecurityEnabled(false)
                            }
                        }
                    )
                }

                if (securityEnabled) {
                    HorizontalDivider()

                    Text("Método de Autenticação", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (!viewModel.securityManager.hasPin()) {
                                    pendingAuthMethodSelection = "PIN"
                                    showPinSetupDialog = true
                                } else {
                                    viewModel.setAuthMethod("PIN")
                                }
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = authMethod == "PIN",
                            onClick = {
                                if (!viewModel.securityManager.hasPin()) {
                                    pendingAuthMethodSelection = "PIN"
                                    showPinSetupDialog = true
                                } else {
                                    viewModel.setAuthMethod("PIN")
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PIN de 4-6 dígitos", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (isBiometricAvailable) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (!viewModel.securityManager.hasPin()) {
                                        pendingAuthMethodSelection = "BIOMETRIC"
                                        showPinSetupDialog = true
                                    } else {
                                        viewModel.setAuthMethod("BIOMETRIC")
                                    }
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = authMethod == "BIOMETRIC",
                                onClick = {
                                    if (!viewModel.securityManager.hasPin()) {
                                        pendingAuthMethodSelection = "BIOMETRIC"
                                        showPinSetupDialog = true
                                    } else {
                                        viewModel.setAuthMethod("BIOMETRIC")
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Biometria do Aparelho", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    OutlinedButton(
                        onClick = { showPinSetupDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (viewModel.securityManager.hasPin()) "Alterar PIN" else "Cadastrar PIN")
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Concluído")
                }
            }
        }
    }
}

@Composable
fun HelpCenterDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Central de Ajuda & FAQ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                FaqItem(
                    question = "Como funciona o 'Pronto para Atribuir'?",
                    answer = "É o valor total de receitas e saldos acumulados ainda não alocados em envelopes de categorias ou metas."
                )

                FaqItem(
                    question = "O que são Transações Recorrentes?",
                    answer = "São despesas ou receitas fixas (como aluguel, salários ou assinaturas) que ocorrem mensalmente."
                )

                FaqItem(
                    question = "Meus dados estão seguros?",
                    answer = "Sim. Os dados são salvos localmente no banco Room e sincronizados com criptografia no Firebase Firestore."
                )

                FaqItem(
                    question = "Como exportar meus relatórios?",
                    answer = "Em Ajustes -> Dados e backup -> Exportar dados, você pode gerar relatórios em JSON, CSV ou PDF."
                )

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Entendi")
                }
            }
        }
    }
}

@Composable
fun FaqItem(question: String, answer: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = question, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(text = answer, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LogoutConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirmLogout: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Sair da Conta",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Deseja realmente encerrar a sessão? Seus dados sincronizados na nuvem ou locais permanecerão salvos com segurança.",
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Sair")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
