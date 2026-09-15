package io.github.ashishkupadhyay.mantis.core.datastore.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM with keys that live in the Android Keystore (StrongBox when the device has it) — the key material
 * is never extractable, which is the guarantee behind NFR-16 and FR-LLM-2. Keys are created lazily per alias.
 *
 * Two flavours of key can be requested at creation time:
 * - default: no user-authentication binding, so background sync and the categorisation fallback can run;
 * - [strict]: bound to biometric/device-credential authentication for 5 minutes (FR-LLM-2 g, "Strict vault mode").
 *
 * Keystore chooses the IV; the blob framing is shared with the JVM test implementation ([AeadBlob]).
 */
@Singleton
class AndroidKeystoreCipher @Inject constructor() : AeadCipher {

    private val keyStore: KeyStore by lazy { KeyStore.getInstance(PROVIDER).apply { load(null) } }

    override fun encrypt(alias: String, plaintext: ByteArray): ByteArray = encrypt(alias, plaintext, strict = false)

    /** [strict] only matters when the key does not exist yet; existing keys keep the mode they were created with. */
    fun encrypt(alias: String, plaintext: ByteArray, strict: Boolean): ByteArray = guard {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyFor(alias, strict))
        AeadBlob.pack(cipher.iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(alias: String, blob: ByteArray): ByteArray = guard {
        val (iv, ciphertext) = AeadBlob.unpack(blob)
        val key = existingKey(alias) ?: throw CipherException("No key for alias")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(AeadBlob.GCM_TAG_BITS, iv))
        cipher.doFinal(ciphertext)
    }

    override fun hasKey(alias: String): Boolean = keyStore.containsAlias(alias)

    override fun deleteKey(alias: String) {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun keyFor(alias: String, strict: Boolean): SecretKey = existingKey(alias) ?: generate(alias, strict)

    private fun existingKey(alias: String): SecretKey? = (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun generate(alias: String, strict: Boolean): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        return try {
            generator.init(spec(alias, strict, strongBox = true))
            generator.generateKey()
        } catch (_: StrongBoxUnavailableException) {
            generator.init(spec(alias, strict, strongBox = false))
            generator.generateKey()
        }
    }

    private fun spec(alias: String, strict: Boolean, strongBox: Boolean): KeyGenParameterSpec =
        KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setRandomizedEncryptionRequired(true)
            .apply {
                if (strongBox) setIsStrongBoxBacked(true)
                if (strict) {
                    setUserAuthenticationRequired(true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(
                            STRICT_VALIDITY_SECONDS,
                            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setUserAuthenticationValidityDurationSeconds(STRICT_VALIDITY_SECONDS)
                    }
                }
            }
            .build()

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: CipherException) {
        throw e
    } catch (e: GeneralSecurityException) {
        throw CipherException("Keystore operation failed", e)
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val STRICT_VALIDITY_SECONDS = 300
    }
}
