/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.test.MacOnly
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.test.TestUtil.runTestInfinitely
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains

// TODO review and merge with AmperExamples2Test
// Runs examples-standalone under current backend
class AmperExamples1Test: AmperCliTestBase() {

    @Test
    fun `compose-multiplatform`() = runTestInfinitely {
        // Temporarily disable stdErr assertions because linking and xcodebuild produce some warnings
        // that are treated like errors.
        runCli(projectName, "build", assertEmptyStdErr = false)
        // Apple tests cannot run on all machines, so we don't run them in this test
        runCli(projectName, "test", "-p", "jvm", "-p", "android")
    }

    @Test
    @MacOnly
    fun `compose-multiplatform_apple-tests`() = runTestInfinitely {
        // TODO run ios simulator tests.
        // runCli(projectName, "test")
    }

    @Test
    fun `compose-multiplatform_buildAndroidDebug`() = runTestInfinitely {
        runCli(projectName, "task", ":android-app:buildAndroidDebug")
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
        runCli(projectName, "task", ":$projectName:buildAndroidDebug") // check AMPER-529
    }

    @Test
    @MacOnly
    fun `compose-ios`() = runTestInfinitely {
        // Temporary disable stdErr assertions because linking and xcodebuild produce some warnings
        // that are treated like errors.
        runCli(projectName, "build", "-p", "iosSimulatorArm64", assertEmptyStdErr = false)
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
