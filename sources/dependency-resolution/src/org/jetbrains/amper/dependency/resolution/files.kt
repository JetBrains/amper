/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.amper.concurrency.Hash
import org.jetbrains.amper.concurrency.Hasher
import org.jetbrains.amper.concurrency.Writer
import org.jetbrains.amper.concurrency.computeHash
import org.jetbrains.amper.concurrency.produceResultWithDoubleLock
import org.jetbrains.amper.concurrency.readTextWithRetry
import org.jetbrains.amper.concurrency.withRetry
import org.jetbrains.amper.concurrency.withRetryOnAccessDenied
import org.jetbrains.amper.dependency.resolution.metadata.json.module.File
import org.jetbrains.amper.dependency.resolution.metadata.xml.parseMetadata
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient.Redirect
import java.net.http.HttpClient.Version
import java.net.http.HttpRequest
import java.net.http.HttpRequest.Builder
import java.net.http.HttpResponse.BodyHandlers
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Base64
import javax.net.ssl.SSLContext
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText


internal val logger = LoggerFactory.getLogger("files.kt")

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
     * Returns a path to a temp directory for a particular dependency.
     *
     * The file is downloaded to a temp directory to be later moved to a permanent one provided by [getPath].
     * Both paths should preferably be on the same files drive to allow atomic move.
     */
    fun getTempDir(dependency: MavenDependency): Path

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
        return location.naiveSearchDepth2 { it.name == name }.firstOrNull()
    }

    /**
     * A very basic and stupid implementation of DFWalk for a file tree, since [Files.walk]
     * sometimes does not notice directory changes.
     * // TODO Need to redesign; See: [issue](https://youtrack.jetbrains.com/issue/AMPER-671/Redesign-resolution-files-downloading)
     */
    private fun Path.naiveSearchDepth2(shouldDescend: Boolean = true, filterBlock: (Path) -> Boolean): List<Path> =
        buildList {
            Files.list(this@naiveSearchDepth2)
                .use {
                    it.toList()
                    .filter { it.exists() }
                    .forEach {
                        if (it.isDirectory() && shouldDescend) addAll(it.naiveSearchDepth2(false, filterBlock))
                        if (filterBlock(it)) add(it)
                    }
                }
        }

    override fun getTempDir(dependency: MavenDependency): Path = getLocation(dependency)

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

    override fun getTempDir(dependency: MavenDependency): Path = repository.resolve(getLocation(dependency))

    override fun getPath(dependency: MavenDependency, name: String, sha1: String): Path = guessPath(dependency, name)

    private fun getLocation(dependency: MavenDependency) =
        repository.resolve(
            "${dependency.group.split('.').joinToString("/")}/${dependency.module}/${dependency.version}"
        )
}

