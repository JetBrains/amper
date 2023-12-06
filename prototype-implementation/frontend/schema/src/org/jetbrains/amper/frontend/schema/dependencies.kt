/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.api.CustomSchemaDef
import org.jetbrains.amper.frontend.api.SchemaNode
import java.nio.file.Path

// TODO Add scopes.
sealed class Dependency : SchemaNode()

@CustomSchemaDef(dependencySchema)
class ExternalMavenDependency : Dependency() {
    val coordinates = value<String>()
}

@CustomSchemaDef(dependencySchema)
class InternalDependency  : Dependency() {
    val path = value<Path>()
}

// A way to handle $ escaping.
const val ref = "\$ref"
const val defs = "\$defs"

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