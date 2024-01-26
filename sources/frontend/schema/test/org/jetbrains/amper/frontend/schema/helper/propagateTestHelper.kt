/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import org.jetbrains.amper.frontend.*
import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.Settings
import java.nio.file.Path
import kotlin.io.path.Path

context (PotatoModuleBuilder) class FragmentLinkProvider(
        override val type: FragmentDependencyType,
        private val fragmentName: String
) : FragmentLink {
    override val target: Fragment
        get() = fragments.firstOrNull { it.name == fragmentName }?.build()
            ?: error("There is no such fragment with name: $fragmentName")

    data class FragmentLinkProviderBuilder(
            val sourceFragment: FragmentBuilder,
            var name: String = "",
            var type: FragmentDependencyType = FragmentDependencyType.REFINE
    )

    companion object {
        context (PotatoModuleBuilder, FragmentBuilder) operator fun invoke(init: FragmentLinkProviderBuilder.() -> Unit): FragmentLinkProvider {
            val builder = FragmentLinkProviderBuilder(this@FragmentBuilder)
            builder.init()
            return FragmentLinkProvider(builder.type, builder.name)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FragmentLinkProvider

        return fragmentName == other.fragmentName
    }

    override fun hashCode(): Int {
        return fragmentName.hashCode()
    }
}

fun potatoModule(name: String, init: PotatoModuleBuilder.() -> Unit): PotatoModule {
    val builder = PotatoModuleBuilder(name)
    builder.init()
    return builder.build()
}

class FragmentBuilder(var name: String) {
    private val fragmentDependencies: MutableList<FragmentLink> = mutableListOf()
    private val fragmentDependants: MutableSet<FragmentLink> = mutableSetOf()
    private val externalDependencies: MutableList<Notation> = mutableListOf()
    private val settings = Settings()
    private val platforms: MutableSet<Platform> = mutableSetOf()

    private val variants: MutableList<String> = mutableListOf()

    context (PotatoModuleBuilder) fun dependsOn(
        to: String, init: FragmentLinkProvider.FragmentLinkProviderBuilder.() -> Unit = {}
    ) {
        fragmentDependencies.add(FragmentLinkProvider {
            name = to
            init()
        })
    }

    context (PotatoModuleBuilder) fun dependant(
        to: String, init: FragmentLinkProvider.FragmentLinkProviderBuilder.() -> Unit = {}
    ) {
        fragmentDependants.add(FragmentLinkProvider {
            name = to
            init()
        })
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

    fun build(): LeafFragment {
        return object : LeafFragment {
            override val platform: Platform
                get() = this@FragmentBuilder.platforms.single()
            override val name: String
                get() = this@FragmentBuilder.name
            override val fragmentDependencies: List<FragmentLink>
                get() = this@FragmentBuilder.fragmentDependencies
            override val fragmentDependants: List<FragmentLink>
                get() = this@FragmentBuilder.fragmentDependants.toList()
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
            override val resourcesPath get() = Path(name).resolve("")
            override val variants: List<String>
                get() = this@FragmentBuilder.variants
        }
    }
}

class PotatoModuleBuilder(var name: String) {
    var type: ProductType = ProductType.JVM_APP
    var source: PotatoModuleSource = PotatoModuleProgrammaticSource
    val fragments: MutableList<FragmentBuilder> = mutableListOf()
    private val artifacts = emptyList<Artifact>()
    private val parts = classBasedSet<ModulePart<*>>()

    fun fragment(name: String, init: FragmentBuilder.() -> Unit): FragmentBuilder {
        val builder = FragmentBuilder(name)
        builder.init()
        fragments.add(builder)
        return builder
    }

    fun build(): PotatoModule {
        return object : PotatoModule {
            override val userReadableName: String
                get() = this@PotatoModuleBuilder.name
            override val type: ProductType
                get() = this@PotatoModuleBuilder.type
            override val source: PotatoModuleSource
                get() = this@PotatoModuleBuilder.source
            override val origin: Module
                get() = Module()
            override val fragments: List<Fragment>
                get() = this@PotatoModuleBuilder.fragments.map { it.build() }
            override val artifacts: List<Artifact>
                get() = this@PotatoModuleBuilder.artifacts
            override val parts: ClassBasedSet<ModulePart<*>>
                get() = this@PotatoModuleBuilder.parts
            override val usedCatalog: VersionCatalog?
                get() = null
        }
    }
}