internal fun getDependencyFile(dependency: MavenDependency, file: File) = getDependencyFile(dependency,
    file.url.substringBeforeLast('.'), file.name.substringAfterLast('.'))

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
    val kmpSourceSet: String? = null,
    private val fileCache: FileCache = dependency.settings.fileCache,
) {

    @Volatile
    private var cacheDirectory: LocalRepository? = null
    private val mutex = Mutex()

    internal val fileName = "$nameWithoutExtension.$extension"

    private suspend fun getCacheDirectory(): LocalRepository {
        if (cacheDirectory == null) {
            mutex.withLock {
                if (cacheDirectory == null) {
                    cacheDirectory = fileCache.localRepositories.find {
                        it.guessPath(dependency, fileName)?.exists() == true
                    } ?: fileCache.fallbackLocalRepository
                }
            }
        }
        return cacheDirectory!!
    }

    @Volatile
    private var path: Path? = null

    @Volatile
    private var guessedPath: Path? = null

    suspend fun getPath(): Path? = path ?: guessedPath ?: withContext(Dispatchers.IO) {
        getCacheDirectory().guessPath(dependency, fileName)?.also { guessedPath = it }
    }

    override fun toString(): String = runBlocking { getPath()?.toString() }
        ?: "[missing path]/$fileName"

    open suspend fun isDownloaded(): Boolean = withContext(Dispatchers.IO) { getPath()?.exists() == true }

    suspend fun hasMatchingChecksum(level: ResolutionLevel, context: Context): Boolean = withContext(Dispatchers.IO) {
        hasMatchingChecksum(level, context.settings.repositories, context.settings.progress, context.resolutionCache)
    }

    private suspend fun hasMatchingChecksum(
        level: ResolutionLevel,
        repositories: List<Repository>,
        progress: Progress,
        cache: Cache,
    ): Boolean {
        val path = getPath() ?: return false
        val hashers = path.computeHash()
        for (repository in repositories) {
            val result = verify(hashers, repository, progress, cache, requestedLevel = level)
            return when (result) {
                VerificationResult.PASSED -> true
                VerificationResult.FAILED -> false
                else -> continue
            }
        }
        return level < ResolutionLevel.NETWORK
    }

    private suspend fun hasMatchingChecksum(expectedHash: Hash): Boolean {
        val path = getPath() ?: return false
        val actualHash = computeHash(path, expectedHash.algorithm)
        val result = checkHash(actualHash, expectedHash)
        return result == VerificationResult.PASSED
    }

    suspend fun readText(): String = getPath()?.readTextWithRetry()
        ?: throw AmperDependencyResolutionException("Path doesn't exist, download the file first")

    suspend fun download(context: Context): Boolean =
        download(context.settings.repositories.ensureFirst(dependency.repository) , context.settings.progress, context.resolutionCache, true)

    private suspend fun downloadUnderFileLock(
        repositories: List<Repository>,
        progress: Progress,
        cache: Cache,
        expectedHash: Hash?,
    ): Path? {
        return withContext(Dispatchers.IO) {
            produceResultWithDoubleLock(
                tempDir = getTempDir(),
                fileName,
                getAlreadyProducedResult = {
                    getPath()?.takeIf { isDownloadedWithVerification(expectedHash) }
                }
            ) { tempFilePath, fileChannel ->
                try {
                    downloadAndVerifyHash(fileChannel, tempFilePath, repositories, progress, cache, expectedHash)
                } catch (e: IOException) {
                    dependency.messages.asMutable() += Message(
                        "Unable to save downloaded file",
                        e.toString(),
                        Severity.ERROR,
                        e,
                    )
                    return@produceResultWithDoubleLock null
                }
            }
        }
    }


    internal suspend fun DependencyFile.getTempDir() = getCacheDirectory().getTempDir(dependency)

    private suspend fun DependencyFile.isDownloadedWithVerification(expectedHash: Hash?) =
        isDownloaded() && (expectedHash == null || hasMatchingChecksum(expectedHash))

    internal suspend fun download(
        repositories: List<Repository>,
        progress: Progress,
        cache: Cache,
        verify: Boolean,
    ): Boolean {
        val path = if (verify) {
            val expectedHash = getOrDownloadExpectedHash(repositories, progress, cache)
            if (expectedHash == null) {
                dependency.messages.asMutable() += Message(
                    "Unable to download checksums of file $fileName for dependency $dependency",
                    repositories.joinToString(),
                    Severity.ERROR,
                )
                return false
            }
            if (isDownloadedWithVerification(expectedHash)) {
                return true
            }

            val optimizedRepositories = repositories.ensureFirst(dependency.repository) // first, try the same repo hash was downloaded from
            downloadUnderFileLock(optimizedRepositories, progress, cache, expectedHash)
        } else {
            if (isDownloadedWithVerification(null)) {
                return true
            }

            downloadUnderFileLock(repositories, progress, cache, null)
        }

        if (path == null && verify) {
            dependency.messages.asMutable() += Message(
                "Unable to download file $fileName for dependency $dependency",
                repositories.joinToString(),
                Severity.ERROR,
            )
        }

        return path != null
    }

    private fun List<Repository>.ensureFirst(repository: Repository?) =
        repository?.let {
            if (this.isEmpty() || this[0].url == repository.url || !this.map{ it.url }.contains(repository.url))
                this
            else
                buildList {
                    add(repository)
                    addAll(this - repository)
                }
        } ?: this

    @Suppress("BlockingMethodInNonBlockingContext") // the whole method is called with Dispatchers.IO
    private suspend fun downloadAndVerifyHash(
        channel: FileChannel,
        temp: Path,
        repositories: List<Repository>,
        progress: Progress,
        cache: Cache,
        expectedHash: Hash?,      // this should be passed for all files being downloaded, except when hash files are downloaded itself
    ): Path? {
        for (repository in repositories) {
            val hasher = expectedHash?.let { Hasher(it.algorithm) }
            val hashers = buildList {
                hasher?.let { add(it) }                                        // for calculation of the given hash on download
                if (hasher?.algorithm != "sha1") add(Hasher("sha1"))  // for calculation of `sha1` hash on download additionally
            }
            val writers = hashers.map { it.writer } + Writer(channel::write)
            if (!download(writers, repository, progress, cache)) {
                channel.truncate(0)
                continue
            }
            if (hasher != null) {
                val result = checkHash(hasher, expectedHash)
                if (result > VerificationResult.PASSED) {
                    channel.truncate(0)
                    continue
                }
            }

            val sha1 = hashers.find { it.algorithm == "sha1" }?.hash
                ?: throw AmperDependencyResolutionException("sha1 must be present among hashers")

            val target = getCacheDirectory().getPath(dependency,fileName, sha1)

            target.parent.createDirectories()
            try {
                temp.moveTo(target)
            } catch (e: FileAlreadyExistsException) {
                logger.debug("### $target already exists")
                if (hasher != null
                    && shouldOverwrite(cache, hasher, computeHash(target, hasher.algorithm)))
                {
                    try {
                        logger.debug("### $target will be replaced with new one")
                        withRetryOnAccessDenied {
                            temp.moveTo(target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                        }
                    } catch (t: Throwable) {
                        logger.debug("### $target was not replaced with new one", t)
                        throw t
                    }
                } else {
                    temp.deleteIfExists()
                }
            }

            logger.trace(
                "Downloaded file {} for the dependency {}:{}:{} was stored into {}",
                target.name,
                dependency.group,
                dependency.module,
                dependency.version,
                target.parent
            )
            onFileDownloaded(target, repository)

            dependency.messages.asMutable() += Message("Downloaded from $repository")

            return target
        }

        temp.deleteIfExists()

        return null
    }

    protected open suspend fun shouldOverwrite(
        cache: Cache,
        expectedHasher: Hasher?,
        actualHash : Hasher
    ): Boolean = expectedHasher != null && checkHash(actualHash, expectedHasher) > VerificationResult.PASSED

    private suspend fun verify(
        hashers: Collection<Hasher>,
        repository: Repository,
        progress: Progress,
        cache: Cache,
        requestedLevel: ResolutionLevel = ResolutionLevel.NETWORK
    ): VerificationResult {
        // Let's first check hashes available on disk.
        val levelToHasher = setOf(ResolutionLevel.LOCAL, requestedLevel)
            .flatMap { level -> hashers.filterWellKnownBrokenHashes(repository.url).map { level to it } }
        for ((level, hasher) in levelToHasher) {
            val algorithm = hasher.algorithm
            val expectedHash = getOrDownloadExpectedHash(algorithm, repository, progress, cache, level)
                ?: continue
            val actualHash = hasher.hash
            if (!expectedHash.equals(actualHash, true)) {
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

    /**
     * @return hash and repository where it was downloaded from (the latter is null if hash was found locally)
     */
    private suspend fun getOrDownloadExpectedHash(
        repositories: List<Repository>,
        progress: Progress,
        cache: Cache,
        requestedLevel: ResolutionLevel = ResolutionLevel.NETWORK
    ): Hash? {
        val hashers = createHashers()

        // First, try to find cache locally
        for (hasher in hashers) {
            val algorithm = hasher.algorithm
            val expectedHash = getOrDownloadExpectedHash(algorithm, null, progress, cache, ResolutionLevel.LOCAL)
            if (expectedHash != null) {
                return object : Hash {
                    override val hash: String = expectedHash
                    override val algorithm = algorithm
                }
            }
        }

        // Then, try to download hash from given repositories
        if (requestedLevel != ResolutionLevel.LOCAL) {
            for (hasher in hashers) {
                val algorithm = hasher.algorithm
                for (repository in repositories) {
                    if (!hasher.isWellKnownBrokenHashIn(repository.url)) {
                        val expectedHash = getOrDownloadExpectedHash(algorithm, repository, progress, cache, requestedLevel)
                        if (expectedHash != null) {
                            return object : Hash {
                                override val hash: String = expectedHash
                                override val algorithm = algorithm
                            }
                        }
                    }
                }
            }
        }

        return null
    }

    private fun checkHash(
        hasher: Hasher,
        expectedHash: Hash,
    ): VerificationResult {
        // Let's first check hashes available on disk.
        val algorithm = hasher.algorithm
        val actualHash = hasher.hash
        if (expectedHash.algorithm != algorithm) {
            throw IllegalStateException("Expected hash type is ${expectedHash.hash}, but $algorithm was calculated")
        } else if (!expectedHash.hash.equals(actualHash,true)) {
            dependency.messages.asMutable() += Message(
                "Hashes don't match for $algorithm",
                "expected: ${expectedHash.hash}, actual: $actualHash",
                Severity.ERROR,
            )
            return VerificationResult.FAILED
        } else {
            return VerificationResult.PASSED
        }
    }

    private fun Collection<Hasher>.filterWellKnownBrokenHashes(repository: String) =
        filterNot { it.isWellKnownBrokenHashIn(repository) }

    private fun Hasher.isWellKnownBrokenHashIn(repository: String) : Boolean {
        if (repository == "https://plugins.gradle.org/m2/") {
            return algorithm == "sha512" || algorithm == "sha256"
        }

        return false
    }

    enum class VerificationResult { PASSED, UNKNOWN, FAILED }

    internal suspend fun getOrDownloadExpectedHash(
        algorithm: String,
        repository: Repository?,
        progress: Progress,
        cache: Cache,
        level: ResolutionLevel = ResolutionLevel.NETWORK
    ): String? {
        val hashFromVariant = when (algorithm) {
            "sha512" -> fileFromVariant(dependency, fileName)?.sha512?.fixOldGradleHash(128)
            "sha256" -> fileFromVariant(dependency, fileName)?.sha256?.fixOldGradleHash(64)
            "sha1" -> fileFromVariant(dependency, fileName)?.sha1?.fixOldGradleHash(40)
            "md5" -> fileFromVariant(dependency, fileName)?.md5?.fixOldGradleHash(32)
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
                    && repository != null && it.download(listOf(repository), progress, cache, verify = false)
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
        repository: Repository,
        progress: Progress,
        cache: Cache
    ): Boolean {
        logger.trace("Trying to download $nameWithoutExtension from $repository")

        val client = cache.computeIfAbsent(httpClientKey) {
            java.net.http.HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .followRedirects(Redirect.NORMAL)
                .sslContext(SSLContext.getDefault())
                .connectTimeout(Duration.ofSeconds(20))
//                .proxy(ProxySelector.of(InetSocketAddress("proxy.example.com", 80)))
//                .authenticator(Authenticator.getDefault())
                .build()
        }
        val name = getNamePart(repository, nameWithoutExtension, extension, progress, cache)
        val url = repository.url.trimEnd('/') +
                "/${dependency.group.replace('.', '/')}" +
                "/${dependency.module}" +
                "/${dependency.version}" +
                "/$name"
        try {
            // todo (AB) : Use exponential retry here
            return withRetry(retryCount = 3,
                retryOnException = { e ->
                    e is IOException
                }
            ) {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .withBasicAuth(repository)
                    .timeout(Duration.ofMinutes(2))
                    .GET()
                    .build()

                val future = client.sendAsync(request, BodyHandlers.ofInputStream())
                val response = future.await()

                when (val status = response.statusCode()) {
                    200 -> {
                        val expectedSize = fileFromVariant(dependency, name)?.size
                            ?: response.headers().firstValueAsLong(HttpHeaders.ContentLength)
                                .takeIf{ it.isPresent && it.asLong != -1L }?.asLong
                        val size = response.body().toByteReadChannel().readTo(writers)
                        if (expectedSize != null && size != expectedSize) {
                            throw IOException(
                                "Content length doesn't match for $url. Expected: $expectedSize, actual: $size"
                            )
                        }

                        if (logger.isDebugEnabled) {
                            logger.debug("Downloaded {} for the dependency {}:{}:{}", url, dependency.group, dependency.module, dependency.version)
                        } else if (extension.substringAfterLast(".") !in hashAlgorithms) {
                            // Reports downloaded dependency to INFO (visible to user by default)
                            logger.info("Downloaded $url")
                        }

                        true
                    }

                    404 -> {
                        logger.debug("Not found URL: $url")
                        false
                    }
                    else -> throw IOException(
                        "Unexpected response code for $url. " +
                                "Expected: ${HttpStatusCode.OK.value}, actual: $status"
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("$repository: Failed to download $url", e)
            dependency.messages.asMutable() += Message(
                "Unable to reach $url (${client.hashCode()})",
                e.toString(),
                Severity.ERROR,
                e,
            )
        }
        return false
    }

    private fun Builder.withBasicAuth(repository: Repository): Builder = also {
        if (repository.userName != null && repository.password != null) {
            header("Authorization", getBasicAuthenticationHeader(repository.userName, repository.password))
        }
    }

    private fun getBasicAuthenticationHeader(username: String, password: String): String {
        val valueToEncode = "$username:$password"
        return "Basic " + Base64.getEncoder().encodeToString(valueToEncode.toByteArray())
    }

    protected open suspend fun getNamePart(
        repository: Repository,
        name: String,
        extension: String,
        progress: Progress,
        cache: Cache
    ) =
        "$name.$extension"

    internal open suspend fun onFileDownloaded(target: Path, repository: Repository? = null) {
        this.path = target
        this.dependency.repository = repository
    }
}

class SnapshotDependencyFile(
    dependency: MavenDependency,
    name: String,
    extension: String,
    fileCache: FileCache = dependency.settings.fileCache,
) : DependencyFile(dependency, name, extension, fileCache = fileCache) {

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
        repository: Repository,
        name: String,
        extension: String,
        progress: Progress,
        cache: Cache
    ): String {
        if (name != "maven-metadata" &&
            (mavenMetadata.isDownloaded()
                    || mavenMetadata.download(listOf(repository), progress, cache, verify = false))
        ) {
            getSnapshotVersion()
                ?.let { name.replace(dependency.version, it) }
                ?.let {
                    return "$it.$extension"
                }
        }
        return super.getNamePart(repository, name, extension, progress, cache)
    }

    override suspend fun shouldOverwrite(cache: Cache, expectedHasher: Hasher?, actualHash: Hasher): Boolean =
        nameWithoutExtension == "maven-metadata"
                || getVersionFile()?.takeIf { it.exists() }?.readText() != getSnapshotVersion()

    override suspend fun onFileDownloaded(target: Path, repository: Repository?) {
        super.onFileDownloaded(target, repository)
        if (nameWithoutExtension != "maven-metadata") {
            getSnapshotVersion()?.let { getVersionFile()?.writeText(it) }
        }
    }
}

internal fun getNameWithoutExtension(node: MavenDependency): String = "${node.module}-${node.version}"

private fun fileFromVariant(dependency: MavenDependency, name: String) =
    dependency.variants.flatMap { it.files }.singleOrNull { it.name == name }

internal suspend fun Path.computeHash(): Collection<Hasher> = computeHash(this) { createHashers() }

private val hashAlgorithms = listOf("sha512", "sha256", "sha1", "md5")

private fun createHashers() = hashAlgorithms.map { Hasher(it) }

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

private val httpClientKey = Key<java.net.http.HttpClient>("httpClient")