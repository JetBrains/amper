/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.downloader

import com.google.common.hash.Hashing
import io.ktor.client.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.jvm.nio.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.util.StripedMutex
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.*

// initially from intellij:community/platform/build-scripts/downloader/src/ktor.kt

@OptIn(DelicateCoroutinesApi::class)
suspend fun downloadFileToCacheLocation(
    url: String,
    userCacheRoot: AmperUserCacheRoot,
    token: String? = null,
    username: String? = null,
    password: String? = null,
): Path {
    val target = getTargetFile(userCacheRoot, url)
    val targetPath = target.toString()
    val lock = fileLocks.getLock(targetPath.hashCode())
    lock.lock()
    try {
        if (Files.exists(target)) {
            Span.current().addEvent("use asset from cache", Attributes.of(
                AttributeKey.stringKey("url"), url,
                AttributeKey.stringKey("target"), targetPath,
            ))

            // update file modification time to maintain FIFO caches, i.e., in persistent cache folder on TeamCity agent
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    Files.setLastModifiedTime(target, FileTime.from(Instant.now()))
                } catch (t: Throwable) {
                    LoggerFactory.getLogger(javaClass).warn("Unable to update mtime: $target", t)
                }
            }

            return target
        }

        println(" * Downloading $url")

        return spanBuilder("download").setAttribute("url", url).setAttribute("target", targetPath).useWithScope {
            suspendingRetryWithExponentialBackOff {
                // save to the same disk to ensure that move will be atomic and not as a copy
                val tempFile = target.parent
                    .resolve("${target.fileName}-${(Instant.now().epochSecond - 1634886185).toString(36)}-${Instant.now().nano.toString(36)}".take(255))
                Files.deleteIfExists(tempFile)
                Files.createDirectories(target.parent)
                try {
                    // each io.ktor.client.HttpClient.config call creates a new client
                    // extract common configuration to prevent excessive client creation
                    val commonConfig: HttpClientConfig<*>.() -> Unit = {
                        expectSuccess = false // we have custom error handler

                        install(ContentEncoding) {
                            // Any `Content-Encoding` will drop `Content-Length` header in nginx responses,
                            // yet we rely on that header in `downloadFileToCacheLocation`.
                            // Hence, we override `ContentEncoding` plugin config from `httpClient` with zero weights.
                            deflate(0.0F)
                            gzip(0.0F)
                        }
                    }

                    val effectiveClient = when {
                        token != null -> httpClient.config {
                            commonConfig()
                            Auth {
                                bearer {
                                    loadTokens {
                                        BearerTokens(token, "")
                                    }
                                }
                            }
                        }

                        username != null && password != null -> httpClient.config {
                            commonConfig()
                            Auth {
                                basic {
                                    credentials {
                                        sendWithoutRequest { true }
                                        BasicAuthCredentials(username, password)
                                    }
                                }
                            }
                        }

                        else -> httpClient.config(commonConfig)
                    }

                    val response = effectiveClient.use { client ->
                        client.prepareGet(url).execute {
                            coroutineScope {
                                it.bodyAsChannel().copyAndClose(writeChannel(tempFile))
                            }
                            it
                        }
                    }

                    val statusCode = response.status.value
                    if (statusCode != 200) {
                        val builder = StringBuilder("Cannot download\n")
                        val headers = response.headers
                        headers.names()
                            .asSequence()
                            .sorted()
                            .flatMap { headerName -> headers.getAll(headerName)!!.map { value -> "Header: $headerName: $value\n" } }
                            .forEach(builder::append)
                        builder.append('\n')
                        if (Files.exists(tempFile)) {
                            Files.newInputStream(tempFile).use { inputStream ->
                                // yes, not trying to guess encoding
                                // string constructor should be exception-free, so at worse, we'll get some random characters
                                builder.append(inputStream.readNBytes(1024).toString(StandardCharsets.UTF_8))
                            }
                        }
                        throw HttpStatusException(builder.toString(), statusCode, url)
                    }

                    val contentLength = response.headers.get(HttpHeaders.ContentLength)?.toLongOrNull() ?: -1
                    check(contentLength > 0) { "Header '${HttpHeaders.ContentLength}' is missing or zero for $url" }
                    val fileSize = Files.size(tempFile)
                    check(fileSize == contentLength) {
                        "Wrong file length after downloading uri '$url' to '$tempFile': expected length $contentLength " +
                                "from ${HttpHeaders.ContentLength} header, but got $fileSize on disk"
                    }
                    Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                }
                finally {
                    Files.deleteIfExists(tempFile)
                }
            }

            target
        }
    }
    finally {
        lock.unlock()
    }
}

fun getUriForMavenArtifact(mavenRepository: String,
                           groupId: String,
                           artifactId: String,
                           version: String,
                           classifier: String? = null,
                           packaging: String): URI {
    val base = mavenRepository.trim('/')
    val groupStr = groupId.replace('.', '/')
    val classifierStr = if (classifier != null) "-${classifier}" else ""
    return URI.create("${base}/${groupStr}/${artifactId}/${version}/${artifactId}-${version}${classifierStr}.${packaging}")
}

// increment on semantic changes in download code to invalidate all current caches
// e.g. when some issues in downloading code were fixed to that extent, that
// existing files must not be reused
private const val DOWNLOAD_CODE_VERSION = 1

private fun getTargetFile(cacheRoot: AmperUserCacheRoot, uriString: String): Path {
    val lastNameFromUri = uriString.substring(uriString.lastIndexOf('/') + 1)
    val hashString = hashString("${uriString}V${DOWNLOAD_CODE_VERSION}").substring(0, 10)
    return cacheRoot.path.resolve("download.cache").resolve("${hashString}-${lastNameFromUri}")
}

internal fun hashString(s: String): String =
    BigInteger(1, Hashing.sha256().hashString(s, StandardCharsets.UTF_8).asBytes()).toString(36)

internal val fileLocks = StripedMutex(stripeCount = 256)

class HttpStatusException(message: String, private val statusCode: Int, val url: String) : IllegalStateException(message) {
    override fun toString(): String =
        "HttpStatusException(status=${statusCode}, url=${url}, message=${message})"
}

private val WRITE_NEW_OPERATION = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
internal fun CoroutineScope.writeChannel(file: Path): ByteWriteChannel {
    return reader(CoroutineName("file-writer") + Dispatchers.IO, autoFlush = true) {
        FileChannel.open(file, WRITE_NEW_OPERATION).use { fileChannel ->
            channel.copyTo(fileChannel)
        }
    }.channel
}
