/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.diagnostics.getAttribute
import org.jetbrains.amper.test.MacOnly
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.test.TestUtil.runTestInfinitely
import org.jetbrains.amper.test.collectSpansFromCli
import org.jetbrains.amper.test.spans.SpansTestCollector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

private const val SUPPLEMENTAL_TAG = "supplemental"

class AmperProjectTemplatesTest : AmperCliTestBase() {
    // Please add as many checks as possible to template tests

    override val testDataRoot: Path
        get() = throw UnsupportedOperationException() // these tests don't use test projects

    @BeforeEach
    fun createFromTemplateAndBuild(testInfo: TestInfo) {
        if (testInfo.tags.contains(SUPPLEMENTAL_TAG)) return

        runBlocking {
            val templateName = testInfo.testMethod.get().name
            runCli(tempRoot, "init", templateName)
        }
    }

    @Test
    @Tag(SUPPLEMENTAL_TAG)
    fun `all templates are covered`() {
        val methods = javaClass.declaredMethods.map { it.name }.toSet()

        val templatesRoot = TestUtil.amperSourcesRoot.resolve("cli/resources/templates")
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
    fun `kmp-lib`() = runTestInfinitely {
        // Can't easily get rid of output associated with
        // class 'World': expect and corresponding actual are declared in the same module, which will be prohibited in Kotlin 2.0.
        // See https://youtrack.jetbrains.com/issue/KT-55177
        runCli(tempRoot, "build", assertEmptyStdErr = false)
    }

    @Test
    fun `jvm-cli`() = runTestInfinitely {
        runCli(tempRoot, "build")
    }

    @Test
    fun `multiplatform-cli`() = runTestInfinitely {
        // Can't easily get rid of output associated with
        // class 'World': expect and corresponding actual are declared in the same module, which will be prohibited in Kotlin 2.0.
        // See https://youtrack.jetbrains.com/issue/KT-55177
        runCli(tempRoot, "build", assertEmptyStdErr = false)
    }

    @Test
    @MacOnly
    fun `compose-multiplatform`() = runTestInfinitely {
        collectSpansFromCli {
            runCli(tempRoot, "build", assertEmptyStdErr = false)
        }.assertXcodeProjectIsValid()
    }

    @Test
    fun `compose-desktop`() = runTestInfinitely {
        runCli(tempRoot, "build")
    }

    @Test
    fun `compose-android`() = runTestInfinitely {
        runCli(tempRoot, "build")
    }

    @Test
    @MacOnly
    fun `compose-ios`() = runTestInfinitely {
        collectSpansFromCli {
            // Temporary disable stdErr assertions because linking and xcodebuild produce some warnings
            // that are treated like errors.
            runCli(tempRoot, "build", assertEmptyStdErr = false)
        }.assertXcodeProjectIsValid()
    }

    private fun SpansTestCollector.assertXcodeProjectIsValid() {
        // Xcode project should be generated correctly by `init` and thus not updated by the build.
        assertFalse { xcodeProjectManagementSpans.assertSingle().getAttribute(UpdatedAttribute) }
    }
}
