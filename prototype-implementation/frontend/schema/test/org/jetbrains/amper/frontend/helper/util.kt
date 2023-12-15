/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helper

import com.intellij.rt.execution.junit5.FileComparisonFailedError
import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.CollectingProblemReporter
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.RepositoriesModulePart
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.test.asserter


class TestProblemReporter : CollectingProblemReporter() {
    override fun doReportMessage(message: BuildProblem) {}

    fun getErrors(): List<BuildProblem> = problems.filter { it.level == Level.Error }

}

class TestProblemReporterContext : ProblemReporterContext {
    override val problemReporter: TestProblemReporter = TestProblemReporter()
}

fun assertEqualsIgnoreLineSeparator(expectedContent: String, actualContent: String, originalFile: Path) {
    // assertEqualsIgnoreLineSeparator(expectedContent,actualContent) - why not assert with precise diff reported
    if (expectedContent.replaceLineSeparators() != actualContent.replaceLineSeparators()) {
        throw FileComparisonFailedError(
            "Comparison failed",
            expectedContent,
            actualContent,
            originalFile.absolutePathString()
        )
    }
}

fun assertEqualsIgnoreLineSeparator(expected: String, checkText: String, message: String? = null) {
    if (expected.replaceLineSeparators() != checkText.replaceLineSeparators()) {
        asserter.assertEquals(message, expected, checkText)
    }
}

fun String.replaceLineSeparators() = replace("\n", "").replace("\r", "")

internal fun PotatoModule.prettyPrint(): String {
    return buildString {
        appendLine("Fragments:")
        for (fragment in fragments.sortedBy { it.name }) {
            appendLine("  ${fragment.name}")
            appendLine("    External dependencies:")
            for (dependency in fragment.externalDependencies.sortedBy { it.toString() }) {
                appendLine("      $dependency")
            }
            appendLine("    Src folder: ${fragment.src.fileName}")
            appendLine("    Fragment dependencies:")
            for (dependency in fragment.fragmentDependencies) {
                appendLine("      ${dependency.target.name} (${dependency.type})")
            }
            appendLine("    Parts:")
            for (part in fragment.parts) {
                appendLine("      $part")
            }
        }
        appendLine("Artifacts:")
        for (artifact in artifacts.sortedBy { it.name }) {
            appendLine("  isTest: ${artifact.isTest}")
            appendLine("  ${artifact.platforms}")
            appendLine("    Fragments:")
            for (fragment in artifact.fragments) {
                appendLine("      ${fragment.name}")
            }
        }

        val repositories = parts[RepositoriesModulePart::class.java]?.mavenRepositories
        if (!repositories.isNullOrEmpty()) {
            appendLine("Repositories:")
            repositories.forEach {
                appendLine("  - id: ${it.id}")
                appendLine("    url: ${it.url}")
                appendLine("    publish: ${it.publish}")
                appendLine("    username: ${it.userName}")
                appendLine("    password: ${it.password}")
            }
        }
    }
}

internal class TestDirectory(val dir: File) {

    init {
        try {
            Files.createDirectories(dir.toPath())
        } catch (e: FileAlreadyExistsException) {
            // do nothing
        }
    }

    inline fun directory(name: String, block: TestDirectory.() -> Unit = {}) {
        val newDir = File(dir, name)
        TestDirectory(newDir).apply(block)
    }

    inline fun file(name: String, block: File.() -> Unit = {}) {
        File(dir, name).apply { createNewFile() }.block()
    }

    fun copyLocal(localName: String, newName: String = localName) {
        val localFile = File(".").resolve("testResources/$localName").normalize().takeIf(File::exists)
        localFile?.copyTo(File(dir, newName), overwrite = true)
    }
}

internal inline fun project(projectDir: File, block: TestDirectory.() -> Unit): TestDirectory {
    return TestDirectory(projectDir).apply(block)
}