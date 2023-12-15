/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.builders.DocBuilder.Companion.buildDoc
import org.jetbrains.amper.frontend.helper.doTestWithInput
import org.jetbrains.amper.frontend.old.helper.TestWithBuildFile
import org.jetbrains.amper.frontend.schema.Module
import java.io.StringWriter
import kotlin.test.Test

class BuildDocTest : TestWithBuildFile() {

    @Test
    fun `build doc test`(): Unit = doTestWithInput("doc-test", ".expected") {
        StringWriter().apply { buildDoc(Module::class, this) }.toString()
    }
}