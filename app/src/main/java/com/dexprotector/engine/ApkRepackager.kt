package com.dexprotector.engine

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ApkRepackager {

    fun repackage(
        inputApk: File,
        outputApk: File,
        patchedSoFiles: Map<String, File>,
        newManifest: ByteArray,
        excludeDex: Boolean = true
    ) {
        outputApk.parentFile?.mkdirs()

        val existingEntries = mutableMapOf<String, ByteArray>()
        val soEntries = mutableSetOf<String>()

        patchedSoFiles.forEach { (abi, _) ->
            soEntries.add("lib/$abi/libdexprotector.so")
        }

        ZipInputStream(FileInputStream(inputApk)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory &&
                    !(excludeDex && name.endsWith(".dex")) &&
                    name !in soEntries &&
                    name != "AndroidManifest.xml" &&
                    !name.startsWith("META-INF/")
                ) {
                    val bos = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var len: Int
                    while (zis.read(buf).also { len = it } != -1) {
                        bos.write(buf, 0, len)
                    }
                    existingEntries[name] = bos.toByteArray()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        ZipOutputStream(FileOutputStream(outputApk)).use { zos ->
            zos.setLevel(9)

            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(newManifest)
            zos.closeEntry()

            existingEntries.forEach { (name, data) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }

            patchedSoFiles.forEach { (abi, soFile) ->
                val entryName = "lib/$abi/libdexprotector.so"
                zos.putNextEntry(ZipEntry(entryName))
                soFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }
}
