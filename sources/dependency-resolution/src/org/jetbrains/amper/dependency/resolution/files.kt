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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.amper.concurrency.FileMutexGroup
import org.jetbrains.amper.concurrency.withRetry
import org.jetbrains.amper.dependency.resolution.diagnostics.CollectingDiagnosticReporter
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.ContentLengthMismatch
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.HashesMismatch
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.SuccessfulDownload
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.SuccessfulLocalResolution
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.UnableToReachURL
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.UnableToResolveChecksums
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.UnableToSaveDownloadedFile
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.UnexpectedErrorOnDownload
import org.jetbrains.amper.dependency.resolution.diagnostics.DiagnosticReporter
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.dependency.resolution.diagnostics.UnableToDownloadChecksums
import org.jetbrains.amper.dependency.resolution.diagnostics.UnableToDownloadFile
import org.jetbrains.amper.dependency.resolution.diagnostics.asMessage
import org.jetbrains.amper.dependency.resolution.files.Hash
import org.jetbrains.amper.dependency.resolution.files.Hasher
import org.jetbrains.amper.dependency.resolution.files.SimpleHash
import org.jetbrains.amper.dependency.resolution.files.Writer
import org.jetbrains.amper.dependency.resolution.files.computeHash
import org.jetbrains.amper.dependency.resolution.files.deleteIfExistsWithLogging
import org.jetbrains.amper.dependency.resolution.files.produceResultWithDoubleLock
import org.jetbrains.amper.dependency.resolution.files.produceResultWithTempFile
import org.jetbrains.amper.dependency.resolution.files.readTextWithRetry
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
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*
import javax.net.ssl.SSLContext
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

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
    fun guessPath(dependency: MavenDependencyImpl, name: String): Path?

    /**
     * Returns a path to a temp directory for a particular dependency.
     *
     * The file is downloaded to a temp directory to be later moved to a permanent one provided by [getPath].
     * Both paths should preferably be on the same file drive to allow atomic move.
     */
    fun getTempDir(dependency: MavenDependencyImpl): Path

    /**
     * Returns a path to a file on disk.
     * It can't return `null` as all necessary information must be available at a call site.
     *
     * Gradle uses a SHA1 hash as a part of a path.
     */
    fun getPath(dependency: MavenDependencyImpl, name: String, sha1: String): Path

    /**
     * Returns a path to a file on disk.
     * It can't return `null` as all necessary information must be available at a call site.
     *
     * Gradle uses a SHA1 hash as a part of a path.
     */
    suspend fun getPath(dependency: MavenDependencyImpl, name: String, sha1: suspend () -> String): Path =
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

    override fun guessPath(dependency: MavenDependencyImpl, name: String): Path? {
        val location = getLocation(dependency)
        val pathFromVariant = fileFromVariant(dependency, name)?.let { location.resolve("${it.sha1}/${it.name}") }
        if (pathFromVariant != null) return pathFromVariant
        if (!location.exists()) return null
        return location.naiveSearchDepth2 { it.name == name }.firstOrNull()
    }

    /**
     * A very basic and stupid implementation of DFWalk for a file tree, since [Files.walk]
     * sometimes does not notice directory changes.
     */
    private fun Path.naiveSearchDepth2(shouldDescend: Boolean = true, filterBlock: (Path) -> Boolean): List<Path> =
        buildList {
            Files.list(this@naiveSearchDepth2)
                .use {
                    it.filter { it.exists() }
                        .forEach {
                            if (it.isDirectory() && shouldDescend) addAll(it.naiveSearchDepth2(false, filterBlock))
                            if (filterBlock(it)) add(it)
                        }
                }
        }

    override fun getTempDir(dependency: MavenDependencyImpl): Path = getLocation(dependency)

    override fun getPath(dependency: MavenDependencyImpl, name: String, sha1: String): Path {
        val location = getLocation(dependency)
        return location.resolve(sha1).resolve(name)
    }

    private fun getLocation(dependency: MavenDependencyImpl) =
        filesPath.resolve("${dependency.group}/${dependency.module}/${dependency.version.orUnspecified()}")
}

