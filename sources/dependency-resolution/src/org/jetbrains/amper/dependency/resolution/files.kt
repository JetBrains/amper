/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.jetbrains.amper.dependency.resolution.metadata.xml.parseMetadata
import java.io.BufferedInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.channels.ReadableByteChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.time.ZonedDateTime
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Provides mapping between [MavenDependency] and a location on disk.
 * It's used to either fetch an existing file or download a new one.
 *
 * @see GradleLocalRepository
 * @see MavenLocalRepository
 */
interface LocalRepository {

    /**
     * Returns a path for a dependency if it can be determined from the information at hand
     * or `null` otherwise.
     *
     * As Gradle uses a SHA1 hash as a part of a path which might not be available without a file content,
     * this method allows returning `null`.
     */
    fun guessPath(dependency: MavenDependency, name: String): Path?

    /**
     * Returns a path to a temp file for a particular dependency.
     *
     * The file is downloaded to a temp location to be later moved to a permanent one provided by [getPath].
     * Both paths should preferably be on the same files to allow atomic move.
     */
    fun getTempPath(dependency: MavenDependency, name: String): Path

    /**
     * Returns a path to a file on disk.
     * It can't return `null` as all necessary information must be available at a call site.
     *
     * A SHA1 hash is used by Gradle as a part of a path.
     */
    fun getPath(dependency: MavenDependency, name: String, sha1: String): Path
}

/**
 * Defines a `.gradle` directory structure.
 * It accepts a path to the `files-2.1` directory or defaults to `~/.gradle/caches/modules-2/files-2.1`.
 */
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
        return Files.walk(location, 2).use {
            it.filter { it.name == name }.findAny().orElse(null)
        }
    }

    override fun getTempPath(dependency: MavenDependency, name: String): Path =
        getLocation(dependency).resolve("~$name")

    override fun getPath(dependency: MavenDependency, name: String, sha1: String): Path {
        val location = getLocation(dependency)
        return location.resolve(sha1).resolve(name)
    }

    private fun getLocation(dependency: MavenDependency) =
        files.resolve("${dependency.group}/${dependency.module}/${dependency.version}")
}

/**
 * Defines an `.m2` directory structure.
 * It accepts a path to the `repository` directory or defaults to `~/.m2/repository`.
 */
class MavenLocalRepository(private val repository: Path) : LocalRepository {

    constructor() : this(getRootFromUserHome())

    companion object {
        private fun getRootFromUserHome() = Path.of(System.getProperty("user.home"), ".m2/repository")
    }

    override fun toString(): String = "[Maven] $repository"

    override fun guessPath(dependency: MavenDependency, name: String): Path =
        repository.resolve(getLocation(dependency)).resolve(name)

    override fun getTempPath(dependency: MavenDependency, name: String): Path = guessPath(dependency, "~$name")

    override fun getPath(dependency: MavenDependency, name: String, sha1: String): Path = guessPath(dependency, name)

    private fun getLocation(dependency: MavenDependency) =
        repository.resolve(
            "${dependency.group.split('.').joinToString("/")}/${dependency.module}/${dependency.version}"
        )
}

