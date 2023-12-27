/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.CollectingProblemReporter
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.frontend.aomBuilder.DefaultFioContext
import org.jetbrains.amper.frontend.aomBuilder.DumbGradleModule
import org.jetbrains.amper.frontend.aomBuilder.FioContext
import org.jetbrains.amper.frontend.old.helper.TestBase
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText


class TestProblemReporter : CollectingProblemReporter() {
    override fun doReportMessage(message: BuildProblem) {}

    fun getErrors(): List<BuildProblem> = problems.filter { it.level == Level.Error || it.level == Level.Fatal }
}

class TestProblemReporterContext : ProblemReporterContext {
    override val problemReporter: TestProblemReporter = TestProblemReporter()
}

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

context(TestBase)
fun copyLocal(localName: String, dest: Path = buildFile, newPath: () -> Path = { dest / localName }) {
    val localFile = base.resolve(localName).normalize().takeIf(Path::exists)
    val newPathWithDirs = newPath().apply { createDirectories() }
    localFile?.copyTo(newPathWithDirs, overwrite = true)
}

context(TestBase)
fun readContentsAndReplace(
    expectedPath: Path,
    base: Path,
): String {
    val buildDir = buildFile.normalize().toString()
    val potDir = expectedPath.toAbsolutePath().normalize().parent.toString()
    val testProcessDir = Path(".").normalize().absolutePathString()
    val testResources = Path(".").resolve(base).normalize().absolutePathString()

    // This is actual check.
    val resourceFileText = expectedPath.readText()
    return resourceFileText
        .replace("{{ buildDir }}", buildDir)
        .replace("{{ potDir }}", buildFile.parent.relativize(Path.of(potDir)).toString())
        .replace("{{ testProcessDir }}", testProcessDir)
        .replace("{{ testResources }}", testResources)
        .replace("{{ fileSeparator }}", File.separator)
}

class TestSystemInfo(
    private val predefined: SystemInfo.Os
) : SystemInfo {
    override fun detect() = predefined
}

open class TestFioContext(
    override val root: Path,
    override val amperModuleFiles: List<Path>,
) : FioContext by DefaultFioContext(root) {
    override val ignorePaths: MutableList<Path> = mutableListOf()
    override val gradleModules: MutableMap<Path, DumbGradleModule> = mutableMapOf()
    override val amperFiles2gradleCatalogs: MutableMap<Path, List<Path>> = mutableMapOf()
}