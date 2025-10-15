/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("CONTEXT_RECEIVERS_DEPRECATED")

package org.jetbrains.amper.frontend.project

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import io.opentelemetry.api.common.Attributes
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.aomBuilder.readProject
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.catalogs.parseGradleVersionCatalog
import org.jetbrains.amper.frontend.diagnostics.UnresolvedModuleDeclaration
import org.jetbrains.amper.frontend.project.StandaloneAmperProjectContext.Companion.find
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Project
import org.jetbrains.amper.frontend.schema.UnscopedExternalMavenDependency
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.GlobalBuildProblemSource
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.NonIdealDiagnostic
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.amper.telemetry.useWithoutCoroutines
import java.nio.file.Path
import java.util.regex.PatternSyntaxException
import kotlin.io.path.createDirectories
import kotlin.io.path.relativeTo
import com.intellij.openapi.project.Project as IJProject

private val amperProjectFileNames = setOf("project.yaml", "project.amper")

@UsedInIdePlugin
class StandaloneAmperProjectContext(
    override val frontendPathResolver: FrontendPathResolver,
    override val projectRootDir: VirtualFile,
    projectBuildDir: Path?,
    override val amperModuleFiles: List<VirtualFile>,
    override val pluginsModuleFiles: List<VirtualFile>,
    override val externalMavenPluginDependencies: List<UnscopedExternalMavenDependency>,
) : AmperProjectContext {

    override val projectBuildDir: Path by lazy {
        (projectBuildDir ?: projectRootDir.toNioPath().resolve("build")).createDirectories()
    }

    override val projectVersionsCatalog: VersionCatalog? by lazy {
        val catalogFile = projectRootDir.findChild("libs.versions.toml")
            ?: projectRootDir.findChild("gradle")?.findChild("libs.versions.toml")
        catalogFile?.let { catalogFile ->
            frontendPathResolver.parseGradleVersionCatalog(catalogFile)
        }
    }

    companion object {
        /**
         * Finds an Amper project, starting at the given [start] directory or file, or returns null if no Amper project
         * is found.
         *
         * Conceptually, we first find the closest ancestor directory of [start] that contains a project file or a
         * module file. Then:
         * * If a project file is found, that's our root.
         * * If a module file is found, that's part of our project. In that case we check if a project file higher up
         * contains this module. If that's the case, then the project file defines the root. If not, then our module
         * file defines the root.
         * * If neither a project nor a module file are found, we don't have an Amper project and null is returned. The
         * caller is responsible for handling this situation as desired.
         *
         * The given IntelliJ [project] is used to access the VFS. If null, a mock project will be created.
         */
        context(_: ProblemReporter)
        @UsedInIdePlugin
        fun find(start: VirtualFile, project: IJProject? = null): AmperProjectContext? {
            val frontendPathResolver = FrontendPathResolver(project)
            return find(start, null, frontendPathResolver)
        }

        /**
         * Does the same as [find] above, but accepts [Path] that it resolves to [VirtualFile] beforehand.
         */
        context(_: ProblemReporter)
        fun find(
            start: Path,
            buildDir: Path?,
            project: IJProject? = null,
        ): AmperProjectContext? {
            val frontendPathResolver = FrontendPathResolver(project)
            val startVirtualFile = frontendPathResolver.loadVirtualFile(start)
            return find(startVirtualFile, buildDir, frontendPathResolver)
        }

        context(_: ProblemReporter)
        private fun find(
            virtualFile: VirtualFile,
            buildDir: Path?,
            frontendPathResolver: FrontendPathResolver,
        ): AmperProjectContext? {
            val result = preSearchProjectRoot(start = virtualFile) ?: return null

            val potentialContext = spanBuilder("Create candidate project context")
                .setAttribute("potential-root", result.potentialRoot.presentableUrl)
                .useWithoutCoroutines {
                    create(result.potentialRoot, buildDir, frontendPathResolver)
                        ?: error("potentialRoot should point to a valid project root")
                }

            if (result.startModuleFile != null && result.startModuleFile !in potentialContext.amperModuleFiles) {
                // We found a module file while going up, and the project file higher up doesn't include it.
                // This means that the potential context (defined by the project file) is not our actual context,
                // we're just a single module project.
                return StandaloneAmperProjectContext(
                    frontendPathResolver = frontendPathResolver,
                    projectRootDir = result.startModuleFile.parent,
                    projectBuildDir = buildDir,
                    amperModuleFiles = listOf(result.startModuleFile),
                    pluginsModuleFiles = emptyList(), // no plugins for the single-module project.
                    externalMavenPluginDependencies = emptyList(), // no maven plugins for the single-module project.
                )
            }
            return potentialContext
        }

        /**
         * Creates an [AmperProjectContext] for the specified [rootDir] based on the contained project or module file.
         * If there is no project file nor module file in the given directory, null is returned and the caller is
         * responsible for handling the situation (only the caller knows whether this is a valid situation).
         *
         * The given IntelliJ [project] is used to resolve virtual files and PSI files. If null, a mock project is
         * created.
         */
        context(_: ProblemReporter)
        fun create(
            rootDir: Path,
            buildDir: Path?,
            project: IJProject? = null,
        ): AmperProjectContext? {
            val pathResolver = FrontendPathResolver(project = project)
            return create(
                rootDir = pathResolver.loadVirtualFile(rootDir),
                buildDir = buildDir,
                frontendPathResolver = pathResolver,
            )
        }

        /**
         * Creates an [AmperProjectContext] for the specified [rootDir] based on the contained project or module file.
         * If there is no project file nor module file in the given directory, null is returned and the caller is
         * responsible for handling the situation (only the caller knows whether this is a valid situation).
         *
         * The given [frontendPathResolver] is used to resolve virtual files and PSI files.
         */
        context(problemReporter: ProblemReporter)
        @OptIn(NonIdealDiagnostic::class)
        internal fun create(
            rootDir: VirtualFile,
            buildDir: Path?,
            frontendPathResolver: FrontendPathResolver,
        ): AmperProjectContext? {
            val rootModuleFile = rootDir.findChildMatchingAnyOf(amperModuleFileNames)
            val amperProject = with(frontendPathResolver) { parseAmperProject(rootDir) }

            if (rootModuleFile == null && amperProject == null) {
                return null
            }

            val explicitProjectModuleFiles = amperProject?.modulePaths(rootDir) ?: emptyList()
            val amperModuleFiles = listOfNotNull(rootModuleFile) + explicitProjectModuleFiles
            if (amperModuleFiles.isEmpty()) {
                problemReporter.reportBundleError(
                    source = amperProject?.asBuildProblemSource() ?: GlobalBuildProblemSource,
                    messageKey = "project.has.no.modules",
                    rootDir.presentableUrl,
                    level = Level.Warning,
                )
            }

            val localPluginDependencies = amperProject?.plugins.orEmpty().mapNotNull { dependency ->
                val pluginModuleFile = frontendPathResolver.loadVirtualFileOrNull(dependency.value)
                    ?.findChildMatchingAnyOf(amperModuleFileNames)
                when (pluginModuleFile) {
                    null -> {
                        problemReporter.reportBundleError(
                            dependency.trace.asBuildProblemSource(), "plugin.dependency.not.found",
                            dependency.value.relativeTo(rootDir.toNioPath())
                        ); null
                    }
                    !in amperModuleFiles -> {
                        // TODO: Quickfix?
                        problemReporter.reportBundleError(
                            dependency.trace.asBuildProblemSource(), "plugin.dependency.not.included",
                            dependency.value.relativeTo(rootDir.toNioPath())
                        ); null
                    }
                    else -> pluginModuleFile
                }
            }

            return StandaloneAmperProjectContext(
                frontendPathResolver = frontendPathResolver,
                projectRootDir = rootDir,
                projectBuildDir = buildDir,
                amperModuleFiles = amperModuleFiles,
                pluginsModuleFiles = localPluginDependencies,
                externalMavenPluginDependencies = amperProject?.mavenPlugins.orEmpty(),
            )
        }
    }
}

