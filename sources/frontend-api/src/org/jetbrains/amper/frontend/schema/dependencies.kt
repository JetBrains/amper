/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.DependencyKey
import org.jetbrains.amper.frontend.api.EnumOrderSensitive
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Shorthand
import java.nio.file.Path

@EnumOrderSensitive
enum class DependencyScope(
    override val schemaValue: String,
    val runtime: Boolean,
    val compile: Boolean,
    override val outdated: Boolean = false,
) : SchemaEnum {
    COMPILE_ONLY("compile-only", runtime = false, compile = true),
    RUNTIME_ONLY("runtime-only", runtime = true, compile = false),
    ALL("all", runtime = true, compile = true);
    companion object : EnumMap<DependencyScope, String>(DependencyScope::values, DependencyScope::schemaValue)
}

// TODO Break this hierarchy into two:
//  - DependencyNotation: MavenNotation, CatalogNotation, LocalNotation (in future replaced as just reference)
//  - Dependency: ScopedDependency, BomDependency (if we need any special meaning here for Bom).
//  .
//  Also, by breaking this hierarchy we can replace KspDependencies by just notation.
//  Also, it may contradict "constructor args" approach from AmperLang.
sealed class Dependency : SchemaNode()

sealed class ScopedDependency : Dependency() {

    // TODO Replace exported flag by new scope (rethink scopes).
    @Shorthand
    @SchemaDoc("Whether a dependency should be [visible as a part of a published API](#scopes-and-visibility)")
    var exported by value(false)
    
    @Shorthand
    @SchemaDoc("When the dependency should be used. Read more about the [dependency scopes](#scopes-and-visibility)")
    var scope by value(DependencyScope.ALL)
}

class ExternalMavenDependency : ScopedDependency() {

    @SchemaDoc("Dependency on [a Kotlin or Java library](#external-maven-dependencies) in a Maven repository")
    @DependencyKey
    var coordinates by value<String>()
}

class InternalDependency : ScopedDependency() {

    @SchemaDoc("Dependency [on another module](#module-dependencies) in the codebase")
    @DependencyKey
    var path by value<Path>()
}

class CatalogDependency : ScopedDependency() {

    @SchemaDoc("Dependency from [a dependency catalog](#dependencyversion-catalogs)")
    @DependencyKey
    var catalogKey by value<String>()
}

sealed class BomDependency : Dependency()

class ExternalMavenBomDependency : BomDependency() {

    @SchemaDoc("Dependency on [a BOM](#external-maven-dependencies) in a Maven repository")
    @DependencyKey
    var coordinates by value<String>()
}

class CatalogBomDependency : BomDependency() {

    @SchemaDoc("BOM dependency from [a dependency catalog](#dependencyversion-catalogs)")
    @DependencyKey
    var catalogKey by value<String>()
}

