/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.builders.NestedCompletionBuilder
import org.jetbrains.amper.frontend.builders.NestedCompletionNode
import org.jetbrains.amper.frontend.builders.NestedCompletionSchemaBuilder
import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.helper.doTestWithInput
import kotlin.test.Test


class BuildNestedCompletionSchemaTest : TestBase() {

    @Test
    fun `build nested completion schema test`(): Unit = doTestWithInput("amper.nested.completion.schema", ".txt") {
        val root = NestedCompletionSchemaBuilder.buildNestedCompletionTree(Module::class).currentNode
        root.flattenHierarchyToLeveled().joinToString(System.lineSeparator()) { (node, level) ->
            "${"  ".repeat(level)}${node.name} (isRegExp=${node.isRegExp},isolated=${node.isolated})"
        }
    }

    @Test
    fun `test settings regex`() {
        val settingModifiersRegExp = NestedCompletionBuilder.modifiersRegExp("settings").toRegex()
        val testSettingModifiersRegExp = NestedCompletionBuilder.modifiersRegExp("test-settings").toRegex()
        assert(settingModifiersRegExp.matches("settings"))
        assert(settingModifiersRegExp.matches("settings@jvm"))
        assert(settingModifiersRegExp.matches("settings@AnDrOiDD"))
        assert(testSettingModifiersRegExp.matches("test-settings@AnDrOiDD"))
        assert(testSettingModifiersRegExp.matches("test-settings"))
    }

    private fun NestedCompletionNode.flattenHierarchyToLeveled(level: Int = 0): List<Pair<NestedCompletionNode, Int>> =
        children.flatMap { listOf(it to level) + it.flattenHierarchyToLeveled(level + 1) }
}