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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.amper.concurrency.Hash
import org.jetbrains.amper.concurrency.Hasher
import org.jetbrains.amper.concurrency.Writer
import org.jetbrains.amper.concurrency.computeHash
import org.jetbrains.amper.concurrency.copyFrom
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
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.SSLContext
import kotlin.io.path.Path
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

    /**
     * Returns a path to a file on disk.
     * It can't return `null` as all necessary information must be available at a call site.
     *
     * A SHA1 hash is used by Gradle as a part of a path.
     */
    suspend fun getPath(dependency: MavenDependency, name: String, sha1: suspend () -> String): Path = getPath(dependency, name, sha1())
}

/**
 * Defines a `.gradle` directory structure.
 * It accepts a path to the `files-2.1` directory or defaults to `~/.gradle/caches/modules-2/files-2.1`.
 */
class GradleLocalRepository(internal val filesPath: Path) : LocalRepository {

    constructor() : this(getRootFromUserHome())

    companion object {
        private fun getRootFromUserHome() =
            Path(
                System.getenv("GRADLE_USER_HOME") ?: System.getProperty("user.home"),
                ".gradle/caches/modules-2/files-2.1"
            )
    }

    override fun toString(): String = "[Gradle] $filesPath"

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
        filesPath.resolve("${dependency.group}/${dependency.module}/${dependency.version}")
}

/**
 * Defines an `.m2` directory structure.
 *
 * It accepts a path to the [repository] directory or discovers the location using maven conventions.
 */
class MavenLocalRepository(val repository: Path) : LocalRepository {

    constructor() : this(findPath())

    companion object {
        private fun findPath() = LocalM2RepositoryFinder.findPath()
    }

    override fun toString(): String = "[Maven] $repository"

    override fun guessPath(dependency: MavenDependency, name: String): Path =
        repository.resolve(getLocation(dependency)).resolve(name)

    override fun getTempDir(dependency: MavenDependency): Path = repository.resolve(getLocation(dependency))

    override fun getPath(dependency: MavenDependency, name: String, sha1: String): Path = guessPath(dependency, name)

    /**
     * Parameter sha1 is ignored and not calculated
     */
    override suspend fun getPath(dependency: MavenDependency, name: String, sha1: suspend () -> String): Path = guessPath(dependency, name)

    private fun getLocation(dependency: MavenDependency) =
        repository.resolve(
            "${dependency.group.split('.').joinToString("/")}/${dependency.module}/${dependency.version}"
        )
}

internal fun getDependencyFile(dependency: MavenDependency, file: File) = getDependencyFile(dependency,
    file.url.substringBeforeLast('.'), file.name.substringAfterLast('.'))

fun getDependencyFile(dependency: MavenDependency, nameWithoutExtension: String, extension: String, isAutoAddedDocumentation: Boolean = false) =
    if (dependency.version.endsWith("-SNAPSHOT")) {
        SnapshotDependencyFile(dependency, nameWithoutExtension, extension, isAutoAddedDocumentation = isAutoAddedDocumentation)
    } else {
        DependencyFile(dependency, nameWithoutExtension, extension, isAutoAddedDocumentation = isAutoAddedDocumentation)
    }


