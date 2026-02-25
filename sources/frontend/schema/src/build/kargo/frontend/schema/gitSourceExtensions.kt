package build.kargo.frontend.schema

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.GitSourcesModulePart
import java.nio.file.Path

/**
 * Returns the local checkout directories for all Git sources declared in this module.
 * Used by [generatedSourceDirs] to expose Git source trees to the IntelliJ IDE for navigation.
 *
 * Failures are silently skipped so that IDE sync is not blocked by unavailable repos.
 */
internal fun AmperModule.gitSourceCheckoutDirs(): List<Path> {
    val gitSources = parts
        .filterIsInstance<GitSourcesModulePart>()
        .firstOrNull()
        ?.gitSources
        ?: return emptyList()

    val cloner = GitSourceCloner()
    return gitSources.mapNotNull { source ->
        runCatching { cloner.resolveSourcesDir(source) }.getOrNull()
    }
}
