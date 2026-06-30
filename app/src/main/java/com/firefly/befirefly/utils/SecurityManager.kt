package com.firefly.befirefly.utils

import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SecurityManager {

    private const val ALGORITHM_AES = "AES"
    private const val ALGORITHM_AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    fun encrypt(data: String, secretKeyBytes: ByteArray): String? {
        try {
            val cipher = Cipher.getInstance(ALGORITHM_AES_GCM_NO_PADDING)
            val secretKey = SecretKeySpec(secretKeyBytes, ALGORITHM_AES)

            val iv = ByteArray(IV_LENGTH_BYTE)
            java.security.SecureRandom().nextBytes(iv)
            val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
            val cipherText = cipher.doFinal(data.toByteArray(StandardCharsets.UTF_8))

            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun decrypt(encryptedData: String, secretKeyBytes: ByteArray): String? {
        try {
            val combined = Base64.decode(encryptedData, Base64.NO_WRAP)

            val iv = ByteArray(IV_LENGTH_BYTE)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTE)

            val cipherTextSize = combined.size - IV_LENGTH_BYTE
            val cipherText = ByteArray(cipherTextSize)
            System.arraycopy(combined, IV_LENGTH_BYTE, cipherText, 0, cipherTextSize)

            val cipher = Cipher.getInstance(ALGORITHM_AES_GCM_NO_PADDING)
            val secretKey = SecretKeySpec(secretKeyBytes, ALGORITHM_AES)
            val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)

            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val plainTextBytes = cipher.doFinal(cipherText)

            return String(plainTextBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
