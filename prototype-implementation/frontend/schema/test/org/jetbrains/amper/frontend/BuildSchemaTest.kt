/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.builders.DocBuilder.Companion.buildDoc
import org.jetbrains.amper.frontend.builders.SchemaBuilder
import org.jetbrains.amper.frontend.schema.Module
import java.io.StringWriter
import kotlin.test.Test

class BuildSchemaTest {

    @Test
    fun `build schema test`() {
        // Check whenever schema build does not fail.
        val x = SchemaBuilder.writeSchema(Module::class)
        println(x)
    }

}