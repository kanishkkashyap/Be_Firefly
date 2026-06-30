package com.firefly.befirefly.data.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets

class CryptoManager(private val context: Context) {

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val sharedPreferences: SharedPreferences by lazy {
        try {
            createSharedPreferences()
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                // If encrypted prefs fail (common on some ROMs), clear and retry
                context.getSharedPreferences("secret_shared_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                createSharedPreferences()
            } catch (e2: Exception) {
                e2.printStackTrace()
                // FALLBACK: Use standard SharedPreferences (Insecure but prevents crash)
                // In a production app, we would show a fatal error dialog.
                // For this prototype, we want it to RUN.
                android.util.Log.e("CryptoManager", "FATAL: Could not create EncryptedSharedPreferences. Falling back to insecure mode.")
                context.getSharedPreferences("insecure_shared_prefs", Context.MODE_PRIVATE)
            }
        }
    }

    private fun createSharedPreferences(): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            "secret_shared_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val KEY_PRIVATE = "private_key"
        private const val KEY_PUBLIC = "public_key"
        private const val KEY_USERNAME = "username"
        private const val KEY_PROFILE_URI = "profile_uri"
        private const val ALGORITHM_EC = "EC"
        private const val CURVE_NAME = "secp256r1"
        private const val ALGORITHM_ECDH = "ECDH"
        private const val ALGORITHM_AES = "AES"
        private const val ALGORITHM_AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BIT = 128
        private const val IV_LENGTH_BYTE = 12
    }

    fun getKeyPair(): KeyPair? {
        val privateKeyStr = sharedPreferences.getString(KEY_PRIVATE, null)
        val publicKeyStr = sharedPreferences.getString(KEY_PUBLIC, null)

        if (privateKeyStr != null && publicKeyStr != null) {
            try {
                val privateKeyBytes = Base64.decode(privateKeyStr, Base64.NO_WRAP)
                val publicKeyBytes = Base64.decode(publicKeyStr, Base64.NO_WRAP)

                val kf = KeyFactory.getInstance(ALGORITHM_EC)
                val privateKeySpec = java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes)
                val publicKeySpec = X509EncodedKeySpec(publicKeyBytes)

                val privateKey = kf.generatePrivate(privateKeySpec)
                val publicKey = kf.generatePublic(publicKeySpec)

                return KeyPair(publicKey, privateKey)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
        return null
    }

    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance(ALGORITHM_EC)
        val ecSpec = ECGenParameterSpec(CURVE_NAME)
        kpg.initialize(ecSpec)
        val keyPair = kpg.generateKeyPair()

        saveKeyPair(keyPair)
        return keyPair
    }

    private fun saveKeyPair(keyPair: KeyPair) {
        val privateKeyStr = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
        val publicKeyStr = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)

        saveKeys(privateKeyStr, publicKeyStr)
    }

    fun saveKeys(privateKeyStr: String, publicKeyStr: String) {
        sharedPreferences.edit()
            .putString(KEY_PRIVATE, privateKeyStr)
            .putString(KEY_PUBLIC, publicKeyStr)
            .apply()
    }

    fun saveUsername(username: String) {
        sharedPreferences.edit()
            .putString(KEY_USERNAME, username)
            .apply()
    }

    fun getUsername(): String? {
        return sharedPreferences.getString(KEY_USERNAME, null)
    }

    fun getPublicKeyString(): String? {
        return sharedPreferences.getString(KEY_PUBLIC, null)
    }

    fun saveProfilePicture(uri: String) {
        sharedPreferences.edit()
            .putString(KEY_PROFILE_URI, uri)
            .apply()
    }

    fun getProfilePicture(): String? {
        return sharedPreferences.getString(KEY_PROFILE_URI, null)
    }

    fun clear() {
        // Clear the active preferences
        sharedPreferences.edit().clear().apply()
        
        // Also clear ALL possible fallback stores to ensure thorough wipe
        try {
            context.getSharedPreferences("secret_shared_prefs", Context.MODE_PRIVATE)
                .edit().clear().apply()
        } catch (_: Exception) {}
        try {
            context.getSharedPreferences("insecure_shared_prefs", Context.MODE_PRIVATE)
                .edit().clear().apply()
        } catch (_: Exception) {}
        
        // Delete the encrypted prefs file entirely
        try {
            val prefsDir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
            prefsDir.listFiles()?.forEach { file ->
                if (file.name.contains("secret_shared_prefs") || file.name.contains("insecure_shared_prefs")) {
                    file.delete()
                    android.util.Log.d("CryptoManager", "Deleted prefs file: ${file.name}")
                }
            }
        } catch (_: Exception) {}
        
        android.util.Log.d("CryptoManager", "All identity data cleared!")
    }

    // ECDH Key Exchange to derive a shared secret
    fun deriveSharedSecret(myPrivateKey: PrivateKey, otherPublicKeyStr: String): ByteArray {
        val otherPublicKeyBytes = Base64.decode(otherPublicKeyStr, Base64.NO_WRAP)
        val kf = KeyFactory.getInstance(ALGORITHM_EC)
        val otherPublicKeySpec = X509EncodedKeySpec(otherPublicKeyBytes)
        val otherPublicKey = kf.generatePublic(otherPublicKeySpec)

        val keyAgreement = KeyAgreement.getInstance(ALGORITHM_ECDH)
        keyAgreement.init(myPrivateKey)
        keyAgreement.doPhase(otherPublicKey, true)
        
        // Use the first 32 bytes of the shared secret for AES-256
        val secret = keyAgreement.generateSecret()
        val secretKeyBytes = ByteArray(32)
        System.arraycopy(secret, 0, secretKeyBytes, 0, Math.min(secret.size, 32))
        return secretKeyBytes
    }

    fun encrypt(message: String, sharedSecret: ByteArray): String {
        val cipher = Cipher.getInstance(ALGORITHM_AES_GCM_NO_PADDING)
        val secretKey = SecretKeySpec(sharedSecret, ALGORITHM_AES)
        
        // Generate a random IV
        val iv = ByteArray(IV_LENGTH_BYTE)
        java.security.SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val cipherText = cipher.doFinal(message.toByteArray(StandardCharsets.UTF_8))

        // Prepend IV to cipherText
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encryptedMessage: String, sharedSecret: ByteArray): String {
        val combined = Base64.decode(encryptedMessage, Base64.NO_WRAP)
        
        // Extract IV
        val iv = ByteArray(IV_LENGTH_BYTE)
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTE)
        
        // Extract CipherText
        val cipherTextSize = combined.size - IV_LENGTH_BYTE
        val cipherText = ByteArray(cipherTextSize)
        System.arraycopy(combined, IV_LENGTH_BYTE, cipherText, 0, cipherTextSize)

        val cipher = Cipher.getInstance(ALGORITHM_AES_GCM_NO_PADDING)
        val secretKey = SecretKeySpec(sharedSecret, ALGORITHM_AES)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val plainTextBytes = cipher.doFinal(cipherText)

        return String(plainTextBytes, StandardCharsets.UTF_8)
    }
}
