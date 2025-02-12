/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.telemetry.getAttribute
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.MacOnly
import org.jetbrains.amper.test.spans.SpansTestCollector
import org.junit.jupiter.api.TestInfo
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

class AmperProjectTemplatesTest : AmperCliTestBase() {
    // Please add as many checks as possible to template tests

    private fun templateNameFromTestName(name: String) = name.substringBefore(' ')

    @Test
    fun `all templates are covered`() {
        val methods = javaClass.declaredMethods.map { templateNameFromTestName(it.name) }.toSet()

        val templatesRoot = Dirs.amperSourcesRoot.resolve("cli/resources/templates")
        val entries = templatesRoot.listDirectoryEntries()
        check(entries.size > 3) {
            "Possibly incorrect templates root: $templatesRoot"
        }
        check(entries.any { it.name == "jvm-cli" } && entries.any { it.name == "multiplatform-cli" }) {
            "Does not look like a templates root: $templatesRoot"
        }

        for (entry in entries) {
            assertContains(methods, entry.name, "Template '${entry.pathString}' is not covered by test '${javaClass.name}'. " +
                    "Please add a test method named '${entry.name}'")
        }
    }

    @Test
    fun `kmp-lib`(testInfo: TestInfo) = runSlowTest {
        runInitForTemplateFromTestName(testInfo)
        // Can't easily get rid of output associated with
        // class 'World': expect and corresponding actual are declared in the same module, which will be prohibited in Kotlin 2.0.
        // See https://youtrack.jetbrains.com/issue/KT-55177
        runCli(tempRoot, "build", assertEmptyStdErr = false)
    }

    @Test
    fun `jvm-cli`(testInfo: TestInfo) = runSlowTest {
        runInitForTemplateFromTestName(testInfo)
        runCli(tempRoot, "build")
    }

    @Test
    fun `multiplatform-cli`(testInfo: TestInfo) = runSlowTest {
        runInitForTemplateFromTestName(testInfo)
        // Can't easily get rid of output associated with
        // class 'World': expect and corresponding actual are declared in the same module, which will be prohibited in Kotlin 2.0.
        // See https://youtrack.jetbrains.com/issue/KT-55177
        runCli(tempRoot, "build", assertEmptyStdErr = false, customAmperScriptPath = null)
    }

    @Test
    @MacOnly
    fun `compose-multiplatform`(testInfo: TestInfo) = runSlowTest {
        runInitForTemplateFromTestName(testInfo)
        val result = runCli(tempRoot, "build", assertEmptyStdErr = false)
        result.readTelemetrySpans().assertXcodeProjectIsValid()
    }

    @Test
    @MacOnly
    fun `compose-multiplatform - build debug with xcodebuild`(testInfo: TestInfo) = runSlowTest {
        runInitForTemplateFromTestName(testInfo)

        val buildDir = tempRoot / "build" / "xcode"
        val result = runXcodebuild(
            "-project", "ios-app/module.xcodeproj",
            "-scheme", "app",
            "-configuration", "Debug",
            "-arch", "arm64",
            "-sdk", "iphonesimulator",
            "-derivedDataPath", buildDir.pathString,
        )
        expect(0) { result.exitCode }
        assertTrue {
            val appPath = buildDir / "Build" / "Products" / "Debug-iphonesimulator" / "ios-app.app"
            appPath.isDirectory()
        }
    }

    @Test
    @MacOnly
    fun `compose-multiplatform - build release with xcodebuild`(testInfo: TestInfo) = runSlowTest {
        runInitForTemplateFromTestName(testInfo)

        val buildDir = tempRoot / "build" / "xcode"
        val result = runXcodebuild(
            "-project", "ios-app/module.xcodeproj",
            "-scheme", "app",
            "-configuration", "Release",
            "-arch", "arm64",
            // Sdk is from the project
            "-derivedDataPath", buildDir.pathString,
            "CODE_SIGNING_ALLOWED=NO",  // To build real device arch
        )

        expect(0) { result.exitCode }
        assertContains(result.stdout, "Amper doesn't yet support building Kotlin for `Release` configuration. " +
                "Falling back to `Debug`")
        assertTrue {
            val appPath = buildDir / "Build" / "Products" / "Release-iphoneos" / "ios-app.app"
            appPath.isDirectory()
        }
    }

    @Test
    fun `compose-desktop`(testInfo: TestInfo) = runSlowTest {
        runInitForTemplateFromTestName(testInfo)
        runCli(tempRoot, "build")
    }

    @Test
    fun `compose-android`(testInfo: TestInfo) = runSlowTest {
        runInitForTemplateFromTestName(testInfo)
        runCli(tempRoot, "build")
    }

    @Test
    @MacOnly
    fun `compose-ios`(testInfo: TestInfo) = runSlowTest {
        runInitForTemplateFromTestName(testInfo)
        // Temporary disable stdErr assertions because linking and xcodebuild produce some warnings
        // that are treated like errors.
        val result = runCli(tempRoot, "build", assertEmptyStdErr = false)
        result.readTelemetrySpans().assertXcodeProjectIsValid()
    }

    @Test
    @MacOnly
    fun `compose-ios - build debug with xcodebuild`(testInfo: TestInfo) = runSlowTest {
        runInitForTemplateFromTestName(testInfo)
        val buildDir = tempRoot / "build" / "xcode"
        val result = runXcodebuild(
            "-project", "module.xcodeproj",
            "-scheme", "app",
            "-configuration", "Debug",
            "-arch", "x86_64",
            "-sdk", "iphonesimulator",
            "-derivedDataPath", buildDir.pathString,
        )
        expect(0) { result.exitCode }
        assertTrue {
            val appPath = buildDir / "Build" / "Products" / "Debug-iphonesimulator" / "compose-ios.app"
            appPath.isDirectory()
        }
    }

    private suspend fun runInitForTemplateFromTestName(testInfo: TestInfo) {
        runCli(tempRoot, "init", templateNameFromTestName(testInfo.testMethod.get().name))
    }

    private fun SpansTestCollector.assertXcodeProjectIsValid() {
        // Xcode project should be generated correctly by `init` and thus not updated by the build.
        assertFalse { xcodeProjectManagementSpans.assertSingle().getAttribute(UpdatedAttribute) }
    }
}
