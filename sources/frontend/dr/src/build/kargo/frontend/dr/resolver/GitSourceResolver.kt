package build.kargo.frontend.dr.resolver

import build.kargo.frontend.schema.GitSource
import build.kargo.frontend.schema.GitSourceCloner
import build.kargo.frontend.schema.GitSourceException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.amper.frontend.Platform
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.io.path.*

/**
 * Resolves Git-backed sources by cloning, checking out specific versions,
 * and building them locally using Kargo.
 *
 * Uses [GitSourceCloner] for the clone/checkout step.
 */
class GitSourceResolver(
    private val cacheRoot: Path = Path(System.getProperty("user.home")).resolve(".kargo/sources-cache")
) {
    private val cloner = GitSourceCloner(cacheRoot)
    // Per-cache-key locks to prevent concurrent clones to the same directory
    private val resolveLocks = ConcurrentHashMap<String, ReentrantLock>()

    /**
     * Resolves a Git source to a set of built artifacts.
     *
     * @param source The Git source configuration
     * @param platforms Target platforms to build
     * @return Paths to the built artifacts (.klib files)
     */
    fun resolve(source: GitSource, platforms: List<Platform>): List<Path> {
        val repoUrl = cloner.extractRepositoryUrl(source)
        val sourceName = cloner.extractSourceName(source)
        val version = source.version.toString()
        val subPath = source.path
        val cacheKey = cloner.generateCacheKey(repoUrl, version)
        val cacheDir = cacheRoot.resolve(cacheKey)

        val lock = resolveLocks.computeIfAbsent(cacheKey) { ReentrantLock() }
        lock.lock()
        try {
            if (isCached(cacheDir)) {
                if (isMutableRef(version)) {
                    // Check if branch advanced upstream
                    val remoteSha = fetchRemoteSha(repoUrl, version)
                    val storedSha = readResolvedCommit(cacheDir)
                    when {
                        remoteSha == null ->
                            logger.warn("Git source '$sourceName' ($version): offline, using cached build.")
                        remoteSha != storedSha -> {
                            logger.info("Git source '$sourceName' ($version) updated upstream, rebuilding...")
                            invalidateCache(cacheDir)
                        }
                        else -> logger.debug("Using cached git source '$sourceName' ($version)")
                    }
                    if (isCached(cacheDir)) return collectCachedArtifacts(cacheDir)
                } else {
                    logger.debug("Using cached git source '$sourceName' ($version)")
                    return collectCachedArtifacts(cacheDir)
                }
            }

            logger.info("Fetching git source '$sourceName' ($version)...")
            val repoDir = try {
                cloner.cloneOrUpdate(repoUrl, cacheDir.resolve("repo"))
            } catch (e: GitSourceException) {
                throw e
            } catch (e: Exception) {
                throw GitSourceException("Failed to fetch git source '$sourceName' from $repoUrl", details = e.message, cause = e)
            }

            logger.info("Checking out git source '$sourceName' ($version)...")
            try {
                cloner.checkout(repoDir, version)
            } catch (e: Exception) {
                throw GitSourceException(
                    "Failed to checkout version '$version' for git source '$sourceName'",
                    details = "Repository: $repoUrl\nVersion: $version\n${e.message}",
                    cause = e
                )
            }

            // Resolve the real commit SHA after checkout (local op, no network)
            val resolvedSha = resolveCurrentSha(repoDir)

            val projectDir = if (subPath != null) repoDir.resolve(subPath) else repoDir

            logger.info("Building git source '$sourceName'...")
            val builtArtifacts = buildSource(projectDir, platforms, sourceName)

            // Copy artifacts to cache dir so isCached() returns true on subsequent builds
            val cachedArtifacts = storeArtifacts(cacheDir, builtArtifacts)
            storeMetadata(cacheDir, repoUrl, version, resolvedSha, platforms)

            logger.info("Installed git source '$sourceName' (${cachedArtifacts.size} artifact(s))")
            return cachedArtifacts
        } finally {
            lock.unlock()
        }
    }

    /**
     * Builds the source using Kargo CLI.
     */
    private fun buildSource(projectDir: Path, platforms: List<Platform>, sourceName: String): List<Path> {
        val moduleFile = projectDir.resolve("module.yaml")
        if (!moduleFile.exists()) throw GitSourceException(
            "Git source '$sourceName' is not a valid Kargo project",
            details = "No module.yaml found in ${projectDir.absolute()}\nGit sources must contain a valid module.yaml file."
        )
        val buildDir = projectDir.resolve("build")
        try {
            executeKargoBuild(projectDir, platforms)
        } catch (e: Exception) {
            throw GitSourceException("Failed to build git source '$sourceName'", details = "Project directory: ${projectDir.absolute()}\n${e.message}", cause = e)
        }
        return collectArtifacts(buildDir)
    }

    private fun executeKargoBuild(projectDir: Path, platforms: List<Platform>) {
        val kargoCli = findKargoCli(projectDir)
        val process = ProcessBuilder(kargoCli.absolutePathString(), "build")
            .directory(projectDir.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) error("Kargo build failed in ${projectDir.absolute()}\nOutput: $output")
    }

    private fun findKargoCli(projectDir: Path): Path {
        return when {
            projectDir.resolve("kargo").exists() -> Path(projectDir.absolutePathString(), "./kargo")
            projectDir.resolve("amper").exists() -> Path(projectDir.absolutePathString(), "./amper")
            else -> throw GitSourceException(
                "Kargo CLI not found in git source project",
                details = "Expected 'kargo' or 'amper' executable in ${projectDir.absolute()}\nEnsure that the project contains the Kargo CLI for building."
            )
        }
    }

    private fun collectArtifacts(buildDir: Path): List<Path> {
        val artifacts = if (buildDir.exists()) buildDir.walk().filter { it.extension == "klib" }.toMutableList() else mutableListOf()
        if (artifacts.isEmpty()) throw GitSourceException(
            "No artifacts produced by git source build",
            details = "Build directory: ${buildDir.absolute()}\nExpected .klib files but none were found."
        )
        return artifacts
    }

    private fun collectCachedArtifacts(cacheDir: Path): List<Path> =
        cacheDir.resolve("artifacts").listDirectoryEntries("*.klib")

    private fun storeArtifacts(cacheDir: Path, builtArtifacts: List<Path>): List<Path> {
        val artifactsDir = cacheDir.resolve("artifacts")
        artifactsDir.createDirectories()
        return builtArtifacts.map { artifact ->
            val dest = artifactsDir.resolve(artifact.name)
            artifact.copyTo(dest, overwrite = true)
            dest
        }
    }

    private fun isCached(cacheDir: Path): Boolean {
        val artifactsDir = cacheDir.resolve("artifacts")
        return cacheDir.resolve(METADATA_FILE_NAME).exists()
            && artifactsDir.exists()
            && artifactsDir.listDirectoryEntries("*.klib").isNotEmpty()
    }

    /** Fetches the current SHA for [version] from the remote. Returns null if offline or on error. */
    private fun fetchRemoteSha(repoUrl: String, version: String): String? = try {
        val output = cloner.executeGitCommand(cacheRoot.also { it.createDirectories() }, "ls-remote", repoUrl, version)
        output.trim().substringBefore("\t").takeIf { isCommitSha(it) }
    } catch (_: Exception) { null }

    /** Resolves the current HEAD commit SHA from a local repo (no network). */
    private fun resolveCurrentSha(repoDir: Path): String =
        cloner.executeGitCommand(repoDir, "rev-parse", "HEAD").trim()

    private fun readResolvedCommit(cacheDir: Path): String? {
        val metadataFile = cacheDir.resolve(METADATA_FILE_NAME)
        if (!metadataFile.exists()) return null
        return try {
            val metadata = json.decodeFromString<GitSourceMetadata>(metadataFile.readText())
            metadata.resolvedCommit
        } catch (e: Exception) {
            null
        }
    }

    private fun invalidateCache(cacheDir: Path) {
        cacheDir.resolve("artifacts").toFile().deleteRecursively()
        cacheDir.resolve(METADATA_FILE_NAME).deleteIfExists()
    }

    private fun storeMetadata(cacheDir: Path, repoUrl: String, originalVersion: String, resolvedCommit: String, platforms: List<Platform>) {
        cacheDir.createDirectories()
        val metadata = GitSourceMetadata(
            repositoryUrl = repoUrl,
            originalVersion = originalVersion,
            resolvedCommit = resolvedCommit,
            platforms = platforms.map { it.name },
            buildTimestamp = System.currentTimeMillis()
        )
        cacheDir.resolve(METADATA_FILE_NAME).writeText(json.encodeToString(metadata))
    }

    /** Returns true if [version] is a branch or tag name rather than a full commit SHA. */
    private fun isMutableRef(version: String): Boolean = !isCommitSha(version) && !isSemverTag(version)

    private fun isCommitSha(version: String) = version.matches(Regex("[0-9a-f]{40}"))

    // Matches v1.0.0, v2.3.1-beta, 1.4.0, etc.
    private fun isSemverTag(version: String) = version.matches(Regex("v?\\d+\\.\\d+.*"))

    companion object {
        private val logger = LoggerFactory.getLogger(GitSourceResolver::class.java)
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
        private const val METADATA_FILE_NAME = "metadata.json"
    }
}

@Serializable
internal data class GitSourceMetadata(
    val repositoryUrl: String,
    val originalVersion: String,
    val resolvedCommit: String,
    val platforms: List<String>,
    val buildTimestamp: Long
)
