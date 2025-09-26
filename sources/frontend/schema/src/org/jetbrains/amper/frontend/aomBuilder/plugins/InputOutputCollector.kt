/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins

import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.plugins.generated.ShadowClasspath
import org.jetbrains.amper.frontend.plugins.generated.ShadowModuleSources
import org.jetbrains.amper.plugins.schema.model.InputOutputMark
import java.nio.file.Path

/**
 * See [gatherPaths]
 */
internal class InputOutputCollector {
    private val _allInputPaths = mutableSetOf<Path>()
    private val _allOutputPaths = mutableSetOf<Path>()
    private val _classpathNodes = mutableSetOf<NodeWithPropertyLocation<ShadowClasspath>>()
    private val _moduleSourcesNodes = mutableSetOf<ShadowModuleSources>()

    /**
     * @property propertyLocation a property path in a data tree, e.g. `[settings, myMap, myKey, 0]` of the [node].
     */
    data class NodeWithPropertyLocation<T : SchemaNode>(
        val node: T,
        val propertyLocation: List<String>,
    )

    val classpathNodes: Set<NodeWithPropertyLocation<ShadowClasspath>>
        get() = _classpathNodes

    val moduleSourcesNodes: Set<ShadowModuleSources>
        get() = _moduleSourcesNodes

    val allInputPaths: Set<Path>
        get() = _allInputPaths

    val allOutputPaths: Set<Path>
        get() = _allOutputPaths

    /**
     * Collects @Input/@Output marked Path values from the [value].
     * The result is accessible via the [allInputPaths] and [allOutputPaths] properties.
     */
    fun gatherPaths(value: Any?) {
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
                    is ShadowModuleSources -> _moduleSourcesNodes.add(value)
                }
            }
            is Map<*, *> -> value.forEach { (key, value) ->
                gatherPaths(value = value, mark = mark, location = location + key.toString())
            }
            is Collection<*> -> value.forEachIndexed { i, value ->
                gatherPaths(value = value, mark = mark, location = location + i.toString())
            }
            is Path -> when (mark) {
                InputOutputMark.Input -> _allInputPaths.add(value)
                InputOutputMark.Output -> _allOutputPaths.add(value)
                InputOutputMark.ValueOnly, null -> {}
            }
        }
    }
}