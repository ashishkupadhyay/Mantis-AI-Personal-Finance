package io.github.ashishkupadhyay.mantis.core.datastore

import androidx.datastore.core.DataStore
import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.datastore.model.AuthTokens
import io.github.ashishkupadhyay.mantis.core.datastore.model.LlmVault
import io.github.ashishkupadhyay.mantis.core.datastore.model.SyncState
import io.github.ashishkupadhyay.mantis.core.model.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Preferences as a flow plus atomic updates; repositories in `core:data` expose these to features. */
@Singleton
class UserPreferencesDataSource @Inject constructor(private val store: DataStore<UserPreferences>) {
    val data: Flow<UserPreferences> = store.data

    suspend fun update(transform: (UserPreferences) -> UserPreferences): UserPreferences = store.updateData(transform)
}

@Singleton
class SyncStateDataSource @Inject constructor(private val store: DataStore<SyncState>, private val ids: UuidV7) {
    val data: Flow<SyncState> = store.data

    suspend fun update(transform: (SyncState) -> SyncState): SyncState = store.updateData(transform)

    /** The install's device id, generated on first call and stable afterwards (doc 02 §5.1 sync columns). */
    suspend fun deviceId(): String {
        val current = store.data.first().deviceId
        if (current.isNotEmpty()) return current
        return store.updateData { state -> if (state.deviceId.isEmpty()) state.copy(deviceId = ids.nextString()) else state }.deviceId
    }
}

@Singleton
class AuthTokensDataSource @Inject constructor(private val store: DataStore<AuthTokens>) {
    val data: Flow<AuthTokens> = store.data

    suspend fun set(tokens: AuthTokens) {
        store.updateData { tokens }
    }

    suspend fun clear() {
        store.updateData { AuthTokens() }
    }
}

/**
 * Raw vault storage. Entries are opaque ciphertext blobs owned by `core:llm`'s `KeyVault`, which is the only
 * caller allowed (Konsist rule). This class never sees a plaintext key.
 */
@Singleton
class LlmVaultDataSource @Inject constructor(private val store: DataStore<LlmVault>) {
    val data: Flow<LlmVault> = store.data

    suspend fun put(alias: String, blob: ByteArray) {
        store.updateData { vault -> vault.copy(entries = vault.entries + (alias to blob)) }
    }

    suspend fun remove(alias: String) {
        store.updateData { vault -> vault.copy(entries = vault.entries - alias) }
    }

    suspend fun clear() {
        store.updateData { LlmVault() }
    }
}
