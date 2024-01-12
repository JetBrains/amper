/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import kotlin.io.path.name
import kotlin.io.path.readBytes

interface CacheDirectory {
    fun guessPath(dependency: MavenDependency, extension: String): Path?
    fun getPath(dependency: MavenDependency, extension: String, bytes: ByteArray): Path
}

class GradleCacheDirectory(private val files: Path) : CacheDirectory {

    constructor() : this(getRootFromUserHome())

    companion object {
        private fun getRootFromUserHome() =
            Path.of(
                System.getenv("GRADLE_USER_HOME") ?: System.getProperty("user.home"),
                ".gradle/caches/modules-2/files-2.1"
            )
    }

    override fun toString(): String = "[Gradle] $files"

    override fun guessPath(dependency: MavenDependency, extension: String): Path? {
        val location = getLocation(dependency)
        val name = getName(dependency, extension)
        val pathFromVariant = fileFromVariant(dependency, name)?.let { location.resolve("${it.sha1}/${it.name}") }
        if (pathFromVariant != null) return pathFromVariant
        if (!location.toFile().exists()) return null
        return Files.walk(location, 2).filter {
            it.name == name
        }.findAny().orElse(null)
    }

    override fun getPath(dependency: MavenDependency, extension: String, bytes: ByteArray): Path {
        val location = getLocation(dependency)
        val name = getName(dependency, extension)
        val sha1 = computeHash("sha1", bytes)
        return location.resolve(sha1).resolve(name)
    }

    private fun getLocation(node: MavenDependency) = files.resolve("${node.group}/${node.module}/${node.version}")
}

class MavenCacheDirectory(private val repository: Path) : CacheDirectory {

    constructor() : this(getRootFromUserHome())

    companion object {
        private fun getRootFromUserHome() = Path.of(System.getProperty("user.home"), ".m2/repository")
    }

    override fun toString(): String = "[Maven] $repository"

    override fun guessPath(dependency: MavenDependency, extension: String): Path =
        repository.resolve(getLocation(dependency)).resolve(getName(dependency, extension))

    override fun getPath(dependency: MavenDependency, extension: String, bytes: ByteArray): Path =
        guessPath(dependency, extension)

    private fun getLocation(node: MavenDependency) =
        repository.resolve("${node.group.split('.').joinToString("/")}/${node.module}/${node.version}")
}

