package io.github.ashishkupadhyay.mantis.core.datastore.crypto

/**
 * Authenticated encryption under a named key. The production implementation keeps keys in the Android Keystore
 * ([AndroidKeystoreCipher]); tests use an in-memory JVM implementation with the same blob format.
 *
 * Blob layout (version 1): `0x01 | ivLength (1 byte) | iv | ciphertext‖tag`.
 */
interface AeadCipher {

    /** Encrypts [plaintext] under the key named [alias], creating the key on first use. */
    fun encrypt(alias: String, plaintext: ByteArray): ByteArray

    /** @throws CipherException when the blob is malformed, tampered with, or the key is gone. */
    fun decrypt(alias: String, blob: ByteArray): ByteArray

    fun hasKey(alias: String): Boolean

    /** Destroys the key; any blob encrypted under it becomes unrecoverable (FR-LLM-2: wipe on provider removal). */
    fun deleteKey(alias: String)
}

class CipherException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Shared blob framing so every implementation is wire-compatible. */
internal object AeadBlob {
    const val VERSION: Byte = 1
    const val GCM_TAG_BITS = 128
    private const val HEADER = 2

    fun pack(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        require(iv.size in 1..Byte.MAX_VALUE) { "iv length out of range" }
        return byteArrayOf(VERSION, iv.size.toByte()) + iv + ciphertext
    }

    fun unpack(blob: ByteArray): Pair<ByteArray, ByteArray> {
        if (blob.size < HEADER || blob[0] != VERSION) throw CipherException("Unsupported or truncated blob")
        val ivLength = blob[1].toInt()
        if (ivLength <= 0 || blob.size < HEADER + ivLength) throw CipherException("Truncated blob")
        val iv = blob.copyOfRange(HEADER, HEADER + ivLength)
        val ciphertext = blob.copyOfRange(HEADER + ivLength, blob.size)
        return iv to ciphertext
    }
}
