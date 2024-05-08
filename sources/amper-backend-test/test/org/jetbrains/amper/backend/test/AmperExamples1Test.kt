/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.util.OS
import org.junit.jupiter.api.Assumptions
import java.nio.file.Path
import java.util.*
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains

// TODO review and merge with AmperExamples2Test
// Runs examples-standalone under current backend
class AmperExamples1Test: AmperCliTestBase() {
    @Test
    fun `compose-multiplatform`() = runTestInfinitely {
        Assumptions.assumeFalse(OS.isWindows, "Skip test on Windows, fix AMPER-527 and remove this line")
        runCli(projectName, "build")
        // TODO Assert output
        runCli(projectName, "test")
    }

    @Test
    fun `compose-multiplatform_build-android`() = runTestInfinitely {
        Assumptions.assumeFalse(OS.isWindows, "Skip test on Windows, fix AMPER-527 and remove this line")
        runCli(projectName, "task", ":android-app:buildAndroidDebug") // check AMPER-529
    }

    @Test
    fun `compose-desktop`() = runTestInfinitely {
        runCli(projectName, "build")
        // TODO Can we run it somehow?
    }

    @Test
    fun `compose-android`() = runTestInfinitely {
        Assumptions.assumeFalse(OS.isWindows, "Skip test on Windows, fix AMPER-527 and remove this line")
        runCli(projectName, "build")
        // TODO Can we run it somehow?
        runCli(projectName, "task", ":$projectName:buildAndroidDebug") // check AMPER-529
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
        val methods = javaClass.declaredMethods.map { it.name.substringBefore("_") }.toSet()

        for (entry in testDataRoot.listDirectoryEntries().filter { it.isDirectory() }) {
            assertContains(methods, entry.name, "Example '${entry.pathString}' is not covered by test '${javaClass.name}'. " +
                    "Please add a test method named '${entry.name}'")
        }
    }

    private val projectName: String
        get() = currentTestName.substringBefore("_")

    override val testDataRoot: Path = TestUtil.amperCheckoutRoot.resolve("examples-standalone")
}