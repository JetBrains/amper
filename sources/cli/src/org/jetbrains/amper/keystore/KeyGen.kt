/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.keystore

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v1CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.jetbrains.amper.core.properties.readProperties
import org.jetbrains.amper.frontend.schema.KeystoreProperty
import org.jetbrains.amper.frontend.schema.keyAlias
import org.jetbrains.amper.frontend.schema.keyPassword
import org.jetbrains.amper.frontend.schema.storeFile
import org.jetbrains.amper.frontend.schema.storePassword
import org.slf4j.LoggerFactory
import java.io.FileOutputStream
import java.math.BigInteger
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.*
import javax.security.auth.x500.X500Principal
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.div
import kotlin.io.path.notExists

private const val asymmetricAlgorithm = "RSA"
private const val signatureAlgorithm = "SHA1withRSA"

internal class KeystoreGenerationException(override val message: String) : Exception(message)

private fun keystoreGenerationError(message: String): Nothing = throw KeystoreGenerationException(message)

object KeystoreGenerator {
    fun createNewKeystore(propertiesFile: Path, dn: String) {
        if (propertiesFile.notExists()) {
            keystoreGenerationError("$propertiesFile does not exist")
        }
        logger.debug("Getting keystore configuration from properties file {}", propertiesFile)
        val properties = propertiesFile.readProperties()

        val storeFile = properties.storeFile ?: errorKeystoreFieldMissing(propertiesFile, KeystoreProperty.StoreFile)

        if (storeFile.isBlank()) {
            keystoreGenerationError("${KeystoreProperty.StoreFile} is blank in $propertiesFile")
        }

        val storePassword = properties.storePassword ?: errorKeystoreFieldMissing(propertiesFile, KeystoreProperty.StorePassword)
        val keyPassword = properties.keyPassword ?: errorKeystoreFieldMissing(propertiesFile, KeystoreProperty.KeyPassword)
        val keyAlias = properties.keyAlias ?: errorKeystoreFieldMissing(propertiesFile, KeystoreProperty.KeyAlias)

        if (keyAlias.isBlank()) {
            keystoreGenerationError("${KeystoreProperty.KeyAlias} is blank in $propertiesFile")
        }

        createNewKeystore(Path(storeFile), storePassword, keyAlias, keyPassword, dn)
    }

    fun createNewKeystore(
        storeFile: Path = Path(System.getProperty("user.home")) / ".keystores" / "release.keystore",
        storePassword: String = "",
        keyAlias: String = "android",
        keyPassword: String = "",
        dn: String = "CN=${System.getProperty("user.name", "Unknown")}"
    ) {
        logger.info("Generating new keystore at $storeFile")
        logger.info("Key alias: $keyAlias")
        logger.info("Distinguished name: $dn")
        if (storeFile.toFile().exists()) {
            keystoreGenerationError("Keystore already exists: $storeFile")
        }
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)
        val generated = generateKeyAndCertificate(dn)
        ks.setKeyEntry(keyAlias, generated.first, keyPassword.toCharArray(), arrayOf(generated.second))
        storeFile.createParentDirectories()
        FileOutputStream(storeFile.toFile()).use { fos ->
            ks.store(fos, storePassword.toCharArray())
        }
        logger.info("Keystore generated successfully")
    }

    private fun errorKeystoreFieldMissing(propertiesFile: Path, field: KeystoreProperty): Nothing =
        keystoreGenerationError("$propertiesFile does not contain \"${field.key}\"")

    private fun generateKeyAndCertificate(dn: String): Pair<PrivateKey, X509Certificate> {
        val validityYears = 30
        val keyPair = KeyPairGenerator.getInstance(asymmetricAlgorithm).generateKeyPair()
        val issuer = X500Name(X500Principal(dn).name)
        val notBefore = Date(System.currentTimeMillis())
        val notAfter = Date(System.currentTimeMillis() + (validityYears * 365L * 24 * 60 * 60 * 1000))
        val publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        val signer = JcaContentSignerBuilder(signatureAlgorithm)
            .setProvider(BouncyCastleProvider())
            .build(keyPair.private)
        val builder = X509v1CertificateBuilder(issuer, BigInteger.ONE, notBefore, notAfter, issuer, publicKeyInfo)
        val holder = builder.build(signer)
        val converter = JcaX509CertificateConverter().setProvider(BouncyCastleProvider())
        return keyPair.private to converter.getCertificate(holder)
    }

    private val logger = LoggerFactory.getLogger(this::class.java)
}
