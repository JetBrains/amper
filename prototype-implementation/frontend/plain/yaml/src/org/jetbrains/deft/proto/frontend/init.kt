package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.model.DumbGradleModule
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
        if (!root.exists()) {
            throw RuntimeException("Can't find ${root.absolutePathString()}")
        }

        val rootFile = root.resolve("drawer.yaml")
        val modelParts = if (rootFile.exists())
            parseModuleParts(rootFile.inputStream())
        else
            classBasedSet()

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
            .map { withBuildFile(it.toAbsolutePath()) { parseModule(it.readText()) } }
            .collect(Collectors.toList())

        val gradleModuleWrappers = Files.walk(root)
            .filter { setOf("build.gradle.kts", "build.gradle").contains(it.name) }
            .map { DumbGradleModule(it) }
            .collect(Collectors.toList())

        return object : Model {
            override val parts = modelParts
            override val modules: List<PotatoModule> = modules + gradleModuleWrappers
        }
    }
}