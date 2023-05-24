package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.model.PlainPotatoModule
import org.jetbrains.deft.proto.frontend.util.getPlatformFromFragmentName
import org.yaml.snakeyaml.Yaml
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

context(BuildFileAware)
fun parseModule(value: String): PotatoModule {
    val yaml = Yaml()
    val config = yaml.load<Settings>(value)
    val rawPlatforms = config.getByPath<List<String>>("product", "platforms") ?: listOf()
    val platforms = rawPlatforms.mapNotNull { getPlatformFromFragmentName(it) }
    if (platforms.isEmpty()) {
        error("Error during parsing ${buildFile}: You need to add up at least one platform")
    }

    val dependencySubsets = config.keys
        .asSequence()
        .map { it.split("@") }
        .filter { it.size > 1 }
        .map { it[1] }
        .map { it.split("+").toSet() }
        .toSet()

    val folderSubsets = buildFile.parent
        .listDirectoryEntries()
        .map { it.name }
        .map { it.split("+").toSet() }
        .toSet()

    val naturalHierarchy = Platform.values()
        .filter { !it.isLeaf }
        .filter { it != Platform.COMMON }
        .associate { with(mapOf<String, Set<Platform>>()) { setOf(it).toCamelCaseString().first } to it.leafChildren.toSet() }

    val aliases: Settings = config.getValue<Settings>("aliases") ?: mapOf()
    val aliasMap: Map<String, Set<Platform>> = aliases.entries.associate {
        val name = it.key
        val platformSet = aliases.getValue<List<String>>(it.key)
            ?.mapNotNull { getPlatformFromFragmentName(it) }
            ?.toSet() ?: setOf()
        name to platformSet
    } + naturalHierarchy
        .entries
        .sortedBy { it.value.size }
        .associate { (key, value) -> key to (platforms intersect value) }

    val subsets = (dependencySubsets + folderSubsets)
        .map {
            it.flatMap {
                aliasMap[it] ?: listOfNotNull(getPlatformFromFragmentName(it))
            }.filter { it != Platform.COMMON }.toSet()
        }
        .filter { it.isNotEmpty() }
        .toSet() + platforms.map { setOf(it) }

    var fragments = with(aliasMap) { subsets.basicFragments }

    fragments = fragments.multiplyFragments(config.variants)
    with(aliasMap) { fragments.handleAdditionalKeys(config.transformed) }
    with(config) {
        fragments.calculateSrcDir(platforms.toSet())
    }

    val mutableState = object : Stateful<FragmentBuilder, Fragment> {
        private val mutableState = mutableMapOf<FragmentBuilder, Fragment>()
        override val state: MutableMap<FragmentBuilder, Fragment>
            get() = mutableState
    }
    return with(mutableState) { PlainPotatoModule(config, fragments, platforms) }
}