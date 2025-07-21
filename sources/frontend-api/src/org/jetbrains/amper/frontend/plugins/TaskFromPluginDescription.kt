/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.plugins

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import java.nio.file.Path

/**
 * A custom task information that comes from a plugin.
 * It fully defines a single registered task instance.
 */
interface TaskFromPluginDescription {
    /**
     * Unique task name.
     */
    val name: TaskName

    /**
     * JVM reflection name of the class (Kotlin file facade)
     * to load that contains the [action function][actionFunctionJvmName].
     */
    val actionClassJvmName: String

    /**
     * JVM name of the static method inside the [Kotlin file facade][actionClassJvmName] that is the task action.
     */
    val actionFunctionJvmName: String

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
    val actionArguments: Map<String, Any?>

    val explicitDependsOn: List<String>

    /**
     * Paths (from the [actionArguments]) that are to be considered as inputs to the task action.
     */
    val inputs: List<Path>

    /**
     * Paths (from the [actionArguments]) that are to be considered as outputs to the task action as keys.
     * These paths are optionally marked with the additional semantics.
     */
    val outputs: Map<Path, OutputMark?>

    /**
     * Local plugin module which runtime classpath contains the [actionClassJvmName].
     * It needs to be built before the task can be executed.
     */
    val codeSource: AmperModule

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
}