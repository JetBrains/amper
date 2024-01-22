/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.jetbrains.amper.dependency.resolution.metadata.xml.parseMetadata
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
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.time.ZonedDateTime
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText

interface LocalRepository {
    fun guessPath(dependency: MavenDependency, name: String): Path?
    fun getPath(dependency: MavenDependency, name: String, bytes: ByteArray): Path
}

class GradleLocalRepository(private val files: Path) : LocalRepository {

    constructor() : this(getRootFromUserHome())

    companion object {
        private fun getRootFromUserHome() =
            Path.of(
                System.getenv("GRADLE_USER_HOME") ?: System.getProperty("user.home"),
                ".gradle/caches/modules-2/files-2.1"
            )
    }

    override fun toString(): String = "[Gradle] $files"

    override fun guessPath(dependency: MavenDependency, name: String): Path? {
        val location = getLocation(dependency)
        val pathFromVariant = fileFromVariant(dependency, name)?.let { location.resolve("${it.sha1}/${it.name}") }
        if (pathFromVariant != null) return pathFromVariant
        if (!location.exists()) return null
        return Files.walk(location, 2).filter {
            it.name == name
        }.findAny().orElse(null)
    }

    override fun getPath(dependency: MavenDependency, name: String, bytes: ByteArray): Path {
        val location = getLocation(dependency)
        val sha1 = computeHash("sha1", bytes)
        return location.resolve(sha1).resolve(name)
    }

    private fun getLocation(dependency: MavenDependency) =
        files.resolve("${dependency.group}/${dependency.module}/${dependency.version}")
}

class MavenLocalRepository(private val repository: Path) : LocalRepository {

    constructor() : this(getRootFromUserHome())

    companion object {
        private fun getRootFromUserHome() = Path.of(System.getProperty("user.home"), ".m2/repository")
    }

    override fun toString(): String = "[Maven] $repository"

    override fun guessPath(dependency: MavenDependency, name: String): Path =
        repository.resolve(getLocation(dependency)).resolve(name)

    override fun getPath(dependency: MavenDependency, name: String, bytes: ByteArray): Path =
        guessPath(dependency, name)

    private fun getLocation(dependency: MavenDependency) =
        repository.resolve(
            "${dependency.group.split('.').joinToString("/")}/${dependency.module}/${dependency.version}"
        )
}

fun getDependencyFile(
    dependency: MavenDependency,
    nameWithoutExtension: String,
    extension: String,
    fileCache: FileCache = dependency.fileCache,
) = if (dependency.version.endsWith("-SNAPSHOT")) {
    SnapshotDependencyFile(dependency, nameWithoutExtension, extension, fileCache)
} else {
    DependencyFile(dependency, nameWithoutExtension, extension, fileCache)
}

