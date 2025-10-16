/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.tasks.ModuleSequenceCtx
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.ArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.JvmResourcesDirArtifact
import org.jetbrains.amper.tasks.artifacts.KotlinJavaSourceDirArtifact
import org.jetbrains.amper.tasks.artifacts.Selectors
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.amper.tasks.jvm.CompiledJvmClassesArtifact
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.reflect.KFunction4

typealias PhaseTaskCtor = KFunction4<TaskName, AmperModule, Boolean, IncrementalCache, BaseUmbrellaMavenPhaseTask>

@Suppress("EnumEntryName")
enum class KnownMavenPhase(
    vararg val dependsOn: KnownMavenPhase = emptyArray(),
    private val taskCtor: PhaseTaskCtor = ::BaseUmbrellaMavenPhaseTask,
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
        initialize,
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

    context(moduleCtx: ModuleSequenceCtx)
    fun createTask() = taskCtor(taskName, moduleCtx.module, isTest, moduleCtx.incrementalCache)
}

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
) : TaskResult {
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
 * Empty task that serves for grouping maven plugin tasks
 * that should be bound to the existing amper jvm tasks.
 */
open class BaseUmbrellaMavenPhaseTask(
    override val taskName: TaskName,
    protected val module: AmperModule,
    protected val isTest: Boolean,
    protected val incrementalCache: IncrementalCache,
) : ArtifactTaskBase() {

    /**
     * Implementation should create an embryo object with a part of information
     * from other amper tasks that should be propagated to the maven model
     * in the corresponding maven phase.
     */
    open suspend fun embryo(dependenciesResult: List<TaskResult>) = MavenProjectEmbryo()

    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): MavenProjectEmbryo = dependenciesResult.filterIsInstance<MavenProjectEmbryo>()
        .fold(embryo(dependenciesResult), MavenProjectEmbryo::merge)
}

/**
 * Aggregating task for [`generate-sources`] and [`generate-test-sources`] phases.
 * It is adding additional sources to the embryo as well as external artifacts that should
 * be accessible to the maven model.
 */
class GeneratedSourcesMavenPhaseTask(taskName: TaskName, module: AmperModule, isTest: Boolean, incrementalCache: IncrementalCache) :
    BaseUmbrellaMavenPhaseTask(taskName, module, isTest, incrementalCache) {

    private val additionalSourceDirs by Selectors.fromMatchingFragments(
        KotlinJavaSourceDirArtifact::class,
        module = module,
        isTest = isTest,
        hasPlatforms = setOf(Platform.JVM),
        quantifier = Quantifier.AnyOrNone,
    )

    // Here we are converting the external dependencies graph to the flat list of maven artifacts.
    fun getExternalAetherArtifacts(dependenciesResult: List<TaskResult>) = dependenciesResult
        .filterIsInstance<ResolveExternalDependenciesTask.Result>()
        .mapNotNull { it.runtimeClasspathTree ?: it.compileClasspathTree }
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

    private fun getEmbryoWithSources(): MavenProjectEmbryo =
        if (!isTest) MavenProjectEmbryo(generatedSourcesPaths = additionalSourceDirs.map { it.path })
        else MavenProjectEmbryo(generatedTestSourcesPaths = additionalSourceDirs.map { it.path })
    
    override suspend fun embryo(dependenciesResult: List<TaskResult>): MavenProjectEmbryo {
        val embryoWithSources = getEmbryoWithSources()
        val externalArtifacts = getExternalAetherArtifacts(dependenciesResult)
        return embryoWithSources.copy(artifacts = externalArtifacts)
    }
}

/**
 * Maven phase task that is aware of sources generation.
 */
class AdditionalResourcesAwareMavenPhaseTask(taskName: TaskName, module: AmperModule, isTest: Boolean, incrementalCache: IncrementalCache) :
    BaseUmbrellaMavenPhaseTask(taskName, module, isTest, incrementalCache) {

    private val additionalResourceDirs by Selectors.fromMatchingFragments(
        type = JvmResourcesDirArtifact::class,
        module = module,
        isTest = isTest,
        hasPlatforms = setOf(Platform.JVM),
        quantifier = Quantifier.AnyOrNone,
    )

    override suspend fun embryo(dependenciesResult: List<TaskResult>) =
        if (!isTest) MavenProjectEmbryo(generatedSourcesPaths = additionalResourceDirs.map { it.path })
        else MavenProjectEmbryo(generatedTestResourcesPaths = additionalResourceDirs.map { it.path })
}

/**
 * Maven phase task that is aware of compiled classes.
 */
class ClassesAwareMavenPhaseTask(taskName: TaskName, module: AmperModule, isTest: Boolean, incrementalCache: IncrementalCache) :
    BaseUmbrellaMavenPhaseTask(taskName, module, isTest, incrementalCache) {

    private val compiledJvmClassesDirs by Selectors.fromModuleOnly(
        type = CompiledJvmClassesArtifact::class,
        module = module,
        platform = Platform.JVM,
        isTest = isTest,
    )

    override suspend fun embryo(dependenciesResult: List<TaskResult>): MavenProjectEmbryo =
        if (!isTest) MavenProjectEmbryo(classesOutputPath = compiledJvmClassesDirs.singleOrNull()?.path)
        else MavenProjectEmbryo(testClassesOutputPath = compiledJvmClassesDirs.singleOrNull()?.path)
}

/**
 * Maven phase task that is aware of compiled classes.
 */
class ModuleDependenciesAwareMavenPhaseTask(taskName: TaskName, module: AmperModule, isTest: Boolean, incrementalCache: IncrementalCache) :
    BaseUmbrellaMavenPhaseTask(taskName, module, isTest, incrementalCache) {

    private val moduleDependenciesClasses by Selectors.fromModuleWithDependencies(
        type = CompiledJvmClassesArtifact::class,
        leafFragment = module.leafFragments.single { it.platform == Platform.JVM && it.isTest == isTest },
        userCacheRoot = AmperUserCacheRoot(Path(".").absolute()),
        quantifier = Quantifier.AnyOrNone,
        includeSelf = false,
        incrementalCache = incrementalCache,
    )

    private fun CompiledJvmClassesArtifact.toArtifact(scope: String) =
        module.asMavenArtifact(scope).apply { file = path.toFile() }

    override suspend fun embryo(dependenciesResult: List<TaskResult>): MavenProjectEmbryo =
        if (isTest) MavenProjectEmbryo(artifacts = moduleDependenciesClasses.map { it.toArtifact("test") }.toSet())
        else MavenProjectEmbryo(artifacts = moduleDependenciesClasses.map { it.toArtifact("runtime") }.toSet())
}