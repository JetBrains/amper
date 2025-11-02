/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins.taskGraph

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.plugins.TaskFromPluginDescription

/**
 * Frontend-level task graph model.
 * Accurate (sometimes conservatively around multiple platforms) around user-defined tasks that come from plugins.
 * The rest (builtin) things are modeled as far as required for nice and clear reporting.
 *
 * @see buildTaskGraph
 */
internal interface TaskGraph {
    /**
     * all task graph nodes
     */
    val nodes: List<Node>

    /**
     * returns all edges that are outgoing from the given node
     */
    operator fun get(node: Node): List<Edge>

    /**
     * [Traceable] graph edge with the [reason] why it exists.
     */
    class Edge(
        /**
         * The node the edge points to (depends on)
         */
        val target: Node,
        override val trace: Trace,
        /**
         * User-readable reason string provider
         */
        val reason: () -> String,
    ) : Traceable

    sealed interface Node {
        /**
         * Registered plugin task
         */
        data class PluginTask(
            val description: TaskFromPluginDescription,
        ) : Node

        /**
         * Module main (non-test) compilation step for [module].
         * Currently, all platforms are represented by the single node in the graph,
         * but in the future that may be changed if needed.
         */
        data class Compilation(
            val module: AmperModule,
        ) : Node

        /**
         * Module source (Java+Kotlin) generation step for non-test [module].
         * No distinction between platforms is made currently.
         */
        data class SourceGeneration(
            val module: AmperModule,
        ) : Node

        /**
         * Module resource generation step for non-test [module].
         * No distinction between platforms is made currently.
         */
        data class ResourceGeneration(
            val module: AmperModule,
        ) : Node
    }
}