package com.example.data.db

import androidx.room.*
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.EnvelopeGroup
import com.example.data.model.Subcategory
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY name ASC")
    fun getAccountsFlow(userId: String): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY name ASC")
    suspend fun getAllAccounts(userId: String): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Int): Account?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<Account>)

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)

    @Query("DELETE FROM accounts WHERE userId = :userId")
    suspend fun clearAll(userId: String)
}

@Dao
interface EnvelopeGroupDao {
    @Query("SELECT * FROM envelope_groups WHERE userId = :userId ORDER BY sort_order ASC, name ASC")
    fun getEnvelopeGroupsFlow(userId: String): Flow<List<EnvelopeGroup>>

    @Query("SELECT * FROM envelope_groups WHERE userId = :userId")
    suspend fun getAllEnvelopeGroups(userId: String): List<EnvelopeGroup>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(envelopeGroup: EnvelopeGroup): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<EnvelopeGroup>)

    @Update
    suspend fun update(envelopeGroup: EnvelopeGroup)

    @Delete
    suspend fun delete(envelopeGroup: EnvelopeGroup)

    @Query("DELETE FROM envelope_groups WHERE userId = :userId")
    suspend fun clearAll(userId: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY name ASC")
    fun getCategoriesFlow(userId: String): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE userId = :userId")
    suspend fun getAllCategories(userId: String): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("DELETE FROM categories WHERE userId = :userId")
    suspend fun clearAll(userId: String)
}

@Dao
interface SubcategoryDao {
    @Query("SELECT * FROM subcategories WHERE userId = :userId ORDER BY name ASC")
    fun getSubcategoriesFlow(userId: String): Flow<List<Subcategory>>

    @Query("SELECT * FROM subcategories WHERE userId = :userId")
    suspend fun getAllSubcategories(userId: String): List<Subcategory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subcategory: Subcategory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subcategories: List<Subcategory>)

    @Update
    suspend fun update(subcategory: Subcategory)

    @Delete
    suspend fun delete(subcategory: Subcategory)

    @Query("DELETE FROM subcategories WHERE userId = :userId")
    suspend fun clearAll(userId: String)
}
