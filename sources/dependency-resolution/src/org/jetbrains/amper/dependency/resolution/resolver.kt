/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.dependency.resolution

import java.util.*

class Resolver(val root: DependencyNode) {

    fun buildGraph(level: ResolutionLevel = ResolutionLevel.NETWORK): Resolver {
        val nodes = mutableMapOf<Key<*>, LinkedHashSet<DependencyNode>>()
        val conflicts = mutableSetOf<Key<*>>()
        val queue = LinkedList(listOf(root))
        do {
            conflicts.clear()
            while (queue.isNotEmpty()) {
                val node = queue.remove()
                val candidates = nodes.computeIfAbsent(node.key) { LinkedHashSet() }.also { it += node }
                if (node.key in conflicts || candidates.haveConflicts()) {
                    conflicts += node.key
                    continue
                }
                if (node.state < level.state) {
                    node.resolve(level)
                }
                node.level = level
                queue.addAll(node.children)
            }
            for (key in conflicts) {
                val candidates = nodes[key] ?: throw AmperDependencyResolutionException("Nodes are missing for $key")
                candidates.resolveConflict()
                queue.addAll(candidates)
            }
        } while (conflicts.isNotEmpty())
        return this
    }

    private fun LinkedHashSet<DependencyNode>.haveConflicts() =
        root.context.settings.conflictResolutionStrategies
            .filter { it.isApplicableFor(this) }
            .any { it.seesConflictsIn(this) }

    private fun LinkedHashSet<DependencyNode>.resolveConflict() {
        root.context.settings.conflictResolutionStrategies
            .filter { it.isApplicableFor(this) }
            .find { it.seesConflictsIn(this) }
            ?.resolveConflictsIn(this)
    }

    fun downloadDependencies(): Resolver = root.asSequence().forEach { it.downloadDependencies() }.let { this }
}

interface DependencyNode {

    val context: Context
    val key: Key<*>
    var state: ResolutionState
    var level: ResolutionLevel
    val children: List<DependencyNode>
    val messages: List<Message>

    fun resolve(level: ResolutionLevel)
    fun downloadDependencies()

    fun asSequence(): Sequence<DependencyNode> = sequence {
        val queue = LinkedList(listOf(this@DependencyNode))
        while (queue.isNotEmpty()) {
            val node = queue.remove()
            yield(node)
            queue.addAll(node.children)
        }
    }

    fun prettyPrint(): String = buildString { prettyPrint(this) }

    private fun prettyPrint(
        builder: StringBuilder,
        indent: StringBuilder = StringBuilder(),
        visited: MutableSet<Key<*>> = mutableSetOf(),
        addLevel: Boolean = false,
    ) {
        builder.append(indent).append(toString())

        val seen = !visited.add(key)
        if (seen && children.isNotEmpty()) {
            builder.append(" (*)")
        }
        builder.append('\n')
        if (seen || children.isEmpty()) {
            return
        }

        if (indent.isNotEmpty()) {
            indent.setLength(indent.length - 5)
            if (addLevel) {
                indent.append("|    ")
            } else {
                indent.append("     ")
            }
        }

        children.forEachIndexed { i, it ->
            val addAnotherLevel = i < children.size - 1
            if (addAnotherLevel) {
                indent.append("+--- ")
            } else {
                indent.append("\\--- ")
            }
            it.prettyPrint(builder, indent, visited, addAnotherLevel)
            indent.setLength(indent.length - 5)
        }
    }
}

enum class ResolutionState {
    UNKNOWN, UNSURE, RESOLVED
}

enum class ResolutionLevel(val state: ResolutionState) {
    CREATED(ResolutionState.UNKNOWN),
    LOCAL(ResolutionState.UNSURE),
    NETWORK(ResolutionState.RESOLVED),
}

class Progress

class AmperDependencyResolutionException(message: String) : RuntimeException(message)
