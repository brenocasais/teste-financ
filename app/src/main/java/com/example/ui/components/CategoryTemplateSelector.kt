package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Category
import com.example.data.model.Subcategory

data class CategoryTemplateItem(
    val icon: String,
    val name: String,
    val subcategories: List<SubcategoryTemplateItem>
)

data class SubcategoryTemplateItem(
    val icon: String,
    val name: String
)

data class CategoryTemplateSelection(
    val category: CategoryTemplateItem,
    val selectedSubcategories: List<SubcategoryTemplateItem>
)

object CategoryTemplates {
    val defaultList = listOf(
        CategoryTemplateItem(
            icon = "🏠",
            name = "Moradia",
            subcategories = listOf(
                SubcategoryTemplateItem("🏘️", "Aluguel"),
                SubcategoryTemplateItem("🏦", "Financiamento"),
                SubcategoryTemplateItem("🏢", "Condomínio"),
                SubcategoryTemplateItem("💡", "Energia Elétrica"),
                SubcategoryTemplateItem("💧", "Água"),
                SubcategoryTemplateItem("🔥", "Gás"),
                SubcategoryTemplateItem("🌐", "Internet")
            )
        ),
        CategoryTemplateItem(
            icon = "🍽️",
            name = "Alimentação",
            subcategories = listOf(
                SubcategoryTemplateItem("🛒", "Supermercado"),
                SubcategoryTemplateItem("🍔", "Restaurante"),
                SubcategoryTemplateItem("🛵", "Delivery"),
                SubcategoryTemplateItem("🥖", "Padaria")
            )
        ),
        CategoryTemplateItem(
            icon = "🚗",
            name = "Transporte",
            subcategories = listOf(
                SubcategoryTemplateItem("⛽", "Combustível"),
                SubcategoryTemplateItem("🚌", "Transporte Público"),
                SubcategoryTemplateItem("🚕", "Apps de Transporte"),
                SubcategoryTemplateItem("🔧", "Manutenção"),
                SubcategoryTemplateItem("🅿️", "Estacionamento")
            )
        ),
        CategoryTemplateItem(
            icon = "🏥",
            name = "Saúde",
            subcategories = listOf(
                SubcategoryTemplateItem("⚕️", "Plano de Saúde"),
                SubcategoryTemplateItem("💊", "Farmácia"),
                SubcategoryTemplateItem("🩺", "Consultas"),
                SubcategoryTemplateItem("🔬", "Exames"),
                SubcategoryTemplateItem("💪", "Academia")
            )
        ),
        CategoryTemplateItem(
            icon = "📚",
            name = "Educação",
            subcategories = listOf(
                SubcategoryTemplateItem("🎓", "Mensalidade Escolar"),
                SubcategoryTemplateItem("📖", "Cursos"),
                SubcategoryTemplateItem("✏️", "Material Escolar"),
                SubcategoryTemplateItem("📕", "Livros")
            )
        ),
        CategoryTemplateItem(
            icon = "🎉",
            name = "Lazer",
            subcategories = listOf(
                SubcategoryTemplateItem("📺", "Streaming"),
                SubcategoryTemplateItem("🎬", "Cinema"),
                SubcategoryTemplateItem("🎤", "Shows"),
                SubcategoryTemplateItem("✈️", "Viagens"),
                SubcategoryTemplateItem("🎨", "Hobbies")
            )
        ),
        CategoryTemplateItem(
            icon = "🛍️",
            name = "Compras",
            subcategories = listOf(
                SubcategoryTemplateItem("👕", "Roupas"),
                SubcategoryTemplateItem("👟", "Calçados"),
                SubcategoryTemplateItem("💻", "Eletrônicos"),
                SubcategoryTemplateItem("🛋️", "Casa e Decoração")
            )
        ),
        CategoryTemplateItem(
            icon = "📱",
            name = "Assinaturas e Contas",
            subcategories = listOf(
                SubcategoryTemplateItem("📞", "Celular"),
                SubcategoryTemplateItem("🛡️", "Seguro"),
                SubcategoryTemplateItem("☁️", "Serviços Digitais"),
                SubcategoryTemplateItem("🏦", "Tarifas Bancárias")
            )
        ),
        CategoryTemplateItem(
            icon = "💰",
            name = "Investimentos",
            subcategories = listOf(
                SubcategoryTemplateItem("📈", "Investimentos"),
                SubcategoryTemplateItem("🪙", "Reserva Financeira")
            )
        ),
        CategoryTemplateItem(
            icon = "🧾",
            name = "Impostos e Taxas",
            subcategories = listOf(
                SubcategoryTemplateItem("🚙", "IPVA"),
                SubcategoryTemplateItem("🏠", "IPTU"),
                SubcategoryTemplateItem("💼", "Imposto de Renda")
            )
        ),
        CategoryTemplateItem(
            icon = "💇",
            name = "Cuidados Pessoais",
            subcategories = listOf(
                SubcategoryTemplateItem("💇", "Cabelo e Salão"),
                SubcategoryTemplateItem("🧴", "Higiene e Beleza")
            )
        ),
        CategoryTemplateItem(
            icon = "🐾",
            name = "Pets",
            subcategories = listOf(
                SubcategoryTemplateItem("🦴", "Ração"),
                SubcategoryTemplateItem("🐕‍🦺", "Veterinário"),
                SubcategoryTemplateItem("🧼", "Banho e Tosa")
            )
        ),
        CategoryTemplateItem(
            icon = "🎁",
            name = "Presentes e Doações",
            subcategories = listOf(
                SubcategoryTemplateItem("🎁", "Presentes"),
                SubcategoryTemplateItem("❤️", "Doações")
            )
        ),
        CategoryTemplateItem(
            icon = "👶",
            name = "Filhos",
            subcategories = listOf(
                SubcategoryTemplateItem("🍼", "Fralda e Higiene"),
                SubcategoryTemplateItem("🏫", "Escola Infantil"),
                SubcategoryTemplateItem("🧸", "Brinquedos")
            )
        )
    )
}

