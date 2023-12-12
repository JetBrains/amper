/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.*
import java.nio.file.Path


typealias Modifiers = Set<TraceableString>
val noModifiers = emptySet<TraceableString>()

sealed class Base : SchemaNode() {
    @SchemaDoc("The list of repositories used to look up and download the Module dependencies")
    val repositories = value<List<Repository>>()

    @ModifierAware
    @SchemaDoc("The list of modules and libraries necessary to build the Module")
    val dependencies = value<Map<Modifiers, List<Dependency>>>()

    @ModifierAware
    @SchemaDoc("Configures the toolchains used in the build process")
    val settings = value<Map<Modifiers, Settings>>()

    @ModifierAware
    @SchemaDoc("The dependencies necessary to build and run tests of the Module")
    val `test-dependencies` = value<Map<Modifiers, List<Dependency>>>()

    @ModifierAware
    @SchemaDoc("Controls building and running the Module tests")
    val `test-settings` = value<Map<Modifiers, Settings>>()
}

class Template : Base()

class Module : Base() {
    @SchemaDoc("Defines what should be produced out of the Module")
    val product = value<ModuleProduct>()

    @SchemaDoc("Defines the names for the custom code sharing groups")
    val aliases = nullableValue<Map<String, Set<Platform>>>()

    val apply = nullableValue<Collection<Path>>()

    val module = value<Meta>().default(Meta())
}

class Repository : SchemaNode() {
    val url = value<String>()
    val id = value<String>().default("Defaults to url") { url.value }
    val credentials = nullableValue<Credentials>()
    val publish = value<Boolean>().default(false)

    class Credentials : SchemaNode() {
        val file = value<Path>()
        val usernameKey = value<String>()
        val passwordKey = value<String>()
    }
}

enum class AmperLayout(
    override val schemaValue: String
) : SchemaEnum {
    GRADLE("gradle-kmp"),
    GRADLE_JVM("gradle-jvm"),
    AMPER("default"),;

    companion object : EnumMap<AmperLayout, String>(AmperLayout::values, AmperLayout::schemaValue)
}

@SchemaDoc("Meta settings for current module")
class Meta : SchemaNode() {
    val layout = value<AmperLayout>().default(AmperLayout.AMPER)
}