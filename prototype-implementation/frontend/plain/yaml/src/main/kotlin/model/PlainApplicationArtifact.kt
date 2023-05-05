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
                                // TODO Replace by meaningful defaults and move them somewhere else.
                                targetInternalFragment.androidCompileSdkVersion ?: "android-31",
                                targetInternalFragment.androidMinSdkVersion ?: 24
                            )
                        )
                    )
                }
                if (!isTest) {
                    if (platform == Platform.JVM) {
                        val mainClass = targetInternalFragment.mainClass ?: "MainKt"
                        add(
                            ByClassWrapper(
                                JavaApplicationArtifactPart(mainClass)
                            )
                        )
                    }
                    if (platform.native()) {
                        val entryPoint = targetInternalFragment.entryPoint ?: "main"
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