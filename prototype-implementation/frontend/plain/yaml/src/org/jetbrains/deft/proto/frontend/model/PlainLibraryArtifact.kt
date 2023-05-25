package org.jetbrains.deft.proto.frontend.model

import org.jetbrains.deft.proto.frontend.*
import kotlin.io.path.name


context (BuildFileAware, Stateful<FragmentBuilder, Fragment>)
internal open class TestPlainLibraryArtifact(
    mutableFragments: List<FragmentBuilder>,
    platformList: List<Platform>,
    override val testFor: Artifact,
) : PlainLibraryArtifact(mutableFragments, platformList), TestArtifact

context (BuildFileAware, Stateful<FragmentBuilder, Fragment>)
internal open class PlainLibraryArtifact(
    private val fragmentBuilders: List<FragmentBuilder>,
    private val platformList: List<Platform>
) : Artifact {
    override val name: String
        get() = buildFile.parent.name
    override val fragments: List<Fragment>
        get() = fragmentBuilders.immutable
    override val platforms: Set<Platform>
        get() = platformList.toSet()
    override val parts: ClassBasedSet<ArtifactPart<*>>
        get() = buildClassBasedSet {
            val androidPart = fragmentBuilders
                .filter { it.platforms.contains(Platform.ANDROID) }
                .firstNotNullOfOrNull { it.android }

            add(
                AndroidArtifactPart(
                    androidPart?.compileSdkVersion,
                    androidPart?.androidMinSdkVersion,
                    androidPart?.sourceCompatibility,
                    androidPart?.targetCompatibility
                )
            )

            val javaPart = fragmentBuilders
                .filter { it.platforms.contains(Platform.JVM) }
                .firstNotNullOfOrNull { it.java }

            add(JavaApplicationArtifactPart(javaPart?.mainClass, javaPart?.packagePrefix))
        }
}