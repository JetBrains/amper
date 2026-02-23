/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.types.instrumentation

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.withIndent
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.types.BuiltinVariantDeclarationBase
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaVariantDeclaration
import org.jetbrains.amper.frontend.types.SchemaVariantDeclaration.Variant.LeafVariant
import org.jetbrains.amper.frontend.types.SchemaVariantDeclaration.Variant.SubVariant
import kotlin.reflect.KClass

/**
 * Parses a sealed [SchemaNode], outputs [BuiltinVariantDeclarationBase] implementation.
 */
context(generator: Generator)
internal fun parseAndGenerateSealedNode(clazz: KClass<out SchemaNode>): ParsedDeclaration.SealedSchemaNode {
    val name = ClassName(
        packageName = TARGET_PACKAGE,
        clazz.asClassName().simpleNames.joinToString("_", prefix = "DeclarationOfVariant"),
    )
    val spec = TypeSpec.objectBuilder(name)
        .superclass(BuiltinVariantDeclarationBase::class.asClassName().parameterizedBy(clazz.asTypeName()))
        .addKdoc("Declaration for [%T]\n", clazz)
        .addProperty(
            PropertySpec.builder("qualifiedName", STRING, KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder().addCode("return %S", clazz.qualifiedName!!).build())
                .build()
        )
        .addProperty(
            PropertySpec.builder(
                "variantTree", LIST.parameterizedBy(SchemaVariantDeclaration.Variant::class.asTypeName()),
                KModifier.OVERRIDE,
            ).initializer(
                CodeBlock.builder().apply {
                    add("listOf(\n")
                    withIndent {
                        clazz.sealedSubclasses.forEach { sealedSubclass ->
                            val (typeName, parsed) = if (sealedSubclass.isSealed) {
                                SubVariant::class to generator.ensureSealedSchemaNodeParsed(sealedSubclass)
                            } else {
                                LeafVariant::class to generator.ensureSchemaNodeParsed(sealedSubclass).also {
                                    check(it.declarationParameters.isEmpty()) {
                                        "Variant declaration can't yet be parameterized. What is your case?"
                                    }
                                }
                            }
                            add("%T(%L),\n", typeName, declarationAccessExpression(parsed))
                        }
                    }
                    add(")")
                }.build()
            ).build()
        )
        .addProperty(
            PropertySpec.builder(
                "variants", LIST.parameterizedBy(SchemaObjectDeclaration::class.asTypeName()),
                KModifier.OVERRIDE,
            ).initializer(
                CodeBlock.builder().apply {
                    add("listOf(\n")
                    withIndent {
                        clazz.deepSealedSubclasses().forEach {
                            val parsed = parseAndGenerateSchemaNode(it)
                            check(parsed.declarationParameters.isEmpty()) {
                                "Variant declaration can't yet be parameterized. What is your case?"
                            }
                            add("%L,\n", declarationAccessExpression(parsed))
                        }
                    }
                    add(")")
                }.build()
            ).build()
        )
        .build()

    generator.writeFile(
        FileSpec.builder(name)
            .addGeneratedComment()
            .addSuppressRedundantVisibilityModifier()
            .addType(spec)
            .build()
    )

    return ParsedDeclaration.SealedSchemaNode(
        name = clazz.asClassName(),
        declarationName = name,
    )
}