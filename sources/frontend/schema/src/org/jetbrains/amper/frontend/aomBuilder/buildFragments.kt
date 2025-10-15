/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.FragmentDependencyType
import org.jetbrains.amper.frontend.FragmentLink
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Notation
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.contexts.PathCtx
import org.jetbrains.amper.frontend.contexts.PlatformCtx
import org.jetbrains.amper.frontend.contexts.TestCtx
import org.jetbrains.amper.frontend.schema.Dependency
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.tree.Refined
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.tree.resolveReferences
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isHidden
import kotlin.io.path.walk

class DefaultLeafFragment(
    seed: FragmentSeed,
    module: AmperModule,
    isTest: Boolean,
    externalDependencies: List<Notation>,
    relevantSettings: Settings,
    usedTree: TreeValue<Refined>,
    moduleFile: VirtualFile,
) : DefaultFragment(seed, module, isTest, externalDependencies, relevantSettings, usedTree, moduleFile), LeafFragment {
    init {
        assert(seed.isLeaf) { "Should be created only for leaf platforms!" }
    }

    override val platform = seed.platforms.single()
}

open class DefaultFragment(
    seed: FragmentSeed,
    final override val module: AmperModule,
    final override val isTest: Boolean,
    override var externalDependencies: List<Notation>,
    override val settings: Settings,
    override val usedTree: TreeValue<Refined>,
    moduleFile: VirtualFile,
) : Fragment {
    final override val modifier = seed.modifier

    final override val fragmentDependencies = mutableListOf<FragmentLink>()

    override val fragmentDependants = mutableListOf<FragmentLink>()

    final override val name = run {
        val suffix = if (isTest) "Test" else ""
        val modifier = seed.modifier.removePrefix("@")
        when {
            modifier.isNotEmpty() -> modifier + suffix
            seed.naturalHierarchyPlatform?.isLeaf == true -> if (isTest) "test" else "main"
            else -> "common$suffix"
        }
    }

    final override val platforms = seed.platforms

    override val isDefault = true

    override val src: Path by lazy {
        moduleFile.parent.toNioPath().resolve("${if (isTest) "test" else "src"}$modifier")
    }

    override val resourcesPath: Path by lazy {
        moduleFile.parent.toNioPath().resolve("${if (isTest) "testResources" else "resources"}$modifier")
    }

    override val composeResourcesPath: Path by lazy {
        moduleFile.parent.toNioPath().resolve("${if (isTest) "testComposeResources" else "composeResources"}$modifier")
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

            add(generateSrcRoot / JavaAnnotationProcessingPathConventions.JavaSources)

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

/**
 * Contains the conventional paths to different Java annotation processing output file types relative to the corresponding generated root.
 */
private object JavaAnnotationProcessingPathConventions {
    const val JavaSources = "apt/java"
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
    findConventionalPath(buildOutputRoot, generatedClassesRelativeDirs, KspPathConventions.Classes)

fun Fragment.composeResourcesGeneratedAccessorsPath(buildOutputRoot: Path): Path =
    findConventionalPath(buildOutputRoot, generatedSrcRelativeDirs, ComposeResourcesPathConventions.Accessors)

fun Fragment.composeResourcesGeneratedCollectorsPath(buildOutputRoot: Path): Path =
    findConventionalPath(buildOutputRoot, generatedSrcRelativeDirs, ComposeResourcesPathConventions.Collectors)

fun Fragment.composeResourcesGeneratedCommonResClassPath(buildOutputRoot: Path): Path =
    findConventionalPath(buildOutputRoot, generatedSrcRelativeDirs, ComposeResourcesPathConventions.CommonResClass)

fun Fragment.javaAnnotationProcessingGeneratedSourcesPath(buildOutputRoot: Path): Path =
    findConventionalPath(buildOutputRoot, generatedSrcRelativeDirs, JavaAnnotationProcessingPathConventions.JavaSources)

private fun findConventionalPath(buildOutputRoot: Path, genDirs: List<Path>, pathSuffix: String) =
    genDirs.map { buildOutputRoot / it }.find { it.endsWith(pathSuffix) }
        ?: error(
            "generated dir paths don't contain conventional generated path suffix '$pathSuffix'. Found:\n" +
                    genDirs.joinToString("\n")
        )

internal fun BuildCtx.createFragments(
    seeds: Collection<FragmentSeed>,
    ctx: ModuleBuildCtx,
    resolveDependency: (Dependency) -> Notation?,
): List<DefaultFragment> {
    data class FragmentBundle(
        val mainFragment: DefaultFragment,
        val testFragment: DefaultFragment,
    )

    fun FragmentSeed.toFragment(isTest: Boolean): DefaultFragment? {
        val testCtx = if (isTest) setOf(TestCtx) else EmptyContexts
        val selectedContexts = testCtx +
                platforms.map { PlatformCtx(it.pretty) } +
                PathCtx(ctx.moduleFile)
        val refinedTree = ctx.refiner.refineTree(ctx.mergedTree, selectedContexts)
            .resolveReferences()
        val handler = object : MissingPropertiesHandler.Default(problemReporter) {
            override fun onMissingRequiredPropertyValue(
                trace: Trace,
                valuePath: List<String>,
                keyTrace: Trace?,
            ) = when (valuePath[0]) {
                "settings", "dependencies" -> super.onMissingRequiredPropertyValue(trace, valuePath, keyTrace)
                else -> Unit  // ignoring; was already reported in the `ctx.moduleCtxModule`.
            }
        }
        val refinedModule = createSchemaNode<Module>(refinedTree, handler)
            ?: return null
        val fragmentCtor = if (isLeaf) ::DefaultLeafFragment else ::DefaultFragment
        return fragmentCtor(
            this,
            ctx.module,
            isTest,
            refinedModule.dependencies.orEmpty().mapNotNull { resolveDependency(it) },
            refinedModule.settings,
            refinedTree,
            ctx.moduleFile,
        )
    }

    // Create fragments.
    val initial = buildMap {
        for (seed in seeds) {
            val main = seed.toFragment(false) ?: continue
            val test = seed.toFragment(true) ?: continue
            put(seed, FragmentBundle(main, test))
        }
    }

    // Set fragment dependencies.
    initial.entries.forEach { (seed, bundle) ->
        // Main fragment dependency.
        bundle.mainFragment.fragmentDependencies += seed.dependencies.map { initial[it]!!.mainFragment.asRefine() }
        seed.dependencies.forEach { initial.getValue(it).mainFragment.fragmentDependants += bundle.mainFragment.asRefine() }

        // Test fragment dependency.
        bundle.testFragment.fragmentDependencies += seed.dependencies.map { initial[it]!!.testFragment.asRefine() }
        seed.dependencies.forEach { initial.getValue(it).testFragment.fragmentDependants += bundle.testFragment.asRefine() }

        // Main - test dependency.
        bundle.testFragment.fragmentDependencies += bundle.mainFragment.asFriend()
        bundle.mainFragment.fragmentDependants += bundle.testFragment.asFriend()
    }

    // Unfold fragments bundles.
    return initial.values.flatMap { listOf(it.mainFragment, it.testFragment) }
}

class SimpleFragmentLink(
    override val target: Fragment,
    override val type: FragmentDependencyType,
) : FragmentLink

private fun Fragment.asFriend() = SimpleFragmentLink(this, FragmentDependencyType.FRIEND)
private fun Fragment.asRefine() = SimpleFragmentLink(this, FragmentDependencyType.REFINE)
