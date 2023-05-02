package org.jetbrains.deft.proto.frontend.model

import org.jetbrains.deft.proto.frontend.*
import kotlin.io.path.name


context (BuildFileAware, Stateful<MutableFragment, Fragment>)
internal open class TestPlainLibraryArtifact(
    mutableFragments: List<MutableFragment>,
    platformList: List<Platform>,
    override val testFor: Artifact,
) : PlainLibraryArtifact(mutableFragments, platformList), TestArtifact

context (BuildFileAware, Stateful<MutableFragment, Fragment>)
internal open class PlainLibraryArtifact(
    private val mutableFragments: List<MutableFragment>,
    private val platformList: List<Platform>
) : Artifact {
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
}