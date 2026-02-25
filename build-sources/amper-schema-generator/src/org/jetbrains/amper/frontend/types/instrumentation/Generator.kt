/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.types.instrumentation

import com.squareup.kotlinpoet.FileSpec
import org.jetbrains.amper.frontend.api.SchemaNode
import java.nio.file.Path
import kotlin.reflect.KClass

/**
 * Entry point into the schema generation process.
 */
internal class Generator(
    private val outputDirectory: Path,
) {
    private val parsed = mutableMapOf<KClass<*>, ParsedDeclaration>()

    fun ensureSchemaNodeParsed(klass: KClass<out SchemaNode>): ParsedDeclaration.SchemaNode {
        require(!klass.isSealed) { "Unexpected: class ${klass.simpleName} is sealed!" }
        return parsed.getOrPut(klass) {
            parseAndGenerateSchemaNode(klass)
        } as ParsedDeclaration.SchemaNode
    }

    fun ensureEnumParsed(klass: KClass<out Enum<*>>): ParsedDeclaration.Enum {
        return parsed.getOrPut(klass) {
            parseAndGenerateEnum(klass)
        } as ParsedDeclaration.Enum
    }

    fun ensureSealedSchemaNodeParsed(klass: KClass<out SchemaNode>): ParsedDeclaration.SealedSchemaNode {
        require(klass.isSealed) { "Unexpected: class ${klass.simpleName} is not sealed!" }
        return parsed.getOrPut(klass) {
            parseAndGenerateSealedNode(klass)
        } as ParsedDeclaration.SealedSchemaNode
    }

    fun writeFile(fileSpec: FileSpec) {
        fileSpec.writeTo(outputDirectory)
    }
}