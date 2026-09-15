package io.github.ashishkupadhyay.mantis.core.database.entity

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

enum class MerchantType {
    GROCERY, QUICK_COMMERCE, FOOD_DELIVERY, RESTAURANT, PHARMACY, FUEL, UTILITY, ECOMMERCE, TRANSPORT, ENTERTAINMENT, PERSON, OTHER
}

enum class MerchantSource { BUNDLED, USER, SERVER }

/** `merchants`: user rows shadow bundled ones; `merchantType` is a report dimension (FR-VIEW-1). */
@Entity(
    tableName = "merchants",
    foreignKeys = [ForeignKey(CategoryEntity::class, ["id"], ["defaultCategoryId"], onDelete = ForeignKey.SET_NULL, deferred = true)],
    indices = [Index("canonicalName", unique = true), Index("defaultCategoryId"), Index("merchantType")],
)
data class MerchantEntity(
    @PrimaryKey val id: String,
    val canonicalName: String,
    val displayName: String,
    val defaultCategoryId: String?,
    val merchantType: MerchantType,
    val logoKey: String?,
    val source: MerchantSource,
    @Embedded val sync: SyncColumns,
)

/** `merchant_aliases`: exact lookup after normalisation (doc 02 §6.1). */
@Entity(
    tableName = "merchant_aliases",
    foreignKeys = [ForeignKey(MerchantEntity::class, ["id"], ["merchantId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("merchantId")],
)
data class MerchantAliasEntity(
    @PrimaryKey val aliasNormalized: String,
    val merchantId: String,
)
