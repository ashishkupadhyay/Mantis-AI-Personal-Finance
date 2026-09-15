package io.github.ashishkupadhyay.mantis.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import io.github.ashishkupadhyay.mantis.core.database.entity.MerchantAliasEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.MerchantEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.MerchantSource
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantDao {

    @Upsert
    suspend fun upsert(merchant: MerchantEntity)

    @Upsert
    suspend fun upsertAll(merchants: List<MerchantEntity>)

    @Upsert
    suspend fun upsertAliases(aliases: List<MerchantAliasEntity>)

    @Query("SELECT * FROM merchants WHERE id = :id")
    suspend fun getById(id: String): MerchantEntity?

    @Query("SELECT * FROM merchants WHERE canonicalName = :canonicalName")
    suspend fun byCanonicalName(canonicalName: String): MerchantEntity?

    /** Exact alias hit after normalisation — the fast path of the categorisation pipeline (doc 02 §6.1). */
    @Query(
        """
        SELECT merchants.* FROM merchants
        JOIN merchant_aliases ON merchant_aliases.merchantId = merchants.id
        WHERE merchant_aliases.aliasNormalized = :aliasNormalized AND merchants.deletedAt IS NULL
        LIMIT 1
        """,
    )
    suspend fun byAlias(aliasNormalized: String): MerchantEntity?

    @Query("SELECT * FROM merchants WHERE deletedAt IS NULL ORDER BY displayName")
    fun observeAll(): Flow<List<MerchantEntity>>

    @Query("SELECT COUNT(*) FROM merchants WHERE source = :source")
    suspend fun countBySource(source: MerchantSource): Int

    @Query("DELETE FROM merchant_aliases WHERE merchantId = :merchantId")
    suspend fun deleteAliases(merchantId: String)

    @Transaction
    suspend fun replace(merchant: MerchantEntity, aliases: List<String>) {
        upsert(merchant)
        deleteAliases(merchant.id)
        if (aliases.isNotEmpty()) upsertAliases(aliases.map { MerchantAliasEntity(it, merchant.id) })
    }
}
