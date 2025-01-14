/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.test.Dirs
import java.nio.file.Path
import kotlin.test.Test

class AmperTestFormatTest : AmperCliTestBase() {

    override val testDataRoot: Path = Dirs.amperTestProjectsRoot

    @Test
    fun `junit tests should print teamcity service messages`() = runSlowTest {
        val r = runCli("multiplatform-tests", "test", "-m", "jvm-cli", "--format=teamcity")
        r.assertSomeStdoutLineContains("##teamcity[testStarted name='JvmIntegrationTest.integrationTest' timestamp='")
        r.assertSomeStdoutLineContains("##teamcity[testFinished name='JvmIntegrationTest.integrationTest' duration='")
        r.assertSomeStdoutLineContains("##teamcity[testStarted name='JvmIntegrationTest.integrationTest' timestamp='")
    }

    // TODO test more exhaustively
}
