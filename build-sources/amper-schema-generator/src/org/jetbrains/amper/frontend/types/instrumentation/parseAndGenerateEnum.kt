/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.types.instrumentation

import com.squareup.kotlinpoet.BOOLEAN
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
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.EnumOrderSensitive
import org.jetbrains.amper.frontend.api.EnumValueFilter
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.types.BuiltinSchemaEnumDeclarationBase
import org.jetbrains.amper.frontend.types.SchemaEnumDeclaration
import java.lang.reflect.Field
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Parses an enum class (must implement [SchemaEnum]), outputs [BuiltinSchemaEnumDeclarationBase] implementation.
 */
context(generator: Generator)
internal fun <E : Enum<E>> parseAndGenerateEnum(clazz: KClass<E>): ParsedDeclaration.Enum = run {
    val name = ClassName(
        packageName = TARGET_PACKAGE,
        clazz.asClassName().simpleNames.joinToString("_", prefix = "DeclarationOfEnum"),
    )
    val order = clazz.findAnnotation<EnumOrderSensitive>()
    val spec = TypeSpec.objectBuilder(name)
        .superclass(BuiltinSchemaEnumDeclarationBase::class.asClassName().parameterizedBy(clazz.asTypeName()))
        .addKdoc("Declaration for [%T]", clazz)
        .addProperty(
            PropertySpec.builder(
                "entries", LIST.parameterizedBy(SchemaEnumDeclaration.EnumEntry::class.asTypeName()),
                KModifier.OVERRIDE
            ).initializer(
                CodeBlock.builder().apply {
                    val annotationsByEntryName: Map<String, Field> = clazz.java.fields
                        .filter { it.type.isEnum }.associateBy { it.name }
                    val filter = clazz.findAnnotation<EnumValueFilter>()?.let { valueFilter ->
                        val filterProperty = clazz.memberProperties
                            .first { it.name == valueFilter.filterPropertyName }
                        filterProperty to !valueFilter.isNegated
                    }
                    add("listOf(⇥\n")

                    clazz.java.enumConstants.let {
                        if (order?.reverse == true) it.reversedArray() else it
                    }.forEach { entry: E ->
                        val schemaEnum = entry as? SchemaEnum
                        add("%T(⇥\n", SchemaEnumDeclaration.EnumEntry::class)
                        add("name = %S,\n", entry.name)
                        add("schemaValue = %S,\n", schemaEnum?.schemaValue ?: entry.name)
                        if (schemaEnum?.outdated == true) {
                            add("isOutdated = true,\n")
                        }
                        if (filter?.first?.get(entry) != filter?.second) {
                            add("isIncludedIntoJsonSchema = false,\n")
                        }
                        annotationsByEntryName[entry.name]?.getDeclaredAnnotation(SchemaDoc::class.java)?.let {
                            add("documentation = %S,\n", it.doc)
                        }
                        add("⇤),\n")
                    }
                    add("⇤)")
                }.build()
            ).build()
        )
        .addProperty(
            PropertySpec.builder("isOrderSensitive", BOOLEAN, KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder().addCode("return %L", order != null).build())
                .build()
        )
        .addProperty(
            PropertySpec.builder("qualifiedName", STRING, KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder().addCode("return %S", clazz.qualifiedName!!).build())
                .build()
        )
        .addFunction(
            FunSpec.builder("toEnumConstant")
                .addModifiers(KModifier.OVERRIDE)
                .returns(clazz)
                .addParameter("name", STRING)
                .addCode("return %T.valueOf(name)", clazz)
                .build()
        )
        .build()

    generator.writeFile(
        FileSpec.builder(name)
            .addGeneratedComment()
            .addSuppressRedundantVisibilityModifier()
            .addType(spec)
            .build()
    )

    ParsedDeclaration.Enum(
        name = clazz.asClassName(),
        declarationName = name,
    )
}