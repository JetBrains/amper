/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.CustomSchemaDef
import org.jetbrains.amper.frontend.api.ImplicitConstructor
import org.jetbrains.amper.frontend.api.ImplicitConstructorParameter
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import java.nio.file.Path

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

@ImplicitConstructor(ExternalMavenDependency::class)
sealed class Dependency : SchemaNode() {

    // TODO Replace exported flag by new scope (rethink scopes).
    @SchemaDoc("Whether a dependency should be [visible as a part of a published API](#scopes-and-visibility)")
    var exported by value(false)

    @SchemaDoc("When the dependency should be used. Read more about the [dependency scopes](#scopes-and-visibility)")
    var scope by value(DependencyScope.ALL)
}

@CustomSchemaDef(dependencySchema)
class ExternalMavenDependency : Dependency() {

    @ImplicitConstructorParameter
    @SchemaDoc("Dependency on [a Kotlin or Java library](#external-maven-dependencies) in a Maven repository")
    var coordinates by value<String>()
}

@CustomSchemaDef(dependencySchema)
class InternalDependency  : Dependency() {

    @SchemaDoc("Dependency [on another module](#module-dependencies) in the codebase")
    var path by nullableValue<Path>()
}

@CustomSchemaDef(dependencySchema)
class CatalogDependency  : Dependency() {

    @SchemaDoc("Dependency from [a dependency catalog](#dependencyversion-catalogs)")
    var catalogKey by value<String>()
}

const val dependencySchema = """
  "anyOf": [
    {
      "type": "string"
    },
    {
      "type": "object",
      "patternProperties": {
        "^.*$": {
          "anyOf": [
            {
              "enum": [
                "exported",
                "compile-only",
                "runtime-only"
              ]
            },
            {
              "type": "object",
              "properties": {
                "scope": {
                  "enum": [
                    "all",
                    "compile-only",
                    "runtime-only"
                  ]
                },
                "exported": {
                  "type": "boolean"
                }
              }
            }
          ]
        }
      },
      "additionalProperties": false
    }
  ]
"""