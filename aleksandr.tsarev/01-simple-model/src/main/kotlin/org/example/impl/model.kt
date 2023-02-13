package org.example.impl

import cc.ekblad.toml.decode
import cc.ekblad.toml.tomlMapper
import org.example.api.CollapsedMap
import org.example.api.Model
import org.example.api.ModelInit
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.streams.toList

data class SimpleModule(
    val id: String,
    val path: Path,
    val targets: List<String>,
    val dependencies: Map<String, List<String>>,
    val moduleInfo: CollapsedMap,
    val kotlinInfo: CollapsedMap,
    val allCollapsed: CollapsedMap,
)

class SimpleModelInit : ModelInit {

    private val mapper = tomlMapper { }

    private fun Path.getModule(): SimpleModule? {
        val buildToml = resolve("build.toml").takeIf { it.exists() } ?: return null
        val moduleId = this.fileName.name
        val decoded = mapper.decode<Map<String, Any>>(buildToml)

        // Parsing.
        val dependencies = parseDependencies(decoded)
        val targets = parseTargets(decoded)
        val module = parseCollapsed(decoded, "module")
        val kotlin = parseCollapsed(decoded, "kotlin")
        val allCollapsed = decoded.collapse()

        return SimpleModule(moduleId, this, targets, dependencies, module, kotlin, allCollapsed)
    }

    override fun getModel(root: Path): Model {
        val allModuleRoots = Files.walk(root)
            .filter { it.name == "build.toml" }
            .toList()
            .mapNotNull { it.parent }
        val allModules = allModuleRoots.mapNotNull { it.getModule() }
        return SimpleModel(allModules)
    }
}

class SimpleModel(
    private val simpleModules: List<SimpleModule>
) : Model {

    private val modulesMap by lazy { simpleModules.associateBy { it.id } }

    override val modules: List<Pair<String, Path>> =
        simpleModules.map { it.id to it.path }

    override fun getTargets(moduleId: String) =
        modulesMap[moduleId]?.targets ?: error("No module $moduleId")

    override fun getDeclaredDependencies(moduleId: String, targetId: String) =
        modulesMap[moduleId]?.dependencies?.get(targetId)
            ?: error("No module $moduleId or target $targetId")

    override fun getModuleInfo(moduleId: String) =
        modulesMap[moduleId]?.moduleInfo
            ?: error("No module $moduleId")

    override fun getKotlinInfo(moduleId: String) =
        modulesMap[moduleId]?.kotlinInfo
            ?: error("No module $moduleId")

    override fun getAllCollapsed(moduleId: String) =
        modulesMap[moduleId]?.allCollapsed
            ?: error("No module $moduleId")
}