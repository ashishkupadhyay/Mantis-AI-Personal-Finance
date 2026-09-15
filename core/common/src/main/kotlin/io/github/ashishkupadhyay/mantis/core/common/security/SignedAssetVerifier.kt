package io.github.ashishkupadhyay.mantis.core.common.security

import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.security.MessageDigest
import java.util.Base64

/**
 * Verifies Ed25519 signatures over downloaded assets — classifier models, bank presets, merchant-dictionary
 * deltas and the on-device LLM manifest (NFR-20a, ADR-0001). The private key lives only in CI; the app
 * ships a list of public keys so a key can be rotated one release before the old one is retired (doc 03 §11).
 *
 * `net.i2p.crypto:eddsa` is a small pure-Java implementation; Android's JCA only gained Ed25519 on API 33.
 */
class SignedAssetVerifier(publicKeys: Map<String, ByteArray>) {

    private val keys: Map<String, EdDSAPublicKey> = publicKeys.mapValues { (_, raw) ->
        require(raw.size == PUBLIC_KEY_BYTES) { "Ed25519 public keys are $PUBLIC_KEY_BYTES bytes" }
        EdDSAPublicKey(EdDSAPublicKeySpec(raw, SPEC))
    }

    val keyIds: Set<String> get() = keys.keys

    /** True iff [signature] (64 raw bytes) is a valid signature of [payload] under the key named [keyId]. */
    fun verify(payload: ByteArray, signature: ByteArray, keyId: String): Boolean {
        val key = keys[keyId] ?: return false
        if (signature.size != SIGNATURE_BYTES) return false
        return runCatching {
            val engine = EdDSAEngine(MessageDigest.getInstance(SPEC.hashAlgorithm))
            engine.initVerify(key)
            engine.update(payload)
            engine.verify(signature)
        }.getOrDefault(false)
    }

    fun verifyBase64(payload: ByteArray, signatureBase64: String, keyId: String): Boolean =
        runCatching { Base64.getDecoder().decode(signatureBase64) }
            .map { verify(payload, it, keyId) }
            .getOrDefault(false)

    companion object {
        const val PUBLIC_KEY_BYTES = 32
        const val SIGNATURE_BYTES = 64
        private val SPEC = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)

        fun fromBase64(publicKeysBase64: Map<String, String>): SignedAssetVerifier =
            SignedAssetVerifier(publicKeysBase64.mapValues { Base64.getDecoder().decode(it.value) })

        /**
         * Canonical message for a versioned asset: `name ‖ version ‖ sha256(payload)` joined with newlines, so the
         * signature covers identity and content (doc 03 §5.5) without hashing large files twice on the server.
         */
        fun canonicalMessage(name: String, version: String, payloadSha256Hex: String): ByteArray =
            "$name\n$version\n${payloadSha256Hex.lowercase()}".toByteArray(Charsets.UTF_8)

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