/**
 * Defines an `.m2` directory structure.
 *
 * It accepts a path to the [repository] directory or discovers the location using maven conventions.
 */
class MavenLocalRepository(val repository: Path) : LocalRepository {

    companion object {
        /**
         * The default local repository as defined by Maven itself. It is initialized lazily by looking at the several
         * locations using [LocalM2RepositoryFinder.findPath].
         */
        val Default by lazy {
            MavenLocalRepository(LocalM2RepositoryFinder.findPath())
        }
    }

    override fun toString(): String = "[Maven] $repository"

    override fun guessPath(dependency: MavenDependencyImpl, name: String): Path =
        repository.resolve(getLocation(dependency)).resolve(name)

    override fun getTempDir(dependency: MavenDependencyImpl): Path = repository.resolve(getLocation(dependency))

    override fun getPath(dependency: MavenDependencyImpl, name: String, sha1: String): Path = guessPath(dependency, name)

    /**
     * Parameter sha1 is ignored and not calculated
     */
    override suspend fun getPath(dependency: MavenDependencyImpl, name: String, sha1: suspend () -> String): Path =
        guessPath(dependency, name)

    private fun getLocation(dependency: MavenDependencyImpl): Path {
        val groupPath = dependency.group.split('.').joinToString("/")
        return repository.resolve("${groupPath}/${dependency.module}/${dependency.version.orUnspecified()}")
    }
}

internal fun getDependencyFile(dependency: MavenDependencyImpl, file: File, isDocumentation: Boolean = false) =
    getDependencyFile(
        dependency,
        file.url.substringBeforeLast('.'),
        file.name.substringAfterLast('.',),
        isDocumentation = isDocumentation
    )

private fun DependencyFileImpl.getHashDependencyFile(algorithm: String) = getDependencyFile(
    dependency,
    nameWithoutExtension,
    "$extension.$algorithm"
)

fun getDependencyFile(
    dependency: MavenDependencyImpl,
    nameWithoutExtension: String,
    extension: String,
    isDocumentation: Boolean = false,
    isAutoAddedDocumentation: Boolean = false,
) =
    // todo (AB) : What if version is not specified, but later we will find out that it ends with "-SNAPSHOT",
    // todo (AB) : such a dependency file should be converted to SnapshotDependency
    if (dependency.version?.endsWith("-SNAPSHOT") == true) {
        SnapshotDependencyFileImpl(
            dependency,
            nameWithoutExtension,
            extension,
            isDocumentation = isDocumentation,
            isAutoAddedDocumentation = isAutoAddedDocumentation,
        )
    } else {
        DependencyFileImpl(dependency, nameWithoutExtension, extension,
            isDocumentation = isDocumentation, isAutoAddedDocumentation = isAutoAddedDocumentation)
    }

@Serializable
data class DependencyFilePlain private constructor(
    override val isAutoAddedDocumentation: Boolean = false,
    override val isDocumentation: Boolean = false,
    override val extension: String = ".jar",
    private val pathAsString: String? = null,
    override val kmpSourceSet: String? = null,
    override val kmpPlatforms: Set<ResolutionPlatform>? = null
) : DependencyFile {

    constructor(dependencyFile: DependencyFile) : this(
        dependencyFile.isAutoAddedDocumentation,
        dependencyFile.isDocumentation,
        dependencyFile.extension,
        dependencyFile.path?.absolutePathString(),
        dependencyFile.kmpSourceSet,
        dependencyFile.kmpPlatforms)

    @Transient
    override val path: Path? = pathAsString?.let { Path(it) }
}

sealed interface DependencyFile {
    val isAutoAddedDocumentation: Boolean
    val isDocumentation: Boolean
    val extension: String
    val path: Path?
    val kmpSourceSet: String?
    val kmpPlatforms: Set<ResolutionPlatform>?
}

