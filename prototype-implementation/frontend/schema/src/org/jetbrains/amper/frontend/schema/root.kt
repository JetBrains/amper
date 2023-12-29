/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.ModifierAware
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaEnum
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceableString
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
    override var schemaValue: String
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