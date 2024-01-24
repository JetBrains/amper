/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.builders.NestedCompletionSchemaBuilder
import org.jetbrains.amper.frontend.builders.iterateThrough
import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.helper.doTestWithInput
import kotlin.test.Test


class BuildNestedCompletionSchemaTest : TestBase() {

    @Test
    fun `build nested completion schema test`(): Unit = doTestWithInput("amper.nested.completion.schema", ".txt") {
        val root = NestedCompletionSchemaBuilder.buildNestedCompletionTree(Module::class).currentNode
        val result = StringBuilder()
        root.iterateThrough { node, level ->
            result.append("${ident(level)}${node.name} (isRegExp=${node.isRegExp},isolated=${node.isolated})${System.lineSeparator()}")
        }
        result.toString()
    }

    private fun ident(level: Int): String =
        StringBuilder().apply {
            var i = 0
            while (i++ < level) {
                append(" ")
            }
        }.toString()
}