class DependencyFile(
    fileCache: List<CacheDirectory>,
    val dependency: MavenDependency,
    val extension: String
) {

    private val name = getName(dependency, extension)
    private val cacheDirectory =
        fileCache.find { it.guessPath(dependency, extension)?.toFile()?.exists() == true } ?: fileCache.first()
    val path: Path?
        get() = cacheDirectory.guessPath(dependency, extension)

    override fun toString(): String = path?.toString() ?: "[missing path]/$name"

    fun isDownloaded(level: ResolutionLevel, resolver: Resolver): Boolean =
        isDownloaded(level, resolver.settings.repositories, resolver.settings.progress)

    private fun isDownloaded(
        level: ResolutionLevel,
        repositories: Collection<String>,
        progress: Progress,
        verify: Boolean = true
    ): Boolean {
        val path = path
        if (path?.toFile()?.exists() != true) {
            return false
        }
        if (!verify) {
            return true
        }
        val bytes = path.readBytes()
        if (bytes.isEmpty()) {
            return false
        }
        for (repository in repositories) {
            val result = verify(bytes, repository, progress, level)
            return when (result) {
                VerificationResult.PASSED -> true
                VerificationResult.FAILED -> false
                else -> continue
            }
        }
        return level < ResolutionLevel.FULL
    }

    fun readText(): String {
        path?.let { path ->
            FileChannel.open(path, StandardOpenOption.READ).use { channel ->
                while (true) {
                    try {
                        channel.lock(0L, Long.MAX_VALUE, true).use {
                            return channel.readBytes().toString(Charsets.UTF_8)
                        }
                    } catch (e: OverlappingFileLockException) {
                        Thread.sleep(100)
                    }
                }
            }
        }
        throw AmperDependencyResolutionException("Path doesn't exist, download the file first")
    }

    fun download(resolver: Resolver): Boolean {
        return resolver.settings.repositories.find { download(it, resolver.settings.progress) }?.also {
            dependency.messages += Message("Downloaded from $it")
        } != null
    }

    private fun download(repository: String, progress: Progress, verify: Boolean = true): Boolean {
        download(repository, extension, progress)?.let { bytes ->
            if (verify) {
                val result = verify(bytes, repository, progress)
                if (result > VerificationResult.PASSED) {
                    if (result == VerificationResult.UNKNOWN) {
                        dependency.messages += Message(
                            "Unable to download checksums",
                            repository,
                            Severity.ERROR,
                        )
                    }
                    return false
                }
            }
            val target = cacheDirectory.getPath(dependency, extension, bytes)
            try {
                Files.createDirectories(target.parent)
                try {
                    FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use { channel ->
                        channel.lock().use {
                            channel.write(ByteBuffer.wrap(bytes))
                            return true
                        }
                    }
                } catch (e: Exception) {
                    when (e) {
                        is FileAlreadyExistsException, is OverlappingFileLockException -> {
                            FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.READ).use { channel ->
                                // We need to wait for the previous lock to get released.
                                // This way we ensure that the file was fully written to disk.
                                while (true) {
                                    try {
                                        channel.lock().use {
                                            if (verify) {
                                                val result = verify(channel.readBytes(), repository, progress)
                                                if (result > VerificationResult.PASSED) {
                                                    channel.write(ByteBuffer.wrap(bytes))
                                                }
                                            }
                                            return true
                                        }
                                    } catch (e: OverlappingFileLockException) {
                                        Thread.sleep(100)
                                    }
                                }
                            }
                        }

                        else -> throw e
                    }
                }
            } catch (e: IOException) {
                dependency.messages += Message(
                    "Unable to move downloaded file",
                    e.toString(),
                    Severity.ERROR,
                )
            }
        }
        return false
    }

    private fun FileChannel.readBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        val buff = ByteBuffer.allocate(1024)
        var count: Int
        while (read(buff).also { count = it } != -1) {
            baos.write(buff.array(), 0, count)
            buff.rewind()
        }
        return baos.toByteArray()
    }

    private fun verify(
        bytes: ByteArray,
        repository: String,
        progress: Progress,
        level: ResolutionLevel = ResolutionLevel.FULL
    ): VerificationResult {
        val algorithms = listOf("sha512", "sha256", "sha1", "md5")
        for (algorithm in algorithms) {
            val expectedHash = getOrDownloadExpectedHash(algorithm, repository, progress, level) ?: continue
            val actualHash = computeHash(algorithm, bytes)
            if (expectedHash != actualHash) {
                dependency.messages += Message(
                    "Hashes don't match for $algorithm",
                    "expected: $expectedHash, actual: $actualHash",
                    Severity.ERROR,
                )
                return VerificationResult.FAILED
            } else {
                return VerificationResult.PASSED
            }
        }
        return VerificationResult.UNKNOWN
    }

    enum class VerificationResult { PASSED, UNKNOWN, FAILED }

    private fun getOrDownloadExpectedHash(
        algorithm: String,
        repository: String,
        progress: Progress,
        level: ResolutionLevel = ResolutionLevel.FULL
    ): String? {
        val hashFromVariant = when (algorithm) {
            "sha512" -> fileFromVariant(dependency, name)?.sha512
            "sha256" -> fileFromVariant(dependency, name)?.sha256
            "sha1" -> fileFromVariant(dependency, name)?.sha1
            "md5" -> fileFromVariant(dependency, name)?.md5
            else -> null
        }
        if (hashFromVariant?.isNotEmpty() == true) {
            return hashFromVariant
        }
        val hashFromGradle = getHashFromGradleCacheDirectory(algorithm)
        if (hashFromGradle?.isNotEmpty() == true) {
            return hashFromGradle
        }
        val hashFromMaven = getHashFromMavenCacheDirectory(algorithm, repository, progress, level)
        if (hashFromMaven?.isNotEmpty() == true) {
            return hashFromMaven.split("\\s".toRegex()).getOrNull(0)
        }
        if (level < ResolutionLevel.FULL) {
            return null
        }
        val hashFromRepository = download(repository, "$extension.$algorithm", progress)
        return hashFromRepository?.toString(Charsets.UTF_8)?.split("\\s".toRegex())?.getOrNull(0)
    }

    private fun getHashFromGradleCacheDirectory(algorithm: String) =
        if (cacheDirectory is GradleCacheDirectory && algorithm == "sha1") path?.parent?.name else null

    private fun getHashFromMavenCacheDirectory(
        algorithm: String,
        repository: String,
        progress: Progress,
        level: ResolutionLevel
    ): String? {
        if (cacheDirectory !is MavenCacheDirectory) {
            return null
        }
        val hashFile = DependencyFile(listOf(cacheDirectory), dependency, "$extension.$algorithm")
        return if (hashFile.isDownloaded(level, listOf(repository), progress, false)
            || level == ResolutionLevel.FULL && hashFile.download(repository, progress, false)
        ) {
            hashFile.readText()
        } else {
            null
        }
    }

    private fun download(repository: String, extension: String, progress: Progress): ByteArray? {
        val url = repository +
                "/${dependency.group.replace('.', '/')}" +
                "/${dependency.module}" +
                "/${dependency.version}" +
                "/${dependency.module}-${dependency.version}.${extension}"
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val contentLength = connection.contentLength
                val bytes = ByteArrayOutputStream().also { baos ->
                    connection.inputStream.use { inputStream ->
                        BufferedInputStream(inputStream).use { bis ->
                            val data = ByteArray(1024)
                            var count: Int
                            while (bis.read(data, 0, 1024).also { count = it } != -1) {
                                baos.write(data, 0, count)
                            }
                        }
                    }
                }.toByteArray()
                if (contentLength != -1 && bytes.size != contentLength) {
                    dependency.messages += Message(
                        "Content length doesn't match for $repository",
                        "Expected: $contentLength, actual: ${bytes.size}",
                        Severity.ERROR
                    )
                    return null
                }
                return bytes
            } else if (responseCode != HttpURLConnection.HTTP_NOT_FOUND) {
                dependency.messages += Message(
                    "Unexpected response code for $repository",
                    "Expected: ${HttpURLConnection.HTTP_OK}, actual: $responseCode",
                    Severity.WARNING,
                )
            }
        } catch (e: Exception) {
            dependency.messages += Message(
                "Unable to reach $repository",
                e.toString(),
                Severity.ERROR,
            )
        }
        return null
    }
}

internal fun getName(node: MavenDependency, extension: String): String = "${node.module}-${node.version}.${extension}"

private fun fileFromVariant(dependency: MavenDependency, name: String) =
    dependency.variant?.files?.singleOrNull { it.name == name }

internal fun computeHash(algorithm: String, bytes: ByteArray): String {
    val messageDigest = MessageDigest.getInstance(algorithm)
    messageDigest.update(bytes, 0, bytes.size)
    val hash = messageDigest.digest()
    return toHex(hash)
}

private fun toHex(bytes: ByteArray): String {
    val result = StringBuilder()
    for (b in bytes) {
        result.append(((b.toInt() and 0xFF) + 0x100).toString(16).substring(1))
    }
    return result.toString()
}
