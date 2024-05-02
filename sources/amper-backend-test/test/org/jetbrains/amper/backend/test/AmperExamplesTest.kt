/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import org.jetbrains.amper.test.TestUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains

// Runs examples.pure under current backend
class AmperExamplesTest: AmperCliTestBase() {
    private lateinit var testInfo: TestInfo

    @BeforeEach
    fun before(testInfo: TestInfo) {
        this.testInfo = testInfo
    }

    @Test
    fun `compose-desktop`() = runTestInfinitely {
        runCli(projectName, "build")
        // TODO Can we run it somehow?
    }

    @Test
    fun `compose-android`() = runTestInfinitely {
        runCli(projectName, "build")
        // TODO Can we run it somehow?
    }

    @Test
    fun `jvm-hello-world`() = runTestInfinitely {
        runCli(projectName, "build")
        // TODO Assert output
        runCli(projectName, "run")
    }

    @Test
    fun `jvm-kotlin+java`() = runTestInfinitely {
        runCli(projectName, "build")
        // TODO Assert output
        runCli(projectName, "run")
        runCli(projectName, "test")
    }

    @Test
    fun `jvm-with-tests`() = runTestInfinitely {
        runCli(projectName, "build")
        // TODO Assert output
        runCli(projectName, "run")
        runCli(projectName, "test")
    }

    @Test
    fun modularized() = runTestInfinitely {
        runCli(projectName, "build")
        // TODO Assert output
        runCli(projectName, "run")
        runCli(projectName, "test")
    }

    @Test
    @Ignore("Currently fails")
    fun multiplatform() = runTestInfinitely {
        runCli(projectName, "build")
        // TODO Assert output
        runCli(projectName, "run")
        runCli(projectName, "test")
    }

    @Test
    fun `all examples are covered`() {
        val methods = javaClass.declaredMethods.map { it.name }.toSet()

        for (entry in testDataRoot.listDirectoryEntries()) {
            assertContains(methods, entry.name, "Example '${entry.pathString}' is not covered by test '${javaClass.name}'. " +
                    "Please add a test method named '${entry.name}'")
        }
    }

    private val projectName: String
        get() = testInfo.testMethod.get().name

    override val testDataRoot: Path = TestUtil.amperCheckoutRoot.resolve("examples.pure")
}