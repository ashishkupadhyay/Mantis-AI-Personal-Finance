package io.github.ashishkupadhyay.mantis.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.ashishkupadhyay.mantis.core.datastore.crypto.AeadCipher
import io.github.ashishkupadhyay.mantis.core.datastore.crypto.AndroidKeystoreCipher
import io.github.ashishkupadhyay.mantis.core.datastore.model.AuthTokens
import io.github.ashishkupadhyay.mantis.core.datastore.model.LlmVault
import io.github.ashishkupadhyay.mantis.core.datastore.model.SyncState
import io.github.ashishkupadhyay.mantis.core.model.preferences.UserPreferences
import io.github.ashishkupadhyay.mantis.core.datastore.serializer.EncryptedSerializer
import io.github.ashishkupadhyay.mantis.core.datastore.serializer.ProtoSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreBindings {
    @Binds
    abstract fun cipher(impl: AndroidKeystoreCipher): AeadCipher
}

/**
 * The four store files under `files/datastore/` (doc 02 §9 backup exclusions name them). A corrupt or
 * undecryptable file is replaced with defaults rather than crashing the app.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    const val USER_PREFERENCES_FILE = "user_prefs.pb"
    const val SYNC_STATE_FILE = "sync_state.pb"
    const val TOKENS_FILE = "tokens.pb"
    const val LLM_VAULT_FILE = "llm_vault.pb"
    const val TOKENS_ALIAS = "mantis.tokens"
    const val LLM_VAULT_ALIAS = "mantis.llm_vault"

    @Provides
    @Singleton
    fun userPreferences(@ApplicationContext context: Context): DataStore<UserPreferences> =
        store(context, USER_PREFERENCES_FILE, ProtoSerializer(UserPreferences.serializer(), UserPreferences()))

    @Provides
    @Singleton
    fun syncState(@ApplicationContext context: Context): DataStore<SyncState> =
        store(context, SYNC_STATE_FILE, ProtoSerializer(SyncState.serializer(), SyncState()))

    @Provides
    @Singleton
    fun authTokens(@ApplicationContext context: Context, cipher: AeadCipher): DataStore<AuthTokens> =
        store(context, TOKENS_FILE, EncryptedSerializer(ProtoSerializer(AuthTokens.serializer(), AuthTokens()), cipher, TOKENS_ALIAS))

    @Provides
    @Singleton
    fun llmVault(@ApplicationContext context: Context, cipher: AeadCipher): DataStore<LlmVault> =
        store(context, LLM_VAULT_FILE, EncryptedSerializer(ProtoSerializer(LlmVault.serializer(), LlmVault()), cipher, LLM_VAULT_ALIAS))

    private fun <T> store(context: Context, fileName: String, serializer: Serializer<T>): DataStore<T> =
        DataStoreFactory.create(
            serializer = serializer,
            corruptionHandler = ReplaceFileCorruptionHandler { serializer.defaultValue },
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { context.dataStoreFile(fileName) },
        )
}
