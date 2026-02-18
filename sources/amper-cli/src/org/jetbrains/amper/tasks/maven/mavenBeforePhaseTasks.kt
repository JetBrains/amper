/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import org.apache.maven.project.MavenProject
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.group
import org.jetbrains.amper.dependency.resolution.module
import org.jetbrains.amper.dependency.resolution.version
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.dr.resolver.CliReportingMavenResolver
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModuleAndContext
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.ModuleDependencies
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.ArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.JvmResourcesDirArtifact
import org.jetbrains.amper.tasks.artifacts.KotlinJavaSourceDirArtifact
import org.jetbrains.amper.tasks.artifacts.Selectors
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.amper.tasks.doResolveExternalDependencies
import org.jetbrains.amper.tasks.jvm.CompiledJvmArtifact
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * All phase tasks must have these and only these constructor parameters,
 * so they can be created in a bunch.
 */
class PhaseTaskParameters(
    val taskName: TaskName,
    val module: AmperModule,
    val isTest: Boolean,
    val incrementalCache: IncrementalCache,
    val cacheRoot: AmperUserCacheRoot,
    val sharedMavenProject: MavenProject,
    amperBuildRoot: Path,
) {
    val sharedMavenProjectBuildDir = amperBuildRoot / "maven-target"
}

/**
 * Task that collects all the information to mock the Maven model before mojo executions.
 * Therefore, it depends on the corresponding Amper tasks and previous phases tasks.
 */
open class BeforeMavenPhaseTask(
    protected val parameters: PhaseTaskParameters,
) : ArtifactTaskBase() {

    protected val targetFragment = parameters.module.leafFragments.singleOrNull {
        it.platform == Platform.JVM && it.isTest == parameters.isTest
    } ?: error("No relevant JVM fragment was found. This task should be created only for modules with JVM platform.")

    override val taskName get() = parameters.taskName

    open suspend fun PhaseTaskParameters.configureSharedMavenProject(dependenciesResult: List<TaskResult>) =
        Unit

    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): TaskResult {
        parameters.configureSharedMavenProject(dependenciesResult)
        return EmptyTaskResult
    }
}

/**
 * Aggregating task for [KnownMavenPhase.`generate-sources`] and [KnownMavenPhase.`generate-test-sources`] phases.
 * It is adding additional sources to the embryo that should
 * be accessible to the maven model.
 */
class GeneratedSourcesMavenPhaseTask(parameters: PhaseTaskParameters) : BeforeMavenPhaseTask(parameters) {

    private val additionalSourceDirs by Selectors.fromMatchingFragments(
        KotlinJavaSourceDirArtifact::class,
        module = parameters.module,
        isTest = parameters.isTest,
        hasPlatforms = setOf(Platform.JVM),
        quantifier = Quantifier.AnyOrNone,
    )

    override suspend fun PhaseTaskParameters.configureSharedMavenProject(dependenciesResult: List<TaskResult>) =
        if (!isTest) sharedMavenProject.addCompileSourceRoots(additionalSourceDirs.map { it.path })
        else sharedMavenProject.addTestCompileSourceRoots(additionalSourceDirs.map { it.path })
}

/**
 * Maven phase task that is aware of sources generation.
 */
class AdditionalResourcesAwareMavenPhaseTask(parameters: PhaseTaskParameters) : BeforeMavenPhaseTask(parameters) {

    private val additionalResourceDirs by Selectors.fromMatchingFragments(
        type = JvmResourcesDirArtifact::class,
        module = parameters.module,
        isTest = parameters.isTest,
        hasPlatforms = setOf(Platform.JVM),
        quantifier = Quantifier.AnyOrNone,
    )

    override suspend fun PhaseTaskParameters.configureSharedMavenProject(dependenciesResult: List<TaskResult>) =
        if (!isTest) sharedMavenProject.addResources(additionalResourceDirs.map { it.path })
        else sharedMavenProject.addTestResources(additionalResourceDirs.map { it.path })
}

/**
 * Maven phase task that is aware of compiled classes and aggregates them to fit into [MavenProject].
 */
class ClassesAwareMavenPhaseTask(parameters: PhaseTaskParameters) : BeforeMavenPhaseTask(parameters) {

    private val compiledJvmClassesDirs by Selectors.fromModuleOnly(
        type = CompiledJvmArtifact::class,
        module = parameters.module,
        platform = Platform.JVM,
        isTest = parameters.isTest,
    )

