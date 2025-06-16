/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.show

import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import org.jetbrains.amper.cli.commands.AmperSubcommand
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.dr.resolver.emptyContext
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.tasks.buildDependenciesGraph

internal class DependenciesCommand: AmperSubcommand(name = "dependencies") {

    private val module by option("-m", "--module",
        help = "Specific module to show dependencies of (run the `show modules` command to get the modules list)")

    private val platforms by option("-p", "--platforms",
        help = "Comma-separated list of platforms specifying a resolution scope to show dependencies for, if left unspecified, " +
                "dependencies for all module resolution scopes are shown."
    )

    private val includeTests by option("--include-tests",
        help = "Whether to include information about test dependencies or not, false by default")
        .flag(default = false)

    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Print the resolved dependencies graph of the module"

    override suspend fun run() {
        withBackend(commonOptions, commandName, terminal) { backend ->
            val terminal = backend.context.terminal

            val modules = backend.modules()

            val resolvedModule = module
                ?.let { backend.resolveModule(it) }
                ?: modules.singleOrNull()
                ?: userReadableError(
                    "There are several modules in the project. " +
                            "Please specify one with '--module' argument.\n\n" +
                            "Available application modules: ${backend.availableModulesString()}"
                )

            val platformSetsToResolveFor = platforms
                ?.let { listOf(resolvedModule.getModuleLeafPlatforms(it)) }
                ?: resolvedModule.fragments.map { it.platforms }.distinct()

            val variantsToResolve = buildList {
                platformSetsToResolveFor.forEach { platforms ->
                    listOfNotNull(false, true.takeIf { includeTests }).forEach { isTests ->
                        listOf(ResolutionScope.RUNTIME, ResolutionScope.COMPILE).forEach { scope ->
                            // todo (AB) : Maybe it is a good idea to show java-like COMPILE graph for native as well
                            // todo (AB) : (since it is used in Idea for symbol resolution)
                            if (platforms.size == 1 && platforms.single().isDescendantOf(Platform.NATIVE) && scope == ResolutionScope.RUNTIME)
                                return@forEach // compile and runtime dependencies are the same for native single-platform contexts
                            add(
                                resolvedModule.buildDependenciesGraph(
                                    isTests,
                                    platforms,
                                    scope,
                                    backend.context.userCacheRoot
                                )
                            )
                        }
                    }
                }
            }

            val resolver = MavenResolver(backend.context.userCacheRoot)

            val root = DependencyNodeHolder(
                name = "root",
                children = variantsToResolve,
                emptyContext(backend.context.userCacheRoot) { spanBuilder(it) }
            )

            resolver.resolve(
                root = root,
                resolveSourceMoniker = "module ${resolvedModule.userReadableName}",
            )

            terminal.println("Dependencies of module ${resolvedModule.userReadableName}: \n")

            root.children.forEach {
                terminal.println(it.prettyPrint())
            }
        }
    }

    /**
     * Get intersection of platforms specified in command line and leaf platforms of the module.
     * Report an error and exit immediately,
     * If some input platforms are either unresolved or absent among module target platforms.
     */
    private fun AmperModule.getModuleLeafPlatforms(
        platformsString: String,
    ): Set<Platform> {
        val moduleLeafPlatforms = leafPlatforms
        val platformNames = platformsString.split(",")

        val unresolvedPlatformNames = mutableListOf<String>()
        val notMatchingPlatforms = mutableListOf<Platform>()
        val resolvedLeafPlatforms = mutableListOf<Platform>()

        platformNames.forEach {
            val resolvedPlatform = Platform[it]
            if (resolvedPlatform == null) {
                unresolvedPlatformNames.add(it)
            } else {
                val leafPlatforms = Platform.naturalHierarchyExt[resolvedPlatform]
                    ?: error("Platform $resolvedPlatform ha no leaf platforms")
                val correspondingModuleLeafPlatforms = leafPlatforms.intersect(moduleLeafPlatforms)
                if (correspondingModuleLeafPlatforms.isEmpty()) {
                    notMatchingPlatforms.add(resolvedPlatform)
                } else {
                    resolvedLeafPlatforms.addAll(correspondingModuleLeafPlatforms)
                }
            }
        }
        if (unresolvedPlatformNames.isNotEmpty()) {
            userReadableError(
                "The following platforms are unresolved: ${unresolvedPlatformNames.joinToString()}.\n\n" +
                        "Module ${userReadableName} target platforms: ${moduleLeafPlatforms.joinToString { it.pretty }}"
            )
        }
        if (notMatchingPlatforms.isNotEmpty()) {
            userReadableError(
                "Module ${userReadableName} " +
                        "doesn't support platforms: ${notMatchingPlatforms.joinToString()}.\n"
            )
        }
        return resolvedLeafPlatforms.toSet()
    }
}