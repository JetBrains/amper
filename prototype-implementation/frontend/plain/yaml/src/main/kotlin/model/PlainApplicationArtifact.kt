package org.jetbrains.deft.proto.frontend.model

import org.jetbrains.deft.proto.frontend.*


context (Stateful<MutableFragment, Fragment>)
internal open class TestPlainApplicationArtifact(
    mutableFragments: List<MutableFragment>,
    platform: Platform,
    cartesianElement: Set<String>,
    override val testFor: Artifact,
) : PlainApplicationArtifact(mutableFragments, platform, cartesianElement), TestArtifact

context (Stateful<MutableFragment, Fragment>)
internal open class PlainApplicationArtifact(
    private val mutableFragments: List<MutableFragment>,
    private val platform: Platform,
    private val cartesianElement: Set<String>,
) : Artifact {
    private val targetInternalFragment = mutableFragments.filter { it.platforms == setOf(platform) }
        .firstOrNull { it.variants == cartesianElement } ?: error("Something went wrong")

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
                if (this !is TestArtifact) {
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
}