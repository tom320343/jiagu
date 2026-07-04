package com.dexprotector.engine

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object DexEncryptor {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val IV_LENGTH = 16
    private const val KEY_LENGTH = 32

    data class EncryptedData(
        val data: ByteArray,
        val iv: ByteArray,
        val key: ByteArray
    )

    fun generateKey(): ByteArray {
        val key = ByteArray(KEY_LENGTH)
        SecureRandom().nextBytes(key)
        return key
    }

    fun encrypt(dexData: ByteArray, key: ByteArray? = null): EncryptedData {
        val secretKey = if (key != null) SecretKeySpec(key, ALGORITHM) else {
            val generatedKey = generateKey()
            SecretKeySpec(generatedKey, ALGORITHM)
        }

        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = javax.crypto.spec.IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val encrypted = cipher.doFinal(dexData)

        return EncryptedData(
            data = encrypted,
            iv = iv,
            key = secretKey.encoded
        )
    }

    fun decrypt(encryptedData: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key, ALGORITHM)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = javax.crypto.spec.IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(encryptedData)
    }
}
