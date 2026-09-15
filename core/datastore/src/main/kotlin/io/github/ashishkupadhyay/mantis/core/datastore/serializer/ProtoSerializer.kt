package io.github.ashishkupadhyay.mantis.core.datastore.serializer

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import io.github.ashishkupadhyay.mantis.core.datastore.crypto.AeadCipher
import io.github.ashishkupadhyay.mantis.core.datastore.crypto.CipherException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** DataStore serializer for a `@Serializable` type in proto wire format. An empty file decodes to [defaultValue]. */
@OptIn(ExperimentalSerializationApi::class)
class ProtoSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : Serializer<T> {

    override suspend fun readFrom(input: InputStream): T {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            ProtoBuf.decodeFromByteArray(serializer, bytes)
        } catch (e: SerializationException) {
            throw CorruptionException("Cannot decode proto", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Cannot decode proto", e)
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(ProtoBuf.encodeToByteArray(serializer, t))
    }
}

/**
 * Wraps another serializer so the file on disk is an [AeadCipher] blob under [alias] (NFR-16, FR-LLM-2).
 * Decryption failures surface as [CorruptionException], which the store's corruption handler turns into a reset
 * to the default value — after a Keystore loss (factory reset restore) the user signs in / re-enters keys again
 * rather than the app crashing.
 */
class EncryptedSerializer<T>(
    private val inner: Serializer<T>,
    private val cipher: AeadCipher,
    private val alias: String,
) : Serializer<T> {

    override val defaultValue: T get() = inner.defaultValue

    override suspend fun readFrom(input: InputStream): T {
        val blob = input.readBytes()
        if (blob.isEmpty()) return defaultValue
        val plaintext = try {
            cipher.decrypt(alias, blob)
        } catch (e: CipherException) {
            throw CorruptionException("Cannot decrypt $alias store", e)
        }
        return inner.readFrom(ByteArrayInputStream(plaintext))
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        val buffer = ByteArrayOutputStream()
        inner.writeTo(t, buffer)
        val plaintext = buffer.toByteArray()
        try {
            output.write(cipher.encrypt(alias, plaintext))
        } finally {
            plaintext.fill(0)
        }
    }
}