open class DependencyFile(
    val dependency: MavenDependency,
    val nameWithoutExtension: String,
    val extension: String,
    val kmpSourceSet: String? = null,
    val isAutoAddedDocumentation: Boolean = false,
    private val fileCache: FileCache = dependency.settings.fileCache,
) {

    @Volatile
    private var readOnlyExternalCacheDirectory: LocalRepository? = null
    private val mutex = Mutex()

    internal val fileName = "$nameWithoutExtension.$extension"

    private fun getCacheDirectory(): LocalRepository = fileCache.localRepository

    internal val diagnosticsReporter: DiagnosticReporter = CollectingDiagnosticReporter()

    private suspend fun getReadOnlyCacheDirectory(): LocalRepository? {
        if (readOnlyExternalCacheDirectory == null) {
            mutex.withLock {
                if (readOnlyExternalCacheDirectory == null) {
                    readOnlyExternalCacheDirectory = fileCache.readOnlyExternalRepositories.find {
                        it.guessPath(dependency, fileName)?.exists() == true
                    }
                }
            }
        }
        return readOnlyExternalCacheDirectory
    }

    @Volatile
    private var path: Path? = null

    @Volatile
    private var guessedPath: Path? = null

    suspend fun getPath(): Path? = path
        ?: guessedPath
        ?: withContext(Dispatchers.IO) { getPathBlocking() }

    private fun getPathBlocking(): Path? = path ?: guessedPath ?:
        getCacheDirectory().guessPath(dependency, fileName)?.also { guessedPath = it }

    override fun toString(): String = getPathBlocking()?.toString() ?: "[missing path]/$fileName"

    open suspend fun isDownloaded(): Boolean = withContext(Dispatchers.IO) { getPath()?.exists() == true }

    internal suspend fun hasMatchingChecksum(level: ResolutionLevel, context: Context, diagnosticsReporter: DiagnosticReporter = this.diagnosticsReporter): Boolean =
        withContext(Dispatchers.IO) {
            hasMatchingChecksum(level, context.settings.repositories, context.settings.progress, context.resolutionCache, diagnosticsReporter)
        }

    private suspend fun hasMatchingChecksum(
        level: ResolutionLevel,
        repositories: List<Repository>,
        progress: Progress,
        cache: Cache,
        diagnosticsReporter: DiagnosticReporter,
    ): Boolean {
        val path = getPath() ?: return false
        val hashers = path.computeHash()
        for (repository in repositories) {
            val result = verify(hashers, repository, progress, cache, diagnosticsReporter, requestedLevel = level)
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
        return path.hasMatchingChecksum(expectedHash)
    }

    suspend fun readText(): String = getPath()?.readTextWithRetry()
        ?: throw AmperDependencyResolutionException("Path doesn't exist, download the file first")

    internal suspend fun download(context: Context, diagnosticsReporter: DiagnosticReporter): Boolean =
        download(
            context.settings.repositories.ensureFirst(dependency.repository) ,
            context.settings.progress,
            context.resolutionCache,
            true,
            diagnosticsReporter
        )

    private suspend fun downloadUnderFileLock(
        repositories: List<Repository>,
        progress: Progress,
        cache: Cache,
        expectedHash: Hash?,
        diagnosticsReporter: DiagnosticReporter,
    ): Path? {
        return withContext(Dispatchers.IO) {
            produceResultWithDoubleLock(
                tempDir = getTempDir(),
                fileName,
                getAlreadyProducedResult = {
                    getPath()?.takeIf { isDownloadedWithVerification(expectedHash) }
                }
            ) { tempFilePath, fileChannel ->
                expectedHash
                      // First, try to resolve artifact from external local storage if actual hash is known
                    ?.let { resolveFromExternalLocalRepository(tempFilePath, fileChannel, expectedHash, cache, diagnosticsReporter) }
                      // Download artifact from external remote storage if it has not been resolved from local cache
                    ?: downloadAndVerifyHash(fileChannel, tempFilePath, repositories, progress, cache, expectedHash, diagnosticsReporter)
            }
        }
    }

    /**
     * Resolve a dependency file in an external local repository.
     * The resolved file is copied to the primary artifact storage from external one if the actual checksum of the file
     * is equal to the checksum downloaded from external remote repository and stored in the primary artifact storage.
     */
    private suspend fun resolveFromExternalLocalRepository(
        temp: Path, tempFileChannel: FileChannel, expectedHash: Hash,
        cache: Cache, diagnosticsReporter: DiagnosticReporter,
    ): Path? =
        this@DependencyFile.getReadOnlyCacheDirectory()
            ?.guessPath(dependency, fileName)
            ?.takeIf { it.hasMatchingChecksum(expectedHash) }
            ?.let { externalRepositoryPath ->
                // Copy external artifact into an opened temporary file channel
                resolveSafeOrNull { tempFileChannel.copyFrom(externalRepositoryPath) }
                    ?.let {
                        // Move a file to the target location on successful copying
                        storeToTargetLocation(temp, expectedHash, cache, null, diagnosticsReporter) {
                            expectedHash.takeIf { it.algorithm == "sha1" }?.hash
                                ?: computeHash(externalRepositoryPath, "sha1").hash
                        }
                    }
            }

    internal fun DependencyFile.getTempDir() = getCacheDirectory().getTempDir(dependency)

    private suspend fun DependencyFile.isDownloadedWithVerification(expectedHash: Hash?) =
        isDownloaded()
                && (isChecksum() && readText().sanitize() != null
                || expectedHash != null && hasMatchingChecksum(expectedHash))

    fun DependencyFile.isChecksum() = hashAlgorithms.any { extension.endsWith(".$it", true) }

    internal suspend fun download(
        repositories: List<Repository>,
        progress: Progress,
        cache: Cache,
        verify: Boolean,
        diagnosticsReporter: DiagnosticReporter,
    ): Boolean {
        val nestedDownloadReporter = CollectingDiagnosticReporter()
        val path = if (verify) {
            val expectedHash = getOrDownloadExpectedHash(repositories, progress, cache, nestedDownloadReporter)
            if (expectedHash == null) {
                diagnosticsReporter.addMessages(nestedDownloadReporter.getMessages())
                return false
            }

            if (isDownloadedWithVerification(expectedHash)) {
                return true
            }

            val optimizedRepositories = repositories.ensureFirst(dependency.repository) // first, try the same repo hash was downloaded from
            downloadUnderFileLock(optimizedRepositories, progress, cache, expectedHash, nestedDownloadReporter)
        } else {
            if (isDownloadedWithVerification(null)) {
                return true
            }

            downloadUnderFileLock(repositories, progress, cache, null, nestedDownloadReporter)
        }

        val messages = if (path != null) {
            nestedDownloadReporter.getMessages()
                .takeIf { it.all { it.severity <= Severity.INFO } }
                ?: emptyList()
        } else if (verify) {
            listOf(
                Message(
                    "Unable to download file $fileName for dependency $dependency",
                    repositories.joinToString(),
                    if (isAutoAddedDocumentation) Severity.INFO else Severity.ERROR,
                    suppressedMessages = nestedDownloadReporter.diagnostics
            ))
        } else nestedDownloadReporter.getMessages()

        diagnosticsReporter.addMessages(messages)

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
        diagnosticsReporter: DiagnosticReporter,
    ): Path? {
        for (repository in repositories) {
            val hasher = expectedHash?.let { Hasher(it.algorithm) }
            val hashers = buildList {
                hasher?.let { add(it) }                                        // for calculation of the given hash on download
                if (hasher?.algorithm != "sha1") add(Hasher("sha1"))  // for calculation of `sha1` hash on download additionally
            }
            val writers = hashers.map { it.writer } + Writer(channel::write)
            if (!download(writers, repository, progress, cache, diagnosticsReporter)) {
                channel.truncate(0)
                continue
            }
            if (hasher != null) {
                val result = checkHash(hasher, expectedHash, diagnosticsReporter)
                if (result > VerificationResult.PASSED) {
                    channel.truncate(0)
                    continue
                }
            }

            return storeToTargetLocation(temp, hasher, cache, repository, diagnosticsReporter) {
                hashers.find { it.algorithm == "sha1" }?.hash
                    ?: error("sha1 must be present among hashers") // should never happen
            }
        }

        temp.deleteIfExists()

        return null
    }

    private suspend fun storeToTargetLocation(
        temp: Path,
        actualHash: Hash?,
        cache: Cache,
        repository: Repository?,
        diagnosticsReporter: DiagnosticReporter,
        sha1: suspend () -> String,
    ): Path? {
        val target = getCacheDirectory().getPath(dependency, fileName, sha1)

        target.parent.createDirectories()
        try {
            temp.moveTo(target)
        } catch (_: FileAlreadyExistsException) {
            logger.debug("### {} already exists", target)
            if (actualHash == null || shouldOverwrite(cache, actualHash, computeHash(target, actualHash.algorithm))) {
                try {
                    logger.debug("### {} will be replaced with new one", target)
                    withRetryOnAccessDenied {
                        temp.moveTo(target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    logger.debug("### {} was not replaced with new one", target, t)
                    diagnosticsReporter.addMessage(
                        Message(
                            "Unable to save downloaded file",
                            t.toString(),
                            Severity.ERROR,
                            t,
                        )
                    )
                    return null
                }
            } else {
                temp.deleteIfExists()
            }
        }

        logger.trace(
            "{} for the dependency {}:{}:{} was stored into {}",
            if (repository == null) "File ${target.name}" else "Downloaded file ${target.name}",
            dependency.group,
            dependency.module,
            dependency.version,
            target.parent
        )

        onFileDownloaded(target, repository)

        diagnosticsReporter.suppress(
            Message(if (repository != null) "Downloaded ${target.name} from $repository" else "Resolved ${target.name} from local repository")
        )

        return target
    }

    protected open suspend fun shouldOverwrite(
        cache: Cache,
        expectedHash: Hash,
        actualHash : Hasher
    ): Boolean = checkHash(actualHash, expectedHash) > VerificationResult.PASSED

    private suspend fun verify(
        hashers: Collection<Hasher>,
        repository: Repository,
        progress: Progress,
        cache: Cache,
        diagnosticsReporter: DiagnosticReporter,
        requestedLevel: ResolutionLevel = ResolutionLevel.NETWORK
    ): VerificationResult {
        // Let's first check hashes available on disk.
        val levelToHasher = setOf(ResolutionLevel.LOCAL, requestedLevel)
            .flatMap { level -> hashers.filterWellKnownBrokenHashes(repository.url).map { level to it } }
        for ((level, hasher) in levelToHasher) {
            val algorithm = hasher.algorithm
            val expectedHash = getOrDownloadExpectedHash(algorithm, repository, progress, cache, diagnosticsReporter, level)
                ?: continue
            val actualHash = hasher.hash
            if (!expectedHash.equals(actualHash, true)) {
                if (requestedLevel != ResolutionLevel.NETWORK) {
                    diagnosticsReporter.addMessage(
                        Message(
                            "Hashes stored in a local artifacts storage don't match for $algorithm",
                            "expected: $expectedHash, actual: $actualHash",
                            severity = Severity.ERROR,
                        )
                    )
                }
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
        diagnosticsReporter: DiagnosticReporter,
        requestedLevel: ResolutionLevel = ResolutionLevel.NETWORK,
    ): Hash? {
        val hashers = createHashers()

        val nestedDownloadReporter = CollectingDiagnosticReporter()

        // First, try to find cache locally
        for (hasher in hashers) {
            val algorithm = hasher.algorithm
            val expectedHash = getOrDownloadExpectedHash(algorithm, null, progress, cache, nestedDownloadReporter, ResolutionLevel.LOCAL)
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
                        val expectedHash = getOrDownloadExpectedHash(algorithm, repository, progress, cache, nestedDownloadReporter, requestedLevel)
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

        diagnosticsReporter.addMessage(
            Message(
                "Unable to ${if (requestedLevel == ResolutionLevel.NETWORK) "download" else "resolve"} " +
                        "checksums of file $fileName for dependency $dependency",
                repositories.joinToString(),
                if (isAutoAddedDocumentation) Severity.INFO else Severity.ERROR,
                suppressedMessages = nestedDownloadReporter.diagnostics
            ))

        return null
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
        diagnosticsReporter: DiagnosticReporter,
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
            it.isDownloadedWithVerification(null)
                    || level == ResolutionLevel.NETWORK
                    && repository != null && it.download(listOf(repository), progress, cache, verify = false, diagnosticsReporter)
        }
        val hashFromRepository = hashFile?.readText()
        if (hashFromRepository != null) {
            return hashFromRepository.sanitize()
        }
        return null
    }

    private val checkSumRegex = "^[A-Fa-f0-9]+$".toRegex()

    /**
     * Sometimes files with checksums have additional information, e.g., a path to a file.
     * We expect that at least the first word in a file is a hash.
     */
    private fun String.sanitize() = split("\\s".toRegex()).getOrNull(0)
        ?.takeIf { it.isNotEmpty() && checkSumRegex.matches(it) }

    private suspend fun getHashFromGradleCacheDirectory(algorithm: String) =
        if (getCacheDirectory() is GradleLocalRepository && algorithm == "sha1") {
            getPath()?.parent?.name?.fixOldGradleHash(40)
        } else {
            null
        }

    private fun String.fixOldGradleHash(hashLength: Int) = takeIf { it != "null" }?.padStart(hashLength, '0')

    private suspend fun download(
        writers: Collection<Writer>,
        repository: Repository,
        progress: Progress,
        cache: Cache,
        diagnosticsReporter: DiagnosticReporter,
    ): Boolean {
        logger.trace("Trying to download {} from {}", nameWithoutExtension, repository)

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
        val name = getNamePart(repository, nameWithoutExtension, extension, progress, cache, diagnosticsReporter)
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
                    // Without user agent header, we don't get snapshot versions in maven-metadata.xml.
                    // I hope this blows your mind.
                    .header(HttpHeaders.UserAgent, "JetBrains Amper")
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
            diagnosticsReporter.addMessage(
                Message(
                    "Unable to reach $url",
                    e.toString(),
                    Severity.ERROR,
                    e,
                )
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

    internal open suspend fun getNamePart(
        repository: Repository,
        name: String,
        extension: String,
        progress: Progress,
        cache: Cache,
        diagnosticsReporter: DiagnosticReporter
    ) =
        "$name.$extension"

    internal open suspend fun onFileDownloaded(target: Path, repository: Repository? = null) {
        this.path = target
        this.dependency.repository = repository
    }

    companion object {

        private fun checkHash(
            hasher: Hasher,
            expectedHash: Hash,
            diagnosticsReporter: DiagnosticReporter? = null
        ): VerificationResult {
            // Let's first check hashes available on disk.
            val algorithm = hasher.algorithm
            val actualHash = hasher.hash
            if (expectedHash.algorithm != algorithm) {
                error("Expected hash type is ${expectedHash.hash}, but $algorithm was calculated") // should never happen
            } else if (!expectedHash.hash.equals(actualHash,true)) {
                diagnosticsReporter?.addMessage(
                    Message(
                        "Hashes don't match for $algorithm",
                        "expected: ${expectedHash.hash}, actual: $actualHash",
                        Severity.ERROR,
                    )
                )
                return VerificationResult.FAILED
            } else {
                return VerificationResult.PASSED
            }
        }

        private suspend fun Path.hasMatchingChecksum(expectedHash: Hash): Boolean {
            val actualHash = computeHash(this, expectedHash.algorithm)
            val result = checkHash(actualHash, expectedHash)
            return result == VerificationResult.PASSED
        }
    }
}

class SnapshotDependencyFile(
    dependency: MavenDependency,
    name: String,
    extension: String,
    fileCache: FileCache = dependency.settings.fileCache,
    isAutoAddedDocumentation: Boolean = false,
) : DependencyFile(dependency, name, extension, fileCache = fileCache, isAutoAddedDocumentation = isAutoAddedDocumentation) {

    private val mavenMetadata by lazy {
        SnapshotDependencyFile(dependency, "maven-metadata", "xml", FileCacheBuilder {
            amperCache = fileCache.amperCache
            localRepository = MavenLocalRepository(fileCache.amperCache.resolve("caches/maven-metadata"))
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
                    snapshotVersion = metadata.versioning.snapshotVersions?.snapshotVersions?.find {
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
        cache: Cache,
        diagnosticsReporter: DiagnosticReporter
    ): String {
        if (name != "maven-metadata" &&
            (mavenMetadata.isDownloaded()
                    || mavenMetadata.download(listOf(repository), progress, cache, diagnosticsReporter = diagnosticsReporter, verify = false))
        ) {
            getSnapshotVersion()
                ?.let { name.replace(dependency.version, it) }
                ?.let {
                    return "$it.$extension"
                }
        }
        return super.getNamePart(repository, name, extension, progress, cache, diagnosticsReporter)
    }

    override suspend fun shouldOverwrite(cache: Cache, expectedHash: Hash, actualHash: Hasher): Boolean =
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

fun <T> resolveSafeOrNull(block: () -> T?): T? {
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }
}

internal val httpClientKey = Key<java.net.http.HttpClient>("httpClient")

internal interface DiagnosticReporter {
    fun addMessage(message: Message): Boolean
    fun addMessages(messages: List<Message>): Boolean
    fun getMessages(): List<Message>
    fun suppress(message: Message): Boolean
    fun lowerSeverityTo(severity: Severity)
    fun reset()
}

internal class CollectingDiagnosticReporter: DiagnosticReporter {
    val diagnostics: MutableList<Message> = CopyOnWriteArrayList<Message>()

    val current: MutableList<Message> = CopyOnWriteArrayList<Message>()

    override fun addMessage(message: Message): Boolean {
        return diagnostics.add(message)
    }

    override fun addMessages(messages: List<Message>): Boolean {
        return diagnostics.addAll(messages)
    }

    override fun getMessages() = diagnostics.toList()

    override fun suppress(message: Message): Boolean {
        val suppressedDiagnostics = diagnostics.toList()
        reset()
        return diagnostics.add(message.copy(suppressedMessages = suppressedDiagnostics))
    }

    override fun lowerSeverityTo(severity: Severity) {
        val loweredDiagnostics = diagnostics.map { if (it.severity > severity) it.copy(severity = severity) else it }.toMutableList()
        reset()
        diagnostics.addAll(loweredDiagnostics)
    }

    override fun reset() { diagnostics.clear() }
}