/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test.helper

import org.jetbrains.amper.cli.test.AmperCliTestBase
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.test.AmperCliResult
import org.jetbrains.amper.test.golden.BaseTestRun
import org.jetbrains.amper.test.golden.GoldenTest
import org.jetbrains.amper.test.golden.readContentsAndReplace
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

fun AmperCliTestBase.cliTest(
    caseName: String,
    systemInfo: SystemInfo = DefaultSystemInfo,
    expectedError: String? = null,
    cliResult: AmperCliResult
) = CliTestRun(caseName, systemInfo, baseTestResourcesPath(), expectedError, cliResult)
    .doTest()

open class CliTestRun(
    caseName: String,
    private val systemInfo: SystemInfo,
    override val base: Path,
    private val expectedError: String? = null,
    private val cliResult: AmperCliResult
) : BaseTestRun(caseName) {
    override fun GoldenTest.getInputContent(inputPath: Path): String {
        println(cliResult.stdout.replaceCliPrefix())
        return cliResult.stdout.replaceCliPrefix()
    }

    override fun GoldenTest.getExpectContent(expectedPath: Path): String {
        // This is the actual check.
        if (!expectedPath.exists()) expectedPath.writeText("")
        return readContentsAndReplace(expectedPath, base)
    }

    private fun String.replaceCliPrefix(): String =
        this.replaceBefore("Dependencies of module root:", "")
}