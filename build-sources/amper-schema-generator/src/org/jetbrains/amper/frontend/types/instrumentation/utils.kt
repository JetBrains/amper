/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.types.instrumentation

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.SchemaValueDelegate
import org.jetbrains.amper.frontend.api.TraceableValue
import java.io.File
import java.nio.file.Path
import kotlin.contracts.contract
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.isAccessible

internal fun FileSpec.Builder.addGeneratedComment() = addFileComment(
        """
        ------------------ GENERATED ---------------------
        This file is produced by `amper-schema-generator`.
        --------------------------------------------------
        """.trimIndent()
    )

internal fun FileSpec.Builder.addSuppressRedundantVisibilityModifier() = addAnnotation(
    AnnotationSpec.builder(Suppress::class)
        .addMember("%S", "REDUNDANT_VISIBILITY_MODIFIER")
        .build()
)

/**
 * Generates a code block that accesses the declaration of the given [declaration] declaration.
 *
 * If the declaration is [parameterized][ParsedDeclaration.SchemaNode.declarationParameters], then all the arguments
 * must be available in the surrounding scope.
 */
internal fun declarationAccessExpression(declaration: ParsedDeclaration) = CodeBlock.builder().apply {
    add("%T", declaration.declarationName)
    if (declaration is ParsedDeclaration.SchemaNode && declaration.declarationParameters.isNotEmpty()) {
        add("(")
        declaration.declarationParameters.forEach {
            add("%N,", it.parameterName)
        }
        add(")")
    }
}.build()

/**
 * Generates a creating expression for the [org.jetbrains.amper.frontend.api.Default.Static.value].
 */
internal fun defaultToCode(default: Any?): CodeBlock = when (default) {
    null -> CodeBlock.of("null")
    is Boolean, is Int -> CodeBlock.of("%L", default)
    is String -> CodeBlock.of("%S", default)
    is Path, is File -> CodeBlock.of("%M(%S)", PathConstructor, default.toString())
    is TraceableValue<*> -> defaultToCode(default.value)
    is Enum<*> -> CodeBlock.of("%T.%N", default::class, default.name)
    is List<*> -> CodeBlock.builder().apply {
        if (default.isEmpty()) {
            add("emptyList<Nothing>()")
        } else {
            add("listOf(")
            default.forEach { add("%L, ", defaultToCode(it)) }
            add(")")
        }
    }.build()
    is Map<*, *> -> CodeBlock.of("emptyMap<Nothing, Nothing>()").also {
        check(default.isEmpty()) { "Only empty map is supported as a default value" }
    }
    else -> error("Unexpected default value: $default")
}

context(generator: Generator)
internal fun ensureParsed(klass: KClass<*>): ParsedDeclaration = when {
    klass.isSubclassOf<Enum<*>>() -> generator.ensureEnumParsed(klass)
    klass.isSubclassOf<SchemaNode>() -> if (klass.isSealed) {
        generator.ensureSealedSchemaNodeParsed(klass)
    } else {
        generator.ensureSchemaNodeParsed(klass)
    }
    else -> error("Unexpected class $klass. Expected a SchemaNode or an Enum")
}

@Suppress("KotlinConstantConditions")
internal inline fun <reified T : Any> KClass<*>.isSubclassOf(): Boolean {
    contract { returns(true) implies (this@isSubclassOf is KClass<out T>) }
    return isSubclassOf(T::class)
}

internal fun KClass<out SchemaNode>.deepSealedSubclasses(): List<KClass<out SchemaNode>> =
    if (isSealed) sealedSubclasses.fold(emptyList()) { a, c -> a + c.deepSealedSubclasses() } else listOf(this)

internal fun <T : SchemaNode> KProperty1<T, *>.schemaDelegate(receiver: T): SchemaValueDelegate<*> =
    apply { isAccessible = true }.getDelegate(receiver)?.let {
        it as SchemaValueDelegate<*>
    } ?: error("Property $this should have a schema delegate")