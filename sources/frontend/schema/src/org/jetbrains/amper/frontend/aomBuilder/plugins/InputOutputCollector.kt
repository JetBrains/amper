/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins

import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.plugins.generated.ShadowClasspath
import org.jetbrains.amper.frontend.plugins.generated.ShadowCompilationArtifact
import org.jetbrains.amper.frontend.plugins.generated.ShadowModuleSources
import org.jetbrains.amper.plugins.schema.model.InputOutputMark
import java.nio.file.Path

/**
 * Collects @Input/@Output marked Path values from the [value].
 * The result is accessible via the [allInputPaths] and [allOutputPaths] properties.
 */
internal class InputOutputCollector(
    value: Any?,
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
        value: Any?,
        mark: InputOutputMark?,
        location: List<String>,
    ) {
        when(value) {
            is SchemaNode -> value.valueHolders.forEach { (name, holder) ->
                val property = value.schemaType.getProperty(name)
                gatherPaths(
                    value = holder.value,
                    mark = property?.inputOutputMark ?: mark,
                    location = location + name,
                )
                when (value) {
                    is ShadowClasspath -> _classpathNodes.add(NodeWithPropertyLocation(value, location))
                    is ShadowModuleSources -> _moduleSourcesNodes.add(NodeWithPropertyLocation(value, location))
                    is ShadowCompilationArtifact -> _compilationArtifactNodes.add(value)
                }
            }
            is Map<*, *> -> value.forEach { (key, value) ->
                gatherPaths(value = value, mark = mark, location = location + key.toString())
            }
            is Collection<*> -> value.forEachIndexed { i, value ->
                gatherPaths(value = value, mark = mark, location = location + i.toString())
            }
            is Path -> if (mark != InputOutputMark.ValueOnly && mark != null) {
                error("Not reached: untraceable marked path")
            }
            is TraceablePath -> when (mark) {
                InputOutputMark.Input -> _allInputPaths.add(InputPath(value))
                InputOutputMark.InputNoDependencyInference -> _allInputPaths.add(InputPath(value, false))
                InputOutputMark.Output -> _allOutputPaths.add(value)
                InputOutputMark.ValueOnly, null -> {}
            }
        }
    }
}