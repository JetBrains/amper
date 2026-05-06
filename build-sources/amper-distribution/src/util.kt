/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ReplacePrintlnWithLogging")

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.bouncycastle.openpgp.api.OpenPGPKeyReader
import org.bouncycastle.openpgp.api.bc.BcOpenPGPApi
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val FilteredClasspath.resolvedFiles: List<Path>
    get() = classpath.resolvedFiles.filter { path -> includeIfFileNameContains.any { it in path.name } }

private val downloadHttpClient: HttpClient by lazy {
    HttpClient {
        expectSuccess = true

        install(UserAgent) {
            agent = "JetBrains Amper"
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            retryOnException(maxRetries = 3, retryOnTimeout = true)
            exponentialDelay()
        }

        // has to be after HttpRequestRetry because we use retryOnTimeout
        install(HttpTimeout) {
            connectTimeoutMillis = 5.seconds.inWholeMilliseconds
            requestTimeoutMillis = 2.minutes.inWholeMilliseconds
        }
    }
}

internal fun downloadFile(url: String, target: Path) {
    println("Downloading $url")
    runBlocking(Dispatchers.IO) {
        downloadHttpClient.prepareGet(url).execute { response: HttpResponse ->
            target.outputStream().buffered().use { output ->
                response.bodyAsChannel().copyTo(output)
            }
        }
    }
}

internal fun verifyGpgSignature(
    dataFile: Path,
    signatureFile: Path,
    publicKey: String,
) {
    println("Verifying GPG signature of ${dataFile.name}")
    val certificate = OpenPGPKeyReader().parseCertificate(publicKey)
    val signatureVerifier = signatureFile.inputStream().use { signatureStream ->
        BcOpenPGPApi().verifyDetachedSignature()
            .addSignatures(signatureStream)
            .addVerificationCertificate(certificate)
    }
    val processedSignatures = dataFile.inputStream().use { dataStream ->
        signatureVerifier.process(dataStream)
    }
    if (processedSignatures.isEmpty()) {
        error("No GPG signatures found for ${dataFile.name}")
    }
    if (!processedSignatures.single().isValid) {
        error("GPG signature verification failed for ${dataFile.name}")
    }
    println("GPG signature verified successfully for ${dataFile.name}")
}
