/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins

import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.plugins.schema.model.InputOutputMark
import java.nio.file.Path

/**
 * See [gatherPaths]
 */
internal class InputOutputPathsCollector {
    private val _allInputPaths = mutableSetOf<Path>()
    private val _allOutputPaths = mutableSetOf<Path>()

    val allInputPaths: Set<Path>
        get() = _allInputPaths

    val allOutputPaths: Set<Path>
        get() = _allOutputPaths

    /**
     * Collects @Input/@Output marked Path values from the [value].
     * The result is accessible via the [allInputPaths] and [allOutputPaths] properties.
     */
    fun gatherPaths(value: Any?) {
        gatherPaths(value, mark = null)
    }

    fun gatherPaths(
        value: Any?,
        mark: InputOutputMark?,
    ) {
        when(value) {
            is SchemaNode -> value.valueHolders.forEach { (name, holder) ->
                val property = value.schemaType.getProperty(name)
                gatherPaths(holder.value, property?.inputOutputMark ?: mark)
            }
            is Map<*, *> -> value.values.forEach { gatherPaths(it, mark) }
            is Collection<*> -> value.forEach { gatherPaths(it, mark) }
            is Path -> when (mark) {
                InputOutputMark.Input -> _allInputPaths.add(value)
                InputOutputMark.Output -> _allOutputPaths.add(value)
                InputOutputMark.ValueOnly, null -> {}
            }
        }
    }
}