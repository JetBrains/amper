/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

data class TaskName(val name: String): Comparable<TaskName> {
    init {
        require(name.isNotBlank())
    }

    override fun compareTo(other: TaskName): Int = name.compareTo(other.name)

    companion object {
        fun fromHierarchy(path: List<String>) = TaskName(path.joinToString(":", prefix = ":"))
        fun fromHierarchy(vararg path: String) = fromHierarchy(path.toList())
    }
}