private data class RootSearchResult(
    /**
     * A directory containing either a project file or a module file, and that is a potential root for the project.
     * It might be the root of an unrelated project higher in the file system hierarchy, so we still need to verify that
     * [startModuleFile], if present, is part of that project.
     */
    val potentialRoot: VirtualFile,
    /**
     * The first module file that was found while going up the file system hierarchy (if any), and which must be
     * included in the current project.
     * It defines the root of the project unless a project file is found higher up and includes this module.
     */
    val startModuleFile: VirtualFile?,
)

/**
 * Attempts to find the project root directory in an Amper project from the given [start] point.
 *
 * In practice, this function goes up the file hierarchy and looks for module files or a project file along the way.
 * The returned [RootSearchResult] is a record of what was found.
 * If neither a project file nor a module file are found, we don't have an Amper project and null is returned.
 */
private fun preSearchProjectRoot(start: VirtualFile): RootSearchResult? = spanBuilder("Pre-search project root")
    .setAttribute("start", start.path)
    .useWithoutCoroutines block@{ span ->
        var currentDir: VirtualFile? = if (start.isDirectory) start else start.parent
        var closestModuleFile: VirtualFile? = null
        while (currentDir != null) {
            if (currentDir.hasChildMatchingAnyOf(amperProjectFileNames)) {
                return@block RootSearchResult(potentialRoot = currentDir, startModuleFile = closestModuleFile)
            }
            if (closestModuleFile == null) {
                val moduleFile = currentDir.findChildMatchingAnyOf(amperModuleFileNames)
                if (moduleFile != null) {
                    span.addEvent(
                        "found start module file",
                        Attributes.builder().put("path", moduleFile.presentableUrl).build()
                    )
                    closestModuleFile = moduleFile
                }
            }
            currentDir = currentDir.parent
        }
        if (closestModuleFile != null) {
            return@block RootSearchResult(potentialRoot = closestModuleFile.parent, startModuleFile = closestModuleFile)
        }
        null // neither project nor module file found
    }

