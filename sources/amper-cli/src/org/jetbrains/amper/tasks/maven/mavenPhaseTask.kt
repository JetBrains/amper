/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import org.apache.maven.project.MavenProject
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.ModuleSequenceCtx
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.ArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.JvmResourcesDirArtifact
import org.jetbrains.amper.tasks.artifacts.KotlinJavaSourceDirArtifact
import org.jetbrains.amper.tasks.artifacts.Selectors
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.amper.tasks.jvm.JvmCompileTask
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.reflect.KFunction3

typealias PhaseTaskCtor = KFunction3<TaskName, AmperModule, Boolean, BaseUmbrellaMavenPhaseTask>

@Suppress("EnumEntryName")
enum class KnownMavenPhase(
    vararg val dependsOn: KnownMavenPhase = emptyArray(),
    private val taskCtor: PhaseTaskCtor = ::BaseUmbrellaMavenPhaseTask,
    private val isTest: Boolean = false,
) {
    validate,
    
    initialize(
        validate
    ),

    `generate-sources`(
        initialize,
        taskCtor = ::AdditionalSourcesAwareMavenPhaseTask
    ),

    `process-sources`(
        initialize,
        `generate-sources`
    ),

    `generate-resources`(
        initialize,
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
        taskCtor = ::AdditionalSourcesAwareMavenPhaseTask,
        isTest = true
    ),

    `process-test-sources`(
        initialize, `generate-test-sources`,
        isTest = true
    ),

    `generate-test-resources`(
        initialize,
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

    `prepare-package`(
        `process-classes`
    ),

    `package`(
        `prepare-package`
    );

    // Now we don't know how to run these:
    //   pre-integration-test,
    //   integration-test,
    //   post-integration-test,
    //   verify,
    //   install,
    //   deploy,

    context(moduleCtx: ModuleSequenceCtx)
    val taskName get() = TaskName.fromHierarchy(listOf(moduleCtx.module.userReadableName, "maven", name))

    context(moduleCtx: ModuleSequenceCtx)
    fun createTask() = taskCtor(taskName, moduleCtx.module, isTest)
}

/**
 * Cumulative information about a maven project that is collected
 * during task execution, that is used to configure maven project
 * mocks that are created during mojo execution.
 */
class MavenProjectEmbryo(
    var classesOutputPath: Path? = null,
    var testClassesOutputPath: Path? = null,
    var generatedSourcesPaths: List<Path> = emptyList(),
    var generatedTestSourcesPaths: List<Path> = emptyList(),
    var generatedResourcesPaths: List<Path> = emptyList(),
    var generatedTestResourcesPaths: List<Path> = emptyList(),
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
    )

    fun configureProject(mavenProject: MavenProject) {
        classesOutputPath?.let { mavenProject.build.outputDirectory = it.absolutePathString() }
        testClassesOutputPath?.let { mavenProject.build.testOutputDirectory = it.absolutePathString() }
        mavenProject.addCompileSourceRoots(generatedSourcesPaths)
        mavenProject.addTestCompileSourceRoots(generatedTestSourcesPaths)
        mavenProject.addResources(generatedResourcesPaths)
        mavenProject.addTestResources(generatedTestResourcesPaths)
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
) : ArtifactTaskBase() {

    /**
     * Allows adding additional maven project information from the task.
     */
    open fun embryo(dependenciesResult: List<TaskResult>) = MavenProjectEmbryo()

    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): MavenProjectEmbryo = dependenciesResult.filterIsInstance<MavenProjectEmbryo>()
        .fold(embryo(dependenciesResult), MavenProjectEmbryo::merge)
}

/**
 * Maven phase task that is aware of sources generation.
 */
class AdditionalSourcesAwareMavenPhaseTask(taskName: TaskName, module: AmperModule, isTest: Boolean) :
    BaseUmbrellaMavenPhaseTask(taskName, module, isTest) {

    private val additionalSourceDirs by Selectors.fromMatchingFragments(
        KotlinJavaSourceDirArtifact::class,
        module = module,
        isTest = isTest,
        hasPlatforms = setOf(Platform.JVM),
        quantifier = Quantifier.AnyOrNone,
    )

    override fun embryo(dependenciesResult: List<TaskResult>) =
        if (!isTest) MavenProjectEmbryo(generatedSourcesPaths = additionalSourceDirs.map { it.path })
        else MavenProjectEmbryo(generatedTestSourcesPaths = additionalSourceDirs.map { it.path })
}

/**
 * Maven phase task that is aware of sources generation.
 */
class AdditionalResourcesAwareMavenPhaseTask(taskName: TaskName, module: AmperModule, isTest: Boolean) :
    BaseUmbrellaMavenPhaseTask(taskName, module, isTest) {

    private val additionalResourceDirs by Selectors.fromMatchingFragments(
        type = JvmResourcesDirArtifact::class,
        module = module,
        isTest = isTest,
        hasPlatforms = setOf(Platform.JVM),
        quantifier = Quantifier.AnyOrNone,
    )

    override fun embryo(dependenciesResult: List<TaskResult>) =
        if (!isTest) MavenProjectEmbryo(generatedSourcesPaths = additionalResourceDirs.map { it.path })
        else MavenProjectEmbryo(generatedTestResourcesPaths = additionalResourceDirs.map { it.path })
}

/**
 * Maven phase task that is aware of compiled classes.
 */
class ClassesAwareMavenPhaseTask(taskName: TaskName, module: AmperModule, isTest: Boolean) :
    BaseUmbrellaMavenPhaseTask(taskName, module, isTest) {

    override fun embryo(dependenciesResult: List<TaskResult>): MavenProjectEmbryo {
        val compileResult = dependenciesResult
            .filterIsInstance<JvmCompileTask.Result>()
            .singleOrNull() ?: return MavenProjectEmbryo()

        return if (!isTest) MavenProjectEmbryo(classesOutputPath = compileResult.classesOutputRoot)
        else MavenProjectEmbryo(testClassesOutputPath = compileResult.classesOutputRoot)
    }
}