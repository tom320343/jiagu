package com.dexprotector.engine

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SoPatcher {

    private const val MAGIC = 0x58455044L  // "DEXP" in little-endian
    private const val HEADER_SIZE = 36      // magic(4) + dexSize(4) + keySize(4) + ivSize(4) + abiLen(4) + reserved(16)

    data class PatchedSoInfo(
        val soFile: File,
        val abi: String,
        val encryptedDexSize: Long,
        val key: ByteArray
    )

    fun patchStubSo(
        stubSoFile: File,
        encryptedDex: DexEncryptor.EncryptedData,
        targetAbi: String,
        outputFile: File
    ): PatchedSoInfo {
        outputFile.parentFile?.mkdirs()
        stubSoFile.copyTo(outputFile, overwrite = true)

        val abiBytes = targetAbi.toByteArray(Charsets.UTF_8)
        val keyBytes = encryptedDex.key
        val ivBytes = encryptedDex.iv
        val dexBytes = encryptedDex.data

        val trailer = buildTrailer(dexBytes, keyBytes, ivBytes, abiBytes)

        RandomAccessFile(outputFile, "rw").use { raf ->
            raf.seek(raf.length())
            raf.write(trailer)
        }

        return PatchedSoInfo(
            soFile = outputFile,
            abi = targetAbi,
            encryptedDexSize = dexBytes.size.toLong(),
            key = keyBytes
        )
    }

    private fun buildTrailer(
        dexData: ByteArray,
        key: ByteArray,
        iv: ByteArray,
        abi: ByteArray
    ): ByteArray {
        val bos = ByteArrayOutputStream()

        bos.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dexData.size).array())
        bos.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(key.size).array())
        bos.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(iv.size).array())
        bos.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(abi.size).array())
        bos.write(dexData)
        bos.write(key)
        bos.write(iv)
        bos.write(abi)

        bos.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(MAGIC).array())
        bos.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bos.size() + 12).array())

        return bos.toByteArray()
    }

    fun getSupportedAbis(): List<String> {
        return listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    }
}