context(_: ProblemReporter, frontendPathResolver: FrontendPathResolver)
private fun parseAmperProject(projectRootDir: VirtualFile): Project? {
    val projectFile = projectRootDir.findChildMatchingAnyOf(amperProjectFileNames) ?: return null
    return spanBuilder("Parse Amper project file")
        .setAttribute("project-file", projectFile.path)
        .useWithoutCoroutines { readProject(frontendPathResolver, projectFile) }
}

context(_: ProblemReporter)
private fun Project.modulePaths(projectRootDir: VirtualFile): List<VirtualFile> =
    spanBuilder("Resolve module paths from project file")
        .setAttribute("module-count", modules.size.toString())
        .useWithoutCoroutines {
            modules
                .flatMap { modulePathOrGlob -> projectRootDir.resolveMatchingModuleFiles(modulePathOrGlob) }
                .distinct() // TODO report error/warning for duplicates
        }

context(_: ProblemReporter)
private fun VirtualFile.resolveMatchingModuleFiles(relativeModulePathOrGlob: TraceableString): List<VirtualFile> {
    // avoid walking the file tree if it's just a plain path (not a glob)
    if (!relativeModulePathOrGlob.value.hasGlobCharacters()) {
        return listOfNotNull(resolveModuleFileOrNull(relativeModulePathOrGlob))
    }
    return resolveModuleFilesRecursively(relativeModulePathOrGlob)
}

