package org.jetbrains.deft.proto.frontend.model

import org.jetbrains.deft.proto.frontend.*


context (Stateful<FragmentBuilder, Fragment>)
internal open class TestPlainApplicationArtifact(
    mutableFragments: List<FragmentBuilder>,
    platform: Platform,
    cartesianElement: Set<String>,
    override val testFor: Artifact,
) : PlainApplicationArtifact(mutableFragments, platform, cartesianElement), TestArtifact

context (Stateful<FragmentBuilder, Fragment>)
internal open class PlainApplicationArtifact(
    private val fragmentBuilders: List<FragmentBuilder>,
    private val platform: Platform,
    private val cartesianElement: Set<String>,
) : Artifact {
    private val targetInternalFragment = fragmentBuilders.filter { it.platforms == setOf(platform) }
        .firstOrNull { it.variants == cartesianElement } ?: error("Something went wrong")

    override val name: String
        // TODO Handle the case, when there are several artifacts with same name. Can it be?
        // If it can't - so it should be expressed in API via sealed interface.
        // FIXME
        get() = targetInternalFragment.name
    override val fragments: List<Fragment>
        get() = listOf(targetInternalFragment.build())
    override val platforms: Set<Platform>
        get() = setOf(platform)
    override val parts: ClassBasedSet<ArtifactPart<*>>
        get() {
            return buildClassBasedSet {
                if (platform == Platform.ANDROID) {
                    val androidPart = targetInternalFragment.android

                    add(
                        AndroidArtifactPart(
                            androidPart?.compileSdkVersion,
                            androidPart?.androidMinSdkVersion,
                            androidPart?.sourceCompatibility,
                            androidPart?.targetCompatibility
                        )
                    )
                }

                if (platform == Platform.JVM) {
                    val javaPart = targetInternalFragment.java
                    add(JavaApplicationArtifactPart(javaPart?.mainClass, javaPart?.packagePrefix))
                }

                if (platform.native()) {
                    val nativePart = targetInternalFragment.native
                    add(NativeApplicationArtifactPart(nativePart?.entryPoint))
                }
            }
        }
}