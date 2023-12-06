/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.builders.JsonSchemaBuilder
import org.jetbrains.amper.frontend.schema.Module
import java.io.StringWriter
import java.nio.file.Path
import kotlin.test.Test

class BuildJsonSchemaTest : FileExpectTest(".expected.json", forceInput = false) {

    override fun getActualContent(input: Path): String =
        StringWriter().apply { JsonSchemaBuilder.writeSchema(Module::class, this) }.toString()

    @Test
    fun `build schema test`() = test("schema-test")

}