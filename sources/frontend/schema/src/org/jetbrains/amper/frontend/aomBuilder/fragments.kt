/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.mapStartAware
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.FragmentDependencyType
import org.jetbrains.amper.frontend.FragmentLink
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Notation
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.doCapitalize
import org.jetbrains.amper.frontend.schema.Dependency
import org.jetbrains.amper.frontend.schema.Settings
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isHidden
import kotlin.io.path.walk

class DefaultLeafFragment(
    seed: FragmentSeed,
    module: PotatoModule,
    isTest: Boolean,
    externalDependencies: List<Notation>,
    relevantSettings: Settings?,
    moduleFile: VirtualFile,
) : DefaultFragment(seed, module, isTest, externalDependencies, relevantSettings, moduleFile), LeafFragment {
    init {
        assert(seed.isLeaf) { "Should be created only for leaf platforms!" }
    }

    override val platform = seed.platforms.single()
}

open class DefaultFragment(
    seed: FragmentSeed,
    final override val module: PotatoModule,
    final override val isTest: Boolean,
    override val externalDependencies: List<Notation>,
    relevantSettings: Settings?,
    moduleFile: VirtualFile,
) : Fragment {

    private val isCommon = seed.modifiersAsStrings == setOf(Platform.COMMON.pretty)

    /**
     * Modifier that is used to reference this fragment in the module file or within source directory.
     */
    private val modifier = if (isCommon) "" else "@" + seed.modifiersAsStrings.joinToString(separator = "+")

    final override val fragmentDependencies = mutableListOf<FragmentLink>()

    override val fragmentDependants = mutableListOf<FragmentLink>()

    final override val name = seed.modifiersAsStrings
        .mapStartAware { isStart, it -> if (isStart) it else it.doCapitalize() }
        .joinToString() +
            if (isTest) "Test" else ""

    // FIXME remove this workaround once https://youtrack.jetbrains.com/issue/AMPER-462 is fixed
    private val actualModulePlatforms = module.origin.product.platforms.map { it.value }.toSet()
    final override val platforms = seed.platforms.filter { it in actualModulePlatforms }.toSet()

    override val variants = emptyList<String>()

    override val settings = relevantSettings ?: Settings()

    override val isDefault = true

    private val srcOnlyOwner by lazy {
        !isCommon && fragmentDependencies.none { it.type == FragmentDependencyType.REFINE }
    }

    override val src: Path by lazy {
        val srcStringPrefix = if (isTest) "test" else "src"
        val srcPathString =
            if (srcOnlyOwner) srcStringPrefix
            else "$srcStringPrefix$modifier"
        moduleFile.parent.toNioPath().resolve(srcPathString)
    }

    override val resourcesPath: Path by lazy {
        val resourcesStringPrefix = if (isTest) "testResources" else "resources"
        val resourcesPathString =
            if (srcOnlyOwner) resourcesStringPrefix
            else "$resourcesStringPrefix$modifier"
        moduleFile.parent.toNioPath().resolve(resourcesPathString)
    }

    override val composeResourcesPath: Path by lazy {
        val resourcesStringPrefix = if (isTest) "testComposeResources" else "composeResources"
        val resourcesPathString =
            if (srcOnlyOwner) resourcesStringPrefix
            else "$resourcesStringPrefix$modifier"
        moduleFile.parent.toNioPath().resolve(resourcesPathString)
    }

    override val hasAnyComposeResources: Boolean by lazy {
        composeResourcesPath.isDirectory() && composeResourcesPath.walk().any { !it.isHidden() }
    }

    private val generatedFilesRelativeRoot: Path = Path("generated/${module.userReadableName}/$name")

    override val generatedSrcRelativeDirs: List<Path> by lazy {
        val generateSrcRoot = generatedFilesRelativeRoot / "src"
        buildList {
            add(generateSrcRoot / KspPathConventions.JavaSources)
            add(generateSrcRoot / KspPathConventions.KotlinSources)

            add(generateSrcRoot / ComposeResourcesPathConventions.Accessors)
            add(generateSrcRoot / ComposeResourcesPathConventions.Collectors)
            add(generateSrcRoot / ComposeResourcesPathConventions.CommonResClass)

            // TODO add custom-task-generated sources here
        }
    }

    override val generatedResourcesRelativeDirs: List<Path> by lazy {
        val resourcesRoot = generatedFilesRelativeRoot / "resources"
        buildList {
            add(resourcesRoot / KspPathConventions.Resources)
            // TODO add custom-task-generated resources here
        }
    }

    override val generatedClassesRelativeDirs: List<Path> by lazy {
        val classesRoot = generatedFilesRelativeRoot / "classes"
        buildList {
            add(classesRoot / KspPathConventions.Classes)
            // TODO add custom-task-generated classes here
        }
    }
}

/**
 * Contains the conventional paths to different KSP output file types relative to the corresponding generated root.
 */
private object KspPathConventions {
    const val JavaSources = "ksp/java"
    const val KotlinSources = "ksp/kotlin"
    const val Resources = "ksp"
    const val Classes = "ksp"
}