context(problemReporter: ProblemReporter)
private fun VirtualFile.resolveModuleFileOrNull(relativeModulePath: TraceableString): VirtualFile? {
    val moduleDir = findFileByRelativePath(relativeModulePath.value)
    if (moduleDir == null) {
        problemReporter.reportMessage(UnresolvedModuleDeclaration(relativeModulePath))
        return null
    }
    if (!moduleDir.isDirectory) {
        problemReporter.reportBundleError(
            source = relativeModulePath.asBuildProblemSource(),
            messageKey = "project.module.path.0.is.not.a.directory",
            relativeModulePath.value,
            level = Level.Error,
            problemType = BuildProblemType.TypeMismatch,
        )
        return null
    }
    val moduleFile = moduleDir.findChildMatchingAnyOf(amperModuleFileNames)
    if (moduleFile == null) {
        problemReporter.reportBundleError(
            source = relativeModulePath.asBuildProblemSource(),
            messageKey = "project.module.dir.0.has.no.module.file",
            relativeModulePath.value,
            level = Level.Error,
        )
        return null
    }
    if (moduleDir.url == url) {
        problemReporter.reportBundleError(
            source = relativeModulePath.asBuildProblemSource(),
            messageKey = "project.module.root.is.included.by.default",
            level = Level.WeakWarning,
            problemType = BuildProblemType.RedundantDeclaration,
        )
        return null
    }
    if (!VfsUtilCore.isAncestor(this, moduleDir, false)) {
        problemReporter.reportBundleError(
            source = relativeModulePath.asBuildProblemSource(),
            messageKey = "project.module.dir.0.is.not.under.root",
            relativeModulePath.value,
            level = Level.Error,
        )
        return null
    }
    return moduleFile
}

context(problemReporter: ProblemReporter)
private fun VirtualFile.resolveModuleFilesRecursively(moduleDirGlob: TraceableString): List<VirtualFile> {
    val moduleFileNamesCommaSeparated = amperModuleFileNames.joinToString(",")
    val moduleFilesGlobPattern = "${moduleDirGlob.value}/{$moduleFileNamesCommaSeparated}"
    val moduleFilesGlob = try {
        Glob(moduleFilesGlobPattern)
    } catch (_: PatternSyntaxException) {
        reportInvalidGlob(moduleDirGlob, moduleFilesGlobPattern)
        return emptyList()
    }
    if ("**" in moduleDirGlob.value) {
        problemReporter.reportBundleError(
            source = moduleDirGlob.asBuildProblemSource(),
            messageKey = "project.module.glob.0.double.star.not.supported",
            moduleDirGlob.value,
            level = Level.Error,
        )
        return emptyList()
    }
    val matchingModuleFiles = this.findAllDescendantsMatching(glob = moduleFilesGlob)
    if (matchingModuleFiles.isEmpty()) {
        problemReporter.reportBundleError(
            source = moduleDirGlob.asBuildProblemSource(),
            messageKey = "project.module.glob.0.matches.nothing",
            moduleDirGlob.value,
            level = Level.WeakWarning,
            problemType = BuildProblemType.RedundantDeclaration,
        )
    }
    return matchingModuleFiles
}

context(problemReporter: ProblemReporter)
private fun reportInvalidGlob(moduleDirGlob: TraceableString, generatedModuleFilesGlob: String) {
    try {
        // we want to generate a glob syntax error for the exact user-provided string, not our own construction
        Glob.checkValid(moduleDirGlob.value)
        // If the user glob succeeds on its own, we have a bug in our code which creates an invalid glob for module files
        error("Invalid glob '$generatedModuleFilesGlob' constructed internally for a valid user-provided glob '${moduleDirGlob.value}'")
    } catch (e: PatternSyntaxException) {
        problemReporter.reportBundleError(
            source = moduleDirGlob.asBuildProblemSource(),
            messageKey = "project.module.glob.0.is.invalid.1",
            moduleDirGlob.value,
            e.message ?: "(no additional information)",
            level = Level.Error,
        )
    }
}

/**
 * Returns all descendants of this directory that match the given [glob].
 */
private fun VirtualFile.findAllDescendantsMatching(glob: Glob): List<VirtualFile> {
    val base = this.toNioPath()

    // TODO we could optimize the search here because we know the depth up front (** is forbidden)
    return filterDescendants { file ->
        val pathToTest = file.toNioPath().relativeTo(base)
        glob.matches(pathToTest)
    }
}