open class DependencyFile(
    val dependency: MavenDependency,
    val nameWithoutExtension: String,
    val extension: String,
    fileCache: FileCache = dependency.fileCache,
) {

    private val cacheDirectory = fileCache.localRepositories.find {
        it.guessPath(dependency, "$nameWithoutExtension.$extension")?.exists() == true
    } ?: fileCache.fallbackLocalRepository
    val path: Path?
        get() = cacheDirectory.guessPath(dependency, "$nameWithoutExtension.$extension")

    override fun toString(): String = path?.toString() ?: "[missing path]/$nameWithoutExtension.$extension"

    fun isDownloaded(level: ResolutionLevel, settings: Settings): Boolean =
        isDownloaded(level, settings.repositories, settings.progress)

    protected open fun isDownloaded(
        level: ResolutionLevel,
        repositories: List<String>,
        progress: Progress,
        verify: Boolean = true
    ): Boolean {
        val path = path
        if (path?.exists() != true) {
            return false
        }
        if (!verify) {
            return true
        }
        val bytes = path.readBytes()
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

    fun download(settings: Settings): Boolean {
        return settings.repositories.find { download(it, settings.progress) }?.also {
            dependency.messages += Message("Downloaded from $it")
        } != null
    }

    protected fun download(
        repository: String,
        progress: Progress,
        verify: Boolean = true
    ): Boolean {
        downloadBytes(repository, progress)?.let { bytes ->
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
            val target = cacheDirectory.getPath(dependency, "$nameWithoutExtension.$extension", bytes)
            try {
                Files.createDirectories(target.parent)
                try {
                    FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use { channel ->
                        channel.lock().use {
                            channel.write(ByteBuffer.wrap(bytes))
                            onFileDownloaded()
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
                                            if (shouldOverwrite(repository, progress, verify, channel)) {
                                                channel.write(ByteBuffer.wrap(bytes))
                                                onFileDownloaded()
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
                    "Unable to save downloaded file",
                    e.toString(),
                    Severity.ERROR,
                )
            }
        }
        return false
    }

    protected open fun shouldOverwrite(
        repository: String,
        progress: Progress,
        verify: Boolean,
        channel: FileChannel
    ): Boolean = verify && verify(
        channel.readBytes(),
        repository,
        progress
    ) > VerificationResult.PASSED

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
        requestedLevel: ResolutionLevel = ResolutionLevel.FULL
    ): VerificationResult {
        // Let's first check hashes available on disk.
        val algorithms = setOf(ResolutionLevel.PARTIAL, requestedLevel)
            .flatMap { level -> listOf("sha512", "sha256", "sha1", "md5").map { level to it } }
        for ((level, algorithm) in algorithms) {
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
        val name = "$nameWithoutExtension.$extension"
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
        val hashFile = getDependencyFile(dependency, nameWithoutExtension, "$extension.$algorithm").also {
            it.download(repository, progress, false)
        }
        val hashFromRepository = hashFile.path?.takeIf { it.exists() }?.readBytes()
        return hashFromRepository?.toString(Charsets.UTF_8)?.split("\\s".toRegex())?.getOrNull(0)
    }

    private fun getHashFromGradleCacheDirectory(algorithm: String) =
        if (cacheDirectory is GradleLocalRepository && algorithm == "sha1") {
            path?.parent?.name?.padStart(40, '0') // old Gradle compatibility
        } else {
            null
        }

    private fun getHashFromMavenCacheDirectory(
        algorithm: String,
        repository: String,
        progress: Progress,
        level: ResolutionLevel
    ): String? {
        if (cacheDirectory !is MavenLocalRepository) {
            return null
        }
        val hashFile = DependencyFile(dependency, nameWithoutExtension, "$extension.$algorithm")
        return if (hashFile.isDownloaded(level, listOf(repository), progress, false)
            || level == ResolutionLevel.FULL && hashFile.download(repository, progress, false)
        ) {
            hashFile.readText()
        } else {
            null
        }
    }

    private fun downloadBytes(repository: String, progress: Progress): ByteArray? {
        try {
            val url = repository +
                    "/${dependency.group.replace('.', '/')}" +
                    "/${dependency.module}" +
                    "/${dependency.version}" +
                    "/${getNamePart(repository, nameWithoutExtension, extension, progress)}"
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

    protected open fun getNamePart(repository: String, name: String, extension: String, progress: Progress) =
        "$name.$extension"

    protected open fun onFileDownloaded() {
    }
}

class SnapshotDependencyFile(
    dependency: MavenDependency,
    name: String,
    extension: String,
    fileCache: FileCache = dependency.fileCache,
) : DependencyFile(dependency, name, extension, fileCache) {

    private val mavenMetadata by lazy {
        SnapshotDependencyFile(dependency, "maven-metadata", "xml", FileCacheBuilder {
            amperCache = fileCache.amperCache
            localRepositories = listOf(
                MavenLocalRepository(fileCache.amperCache.resolve("caches/maven-metadata"))
            )
        }.build())
    }
    private val versionFile by lazy { mavenMetadata.path?.parent?.resolve("$extension.version") }
    private val snapshotVersion by lazy {
        val metadata = mavenMetadata.readText().parseMetadata()
        metadata.versioning.snapshotVersions.snapshotVersions.find {
            it.extension == extension.substringBefore('.') // pom.sha512 -> pom
        }?.value
    }

    override fun isDownloaded(
        level: ResolutionLevel,
        repositories: List<String>,
        progress: Progress,
        verify: Boolean
    ): Boolean {
        val path = path
        if (path?.exists() != true) {
            return false
        }
        if (nameWithoutExtension != "maven-metadata") {
            if (versionFile?.exists() != true) {
                return false
            }
            if (mavenMetadata.isDownloaded(level, repositories, progress, false)) {
                if (versionFile?.readText() != snapshotVersion) {
                    return false
                }
            } else {
                return false
            }
        } else {
            return Files.getLastModifiedTime(path) > FileTime.from(ZonedDateTime.now().minusDays(1).toInstant())
        }
        return super.isDownloaded(level, repositories, progress, verify)
    }

    override fun getNamePart(repository: String, name: String, extension: String, progress: Progress): String {
        if (name != "maven-metadata" &&
            (mavenMetadata.isDownloaded(ResolutionLevel.FULL, listOf(repository), progress, false)
                    || mavenMetadata.download(repository, progress, verify = false))
        ) {
            snapshotVersion?.let { name.replace(dependency.version, it) }?.let { return "$it.$extension" }
        }
        return super.getNamePart(repository, name, extension, progress)
    }

    override fun shouldOverwrite(
        repository: String,
        progress: Progress,
        verify: Boolean,
        channel: FileChannel
    ): Boolean = nameWithoutExtension == "maven-metadata" || versionFile?.readText() != snapshotVersion

    override fun onFileDownloaded() {
        if (nameWithoutExtension != "maven-metadata") {
            snapshotVersion?.let { versionFile?.writeText(it) }
        }
    }
}

internal fun getNameWithoutExtension(node: MavenDependency): String = "${node.module}-${node.version}"

private fun fileFromVariant(dependency: MavenDependency, name: String) =
    dependency.variant?.files?.singleOrNull { it.name == name }

internal fun computeHash(algorithm: String, bytes: ByteArray): String {
    val messageDigest = MessageDigest.getInstance(algorithm)
    messageDigest.update(bytes, 0, bytes.size)
    val hash = messageDigest.digest()
    return toHex(hash)
}

@OptIn(ExperimentalStdlibApi::class)
private fun toHex(bytes: ByteArray) = bytes.toHexString()
