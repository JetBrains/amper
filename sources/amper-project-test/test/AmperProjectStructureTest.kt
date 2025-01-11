/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.frontend.AmperModuleFileSource
import org.jetbrains.amper.test.TempDirExtension
import org.jetbrains.amper.test.TestCollector.Companion.runTestWithCollector
import org.jetbrains.amper.test.TestUtil
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.visitFileTree
import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class AmperProjectStructureTest {
    @Test
    fun sameVersionInEveryWrapper() {
        val versionToFiles = TestUtil.amperCheckoutRoot.findWrapperFiles()
            .map { it to extractAmperVersion(it) }
            .groupBy({ it.second }, { it.first })
            .toList()
            .map { it.first to it.second.sorted() }
            .sortedByDescending { it.second.size }
        check(versionToFiles.size == 1) {
            "Wrapper files reference different versions:\n\n" +
                    versionToFiles.joinToString("\n\n") { "${it.first}:\n${it.second.joinToString("\n")}" }
        }
    }

    private fun Path.findWrapperFiles(): List<Path> {
        val filesWithWrappers = mutableListOf<Path>()
        val gradleTestProjects = TestUtil.amperSourcesRoot.resolve("gradle-e2e-test/testData/projects")
        visitFileTree {
            onPreVisitDirectory { dir, _ ->
                when {
                    dir.name == "build" -> FileVisitResult.SKIP_SUBTREE
                    // do not reference wrappers
                    dir == gradleTestProjects -> FileVisitResult.SKIP_SUBTREE
                    dir.name == "amper-dr-test-bom-usages" -> FileVisitResult.SKIP_SUBTREE
                    else -> FileVisitResult.CONTINUE
                }
            }
            onVisitFile { file, _ ->
                if (file.name in setOf("settings.gradle.kts", "amper", "amper.bat")) {
                    filesWithWrappers.add(file)
                }
                FileVisitResult.CONTINUE
            }
        }
        return filesWithWrappers
    }

    @Test
    fun `list of modules is the same for gradle and standalone amper`() = runTestWithCollector {
        val backend = createAmperProjectBackend(backgroundScope)
        val standaloneModulesList = backend.modules()
            .map { (it.source as AmperModuleFileSource).moduleDir.relativeTo(TestUtil.amperCheckoutRoot) }
            .map { it.toString().replace('\\', '/') }
            .sorted()
            .joinToString("\n")

        val settingsGradleFile = TestUtil.amperCheckoutRoot.resolve("settings.gradle.kts")
        val gradleModulesList = settingsGradleFile
            .readLines()
            .mapNotNull { Regex("include\\(\"(.*)\"\\)").find(it)?.groupValues?.get(1)?.removePrefix(":")?.replace(':', '/') }
            .sorted()
            .joinToString("\n")

        assertEquals(standaloneModulesList, gradleModulesList,
            "Modules list in project.yaml (expected) and settings.gradle.kts (actual) differ")
    }

    private suspend fun createAmperProjectBackend(backgroundScope: CoroutineScope): AmperBackend {
        val context = CliContext.create(
            explicitProjectRoot = TestUtil.amperCheckoutRoot,
            userCacheRoot = AmperUserCacheRoot(TestUtil.userCacheRoot),
            buildOutputRoot = AmperBuildOutputRoot(tempRoot),
            currentTopLevelCommand = "n/a",
            backgroundScope = backgroundScope,
            terminal = Terminal(),
        )

        return AmperBackend(context = context)
    }

    private fun extractAmperVersion(file: Path): String {
        val text = file.readText()
        val lines = text.lines()

        val amperVersion = run {
            val versionPattern = when (file.name) {
                "settings.gradle.kts" -> Regex("\\s*id\\(\"org\\.jetbrains\\.amper\\.settings\\.plugin\"\\)\\.version\\(\"([^\"]+)\"\\)\\s*")
                "amper" -> Regex("amper_version=(.+)")
                "amper.bat" -> Regex("set amper_version=(.+)")
                else -> error("Unsupported file: $file")
            }

            val matches = lines.mapNotNull { line -> versionPattern.matchEntire(line) }
            check(matches.size == 1) {
                "Expect one and only one match of '${versionPattern.pattern}' in $file, but got ${matches.size} matches"
            }
            val match = matches.single()

            match.groupValues[1]
        }

        // check for some unmatched dev versions
        val split = text.split(Regex("[\\s:+,*#\"!?`'()=%~<>_]"))
            .filter { Regex("[0-9.]+-dev-[0-9.]+").matches(it) }
            .filter { it != amperVersion }
        if (split.isNotEmpty()) {
            error("Some strings look like an Amper version in $file:\n${split.joinToString("\n")}")
        }

        return amperVersion
    }

    @RegisterExtension
    private val tempDirExtension = TempDirExtension()

    private val tempRoot: Path
        get() = tempDirExtension.path
}
