/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.ModifierAware
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.unsafe
import org.jetbrains.amper.frontend.api.withoutDefault
import org.jetbrains.amper.frontend.reportBundleError
import java.nio.file.Path


typealias Modifiers = Set<TraceableString>
val noModifiers = emptySet<TraceableString>()

sealed class Base : SchemaNode() {
    @SchemaDoc("The list of repositories used to look up and download the Module dependencies")
    var repositories by nullableValue<List<Repository>>()

    @ModifierAware
    @SchemaDoc("The list of modules and libraries necessary to build the Module")
    var dependencies by nullableValue<Map<Modifiers, List<Dependency>>>()

    @ModifierAware
    @SchemaDoc("Configures the toolchains used in the build process")
    var settings by value<Map<Modifiers, Settings>>()
        .default(mapOf(noModifiers to Settings()))

    @ModifierAware
    @SchemaDoc("The dependencies necessary to build and run tests of the Module")
    var `test-dependencies` by nullableValue<Map<Modifiers, List<Dependency>>>()

    @ModifierAware
    @SchemaDoc("Controls building and running the Module tests")
    var `test-settings` by value<Map<Modifiers, Settings>>()
        .default(mapOf(noModifiers to Settings()))

    context(ProblemReporterContext)
    override fun validate() {
        super.validate()
        ::dependencies.withoutDefault?.keys?.forEach{ it.validate() }
        ::settings.withoutDefault?.keys?.forEach{ it.validate() }
        ::`test-dependencies`.withoutDefault?.keys?.forEach{ it.validate() }
        ::`test-settings`.withoutDefault?.keys?.forEach{ it.validate() }
    }

    context(ProblemReporterContext)
    private fun Modifiers.validate(): Nothing? {
        val unknownPlatforms = this.filter { platform ->
            platform.value !in Platform.values.map { it.pretty }
        }

        if (unknownPlatforms.isNotEmpty())
            return SchemaBundle.reportBundleError(
                unknownPlatforms.first(),
                "product.unknown.platforms",
                unknownPlatforms.joinToString { it.value },
            )
        return null
    }
}

class Template : Base()

class Module : Base() {
    @SchemaDoc("Defines what should be produced out of the Module")
    var product by value<ModuleProduct>()

    // TODO Parse list in this:
    // aliases:
    //  - <key>: <values>
    @SchemaDoc("Defines the names for the custom code sharing groups")
    var aliases by nullableValue<Map<String, Set<Platform>>>()

    var apply by nullableValue<List<Path>>()

    var module by value<Meta>().default(Meta())
}

class Repository : SchemaNode() {
    var url by value<String>()
    var id by value<String>().default { url }
    var credentials by nullableValue<Credentials>()
    var publish by value<Boolean>().default(false)

    class Credentials : SchemaNode() {
        var file by value<Path>()
        var usernameKey by value<String>()
        var passwordKey by value<String>()
    }
}

enum class AmperLayout(
    override var schemaValue: String,
    override val outdated: Boolean = false
) : SchemaEnum {
    GRADLE("gradle-kmp"),
    GRADLE_JVM("gradle-jvm"),
    AMPER("default"),;

    companion object : EnumMap<AmperLayout, String>(AmperLayout::values, AmperLayout::schemaValue)
}

@SchemaDoc("Meta settings for current module")
class Meta : SchemaNode() {
    var layout by value<AmperLayout>().default(AmperLayout.AMPER)
}