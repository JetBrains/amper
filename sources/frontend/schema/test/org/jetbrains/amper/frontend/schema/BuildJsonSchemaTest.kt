/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.builders.JsonSchemaBuilder
import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.helper.doTestWithInput
import java.io.StringWriter
import kotlin.io.path.Path
import kotlin.test.Test


class BuildJsonSchemaTest : TestBase() {

    @Test
    fun `build doc test`(): Unit = doTestWithInput("amper.module", ".json", Path("resources").resolve("schema")) {
        StringWriter().apply { JsonSchemaBuilder.writeSchema(Module::class, this) }.toString()
    }

    @Test
    fun `build template doc test`(): Unit = doTestWithInput("amper.template", ".json", Path("resources").resolve("schema")) {
        StringWriter().apply { JsonSchemaBuilder.writeSchema(Template::class, this) }.toString()
    }
}