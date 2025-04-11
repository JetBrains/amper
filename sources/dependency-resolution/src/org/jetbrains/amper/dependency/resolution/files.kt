/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.amper.concurrency.Hash
import org.jetbrains.amper.concurrency.Hasher
import org.jetbrains.amper.concurrency.StripedMutex
import org.jetbrains.amper.concurrency.Writer
import org.jetbrains.amper.concurrency.computeHash
import org.jetbrains.amper.concurrency.deleteIfExistsWithLogging
import org.jetbrains.amper.concurrency.produceResultWithDoubleLock
import org.jetbrains.amper.concurrency.produceResultWithTempFile
import org.jetbrains.amper.concurrency.readTextWithRetry
import org.jetbrains.amper.concurrency.withRetry
import org.jetbrains.amper.concurrency.withRetryOnAccessDenied
import org.jetbrains.amper.dependency.resolution.diagnostics.CollectingDiagnosticReporter
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.ContentLengthMismatch
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.HashesMismatch
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.SuccessfulDownload
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.SuccessfulLocalResolution
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.UnableToDownloadChecksums
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.UnableToDownloadFile
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.UnableToReachURL
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.UnableToResolveChecksums
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.UnableToSaveDownloadedFile
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.UnexpectedErrorOnDownload
import org.jetbrains.amper.dependency.resolution.diagnostics.DiagnosticReporter
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.dependency.resolution.diagnostics.asMessage
import org.jetbrains.amper.dependency.resolution.metadata.json.module.File
import org.jetbrains.amper.dependency.resolution.metadata.xml.parseMetadata
import org.jetbrains.amper.filechannels.writeFrom
import org.jetbrains.amper.telemetry.use
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpClient.Redirect
import java.net.http.HttpClient.Version
import java.net.http.HttpRequest
import java.net.http.HttpRequest.Builder
import java.net.http.HttpResponse
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


private val logger = LoggerFactory.getLogger("files.kt")

private val downloadSemaphore = Semaphore(10)

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
    suspend fun getPath(dependency: MavenDependency, name: String, sha1: suspend () -> String): Path =
        getPath(dependency, name, sha1())
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
        filesPath.resolve("${dependency.group}/${dependency.module}/${dependency.version.orUnspecified()}")
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
    override suspend fun getPath(dependency: MavenDependency, name: String, sha1: suspend () -> String): Path =
        guessPath(dependency, name)

    private fun getLocation(dependency: MavenDependency): Path {
        val groupPath = dependency.group.split('.').joinToString("/")
        return repository.resolve("${groupPath}/${dependency.module}/${dependency.version.orUnspecified()}")
    }
}

internal fun getDependencyFile(dependency: MavenDependency, file: File) = getDependencyFile(
    dependency,
    file.url.substringBeforeLast('.'),
    file.name.substringAfterLast('.'),
)

