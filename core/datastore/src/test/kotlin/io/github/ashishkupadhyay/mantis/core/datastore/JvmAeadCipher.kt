package io.github.ashishkupadhyay.mantis.core.datastore

import io.github.ashishkupadhyay.mantis.core.datastore.crypto.AeadBlob
import io.github.ashishkupadhyay.mantis.core.datastore.crypto.AeadCipher
import io.github.ashishkupadhyay.mantis.core.datastore.crypto.CipherException
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with in-memory keys: the same blob framing and failure modes as the Keystore implementation, which
 * Robolectric cannot host (no AndroidKeyStore provider).
 */
class JvmAeadCipher : AeadCipher {

    private val keys = mutableMapOf<String, SecretKey>()
    private val random = SecureRandom()

    override fun encrypt(alias: String, plaintext: ByteArray): ByteArray {
        val key = keys.getOrPut(alias) { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() }
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(AeadBlob.GCM_TAG_BITS, iv))
        return AeadBlob.pack(iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(alias: String, blob: ByteArray): ByteArray {
        val key = keys[alias] ?: throw CipherException("No key for alias")
        val (iv, ciphertext) = AeadBlob.unpack(blob)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(AeadBlob.GCM_TAG_BITS, iv))
                doFinal(ciphertext)
            }
        } catch (e: GeneralSecurityException) {
            throw CipherException("Decrypt failed", e)
        }
    }

    override fun hasKey(alias: String): Boolean = alias in keys

    override fun deleteKey(alias: String) {
        keys.remove(alias)
    }
}
