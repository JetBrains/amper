/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.builders.DocBuilder.Companion.buildDoc
import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.helper.doTestWithInput
import java.io.StringWriter
import kotlin.test.Test

class BuildDocTest : TestBase() {

    @Test
    fun `build doc test`(): Unit = doTestWithInput("doc-test", ".expected") {
        StringWriter().apply { buildDoc(Module::class, this) }.toString()
    }
}