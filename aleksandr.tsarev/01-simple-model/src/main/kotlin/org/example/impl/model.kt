package org.example.impl

import cc.ekblad.toml.decode
import cc.ekblad.toml.tomlMapper
import org.example.api.Model
import org.example.api.ModelInit
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

data class SimpleModule(
    val id: String,
    val path: Path,
    val targets: List<String>,
    val sources: Map<String, List<String>>,
    val dependencies: Map<String, List<String>>,
)

class SimpleModelInit : ModelInit {

    private val mapper = tomlMapper { }

    private fun Path.getModule(): SimpleModule? {
        val buildToml = resolve("build.toml").takeIf { it.exists() } ?: return null
        val moduleId = this.fileName.name

        val decoded = mapper.decode<Map<String, Any>>(buildToml)

        // Sources.
        @Suppress("UNCHECKED_CAST")
        val sourcesRaw = decoded["sources"] as? Map<String, *>

        @Suppress("UNCHECKED_CAST")
        val defaultSources = sourcesRaw
            ?.entries
            ?.filter { it.value is Map<*, *> }
            ?.map { it.key to (it.value as Map<String, *>) }
            ?.map { it.first }
            ?: emptyList()

        val sources = mapOf("default-target" to (defaultSources + "src" + "test"))

        // Dependencies.
        @Suppress("UNCHECKED_CAST")
        val dependenciesRaw = decoded["dependencies"] as? Map<String, *>

        fun Map<String, *>.getPlaintDependencies() = entries
            .filter { it.value is String }
            .map { "${it.key}:${it.value}" }

        val defaultDependencies = dependenciesRaw
            ?.getPlaintDependencies()
            ?: emptyList()

        // Targets.
        @Suppress("UNCHECKED_CAST")
        val targetsRaw = decoded["target"] as? Map<String, *>

        @Suppress("UNCHECKED_CAST")
        val targetDependencies = targetsRaw
            ?.entries
            ?.filter { it.value is Map<*, *> }
            ?.map { it.key to it.value as Map<String, *> }
            ?.filter { it.second["dependencies"] is Map<*, *> }
            ?.associate { it.first to (it.second["dependencies"] as Map<String, *>).getPlaintDependencies() }
            ?: emptyMap()

        val dependencies = mutableMapOf<String, List<String>>().apply {
            put("default-target", defaultDependencies)
            putAll(targetDependencies)
        }

        val targets = buildList {
            add("default-target")
            addAll(targetDependencies.keys)
        }

        return SimpleModule(moduleId, this, targets, sources, dependencies)
    }

    override fun getModel(root: Path): Model {
        val module = root.getModule()
        val foundModules = if (module != null) mapOf(module.id to module) else emptyMap()
        return SimpleModel(foundModules)
    }

}

class SimpleModel(
    private val simpleModules: Map<String, SimpleModule>
) : Model {

    override val modules: List<Pair<String, Path>> =
        simpleModules.values.map { it.id to it.path }

    override fun getTargets(moduleId: String) =
        simpleModules[moduleId]?.targets ?: error("No module $moduleId")

    override fun getSources(moduleId: String, targetId: String) =
        simpleModules[moduleId]?.sources?.get(targetId)
            ?: error("No module $moduleId or target $targetId")

    override fun getDeclaredDependencies(moduleId: String, targetId: String) =
        simpleModules[moduleId]?.dependencies?.get(targetId)
            ?: error("No module $moduleId or target $targetId")

}