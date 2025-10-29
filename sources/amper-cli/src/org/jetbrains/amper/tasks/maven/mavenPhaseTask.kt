/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import org.apache.maven.project.MavenProject
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.group
import org.jetbrains.amper.dependency.resolution.module
import org.jetbrains.amper.dependency.resolution.version
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModuleAndContext
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.tasks.AdditionalSourceRootsProvider
import org.jetbrains.amper.tasks.ModuleSequenceCtx
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.SourceRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.ArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.JvmResourcesDirArtifact
import org.jetbrains.amper.tasks.artifacts.KotlinJavaSourceDirArtifact
import org.jetbrains.amper.tasks.artifacts.Selectors
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.amper.tasks.buildDependenciesGraph
import org.jetbrains.amper.tasks.doResolveExternalDependencies
import org.jetbrains.amper.tasks.jvm.CompiledJvmClassesArtifact
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString

@Suppress("EnumEntryName")
enum class KnownMavenPhase(
    vararg val dependsOn: KnownMavenPhase = emptyArray(),
    private val taskCtor: (PhaseTaskParameters) -> BaseUmbrellaMavenPhaseTask = ::BaseUmbrellaMavenPhaseTask,
    private val isTest: Boolean = false,
) {
    validate(
        taskCtor = ::ModuleDependenciesAwareMavenPhaseTask
    ),

    initialize(
        validate
    ),

    `generate-sources`(
        initialize,
        taskCtor = ::GeneratedSourcesMavenPhaseTask
    ),

    `process-sources`(
        initialize,
        `generate-sources`
    ),

    `generate-resources`(
        initialize, `generate-sources`,
        taskCtor = ::AdditionalResourcesAwareMavenPhaseTask
    ),

    `process-resources`(
        initialize, `generate-resources`
    ),

    compile(
        `process-sources`, `process-resources`,
        taskCtor = ::ClassesAwareMavenPhaseTask
    ),

    `process-classes`(
        compile
    ),

    `generate-test-sources`(
        `process-classes`,
        taskCtor = ::GeneratedSourcesMavenPhaseTask,
        isTest = true
    ),

    `process-test-sources`(
        initialize, `generate-test-sources`,
        isTest = true
    ),

    `generate-test-resources`(
        initialize, `generate-test-sources`,
        taskCtor = ::AdditionalResourcesAwareMavenPhaseTask,
        isTest = true
    ),

    `process-test-resources`(
        initialize, `generate-test-resources`,
        isTest = true
    ),

    `test-compile`(
        `process-test-sources`, `process-test-resources`, `process-classes`,
        taskCtor = ::ClassesAwareMavenPhaseTask,
        isTest = true
    ),

    `process-test-classes`(
        `test-compile`,
        isTest = true
    ),

    test(
        `process-test-classes`
    ),

    // Now we don't know how to run these:
//    `prepare-package`(
//        `process-classes`
//    ),
//
//    `package`(
//        `prepare-package`
//    ),
    ;

    //   pre-integration-test,
    //   integration-test,
    //   post-integration-test,
    //   verify,
    //   install,
    //   deploy,

    context(moduleCtx: ModuleSequenceCtx)
    val taskName get() = TaskName.fromHierarchy(listOf(moduleCtx.module.userReadableName, "maven", name))

    context(moduleCtx: ModuleSequenceCtx, taskBuilder: ProjectTasksBuilder)
    fun createTask() = taskCtor(
        PhaseTaskParameters(
            taskName = taskName,
            module = moduleCtx.module,
            isTest = isTest,
            incrementalCache = moduleCtx.incrementalCache,
            cacheRoot = taskBuilder.context.userCacheRoot,
        )
    )
}

