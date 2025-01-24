/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.test.Dirs
import java.nio.file.Path
import kotlin.test.Test

class AmperTestBasicTest : AmperCliTestBase() {

    override val testDataRoot: Path = Dirs.amperTestProjectsRoot

    @Test
    fun `test using reflection`() = runSlowTest {
        runCli(backendTestProjectName = "jvm-test-using-reflection", "test", "--platform=jvm")
    }
}
