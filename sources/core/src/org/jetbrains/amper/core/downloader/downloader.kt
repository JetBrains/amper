/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.downloader

import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import org.jetbrains.amper.concurrency.FileMutexGroup
import org.jetbrains.amper.concurrency.withLock
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.stdlib.hashing.sha256String
import org.jetbrains.amper.telemetry.use
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.moveTo
import kotlin.io.path.pathString
import kotlin.io.path.setLastModifiedTime

// initially from intellij:community/platform/build-scripts/downloader/src/ktor.kt

object Downloader {

    // increment on semantic changes in download code to invalidate all current caches
    // e.g. when some issues in downloading code were fixed to that extent, that
    // existing files must not be reused
    private const val DOWNLOAD_CODE_VERSION = 1

    private val logger = LoggerFactory.getLogger(javaClass)

    private val fileLocks = FileMutexGroup.striped(stripeCount = 256)

    suspend fun downloadFileToCacheLocation(
        url: String,
        userCacheRoot: AmperUserCacheRoot,
        infoLog: Boolean = true,
    ): Path {
        val target = getTargetFile(userCacheRoot, url)
        fileLocks.withLock(target) {
            if (target.exists()) {
                Span.current().addEvent(
                    "use asset from cache", Attributes.of(
                        AttributeKey.stringKey("url"), url,
                        AttributeKey.stringKey("target"), target.pathString,
                    )
                )

                // update file modification time to maintain FIFO caches, i.e., in persistent cache folder on TeamCity agent
                try {
                    target.setLastModifiedTime(FileTime.from(Instant.now()))
                } catch (t: Throwable) {
                    logger.warn("Unable to update mtime: $target", t)
                }

                return target
            }

            if (infoLog) {
                logger.info("Downloading $url to ${target.pathString}")
            }

            return spanBuilder("download").setAttribute("url", url).setAttribute("target", target.pathString).use {
                // TODO check if this is redundant with Ktor's retry mechanism.
                //  It might be necessary due to expectSuccess=false.
                suspendingRetryWithExponentialBackOff {
                    // save to the same disk to ensure that move will be atomic and not as a copy
                    val tempFile = target.parent
                        .resolve("${target.fileName}-${(Instant.now().epochSecond - 1634886185).toString(36)}-${Instant.now().nano.toString(36)}".take(255))
                    tempFile.deleteIfExists()
                    // Add a hook, so interruption won't leave garbage files.
                    tempFile.toFile().deleteOnExit()
                    target.parent.createDirectories()
                    try {
                        // TODO each HttpClient.config call creates a new client, do we really need this?
                        val effectiveClient = httpClient.config {
                            install(ContentEncoding) {
                                // Any `Content-Encoding` will drop `Content-Length` header in nginx responses,
                                // yet we rely on that header for file-length checks after download.
                                // Hence, we override `ContentEncoding` plugin config from `httpClient` with zero weights.
                                deflate(0.0F)
                                gzip(0.0F)
                                identity() // tells the server that no compression is also acceptable
                            }
                        }

                        val response = effectiveClient.use { client ->
                            client.prepareGet(url) {
                                // we manually handle errors below
                                expectSuccess = false
                            }.execute {
                                coroutineScope {
                                    it.bodyAsChannel().copyAndClose(writeChannel(tempFile))
                                }
                                it
                            }
                        }

                        val statusCode = response.status.value
                        if (statusCode != 200) {
                            failWithResponseData(response, tempFile, statusCode, url)
                        }

                        checkContentLength(response, url, tempFile)
                        tempFile.moveTo(target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    } catch (httpException: HttpStatusException) {
                        if (httpException.statusCode == 404) {
                            // do not retry 404
                            throw NoMoreRetriesException(httpException.toString(), httpException)
                        } else {
                            throw httpException
                        }
                    } finally {
                        tempFile.deleteIfExists()
                    }
                }

                target
            }
        }
    }

    private fun getTargetFile(cacheRoot: AmperUserCacheRoot, uriString: String): Path {
        val lastNameFromUri = uriString.substringAfterLast('/')
        val hashString = "${uriString}V${DOWNLOAD_CODE_VERSION}".sha256String().take(10)
        return cacheRoot.downloadCache.resolve("${hashString}-${lastNameFromUri}")
    }

    private fun failWithResponseData(
        response: HttpResponse,
        tempFile: Path,
        statusCode: Int,
        url: String
    ): Nothing {
        val message = buildString {
            appendLine("Cannot download")
            appendLine(response.headers.entries().joinToString("\n") { (name, values) -> "Header: $name: $values" })
            appendLine()
            if (tempFile.exists()) {
                val firstBytes = tempFile.inputStream().use { it.readNBytes(1024) }
                // No need to try and guess the encoding here: the String conversion should never fail, so at worst
                // we'll get some random characters. This is fine because it's just an error message.
                appendLine(firstBytes.toString(StandardCharsets.UTF_8))
            }
        }
        throw HttpStatusException(message, statusCode, url)
    }

    private fun checkContentLength(response: HttpResponse, url: String, tempFile: Path) {
        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1
        check(contentLength > 0) { "Header '${HttpHeaders.ContentLength}' is missing or zero for $url" }
        val fileSize = tempFile.fileSize()
        check(fileSize == contentLength) {
            "Wrong file length after downloading uri '$url' to '$tempFile': expected length $contentLength " +
                    "from ${HttpHeaders.ContentLength} header, but got $fileSize on disk"
        }
    }

    fun getUriForMavenArtifact(
        mavenRepository: String,
        groupId: String,
        artifactId: String,
        version: String,
        classifier: String? = null,
        packaging: String,
    ): URI {
        val base = mavenRepository.trim('/')
        val groupStr = groupId.replace('.', '/')
        val classifierStr = if (classifier != null) "-${classifier}" else ""
        return URI.create("${base}/${groupStr}/${artifactId}/${version}/${artifactId}-${version}${classifierStr}.${packaging}")
    }

    class HttpStatusException(message: String, val statusCode: Int, val url: String) :
        IllegalStateException(message) {
        override fun toString(): String =
            "HttpStatusException(status=${statusCode}, url=${url}, message=${message})"
    }
}

private val WRITE_NEW_OPERATION: EnumSet<StandardOpenOption> = EnumSet.of(
    StandardOpenOption.WRITE,
    StandardOpenOption.CREATE,
    StandardOpenOption.TRUNCATE_EXISTING,
)

private fun CoroutineScope.writeChannel(file: Path): ByteWriteChannel {
    return reader(CoroutineName("file-writer") + Dispatchers.IO, autoFlush = true) {
        FileChannel.open(file, WRITE_NEW_OPERATION).use { fileChannel ->
            channel.copyTo(fileChannel)
        }
    }.channel
}