fun getDependencyFile(dependency: MavenDependency, nameWithoutExtension: String, extension: String) =
    if (dependency.version.endsWith("-SNAPSHOT")) {
        SnapshotDependencyFile(dependency, nameWithoutExtension, extension)
    } else {
        DependencyFile(dependency, nameWithoutExtension, extension)
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

    open fun isDownloaded(): Boolean = path?.exists() == true

    fun hasMatchingChecksum(level: ResolutionLevel, settings: Settings): Boolean =
        hasMatchingChecksum(level, settings.repositories, settings.progress)

    private fun hasMatchingChecksum(level: ResolutionLevel, repositories: List<String>, progress: Progress): Boolean {
        val path = path ?: return false
        val hashers = computeHash(path)
        for (repository in repositories) {
            val result = verify(hashers, repository, progress, level)
            return when (result) {
                VerificationResult.PASSED -> true
                VerificationResult.FAILED -> false
                else -> continue
            }
        }
        return level < ResolutionLevel.NETWORK
    }

    fun readText(): String = path?.readText()
        ?: throw AmperDependencyResolutionException("Path doesn't exist, download the file first")

    fun download(settings: Settings): Boolean = download(settings.repositories, settings.progress)

    protected fun download(
        repositories: List<String>,
        progress: Progress,
        verify: Boolean = true,
    ): Boolean {
        val temp = cacheDirectory.getTempPath(dependency, "$nameWithoutExtension.$extension")
        try {
            Files.createDirectories(temp.parent)
            try {
                FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use { channel ->
                    channel.lock().use {
                        return download(channel, temp, repositories, progress, verify)
                    }
                }
            } catch (e: Exception) {
                when (e) {
                    is FileAlreadyExistsException, is OverlappingFileLockException -> {
                        // Someone's already downloading the file. Let's wait for it and then check the result.
                        var delay = 10L
                        while (true) {
                            try {
                                FileChannel.open(temp, StandardOpenOption.WRITE).use { channel ->
                                    channel.lock().use {
                                        // Another thread has created a file,
                                        // but this thread was faster to acquire a lock.
                                        return download(channel, temp, repositories, progress, verify)
                                    }
                                }
                            } catch (e: Exception) {
                                when (e) {
                                    // The file is still being downloaded.
                                    is OverlappingFileLockException -> {
                                        Thread.sleep(delay)
                                        delay = (delay * 2).coerceAtMost(1000)
                                        continue
                                    }

                                    // The file has been released and moved.
                                    is NoSuchFileException -> {
                                        break
                                    }

                                    else -> throw e
                                }
                            }
                        }
                        return isDownloaded() && (!verify || hasMatchingChecksum(
                            ResolutionLevel.NETWORK,
                            repositories,
                            progress
                        ))
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
            return false
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun download(
        channel: FileChannel,
        temp: Path,
        repositories: List<String>,
        progress: Progress,
        verify: Boolean,
    ): Boolean {
        for (repository in repositories) {
            val hashers = createHashers().filter { verify || it.algorithm == "sha1" }
            val writers = hashers.map { it.writer } + Writer(channel::write)
            if (!download(writers, repository, progress)) {
                channel.truncate(0)
                continue
            }
            if (verify) {
                val result = verify(hashers, repository, progress)
                if (result > VerificationResult.PASSED) {
                    if (result == VerificationResult.UNKNOWN) {
                        dependency.messages += Message(
                            "Unable to download checksums",
                            repository,
                            Severity.ERROR,
                        )
                    }
                    channel.truncate(0)
                    continue
                }
            }
            val target = cacheDirectory.getPath(
                dependency,
                "$nameWithoutExtension.$extension",
                hashers.find { it.algorithm == "sha1" }?.hash
                    ?: throw AmperDependencyResolutionException("sha1 must be present among hashers"),
            )
            Files.createDirectories(target.parent)
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: FileAlreadyExistsException) {
                if (repositories.any {
                        shouldOverwrite(object : HashersProvider {
                            private val lazyHashers: Collection<Hasher> by lazy { computeHash(target) }
                            override fun invoke(): Collection<Hasher> = lazyHashers
                        }, it, progress, verify)
                    }) {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            onFileDownloaded()
            dependency.messages += Message("Downloaded from $repository")
            return true
        }
        return false
    }

    fun interface HashersProvider : () -> Collection<Hasher>

    protected open fun shouldOverwrite(
        hashersProvider: HashersProvider,
        repository: String,
        progress: Progress,
        verify: Boolean
    ): Boolean = verify && verify(
        hashersProvider(),
        repository,
        progress,
    ) > VerificationResult.PASSED

    private fun verify(
        hashers: Collection<Hasher>,
        repository: String,
        progress: Progress,
        requestedLevel: ResolutionLevel = ResolutionLevel.NETWORK
    ): VerificationResult {
        // Let's first check hashes available on disk.
        val levelToHasher = setOf(ResolutionLevel.LOCAL, requestedLevel)
            .flatMap { level -> hashers.map { level to it } }
        for ((level, hasher) in levelToHasher) {
            val algorithm = hasher.algorithm
            val expectedHash = getOrDownloadExpectedHash(algorithm, repository, progress, level) ?: continue
            val actualHash = hasher.hash
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
        level: ResolutionLevel = ResolutionLevel.NETWORK
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
        val hashFile = getDependencyFile(dependency, nameWithoutExtension, "$extension.$algorithm").takeIf {
            it.isDownloaded()
                    || level == ResolutionLevel.NETWORK && it.download(listOf(repository), progress, verify = false)
        }
        val hashFromRepository = hashFile?.readText()?.toByteArray()
        if (hashFromRepository?.isNotEmpty() == true) {
            return hashFromRepository.toString(Charsets.UTF_8).sanitize()
        }
        return null
    }

    /**
     * Sometimes files with checksums have additional information, e.g., a path to a file.
     * We expect that at least the first word in a file is a hash.
     */
    private fun String.sanitize() = split("\\s".toRegex()).getOrNull(0)?.takeIf { it.isNotEmpty() }

    private fun getHashFromGradleCacheDirectory(algorithm: String) =
        if (cacheDirectory is GradleLocalRepository && algorithm == "sha1") {
            path?.parent?.name?.padStart(40, '0') // old Gradle compatibility
        } else {
            null
        }

    private fun download(writers: Collection<Writer>, repository: String, progress: Progress): Boolean {
        var exception: Exception? = null
        repeat(3) {
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
                when (val responseCode = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val contentLength = connection.contentLength
                        val size = Channels.newChannel(BufferedInputStream(connection.inputStream)).use { channel ->
                            channel.readTo(writers)
                        }
                        if (contentLength != -1 && size != contentLength) {
                            throw IOException(
                                "Content length doesn't match for $repository. Expected: $contentLength, actual: $size"
                            )
                        }
                        return true
                    }

                    HttpURLConnection.HTTP_NOT_FOUND -> return false
                    else -> throw IOException(
                        "Unexpected response code for $repository. " +
                                "Expected: ${HttpURLConnection.HTTP_OK}, actual: $responseCode"
                    )
                }
            } catch (e: Exception) {
                exception = e
            }
        }
        dependency.messages += Message(
            "Unable to reach $repository",
            exception.toString(),
            Severity.ERROR,
        )
        return false
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

    override fun isDownloaded(): Boolean {
        val path = path
        if (path?.exists() != true) {
            return false
        }
        if (nameWithoutExtension != "maven-metadata") {
            if (versionFile?.exists() != true) {
                return false
            }
            if (mavenMetadata.isDownloaded()) {
                if (versionFile?.readText() != snapshotVersion) {
                    return false
                }
            } else {
                return false
            }
        } else {
            return Files.getLastModifiedTime(path) > FileTime.from(ZonedDateTime.now().minusDays(1).toInstant())
        }
        return true
    }

    override fun getNamePart(repository: String, name: String, extension: String, progress: Progress): String {
        if (name != "maven-metadata" &&
            (mavenMetadata.isDownloaded() || mavenMetadata.download(listOf(repository), progress, verify = false))
        ) {
            snapshotVersion?.let { name.replace(dependency.version, it) }?.let { return "$it.$extension" }
        }
        return super.getNamePart(repository, name, extension, progress)
    }

    override fun shouldOverwrite(
        hashersProvider: HashersProvider,
        repository: String,
        progress: Progress,
        verify: Boolean,
    ): Boolean = nameWithoutExtension == "maven-metadata"
            || versionFile?.takeIf { it.exists() }?.readText() != snapshotVersion

    override fun onFileDownloaded() {
        if (nameWithoutExtension != "maven-metadata") {
            snapshotVersion?.let { versionFile?.writeText(it) }
        }
    }
}

internal fun getNameWithoutExtension(node: MavenDependency): String = "${node.module}-${node.version}"

private fun fileFromVariant(dependency: MavenDependency, name: String) =
    dependency.variant?.files?.singleOrNull { it.name == name }

fun interface Writer {
    fun write(data: ByteBuffer)
}

class Hasher(algorithm: String) {
    private val digest = MessageDigest.getInstance(algorithm)
    val algorithm: String = digest.algorithm
    val writer: Writer = Writer(digest::update)
    val hash: String by lazy { toHex(digest.digest()) }
}

private fun computeHash(path: Path): Collection<Hasher> {
    val hashers = createHashers()
    FileChannel.open(path, StandardOpenOption.READ).use { channel ->
        channel.readTo(hashers.map { it.writer })
    }
    return hashers
}

private fun createHashers() = listOf("sha512", "sha256", "sha1", "md5").map { Hasher(it) }

private fun ReadableByteChannel.readTo(writers: Collection<Writer>): Int {
    var size = 0
    val data = ByteBuffer.allocate(1024)
    while (read(data) != -1) {
        writers.forEach {
            data.flip()
            it.write(data)
        }
        size += data.position()
        data.clear()
    }
    return size
}

@OptIn(ExperimentalStdlibApi::class)
internal fun toHex(bytes: ByteArray) = bytes.toHexString()
