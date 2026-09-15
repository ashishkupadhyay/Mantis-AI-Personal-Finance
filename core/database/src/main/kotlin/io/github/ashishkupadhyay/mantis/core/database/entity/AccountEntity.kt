package io.github.ashishkupadhyay.mantis.core.database.entity

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.ashishkupadhyay.mantis.core.model.AccountType

/** `accounts` (doc 02 §5.2). Money columns are minor units; dates are ISO `yyyy-MM-dd`. */
@Entity(tableName = "accounts", indices = [Index("isArchived", "sortOrder")])
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: AccountType,
    val institutionId: String?,
    val last4: String?,
    val currency: String,
    val openingBalanceMinor: Long,
    val openingDate: String,
    val colorSeed: Int,
    val icon: String,
    val statementDay: Int?,
    val dueDay: Int?,
    val isArchived: Boolean,
    val sortOrder: Int,
    @Embedded val sync: SyncColumns,
)
