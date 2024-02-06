/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.AdditionalSchemaDef
import org.jetbrains.amper.frontend.api.ModifierAware
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.withoutDefault
import org.jetbrains.amper.frontend.reportBundleError
import java.nio.file.Path


typealias Modifiers = Set<TraceableString>
val noModifiers = emptySet<TraceableString>()

sealed class Base : SchemaNode() {

    @SchemaDoc("The list of repositories used to look up and download the Module dependencies. See [repositories](#managing-maven-repositories)")
    var repositories by nullableValue<List<Repository>>()

    @ModifierAware
    @SchemaDoc("The list of modules and libraries necessary to build the Module. See [dependencies](#dependencies)")
    var dependencies by nullableValue<Map<Modifiers, List<Dependency>>>()

    @ModifierAware
    @SchemaDoc("Configures the toolchains used in the build process. See [settings](#settings)")
    var settings by value(mapOf(noModifiers to Settings()))

    @ModifierAware
    @SchemaDoc("The dependencies necessary to build and run tests of the Module. See [dependencies](#dependencies)")
    var `test-dependencies` by nullableValue<Map<Modifiers, List<Dependency>>>()

    @ModifierAware
    @SchemaDoc("Controls building and running the Module tests. See [settings](#settings)")
    var `test-settings` by value(mapOf(noModifiers to Settings()))

    /**
     * Check that modifiers are either known platforms, or known aliases.
     */
    context(ProblemReporterContext)
    protected fun validateModifiers(knownAliases: Set<String> = emptySet()) {
        fun Modifiers.validate() {
            val unknownPlatforms = this
                .filter { it.value !in knownAliases }
                .filter { it.value !in Platform.values.map { it.schemaValue } }

            if (unknownPlatforms.isNotEmpty())
                SchemaBundle.reportBundleError(
                    unknownPlatforms.first(),
                    "product.unknown.platforms",
                    unknownPlatforms.joinToString { it.value },
                )
        }

        ::dependencies.withoutDefault?.keys?.forEach { it.validate() }
        ::settings.withoutDefault?.keys?.forEach { it.validate() }
        ::`test-dependencies`.withoutDefault?.keys?.forEach { it.validate() }
        ::`test-settings`.withoutDefault?.keys?.forEach { it.validate() }
    }
}

/**
 * Common settings section.
 */
val Base.commonSettings get() = settings[noModifiers]!!

/**
 * Common test settings section.
 */
val Base.commonTestSettings get() = `test-settings`[noModifiers]!!

class Template : Base() {
    context(ProblemReporterContext)
    override fun validate() {
        // Check the modifiers.
        validateModifiers()
    }
}

class Module : Base() {

    @SchemaDoc("Defines what should be produced out of the module. See [products](#product-types)")
    var product by value<ModuleProduct>()

    @SchemaDoc("Defines the names for the custom code sharing groups. See [aliases](#aliases)")
    var aliases by nullableValue<Map<String, Set<Platform>>>()

    @SchemaDoc("List of templates that are applied. See [Templates](#templates)")
    var apply by nullableValue<List<Path>>()

    @SchemaDoc("Non-code/product related aspects of the Module (e.g. file layout)")
    var module by value(::Meta)

    context(ProblemReporterContext)
    override fun validate() {
        // Check the modifiers.
        validateModifiers(aliases?.keys.orEmpty())
    }
}

@AdditionalSchemaDef(repositoryShortForm, useOneOf = true)
@SchemaDoc("Maven repository settings")
class Repository : SchemaNode() {

    @SchemaDoc("The url to the repository")
    var url by value<String>()

    @SchemaDoc("The ID of the repository, used for to reference it (defaults to url)")
    var id by value { url }

    @SchemaDoc("Username/password authentication support")
    var credentials by nullableValue<Credentials>()

    @SchemaDoc("Should this repository used to publish artifacts")
    var publish by value(false)

    @SchemaDoc("Username/password pair for maven repository")
    class Credentials : SchemaNode() {

        @SchemaDoc("A relative path to a file with the credentials")
        var file by value<Path>()

        @SchemaDoc("A key in the file that holds the username")
        var usernameKey by value<String>()

        @SchemaDoc("A key in the file that holds the password")
        var passwordKey by value<String>()
    }
}

const val repositoryShortForm = """
  {
    "type": "string"
  }
"""

@SchemaDoc("File layout that is used for the module")
enum class AmperLayout(
    override var schemaValue: String,
    override val outdated: Boolean = false
) : SchemaEnum {
    @SchemaDoc("Gradle like file layout")
    GRADLE("gradle-kmp"),

    @SchemaDoc("Jvm compatible gradle like layout")
    GRADLE_JVM("gradle-jvm"),

    @SchemaDoc("Amper file layout")
    AMPER("default"),;

    companion object : EnumMap<AmperLayout, String>(AmperLayout::values, AmperLayout::schemaValue)
}

@SchemaDoc("Meta settings for current module")
class Meta : SchemaNode() {

    @SchemaDoc("Which file layout to use")
    var layout by value(AmperLayout.AMPER)
}