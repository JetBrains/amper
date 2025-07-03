/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.builders.schema.jsonSchemaString
import org.jetbrains.amper.frontend.meta.DefaultSchemaTypingContext
import org.jetbrains.amper.frontend.schema.helper.doTestWithInput
import org.jetbrains.amper.frontend.types.getDeclaration
import org.jetbrains.amper.test.golden.GoldenTestBase
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

class BuildJsonSchemaTest : GoldenTestBase() {

    @Test
    fun `build module schema`() =
        doTest("amper.module") { jsonSchemaString(DefaultSchemaTypingContext.getDeclaration<Module>()) }

    @Test
    fun `build template schema`() =
        doTest("amper.template") { jsonSchemaString(DefaultSchemaTypingContext.getDeclaration<Template>()) }

    @Test
    fun `build project schema`() =
        doTest("amper.project") { jsonSchemaString(DefaultSchemaTypingContext.getDeclaration<Project>()) }

    private fun doTest(caseName: String, input: () -> String) =
        doTestWithInput(caseName, ".json", Path("resources") / "schema", input)
}