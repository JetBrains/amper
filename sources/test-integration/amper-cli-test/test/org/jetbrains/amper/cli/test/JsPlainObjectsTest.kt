/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.Test

class JsPlainObjectsTest : AmperCliTestBase() {

    @Test
    fun `jsPlainObjects work when enabled`() = runSlowTest {
        val result = runCli(
            testProject(name = "kotlin-jsplainobjects"),
            "build", // TODO use 'run' when we support running JS apps
            assertEmptyStdErr = false, // the Kotlin/JS compiler currently logs info stuff to stderr for some reason
        )

        result.assertStdoutContains("Build successful")
    }
}
