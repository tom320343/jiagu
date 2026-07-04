package com.dexprotector.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ManifestPatcher {

    fun patchApplicationClass(
        manifestBytes: ByteArray,
        originalAppClass: String?,
        newAppClass: String
    ): ByteArray {
        val manifestText = String(manifestBytes, Charsets.ISO_8859_1)

        val patched = if (originalAppClass != null) {
            manifestText.replace(
                "android:name=\"$originalAppClass\"",
                "android:name=\"$newAppClass\""
            )
        } else {
            val appTagEnd = "<application"
            val replacement = "<application android:name=\"$newAppClass\""
            if (manifestText.contains(appTagEnd) && !manifestText.contains(replacement)) {
                manifestText.replaceFirst(appTagEnd, replacement)
            } else {
                manifestText
            }
        }

        return patched.toByteArray(Charsets.ISO_8859_1)
    }

    fun addMetaData(manifestBytes: ByteArray, name: String, value: String): ByteArray {
        val manifestText = String(manifestBytes, Charsets.ISO_8859_1)
        val metaTag = "<meta-data android:name=\"$name\" android:value=\"$value\" />"
        val insertionPoint = manifestText.indexOf("</application>")
        if (insertionPoint == -1) return manifestBytes

        val before = manifestText.substring(0, insertionPoint)
        val after = manifestText.substring(insertionPoint)
        return (before + "    " + metaTag + "\n    " + after).toByteArray(Charsets.ISO_8859_1)
    }
}
