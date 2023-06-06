package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.model.DumbGradleModule
import org.jetbrains.deft.proto.frontend.util.inputStreamOrNull
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.*


internal fun withBuildFile(buildFile: Path, func: BuildFileAware.() -> PotatoModule): PotatoModule =
    with(object : BuildFileAware {
        override val buildFile: Path = buildFile
    }) {
        this.func()
    }

class YamlModelInit : ModelInit {

    override val name = "plain"

    override fun getModel(root: Path): Model {
        val yaml = Yaml()

        if (!root.exists()) {
            throw RuntimeException("Can't find ${root.absolutePathString()}")
        }

        val localPropertiesFile = root.resolve("root.local.properties")
        val interpolateCtx = InterpolateCtx().apply {
            localPropertiesFile.inputStreamOrNull()?.let { load(it) }
        }

        return with(interpolateCtx) {
            val ignore = root.resolve(".deftignore")
            val ignoreLines = if (ignore.exists()) {
                ignore.readLines()
            } else {
                emptyList()
            }
            val ignorePaths = ignoreLines.map { root.resolve(it) }

            val modules = Files.walk(root)
                .filter {
                    it.name == "Pot.yaml" && ignorePaths.none { ignorePath -> it.startsWith(ignorePath) }
                }
                .map {
                    withBuildFile(it.toAbsolutePath()) {
                        val parsed = yaml.parseAndPreprocess(it.inputStream()) { includePath ->
                            buildFile.parent.resolve(includePath).inputStream()
                        }
                        parseModule(parsed)
                    }
                }
                .collect(Collectors.toList())

            val gradleModuleWrappers = Files.walk(root)
                .filter { setOf("build.gradle.kts", "build.gradle").contains(it.name) }
                .map { DumbGradleModule(it) }
                .collect(Collectors.toList())

            object : Model {
                override val modules: List<PotatoModule> = modules + gradleModuleWrappers
            }
        }
    }
}