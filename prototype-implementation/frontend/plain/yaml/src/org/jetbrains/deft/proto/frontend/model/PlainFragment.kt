package org.jetbrains.deft.proto.frontend.model

import org.jetbrains.deft.proto.frontend.*
import java.nio.file.Path

context (Stateful<FragmentBuilder, Fragment>)
internal open class PlainFragment(
    val fragmentBuilder: FragmentBuilder
) : Fragment {
    override val isTest: Boolean
        get() = fragmentBuilder.isTest
    override val isDefault: Boolean
        get() = fragmentBuilder.isDefault
    override val name: String
        get() = fragmentBuilder.name
    override val fragmentDependencies: List<FragmentLink>
        get() = fragmentBuilder.dependencies.map { PlainFragmentLink(it) }
    override val fragmentDependants: List<FragmentLink>
        get() = fragmentBuilder.dependants.map { PlainFragmentLink(it) }
    override val externalDependencies: List<Notation>
        get() = fragmentBuilder.externalDependencies.toList()

    override val parts: ClassBasedSet<FragmentPart<*>> = buildClassBasedSet {
        addPartFrom(fragmentBuilder.kotlin) {
            KotlinPart(
                languageVersion = languageVersion?.version,
                apiVersion = apiVersion?.version,
                allWarningsAsErrors = allWarningsAsErrors,
                freeCompilerArgs = freeCompilerArgs,
                suppressWarnings = suppressWarnings,
                verbose = verbose,
                linkerOpts = likerOpts,
                debug = debug,
                progressiveMode = progressiveMode,
                languageFeatures = languageFeatures,
                optIns = optIns,
            )
        }

        addPartFrom(fragmentBuilder.junit) {
            TestPart(platformEnabled)
        }

        addPartFrom(fragmentBuilder.android) {
            AndroidPart(
                compileSdkVersion,
                minSdk,
                minSdkPreview,
                maxSdk,
                targetSdk,
                applicationId,
                namespace,
            )
        }

        addPartFrom(fragmentBuilder.java) {
            JavaPart(
                mainClass,
                packagePrefix,
                target,
                source,
            )
        }

        addPartFrom(fragmentBuilder.publishing) {
            PublicationPart(
                group,
                version,
            )
        }

        addPartFrom(fragmentBuilder.native) {
            NativeApplicationPart(
                entryPoint
            )
        }
    }

    override val platforms: Set<Platform>
        get() = fragmentBuilder.platforms

    override val src: Path?
        get() = fragmentBuilder.src
}

context (Stateful<FragmentBuilder, Fragment>)
internal class PlainLeafFragment(
    fragmentBuilder: FragmentBuilder
) : PlainFragment(fragmentBuilder), LeafFragment {

    init {
        assert(platforms.size == 1) { "Leaf fragment must have single platform" }
    }

    override val platform: Platform
        get() = platforms.single()
}

context(MutableSet<FragmentPart<*>>)
private fun <T : Any> addPartFrom(value: T?, block: T.() -> FragmentPart<*>) {
    value?.let {
        add(it.block())
    }
}