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

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val account_id: Int,                 // Conta de origem
    val to_account_id: Int?,             // Nullable, só para TRANSFERENCIA
    val category_id: Int?,               // Nullable para transferência
    val subcategory_id: Int?,            // Nullable
    val type: String,                    // RECEITA, DESPESA, TRANSFERENCIA
    val value: Double,                   // Valor
    val description: String,
    val date: String,                    // Formato YYYY-MM-DD
    val installment_plan_id: Int?,       // Nullable — se veio de compra parcelada (Fase 4)
    val installment_number: Int?,        // Nullable — ex: 2 (de "2 de 3")
    val recurrence_rule_id: Int?,        // Nullable — se veio de regra recorrente (Fase 5)
    val is_recurrence_override: Boolean = false, // true se editada isoladamente
    val attachment_uri: String? = null,  // URI opcional do comprovante
    val attachment_name: String? = null, // Nome opcional do comprovante
    val attachment_type: String? = null, // Tipo opcional do comprovante
    val synced: Boolean = false,         // Controle local↔Firestore
    val userId: String = ""
)

@Entity(tableName = "budget_allocations")
data class BudgetAllocation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category_id: Int,
    val subcategory_id: Int?,            // Nullable
    val month: String,                   // Formato YYYY-MM
    val planned_value: Double,
    val userId: String = ""
)

@Entity(tableName = "allocation_movements")
data class AllocationMovement(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val source_budget_allocation_id: Int?, // Nullable
    val source_goal_id: Int?,              // Nullable
    val dest_budget_allocation_id: Int?,   // Nullable
    val dest_goal_id: Int?,                // Nullable
    val amount: Double,
    val note: String? = null,
    val moved_at: Long = System.currentTimeMillis(),
    val userId: String = ""
)


