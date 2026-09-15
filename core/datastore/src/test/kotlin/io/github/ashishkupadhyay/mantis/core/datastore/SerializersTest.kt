package io.github.ashishkupadhyay.mantis.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.common.money.NumberStyle
import io.github.ashishkupadhyay.mantis.core.datastore.crypto.CipherException
import io.github.ashishkupadhyay.mantis.core.model.preferences.AppLockPreferences
import io.github.ashishkupadhyay.mantis.core.model.preferences.LockTimeout
import io.github.ashishkupadhyay.mantis.core.datastore.model.AuthTokens
import io.github.ashishkupadhyay.mantis.core.datastore.model.LlmVault
import io.github.ashishkupadhyay.mantis.core.datastore.model.SyncState
import io.github.ashishkupadhyay.mantis.core.model.preferences.ThemePreference
import io.github.ashishkupadhyay.mantis.core.model.preferences.UserPreferences
import io.github.ashishkupadhyay.mantis.core.datastore.serializer.EncryptedSerializer
import io.github.ashishkupadhyay.mantis.core.testing.datastore.InMemoryDataStore
import io.github.ashishkupadhyay.mantis.core.datastore.serializer.ProtoSerializer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Wire format, encryption at rest and corruption recovery for the typed stores (NFR-16, FR-LLM-2). */
@OptIn(ExperimentalSerializationApi::class)
class SerializersTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val prefsSerializer = ProtoSerializer(UserPreferences.serializer(), UserPreferences())

    @Test
    fun protoRoundTripsAndDecodesEmptyFileToDefaults() = runTest {
        val prefs = UserPreferences(
            currencyCode = "USD", numberStyle = NumberStyle.INTERNATIONAL, monthStartDay = 25, theme = ThemePreference.DARK,
            appLock = AppLockPreferences(enabled = true, timeout = LockTimeout.IMMEDIATELY, strictVault = true), onboardingCompleted = true,
        )
        val out = ByteArrayOutputStream()

        prefsSerializer.writeTo(prefs, out)

        prefsSerializer.readFrom(ByteArrayInputStream(out.toByteArray())) shouldBe prefs
        prefsSerializer.readFrom(ByteArrayInputStream(ByteArray(0))) shouldBe UserPreferences()
    }

    @Test
    fun unknownFieldsFromANewerVersionAreIgnored() = runTest {
        // A future build writes field 99; this build must still read everything it knows.
        val newer = ProtoBuf.encodeToByteArray(NewerPreferences.serializer(), NewerPreferences(currencyCode = "EUR", future = "x"))

        prefsSerializer.readFrom(ByteArrayInputStream(newer)).currencyCode shouldBe "EUR"
    }

    @Test
    fun garbageBytesRaiseCorruption() = runTest {
        shouldThrow<CorruptionException> { prefsSerializer.readFrom(ByteArrayInputStream(byteArrayOf(-1, -1, -1, 9))) }
    }

    @Test
    fun encryptedSerializerHidesPlaintextAndDetectsTampering() = runTest {
        val cipher = JvmAeadCipher()
        val serializer = EncryptedSerializer(ProtoSerializer(AuthTokens.serializer(), AuthTokens()), cipher, "tokens")
        val tokens = AuthTokens(accessToken = "access-SECRET-123", refreshToken = "refresh-SECRET-456", accessExpiresAtEpochMillis = 42)
        val out = ByteArrayOutputStream()

        serializer.writeTo(tokens, out)
        val onDisk = out.toByteArray()

        String(onDisk, Charsets.ISO_8859_1) shouldNotContain "SECRET"
        serializer.readFrom(ByteArrayInputStream(onDisk)) shouldBe tokens

        val tampered = onDisk.copyOf().also { it[it.lastIndex] = (it[it.lastIndex] + 1).toByte() }
        shouldThrow<CorruptionException> { serializer.readFrom(ByteArrayInputStream(tampered)) }

        cipher.deleteKey("tokens")
        shouldThrow<CorruptionException> { serializer.readFrom(ByteArrayInputStream(onDisk)) }
    }

    @Test
    fun blobFramingRejectsTruncatedInput() {
        val cipher = JvmAeadCipher()
        shouldThrow<CipherException> { cipher.decrypt("a", byteArrayOf(1)) }
        shouldThrow<CipherException> { cipher.decrypt("a", byteArrayOf(9, 12, 0)) }
    }

    @Test
    fun corruptStoreIsReplacedWithDefaultsInsteadOfCrashing() = runTest {
        // DataStore replaces files with File.renameTo, which cannot overwrite on Windows hosts; Android and CI (Linux) can.
        Assume.assumeFalse(System.getProperty("os.name").orEmpty().startsWith("Windows"))
        val file = folder.newFile("sync_state.pb").also { it.writeBytes(byteArrayOf(-1, -1, -1, 9)) }
        val store = DataStoreFactory.create(
            serializer = ProtoSerializer(SyncState.serializer(), SyncState()),
            corruptionHandler = ReplaceFileCorruptionHandler { SyncState() },
            scope = this,
            produceFile = { file },
        )
        val source = SyncStateDataSource(store, UuidV7())

        val deviceId = source.deviceId()

        deviceId shouldNotBe ""
        source.deviceId() shouldBe deviceId
        store.data.first().deviceId shouldBe deviceId
    }

    @Test
    fun vaultEntriesAreOpaqueAndRemovable() = runTest {
        val cipher = JvmAeadCipher()
        val source = LlmVaultDataSource(InMemoryDataStore(LlmVault()))

        source.put("provider-1", cipher.encrypt("provider-1", "sk-ant-secret".toByteArray()))
        source.put("provider-2", byteArrayOf(1, 2, 3))
        source.remove("provider-2")

        val vault = source.data.first()
        vault.entries.keys shouldBe setOf("provider-1")
        String(cipher.decrypt("provider-1", vault.entries.getValue("provider-1"))) shouldBe "sk-ant-secret"

        // What reaches disk: the vault serialised then encrypted under the file alias — no key text anywhere.
        val serializer = EncryptedSerializer(ProtoSerializer(LlmVault.serializer(), LlmVault()), cipher, "vault")
        val out = ByteArrayOutputStream()
        serializer.writeTo(vault, out)
        String(out.toByteArray(), Charsets.ISO_8859_1) shouldNotContain "sk-ant"
        serializer.readFrom(ByteArrayInputStream(out.toByteArray())).entries.keys shouldBe setOf("provider-1")
    }

    @Test
    fun deviceIdIsGeneratedOnceAndSyncStateUpdatesAtomically() = runTest {
        val source = SyncStateDataSource(InMemoryDataStore(SyncState()), UuidV7())

        val id = source.deviceId()
        source.update { it.copy(signedIn = true, email = "a@b.c") }

        id shouldNotBe ""
        source.deviceId() shouldBe id
        source.data.first() shouldBe SyncState(deviceId = id, signedIn = true, email = "a@b.c")
    }
}

/** Simulates a later schema: same field 1, plus a field this build does not know. */
@OptIn(ExperimentalSerializationApi::class)
@kotlinx.serialization.Serializable
private data class NewerPreferences(
    @kotlinx.serialization.protobuf.ProtoNumber(1) val currencyCode: String = "INR",
    @kotlinx.serialization.protobuf.ProtoNumber(99) val future: String = "",
)
