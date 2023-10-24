package org.jetbrains.amper.frontend.model

import org.jetbrains.amper.frontend.*
import java.nio.file.Path

context (Stateful<FragmentBuilder, Fragment>, TypesafeVariants)
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
                serialization = serialization?.format
            )
        }

        addPartFrom(fragmentBuilder.android) {
            AndroidPart(
                compileSdk?.toStringVersion(),
                minSdk?.toIntAsString(),
                maxSdk?.toIntVersion(),
                targetSdk?.toIntAsString(),
                applicationId,
                namespace,
            )
        }

        addPartFrom(fragmentBuilder.ios) {
            IosPart(teamId)
        }

        addPartFrom(fragmentBuilder.java) {
            JavaPart(
                source,
            )
        }

        addPartFrom(fragmentBuilder.jvm) {
            JvmPart(
                mainClass,
                target,
            )
        }

        addPartFrom(fragmentBuilder.junit) {
            JUnitPart(
                jUnitVersion,
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
                entryPoint = entryPoint,
                declaredFrameworkBasename = frameworkSettings?.declaredBasename,
                frameworkParams = frameworkSettings?.settings?.toMap()
            )
        }

        addPartFrom(fragmentBuilder.compose) {
            ComposePart(enabled)
        }
    }

    override val platforms: Set<Platform>
        get() = fragmentBuilder.platforms

    override val src: Path
        get() = fragmentBuilder.src

    override val resourcesPath: Path
        get() = fragmentBuilder.resourcesPath

    override val variants: List<String>
        get() = (fragmentBuilder.variants - defaultVariants - dimensionVariants).toList()
}

context (Stateful<FragmentBuilder, Fragment>, TypesafeVariants)
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