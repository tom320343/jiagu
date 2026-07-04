package com.dexprotector.engine

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.Deflater

object ApkSigner {

    private const val MANIFEST_NAME = "META-INF/MANIFEST.MF"
    private const val SF_NAME = "META-INF/DEXPROT.SF"
    private const val RSA_NAME = "META-INF/DEXPROT.RSA"

    init {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
    }

    data class SigningKey(
        val privateKey: PrivateKey,
        val certificate: X509Certificate
    )

    fun generateDebugKey(): SigningKey {
        val keyPairGen = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME)
        keyPairGen.initialize(2048, SecureRandom())
        val keyPair = keyPairGen.generateKeyPair()

        val issuer = X500Name("CN=DexProtector Debug, OU=Development, O=DexProtector, L=Unknown, ST=Unknown, C=CN")
        val subject = issuer
        val serial = BigInteger.valueOf(System.currentTimeMillis())

        val notBefore = Date(System.currentTimeMillis() - 86400000L)
        val notAfter = Date(System.currentTimeMillis() + 365L * 30 * 86400000L)

        val certBuilder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            issuer, serial, notBefore, notAfter, subject, keyPair.public
        )

        certBuilder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        certBuilder.addExtension(
            Extension.keyUsage, true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
        )

        val signer = JcaContentSignerBuilder("SHA256WithRSA")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(keyPair.private)

        val certificate = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(certBuilder.build(signer))

        return SigningKey(keyPair.private, certificate)
    }

    fun signApk(
        unsignedApk: File,
        outputApk: File,
        privateKey: PrivateKey,
        certificate: X509Certificate
    ) {
        outputApk.parentFile?.mkdirs()

        val tempFile = File.createTempFile("apk_sign_", ".apk")
        try {
            JarFile(unsignedApk).use { jar ->
                JarOutputStream(FileOutputStream(tempFile)).apply {
                    setLevel(Deflater.BEST_COMPRESSION)
                }.use { jos ->
                    val manifest = buildManifest(jar)
                    jos.putNextEntry(JarEntry(MANIFEST_NAME))
                    jos.write(manifest.toString().toByteArray(Charsets.UTF_8))
                    jos.closeEntry()

                    val sfData = buildSignatureFile(jar, manifest)
                    jos.putNextEntry(JarEntry(SF_NAME))
                    jos.write(sfData.toString().toByteArray(Charsets.UTF_8))
                    jos.closeEntry()

                    val sigBlock = generatePkcs7Signature(sfData.toString().toByteArray(Charsets.UTF_8), privateKey, certificate)
                    jos.putNextEntry(JarEntry(RSA_NAME))
                    jos.write(sigBlock)
                    jos.closeEntry()

                    val entries = jar.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        if (entry.name.startsWith("META-INF/")) continue
                        jos.putNextEntry(JarEntry(entry.name))
                        jar.getInputStream(entry).copyTo(jos)
                        jos.closeEntry()
                    }
                }
            }
            tempFile.renameTo(outputApk)
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun buildManifest(jar: JarFile): StringBuilder {
        val sb = StringBuilder()
        sb.append("Manifest-Version: 1.0\r\n")
        sb.append("Created-By: DexProtector\r\n\r\n")

        val entries = jar.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            if (entry.name.startsWith("META-INF/")) continue

            val digest = sha256Base64(jar.getInputStream(entry).readBytes())
            sb.append("Name: ${entry.name}\r\n")
            sb.append("SHA-256-Digest: $digest\r\n\r\n")
        }
        return sb
    }

    private fun buildSignatureFile(jar: JarFile, manifest: StringBuilder): StringBuilder {
        val sb = StringBuilder()
        sb.append("Signature-Version: 1.0\r\n")
        sb.append("Created-By: DexProtector\r\n")
        sb.append("SHA-256-Digest-Manifest: ${sha256Base64(manifest.toString().toByteArray(Charsets.UTF_8))}\r\n\r\n")

        val entries = jar.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            if (entry.name.startsWith("META-INF/")) continue

            val data = jar.getInputStream(entry).readBytes()
            val entryDigest = sha256Base64(data)
            val sfLine = "Name: ${entry.name}\r\nSHA-256-Digest: $entryDigest\r\n\r\n"
            sb.append("Name: ${entry.name}\r\n")
            sb.append("SHA-256-Digest: ${sha256Base64(sfLine.toByteArray(Charsets.UTF_8))}\r\n\r\n")
        }
        return sb
    }

    private fun generatePkcs7Signature(
        sfBytes: ByteArray,
        privateKey: PrivateKey,
        certificate: X509Certificate
    ): ByteArray {
        val certList = listOf(certificate)
        val certStore = org.bouncycastle.cert.jcajce.JcaCertStore(certList)

        val generator = org.bouncycastle.cms.CMSSignedDataGenerator()
        val contentSigner = JcaContentSignerBuilder("SHA256WithRSA")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(privateKey)

        generator.addSignerInfoGenerator(
            org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder(
                org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build()
            ).build(contentSigner, certificate)
        )

        generator.addCertificates(certStore)

        val signedData = generator.generate(
            org.bouncycastle.cms.CMSProcessableByteArray(sfBytes),
            true
        )

        return signedData.encoded
    }

    private fun sha256Base64(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP)
    }
}
