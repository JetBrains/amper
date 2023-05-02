package org.jetbrains.deft.proto.frontend.model

import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.frontend.MutableFragment
import org.jetbrains.deft.proto.frontend.Settings
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

    private fun library(): List<Artifact> = listOf(PlainLibraryArtifact(mutableFragments, platformList))

    private fun application(): List<Artifact> {
        val options = buildList {
            for (variant in config.variants) {
                val options = variant.getValue<List<Settings>>("options") ?: listOf()
                add(buildList {
                    for (option in options) {
                        add(
                            option.getValue<String>("name") ?: error("Name is required for variant option")
                        )
                    }
                })
            }
        }

        val cartesian = cartesianSets(options)

        return buildList {
            for (platform in platformList) {
                val elements2Artifacts = mutableMapOf<Set<String>, PlainApplicationArtifact>()

                // Divide by test property.
                val groupedByTestProperty = cartesian.groupBy { it.contains("test") }
                val nonTestCartesian = groupedByTestProperty[false] ?: emptyList()
                val testCartesian = groupedByTestProperty[true] ?: emptyList()

                // Init for non-test.
                for (nonTest in nonTestCartesian) {
                    val plain = PlainApplicationArtifact(mutableFragments, platform, nonTest)
                    elements2Artifacts[nonTest] = plain
                    add(plain)
                }

                // Bind test artifacts with non-test.
                for (test in testCartesian) {
                    val foundBound = elements2Artifacts[test - "test" + "main"] ?: error("No non test artifact found for $test")
                    val plain = TestPlainApplicationArtifact(mutableFragments, platform, test, foundBound)
                    elements2Artifacts[test] = plain
                    add(plain)
                }
            }
        }
    }
}