/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.*
import java.nio.file.Path


typealias Modifiers = Set<TraceableString>

sealed class Base : SchemaBase() {
    @Embedded
    @SchemaDoc("The list of repositories used to look up and download the Module dependencies")
    val repositories = value<Repositories>()

    @ModifierAware
    @SchemaDoc("The list of modules and libraries necessary to build the Module")
    val dependencies = value<Map<Modifiers, Dependencies>>()

    @ModifierAware
    @SchemaDoc("Configures the toolchains used in the build process")
    val settings = value<Map<Modifiers, Settings>>()

    @ModifierAware
    @SchemaDoc("The dependencies necessary to build and run tests of the Module")
    val `test-dependencies` = value<Map<Modifiers, Dependencies>>()

    @ModifierAware
    @SchemaDoc("Controls building and running the Module tests")
    val `test-settings` = value<Map<Modifiers, Settings>>()
}

class Template : Base()

class Module : Base() {
    @SchemaDoc("Defines what should be produced out of the Module")
    val product = value<ModuleProduct>()

    @SchemaDoc("Defines the names for the custom code sharing groups")
    val alias = nullableValue<Map<String, Set<Platform>>>()

    val apply = nullableValue<Collection<Path>>()
}

class Repositories : SchemaBase() {
    val repositories = value<Collection<Repository>>()
}

class Repository : SchemaBase() {
    val url = value<String>()
    val id = nullableValue<String>()
    val credentials = nullableValue<Credentials>()
    val publish = nullableValue(default = false)

    class Credentials : SchemaBase() {
        val file = value<Path>()
        val usernameKey = value<String>()
        val passwordKey = value<String>()
    }
}