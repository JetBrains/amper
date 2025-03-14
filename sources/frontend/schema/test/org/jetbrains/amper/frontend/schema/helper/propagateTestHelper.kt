/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import org.jetbrains.amper.frontend.Artifact
import org.jetbrains.amper.frontend.ClassBasedSet
import org.jetbrains.amper.frontend.CustomTaskDescription
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.FragmentDependencyType
import org.jetbrains.amper.frontend.FragmentLink
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.ModulePart
import org.jetbrains.amper.frontend.Notation
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.AmperModuleProgrammaticSource
import org.jetbrains.amper.frontend.AmperModuleSource
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.classBasedSet
import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.Settings
import java.nio.file.Path
import kotlin.io.path.Path

fun amperModule(name: String, init: AmperModuleBuilder.() -> Unit): AmperModule {
    val builder = AmperModuleBuilder(name)
    builder.init()
    return builder.build()
}

class FragmentBuilder(var name: String) {
    private val fragmentDependencies: MutableList<NamedFragmentLink> = mutableListOf()
    private val fragmentDependants: MutableList<NamedFragmentLink> = mutableListOf()
    private val externalDependencies: MutableList<Notation> = mutableListOf()
    private val settings = Settings()
    private val platforms: MutableSet<Platform> = mutableSetOf()

    private val variants: MutableList<String> = mutableListOf()

    fun AmperModuleBuilder.dependsOn(to: String) {
        fragmentDependencies.add(NamedFragmentLink(targetName = to, type = FragmentDependencyType.REFINE))
    }

    fun AmperModuleBuilder.dependant(to: String) {
        fragmentDependants.add(NamedFragmentLink(targetName = to, type = FragmentDependencyType.REFINE))
    }

    fun kotlin(init: KotlinSettings.() -> Unit) {
        // we reassign to trigger the delegate (to force non-default state)
        settings.kotlin = KotlinSettings().apply(init)
    }

    fun jvm(init: JvmSettings.() -> Unit) {
        // we reassign to trigger the delegate (to force non-default state)
        settings.jvm = JvmSettings().apply(init)
    }

    fun android(init: AndroidSettings.() -> Unit) {
        // we reassign to trigger the delegate (to force non-default state)
        settings.android = AndroidSettings().apply(init)
    }

    fun build(module: AmperModule): LeafFragment {
        return object : LeafFragment {
            override val platform: Platform
                get() = this@FragmentBuilder.platforms.single()
            override val name: String
                get() = this@FragmentBuilder.name
            override val module: AmperModule
                get() = module
            override val fragmentDependencies: List<FragmentLink> by lazy {
                this@FragmentBuilder.fragmentDependencies.resolveIn(module)
            }
            override val fragmentDependants: List<FragmentLink> by lazy {
                this@FragmentBuilder.fragmentDependants.resolveIn(module)
            }
            override val externalDependencies: List<Notation>
                get() = this@FragmentBuilder.externalDependencies
            override val settings: Settings
                get() = this@FragmentBuilder.settings
            override val platforms: Set<Platform>
                get() = this@FragmentBuilder.platforms
            override val isTest: Boolean
                get() = false
            override val isDefault: Boolean
                get() = true
            override val src: Path get() = Path(name).resolve("main")
            override val resourcesPath get() = Path(name).resolve("resources")
            override val composeResourcesPath get() = Path(name).resolve("composeResources")
            override val hasAnyComposeResources: Boolean get() = false
            override val generatedResourcesRelativeDirs: List<Path> = emptyList()
            override val generatedSrcRelativeDirs: List<Path> = emptyList()
            override val generatedClassesRelativeDirs: List<Path> = emptyList()
            override val variants: List<String>
                get() = this@FragmentBuilder.variants
        }
    }
}

private data class NamedFragmentLink(
    val targetName: String,
    val type: FragmentDependencyType,
)

private fun List<NamedFragmentLink>.resolveIn(module: AmperModule) =
    map { namedLink -> namedLink.resolveIn(module) }

private fun NamedFragmentLink.resolveIn(module: AmperModule) =
    FragmentLink(module.fragments.first { it.name == targetName }, type)

private fun FragmentLink(target: Fragment, type: FragmentDependencyType) = object : FragmentLink {
    override val target: Fragment = target
    override val type: FragmentDependencyType = type
}

class AmperModuleBuilder(var name: String) {
    var type: ProductType = ProductType.JVM_APP
    var source: AmperModuleSource = AmperModuleProgrammaticSource
    val fragments: MutableList<FragmentBuilder> = mutableListOf()
    private val artifacts = emptyList<Artifact>()
    private val parts = classBasedSet<ModulePart<*>>()

    fun fragment(name: String, init: FragmentBuilder.() -> Unit): FragmentBuilder {
        val builder = FragmentBuilder(name)
        builder.init()
        fragments.add(builder)
        return builder
    }

    fun build(): AmperModule {
        return object : AmperModule {
            override val userReadableName: String
                get() = this@AmperModuleBuilder.name
            override val type: ProductType
                get() = this@AmperModuleBuilder.type
            override val source: AmperModuleSource
                get() = this@AmperModuleBuilder.source
            override val origin: Module
                get() = Module()
            override val fragments: List<Fragment>
                get() = this@AmperModuleBuilder.fragments.map { it.build(this) }
            override val artifacts: List<Artifact>
                get() = this@AmperModuleBuilder.artifacts
            override val parts: ClassBasedSet<ModulePart<*>>
                get() = this@AmperModuleBuilder.parts
            override val usedCatalog: VersionCatalog?
                get() = null
            override val customTasks: List<CustomTaskDescription>
                get() = emptyList()
        }
    }
}
