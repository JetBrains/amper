/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.types.instrumentation

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import org.jetbrains.amper.frontend.api.CustomSchemaDeclaration
import org.jetbrains.amper.frontend.api.KnownIntValues
import org.jetbrains.amper.frontend.api.KnownStringValues
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.StringSemantics
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.SchemaTypeDeclaration
import java.nio.file.Path
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation

/**
 * Instrumentation-time mirror for [org.jetbrains.amper.frontend.tree.TypeDescriptor] hierarchy.
 */
internal sealed interface ParsedTypeDescriptor {
    data class Object(
        val declaration: ClassName,
    ) : ParsedTypeDescriptor

    data class Variant(
        /**
         * Name of the [SchemaNode] class.
         */
        val schemaNodeName: ClassName,
        /**
         * Name of the generated declaration class/object.
         */
        val declaration: ClassName,
    ) : ParsedTypeDescriptor

    data class List(
        val element: ParsedTypeDescriptor,
    ) : ParsedTypeDescriptor

    data class Map(
        val element: ParsedTypeDescriptor,
    ) : ParsedTypeDescriptor

    data class Enum(
        /**
         * Name of the [Enum] class.
         */
        val enumName: ClassName,
        /**
         * Name of the generated declaration object.
         */
        val declaration: ClassName,
    ) : ParsedTypeDescriptor

    data object Path : ParsedTypeDescriptor
    data object String : ParsedTypeDescriptor
    data object Boolean : ParsedTypeDescriptor
    data object Int : ParsedTypeDescriptor

    /**
     * A special descriptor that is outputted when a @[CustomSchemaDeclaration]-annotated type is encountered.
     */
    data object CustomObject : ParsedTypeDescriptor
}

/**
 * A parsed schema type, containing various useful information pieces about the type.
 */
internal class ParsedType(
    /**
     * An expression that creates a required [SchemaType] instance.
     */
    val schemaTypeCreationExpression: CodeBlock,
    /**
     * A schema-type descriptor.
     */
    val descriptor: ParsedTypeDescriptor,
    /**
     * All the external declarations that [schemaTypeCreationExpression] refrences.
     * The embedding code must ensure that these arguments are available in the scope.
     *
     * @see ParsedDeclaration.SchemaNode.declarationParameters
     */
    val declarationArguments: List<ParsedDeclaration.SchemaNode.Parameter> = emptyList(),
)

/**
 * Parses the given [type], providing the [ParsedType] object.
 */
