/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.plugins

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.plugins.generated.ShadowClasspath
import org.jetbrains.amper.frontend.plugins.generated.ShadowModuleSources
import java.nio.file.Path

/**
 * A custom task information that comes from a plugin.
 * It fully defines a single registered task instance.
 */
class TaskFromPluginDescription(
    /**
     * Unique task name.
     */
    val name: TaskName,

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
     * Action argument values that are supplied by the *frontend*.
     *
     * The values are:
     * - primitive
     * - Path
     * - `SchemaNode` objects
     * - String (for enums also)
     *
     * Backend may need
     * to convert some arguments before passing it to the [action method][actionFunctionJvmName].
     */
    val actionArguments: Map<String, Any?>,

    val explicitDependsOn: List<String>,

    /**
     * Paths (from the [actionArguments]) that are to be considered as inputs to the task action.
     */
    val inputs: List<Path>,

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
     * Paths (from the [actionArguments]) that are to be considered as outputs to the task action as keys.
     * These paths are optionally marked with the additional semantics.
     */
    val outputs: Map<Path, OutputMark?>,

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
    )

    /**
     * A wrapper around [ShadowModuleSources] that was validated
     * and pre-resolved with frontend-level data ([from]).
     *
     * [ShadowModuleSources.sourceDirectories] must be set to finish resolution.
     */
    class ModuleSourcesRequest(
        val node: ShadowModuleSources,
        val from: AmperModule,
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
        val externalDependencies: List<String>,
    )
}