/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import org.jetbrains.amper.test.TestUtil
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains

// Runs examples-standalone under current backend
class AmperExamplesTest: AmperCliTestBase() {
    @Test
    @Ignore("AMPER-526 Building of 'examples.pure/multiplatform' failed due to 'unresolved reference: Screen'")
    fun `compose-multiplatform`() = runTestInfinitely {
        runCli(projectName, "build")
        // TODO Assert output
        runCli(projectName, "run")
        runCli(projectName, "test")
    }

    @Test
    @Ignore("AMPER-525 The process cannot access the file because another process has locked a portion of the file")
    fun `compose-desktop`() = runTestInfinitely {
        runCli(projectName, "build")
        // TODO Can we run it somehow?
    }

    @Test
    @Ignore("AMPER-527 Test AmperExamplesTest.compose-android is flaky: DirectoryNotEmptyException")
    fun `compose-android`() = runTestInfinitely {
        runCli(projectName, "build")
        // TODO Can we run it somehow?
    }

    @Test
    @Ignore("AMPER-534 Can't build a basic iOS project")
    fun `compose-ios`() = runTestInfinitely {
        runCli(projectName, "build")
        // TODO Can we run it somehow?
    }

    @Test
    fun jvm() = runTestInfinitely {
        runCli(projectName, "build")
        // TODO Assert output
        runCli(projectName, "run")
        runCli(projectName, "test")
    }

    @Test
    fun `new-project-template`() = runTestInfinitely {
        runCli(projectName, "build")
        // TODO Assert output
        runCli(projectName, "run")
        runCli(projectName, "test")
    }

    @Test
    fun `all examples are covered`() {
        val methods = javaClass.declaredMethods.map { it.name }.toSet()

        for (entry in testDataRoot.listDirectoryEntries().filter { it.isDirectory() }) {
            assertContains(methods, entry.name, "Example '${entry.pathString}' is not covered by test '${javaClass.name}'. " +
                    "Please add a test method named '${entry.name}'")
        }
    }

    private val projectName: String
        get() = currentTestName

    override val testDataRoot: Path = TestUtil.amperCheckoutRoot.resolve("examples-standalone")
}