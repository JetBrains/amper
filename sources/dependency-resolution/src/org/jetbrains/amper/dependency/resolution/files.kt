/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.amper.concurrency.withLock
import org.jetbrains.amper.dependency.resolution.metadata.xml.parseMetadata
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.channels.ReadableByteChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.time.ZonedDateTime
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText


private val logger = LoggerFactory.getLogger("files.kt")

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
        return Files.walk(location, 2).use { stream ->
            stream.filter { it.name == name }.findAny().orElse(null)
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
class MavenLocalRepository(val repository: Path) : LocalRepository {

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
    private val fileCache: FileCache = dependency.fileCache,
) {

    @Volatile
    private var cacheDirectory: LocalRepository? = null
    private val mutex = Mutex()

    private suspend fun getCacheDirectory(): LocalRepository {
        if (cacheDirectory == null) {
            mutex.withLock {
                if (cacheDirectory == null) {
                    cacheDirectory = fileCache.localRepositories.find {
                        it.guessPath(dependency, "$nameWithoutExtension.$extension")?.exists() == true
                    } ?: fileCache.fallbackLocalRepository
                }
            }
        }
        return cacheDirectory!!
    }

    @Volatile
    private var path: Path? = null

    suspend fun getPath(): Path? = path ?: withContext(Dispatchers.IO) {
        getCacheDirectory().guessPath(dependency, "$nameWithoutExtension.$extension")
    }

    override fun toString(): String = runBlocking { getPath()?.toString() }
        ?: "[missing path]/$nameWithoutExtension.$extension"

    open suspend fun isDownloaded(): Boolean = withContext(Dispatchers.IO) { getPath()?.exists() == true }

    suspend fun hasMatchingChecksum(level: ResolutionLevel, context: Context): Boolean = withContext(Dispatchers.IO) {
        hasMatchingChecksum(level, context.settings.repositories, context.settings.progress, context.resolutionCache)
    }

    private suspend fun hasMatchingChecksum(
        level: ResolutionLevel,
        repositories: List<String>,
        progress: Progress,
        cache: Cache,
        isLockAcquired: Boolean = false,
    ): Boolean {
        val path = getPath() ?: return false
        val hashers = computeHash(path)
        for (repository in repositories) {
            val result = verify(hashers, repository, progress, cache, isLockAcquired = isLockAcquired, requestedLevel = level)
            return when (result) {
                VerificationResult.PASSED -> true
                VerificationResult.FAILED -> false
                else -> continue
            }
        }
        return level < ResolutionLevel.NETWORK
    }

    suspend fun readText(): String = withContext(Dispatchers.IO) { getPath()?.readText() }
        ?: throw AmperDependencyResolutionException("Path doesn't exist, download the file first")


    suspend fun download(context: Context): Boolean =
        download(context.settings.repositories, context.settings.progress, context.resolutionCache)

    protected suspend fun download(
        repositories: List<String>,
        progress: Progress,
        cache: Cache,
        isLockAcquired: Boolean = false,
        verify: Boolean = true,
    ): Boolean = withContext(Dispatchers.IO) {
        val temp = getTempFilePath()
        return@withContext if (!isLockAcquired) {
            temp.withLock {
                downloadUnderFileLock(repositories, progress, cache, verify)
            }
        } else
            downloadUnderFileLock(repositories, progress, cache, verify)
    }

    private suspend fun downloadUnderFileLock(
        repositories: List<String>,
        progress: Progress,
        cache: Cache,
        verify: Boolean = true,
    ): Boolean {
        val temp = getTempFilePath()
        try {
            temp.parent.createDirectories()
            try {
                return downloadUnderFileLock(
                    temp,
                    repositories,
                    progress,
                    cache,
                    verify,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE_NEW
                )
            } catch (e: FileAlreadyExistsException) {
                return waitForFileLockReleaseAndCheckResult(temp, repositories, progress, cache, verify)
            } catch (e: OverlappingFileLockException) {
                return waitForFileLockReleaseAndCheckResult(temp, repositories, progress, cache, verify)
            }
        } catch (e: IOException) {
            dependency.messages.asMutable() += Message(
                "Unable to save downloaded file",
                e.toString(),
                Severity.ERROR,
                e,
            )
            return false
        } finally {
            temp.deleteIfExists()
        }
    }

    private suspend fun DependencyFile.getTempFilePath() =
        getCacheDirectory().getTempPath(dependency, "$nameWithoutExtension.$extension")

    private suspend fun waitForFileLockReleaseAndCheckResult(
        temp: Path,
        repositories: List<String>,
        progress: Progress,
        cache: Cache,
        verify: Boolean
    ): Boolean {
        // Someone's already downloading the file. Let's wait for it and then check the result.
        var wait = 10L
        while (true) {
            return try {
                // Another process has created a file, but this process was faster to acquire a lock.
                downloadUnderFileLock(temp, repositories, progress, cache, verify = verify, StandardOpenOption.WRITE)
            } catch (e: OverlappingFileLockException) {
                // The file is still being downloaded.
                delay(wait)
                wait = (wait * 2).coerceAtMost(1000)
                continue
            } catch (e: NoSuchFileException) {
                // The file has been released and moved.
                isDownloadWithVerification(verify, repositories, progress, cache)
            }
        }
    }

    private suspend fun DependencyFile.isDownloadWithVerification(
        verify: Boolean,
        repositories: List<String>,
        progress: Progress,
        cache: Cache
    ) = isDownloaded() && (!verify
            || hasMatchingChecksum(ResolutionLevel.NETWORK, repositories, progress, cache, isLockAcquired = true))

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun downloadUnderFileLock(
        temp: Path,
        repositories: List<String>,
        progress: Progress,
        cache: Cache,
        verify: Boolean,
        vararg options: OpenOption,
    ) =
        FileChannel.open(temp, *options)
            .use { fileChannel ->
                fileChannel.lock().use {
                    if (isDownloadWithVerification(verify, repositories, progress, cache)) {
                        true
                    } else {
                        downloadUnderFileLock(fileChannel, temp, repositories, progress, cache, verify)
                    }
                }
            }

    @Suppress("BlockingMethodInNonBlockingContext") // the whole method is called with Dispatchers.IO
    private suspend fun downloadUnderFileLock(
        channel: FileChannel,
        temp: Path,
        repositories: List<String>,
        progress: Progress,
        cache: Cache,
        verify: Boolean,
    ): Boolean {
        for (repository in repositories) {
            logger.trace("download: Trying to download $nameWithoutExtension from $repository")
            val hashers = createHashers().filter { verify || it.algorithm == "sha1" }.filterWellKnownBrokenHashes(repository)
            val writers = hashers.map { it.writer } + Writer(channel::write)
            if (!download(writers, repository, progress, cache)) {
                channel.truncate(0)
                continue
            }
            if (verify) {
                val result = verify(hashers, repository, progress, cache, isLockAcquired = true)
                if (result > VerificationResult.PASSED) {
                    if (result == VerificationResult.UNKNOWN) {
                        dependency.messages.asMutable() += Message(
                            "Unable to download checksums for $dependency",
                            repository,
                            Severity.ERROR,
                        )
                    }
                    channel.truncate(0)
                    continue
                }
            }
            val target = getCacheDirectory().getPath(
                dependency,
                "$nameWithoutExtension.$extension",
                hashers.find { it.algorithm == "sha1" }?.hash
                    ?: throw AmperDependencyResolutionException("sha1 must be present among hashers"),
            )
            target.parent.createDirectories()
            try {
                temp.moveTo(target, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: FileAlreadyExistsException) {
                if (repositories.any {
                        shouldOverwrite(object : HashersProvider {
                            private val lazyHashers: Collection<Hasher> by lazy { computeHash(target) }
                            override fun invoke(): Collection<Hasher> = lazyHashers
                        }, it, progress, cache, verify)
                    }) {
                    temp.moveTo(target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            onFileDownloaded(target)
            logger.info(
                "Downloaded {}:{}:{} from {} and stored to {} ",
                dependency.group,
                dependency.module,
                dependency.version,
                repository,
                target
            )
            dependency.messages.asMutable() += Message("Downloaded from $repository")
            return true
        }
        return false
    }

    fun interface HashersProvider : () -> Collection<Hasher>

    protected open suspend fun shouldOverwrite(
        hashersProvider: HashersProvider,
        repository: String,
        progress: Progress,
        cache: Cache,
        verify: Boolean
    ): Boolean = verify && verify(
        hashersProvider(),
        repository,
        progress,
        cache,
        isLockAcquired = true
    ) > VerificationResult.PASSED

    private suspend fun verify(
        hashers: Collection<Hasher>,
        repository: String,
        progress: Progress,
        cache: Cache,
        isLockAcquired: Boolean = false,
        requestedLevel: ResolutionLevel = ResolutionLevel.NETWORK
    ): VerificationResult {
        // Let's first check hashes available on disk.
        val levelToHasher = setOf(ResolutionLevel.LOCAL, requestedLevel)
            .flatMap { level -> hashers.filterWellKnownBrokenHashes(repository).map { level to it } }
        for ((level, hasher) in levelToHasher) {
            val algorithm = hasher.algorithm
            val expectedHash = getOrDownloadExpectedHash(algorithm, repository, progress, cache, isLockAcquired = isLockAcquired, level)
                ?: continue
            val actualHash = hasher.hash
            if (expectedHash != actualHash) {
                dependency.messages.asMutable() += Message(
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

    private fun Collection<Hasher>.filterWellKnownBrokenHashes(repository: String) =
        when {
            repository == "https://plugins.gradle.org/m2/" -> filter { it.algorithm != "sha512" && it.algorithm != "sha256" }
            else -> this
        }

    enum class VerificationResult { PASSED, UNKNOWN, FAILED }

    private suspend fun getOrDownloadExpectedHash(
        algorithm: String,
        repository: String,
        progress: Progress,
        cache: Cache,
        isLockAcquired: Boolean = false,
        level: ResolutionLevel = ResolutionLevel.NETWORK
    ): String? {
        val name = "$nameWithoutExtension.$extension"
        val hashFromVariant = when (algorithm) {
            "sha512" -> fileFromVariant(dependency, name)?.sha512?.fixOldGradleHash(128)
            "sha256" -> fileFromVariant(dependency, name)?.sha256?.fixOldGradleHash(64)
            "sha1" -> fileFromVariant(dependency, name)?.sha1?.fixOldGradleHash(40)
            "md5" -> fileFromVariant(dependency, name)?.md5?.fixOldGradleHash(32)
            else -> null
        }
        if (hashFromVariant != null) {
            return hashFromVariant
        }
        val hashFromGradle = getHashFromGradleCacheDirectory(algorithm)
        if (hashFromGradle != null) {
            return hashFromGradle
        }
        val hashFile = getDependencyFile(dependency, nameWithoutExtension, "$extension.$algorithm").takeIf {
            it.isDownloaded()
                    || level == ResolutionLevel.NETWORK
                    && it.download(listOf(repository), progress, cache, isLockAcquired = isLockAcquired, verify = false)
        }
        val hashFromRepository = hashFile?.readText()
        if (hashFromRepository != null) {
            return hashFromRepository.sanitize()
        }
        return null
    }

    /**
     * Sometimes files with checksums have additional information, e.g., a path to a file.
     * We expect that at least the first word in a file is a hash.
     */
    private fun String.sanitize() = split("\\s".toRegex()).getOrNull(0)?.takeIf { it.isNotEmpty() }

    private suspend fun getHashFromGradleCacheDirectory(algorithm: String) =
        if (getCacheDirectory() is GradleLocalRepository && algorithm == "sha1") {
            getPath()?.parent?.name?.fixOldGradleHash(40)
        } else {
            null
        }

    private fun String.fixOldGradleHash(hashLength: Int) = padStart(hashLength, '0')

    private suspend fun download(
        writers: Collection<Writer>,
        repository: String,
        progress: Progress,
        cache: Cache,
    ): Boolean {
        val client = cache.computeIfAbsent(httpClientKey) {
            HttpClient(CIO) {
                engine {
                    requestTimeout = 60000
                }
                install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries = 3)
                    retryOnException(maxRetries = 3, retryOnTimeout = true)
                    exponentialDelay()
                }
                install(HttpTimeout) {
                    connectTimeoutMillis = 10000
                    requestTimeoutMillis = 60000
                    socketTimeoutMillis = 10000
                }
            }
        }
        val name = getNamePart(repository, nameWithoutExtension, extension, progress, cache)
        val url = repository.trimEnd('/') +
                "/${dependency.group.replace('.', '/')}" +
                "/${dependency.module}" +
                "/${dependency.version}" +
                "/$name"
        try {
            val response = client.get(url)
            when (val status = response.status) {
                HttpStatusCode.OK -> {
                    val expectedSize = fileFromVariant(dependency, name)?.size
                        ?: response.contentLength().takeIf { it != -1L }
                    val size = response.bodyAsChannel().readTo(writers)
                    if (expectedSize != null && size != expectedSize) {
                        throw IOException(
                            "Content length doesn't match for $url. Expected: $expectedSize, actual: $size"
                        )
                    }
                    return true
                }

                HttpStatusCode.NotFound -> return false
                else -> throw IOException(
                    "Unexpected response code for $url. " +
                            "Expected: ${HttpStatusCode.OK.value}, actual: $status"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("$repository: Failed to download", e)
            dependency.messages.asMutable() += Message(
                "Unable to reach $url",
                e.toString(),
                Severity.ERROR,
                e,
            )
        }
        return false
    }

    protected open suspend fun getNamePart(
        repository: String,
        name: String,
        extension: String,
        progress: Progress,
        cache: Cache
    ) =
        "$name.$extension"

    protected open suspend fun onFileDownloaded(target: Path) {
        path = target
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

    private suspend fun getVersionFile() = mavenMetadata.getPath()?.parent?.resolve("$extension.version")

    @Volatile
    private var snapshotVersion: String? = null
    private var mutex = Mutex()

    private suspend fun getSnapshotVersion(): String? {
        if (snapshotVersion == null) {
            mutex.withLock {
                if (snapshotVersion == null) {
                    val metadata = mavenMetadata.readText().parseMetadata()
                    snapshotVersion = metadata.versioning.snapshotVersions.snapshotVersions.find {
                        it.extension == extension.substringBefore('.') // pom.sha512 -> pom
                    }?.value ?: ""
                }
            }
        }
        return snapshotVersion.takeIf { it?.isNotEmpty() == true }
    }

    override suspend fun isDownloaded(): Boolean = withContext(Dispatchers.IO) { isSnapshotDownloaded() }

    private suspend fun isSnapshotDownloaded(): Boolean {
        val path = getPath()
        if (path?.exists() != true) {
            return false
        }
        if (nameWithoutExtension != "maven-metadata") {
            val versionFile = getVersionFile()
            if (versionFile?.exists() != true) {
                return false
            }
            if (mavenMetadata.isDownloaded()) {
                if (versionFile.readText() != getSnapshotVersion()) {
                    return false
                }
            } else {
                return false
            }
        } else {
            return path.getLastModifiedTime() > FileTime.from(ZonedDateTime.now().minusDays(1).toInstant())
        }
        return true
    }

    override suspend fun getNamePart(
        repository: String,
        name: String,
        extension: String,
        progress: Progress,
        cache: Cache
    ): String {
        if (name != "maven-metadata" &&
            (mavenMetadata.isDownloaded()
                    || mavenMetadata.download(listOf(repository), progress, cache, isLockAcquired = true, verify = false))
        ) {
            getSnapshotVersion()
                ?.let { name.replace(dependency.version, it) }
                ?.let {
                    return "$it.$extension"
                }
        }
        return super.getNamePart(repository, name, extension, progress, cache)
    }

    override suspend fun shouldOverwrite(
        hashersProvider: HashersProvider,
        repository: String,
        progress: Progress,
        cache: Cache,
        verify: Boolean,
    ): Boolean = nameWithoutExtension == "maven-metadata"
            || getVersionFile()?.takeIf { it.exists() }?.readText() != getSnapshotVersion()

    override suspend fun onFileDownloaded(target: Path) {
        super.onFileDownloaded(target)
        if (nameWithoutExtension != "maven-metadata") {
            getSnapshotVersion()?.let { getVersionFile()?.writeText(it) }
        }
    }
}

internal fun getNameWithoutExtension(node: MavenDependency): String = "${node.module}-${node.version}"

private fun fileFromVariant(dependency: MavenDependency, name: String) =
    dependency.variants.flatMap { it.files }.singleOrNull { it.name == name }

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

private fun ReadableByteChannel.readTo(writers: Collection<Writer>): Long {
    var size = 0L
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

private suspend fun ByteReadChannel.readTo(writers: Collection<Writer>): Long {
    var size = 0L
    val data = ByteBuffer.allocate(1024)
    while (readAvailable(data) != -1) {
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

private val httpClientKey = Key<HttpClient>("httpClient")
