/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helper

import org.jetbrains.amper.frontend.old.helper.BuildFileAware
import org.jetbrains.amper.frontend.old.helper.TestWithBuildFile
import org.jetbrains.amper.frontend.schemaConverter.convertModule
import java.nio.file.Path
import kotlin.io.path.reader

context(TestWithBuildFile)
fun convertTest(caseName: String, expectedErrors: String) =
    FailedConvertTestRun(caseName, expectedErrors).doTest()

class FailedConvertTestRun(
    caseName: String,
    private val expectedErrors: String,
) : BaseTestRun(caseName) {

    override fun getInputContent(path: Path): String {
        with(ctx) { convertModule { path.reader() } }
        return ctx.problemReporter.getErrors().map { it.message }.joinToString()
    }

    override fun getExpectContent(path: Path) = expectedErrors
}