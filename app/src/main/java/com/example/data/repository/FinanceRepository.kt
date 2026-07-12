package com.example.data.repository

import android.util.Log
import com.example.data.db.*
import com.example.data.model.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.Date

class FinanceRepository(
    private val accountDao: AccountDao,
    private val envelopeGroupDao: EnvelopeGroupDao,
    private val categoryDao: CategoryDao,
    private val subcategoryDao: SubcategoryDao
) {
    // --- ACCOUNTS ---
    fun getAccountsFlow(userId: String): Flow<List<Account>> = accountDao.getAccountsFlow(userId)
    suspend fun getAllAccounts(userId: String): List<Account> = accountDao.getAllAccounts(userId)
    suspend fun insertAccount(account: Account): Long = accountDao.insert(account)
    suspend fun updateAccount(account: Account) = accountDao.update(account)
    suspend fun deleteAccount(account: Account) = accountDao.delete(account)

    // --- ENVELOPE GROUPS ---
    fun getEnvelopeGroupsFlow(userId: String): Flow<List<EnvelopeGroup>> = envelopeGroupDao.getEnvelopeGroupsFlow(userId)
    suspend fun getAllEnvelopeGroups(userId: String): List<EnvelopeGroup> = envelopeGroupDao.getAllEnvelopeGroups(userId)
    suspend fun insertEnvelopeGroup(group: EnvelopeGroup): Long = envelopeGroupDao.insert(group)
    suspend fun updateEnvelopeGroup(group: EnvelopeGroup) = envelopeGroupDao.update(group)
    suspend fun deleteEnvelopeGroup(group: EnvelopeGroup) = envelopeGroupDao.delete(group)

    // --- CATEGORIES ---
    fun getCategoriesFlow(userId: String): Flow<List<Category>> = categoryDao.getCategoriesFlow(userId)
    suspend fun getAllCategories(userId: String): List<Category> = categoryDao.getAllCategories(userId)
    suspend fun insertCategory(category: Category): Long = categoryDao.insert(category)
    suspend fun updateCategory(category: Category) = categoryDao.update(category)
    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)

    // --- SUBCATEGORIES ---
    fun getSubcategoriesFlow(userId: String): Flow<List<Subcategory>> = subcategoryDao.getSubcategoriesFlow(userId)
    suspend fun getAllSubcategories(userId: String): List<Subcategory> = subcategoryDao.getAllSubcategories(userId)
    suspend fun insertSubcategory(subcategory: Subcategory): Long = subcategoryDao.insert(subcategory)
    suspend fun updateSubcategory(subcategory: Subcategory) = subcategoryDao.update(subcategory)
    suspend fun deleteSubcategory(subcategory: Subcategory) = subcategoryDao.delete(subcategory)

    // --- FIRESTORE SYNC ---
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun syncPush(userId: String): Boolean {
        if (userId.isEmpty() || userId == "GUEST") return false
        return try {
            val accounts = getAllAccounts(userId).map { it.toMap() }
            val groups = getAllEnvelopeGroups(userId).map { it.toMap() }
            val categories = getAllCategories(userId).map { it.toMap() }
            val subcategories = getAllSubcategories(userId).map { it.toMap() }

            val syncData = mapOf(
                "accounts" to accounts,
                "envelope_groups" to groups,
                "categories" to categories,
                "subcategories" to subcategories,
                "last_updated" to Date()
            )

            firestore.collection("users").document(userId).set(syncData).await()
            Log.d("FinanceRepository", "Push sync succeeded for $userId")
            true
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Push sync failed", e)
            false
        }
    }

    suspend fun syncPull(userId: String): Boolean {
        if (userId.isEmpty() || userId == "GUEST") return false
        return try {
            val document = firestore.collection("users").document(userId).get().await()
            if (document.exists()) {
                val data = document.data ?: return false

                val accountsList = (data["accounts"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { accountFromMap(it.castKeys(), userId) }
                } ?: emptyList()

                val groupsList = (data["envelope_groups"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { envelopeGroupFromMap(it.castKeys(), userId) }
                } ?: emptyList()

                val categoriesList = (data["categories"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { categoryFromMap(it.castKeys(), userId) }
                } ?: emptyList()

                val subcategoriesList = (data["subcategories"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { subcategoryFromMap(it.castKeys(), userId) }
                } ?: emptyList()

                // Overwrite local Room with pull data
                accountDao.clearAll(userId)
                envelopeGroupDao.clearAll(userId)
                categoryDao.clearAll(userId)
                subcategoryDao.clearAll(userId)

                accountDao.insertAll(accountsList)
                envelopeGroupDao.insertAll(groupsList)
                categoryDao.insertAll(categoriesList)
                subcategoryDao.insertAll(subcategoriesList)

                Log.d("FinanceRepository", "Pull sync succeeded for $userId")
                true
            } else {
                Log.d("FinanceRepository", "No remote data found to pull for $userId")
                // No document exists, so we might want to upload our current local data
                syncPush(userId)
                true
            }
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Pull sync failed", e)
            false
        }
    }

    // --- EXTENSION / CONVERSION HELPERS ---
    private fun Account.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "type" to type,
        "initial_balance" to initial_balance,
        "archived" to archived
    )

    private fun accountFromMap(map: Map<String, Any?>, userId: String): Account = Account(
        id = (map["id"] as? Long)?.toInt() ?: (map["id"] as? Int) ?: 0,
        name = map["name"] as? String ?: "",
        type = map["type"] as? String ?: "CONTA_CORRENTE",
        initial_balance = (map["initial_balance"] as? Double) ?: (map["initial_balance"] as? Long)?.toDouble() ?: 0.0,
        archived = map["archived"] as? Boolean ?: false,
        userId = userId
    )

    private fun EnvelopeGroup.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "sort_order" to sort_order,
        "budget_rule_type" to budget_rule_type,
        "archived" to archived
    )

    private fun envelopeGroupFromMap(map: Map<String, Any?>, userId: String): EnvelopeGroup = EnvelopeGroup(
        id = (map["id"] as? Long)?.toInt() ?: (map["id"] as? Int) ?: 0,
        name = map["name"] as? String ?: "",
        sort_order = (map["sort_order"] as? Long)?.toInt() ?: (map["sort_order"] as? Int) ?: 0,
        budget_rule_type = map["budget_rule_type"] as? String,
        archived = map["archived"] as? Boolean ?: false,
        userId = userId
    )

    private fun Category.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "envelope_group_id" to envelope_group_id,
        "name" to name,
        "archived" to archived
    )

    private fun categoryFromMap(map: Map<String, Any?>, userId: String): Category = Category(
        id = (map["id"] as? Long)?.toInt() ?: (map["id"] as? Int) ?: 0,
        envelope_group_id = (map["envelope_group_id"] as? Long)?.toInt() ?: (map["envelope_group_id"] as? Int),
        name = map["name"] as? String ?: "",
        archived = map["archived"] as? Boolean ?: false,
        userId = userId
    )

    private fun Subcategory.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "category_id" to category_id,
        "name" to name,
        "archived" to archived
    )

    private fun subcategoryFromMap(map: Map<String, Any?>, userId: String): Subcategory = Subcategory(
        id = (map["id"] as? Long)?.toInt() ?: (map["id"] as? Int) ?: 0,
        category_id = (map["category_id"] as? Long)?.toInt() ?: (map["category_id"] as? Int) ?: 0,
        name = map["name"] as? String ?: "",
        archived = map["archived"] as? Boolean ?: false,
        userId = userId
    )

    @Suppress("UNCHECKED_CAST")
    private fun Map<*, *>.castKeys(): Map<String, Any?> {
        return this.entries.associate { (key, value) ->
            key.toString() to value
        }
    }
}
