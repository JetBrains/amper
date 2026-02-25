package build.kargo.frontend.dr.resolver

import build.kargo.frontend.schema.BitbucketSource
import build.kargo.frontend.schema.GitHubSource
import build.kargo.frontend.schema.GitLabSource
import build.kargo.frontend.schema.GitSource
import org.jetbrains.amper.frontend.GitSourcesModulePart
import build.kargo.frontend.schema.GitUrlSource
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Extension point for processing Git sources before dependency resolution.
 *
 * This should be called early in the build pipeline, before Maven dependency
 * resolution, to ensure Git sources are built and artifacts are available.
 */
object GitSourcesExtension {

    private val artifactCache = ConcurrentHashMap<String, List<GitSourceArtifact>>()
    private val cloner = build.kargo.frontend.schema.GitSourceCloner()
    private val resolver = GitSourceResolver()

    /**
     * Process Git sources for a module and cache the results.
     *
     * @param module The Amper module with potential Git sources
     * @param targetPlatforms The platforms to build for
     * @return List of built artifacts
     */
    fun processModuleGitSources(
        module: AmperModule,
        targetPlatforms: List<Platform>
    ): List<GitSourceArtifact> {
        val cacheKey = "${module.userReadableName}-${targetPlatforms.hashCode()}"

        return artifactCache.getOrPut(cacheKey) {
            val gitSources = getModuleGitSources(module)
            gitSources.flatMap { source ->
                processGitSource(source, targetPlatforms)
            }
        }
    }

    /**
     * Extracts Git sources from module configuration via [GitSourcesModulePart].
     */
    private fun getModuleGitSources(module: AmperModule): List<GitSource> {
        return module.parts
            .filterIsInstance<GitSourcesModulePart>()
            .firstOrNull()
            ?.gitSources
            ?: emptyList()
    }

    /**
     * Process a single Git source.
     */
    private fun processGitSource(
        source: GitSource,
        targetPlatforms: List<Platform>
    ): List<GitSourceArtifact> {
        val platforms = source.platforms?.map { it.value } ?: targetPlatforms

        if (source.publishOnly) {
            return emptyList()
        }

        val artifactPaths = resolver.resolve(source, platforms)

        return artifactPaths.map { path ->
            GitSourceArtifact(
                source = source,
                artifactPath = path,
                platform = determinePlatform(path, platforms)
            )
        }
    }

    /**
     * Determines the platform for a given artifact path.
     * This is a heuristic based on the artifact file name or path.
     */
    private fun determinePlatform(artifactPath: Path, candidatePlatforms: List<Platform>): Platform {
        // TODO: Implement proper platform detection from .klib metadata
        // For now, return the first platform
        return candidatePlatforms.firstOrNull() ?: Platform.COMMON
    }

    /**
     * Get artifact paths for a module (for compiler classpath injection).
     */
    fun getArtifactPaths(module: AmperModule): List<Path> {
        val allKeys = artifactCache.keys.filter { it.startsWith("${module.userReadableName}-") }
        return allKeys
            .flatMap { artifactCache[it] ?: emptyList() }
            .map { it.artifactPath }
    }

    /**
     * Clear the artifact cache (useful for testing).
     */
    fun clearCache() {
        artifactCache.clear()
    }
}

data class GitSourceArtifact(
    val source: GitSource,
    val artifactPath: Path,
    val platform: Platform
)
