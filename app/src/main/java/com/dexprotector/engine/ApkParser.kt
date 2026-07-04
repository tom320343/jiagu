package com.dexprotector.engine

import com.dexprotector.model.ApkInfo
import java.io.File
import java.util.zip.ZipFile

object ApkParser {

    fun parse(apkFile: File): ApkInfo {
        ZipFile(apkFile).use { zip ->
            val entries = zip.entries()

            val dexFiles = mutableListOf<String>()
            val nativeLibs = mutableListOf<String>()
            val abiList = mutableSetOf<String>()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name

                when {
                    name.endsWith(".dex") -> dexFiles.add(name)
                    name.startsWith("lib/") && name.endsWith(".so") -> {
                        nativeLibs.add(name)
                        val parts = name.split("/")
                        if (parts.size >= 3) {
                            abiList.add(parts[1])
                        }
                    }
                }
            }

            val manifestBytes = zip.getInputStream(zip.getEntry("AndroidManifest.xml"))?.readBytes()
            val packageName = extractPackageName(manifestBytes)
            val appClass = extractApplicationClass(manifestBytes)

            return ApkInfo(
                packageName = packageName,
                applicationClass = appClass,
                dexFiles = dexFiles,
                nativeLibs = nativeLibs,
                abiList = abiList.toList()
            )
        }
    }

    fun extractDexFiles(apkFile: File, destDir: File): Map<String, File> {
        destDir.mkdirs()
        val result = mutableMapOf<String, File>()

        ZipFile(apkFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".dex")) {
                    val destFile = File(destDir, entry.name)
                    zip.getInputStream(entry).use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    result[entry.name] = destFile
                }
            }
        }

        return result
    }

    fun extractManifest(apkFile: File): ByteArray {
        ZipFile(apkFile).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml")
                ?: throw IllegalStateException("AndroidManifest.xml not found in APK")
            return zip.getInputStream(entry).readBytes()
        }
    }

    private fun extractPackageName(manifestBytes: ByteArray?): String {
        if (manifestBytes == null) return "unknown"
        try {
            val xml = String(manifestBytes)
            val regex = Regex("package=\"([^\"]+)\"")
            return regex.find(xml)?.groupValues?.get(1) ?: "unknown"
        } catch (_: Exception) {
            return "unknown"
        }
    }

    private fun extractApplicationClass(manifestBytes: ByteArray?): String? {
        if (manifestBytes == null) return null
        try {
            val xml = String(manifestBytes)
            val regex = Regex("<application[^>]*android:name=\"([^\"]+)\"")
            return regex.find(xml)?.groupValues?.get(1)
        } catch (_: Exception) {
            return null
        }
    }
}