open class DependencyFileImpl(
    val dependency: MavenDependencyImpl,
    val nameWithoutExtension: String,
    override val extension: String,
    override val isDocumentation: Boolean = false,
    override val isAutoAddedDocumentation: Boolean = false,
    private val fileCache: FileCache = dependency.settings.fileCache,
): DependencyFile {
    val settings = TypedKeyMap()

    override val kmpSourceSet = settings[KmpSourceSetName]
    override val kmpPlatforms = settings[KmpPlatforms]

    @Volatile
    private var readOnlyExternalCacheDirectory: LocalRepository? = null
    private val mutex = Mutex()

    internal val fileName = "$nameWithoutExtension.$extension"

    private fun getCacheDirectory(): LocalRepository = fileCache.localRepository

    private suspend fun getMavenLocalPath(): Path? {
        if (!dependency.settings.repositories.contains(MavenLocal))
            return null

        return fileCache.mavenLocalRepository.guessPath(dependency, fileName).takeIf {
            // Dependencies are published to mavenLocal without checksums by default
            // (neither by Gradle plugin nor by 'mvn install')
            isValidMavenLocalPath(it)
                    /*&& getDependencyFile(
                dependency, nameWithoutExtension, extension, isAutoAddedDocumentation,
                // todo (AB) : Check that ERROR diagnostic bout non-matching checksum in maven Local is suppressed in case dependency was resolved from some external repo)
                fileCache.copy(localRepository = mavenLocal)
            ).hasMatchingChecksumLocally(
                diagnosticsReporter, dependency.settings, it, ResolutionLevel.LOCAL
            )*/
        }
    }

    protected open suspend fun isValidMavenLocalPath(path: Path) = path.exists()

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

    override val path: Path?
        get() = getResolvedPath()
            // runBlocking could be called for the first time only until the path is not resolved yet.
            // Calling path should be avoided from inside the resolution process. it is here to comply with the base interface.
            // Use suspendable getPath() instead.
            ?: runBlocking { getPath() } // todo (AB) : replace with error?

    @Volatile
    private var downloadedFilePath: Path? = null

    @Volatile
    private var guessedCacheLocalPath: Path? = null

    @Volatile
    internal var mavenLocalPath: Path? = null

    suspend fun getPath(): Path? = getResolvedPath()
        ?: withContext(Dispatchers.IO) { resolvePath() }

    private suspend fun resolvePath(): Path? = getResolvedPath()
        ?: run {
            // resolve the file from mavenLocal if possible
            // (if mavenLocal is among a repository list + a file exists,
            //  checksums from mavenLocal are ignored, since both Gradle and maven don't publish checksums to mavenLocal)
            val mavenLocalPath = getMavenLocalPath()
            val cacheLocalPath = getCacheDirectory().guessPath(dependency, fileName)

            if (mavenLocalPath != null
                && (cacheLocalPath == null
                        || !cacheLocalPath.exists()
                        || mavenLocalPath.getLastModifiedTime() > cacheLocalPath.getLastModifiedTime())) {
                mavenLocalPath.also { this.mavenLocalPath = it }
            } else {
                cacheLocalPath?.also { this.guessedCacheLocalPath = it } }
            }

    private fun getResolvedPath(): Path? = downloadedFilePath
            // a verified location in mavenLocal was detected and set => reuse it
            ?: mavenLocalPath
            // a location in Amper local storage was calculated and set => reuse it (at this point artifact can't be resolved from mavenLocal)
            ?: guessedCacheLocalPath

    override fun toString(): String = getResolvedPath()?.toString() ?: "[not yet resolved path]/$fileName"

    private suspend fun hasMatchingChecksumLocally(
        diagnosticsReporter: DiagnosticReporter = this.diagnosticsReporter,
        settings: Settings,
        level: ResolutionLevel = ResolutionLevel.NETWORK,
    ): Boolean =
        if (mavenLocalPath != null) { // todo (AB) : Move to upper level?
            // skipping verification of artifacts resolved from mavenLocal (it was verified already)
            true
        } else {
            getPath()?.let {
                hasMatchingChecksumLocally(
                    diagnosticsReporter, settings, it, level
                )
            } ?: false
        }

    // todo (AB) : Move it to companion object
    private suspend fun hasMatchingChecksumLocally(
        diagnosticsReporter: DiagnosticReporter = this.diagnosticsReporter,
        settings: Settings,
        filePath: Path,
        level: ResolutionLevel = ResolutionLevel.NETWORK,
    ): Boolean {
        return settings.spanBuilder("hasMatchingChecksumLocally")
            .setAttribute("fileName", fileName)
            .use {
                val computedHashes = settings.spanBuilder("Computing hashes").use { filePath.computeHash() }

                val result = settings.spanBuilder("verifyHashes").use {
                    verifyHashes(computedHashes, diagnosticsReporter, level, settings)
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
        repositories: List<MavenRepository>,
        progress: Progress,
        cache: Cache,
        spanBuilderSource: SpanBuilderSource,
        verify: Boolean,
        diagnosticsReporter: DiagnosticReporter,
        fileLockSource: FileMutexGroup? = null,
    ): Path? {
        val expectedHashBeforeLocking = if (verify) {
            getExpectedHash(diagnosticsReporter, ResolutionLevel.NETWORK)
        } else null

        return try {
            produceResultWithDoubleLock(
                tempDir = getTempDir(),
                fileName,
                fileLockSource,
                getAlreadyProducedResult = {
                    if (verify) {
                        getPath().takeIf {
                            isDownloadedWithVerification {
                                expectedHashBeforeLocking
                                    ?: getExpectedHash(diagnosticsReporter,ResolutionLevel.NETWORK,)
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
                        // In this case, we should take actual checksum downloaded from the external repository
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
            val message = UnexpectedErrorOnDownload.asMessage(
                fileName,
                dependency,
                extra = DependencyResolutionBundle.message("extra.exception", t),
                exception = t,
            )
            logger.warn(message.message, t)
            diagnosticsReporter.addMessage(message)
            null
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
        this@DependencyFileImpl.getReadOnlyCacheDirectory()
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

    protected open suspend fun isDownloaded(): Boolean = getPath()?.exists() == true

    internal open suspend fun isDownloadedWithVerification(
        level: ResolutionLevel = ResolutionLevel.NETWORK,
        settings: Settings = dependency.settings,
        diagnosticsReporter: DiagnosticReporter = this.diagnosticsReporter
    ): Boolean = isDownloaded() && when {
        isChecksum() -> readText().sanitize() != null
        else -> hasMatchingChecksumLocally(diagnosticsReporter, settings, level)
    }

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
        fileLockSource: FileMutexGroup? = null,
    ): Boolean {
        val nestedDownloadReporter = CollectingDiagnosticReporter()

        val path = downloadUnderFileLock(repositories.filterIsInstance<MavenRepository>(), progress, cache, spanBuilderSource, verify, nestedDownloadReporter, fileLockSource)
        val collectedMessages = nestedDownloadReporter.getMessages()

        val messages = when {
            path != null -> collectedMessages.takeIf { messages -> messages.all { it.severity <= Severity.INFO } }
                ?: emptyList()

            verify -> {
                if (collectedMessages.singleOrNull() is UnableToDownloadChecksums) {
                    collectedMessages
                } else {
                    listOf(
                        UnableToDownloadFile(
                            fileName = fileName,
                            coordinates = dependency.coordinates,
                            repositories = repositories,
                            isAutoAddedDocumentation = isAutoAddedDocumentation,
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

    private inline fun <reified T:Repository> List<T>.ensureFirst(repository: Repository?) =
        repository?.let {
            if (this.isEmpty() || repository !is T || this[0] == repository || !this.contains(repository))
                this
            else
                buildList {
                    add(repository)
                    addAll(this@ensureFirst - repository)
                }
        } ?: this

    private suspend fun downloadAndVerifyHash(
        channel: FileChannel,
        temp: Path,
        repositories: List<MavenRepository>,
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
        repository: MavenRepository?,
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
                    withRetry(retryOnException = { it is AccessDeniedException }) {
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

        diagnosticsReporter.suppress { suppressedMessages ->
            if (repository != null) {
                SuccessfulDownload.asMessage(target.name, repository, childMessages = suppressedMessages)
            } else {
                SuccessfulLocalResolution.asMessage(target.name, childMessages = suppressedMessages)
            }
        }

        return target
    }

    protected open suspend fun shouldOverwrite(
        cache: Cache,
        expectedHash: Hash,
        actualHash: Hash,
    ): Boolean = checkHash(actualHash, expectedHash) > VerificationResult.PASSED

    /**
     * Check that the hashes of this artifact file match the expected ones taken from the local artifact storage.
     * The actual hashes of the artifact file are computed using the given [hashers].
     */
    private suspend fun verifyHashes(
        hashers: Collection<Hash>,
        diagnosticsReporter: DiagnosticReporter,
        requestedLevel: ResolutionLevel,
        settings: Settings,
    ): VerificationResult {
        for (hasher in hashers) {
            val algorithm = hasher.algorithm
            val expectedHash = settings.spanBuilder("getExpectedHash").use { getExpectedHash(algorithm, settings) } ?: continue
            return checkHash(
                hasher,
                expectedHash = SimpleHash(hash = expectedHash, algorithm = algorithm),
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
        repositories: List<MavenRepository>,
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
                return SimpleHash(hash = expectedHash, algorithm = algorithm)
            }
        }

        diagnosticsReporter.addMessage(
            UnableToDownloadChecksums(
                fileName,
                coordinates = dependency.coordinates,
                repositories,
                isAutoAddedDocumentation = isAutoAddedDocumentation,
                childMessages = nestedDownloadReporter.getMessages(),
            )
        )

        return null
    }

    private fun Hash.isWellKnownBrokenHashIn(repository: String): Boolean {
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
     * But sometimes checksums declared in the metadata file are invalid, and in this case, DR downloads checksum files and
     * uses them for verification.
     * This way, the checksum file is presented in local storage only in case metadata contains invalid data or is completely missing.
     */
    internal suspend fun getExpectedHash(algorithm: String, settings: Settings, searchInMetadata: Boolean = true): String? =
        LocalStorageHashSource.getExpectedHash(this, algorithm, settings, searchInMetadata)

    /**
     * Downloads hash of a particular type from one of the specified repositories.
     * Returns <code>null</code> if the hash was not found.
     */
    internal suspend fun downloadHash(
        algorithm: String,
        repositories: List<MavenRepository>,
        progress: Progress,
        cache: Cache,
        spanBuilderSource: SpanBuilderSource,
        diagnosticsReporter: DiagnosticReporter,
    ): String? {
        val hashFile = getHashDependencyFile(algorithm)

        for (repository in repositories) {
            // We need to iterate repositories one by one, because some invalid repo could return some invalid response
            // that will be validated and sanitized, in this case we should try other repositories
            val expectedHashPath = hashFile.takeIf { it.isDownloadedValidHash() }?.getPath()
                ?: produceResultWithTempFile(
                    tempDir = getTempDir(),
                    targetFileName = hashFile.fileName,
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
        repository: MavenRepository,
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
            ) { attempt ->
                spanBuilderSource("downloadAttempt")
                    .setAttribute("dependency", dependency.toString())
                    .setAttribute("attempt", attempt.toLong())
                    .setAttribute("repository", repository.url)
                    .setAttribute("fileName", fileName)
                    .setAttribute("url", url)
                    .use {
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
                                        // todo (AB) : It might be useful to use here a dedicated limited dispatcher based on Dispatchers.IO
                                        val size = responseBody.toByteReadChannel().readTo(writers)
//                                val size = responseBody.readTo(writers)

                                        val isSuccessfullyDownloaded =
                                            if (expectedSize != null && size != expectedSize) {
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
                    exception = e
                )
            )
        }

        return false
    }

    private fun Builder.withBasicAuth(repository: MavenRepository): Builder = also {
        if (repository.userName != null && repository.password != null) {
            header("Authorization", getBasicAuthenticationHeader(repository.userName, repository.password))
        }
    }

    private fun getBasicAuthenticationHeader(username: String, password: String): String {
        val valueToEncode = "$username:$password"
        return "Basic " + Base64.getEncoder().encodeToString(valueToEncode.toByteArray())
    }

    internal open suspend fun getNamePart(
        repository: MavenRepository,
        name: String,
        extension: String,
        progress: Progress,
        cache: Cache,
        spanBuilderSource: SpanBuilderSource,
        diagnosticsReporter: DiagnosticReporter,
    ) =
        "$name.$extension"

    internal open suspend fun onFileDownloaded(target: Path, repository: MavenRepository? = null) {
        this.downloadedFilePath = target
        this.dependency.repository = repository
    }

    private enum class LocalStorageHashSource {
        File {
            override suspend fun getExpectedHash(artifact: DependencyFileImpl, algorithm: String, settings: Settings) =
                settings.spanBuilder("getHashFromGradleCacheDirectory").use { artifact.getHashFromGradleCacheDirectory(algorithm) }
                    ?: settings.spanBuilder("getDependencyFile").use {
                        artifact.getHashDependencyFile(algorithm)
                    }
                            .takeIf { file -> settings.spanBuilder("DependencyFile.isDownloaded").use { file.isDownloaded() }}
                            ?.let { file -> settings.spanBuilder("readText").use { file.readText() } }
                            ?.sanitize()
        },
        MetadataInfo {
            override suspend fun getExpectedHash(artifact: DependencyFileImpl, algorithm: String, settings: Settings) =
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

        protected abstract suspend fun getExpectedHash(artifact: DependencyFileImpl, algorithm: String, settings: Settings): String?

        private suspend fun getExpectedHash(artifact: DependencyFileImpl, settings: Settings): Hash? {
            for (hashAlgorithm in hashAlgorithms) {
                val expectedHash = getExpectedHash(artifact, hashAlgorithm, settings)
                if (expectedHash != null) {
                    return SimpleHash(hash = expectedHash, algorithm = hashAlgorithm)
                }
            }
            return null
        }

        companion object {
            suspend fun getExpectedHash(artifact: DependencyFileImpl, algorithm: String, settings: Settings, searchInMetadata: Boolean) =
                settings.spanBuilder(" File.getExpectedHash").use {
                    File.getExpectedHash(artifact, algorithm, settings)
                }
                    ?: if (searchInMetadata) settings.spanBuilder("MetadataInfo.getExpectedHash").use {
                        MetadataInfo.getExpectedHash(artifact, algorithm, settings)
                    } else null

            /**
             * @return hash of the artifact resolved from a local artifacts storage
             */
            suspend fun getExpectedHash(artifact: DependencyFileImpl, searchInMetadata: Boolean = true): Hash? =
                // todo (AB) : Remove this empty Context {}
                File.getExpectedHash(artifact, Context {}.settings)
                    ?: if (searchInMetadata) MetadataInfo.getExpectedHash(artifact, Context {}.settings) else null
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
                            actualHash.hash
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

class SnapshotDependencyFileImpl(
    dependency: MavenDependencyImpl,
    name: String,
    extension: String,
    fileCache: FileCache = dependency.settings.fileCache,
    isDocumentation: Boolean = false,
    isAutoAddedDocumentation: Boolean = false,
) : DependencyFileImpl(
    dependency,
    name,
    extension,
    fileCache = fileCache,
    isDocumentation = isDocumentation,
    isAutoAddedDocumentation = isAutoAddedDocumentation,
) {

    private val mavenMetadata by lazy {
        SnapshotDependencyFileImpl(dependency, "maven-metadata", "xml", FileCacheBuilder {
            amperCache = fileCache.amperCache
            localRepository = MavenLocalRepository(fileCache.amperCache.resolve("caches/maven-metadata"))
        }.build())
    }

    private suspend fun getVersionFile() = mavenMetadata.getPath()?.parent?.resolve("$extension.version")

    private suspend fun getChecksumFile(algorithm: String) = mavenMetadata.getPath()?.parent?.resolve("$extension.$algorithm") // pom -> pom.sha256

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

    /**
     * @return file expiration time: the moment in time after which
     * this file corresponding to the SNAPSHOT version of maven dependency is no longer up to date
     * and should be refreshed.
     */
    internal suspend fun getExpirationTime(): Instant? {
        val path = getPath()
        if (path?.exists() != true) {
            return null
        }

        // Any SNAPSHOT dependency resolved from mavenLocal should be re-resolved on every usage,
        // because it could be updated at any time and that change is intended to affect the resolution graph immediately
        // (primary use case for the usage of mavenLocal).
        // This way expiration time is set as 'now' indicating that SNAPSHOT dependency should be re-resolved the next time again
        if (mavenLocalPath != null) return Clock.System.now()

        return if (nameWithoutExtension == "maven-metadata")
            // maven-metadata file is the primary source of information about SNAPSHOT dependency versions,
            // its expiration time is calculated very straightforwardly as an expiration time of the file itself.
            path.expirationTime()
        else {
            // downloadable resource (artifact or checksum or documentation)
            if (mavenMetadata.isDownloaded())
                // If maven-metadata is there, then its expiration time is the expiration time
                // of all other downloadable resources of this library
                mavenMetadata.getPath()?.expirationTime()
            else {
                // maven-metadata is missing, we rely on checksums expiration time
                // (checksum is redownloaded after expiration time passed,
                // but not the artifact, that is just reused if it matches redownloaded checksum, keeping its own expiration time outdated)
                if (isChecksum())
                    path.expirationTime()
                else
                    // find the last downloaded checksum and use its expiration time
                    hashAlgorithms
                        .mapNotNull { getChecksumFile(it)?.expirationTime() }
                        .maxByOrNull { it }
            }
        }
    }

    private suspend fun isSnapshotDownloaded(): Boolean {
        val path = getPath()
        if (path?.exists() != true) {
            return false
        }

        if (mavenLocalPath != null) return true // Resolved from mavenLocal => no further checks are required

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

    private fun Path.isUpToDate(): Boolean = expirationTime() > Clock.System.now()

    private fun Path.expirationTime(): Instant = getLastModifiedTime().toInstant().toKotlinInstant().plus(snapshotValidityPeriod)

    override suspend fun getNamePart(
        repository: MavenRepository,
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
        repository: MavenRepository, progress: Progress, cache: Cache, spanBuilderSource: SpanBuilderSource, diagnosticsReporter: DiagnosticReporter,
    ): Boolean {
        return mavenMetadata.isDownloaded()
                || mavenMetadata
            .download(listOf(repository), progress, cache, spanBuilderSource, verify = false, diagnosticsReporter, mavenMetadataFilesLock)
    }

    override suspend fun shouldOverwrite(cache: Cache, expectedHash: Hash, actualHash: Hash): Boolean =
        nameWithoutExtension == "maven-metadata"
                || getVersionFile()?.takeIf { it.exists() }?.readText() != getSnapshotVersion()
                || super.shouldOverwrite(cache, expectedHash, actualHash)

    override suspend fun onFileDownloaded(target: Path, repository: MavenRepository?) {
        super.onFileDownloaded(target, repository)
        if (nameWithoutExtension != "maven-metadata") {
            getSnapshotVersion()?.let { getVersionFile()?.writeText(it) }
        }
    }

    override suspend fun isValidMavenLocalPath(path: Path): Boolean =
        super.isValidMavenLocalPath(path) && isRegisteredInMavenLocalMetadata(path)

    /**
     * Checks that descriptor file 'maven-metadata-local.xml' exists,
     *  and the actual lasModified time of the file from mavenLocal matches the one taken from the descriptor
     */
    private suspend fun isRegisteredInMavenLocalMetadata(path: Path): Boolean {
        return withContext(Dispatchers.IO) {
            val mavenMetadataLocalPath = path.parent.resolve("maven-metadata-local.xml")
            if (!mavenMetadataLocalPath.exists()) return@withContext false

            val mavenMetadataLocal = try {
                mavenMetadataLocalPath.readTextWithRetry().parseMetadata()
            } catch (e: AmperDependencyResolutionException) {
                logger.error("Failed to parse ${mavenMetadataLocalPath.toAbsolutePath()}", e)
                return@withContext false
            }

            if (mavenMetadataLocal.versioning.snapshot.localCopy != true) return@withContext false

            val lastUpdated = mavenMetadataLocal.versioning.snapshotVersions?.snapshotVersions
                ?.find {
                    it.extension == extension.substringBefore('.')                                           // pom.sha512 -> pom
                            && (it.classifier == null || nameWithoutExtension.endsWith("-${it.classifier}"))
                }
                ?.updated
                ?: return@withContext false

            // The timestamp is expressed using UTC in the format yyyyMMddHHmmss.
            val pattern = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            val lastUpdateFromMavenMetadata = try {
                LocalDateTime.parse(lastUpdated, pattern)
            } catch (e: DateTimeParseException) {
                logger.error("Failed to parse value of property updated read from maven-metadata-local.xml", e)
                return@withContext false
            }

            val actualLastUpdated = LocalDateTime.ofInstant(path.getLastModifiedTime().toInstant(), ZoneId.of("UTC"))
            val actualLastUpdatedSeconds = LocalDateTime.of(
                actualLastUpdated.year, actualLastUpdated.monthValue, actualLastUpdated.dayOfMonth,
                actualLastUpdated.hour, actualLastUpdated.minute, actualLastUpdated.second
            )

            actualLastUpdatedSeconds <= lastUpdateFromMavenMetadata
        }
    }

    companion object {
        /**
         * maven-metadata.xml is downloaded on demand when downloading of the artifact (pom or module or jar)
         * is in progress, and file lock is already taken.
         * This lock source is used for nested locking under the main artifact lock.
         * It is safe since it is always ordered, it is taken under the artifact's lock, not vice versa.
         * It protects from concurrent attempts to download maven-metadata
         * while downloading pom/module/different artifacts in parallel
         */
        private val mavenMetadataFilesLock = FileMutexGroup.striped(stripeCount = 512)

        val snapshotValidityPeriod = 1.days
    }
}

internal fun getNameWithoutExtension(node: MavenDependency): String = "${node.module}-${node.version.orUnspecified()}"

private fun fileFromVariant(dependency: MavenDependencyImpl, name: String) =
    dependency.variants.flatMap { it.files }.singleOrNull { it.name == name }

internal suspend fun Path.computeHash(): Collection<Hash> = computeHash(this) { createHashers() }

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

internal inline fun <T> resolveSafeOrNull(block: () -> T?): T? {
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
 * it is the responsibility of the calling side that provides the custom client to handle its lifecycle.
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
     * This is unsued now but left for the reference for the future.
     * It might be a good practice to reinitialize HTTP Client from time to time after an idle period for
     * the connection leak prophylactics,
     * and in that case, the old instance of a client should be properly shutdown.
     */
    private fun closeHttpClient() {
        val client = this.client
        this.client = null
        try {
            // In java 21, HttpClient is AutoClosable,
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
