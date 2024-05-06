/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.util.OS
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains

class AmperProjectTemplatesTest: AmperCliTestBase() {
    // Please add as many checks as possible to template tests

    @Test
    fun `compose-desktop`() = runTestInfinitely {
        runCli(tempRoot, "build")
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
    fun `multiplatform-app`() = runTestInfinitely {
        Assumptions.assumeFalse(OS.isWindows, "Skip test on Windows, fix AMPER-527 and remove this line")
        runCli(tempRoot, "build")
    }

    @BeforeEach
    fun createFromTemplateAndBuild() {
        if (testInfo.tags.contains(SUPPLEMENTAL_TAG)) return

        runBlocking {
            runCli(tempRoot, "init", currentTestName)
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

    override val testDataRoot: Path
        get() = throw UnsupportedOperationException()

    companion object {
        private const val SUPPLEMENTAL_TAG = "supplemental"
    }
}
