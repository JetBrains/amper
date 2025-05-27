/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.builders.schema.jsonSchemaString
import org.jetbrains.amper.frontend.meta.ATypesDiscoverer
import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.helper.doTestWithInput
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test


class BuildJsonSchemaTest : TestBase() {

    @Test
    fun `build module schema`() = doTest("amper.module") { jsonSchemaString(ATypesDiscoverer.aType<Module>()) }

    @Test
    fun `build template schema`() = doTest("amper.template") { jsonSchemaString(ATypesDiscoverer.aType<Template>()) }

    @Test
    fun `build project schema`() = doTest("amper.project") { jsonSchemaString(ATypesDiscoverer.aType<Project>()) }

    private fun doTest(caseName: String, input: () -> String) =
        doTestWithInput(caseName, ".json", Path("resources") / "schema", input)
}