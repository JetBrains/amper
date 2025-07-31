/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.show

import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.commands.AmperSubcommand
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.filterGraph
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

    private val filter by option("--filter",
        metavar = "groupId:artifactId",
        help = "Coordinates of a particular dependency from the module dependencies graph. " +
                "Only maven dependencies are supported at the moment in a format group:module. " +
                "group defines a maven library group and module defines maven library module" +
                "If the argument is specified, a subgraph of module dependencies that contains paths from root " +
                "to the specified dependency will be presented as a result. " +
                "If a dependency version is resolved based on a dependency constraint, " +
                "the path from the root to that constraint will be included in the resulting subgraph as well. "
    )

    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Print the resolved dependencies graph of the module"

    override suspend fun run() {
        withBackend(commonOptions, commandName, terminal) { backend ->
            val terminal = backend.context.terminal

            val resolvedModule = module.resolveModule(backend)

            val platformSetsToResolveFor = platforms
                ?.let { listOf(resolvedModule.getModuleLeafPlatforms(it)) }
                ?: resolvedModule.fragments.map { it.platforms }.distinct()

            val mavenCoordinates = filter?.resolveFilter()

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

            printDependencies(mavenCoordinates, terminal, resolvedModule, root, filter)
        }
    }

    private fun printDependencies(
        mavenCoordinates: MavenCoordinates?,
        terminal: Terminal,
        resolvedModule: AmperModule,
        root: DependencyNodeHolder,
        filter: String?,
    ) {
        if (mavenCoordinates == null) {
            terminal.println("Dependencies of module ${resolvedModule.userReadableName}: \n")
            root.children.forEach {
                terminal.println(it.prettyPrint())
            }
        } else {
            val rootsToPrint = root.children.mapNotNull {
                filterGraph(mavenCoordinates.groupId, mavenCoordinates.artifactId, it)
                    .takeIf { it.children.isNotEmpty() }
            }
            if (rootsToPrint.isEmpty()) {
                terminal.println("Module doesn't depend on $filter")
            } else {
                terminal.println("Subgraphs of module dependencies showing where dependency on the library '$filter' comes from: \n")
                rootsToPrint.forEach {
                    terminal.println(it.prettyPrint())
                }
            }
        }
    }

    private fun String.resolveFilter(): MavenCoordinates {
        val parts = this.split(":")
        if (parts.size != 2) userReadableError("Option 'filter' supports maven coordinates in the format 'group:module' only.")
        return MavenCoordinates(groupId = parts[0], artifactId = parts[1], version = null)
    }

    private fun String?.resolveModule(backend: AmperBackend): AmperModule {
        val modules = backend.modules()
        return this
            ?.let { backend.resolveModule(it) }
            ?: modules.singleOrNull()
            ?: userReadableError(
                "There are several modules in the project. " +
                        "Please specify one with '--module' argument.\n\n" +
                        "Available application modules: ${backend.availableModulesString()}"
            )
    }

    /**
     * Get intersection of platforms specified in command line and leaf platforms of the module.
     * Report an error and exit immediately,
     * if some input platforms are either unresolved or absent among module target platforms.
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