package org.jetbrains.deft.proto.frontend

import kotlin.io.path.name

context (BuildFileAware, Stateful<MutableFragment, Fragment>)
internal class PlainPotatoModule(
    private val config: Settings,
    private val mutableFragments: List<MutableFragment>,
    private val platformList: List<Platform>,
) : PotatoModule {
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
        get() = mutableFragments.immutable

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
                get() = mutableFragments.immutable
            override val platforms: Set<Platform>
                get() = platformList.toSet()
            override val parts: ClassBasedSet<ArtifactPart<*>>
                get() = buildSet {
                    val compileSdkVersion = mutableFragments
                        .filter { it.platforms.contains(Platform.ANDROID) }
                        .map { it.androidCompileSdkVersion }
                        .firstOrNull() ?: "android-31"
                    add(ByClassWrapper(AndroidArtifactPart(compileSdkVersion)))
                }
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
            for (platform in platformList) {
                for (element in cartesian) {
                    add(object : Artifact {
                        private val targetInternalFragment = mutableFragments
                            .filter { it.platforms == setOf(platform) }
                            .firstOrNull { it.variants == element.toSet() }
                            ?: error("Something went wrong")

                        override val name: String
                            // TODO Handle the case, when there are several artifacts with same name. Can it be?
                            // If it can't - so it should be expressed in API via sealed interface.
                            // FIXME
                            get() = targetInternalFragment.name
                        override val fragments: List<Fragment>
                            get() = listOf(targetInternalFragment.immutable())
                        override val platforms: Set<Platform>
                            get() = setOf(platform)
                        override val parts: ClassBasedSet<ArtifactPart<*>>
                            get() {
                                return buildSet {
                                    if (platform == Platform.ANDROID) {
                                        add(
                                            ByClassWrapper(
                                                AndroidArtifactPart(
                                                    targetInternalFragment.androidCompileSdkVersion ?: "android-31"
                                                )
                                            )
                                        )
                                    }
                                    if (!element.contains("test")) {
                                        val mainClass = targetInternalFragment.mainClass ?: "MainKt"
                                        val entryPoint = targetInternalFragment.entryPoint ?: "main"
                                        if (platform == Platform.JVM) {
                                            add(
                                                ByClassWrapper(
                                                    JavaApplicationArtifactPart(mainClass)
                                                )
                                            )
                                        }
                                        if (platform.native()) {
                                            add(
                                                ByClassWrapper(
                                                    NativeApplicationArtifactPart(entryPoint)
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