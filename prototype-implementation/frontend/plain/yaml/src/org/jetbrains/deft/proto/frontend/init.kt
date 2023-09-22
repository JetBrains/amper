package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.core.*
import org.jetbrains.deft.proto.core.messages.ProblemReporterContext
import org.jetbrains.deft.proto.frontend.model.DumbGradleModule
import org.yaml.snakeyaml.Yaml
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.reader

internal fun withBuildFile(buildFile: Path, func: BuildFileAware.() -> Result<PotatoModule>): Result<PotatoModule> =
    with(object : BuildFileAware {
        override val buildFile: Path = buildFile
    }) {
        this.func()
    }

class YamlModelInit : ModelInit {

    override val name = "plain"

    context(ProblemReporterContext)
    override fun getModel(root: Path): Result<Model> {
        if (!root.exists()) {
            problemReporter.reportError(FrontendYamlBundle.message("no.root.found", root.name))
            return deftFailure()
        }

        val ignorePaths = root.deftIgnoreIfAny?.parseIgnorePaths().orEmpty()

        return buildModelTopDown(root, ignorePaths)
    }

    /**
     * Warning! Do not read content from [pot]
     *
     * This method is used from IDE, which uses VirtualFileSystem. VFS state is more up-to-date than on-disk state.
     * [contentReader] reads state directly from VFS, while attempting to invoke something like `pot.readLines()`
     * will return stale content of the Pot
     */
    context(ProblemReporterContext)
    @UsedInIdePlugin
    fun getPartialModel(pot: Path, contentReader: Reader): Result<Model> {
        if (!pot.isModuleYaml()) {
            return Result.failure(
                DeftException("Expected Pot-file for partial model building, got: ${pot.toAbsolutePath()}")
            )
        }

        val ignorePaths = pot.findAndParseIgnorePaths()
        if (ignorePaths.any { it.startsWith(pot) }) return Result.success(object : Model {
            override val modules: List<PotatoModule> = emptyList()
        })

        val yaml = Yaml()
        val partialModules = pot.parseModule(yaml, contentReader)

        return partialModules.map { object : Model {
            override val modules: List<PotatoModule> = listOf(it)
        } }
    }

    /**
     * Finds a .deftignore in the current folder or any parent, and if found, parses a list of ignore-paths from it.
     */
    private fun Path.findAndParseIgnorePaths(): List<Path> = sequence<Path> { parent }
        .firstNotNullOfOrNull { deftIgnoreIfAny }
        ?.parseIgnorePaths()
        .orEmpty()

    // NB: assumes that .deftignore is in the "root
    private fun Path.parseIgnorePaths(): List<Path> {
        val root = parent
        val ignoreLines = if (exists()) {
            readLines()
        } else {
            emptyList()
        }
        return ignoreLines.map { root.resolve(it) }
    }

    context(ProblemReporterContext)
    private fun buildModelTopDown(
        root: Path,
        ignorePaths: List<Path>
    ): Result<Model> {
        val yaml = Yaml()
        val modules: List<Result<PotatoModule>> = Files.walk(root)
            .filter {
                it.isModuleYaml() && ignorePaths.none { ignorePath -> it.startsWith(ignorePath) }
            }
            .map { it.parseModule(yaml, it.reader()) }
            .collect(Collectors.toList())

        if (modules.any { it is Result.Failure }) return deftFailure()

        val moduleNames = modules.unwrap().map { it.userReadableName }.toSet()

        val gradleModuleWrappers = Files.walk(root)
            .filter { setOf("build.gradle.kts", "build.gradle").contains(it.name) }
            .filter { it.parent.fileName.name !in moduleNames }
            .filter { ignorePaths.none { ignorePath -> it.startsWith(ignorePath) } }
            .map { DumbGradleModule(it) }
            .collect(Collectors.toList())

        return Result.success(
            object : Model {
                override val modules: List<PotatoModule> = modules.mapNotNull { it.getOrNull() } + gradleModuleWrappers
            }
        )
    }

    context(ProblemReporterContext)
    private fun Path.parseModule(
        yaml: Yaml,
        contentReader: Reader
    ) = withBuildFile(toAbsolutePath()) {
        val result = yaml.parseAndPreprocess(this@parseModule, contentReader) { includePath ->
            buildFile.parent.resolve(includePath)
        }
        result.flatMap { settings ->
            with(ParsingContext(settings)) {
                parseModule()
            }
        }
    }
}
