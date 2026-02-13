/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.plugins

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.MavenCoordinates
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.plugins.generated.ShadowClasspath
import org.jetbrains.amper.frontend.plugins.generated.ShadowCompilationArtifact
import org.jetbrains.amper.frontend.plugins.generated.ShadowModuleSources
import org.jetbrains.amper.frontend.tree.CompleteObjectNode
import org.jetbrains.amper.plugins.schema.model.PluginData

/**
 * A custom task information that comes from a plugin.
 * It fully defines a single registered task instance.
 */
class TaskFromPluginDescription(
    /**
     * Short task name as declared in `plugin.yaml`. Only unique within the [plugin][pluginId].
     */
    val name: String,

    /**
     * Plugin ID that this task belongs to.
     * @see codeSource
     */
    val pluginId: PluginData.Id,

    /**
     * Amper module that the [plugin][pluginId] is applied to, resulting into this task registration.
     */
    val appliedTo: AmperModule,

    /**
     * Unique internal task name.
     */
    val backendTaskName: TaskName,

    /**
     * JVM reflection name of the class (Kotlin file facade)
     * to load that contains the [action function][actionFunctionJvmName].
     */
    val actionClassJvmName: String,

    /**
     * JVM name of the static method inside the [Kotlin file facade][actionClassJvmName] that is the task action.
     */
    val actionFunctionJvmName: String,

    /**
     * Action arguments that are supplied by the *frontend* in the form of an object node.
     * Arguments are [properties][CompleteObjectNode.refinedChildren] within the object.
     *
     * Backend will need to convert arguments before passing it to the [action method][actionFunctionJvmName].
     */
    val actionArguments: CompleteObjectNode,

    /**
     * Paths (from the [actionArguments]) that are to be considered as inputs to the task action.
     */
    val inputs: List<InputPath>,

    /**
     * Requested module sources. All the nodes are from the [actionArguments].
     *
     * @see ShadowModuleSources
     */
    val requestedModuleSources: List<ModuleSourcesRequest>,

    /**
     * Requested classpaths to be resolved. All the nodes are from the [actionArguments].
     *
     * @see ShadowClasspath
     */
    val requestedClasspaths: List<ClasspathRequest>,

    /**
     * Requested compilation artifacts to be resolved. All the nodes are from the [actionArguments].
     *
     * @see ShadowCompilationArtifact
     */
    val requestedCompilationArtifacts: List<CompilationResultRequest>,

    /**
     * Paths (from the [actionArguments]) that are to be considered as outputs to the task action as keys.
     * These paths are optionally marked with the additional semantics.
     */
    val outputs: List<OutputPath>,

    /**
     * Local plugin module which runtime classpath contains the [actionClassJvmName].
     * It needs to be built before the task can be executed.
     */
    val codeSource: AmperModule,

    /**
     * Whether the user has explicitly opted out of automatic input/output/config-based execution avoidance.
     */
    val explicitOptOutOfExecutionAvoidance: Boolean,
) {
    /**
     * Dependencies on other [plugin tasks][TaskFromPluginDescription] that were computed on the frontend.
     */
    lateinit var dependsOn: List<TaskFromPluginDescription>

    /**
     * A marked path to be used as a task input.
     */
    data class InputPath(
        val path: TraceablePath,
        /**
         * If true, and if the [path] is the output of another task, a dependency is automatically inferred
         * between the task declaring this input and the task declaring the same path as output.
         */
        val inferTaskDependency: Boolean,
    )

    /**
     * A potentially marked path to be used as a task output.
     */
    data class OutputPath(
        val path: TraceablePath,
        val outputMark: OutputMark?,
    )

    /**
     * Signals that a path points to the generated contents of a certain [kind] (an Artifact), so that the backend
     * can recognize it and wire it to the build.
     *
     * This is needed when the custom task output needs to be plugged into the existing builtin Amper pipelines.
     */
    data class OutputMark(
        /**
         * Describes the output contents.
         */
        val kind: GeneratedPathKind,

        /**
         * Which fragment this output belongs to.
         */
        val associateWith: Fragment,
        override val trace: Trace,
    ) : Traceable

    /**
     * A wrapper around [ShadowModuleSources] that was validated
     * and pre-resolved with frontend-level data ([from]).
     *
     * [ShadowModuleSources.sourceDirectories] must be set to finish resolution.
     */
    class ModuleSourcesRequest(
        val node: ShadowModuleSources,
        val from: AmperModule,
        val propertyLocation: List<String>,
    )

    /**
     * A wrapper around [ShadowClasspath] that was validated
     * and pre-resolved with frontend-level data ([localDependencies]).
     *
     * [ShadowClasspath.resolvedFiles] must be set to finish resolution.
     */
    class ClasspathRequest(
        val node: ShadowClasspath,
        val localDependencies: List<AmperModule>,
        val externalDependencies: List<MavenCoordinates>,
        val propertyLocation: List<String>,
    )

    /**
     * A wrapper around [ShadowCompilationArtifact] that was validated
     * and pre-resolved with frontend-level data ([from]).
     *
     * [ShadowCompilationArtifact.artifact] must be set to finish resolution.
     */
    class CompilationResultRequest(
        val node: ShadowCompilationArtifact,
        val from: AmperModule,
    )
}