/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.CustomSchemaDef
import org.jetbrains.amper.frontend.api.SchemaNode
import java.nio.file.Path

enum class DependencyScope(
    override val schemaValue: String,
    val runtime: Boolean,
    val compile: Boolean,
) : SchemaEnum {
    COMPILE_ONLY("compile-only", false, true),
    RUNTIME_ONLY("runtime-only", true, false),
    ALL("all", true, true),;
    companion object : EnumMap<DependencyScope, String>(DependencyScope::values, DependencyScope::schemaValue)
}

sealed class Dependency : SchemaNode() {
    // TODO Replace exported flag by new scope (rethink scopes).
    var exported by value<Boolean>().default(false)
    var scope by value<DependencyScope>().default(DependencyScope.ALL)
}

@CustomSchemaDef(dependencySchema)
class ExternalMavenDependency : Dependency() {
    var coordinates by value<String>()
}

@CustomSchemaDef(dependencySchema)
class InternalDependency  : Dependency() {
    var path by nullableValue<Path>()
}

@CustomSchemaDef(dependencySchema)
class CatalogDependency  : Dependency() {
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