private object ComposeResourcesPathConventions {
    const val Accessors = "compose/resources/accessors"
    const val Collectors = "compose/resources/collectors"
    const val CommonResClass = "compose/resources/commonResClass"
}

/**
 * The path to the root of the KSP-generated Kotlin sources for this [Fragment].
 */
fun Fragment.kspGeneratedKotlinSourcesPath(buildOutputRoot: Path): Path =
    findConventionalPath(buildOutputRoot, generatedSrcRelativeDirs, KspPathConventions.KotlinSources)

/**
 * The path to the root of the KSP-generated Java sources for this [Fragment].
 */
fun Fragment.kspGeneratedJavaSourcesPath(buildOutputRoot: Path): Path =
    findConventionalPath(buildOutputRoot, generatedSrcRelativeDirs, KspPathConventions.JavaSources)

/**
 * The path to the root of the KSP-generated resources for this [Fragment].
 */
fun Fragment.kspGeneratedResourcesPath(buildOutputRoot: Path): Path =
    findConventionalPath(buildOutputRoot, generatedResourcesRelativeDirs, KspPathConventions.Resources)

/**
 * The path to the root of the KSP-generated classes for this [Fragment].
 */
fun Fragment.kspGeneratedClassesPath(buildOutputRoot: Path): Path =
    findConventionalPath(buildOutputRoot, generatedResourcesRelativeDirs, KspPathConventions.Classes)

fun Fragment.composeResourcesGeneratedAccessorsPath(buildOutputRoot: Path): Path =
    findConventionalPath(buildOutputRoot, generatedSrcRelativeDirs, ComposeResourcesPathConventions.Accessors)

fun Fragment.composeResourcesGeneratedCollectorsPath(buildOutputRoot: Path): Path =
    findConventionalPath(buildOutputRoot, generatedSrcRelativeDirs, ComposeResourcesPathConventions.Collectors)

fun Fragment.composeResourcesGeneratedCommonResClassPath(buildOutputRoot: Path): Path =
    findConventionalPath(buildOutputRoot, generatedSrcRelativeDirs, ComposeResourcesPathConventions.CommonResClass)

private fun findConventionalPath(buildOutputRoot: Path, genDirs: List<Path>, pathSuffix: String) =
    genDirs.map { buildOutputRoot / it }.find { it.endsWith(pathSuffix) }
        ?: error("generated dir paths don't contain conventional generated path suffix '$pathSuffix'. Found:\n" +
                genDirs.joinToString("\n"))

fun createFragments(
    seeds: Collection<FragmentSeed>,
    moduleFile: VirtualFile,
    module: PotatoModule,
    resolveDependency: (Dependency) -> Notation?,
): List<DefaultFragment> {
    data class FragmentBundle(
        val mainFragment: DefaultFragment,
        val testFragment: DefaultFragment,
    )

    fun FragmentSeed.toFragment(isTest: Boolean, externalDependencies: List<Notation>) =
        if (isLeaf) DefaultLeafFragment(
            this,
            module,
            isTest,
            externalDependencies,
            if (isTest) relevantTestSettings else relevantSettings,
            moduleFile
        )
        else DefaultFragment(
            this,
            module,
            isTest,
            externalDependencies,
            if (isTest) relevantTestSettings else relevantSettings,
            moduleFile
        )

    // Create fragments.
    val initial = seeds.associateWith {
        FragmentBundle(
            // TODO Report unresolved dependencies
            it.toFragment(false, it.relevantDependencies?.mapNotNull { resolveDependency(it) }.orEmpty()),
            it.toFragment(true, it.relevantTestDependencies?.mapNotNull { resolveDependency(it) }.orEmpty()),
        )
    }

    // Set fragment dependencies.
    initial.entries.forEach { (seed, bundle) ->
        // Main fragment dependency.
        bundle.mainFragment.fragmentDependencies +=
            seed.dependencies.map { initial[it]!!.mainFragment.asRefine() }

        seed.dependencies.forEach {
            initial[it]!!.mainFragment.fragmentDependants += bundle.mainFragment.asRefine()
        }

        // Test fragment dependency.
        bundle.testFragment.fragmentDependencies +=
            seed.dependencies.map { initial[it]!!.testFragment.asRefine() }

        seed.dependencies.forEach {
            initial[it]!!.testFragment.fragmentDependants += bundle.testFragment.asRefine()
        }

        // Main - test dependency.
        bundle.testFragment.fragmentDependencies +=
            bundle.mainFragment.asFriend()

        bundle.mainFragment.fragmentDependants +=
            bundle.testFragment.asFriend()
    }

    // Unfold fragments bundles.
    return initial.values.flatMap { listOf(it.mainFragment, it.testFragment) }
}

class SimpleFragmentLink(
    override val target: Fragment,
    override val type: FragmentDependencyType
) : FragmentLink

private fun Fragment.asFriend() = SimpleFragmentLink(this, FragmentDependencyType.FRIEND)
private fun Fragment.asRefine() = SimpleFragmentLink(this, FragmentDependencyType.REFINE)