@Composable
fun CategoryTemplateSelectorContent(
    existingCategories: List<Category> = emptyList(),
    existingSubcategories: List<Subcategory> = emptyList(),
    onConfirm: (List<CategoryTemplateSelection>) -> Unit,
    onSkip: (() -> Unit)? = null,
    confirmButtonText: String = "Criar categorias selecionadas",
    titleText: String = "Template de Categorias Sugerido",
    subtitleText: String = "Selecione as categorias e subcategorias que deseja adicionar:",
    modifier: Modifier = Modifier
) {
    val templateList = CategoryTemplates.defaultList

    val existingCatMap = remember(existingCategories) {
        existingCategories.associateBy { it.name.trim().lowercase() }
    }

    fun isSubcategoryInDb(catName: String, subName: String): Boolean {
        val existingCat = existingCatMap[catName.trim().lowercase()] ?: return false
        return existingSubcategories.any {
            it.category_id == existingCat.id && it.name.trim().equals(subName.trim(), ignoreCase = true)
        }
    }

    fun isCategoryInDb(catName: String): Boolean {
        return existingCatMap.containsKey(catName.trim().lowercase())
    }

    val selectedSubcategoriesMap = remember(existingCategories, existingSubcategories) {
        mutableStateMapOf<String, MutableSet<String>>().apply {
            templateList.forEach { cat ->
                val nonExistingSubs = cat.subcategories
                    .filter { !isSubcategoryInDb(cat.name, it.name) }
                    .map { it.name }
                    .toMutableSet()
                if (nonExistingSubs.isNotEmpty()) {
                    put(cat.name, nonExistingSubs)
                }
            }
        }
    }

    val expandedCategories = remember { mutableStateMapOf<String, Boolean>() }

    val totalCategoriesSelected = selectedSubcategoriesMap.filter { it.value.isNotEmpty() }.size
    val totalSubcategoriesSelected = selectedSubcategoriesMap.values.sumOf { it.size }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (titleText.isNotBlank()) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (subtitleText.isNotBlank()) {
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$totalSubcategoriesSelected subcategorias em $totalCategoriesSelected categorias",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        templateList.forEach { cat ->
                            val subs = cat.subcategories
                                .filter { !isSubcategoryInDb(cat.name, it.name) }
                                .map { it.name }
                                .toMutableSet()
                            if (subs.isNotEmpty()) {
                                selectedSubcategoriesMap[cat.name] = subs
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text("Marcar todas", fontSize = 11.5.sp)
                }
                TextButton(
                    onClick = {
                        selectedSubcategoriesMap.clear()
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text("Desmarcar todas", fontSize = 11.5.sp)
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(templateList, key = { it.name }) { cat ->
                    val catName = cat.name
                    val catIcon = cat.icon
                    val isCatInDb = isCategoryInDb(catName)
                    val allSubsInDb = cat.subcategories.all { isSubcategoryInDb(catName, it.name) }

                    val selectedSubsSet = selectedSubcategoriesMap[catName] ?: emptySet()
                    val nonExistingSubs = cat.subcategories.filter { !isSubcategoryInDb(catName, it.name) }
                    val isCatChecked = selectedSubsSet.isNotEmpty()
                    val isExpanded = expandedCategories[catName] == true

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(vertical = 2.dp, horizontal = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedCategories[catName] = !isExpanded
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isCatChecked,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        val subsToAdd = nonExistingSubs.map { it.name }.toMutableSet()
                                        if (subsToAdd.isNotEmpty()) {
                                            selectedSubcategoriesMap[catName] = subsToAdd
                                        }
                                    } else {
                                        selectedSubcategoriesMap.remove(catName)
                                    }
                                },
                                enabled = !allSubsInDb,
                                modifier = Modifier.testTag("checkbox_cat_${catName}")
                            )

                            Text(
                                text = "$catIcon $catName",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = if (allSubsInDb) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            if (allSubsInDb) {
                                Surface(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = "Já existe",
                                        fontSize = 10.5.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else if (isCatInDb) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = "Existe em parte",
                                        fontSize = 10.5.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Text(
                                text = "${selectedSubsSet.size}/${cat.subcategories.size}",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp)
                            )

                            IconButton(
                                onClick = { expandedCategories[catName] = !isExpanded },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) "Recolher" else "Expandir",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isExpanded) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            Column(
                                modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                cat.subcategories.forEach { sub ->
                                    val isSubInDb = isSubcategoryInDb(catName, sub.name)
                                    val isSubChecked = selectedSubsSet.contains(sub.name)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !isSubInDb) {
                                                val currentSet = selectedSubcategoriesMap[catName]?.toMutableSet() ?: mutableSetOf()
                                                if (isSubChecked) {
                                                    currentSet.remove(sub.name)
                                                } else {
                                                    currentSet.add(sub.name)
                                                }
                                                if (currentSet.isEmpty()) {
                                                    selectedSubcategoriesMap.remove(catName)
                                                } else {
                                                    selectedSubcategoriesMap[catName] = currentSet
                                                }
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSubChecked,
                                            onCheckedChange = { checked ->
                                                val currentSet = selectedSubcategoriesMap[catName]?.toMutableSet() ?: mutableSetOf()
                                                if (checked) {
                                                    currentSet.add(sub.name)
                                                } else {
                                                    currentSet.remove(sub.name)
                                                }
                                                if (currentSet.isEmpty()) {
                                                    selectedSubcategoriesMap.remove(catName)
                                                } else {
                                                    selectedSubcategoriesMap[catName] = currentSet
                                                }
                                            },
                                            enabled = !isSubInDb,
                                            modifier = Modifier.testTag("checkbox_sub_${sub.name}")
                                        )

                                        Text(
                                            text = "${sub.icon} ${sub.name}",
                                            fontSize = 13.sp,
                                            color = if (isSubInDb) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (isSubInDb) {
                                            Text(
                                                text = "Já existe",
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.padding(end = 8.dp)
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onSkip != null) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Pular")
                }
            }

            Button(
                onClick = {
                    val selections = templateList.mapNotNull { cat ->
                        val selectedSubNames = selectedSubcategoriesMap[cat.name] ?: emptySet()
                        if (selectedSubNames.isNotEmpty()) {
                            val selectedSubItems = cat.subcategories.filter { selectedSubNames.contains(it.name) }
                            CategoryTemplateSelection(
                                category = cat,
                                selectedSubcategories = selectedSubItems
                            )
                        } else null
                    }
                    onConfirm(selections)
                },
                modifier = Modifier
                    .weight(if (onSkip != null) 2f else 1f)
                    .height(46.dp),
                shape = RoundedCornerShape(10.dp),
                enabled = totalSubcategoriesSelected > 0
            ) {
                Text(
                    text = confirmButtonText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CategoryTemplateSelectorDialog(
    existingCategories: List<Category>,
    existingSubcategories: List<Subcategory>,
    onDismiss: () -> Unit,
    onConfirm: (List<CategoryTemplateSelection>) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Modelo Sugerido de Categorias", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                CategoryTemplateSelectorContent(
                    existingCategories = existingCategories,
                    existingSubcategories = existingSubcategories,
                    titleText = "",
                    subtitleText = "Selecione as categorias do modelo para adicionar ao seu aplicativo:",
                    confirmButtonText = "Adicionar Selecionadas",
                    onConfirm = { selections ->
                        onConfirm(selections)
                        onDismiss()
                    },
                    onSkip = onDismiss,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
