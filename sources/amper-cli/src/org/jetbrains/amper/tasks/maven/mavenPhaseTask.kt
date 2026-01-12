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
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.dr.resolver.CliReportingMavenResolver
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModuleAndContext
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.tasks.ModuleDependencies
import org.jetbrains.amper.tasks.ModuleSequenceCtx
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.SourceRoot
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

/**
 * An order-sensitive list of maven phases that are supported by the maven compatibility layer.
 */
@Suppress("EnumEntryName")
enum class KnownMavenPhase(
    private val taskCtor: (PhaseTaskParameters) -> BaseUmbrellaMavenPhaseTask = ::BaseUmbrellaMavenPhaseTask,
    private val isTest: Boolean = false,
) {
    validate(::InitialMavenPhaseTask),
    initialize,
    `generate-sources`(::GeneratedSourcesMavenPhaseTask),
    `process-sources`,
    `generate-resources`(::AdditionalResourcesAwareMavenPhaseTask),
    `process-resources`,
    compile(::ClassesAwareMavenPhaseTask),
    `process-classes`,
    `generate-test-sources`(::GeneratedSourcesMavenPhaseTask, isTest = true),
    `process-test-sources`(isTest = true),
    `generate-test-resources`(::AdditionalResourcesAwareMavenPhaseTask, isTest = true),
    `process-test-resources`(isTest = true),
    `test-compile`(::ClassesAwareMavenPhaseTask, isTest = true),
    `process-test-classes`(isTest = true),
    test,

    // We don't know how to bind these to the existing Amper tasks yet.
    `prepare-package`,
    `package`,
    `pre-integration-test`,
    `integration-test`,
    `post-integration-test`,
    verify,
    install,
    deploy,
    ;

    context(moduleCtx: ModuleSequenceCtx)
    val taskName get() = TaskName.fromHierarchy(listOf(moduleCtx.module.userReadableName, "maven", name))

    val dependsOn get() = entries.getOrNull(entries.indexOf(this) - 1)

    context(moduleCtx: ModuleSequenceCtx, taskBuilder: ProjectTasksBuilder)
    fun createTask() = taskCtor(
        PhaseTaskParameters(
            taskName = taskName,
            module = moduleCtx.module,
            isTest = isTest,
            incrementalCache = taskBuilder.context.incrementalCache,
            cacheRoot = taskBuilder.context.userCacheRoot,
        )
    )
}

data class MavenPhaseResult(
    val fragment: Fragment,
    val embryo: MavenProjectEmbryo,
    val modelChanges: List<ModelChange>,
) : TaskResult {
    val sourceRoots: List<SourceRoot>
        get() = modelChanges
            .flatMap { if (!fragment.isTest) it.additionalSources else it.additionalTestSources }
            .distinct()
            .map { SourceRoot(fragment.name, it) }
}

/**
 * Project model additions brought up by maven mojo executions.
 */
data class ModelChange(
    val additionalSources: List<Path>,
    val additionalTestSources: List<Path>,
) : TaskResult

/**
 * Cumulative information about a maven project that is collected
 * during task execution, that is used to configure maven project
 * mocks that are created during mojo execution.
 */
data class MavenProjectEmbryo(
    val allClassesOutputPaths: List<Path> = emptyList(),
    val allTestClassesOutputPaths: List<Path> = emptyList(),
    val sourcesPaths: List<Path> = emptyList(),
    val testSourcesPaths: List<Path> = emptyList(),
    val resourcesPaths: List<Path> = emptyList(),
    val testResourcesPaths: List<Path> = emptyList(),
    val artifacts: Set<MavenArtifact> = emptySet(),
) {
    /**
     * Merges this embryo with another one with this's embryo properties taking precedence.
     */
    fun merge(other: MavenProjectEmbryo) = MavenProjectEmbryo(
        allClassesOutputPaths = other.allClassesOutputPaths + allClassesOutputPaths,
        allTestClassesOutputPaths = other.allTestClassesOutputPaths + allTestClassesOutputPaths,
        sourcesPaths = other.sourcesPaths + sourcesPaths,
        testSourcesPaths = other.testSourcesPaths + testSourcesPaths,
        resourcesPaths = other.resourcesPaths + resourcesPaths,
        testResourcesPaths = other.testResourcesPaths + testResourcesPaths,
        artifacts = other.artifacts + artifacts,
    )

    fun configureProject(mavenProject: MavenProject) {
        // Note: outputDirectory and testOutputDirectory are set in ExecuteMavenMojoTask after aggregation.
        mavenProject.addCompileSourceRoots(sourcesPaths.distinct())
        mavenProject.addTestCompileSourceRoots(testSourcesPaths.distinct())
        mavenProject.addResources(resourcesPaths.distinct())
        mavenProject.addTestResources(testResourcesPaths.distinct())
        // Copy the collection.
        mavenProject.artifacts = artifacts.toSet()
    }
}

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
)

/**
 * Empty task that serves for grouping maven plugin tasks
 * that should be bound to the existing amper jvm tasks.
 */
