package com.firefly.befirefly

import com.firefly.befirefly.utils.SecurityManager
import org.junit.Test
import org.junit.Assert.*
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

class SecurityManagerTest {

    private val CURVE_NAME = "secp256r1"
    private val ALGORITHM_EC = "EC"
    private val ALGORITHM_ECDH = "ECDH"

    @Test
    fun ecdh_keyExchange_and_encryption_isCorrect() {
        // 1. Generate KeyPairs for User A and User B
        val kpg = KeyPairGenerator.getInstance(ALGORITHM_EC)
        val ecSpec = ECGenParameterSpec(CURVE_NAME)
        kpg.initialize(ecSpec)

        val keyPairA = kpg.generateKeyPair()
        val keyPairB = kpg.generateKeyPair()

        // 2. Derive Shared Secret (A sides logic: using A's Private + B's Public)
        val secretA = deriveSharedSecret(keyPairA.private, keyPairB.public)

        // 3. Derive Shared Secret (B sides logic: using B's Private + A's Public)
        val secretB = deriveSharedSecret(keyPairB.private, keyPairA.public)

        // 4. Assert Secrets match
        assertArrayEquals("Derived secrets must be identical", secretA, secretB)
        println("Shared Secret (Hex): " + bytesToHex(secretA))

        // 5. Test Encryption (A sends to B)
        val originalText = "Hello Secure Mesh World!"
        val encrypted = SecurityManager.encrypt(originalText, secretA)
        assertNotNull(encrypted)
        assertNotEquals(originalText, encrypted)
        println("Encrypted: $encrypted")

        // 6. Test Decryption (B receives)
        val decrypted = SecurityManager.decrypt(encrypted!!, secretB)
        assertEquals(originalText, decrypted)
        println("Decrypted: $decrypted")
    }

    private fun deriveSharedSecret(myPrivateKey: java.security.PrivateKey, otherPublicKey: java.security.PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance(ALGORITHM_ECDH)
        keyAgreement.init(myPrivateKey)
        keyAgreement.doPhase(otherPublicKey, true)
        
        val secret = keyAgreement.generateSecret()
        // Simulate pulling 32 bytes for AES-256
        val secretKeyBytes = ByteArray(32)
        System.arraycopy(secret, 0, secretKeyBytes, 0, Math.min(secret.size, 32))
        return secretKeyBytes
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02X", b))
        }
        return sb.toString()
    }
}
