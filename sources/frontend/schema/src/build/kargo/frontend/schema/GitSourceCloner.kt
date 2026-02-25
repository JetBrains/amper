package build.kargo.frontend.schema

import build.kargo.frontend.schema.BitbucketSource
import build.kargo.frontend.schema.GitHubSource
import build.kargo.frontend.schema.GitLabSource
import build.kargo.frontend.schema.GitSource
import build.kargo.frontend.schema.GitUrlSource
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.io.path.*

/**
 * Handles Git repository cloning and checkout.
 * Lightweight: no build execution, no artifact collection.
 * Used by IDE sync to expose source directories for navigation.
 */
class GitSourceCloner(
    private val cacheRoot: Path = Path(System.getProperty("user.home")).resolve(".kargo/sources-cache")
) {
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    /**
     * Clones (or updates) the repository and checks out the given version.
     * Returns the path to the source directory (respecting [GitSource.path] if set).
     */
    fun resolveSourcesDir(source: GitSource): Path {
        val repoUrl = extractRepositoryUrl(source)
        val version = source.version.toString()
        val cacheKey = generateCacheKey(repoUrl, version)
        val repoDir = cacheRoot.resolve(cacheKey).resolve("repo")

        val lock = locks.computeIfAbsent(cacheKey) { ReentrantLock() }
        lock.lock()
        try {
            cloneOrUpdate(repoUrl, repoDir)
            checkout(repoDir, version)
            return if (source.path != null) repoDir.resolve(source.path) else repoDir
        } finally {
            lock.unlock()
        }
    }

    fun extractRepositoryUrl(source: GitSource): String = when (source) {
        is GitHubSource -> "https://github.com/${source.github}.git"
        is GitLabSource -> "https://gitlab.com/${source.gitlab}.git"
        is BitbucketSource -> "https://bitbucket.org/${source.bitbucket}.git"
        is GitUrlSource -> source.git
    }

    fun extractSourceName(source: GitSource): String = when (source) {
        is GitHubSource -> source.github
        is GitLabSource -> source.gitlab
        is BitbucketSource -> source.bitbucket
        is GitUrlSource -> source.git.substringAfterLast('/').removeSuffix(".git")
    }

    fun generateCacheKey(repoUrl: String, version: String): String {
        val repoName = repoUrl.substringAfterLast('/').removeSuffix(".git")
        val shortRef = version.take(7)
        return "$repoName/$shortRef"
    }

    fun cloneOrUpdate(repoUrl: String, targetDir: Path): Path {
        if (targetDir.exists()) {
            val gitDir = targetDir.resolve(".git")
            if (gitDir.exists() && gitDir.isDirectory()) {
                executeGitCommand(targetDir, "fetch", "--all", "--quiet")
            } else {
                targetDir.toFile().deleteRecursively()
                targetDir.parent.createDirectories()
                executeGitCommand(workingDir = targetDir.parent, "clone", "--quiet", repoUrl, targetDir.name)
            }
        } else {
            targetDir.parent.createDirectories()
            executeGitCommand(workingDir = targetDir.parent, "clone", "--quiet", repoUrl, targetDir.name)
        }
        return targetDir
    }

    fun checkout(repoDir: Path, ref: String) {
        executeGitCommand(repoDir, "checkout", ref)
    }

    fun executeGitCommand(workingDir: Path, vararg args: String): String {
        val process = ProcessBuilder("git", *args)
            .directory(workingDir.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) throw GitSourceException("Git command failed: git ${args.joinToString(" ")}", details = output.trim())
        return output
    }

}

/**
 * User-friendly exception for git source resolution failures.
 */
class GitSourceException(
    message: String,
    val details: String? = null,
    cause: Throwable? = null,
) : RuntimeException(buildString {
    appendLine()
    appendLine("  Git Source Error: $message")
    if (details != null) details.lines().forEach { appendLine("    $it") }
    appendLine()
}, cause)
