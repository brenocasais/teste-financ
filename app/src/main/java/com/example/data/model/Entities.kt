package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // DINHEIRO, CONTA_CORRENTE, CARTAO_CREDITO
    val initial_balance: Double,
    val archived: Boolean = false,
    val userId: String = ""
)

@Entity(tableName = "envelope_groups")
data class EnvelopeGroup(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val sort_order: Int,
    val budget_rule_type: String?, // NECESSIDADE, DESEJO, POUPANCA
    val archived: Boolean = false,
    val userId: String = ""
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val envelope_group_id: Int?, // nullable — categoria pode ficar sem envelope
    val name: String,
    val archived: Boolean = false,
    val userId: String = ""
)

@Entity(tableName = "subcategories")
data class Subcategory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category_id: Int,
    val name: String,
    val archived: Boolean = false,
    val userId: String = ""
)
