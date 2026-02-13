/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins

import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.plugins.generated.ShadowClasspath
import org.jetbrains.amper.frontend.plugins.generated.ShadowCompilationArtifact
import org.jetbrains.amper.frontend.plugins.generated.ShadowModuleSources
import org.jetbrains.amper.frontend.tree.CompleteListNode
import org.jetbrains.amper.frontend.tree.CompleteMapNode
import org.jetbrains.amper.frontend.tree.CompleteObjectNode
import org.jetbrains.amper.frontend.tree.CompleteTreeNode
import org.jetbrains.amper.frontend.tree.PathNode
import org.jetbrains.amper.frontend.tree.traceableValue
import org.jetbrains.amper.plugins.schema.model.InputOutputMark

/**
 * Collects @Input/@Output marked Path values from the [value].
 * The result is accessible via the [allInputPaths] and [allOutputPaths] properties.
 */
internal class InputOutputCollector(
    value: CompleteObjectNode,
) {
    private val _allInputPaths = mutableListOf<InputPath>()
    private val _allOutputPaths = mutableListOf<TraceablePath>()
    private val _classpathNodes = mutableSetOf<NodeWithPropertyLocation<ShadowClasspath>>()
    private val _moduleSourcesNodes = mutableSetOf<NodeWithPropertyLocation<ShadowModuleSources>>()
    private val _compilationArtifactNodes = mutableSetOf<ShadowCompilationArtifact>()

    /**
     * @property propertyLocation a property path in a data tree, e.g. `[settings, myMap, myKey, 0]` of the [node].
     */
    data class NodeWithPropertyLocation<T : SchemaNode>(
        val node: T,
        val propertyLocation: List<String>,
    )

    data class InputPath(
        val path: TraceablePath,
        val inferTaskDependency: Boolean = true,
    )

    val classpathNodes: Set<NodeWithPropertyLocation<ShadowClasspath>>
        get() = _classpathNodes

    val moduleSourcesNodes: Set<NodeWithPropertyLocation<ShadowModuleSources>>
        get() = _moduleSourcesNodes

    val compilationArtifactNodes: Set<ShadowCompilationArtifact>
        get() = _compilationArtifactNodes

    val allInputPaths: List<InputPath>
        get() = _allInputPaths

    val allOutputPaths: List<TraceablePath>
        get() = _allOutputPaths

    init {
        gatherPaths(value, mark = null, location = listOf())
    }

    private fun gatherPaths(
        value: CompleteTreeNode,
        mark: InputOutputMark?,
        location: List<String>,
    ) {
        when(value) {
            is CompleteObjectNode -> value.refinedChildren.forEach { (name, keyValue) ->
                gatherPaths(
                    value = keyValue.value,
                    mark = keyValue.propertyDeclaration.inputOutputMark ?: mark,
                    location = location + name,
                )
                when (val instance = value.instance) {
                    is ShadowClasspath -> _classpathNodes.add(NodeWithPropertyLocation(instance, location))
                    is ShadowModuleSources -> _moduleSourcesNodes.add(NodeWithPropertyLocation(instance, location))
                    is ShadowCompilationArtifact -> _compilationArtifactNodes.add(instance)
                }
            }
            is CompleteMapNode-> value.refinedChildren.forEach { (key, keyValue) ->
                gatherPaths(value = keyValue.value, mark = mark, location = location + key)
            }
            is CompleteListNode -> value.children.forEachIndexed { i, value ->
                gatherPaths(value = value, mark = mark, location = location + i.toString())
            }
            is PathNode -> when (mark) {
                InputOutputMark.Input -> _allInputPaths.add(InputPath(value.traceableValue))
                InputOutputMark.InputNoDependencyInference -> _allInputPaths.add(InputPath(value.traceableValue, false))
                InputOutputMark.Output -> _allOutputPaths.add(value.traceableValue)
                InputOutputMark.ValueOnly, null -> {}
            }
            else -> {}
        }
    }
}