context(generator: Generator)
internal fun schemaTypeExpression(
    type: KType,
    /**
     * The annotated construction that can have annotations that would influence how the type is interpreted, e.g.
     * [KnownStringValues], [StringSemantics], [KnownIntValues], etc.
     */
    annotated: KAnnotatedElement? = null,
): ParsedType = when (val classifier = type.classifier) {
    // TODO: Use pre-created type instances where possible, like `SchemaType.Boolean` or `SchemaType.StringNullable`
    Int::class -> {
        val knownValues = annotated?.findAnnotation<KnownIntValues>()?.values
        val expression = CodeBlock.builder().apply {
            add("%T(%L", SchemaType.IntType::class, type.isMarkedNullable)
            if (knownValues != null) {
                add(", knownValues = setOf(")
                knownValues.forEach { add("%L, ", it) }
                add(")")
            }
            add(")")
        }.build()
        ParsedType(
            schemaTypeCreationExpression = expression,
            descriptor = ParsedTypeDescriptor.Int,
        )
    }
    Boolean::class -> ParsedType(
        schemaTypeCreationExpression = CodeBlock.of("%T(%L)", SchemaType.BooleanType::class, type.isMarkedNullable),
        descriptor = ParsedTypeDescriptor.Boolean,
    )
    String::class, TraceableString::class -> {
        val knownValues = annotated?.findAnnotation<KnownStringValues>()?.values
        val semantics = annotated?.findAnnotation<StringSemantics>()?.value
        val expression = CodeBlock.builder().apply {
            add("%T(%L", SchemaType.StringType::class, type.isMarkedNullable)
            if (classifier == TraceableString::class) {
                add(", true")
            }
            if (knownValues != null) {
                add(", knownValues = setOf(")
                knownValues.forEach { add("%S, ", it) }
                add(")")
            }
            if (semantics != null) {
                add(", semantics = %T.%N", SchemaType.StringType.Semantics::class, semantics.name)
            }
            add(")")
        }.build()
        ParsedType(schemaTypeCreationExpression = expression, descriptor = ParsedTypeDescriptor.String)
    }
    Path::class -> ParsedType(
        schemaTypeCreationExpression = CodeBlock.of("%T(%L)", SchemaType.PathType::class, type.isMarkedNullable),
        descriptor = ParsedTypeDescriptor.Path,
    )
    TraceablePath::class -> ParsedType(
        schemaTypeCreationExpression = CodeBlock.of("%T(%L, true)", SchemaType.PathType::class, type.isMarkedNullable),
        descriptor = ParsedTypeDescriptor.Path,
    )
    Map::class -> {
        val valueType = schemaTypeExpression(checkNotNull(type.arguments[1].type))
        val keyType = schemaTypeExpression(checkNotNull(type.arguments[0].type))
        val expression = CodeBlock.of(
            "%T(%L, %L, %L)", SchemaType.MapType::class,
            valueType.schemaTypeCreationExpression,
            keyType.schemaTypeCreationExpression,
            type.isMarkedNullable,
        )
        check(keyType.declarationArguments.isEmpty()) { "Parameterized map keys are not possible" }
        ParsedType(
            schemaTypeCreationExpression = expression,
            descriptor = ParsedTypeDescriptor.Map(valueType.descriptor),
            declarationArguments = valueType.declarationArguments,
        )
    }
    List::class -> {
        val elementType =
            schemaTypeExpression(checkNotNull(type.arguments[0].type))
        ParsedType(
            schemaTypeCreationExpression = CodeBlock.of(
                "%T(%L, %L)",
                SchemaType.ListType::class,
                elementType.schemaTypeCreationExpression,
                type.isMarkedNullable
            ),
            descriptor = ParsedTypeDescriptor.List(elementType.descriptor),
            declarationArguments = elementType.declarationArguments,
        )
    }
    TraceableEnum::class -> {
        val enumClass = checkNotNull(type.arguments[0].type).classifier as KClass<*>
        check(enumClass.isSubclassOf<Enum<*>>())
        val parsed = generator.ensureEnumParsed(enumClass)
        ParsedType(
            schemaTypeCreationExpression = CodeBlock.of(
                "%T(%L, true, %L)",
                SchemaType.EnumType::class,
                declarationAccessExpression(parsed),
                type.isMarkedNullable
            ),
            descriptor = ParsedTypeDescriptor.Enum(enumClass.asClassName(), parsed.declarationName),
        )
    }
    is KClass<*> -> when {
        classifier.isSubclassOf<Enum<*>>() -> {
            val parsed = generator.ensureEnumParsed(classifier)
            ParsedType(
                schemaTypeCreationExpression = CodeBlock.of(
                    "%T(%L, false, %L)",
                    SchemaType.EnumType::class,
                    declarationAccessExpression(parsed),
                    type.isMarkedNullable
                ),
                descriptor = ParsedTypeDescriptor.Enum(classifier.asClassName(), parsed.declarationName),
            )
        }
        classifier.isSubclassOf<SchemaNode>() -> if (classifier.isSealed) {
            val parsed = generator.ensureSealedSchemaNodeParsed(classifier)
            ParsedType(
                schemaTypeCreationExpression = CodeBlock.of(
                    "%T(%L, %L)",
                    SchemaType.VariantType::class,
                    declarationAccessExpression(parsed),
                    type.isMarkedNullable
                ),
                descriptor = ParsedTypeDescriptor.Variant(classifier.asClassName(), parsed.declarationName),
            )
        } else {
            val customDeclaration = classifier.findAnnotation<CustomSchemaDeclaration>()
            if (customDeclaration != null) {
                // First, we need to ensure that all referenced types have their
                // type definitions generated.
                customDeclaration.requiredReferences.forEach { parseAndGenerateSchemaNode(it) }
                
                val parameter = ParsedDeclaration.SchemaNode.Parameter(
                    schemaNodeClass = classifier,
                    parameterType = SchemaTypeDeclaration::class.asTypeName(),
                )
                ParsedType(
                    schemaTypeCreationExpression = CodeBlock.of(
                        "%N.toType(isMarkedNullable = %L)",
                        parameter.parameterName,
                        type.isMarkedNullable
                    ),
                    descriptor = ParsedTypeDescriptor.CustomObject,
                    declarationArguments = listOf(parameter),
                )
            } else {
                val parsed = parseAndGenerateSchemaNode(classifier)
                if (parsed.declarationParameters.isNotEmpty()) {
                    val parameter = ParsedDeclaration.SchemaNode.Parameter(
                        schemaNodeClass = classifier,
                        parameterType = parsed.declarationName,
                    )
                    ParsedType(
                        schemaTypeCreationExpression = CodeBlock.of(
                            "%N.toType(isMarkedNullable = %L)",
                            parameter.parameterName,
                            type.isMarkedNullable
                        ),
                        descriptor = ParsedTypeDescriptor.Object(parsed.declarationName),
                        declarationArguments = listOf(parameter)
                    )
                } else {
                    ParsedType(
                        schemaTypeCreationExpression = CodeBlock.of(
                            "%T(%L, %L)",
                            SchemaType.ObjectType::class,
                            declarationAccessExpression(parsed),
                            type.isMarkedNullable,
                        ),
                        descriptor = ParsedTypeDescriptor.Object(parsed.declarationName),
                    )
                }
            }
        }
        else -> error("Unsupported class: $classifier")
    }
    else -> error("Unsupported type: $type")
}