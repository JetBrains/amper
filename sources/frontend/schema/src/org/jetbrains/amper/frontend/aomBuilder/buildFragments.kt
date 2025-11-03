/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.FragmentDependencyType
import org.jetbrains.amper.frontend.FragmentLink
import org.jetbrains.amper.frontend.Layout
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
import org.jetbrains.amper.frontend.schema.enabled
import org.jetbrains.amper.frontend.tree.resolveReferences
import java.nio.file.Path
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
    moduleFile: VirtualFile,
) : DefaultFragment(seed, module, isTest, externalDependencies, relevantSettings, moduleFile),
    LeafFragment {

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

    override val sourceRoots: List<Path> by lazy {
        when (module.layout) {
            Layout.AMPER -> listOf(moduleFile.parent.toNioPath() / "${if (isTest) "test" else "src"}$modifier")
            Layout.MAVEN_LIKE -> {
                val sourcesRoot = moduleFile.parent.toNioPath() / "src"
                listOf(
                    sourcesRoot / (if (isTest) "test" else "main") / "java",
                    sourcesRoot / (if (isTest) "test" else "main") / "kotlin",
                )
            }
        }
    }

    override val resourcesPath: Path by lazy {
        when (module.layout) {
            Layout.AMPER -> moduleFile.parent.toNioPath() / "${if (isTest) "testResources" else "resources"}$modifier"
            Layout.MAVEN_LIKE -> {
                moduleFile.parent.toNioPath() / "src" / (if (isTest) "test" else "main") / "resources"
            }
        }
    }

    override val composeResourcesPath: Path by lazy {
        when (module.layout) {
            Layout.AMPER -> moduleFile.parent.toNioPath().resolve("${if (isTest) "testComposeResources" else "composeResources"}$modifier")
            Layout.MAVEN_LIKE -> {
                moduleFile.parent.toNioPath() / "src" / (if (isTest) "test" else "main") / "composeResources"
            }
        }
    }

    override val hasAnyComposeResources: Boolean by lazy {
        composeResourcesPath.isDirectory() && composeResourcesPath.walk().any { !it.isHidden() }
    }

    override fun generatedSourceDirs(buildOutputRoot: Path): List<Path> = buildList {
        // Java compilation task always ensures the contents of this directory to be correct
        add(javaAnnotationProcessingGeneratedSourcesPath(buildOutputRoot))

        if (settings.kotlin.ksp.enabled) {
            add(kspGeneratedJavaSourcesPath(buildOutputRoot))
            add(kspGeneratedKotlinSourcesPath(buildOutputRoot))
        }

        if (settings.compose.enabled) {
            add(composeResourcesGeneratedAccessorsPath(buildOutputRoot))
            add(composeResourcesGeneratedCollectorsPath(buildOutputRoot))
            add(composeResourcesGeneratedCommonResClassPath(buildOutputRoot))
        }

        // TODO add custom-task-generated sources here
    }

    override fun generatedResourceDirs(buildOutputRoot: Path): List<Path> = buildList {
        if (settings.kotlin.ksp.enabled) {
            add(kspGeneratedResourcesPath(buildOutputRoot))
        }

        // TODO add custom-task-generated resources here
    }

    override fun generatedClassDirs(buildOutputRoot: Path): List<Path> = buildList {
        if (settings.kotlin.ksp.enabled) {
            add(kspGeneratedClassesPath(buildOutputRoot))
        }

        // TODO add custom-task-generated classes here
    }
}

/**
 * The path to the root of _all_ the generated files for this [Fragment].
 */
fun Fragment.generatedFilesRoot(buildOutputRoot: Path): Path = buildOutputRoot / "generated" / module.userReadableName / name

/**
 * The path to the root of the generated sources for this [Fragment].
 */
fun Fragment.generatedSourcesRoot(buildOutputRoot: Path): Path = generatedFilesRoot(buildOutputRoot) / "src"

/**
 * The path to the root of the generated resources for this [Fragment].
 */
fun Fragment.generatedResourcesRoot(buildOutputRoot: Path): Path = generatedFilesRoot(buildOutputRoot) / "resources"

/**
 * The path to the root of the generated classes for this [Fragment].
 */
fun Fragment.generatedClassesRoot(buildOutputRoot: Path): Path = generatedFilesRoot(buildOutputRoot) / "classes"

/**
 * The path to the root of the KSP-generated Kotlin sources for this [Fragment].
 */
fun Fragment.kspGeneratedKotlinSourcesPath(buildOutputRoot: Path): Path =
    generatedSourcesRoot(buildOutputRoot) / "ksp/kotlin"

/**
 * The path to the root of the KSP-generated Java sources for this [Fragment].
 */
fun Fragment.kspGeneratedJavaSourcesPath(buildOutputRoot: Path): Path =
    generatedSourcesRoot(buildOutputRoot) / "ksp/java"

/**
 * The path to the root of the KSP-generated resources for this [Fragment].
 */
fun Fragment.kspGeneratedResourcesPath(buildOutputRoot: Path): Path =
    generatedResourcesRoot(buildOutputRoot) / "ksp"

/**
 * The path to the root of the KSP-generated classes for this [Fragment].
 */
fun Fragment.kspGeneratedClassesPath(buildOutputRoot: Path): Path =
    generatedClassesRoot(buildOutputRoot) / "ksp"

fun Fragment.composeResourcesGeneratedAccessorsPath(buildOutputRoot: Path): Path =
    generatedSourcesRoot(buildOutputRoot) / "compose/resources/accessors"

fun Fragment.composeResourcesGeneratedCollectorsPath(buildOutputRoot: Path): Path =
    generatedSourcesRoot(buildOutputRoot) / "compose/resources/collectors"

fun Fragment.composeResourcesGeneratedCommonResClassPath(buildOutputRoot: Path): Path =
    generatedSourcesRoot(buildOutputRoot) / "compose/resources/commonResClass"

fun Fragment.javaAnnotationProcessingGeneratedSourcesPath(buildOutputRoot: Path): Path =
    generatedSourcesRoot(buildOutputRoot) / "apt/java"

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
            ) = when (valuePath[0]) {
                "settings", "dependencies" -> super.onMissingRequiredPropertyValue(trace, valuePath)
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