open class BaseUmbrellaMavenPhaseTask(
    protected val parameters: PhaseTaskParameters,
) : ArtifactTaskBase() {

    val targetFragment = parameters.module.leafFragments.singleOrNull {
        it.platform == Platform.JVM && it.isTest == parameters.isTest
    } ?: error("No relevant JVM fragment was found. This task should be created only for modules with JVM platform.")

    override val taskName get() = parameters.taskName

    /**
     * Implementation should create an embryo object with a part of information
     * from other amper tasks that should be propagated to the maven model
     * in the corresponding maven phase.
     */
    open suspend fun PhaseTaskParameters.embryo(dependenciesResult: List<TaskResult>) = MavenProjectEmbryo()

    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): MavenPhaseResult {
        val previousPhasesResults = dependenciesResult.filterIsInstance<MavenPhaseResult>()
        val currentPhaseModelChanges = dependenciesResult.filterIsInstance<ModelChange>()

        // Collect all the model information to pass to the next phase task.
        val currentEmbryo = parameters.embryo(dependenciesResult)
        val embryoFromChanges = MavenProjectEmbryo(
            sourcesPaths = currentPhaseModelChanges.flatMap { it.additionalSources },
            testSourcesPaths = currentPhaseModelChanges.flatMap { it.additionalTestSources },
        )
        val cumulativeEmbryo = (previousPhasesResults.map { it.embryo } + embryoFromChanges + currentEmbryo)
            .reversed().reduce(MavenProjectEmbryo::merge)

        // Aggregate model changes to pass to the dependent Amper tasks through task results.
        val previousChanges = previousPhasesResults.flatMap { it.modelChanges }
        return MavenPhaseResult(targetFragment, cumulativeEmbryo, previousChanges + currentPhaseModelChanges)
    }
}

/**
 * Aggregating task for [`generate-sources`] and [`generate-test-sources`] phases.
 * It is adding additional sources to the embryo that should
 * be accessible to the maven model.
 */
class GeneratedSourcesMavenPhaseTask(parameters: PhaseTaskParameters) : BaseUmbrellaMavenPhaseTask(parameters) {

    private val additionalSourceDirs by Selectors.fromMatchingFragments(
        KotlinJavaSourceDirArtifact::class,
        module = parameters.module,
        isTest = parameters.isTest,
        hasPlatforms = setOf(Platform.JVM),
        quantifier = Quantifier.AnyOrNone,
    )

    override suspend fun PhaseTaskParameters.embryo(dependenciesResult: List<TaskResult>) =
        if (!isTest) MavenProjectEmbryo(sourcesPaths = additionalSourceDirs.map { it.path })
        else MavenProjectEmbryo(testSourcesPaths = additionalSourceDirs.map { it.path })
}

/**
 * Maven phase task that is aware of sources generation.
 */
class AdditionalResourcesAwareMavenPhaseTask(parameters: PhaseTaskParameters) : BaseUmbrellaMavenPhaseTask(parameters) {

    private val additionalResourceDirs by Selectors.fromMatchingFragments(
        type = JvmResourcesDirArtifact::class,
        module = parameters.module,
        isTest = parameters.isTest,
        hasPlatforms = setOf(Platform.JVM),
        quantifier = Quantifier.AnyOrNone,
    )

    override suspend fun PhaseTaskParameters.embryo(dependenciesResult: List<TaskResult>) =
        if (!isTest) MavenProjectEmbryo(resourcesPaths = additionalResourceDirs.map { it.path })
        else MavenProjectEmbryo(testResourcesPaths = additionalResourceDirs.map { it.path })
}

/**
 * Maven phase task that is aware of compiled classes.
 */
class ClassesAwareMavenPhaseTask(parameters: PhaseTaskParameters) : BaseUmbrellaMavenPhaseTask(parameters) {

    private val compiledJvmClassesDirs by Selectors.fromModuleOnly(
        type = CompiledJvmArtifact::class,
        module = parameters.module,
        platform = Platform.JVM,
        isTest = parameters.isTest,
    )

    override suspend fun PhaseTaskParameters.embryo(dependenciesResult: List<TaskResult>): MavenProjectEmbryo {
        val kotlinOutputRoots = compiledJvmClassesDirs.map { it.kotlinCompilerOutputRoot }
        val javaOutputRoots = compiledJvmClassesDirs.map { it.javaCompilerOutputRoot }
        return if (!isTest) MavenProjectEmbryo(allClassesOutputPaths = kotlinOutputRoots + javaOutputRoots)
        else MavenProjectEmbryo(allTestClassesOutputPaths = kotlinOutputRoots + javaOutputRoots)
    }
}

/**
 * Initial maven phase task that adds:
 *  - compiled classes as artifacts
 *  - resolved external artifacts
 *  - initial source and resource paths
 */
class InitialMavenPhaseTask(parameters: PhaseTaskParameters) : BaseUmbrellaMavenPhaseTask(parameters) {

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

    override suspend fun PhaseTaskParameters.embryo(dependenciesResult: List<TaskResult>): MavenProjectEmbryo {
        val classesArtifacts = moduleDependenciesClasses
            .flatMap { it.toArtifacts(if (it.isTest) "test" else "runtime") }.toSet()
        val externalArtifacts = getExternalAetherArtifacts(false) + getExternalAetherArtifacts(true)

        return MavenProjectEmbryo(
            artifacts = classesArtifacts + externalArtifacts,
            sourcesPaths = targetFragment.sourceRoots,
            resourcesPaths = listOf(targetFragment.resourcesPath),
        )
    }
}