fun getDependencyFile(
    dependency: MavenDependency,
    nameWithoutExtension: String,
    extension: String,
    isAutoAddedDocumentation: Boolean = false,
) =
    // todo (AB) : What if version is nt specified, but later we will find out that it ends with "-SNAPSHOT",
    // todo (AB) : such a dependency file should be converted to SnapshotDependency
    if (dependency.version?.endsWith("-SNAPSHOT") == true) {
        SnapshotDependencyFile(
            dependency,
            nameWithoutExtension,
            extension,
            isAutoAddedDocumentation = isAutoAddedDocumentation,
        )
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

    private fun getPathBlocking(): Path? =
        path ?: guessedPath ?: getCacheDirectory().guessPath(dependency, fileName)?.also { guessedPath = it }

    override fun toString(): String = getPathBlocking()?.toString() ?: "[missing path]/$fileName"

    open suspend fun isDownloaded(): Boolean = getPath()?.exists() == true

    internal suspend fun hasMatchingChecksumLocally(
        diagnosticsReporter: DiagnosticReporter = this.diagnosticsReporter,
        context: Context,
        level: ResolutionLevel = ResolutionLevel.NETWORK,
    ): Boolean {
        val path = getPath() ?: return false
        return context.spanBuilder("hasMatchingChecksumLocally")
            .setAttribute("fileName", fileName)
            .use {
                val hashers = context.spanBuilder("computeHash").use { path.computeHash() }
                val result = context.spanBuilder("verifyHashes").use {
                    verifyHashes(hashers, diagnosticsReporter, level, context)
                }
                when (result) {
                    VerificationResult.PASSED -> true
                    VerificationResult.FAILED -> false
                    // todo (AB) : Check behavior, it is not clear,
                    // todo (AB) : why dependency should be considered resolved if it has no matching checksum.
                    VerificationResult.UNKNOWN -> level < ResolutionLevel.NETWORK
                }
            }
    }

    private suspend fun hasMatchingChecksum(expectedHash: Hash): Boolean {
        val path = getPath() ?: return false
        return path.hasMatchingChecksum(expectedHash)
    }

    suspend fun readText(): String = getPath()?.takeIf { it.exists() }?.readTextWithRetry()
        ?: throw AmperDependencyResolutionException("Path doesn't exist, download the file first")

    internal suspend fun download(context: Context, diagnosticsReporter: DiagnosticReporter): Boolean =
        download(
            context.settings.repositories.ensureFirst(dependency.repository),
            context.settings.progress,
            context.resolutionCache,
            context.settings.spanBuilder,
            true,
            diagnosticsReporter,
        )

    private suspend fun downloadUnderFileLock(
        repositories: List<Repository>,
        progress: Progress,
        cache: Cache,
        spanBuilderSource: SpanBuilderSource,
        verify: Boolean,
        diagnosticsReporter: DiagnosticReporter,
        fileLockSource: StripedMutex? = null,
    ): Path? {
        val expectedHashBeforeLocking = if (verify) {
            getExpectedHash(diagnosticsReporter, ResolutionLevel.NETWORK)
        } else null

        return withContext(Dispatchers.IO) {
            try {
                produceResultWithDoubleLock(
                    tempDir = getTempDir(),
                    fileName,
                    fileLockSource,
                    getAlreadyProducedResult = {
                        if (verify) {
                            getPath().takeIf {
                                isDownloadedWithVerification {
                                    expectedHashBeforeLocking ?: getExpectedHash(
                                        diagnosticsReporter,
                                        ResolutionLevel.NETWORK,
                                    )
                                }
                            }
                        } else null
                    }
                ) { tempFilePath, fileChannel ->
                    // under lock.
                    val expectedHash = if (verify) {
                        val expectedHashResolved = expectedHashBeforeLocking
                            ?: getExpectedHash(diagnosticsReporter, ResolutionLevel.NETWORK)
                            ?: downloadHash(repositories, progress, cache, spanBuilderSource, diagnosticsReporter)
                            // if hash is not resolved, but we still need to verify it, then we can't proceed with downloading
                            ?: return@produceResultWithDoubleLock null
                        expectedHashResolved.also {
                            if (isDownloadedWithVerification(expectedHashResolved)) {
                                return@produceResultWithDoubleLock getPath()
                            }
                        }
                    } else null

                    val artifactPath = expectedHash
                        ?.let {
                            // First, try to resolve artifact from external local storage if the actual hash is known
                            resolveFromExternalLocalRepository(
                                tempFilePath,
                                fileChannel,
                                expectedHash,
                                cache,
                                diagnosticsReporter,
                            )
                        }
                        ?: run {
                            // Download an artifact from external remote storage if it has not been resolved from the local cache
                            // trying at first the same repo where hash was downloaded from
                            val optimizedRepositories = repositories.ensureFirst(dependency.repository)
                            downloadAndVerifyHash(
                                fileChannel,
                                tempFilePath,
                                optimizedRepositories,
                                progress,
                                cache,
                                spanBuilderSource,
                                expectedHash,
                                diagnosticsReporter,
                                deleteTempFileOnFinish = false,
                            )
                        }
                        ?: expectedHash?.let {
                            // Downloading an artifact from external storage might have failed if checksum
                            // resolved from Gradle metadata is invalid (a pretty rare case though).
                            // In this case we should take actual checksum downloaded from the external repository
                            // and perform one more downloading attempt.
                            val expectedHashIgnoringMetadata = getExpectedHash(
                                diagnosticsReporter, ResolutionLevel.NETWORK, false
                            )
                                ?: downloadHash(repositories, progress, cache, spanBuilderSource, diagnosticsReporter)
                            if (expectedHashIgnoringMetadata != null && expectedHashIgnoringMetadata.hash != expectedHash.hash) {
                                // Expected checksum was taken from metadata on the previous download attempt, not from the checksum file.
                                // Trying to download the artifact one more time with the checksum downloaded from the remote repository.
                                val optimizedRepositories = repositories.ensureFirst(dependency.repository)
                                return@let downloadAndVerifyHash(
                                    fileChannel,
                                    tempFilePath,
                                    optimizedRepositories,
                                    progress,
                                    cache,
                                    spanBuilderSource,
                                    expectedHashIgnoringMetadata,
                                    diagnosticsReporter,
                                )
                            }
                            null
                        }

                    tempFilePath.deleteIfExists()

                    artifactPath
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                diagnosticsReporter.addMessage(
                    UnexpectedErrorOnDownload.asMessage(
                        fileName,
                        dependency,
                        t::class.simpleName,
                        t.message,
                        exception = t,
                    )
                )
                null
            }
        }
    }

    /**
     * Resolve a dependency file in an external local repository.
     * The resolved file is copied to the primary artifact storage from an external one if the actual checksum of the file
     * is equal to the checksum downloaded from the external remote repository and stored in the primary artifact storage.
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
                resolveSafeOrNull { tempFileChannel.writeFrom(externalRepositoryPath) }
                    ?.let {
                        // Move a file to the target location on successful copying
                        storeToTargetLocation(temp, expectedHash, cache, null, diagnosticsReporter) {
                            expectedHash.takeIf { it.algorithm == "sha1" }?.hash
                                ?: computeHash(externalRepositoryPath, "sha1").hash
                        }
                    }
            }

    internal fun DependencyFile.getTempDir() = getCacheDirectory().getTempDir(dependency)

    private suspend fun DependencyFile.isDownloadedWithVerification(expectedHash: Hash) =
        isDownloadedWithVerification { expectedHash }

    private suspend fun DependencyFile.isDownloadedWithVerification(expectedHashFn: suspend () -> Hash?) =
        isDownloaded() && expectedHashFn()?.let { hasMatchingChecksum(it) } == true

    private suspend fun DependencyFile.isDownloadedValidHash() =
        isDownloaded() && isChecksum() && readText().sanitize() != null

    fun DependencyFile.isChecksum() = hashAlgorithms.any { extension.endsWith(".$it", true) }

    internal suspend fun download(
        repositories: List<Repository>,
        progress: Progress,
        cache: Cache,
        spanBuilderSource: SpanBuilderSource,
        verify: Boolean,
        diagnosticsReporter: DiagnosticReporter,
        fileLockSource: StripedMutex? = null,
    ): Boolean {
        val nestedDownloadReporter = CollectingDiagnosticReporter()

        val path = downloadUnderFileLock(repositories, progress, cache, spanBuilderSource, verify, nestedDownloadReporter, fileLockSource)
        val collectedMessages = nestedDownloadReporter.getMessages()

        val messages = when {
            path != null -> collectedMessages.takeIf { messages -> messages.all { it.severity <= Severity.INFO } }
                ?: emptyList()

            verify -> {
                if (collectedMessages.singleOrNull()?.id == UnableToDownloadChecksums.id) {
                    collectedMessages
                } else {
                    listOf(
                        UnableToDownloadFile.asMessage(
                            fileName,
                            dependency,
                            extra = DependencyResolutionBundle.message(
                                "extra.repositories",
                                repositories.joinToString()
                            ),
                            overrideSeverity = Severity.INFO.takeIf { isAutoAddedDocumentation },
                            childMessages = collectedMessages,
                        )
                    )
                }
            }

            else -> collectedMessages
        }

        diagnosticsReporter.addMessages(messages)

        return path != null
    }

    private fun List<Repository>.ensureFirst(repository: Repository?) =
        repository?.let {
            if (this.isEmpty() || this[0].url == repository.url || !this.map { it.url }.contains(repository.url))
                this
            else
                buildList {
                    add(repository)
                    addAll(this - repository)
                }
        } ?: this

    private suspend fun downloadAndVerifyHash(
        channel: FileChannel,
        temp: Path,
        repositories: List<Repository>,
        progress: Progress,
        cache: Cache,
        spanBuilderSource: SpanBuilderSource,
        expectedHash: Hash?,      // this should be passed for all files being downloaded, except when hash files are downloaded itself
        diagnosticsReporter: DiagnosticReporter,
        deleteTempFileOnFinish: Boolean = true,
    ): Path? {
        for (repository in repositories) {
            val hasher = expectedHash?.let { Hasher(it.algorithm) }
            val sha1Hasher = hasher?.takeIf { it.algorithm == "sha1" } ?: Hasher("sha1")
            val hashers = buildList {
                hasher?.let { add(it) }                     // for calculation of the given hash on download
                if (hasher !== sha1Hasher) add(sha1Hasher)  // for calculation of `sha1` hash on download additionally
            }
            val writers = hashers.map { it.writer } + Writer(channel::write)
            if (!download(writers, repository, progress, cache, spanBuilderSource, diagnosticsReporter)) {
                channel.truncate(0)
                continue
            }
            if (hasher != null) {
                // todo (AB) : [AMPER-4149]: Download hash file if expectedHash was resolved locally and doesn't match
                val result = checkHash(hasher, expectedHash, diagnosticsReporter)
                if (result > VerificationResult.PASSED) {
                    channel.truncate(0)
                    continue
                }
            }

            return storeToTargetLocation(temp, hasher, cache, repository, diagnosticsReporter) { sha1Hasher.hash }
        }

        if (deleteTempFileOnFinish) {
            temp.deleteIfExists()
        }

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
                        UnableToSaveDownloadedFile.asMessage(
                            fileName,
                            dependency,
                            extra = DependencyResolutionBundle.message("extra.exception", t),
                            exception = t,
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
            dependency.version.orUnspecified(),
            target.parent
        )

        onFileDownloaded(target, repository)

        diagnosticsReporter.suppress(
            if (repository != null) {
                SuccessfulDownload.asMessage(target.name, repository)
            } else {
                SuccessfulLocalResolution.asMessage(target.name)
            }
        )

        return target
    }

    protected open suspend fun shouldOverwrite(
        cache: Cache,
        expectedHash: Hash,
        actualHash: Hasher,
    ): Boolean = checkHash(actualHash, expectedHash) > VerificationResult.PASSED

    /**
     * Check that the hashes of this artifact file match the expected ones taken from the local artifact storage.
     * The actual hashes of the artifact file are computed using the given [hashers].
     */
    private suspend fun verifyHashes(
        hashers: Collection<Hash>,
        diagnosticsReporter: DiagnosticReporter,
        requestedLevel: ResolutionLevel,
        context: Context,
    ): VerificationResult {
        for (hasher in hashers) {
            val algorithm = hasher.algorithm
            val expectedHash = context.spanBuilder("getExpectedHash").use { getExpectedHash(algorithm, context) } ?: continue
            return checkHash(
                hasher,
                expectedHash = object : Hash {
                    override val hash: String = expectedHash
                    override val algorithm = algorithm
                },
                diagnosticsReporter.takeIf { requestedLevel != ResolutionLevel.NETWORK })
        }
        return VerificationResult.UNKNOWN
    }

    /**
     * @return hash of the artifact resolved from a local artifacts storage
     */
    private suspend fun getExpectedHash(
        diagnosticsReporter: DiagnosticReporter, requestedLevel: ResolutionLevel, searchInMetadata: Boolean = true,
    ): Hash? {
        val hash = LocalStorageHashSource.getExpectedHash(this, searchInMetadata)

        if (hash == null && requestedLevel != ResolutionLevel.NETWORK) {
            diagnosticsReporter.addMessage(
                UnableToResolveChecksums.asMessage(
                    fileName,
                    dependency,
                    overrideSeverity = Severity.INFO.takeIf { isAutoAddedDocumentation },
                )
            )
        }

        return hash
    }

    /**
     * Downloads hash from one of the specified repositories.
     * It tries to download sha512 checksum from all provided repositories, the first found is returned.
     * If nothing is found, then it tries sha256, sha1 and then md5 consequently.
     */
    private suspend fun downloadHash(
        repositories: List<Repository>,
        progress: Progress,
        cache: Cache,
        spanBuilderSource: SpanBuilderSource,
        diagnosticsReporter: DiagnosticReporter,
    ): Hash? {
        val hashers = createHashers()

        val nestedDownloadReporter = CollectingDiagnosticReporter()

        // Try to download hash from given repositories
        for (hasher in hashers) {
            val algorithm = hasher.algorithm
            val validRepositories = repositories.filter { !hasher.isWellKnownBrokenHashIn(it.url) }
            val expectedHash =
                downloadHash(algorithm, validRepositories, progress, cache, spanBuilderSource, nestedDownloadReporter)
            if (expectedHash != null) {
                return object : Hash {
                    override val hash: String = expectedHash
                    override val algorithm = algorithm
                }
            }
        }

        diagnosticsReporter.addMessage(
            UnableToDownloadChecksums.asMessage(
                fileName,
                dependency,
                extra = DependencyResolutionBundle.message("extra.repositories", repositories.joinToString()),
                overrideSeverity = Severity.INFO.takeIf { isAutoAddedDocumentation },
                childMessages = nestedDownloadReporter.getMessages(),
            )
        )

        return null
    }

    private fun Hasher.isWellKnownBrokenHashIn(repository: String): Boolean {
        if (repository == "https://plugins.gradle.org/m2/") {
            return algorithm == "sha512" || algorithm == "sha256"
        }

        return false
    }

    enum class VerificationResult { PASSED, UNKNOWN, FAILED }

    /**
     * Tries to find hash of the given type in the local artifact storage.
     * Being found, it is supposed to be valid (taken from a verified location).
     *
     * The hash calculated from the actual artifact (either downloaded or resolved from external local storage) should match the found one.
     *
     * If checksums were found locally both in a metadata file and in a separate checksum file, the latter is preferred.
     * Usually, the checksum from the metadata file is enough for resolution,
     * and checksum files are not even downloaded from the external repository.
     * But sometimes checksums declared in the metadata file are invalid, and in this case DR downloads checksum files and
     * uses them for verification.
     * This way, the checksum file is presented in local storage only in case metadata contains invalid data or is completely missing.
     */
    internal suspend fun getExpectedHash(algorithm: String, context: Context, searchInMetadata: Boolean = true): String? =
        LocalStorageHashSource.getExpectedHash(this, algorithm, context, searchInMetadata)

    /**
     * Downloads hash of a particular type from one of the specified repositories.
     * Returns <code>null</code> if the hash was not found.
     */
    internal suspend fun downloadHash(
        algorithm: String,
        repositories: List<Repository>,
        progress: Progress,
        cache: Cache,
        spanBuilderSource: SpanBuilderSource,
        diagnosticsReporter: DiagnosticReporter,
    ): String? {
        val hashFile = getDependencyFile(dependency, nameWithoutExtension, "$extension.$algorithm")

        for (repository in repositories) {
            // We need to iterate repositories one by one, because some invalid repo could return some invalid response
            // that will be validated and sanitized, in this case we should try other repositories
            val expectedHashPath = produceResultWithTempFile(
                tempDir = getTempDir(),
                targetFileName = hashFile.fileName,
                { hashFile.takeIf { it.isDownloadedValidHash() }?.getPath() },
            ) { tempFilePath, fileChannel ->
                hashFile.downloadAndVerifyHash(
                    fileChannel,
                    tempFilePath,
                    listOf(repository),
                    progress,
                    cache,
                    spanBuilderSource,
                    null,
                    diagnosticsReporter,
                )
            }

            val hashFromRepository = expectedHashPath?.readTextWithRetry()?.sanitize()
            if (hashFromRepository != null) {
                return hashFromRepository
            } else {
                expectedHashPath?.deleteIfExistsWithLogging("Successfully deleted invalid checksum file $expectedHashPath")
            }
        }

        return null
    }

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
        spanBuilderSource: SpanBuilderSource,
        diagnosticsReporter: DiagnosticReporter,
    ): Boolean {
        logger.trace("Trying to download {} from {}", nameWithoutExtension, repository)

        val client = cache.computeIfAbsent(httpClientKey) { HttpClientProvider.getHttpClient() }

        val name = getNamePart(repository, nameWithoutExtension, extension, progress, cache, spanBuilderSource, diagnosticsReporter)
        val url = repository.url.trimEnd('/') +
                "/${dependency.group.replace('.', '/')}" +
                "/${dependency.module}" +
                "/${dependency.version.orUnspecified()}" +
                "/$name"

        fun getContentLengthHeaderValue(response: HttpResponse<InputStream>): Long? = response.headers()
            .firstValueAsLong(HttpHeaders.ContentLength)
            .takeIf { it.isPresent && it.asLong != -1L }?.asLong

        try {

            // todo (AB) : Use exponential retry here
            return withRetry(
                retryCount = 3,
                retryOnException = { e ->
                    e is IOException
                }
            ) {
                spanBuilderSource("downloadAttempt").use {
                    val request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        // Without a user agent header, we don't get snapshot versions in maven-metadata.xml.
                        // I hope this blows your mind.
                        .header(HttpHeaders.UserAgent, "JetBrains Amper")
                        .withBasicAuth(repository)
                        .timeout(Duration.ofMinutes(2))
                        .GET()
                        .build()

                    downloadSemaphore.withPermit {
                        val future = client.sendAsync(request, BodyHandlers.ofInputStream())
                        val response = future.await()

                        response.body().use { responseBody ->
                            when (val status = response.statusCode()) {
                                200 -> {
                                    val expectedSize = fileFromVariant(dependency, name)?.size
                                        ?: getContentLengthHeaderValue(response)
                                    val size = responseBody.toByteReadChannel().readTo(writers)
//                                val size = responseBody.readTo(writers)

                                    val isSuccessfullyDownloaded = if (expectedSize != null && size != expectedSize) {
                                        val sizeFromResponse = getContentLengthHeaderValue(response)
                                        // If Content-Length is presented and differs from the actual content length, then it is an error
                                        // Otherwise, it might be an incorrect record in Gradle metadata.
                                        // That happens, we just report an INFO message and proceed with checksum verification in that case.
                                        val overrideSeverity =
                                            Severity.INFO.takeIf { sizeFromResponse == null || size == sizeFromResponse }
                                        diagnosticsReporter.addMessage(
                                            ContentLengthMismatch.asMessage(
                                                url,
                                                sizeFromResponse,
                                                size,
                                                overrideSeverity = overrideSeverity,
                                            )
                                        )

                                        overrideSeverity != null
                                    } else {
                                        if (logger.isDebugEnabled) {
                                            logger.debug(
                                                "Downloaded {} for the dependency {}:{}:{}",
                                                url,
                                                dependency.group,
                                                dependency.module,
                                                dependency.version
                                            )
                                        } else if (extension.substringAfterLast(".") !in hashAlgorithms) {
                                            // Reports downloaded dependency to INFO (visible to user by default)
                                            logger.info("Downloaded $url")
                                        }

                                        true
                                    }

                                    isSuccessfullyDownloaded
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
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("$repository: Failed to download $url", e)
            diagnosticsReporter.addMessage(
                UnableToReachURL.asMessage(
                    url,
                    extra = DependencyResolutionBundle.message("extra.exception", e),
                    exception = e,
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
        spanBuilderSource: SpanBuilderSource,
        diagnosticsReporter: DiagnosticReporter,
    ) =
        "$name.$extension"

    internal open suspend fun onFileDownloaded(target: Path, repository: Repository? = null) {
        this.path = target
        this.dependency.repository = repository
    }

    private enum class LocalStorageHashSource {
        File {
            override suspend fun getExpectedHash(artifact: DependencyFile, algorithm: String, context: Context,) =
                artifact.getHashFromGradleCacheDirectory(algorithm)
                    ?: getDependencyFile(
                            artifact.dependency,
                            artifact.nameWithoutExtension,
                            "${artifact.extension}.$algorithm",
                        )
                        .takeIf { file ->
                            context.settings.spanBuilder("isDownloaded").use { file.isDownloaded() }
                        }
                        ?.let { file -> context.settings.spanBuilder("readText").use { file.readText() } }
                        ?.sanitize()
        },
        MetadataInfo {
            override suspend fun getExpectedHash(artifact: DependencyFile, algorithm: String, context: Context,) =
                with(artifact) {
                    when (algorithm) {
                        "sha512" -> fileFromVariant(dependency, fileName)?.sha512?.fixOldGradleHash(128)
                        "sha256" -> fileFromVariant(dependency, fileName)?.sha256?.fixOldGradleHash(64)
                        "sha1" -> fileFromVariant(dependency, fileName)?.sha1?.fixOldGradleHash(40)
                        "md5" -> fileFromVariant(dependency, fileName)?.md5?.fixOldGradleHash(32)
                        else -> null
                    }
                }
        };

        protected abstract suspend fun getExpectedHash(artifact: DependencyFile, algorithm: String, context: Context): String?

        private suspend fun getExpectedHash(artifact: DependencyFile, context: Context): Hash? {
            for (hashAlgorithm in hashAlgorithms) {
                val expectedHash = getExpectedHash(artifact, hashAlgorithm, context)
                if (expectedHash != null) {
                    return object : Hash {
                        override val hash: String = expectedHash
                        override val algorithm = hashAlgorithm
                    }
                }
            }
            return null
        }

        companion object {
            suspend fun getExpectedHash(artifact: DependencyFile, algorithm: String, context: Context, searchInMetadata: Boolean) =
                context.spanBuilder(" File.getExpectedHash").use {
                    File.getExpectedHash(artifact, algorithm, context)
                }
                    ?: if (searchInMetadata) context.spanBuilder("MetadataInfo.getExpectedHash").use {
                        MetadataInfo.getExpectedHash(artifact, algorithm, context)
                    } else null

            /**
             * @return hash of the artifact resolved from a local artifacts storage
             */
            suspend fun getExpectedHash(artifact: DependencyFile, searchInMetadata: Boolean = true): Hash? =
                // todo (AB) : Remove this empty Context {}
                File.getExpectedHash(artifact, Context {})
                    ?: if (searchInMetadata) MetadataInfo.getExpectedHash(artifact, Context {}) else null
        }
    }

    companion object {

        private val checkSumRegex = "^[A-Fa-f0-9]+$".toRegex()

        /**
         * Sometimes files with checksums have additional information, e.g., a path to a file.
         * We expect that at least the first word in a file is a hash.
         */
        private fun String.sanitize() = split("\\s".toRegex()).getOrNull(0)
            ?.takeIf { it.isNotEmpty() && checkSumRegex.matches(it) }

        private fun checkHash(
            actualHash: Hash,
            expectedHash: Hash,
            diagnosticsReporter: DiagnosticReporter? = null,
        ): VerificationResult {
            // Let's first check hashes available on disk.
            if (expectedHash.algorithm != actualHash.algorithm) {
                error("Expected hash type is ${expectedHash.algorithm}, but ${actualHash.algorithm} was calculated") // should never happen
            } else if (!expectedHash.hash.equals(actualHash.hash, true)) {
                diagnosticsReporter?.addMessage(
                    HashesMismatch.asMessage(
                        actualHash.algorithm,
                        extra = DependencyResolutionBundle.message(
                            "extra.expected.actual",
                            expectedHash.hash,
                            actualHash
                        ),
                    ),
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
) : DependencyFile(
    dependency,
    name,
    extension,
    fileCache = fileCache,
    isAutoAddedDocumentation = isAutoAddedDocumentation,
) {

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
                    val metadata = mavenMetadata.getPath()?.takeIf { it.exists() }?.readText()?.parseMetadata()
                    snapshotVersion = metadata?.let {
                        it.versioning.snapshotVersions?.snapshotVersions?.find {
                            it.extension == extension.substringBefore('.') // pom.sha512 -> pom
                        }?.value ?: ""
                    }
                }
            }
        }
        return snapshotVersion.takeIf { it?.isNotEmpty() == true }
    }

    override suspend fun isDownloaded(): Boolean = isSnapshotDownloaded()

    private suspend fun isSnapshotDownloaded(): Boolean {
        val path = getPath()
        if (path?.exists() != true) {
            return false
        }
        if (nameWithoutExtension != "maven-metadata") {
            if (!mavenMetadata.isDownloaded()
                && (!isChecksum() || path.isUpToDate())
            ) {
                // maven-metadata.xml is absent, no way to verify if the snapshot is actual or not.
                // We will have to redownload checksum if it is too old
                // and will reuse the artifact itself if it matches the checksum.
                return true
            }

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
            return path.isUpToDate()
        }
        return true
    }

    private fun Path.isUpToDate(): Boolean =
        getLastModifiedTime() > FileTime.from(ZonedDateTime.now().minusDays(1).toInstant())

    override suspend fun getNamePart(
        repository: Repository,
        name: String,
        extension: String,
        progress: Progress,
        cache: Cache,
        spanBuilderSource: SpanBuilderSource,
        diagnosticsReporter: DiagnosticReporter,
    ): String {
        if (name != "maven-metadata" &&
            isMavenMetadataDownloadedOrDownload(repository, progress, cache, spanBuilderSource, diagnosticsReporter = diagnosticsReporter)
        ) {
            getSnapshotVersion()
                ?.let { name.replace(dependency.version.orUnspecified(), it) }
                ?.let {
                    return "$it.$extension"
                }
        }
        return super.getNamePart(repository, name, extension, progress, cache, spanBuilderSource, diagnosticsReporter)
    }

    private suspend fun isMavenMetadataDownloadedOrDownload(
        repository: Repository, progress: Progress, cache: Cache, spanBuilderSource: SpanBuilderSource, diagnosticsReporter: DiagnosticReporter,
    ): Boolean {
        return mavenMetadata.isDownloaded()
                || mavenMetadata
            .download(listOf(repository), progress, cache, spanBuilderSource, verify = false, diagnosticsReporter, mavenMetadataFilesLock)
    }


    override suspend fun shouldOverwrite(cache: Cache, expectedHash: Hash, actualHash: Hasher): Boolean =
        nameWithoutExtension == "maven-metadata"
                || getVersionFile()?.takeIf { it.exists() }?.readText() != getSnapshotVersion()
                || super.shouldOverwrite(cache, expectedHash, actualHash)

    override suspend fun onFileDownloaded(target: Path, repository: Repository?) {
        super.onFileDownloaded(target, repository)
        if (nameWithoutExtension != "maven-metadata") {
            getSnapshotVersion()?.let { getVersionFile()?.writeText(it) }
        }
    }

    companion object {
        /**
         * maven-metadata.xml is downloaded on demand when downloading of the artifact (pom or module or jar)
         * is in progress and file lock is already taken.
         * This lock source is used for nested locking under the main artifact lock.
         * It is safe since it is always ordered, it is taken under the artifact's ock, not vice versa.
         * It protects from concurrent attempts to download maven-metadata
         * while downloading pom/module/different artifacts in parallel
         */
        private val mavenMetadataFilesLock = StripedMutex(stripeCount = 512)
    }
}

internal fun getNameWithoutExtension(node: MavenDependency): String = "${node.module}-${node.version.orUnspecified()}"

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

/**
 * This could be used as a replacement of the channel-based function above
 * if we decide to remove ktor dependencies from DR.
 */
@Suppress("unused")
private fun InputStream.readTo(writers: Collection<Writer>): Long {
    var size = 0L
    val chunk = ByteArray(1024)
    do {
        val readLength = read(chunk)
        if (readLength == -1) break

        size += readLength

        val buffer = ByteBuffer.wrap(chunk, 0, readLength)
        writers.forEach {
            it.write(buffer)
            buffer.flip()
        }
    } while (true)

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

/**
 * This is a way to inject custom HttpClient via resolution [Context].
 * The injected client is not closed after resolution is finished;
 * it is a responsibility of the calling side that provides the custom client to handle its lifecycle.
 *
 * If the [Context] doesn't provide a custom client, then the default one is taken from [HttpClientProvider]
 */
internal val httpClientKey = Key<HttpClient>("httpClient")

internal object HttpClientProvider {

    @kotlin.concurrent.Volatile
    var client: HttpClient? = null

    fun getHttpClient(): HttpClient = client
        ?: synchronized(this) {
            client
                ?: HttpClient.newBuilder()
                    .version(Version.HTTP_2)
                    .followRedirects(Redirect.NORMAL)
                    .sslContext(SSLContext.getDefault())
                    .connectTimeout(Duration.ofSeconds(20))
//                .proxy(ProxySelector.of(InetSocketAddress("proxy.example.com", 80)))
//                .authenticator(Authenticator.getDefault())
                    .build()
                    .also { client = it }
        }

    /**
     * This is unsued now but left for the reference for future.
     * It might be a good practice to reinitialize HTTP Client from time to time after an idle period for
     * the connections leak prophylactics,
     * and in that case the old instance of a client should be properly shutdown.
     */
    private fun closeHttpClient() {
        val client = this.client
        this.client = null
        try {
            // In java 21 HttpClient is AutoClosable,
            // but it has an issue that prevents it from shutting down https://bugs.openjdk.org/browse/JDK-8316580
            // Calling 'HttpClient.shutdownNow' is a workaround
            client
                ?.let {
                    HttpClient::class.java.declaredMethods.firstOrNull { it.name == "shutdownNow" && it.parameterTypes.isEmpty() }
                        ?: HttpClient::class.java.declaredMethods.firstOrNull { it.name == "shutdown" && it.parameterTypes.isEmpty() }
                }
                ?.let {
                    logger.debug("Calling ${it.declaringClass.name}.${it.name} (on closing DR context)")
                    it.invoke(client)
                }
                ?: (client as? AutoCloseable)?.close()
        } catch (e: Exception) {
            logger.warn("Failed to close DR context resource", e)
        }
    }
}