    override suspend fun PhaseTaskParameters.configureSharedMavenProject(
        dependenciesResult: List<TaskResult>,
    ) {
        val allClasses = compiledJvmClassesDirs.map { it.kotlinCompilerOutputRoot } + 
                    compiledJvmClassesDirs.map { it.javaCompilerOutputRoot }
        val build = sharedMavenProject.build

        // Aggregate classes directories into the target one (maven does not support multiple classes directories).
        val aggregatedClassesDir = aggregateClassDirectoriesOrNull(
            allClasses,
            sharedMavenProjectBuildDir / "classes",
        )

        val aggregatedTestClassesDir = aggregateClassDirectoriesOrNull(
            allClasses,
            sharedMavenProjectBuildDir / "test-classes",
        )

        // Set the aggregated directories.
        build.outputDirectory = aggregatedClassesDir.absolutePathString()
        build.testOutputDirectory = aggregatedTestClassesDir.absolutePathString()
    }

    /**
     * Copy all files from source directories to the target directory.
     * If the same file exists in multiple source directories, the last one wins (overwrites).
     */
    private fun aggregateClassDirectoriesOrNull(sourceDirs: List<Path>, targetDir: Path): Path =
        if (sourceDirs.isEmpty()) targetDir
        else targetDir.createDirectories().also {
            for (sourceDir in sourceDirs.map(Path::absolute).distinct())
                if (sourceDir.exists() && sourceDir.isDirectory())
                    sourceDir.copyToRecursively(targetDir, followLinks = false, overwrite = true)
        }
}

/**
 * Initial maven phase task that adds:
 *  - compiled classes as artifacts
 *  - resolved external artifacts
 *  - initial source and resource paths
 */
class InitialMavenPhaseTask(parameters: PhaseTaskParameters) : BeforeMavenPhaseTask(parameters) {

    private val mavenResolver by lazy {
        CliReportingMavenResolver(parameters.cacheRoot, parameters.incrementalCache)
    }

    /**
     * [ModuleDependencyNodeWithModuleAndContext] cannot be reused, as its transitive children can change after
     * resolve and as a result - change cache key that is used within DR incremental request.
     * Thus, we need to create a new instance of the node every time here.
     */
    private fun getModuleDependencies(isTest: Boolean) =
        ModuleDependencies(parameters.module, parameters.cacheRoot, parameters.incrementalCache)
            .forResolution(isTest)

    // Here we are converting the external dependencies graph to the flat list of maven artifacts.
    private suspend fun PhaseTaskParameters.getExternalAetherArtifacts(isTest: Boolean) =
        mavenResolver.doResolveExternalDependencies(
            module = module,
            platform = Platform.JVM,
            isTest = isTest,
            moduleDependencies = getModuleDependencies(isTest),
        )
            .let { it.runtimeDependenciesRootNode ?: it.compileDependenciesRootNode }
            .distinctBfsSequence()
            .filterIsInstance<MavenDependencyNode>()
            // Filter out all dependencies without files.
            .mapNotNull { it.dependency.files().firstOrNull()?.path?.to(it) }
            .map { (path, it) ->
                DefaultMavenArtifact(
                    groupId = it.dependency.group,
                    artifactId = it.dependency.module,
                    version = it.dependency.version ?: "unspecified",
                    scope = "runtime",
                    type = "jar",
                    isAddedToClasspath = true
                ).apply {
                    file = path.toFile()
                }
            }
            .toSet()

    private val moduleDependenciesClasses by Selectors.fromModuleWithDependencies(
        type = CompiledJvmArtifact::class,
        leafFragment = targetFragment,
        userCacheRoot = AmperUserCacheRoot(Path(".").absolute()),
        quantifier = Quantifier.AnyOrNone,
        includeSelf = false,
        incrementalCache = parameters.incrementalCache,
    )

    private fun CompiledJvmArtifact.toArtifacts(scope: String) =
        listOf("java-output", "kotlin-output", "resources-output").map {
            module.asMavenArtifact(scope, "-${it}")
                .apply { file = path.resolve(it).toFile() }
        }

    override suspend fun PhaseTaskParameters.configureSharedMavenProject(dependenciesResult: List<TaskResult>) {
        val classesArtifacts = moduleDependenciesClasses
            .flatMap { it.toArtifacts(if (it.isTest) MavenArtifact.SCOPE_TEST else MavenArtifact.SCOPE_RUNTIME) }
            .toSet()
        val externalArtifacts = getExternalAetherArtifacts(false) + getExternalAetherArtifacts(true)
        
        if (!isTest) {
            // Since test execution of this task will happen after the main execution - thus
            // we can initialize things here that are needed to be initialized only once.
            sharedMavenProject.build.directory = sharedMavenProjectBuildDir.absolutePathString()
            
            sharedMavenProject.artifacts = classesArtifacts + externalArtifacts
            sharedMavenProject.artifact = module.asMavenArtifact("runtime")
            
            sharedMavenProject.addCompileSourceRoots(targetFragment.sourceRoots)
            sharedMavenProject.addResources(listOf(targetFragment.resourcesPath))
        } else {
            sharedMavenProject.addTestCompileSourceRoots(targetFragment.sourceRoots)
            sharedMavenProject.addTestResources(listOf(targetFragment.resourcesPath))
        }
    }
}