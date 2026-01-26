/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven

import org.jetbrains.amper.frontend.contexts.Context
import org.jetbrains.amper.frontend.tree.KeyValue
import org.jetbrains.amper.frontend.tree.ListNode
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.ScalarNode
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.frontend.tree.copyWithContexts
import org.jetbrains.amper.frontend.tree.copyWithValue
import org.jetbrains.amper.frontend.tree.valueEqualsTo

fun TreeNode.filterByContext(
    context: Context,
    against: TreeNode? = null,
): TreeNode? {
    return when (this@filterByContext) {
        is ListNode -> {
            val mainList = against as? ListNode

            val reducedChildren = children.mapNotNull { it.filterByContext(context) }
            if (reducedChildren.isEmpty()) {
                null
            } else if (mainList != null && listsAreEqual(reducedChildren, mainList.children)) {
                null
            } else {
                ListNode(reducedChildren, type, trace, listOf(context))
            }
        }
        is MappingNode -> {
            val mainMap = against as? MappingNode
            val mainByKey = mainMap?.children?.associateBy { it.key } ?: emptyMap()

            val reducedChildren = children.mapNotNull { child ->
                val mainChild = mainByKey[child.key]
                child.filterByContext(context, mainChild?.value)
            }

            if (reducedChildren.isEmpty()) {
                null
            } else {
                MappingNode(reducedChildren, type, trace, listOf(context))
            }
        }
        is ScalarNode -> {
            if (contexts.contains(context)) {
                if (against is ScalarNode && this valueEqualsTo against) {
                    return null
                }
                return copyWithContexts(listOf(context))
            }
            null
        }
        else -> {
            null
        }
    }
}

private fun listsAreEqual(a: List<TreeNode>, b: List<TreeNode>): Boolean {
    if (a.size != b.size) return false
    return a.zip(b).all { (aChild, bChild) -> treesAreEqual(aChild, bChild) }
}

private fun treesAreEqual(a: TreeNode, b: TreeNode): Boolean {
    return when (a) {
        is ScalarNode if b is ScalarNode -> a valueEqualsTo b
        is ListNode if b is ListNode -> listsAreEqual(a.children, b.children)
        is MappingNode if b is MappingNode -> {
            if (a.children.size != b.children.size) return false
            val bByKey = b.children.associateBy { it.key }
            a.children.all { aChild ->
                val bChild = bByKey[aChild.key]
                bChild != null && treesAreEqual(aChild.value, bChild.value)
            }
        }
        else -> false
    }
}

fun KeyValue.filterByContext(
    context: Context,
    mainValue: TreeNode? = null,
): KeyValue? {
    val selectedContext = value.filterByContext(context, mainValue)
    if (selectedContext != null) {
        return copyWithValue(selectedContext)
    }
    return null
}
