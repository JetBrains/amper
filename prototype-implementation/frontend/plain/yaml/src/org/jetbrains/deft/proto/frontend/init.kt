package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.core.*
import org.jetbrains.deft.proto.core.messages.ProblemReporterContext
import org.jetbrains.deft.proto.frontend.model.DumbGradleModule
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readLines

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
        val yaml = Yaml()

        if (!root.exists()) {
            problemReporter.reportError(FrontendYamlBundle.message("no.root.found", root.name))
            return Result.failure(DeftException())
        }

        val ignore = root.resolve(".deftignore")
        val ignoreLines = if (ignore.exists()) {
            ignore.readLines()
        } else {
            emptyList()
        }
        val ignorePaths = ignoreLines.map { root.resolve(it) }

        val modules: List<Result<PotatoModule>> = Files.walk(root)
            .filter {
                it.name == "Pot.yaml" && ignorePaths.none { ignorePath -> it.startsWith(ignorePath) }
            }
            .map {
                withBuildFile(it.toAbsolutePath()) {
                    val result = yaml.parseAndPreprocess(it) { includePath ->
                        buildFile.parent.resolve(includePath)
                    }
                    result.flatMap { settings -> parseModule(settings) }
                }
            }
            .collect(Collectors.toList())

        if (modules.any { it is Result.Failure }) return Result.failure(DeftException())

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
}
