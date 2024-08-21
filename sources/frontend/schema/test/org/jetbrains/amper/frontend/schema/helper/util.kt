/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.CollectingProblemReporter
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.project.AmperProjectContext
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class TestProblemReporter : CollectingProblemReporter() {
    override fun doReportMessage(message: BuildProblem) {}

    fun clearAll() = problems.clear()

    fun getDiagnostics(vararg levels: Level = arrayOf(Level.Error, Level.Fatal)): List<BuildProblem> = problems.filter { levels.contains(it.level) }
}

class TestProblemReporterContext : ProblemReporterContext {
    override val problemReporter: TestProblemReporter = TestProblemReporter()
}

context(TestBase)
fun copyLocal(localName: String, dest: Path = buildFile, newPath: () -> Path = { dest / localName }) {
    val localFile = baseTestResourcesPath.resolve(localName).normalize().takeIf(Path::exists)
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
    if (!expectedPath.exists()) expectedPath.writeText("")
    val resourceFileText = expectedPath.readText()
    return resourceFileText
        .replace("{{ buildDir }}", buildDir)
        .replace("{{ potDir }}", buildFile.parent.relativize(Path(potDir)).toString())
        .replace("{{ testProcessDir }}", testProcessDir)
        .replace("{{ testResources }}", testResources)
        .replace("{{ fileSeparator }}", File.separator)
}

class TestSystemInfo(
    private val predefined: SystemInfo.Os
) : SystemInfo {
    override fun detect() = predefined
}

open class TestProjectContext(
    override val projectRootDir: VirtualFile,
    override val amperModuleFiles: List<VirtualFile>,
    override val frontendPathResolver: FrontendPathResolver,
) : AmperProjectContext {
    override val amperCustomTaskFiles: List<VirtualFile> = emptyList()
    override val gradleBuildFilesWithoutAmper: List<VirtualFile> = emptyList()
    val path2catalog: MutableMap<VirtualFile, VirtualFile> = mutableMapOf()
    override fun getCatalogPathFor(file: VirtualFile): VirtualFile? = path2catalog[file]
}

fun listOfTraceable(vararg items: String) = items.map { TraceableString(it) }