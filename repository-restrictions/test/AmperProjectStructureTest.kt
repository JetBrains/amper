/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.jetbrains.amper.core.get
import org.jetbrains.amper.core.messages.NoOpCollectingProblemReporter
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.aomBuilder.SchemaBasedModelImport
import org.jetbrains.amper.frontend.project.StandaloneAmperProjectContext
import org.jetbrains.amper.test.Dirs
import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.visitFileTree
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.fail
import kotlin.use

class AmperProjectStructureTest {

    @Serializable
    data class ProjectFile(val modules: List<String>)

    @Test
    fun `list of modules is alphabetically sorted`() {
        val projectYaml = Dirs.amperCheckoutRoot.resolve("project.yaml")
        val project = projectYaml.inputStream().use { Yaml.default.decodeFromStream<ProjectFile>(it) }
        val modules = project.modules
        assertAlphabeticalOrder(modules, "Modules in project.yaml")
    }

    @Test
    fun `UsedVersions is alphabetically sorted`() {
        val usedVersionsKt = Dirs.amperSourcesRoot.resolve("core/src/org/jetbrains/amper/core/UsedVersions.kt")
        val versionLines = usedVersionsKt.readLines()
            .map { it.trim() }
            .filter { it.startsWith("/*magic_replacement*/ val ") }
        assertAlphabeticalOrder(versionLines, "Used versions")
    }

    @Test
    fun `Amper-agnostic library modules don't use the word Amper`() = runTest {
        val invalidLines = readAmperProjectModel()
            .modules
            .filter { it.isAmperAgnosticLibrary() }
            .map { it to it.linesWithTheWordAmper() }
            .filter { it.second.isNotEmpty() }
        if (invalidLines.isNotEmpty()) {
            fail("Some Amper-agnostic library modules contain the word 'Amper'.\n\n" +
                    invalidLines.joinToString("\n\n") { (module, linesWithAmper) ->
                        "Module '${module.userReadableName}' uses the word 'Amper':\n" +
                                linesWithAmper.joinToString("\n") { "  - $it" }
                    } + "\n\nMake sure these modules are Amper-agnostic.")
        }
    }

    private fun AmperModule.linesWithTheWordAmper(): List<String> = fragments.flatMap { it.linesWithTheWordAmper() }

    private fun Fragment.linesWithTheWordAmper(): Sequence<String> =
        (src.walk() + resourcesPath.walk()).flatMap { it.linesWithTheWordAmper() }

    /**
     * We are still in the Amper repo and the libraries are not yet (or maybe ever) separated from Amper, so they still
     * legitimately use the org.jetbrains.amper package root. Apart from that, there should be no Amper reference.
     */
    private val amperExceptInPackageRegex = Regex("""(?<!org\.jetbrains\.)[aA]mper""")
    private fun Path.linesWithTheWordAmper(): List<String> = readLines()
        .withIndex()
        .filter { (_, line) -> line.contains(amperExceptInPackageRegex) }
        .map { (i, line) -> "${absolutePathString()}:$i: $line" }

    @Test
    fun `Amper-agnostic library modules don't depend on Amper-aware modules`() = runTest {
        val invalidDeps = readAmperProjectModel()
            .modules
            .filter { it.isAmperAgnosticLibrary() }
            .map { it to it.nonLibraryDependencies() }
            .filter { it.second.isNotEmpty() }
        if (invalidDeps.isNotEmpty()) {
            fail(
                "Some Amper-agnostic library modules depend on Amper-aware modules.\n\n" +
                    invalidDeps.joinToString("\n\n") { (module, dependencies) ->
                        "Module '${module.userReadableName}' depends on Amper-aware module(s):\n" +
                                dependencies.joinToString("\n") { "  - $it" }
                    } + "\n\nRemove these dependencies or move the modules out of the 'libraries' directory.")
        }
    }

    @Test
    fun `DR module doesn't depend on Amper-aware modules in its production dependencies`() = runTest {
        val drModuleName = "dependency-resolution"
        val drModule = readAmperProjectModel().modules.find { it.userReadableName == drModuleName }
            ?: error("Module '$drModuleName' not found, please update this test if it was renamed")
        val invalidDeps = drModule.nonLibraryDependencies(includeTestDeps = false)
        if (invalidDeps.isNotEmpty()) {
            fail(
                "The '$drModuleName' module depends on the following Amper-aware modules:\n\n" +
                    invalidDeps.joinToString("\n") { "  - $it" } +
                        "\n\nPlease remove these dependencies, as we want DR to be independent of Amper.")
        }
    }

    private fun readAmperProjectModel(): Model = with(SimpleProblemReporterContext(NoOpCollectingProblemReporter())) {
        val projectContext = StandaloneAmperProjectContext.create(Dirs.amperCheckoutRoot, project = null)
            ?: error("Invalid project root: ${Dirs.amperCheckoutRoot}")
        SchemaBasedModelImport.getModel(projectContext).get()
    }

    private fun AmperModule.nonLibraryDependencies(includeTestDeps: Boolean = true): List<String> = fragments
        .let { if (includeTestDeps) it else it.filterNot(Fragment::isTest) }
        .flatMap { it.externalDependencies }
        .filterIsInstance<LocalModuleDependency>()
        .filterNot { it.module.isAmperAgnosticLibrary() }
        .map { it.module.source.moduleDir?.relativeTo(Dirs.amperCheckoutRoot).toString() }

    private fun AmperModule.isAmperAgnosticLibrary(): Boolean =
        source.moduleDir?.absolute()?.startsWith(Dirs.amperCheckoutRoot.resolve("sources/libraries")) == true

    @Test
    fun sameVersionInEveryWrapper() {
        val versionToFiles = Dirs.amperCheckoutRoot.findWrapperFiles()
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

    private val excludedDirs = setOf("build", "build-from-sources", ".gradle", ".kotlin", ".git", "shared test caches")

    private fun Path.findWrapperFiles(): List<Path> {
        val filesWithWrappers = mutableListOf<Path>()
        val gradleTestProjects = Dirs.amperSourcesRoot.resolve("gradle-e2e-test/testData/projects")
        visitFileTree {
            onPreVisitDirectory { dir, _ ->
                when {
                    dir.name in excludedDirs -> FileVisitResult.SKIP_SUBTREE
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
}

private class SimpleProblemReporterContext(override val problemReporter: ProblemReporter) : ProblemReporterContext
