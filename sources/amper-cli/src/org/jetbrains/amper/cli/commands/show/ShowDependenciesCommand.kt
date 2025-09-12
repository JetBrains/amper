/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.show

import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.unique
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.commands.AmperModelAwareCommand
import org.jetbrains.amper.cli.commands.PlatformGroup
import org.jetbrains.amper.cli.commands.PlatformGroupOption
import org.jetbrains.amper.cli.commands.platformGroupOption
import org.jetbrains.amper.cli.commands.validLeavesIn
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.filterGraph
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.dr.resolver.emptyContext
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.tasks.buildDependenciesGraph

internal class ShowDependenciesCommand: AmperModelAwareCommand(name = "dependencies") {

    private val module by option("-m", "--module",
        help = "The module to show dependencies of. If unspecified, you will be prompted to choose one."
    )

    private val platformGroups by platformGroupOption(
        help = """
            The name of a platform, group of platforms, or alias that defines a resolution scope to show the 
            dependencies of.

            For example, `$PlatformGroupOption=native` shows the dependencies resolved for the sources that target
            all native platforms declared in the module: `src` and `src@native`.

            This option can be repeated to show the dependencies used in multiple resolution scopes.
            By default, the dependencies for all resolution scopes of the module are shown.
        """.trimIndent(),
    ).multiple().unique()

    private val includeTests by option("--include-tests",
        help = "Whether to include information about test dependencies or not, false by default"
    ).flag("--exclude-tests", default = false)

    private val filter by option("--filter",
        metavar = "groupId:artifactId",
        help = "Filter the dependency graph to only show paths that contain a specific dependency, in any version. " +
                "Only maven dependencies are supported at the moment, in the format `groupId:artifactId`. " +
                "If a dependency version is resolved based on a dependency constraint, the path from the root to " +
                "that constraint will be included in the resulting subgraph as well. "
    )

    override fun help(context: com.github.ajalt.clikt.core.Context): String = "Print the resolved dependencies graph of the module"

    override suspend fun run(cliContext: CliContext, model: Model) {
        val resolvedModule = resolveModuleIn(model.modules)

        val platformSetsToResolveFor = platformGroups
            .map { it.checkAndFilterLeaves(resolvedModule) }
            .ifEmpty { resolvedModule.fragments.map { it.platforms }.distinct() }

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
                                commonOptions.sharedCachesRoot,
                            )
                        )
                    }
                }
            }
        }

        val resolver = MavenResolver(commonOptions.sharedCachesRoot)

        val root = DependencyNodeHolder(
            name = "root",
            children = variantsToResolve,
            emptyContext(commonOptions.sharedCachesRoot) { spanBuilder(it) }
        )

        resolver.resolve(
            root = root,
            resolveSourceMoniker = "module ${resolvedModule.userReadableName}",
        )

        printDependencies(mavenCoordinates, resolvedModule, root, filter)
    }

    private fun printDependencies(
        mavenCoordinates: MavenCoordinates?,
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

    private fun resolveModuleIn(modules: List<AmperModule>): AmperModule {
        val resolvedModule = if (module != null) {
            modules.find { it.userReadableName == module } ?: userReadableError(
                "Unable to resolve module by name '$module'.\n\n" +
                        "Available modules:\n${modules.joinToString("\n") { it.userReadableName }}"
            )
        } else {
            modules.singleOrNull() ?: userReadableError(
                "There are several modules in the project. " +
                        "Please specify one with '--module' argument.\n\n" +
                        "Available modules:\n${modules.joinToString("\n") { it.userReadableName }}"
            )
        }
        return resolvedModule
    }

    /**
     * Returns the leaf platforms from this group that are present in the given [module]'s declared platforms.
     *
     * If any explicit leaf platform from this group is not declared in the [module], it is reported as an error.
     * If any intermediate platform from this group doesn't intersect with the [module] leaf platforms at all, it is
     * reported as an error.
     */
    private fun PlatformGroup.checkAndFilterLeaves(module: AmperModule): Set<Platform> {
        val validLeafPlatforms = validLeavesIn(module)
        if (validLeafPlatforms.isEmpty()) {
            // can never happen with an alias, so the wording is ok like this
            userReadableError("Module '${module.userReadableName}' doesn't support platform '$name'")
        }
        return validLeafPlatforms
    }
}