data class MavenPhaseResult(
    val fragment: Fragment,
    val embryo: MavenProjectEmbryo,
    val modelChanges: List<ModelChange>,
) : TaskResult, AdditionalSourceRootsProvider {
    override val sourceRoots: List<SourceRoot>
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
    val classesOutputPath: Path? = null,
    val testClassesOutputPath: Path? = null,
    val generatedSourcesPaths: List<Path> = emptyList(),
    val generatedTestSourcesPaths: List<Path> = emptyList(),
    val generatedResourcesPaths: List<Path> = emptyList(),
    val generatedTestResourcesPaths: List<Path> = emptyList(),
    val artifacts: Set<MavenArtifact> = emptySet(),
) {
    /**
     * Merges this embryo with another one with this's embryo properties taking precedence.
     */
    fun merge(other: MavenProjectEmbryo) = MavenProjectEmbryo(
        classesOutputPath = this.classesOutputPath ?: other.classesOutputPath,
        testClassesOutputPath = this.testClassesOutputPath ?: other.testClassesOutputPath,
        generatedSourcesPaths = other.generatedSourcesPaths + generatedSourcesPaths,
        generatedTestSourcesPaths = other.generatedTestSourcesPaths + generatedTestSourcesPaths,
        generatedResourcesPaths = other.generatedResourcesPaths + generatedResourcesPaths,
        generatedTestResourcesPaths = other.generatedTestResourcesPaths + generatedTestResourcesPaths,
        artifacts = other.artifacts + artifacts,
    )

    fun configureProject(mavenProject: MavenProject) {
        classesOutputPath?.let { mavenProject.build.outputDirectory = it.absolutePathString() }
        testClassesOutputPath?.let { mavenProject.build.testOutputDirectory = it.absolutePathString() }
        mavenProject.addCompileSourceRoots(generatedSourcesPaths)
        mavenProject.addTestCompileSourceRoots(generatedTestSourcesPaths)
        mavenProject.addResources(generatedResourcesPaths)
        mavenProject.addTestResources(generatedTestResourcesPaths)
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
            generatedSourcesPaths = currentPhaseModelChanges.flatMap { it.additionalSources },
            generatedTestSourcesPaths = currentPhaseModelChanges.flatMap { it.additionalTestSources },
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
 * It is adding additional sources to the embryo as well as external artifacts that should
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

    private val mavenResolver by lazy {
        MavenResolver(parameters.cacheRoot, parameters.incrementalCache)
    }

    // Here we are converting the external dependencies graph to the flat list of maven artifacts.
    suspend fun PhaseTaskParameters.getExternalAetherArtifacts(dependenciesResult: List<TaskResult>) =
        dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.Result>()
            .map {
                mavenResolver.doResolveExternalDependencies(
                    module = module,
                    platform = Platform.JVM,
                    isTest = isTest,
                    compileModuleDependencies = getModuleDependencies(ResolutionScope.COMPILE),
                    runtimeModuleDependencies = getModuleDependencies(ResolutionScope.RUNTIME),
                )
            }
            .map { it.runtimeDependenciesRootNode ?: it.compileDependenciesRootNode }
            .flatMap { it.distinctBfsSequence() }
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

    /**
     * [ModuleDependencyNodeWithModuleAndContext] cannot be reused, as its transitive children can change after
     * resolve and as a result - change cache key that is used within DR incremental request.
     * Thus, we need to create a new instance of the node every time here.
     */
    private fun getModuleDependencies(scope: ResolutionScope) = parameters.module.buildDependenciesGraph(
        isTest = parameters.isTest,
        platform = Platform.JVM,
        dependencyReason = scope,
        userCacheRoot = parameters.cacheRoot,
        incrementalCache = parameters.incrementalCache,
    )

    private fun PhaseTaskParameters.getEmbryoWithSources(): MavenProjectEmbryo =
        if (!isTest) MavenProjectEmbryo(generatedSourcesPaths = additionalSourceDirs.map { it.path })
        else MavenProjectEmbryo(generatedTestSourcesPaths = additionalSourceDirs.map { it.path })

    override suspend fun PhaseTaskParameters.embryo(dependenciesResult: List<TaskResult>): MavenProjectEmbryo {
        val embryoWithSources = getEmbryoWithSources()
        val externalArtifacts = getExternalAetherArtifacts(dependenciesResult)
        return embryoWithSources.copy(artifacts = externalArtifacts)
    }
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
        if (!isTest) MavenProjectEmbryo(generatedSourcesPaths = additionalResourceDirs.map { it.path })
        else MavenProjectEmbryo(generatedTestResourcesPaths = additionalResourceDirs.map { it.path })
}

/**
 * Maven phase task that is aware of compiled classes.
 */
class ClassesAwareMavenPhaseTask(parameters: PhaseTaskParameters) : BaseUmbrellaMavenPhaseTask(parameters) {

    private val compiledJvmClassesDirs by Selectors.fromModuleOnly(
        type = CompiledJvmClassesArtifact::class,
        module = parameters.module,
        platform = Platform.JVM,
        isTest = parameters.isTest,
    )

    override suspend fun PhaseTaskParameters.embryo(dependenciesResult: List<TaskResult>): MavenProjectEmbryo =
        if (!isTest) MavenProjectEmbryo(classesOutputPath = compiledJvmClassesDirs.singleOrNull()?.path)
        else MavenProjectEmbryo(testClassesOutputPath = compiledJvmClassesDirs.singleOrNull()?.path)
}

/**
 * Maven phase task that is aware of compiled classes.
 */
class ModuleDependenciesAwareMavenPhaseTask(parameters: PhaseTaskParameters) : BaseUmbrellaMavenPhaseTask(parameters) {

    private val moduleDependenciesClasses by Selectors.fromModuleWithDependencies(
        type = CompiledJvmClassesArtifact::class,
        leafFragment = targetFragment,
        userCacheRoot = AmperUserCacheRoot(Path(".").absolute()),
        quantifier = Quantifier.AnyOrNone,
        includeSelf = false,
        incrementalCache = parameters.incrementalCache,
    )

    private fun CompiledJvmClassesArtifact.toArtifact(scope: String) =
        module.asMavenArtifact(scope).apply { file = path.toFile() }

    override suspend fun PhaseTaskParameters.embryo(dependenciesResult: List<TaskResult>): MavenProjectEmbryo =
        if (isTest) MavenProjectEmbryo(artifacts = moduleDependenciesClasses.map { it.toArtifact("test") }.toSet())
        else MavenProjectEmbryo(artifacts = moduleDependenciesClasses.map { it.toArtifact("runtime") }.toSet())
}