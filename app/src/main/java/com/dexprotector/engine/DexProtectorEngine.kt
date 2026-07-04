package com.dexprotector.engine

import android.content.Context
import com.dexprotector.model.ProtectConfig
import com.dexprotector.model.ProtectProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DexProtectorEngine(private val context: Context) {

    private var signingKey: ApkSigner.SigningKey? = null

    suspend fun protect(
        inputApkPath: String,
        config: ProtectConfig,
        onProgress: (ProtectProgress) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val inputApk = File(inputApkPath)
        if (!inputApk.exists()) throw IllegalStateException("Input APK not found: $inputApkPath")

        if (signingKey == null) {
            signingKey = ApkSigner.generateDebugKey()
        }

        val workDir = File(context.cacheDir, "dp_${System.currentTimeMillis()}")
        workDir.mkdirs()

        try {
            onProgress(ProtectProgress("Parsing APK...", 0.05f))
            val apkInfo = ApkParser.parse(inputApk)
            val dexFiles = ApkParser.extractDexFiles(inputApk, File(workDir, "dex"))

            val targetAbis = if (apkInfo.abiList.isEmpty()) SoPatcher.getSupportedAbis()
            else apkInfo.abiList

            val outputDir = if (config.outputDir.isNotEmpty()) {
                File(config.outputDir)
            } else {
                File(context.getExternalFilesDir(null) ?: context.filesDir, "protected")
            }
            outputDir.mkdirs()

            val baseName = inputApk.nameWithoutExtension
            val outputApk = File(outputDir, "${baseName}_protected.apk")

            val patchedSoFiles = mutableMapOf<String, File>()
            var encryptionKey: ByteArray? = null
            var totalDexSize = 0L

            dexFiles.values.forEach { totalDexSize += it.length() }

            var processedSize = 0L
            val allEncryptedDex = mutableListOf<ByteArray>()

            onProgress(ProtectProgress("Encrypting DEX files...", 0.15f))
            dexFiles.forEach { (name, dexFile) ->
                val ratio = if (totalDexSize > 0) (processedSize.toFloat() / totalDexSize) * 0.35f else 0f
                onProgress(ProtectProgress("Processing $name...", 0.15f + ratio))

                val dexData = dexFile.readBytes()
                val encrypted = DexEncryptor.encrypt(dexData, encryptionKey)
                if (encryptionKey == null) encryptionKey = encrypted.key
                allEncryptedDex.add(encrypted.data)

                targetAbis.forEach { abi ->
                    if (!patchedSoFiles.containsKey(abi)) {
                        val stubSoAssetPath = "stub_libs/$abi/libdexprotector.so"
                        val stubSoFile = File(context.filesDir, "stub_$abi.so")

                        if (!stubSoFile.exists()) {
                            copyAsset(stubSoAssetPath, stubSoFile)
                        }

                        val patchedSo = File(workDir, "patched_${abi}.so")
                        SoPatcher.patchStubSo(stubSoFile, encrypted, abi, patchedSo)
                        patchedSoFiles[abi] = patchedSo
                    }
                }

                processedSize += dexFile.length()
            }

            onProgress(ProtectProgress("Patching manifest...", 0.55f))
            val manifestBytes = ApkParser.extractManifest(inputApk)
            var patchedManifest = ManifestPatcher.patchApplicationClass(
                manifestBytes,
                apkInfo.applicationClass,
                "com.dexprotector.stub.StubApplication"
            )

            if (config.antiDebug) {
                patchedManifest = ManifestPatcher.addMetaData(patchedManifest, "DEXPROT_ANTI_DEBUG", "true")
            }
            if (config.integrityCheck) {
                patchedManifest = ManifestPatcher.addMetaData(patchedManifest, "DEXPROT_INTEGRITY", "true")
            }

            onProgress(ProtectProgress("Repackaging APK...", 0.65f))
            val tempUnsigned = File(workDir, "unsigned.apk")
            ApkRepackager.repackage(inputApk, tempUnsigned, patchedSoFiles, patchedManifest, excludeDex = true)

            onProgress(ProtectProgress("Signing APK...", 0.85f))
            val key = signingKey!!
            ApkSigner.signApk(tempUnsigned, outputApk, key.privateKey, key.certificate)

            onProgress(ProtectProgress("Completed", 1.0f, isCompleted = true))

            outputApk.absolutePath
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun copyAsset(assetPath: String, destFile: File) {
        destFile.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
