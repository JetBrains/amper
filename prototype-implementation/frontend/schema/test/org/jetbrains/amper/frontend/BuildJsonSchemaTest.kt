/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.builders.JsonSchemaBuilder
import org.jetbrains.amper.frontend.helper.doTestWithInput
import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.Module
import java.io.StringWriter
import kotlin.test.Test


class BuildJsonSchemaTest : TestBase() {

    @Test
    fun `build doc test`(): Unit = doTestWithInput("schema-test", ".expected.json") {
        StringWriter().apply { JsonSchemaBuilder.writeSchema(Module::class, this) }.toString()
    }
}