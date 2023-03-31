package org.jetbrains.deft.proto.frontend

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
        error("You need to add up at least one platform")
    }

    val dependencySubsets = config.keys
        .asSequence()
        .map { it.split(".") }
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
        .associate { with(mapOf<String, Set<Platform>>()) { setOf(it).toCamelCaseString() } to it.leafChildren.toSet() }

    val aliases: Settings = config.getValue<Settings>("aliases") ?: mapOf()
    val aliasMap: Map<String, Set<Platform>> = aliases.entries.associate {
        val name = it.key
        val platformSet = aliases.getValue<List<String>>(it.key)
            ?.mapNotNull { getPlatformFromFragmentName(it) }
            ?.toSet() ?: setOf()
        name to platformSet
    } + naturalHierarchy

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
    with(aliasMap) { fragments.handleAdditionalKeys(config) }
    val mutableState = object : Stateful<MutableFragment, Fragment> {}
    val immutableFragments = fragments.map { with(mutableState) { it.immutable() } }
    return object : PotatoModule {
        override val userReadableName: String
            get() = buildFile.parent.name
        override val type: PotatoModuleType
            get() = when (config.getByPath<String>("product", "type") ?: error("Product type is required")) {
                "app" -> PotatoModuleType.APPLICATION
                "lib" -> PotatoModuleType.LIBRARY
                else -> error("Unsupported product type")
            }
        override val source: PotatoModuleSource
            get() = PotatoModuleFileSource(buildFile)
        override val fragments: List<Fragment>
            get() = immutableFragments
        override val artifacts: List<Artifact>
            get() = when (config.getByPath<String>("product", "type") ?: error("Product type is required")) {
                "app" -> application()
                "lib" -> library()
                else -> error("Unsupported product type")
            }

        private fun library(): List<Artifact> {
            return listOf(object : Artifact {
                override val name: String
                    get() = buildFile.parent.name
                override val fragments: List<Fragment>
                    get() = with(mutableState) { fragments.map { it.immutable() } }
                override val platforms: Set<Platform>
                    get() = platforms.toSet()
                override val parts: ClassBasedSet<ArtifactPart<*>>
                    get() = setOf()
            })
        }

        private fun application(): List<Artifact> {
            val options = buildList {
                for (variant in config.variants) {
                    val options = variant.getValue<List<Settings>>("options") ?: listOf()
                    add(buildList {
                        for (option in options) {
                            add(
                                option.getValue<String>("name")
                                    ?: error("Name is required for variant option")
                            )
                        }
                    })
                }
            }

            val cartesian = cartesian(*options.toTypedArray())

            return buildList {
                for (platform in platforms) {
                    for (element in cartesian) {
                        add(object : Artifact {
                            private val targetInternalFragment = fragments.filter { it.platforms == setOf(platform) }
                                .firstOrNull { it.variants == element.toSet() } ?: error("Something went wrong")

                            override val name: String
                                // TODO Handle the case, when there are several artifacts with same name. Can it be?
                                // If it can't - so it should be expressed in API via sealed interface.
                                // FIXME
                                get() = targetInternalFragment.name
                            override val fragments: List<Fragment>
                                get() = with(mutableState) { listOf(targetInternalFragment.immutable()) }
                            override val platforms: Set<Platform>
                                get() = setOf(platform)
                            override val parts: ClassBasedSet<ArtifactPart<*>>
                                get() {
                                    return buildSet {
                                        if (!element.contains("test")) {
                                            val mainClass = targetInternalFragment.mainClass ?: "MainKt"
                                            val entryPoint = targetInternalFragment.entryPoint ?: "main"
                                            if (platform == Platform.JVM) {
                                                add(
                                                    ByClassWrapper(
                                                        JavaArtifactPart(mainClass)
                                                    )
                                                )
                                            }
                                            if (platform.native()) {
                                                add(
                                                    ByClassWrapper(
                                                        NativeArtifactPart(entryPoint)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                        })
                    }
                }
            }
        }
    }
}