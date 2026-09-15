package io.github.ashishkupadhyay.mantis.core.common.security

import io.kotest.matchers.shouldBe
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.KeyPairGenerator
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.Base64

class SignedAssetVerifierTest {

    private val spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)
    private val keyPair = KeyPairGenerator().generateKeyPair()
    private val publicRaw = (keyPair.public as EdDSAPublicKey).abyte
    private val verifier = SignedAssetVerifier(mapOf("k1" to publicRaw))

    private fun sign(payload: ByteArray): ByteArray {
        val engine = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
        engine.initSign(keyPair.private as EdDSAPrivateKey)
        engine.update(payload)
        return engine.sign()
    }

    @Test
    fun `a valid signature verifies`() {
        val payload = "categorizer\n7\nabc".toByteArray()
        verifier.verify(payload, sign(payload), "k1") shouldBe true
        verifier.verifyBase64(payload, Base64.getEncoder().encodeToString(sign(payload)), "k1") shouldBe true
    }

    @Test
    fun `tampered payload, wrong key id, or malformed signature fails closed`() {
        val payload = "categorizer\n7\nabc".toByteArray()
        val signature = sign(payload)
        verifier.verify("categorizer\n8\nabc".toByteArray(), signature, "k1") shouldBe false
        verifier.verify(payload, signature, "unknown") shouldBe false
        verifier.verify(payload, signature.copyOf(63), "k1") shouldBe false
        verifier.verify(payload, signature.also { it[0] = (it[0].toInt() xor 1).toByte() }, "k1") shouldBe false
        verifier.verifyBase64(payload, "not base64!", "k1") shouldBe false
    }

    @Test
    fun `canonical message binds name, version and content hash`() {
        val a = SignedAssetVerifier.canonicalMessage("categorizer", "7", "ABCDEF")
        val b = SignedAssetVerifier.canonicalMessage("categorizer", "7", "abcdef")
        a.contentEquals(b) shouldBe true
        SignedAssetVerifier.canonicalMessage("categorizer", "8", "abcdef").contentEquals(a) shouldBe false
        SignedAssetVerifier.sha256Hex("mantis".toByteArray()).length shouldBe 64
    }

    @Test
    fun `key rotation - a second key id verifies its own signatures only`() {
        val other = KeyPairGenerator().generateKeyPair()
        val multi = SignedAssetVerifier(mapOf("k1" to publicRaw, "k2" to (other.public as EdDSAPublicKey).abyte))
        val payload = "presets\n3\n00".toByteArray()
        multi.verify(payload, sign(payload), "k1") shouldBe true
        multi.verify(payload, sign(payload), "k2") shouldBe false
        multi.keyIds shouldBe setOf("k1", "k2")
    }
}
