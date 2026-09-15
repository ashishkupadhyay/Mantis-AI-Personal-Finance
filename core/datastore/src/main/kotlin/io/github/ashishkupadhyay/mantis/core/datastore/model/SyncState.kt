package io.github.ashishkupadhyay.mantis.core.datastore.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/** Account and sync bookkeeping (doc 03 §6). `datastore/sync_state.pb`, unencrypted — contains no secrets. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SyncState(
    /** Stable per-install id stamped on every synced row (`deviceId` sync column); generated on first launch. */
    @ProtoNumber(1) val deviceId: String = "",
    @ProtoNumber(2) val signedIn: Boolean = false,
    @ProtoNumber(3) val userId: String? = null,
    @ProtoNumber(4) val email: String? = null,
    @ProtoNumber(5) val syncEnabled: Boolean = false,
    @ProtoNumber(6) val lastSyncAtEpochMillis: Long = 0,
    @ProtoNumber(7) val lastSyncError: String? = null,
)

/** Backend session tokens (NFR-16). `datastore/tokens.pb`, encrypted under the `tokens` Keystore alias. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AuthTokens(
    @ProtoNumber(1) val accessToken: String = "",
    @ProtoNumber(2) val refreshToken: String = "",
    @ProtoNumber(3) val accessExpiresAtEpochMillis: Long = 0,
) {
    val isPresent: Boolean get() = refreshToken.isNotEmpty()
}

/**
 * BYOK provider keys (FR-LLM-2). `datastore/llm_vault.pb`: the file is encrypted under the `llm_vault` alias, and
 * each entry's bytes are additionally the ciphertext of that provider's key under its own per-provider Keystore
 * alias — the plaintext key never exists at rest in either layer. Only `core:llm`'s `KeyVault` reads entries.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LlmVault(
    @ProtoNumber(1) val entries: Map<String, ByteArray> = emptyMap(),
)
