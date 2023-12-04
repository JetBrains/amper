/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.builders

import org.jetbrains.amper.frontend.api.Embedded
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.hasAnnotation


/**
 * Marker for special treated fields.
 */
sealed interface SchemaBuilderCustom

/**
 * Mark, that current property is "embedded" and should not be treated like self-sufficient
 * in terms of doc generating.
 */
data object EmbeddedSchema : SchemaBuilderCustom

/**
 * The place where schema is actually built.
 */
class SchemaBuilderCtx {
    val declaredDefs: MutableMap<String, MutableList<String>> = mutableMapOf()
}

/**
 * A visitor, that traverses the tree and put all schema info into [SchemaBuilderCtx].
 */
class SchemaBuilder(
    private val currentRoot: KClass<*>,
    private val ctx: SchemaBuilderCtx,
) : RecurringVisitor<SchemaBuilderCustom>(detectCustom) {
    companion object {
        private val detectCustom: (KProperty<*>) -> SchemaBuilderCustom? = { prop ->
            if (prop.unwrapSchemaTypeOrNull?.hasAnnotation<Embedded>() == true) EmbeddedSchema else null
        }

        fun writeSchema(root: KClass<*>) = SchemaBuilder(root, SchemaBuilderCtx())
            .apply { visitClas(root) }
            .ctx.run {
                buildString {
                    appendLine("{")
                    declaredDefs.forEach { (key, values) ->
                        appendLine("\"$key\": {")
                        append("\"type\": \"object\",")
                        values.forEachEndAware { isEnd, it ->
                            appendLine(it)
                            if (!isEnd) append(",")
                        }
                        appendLine("},")
                    }
                    appendLine("}")
                }
            }
    }

    /**
     * Create new builder with a new root and perform operation.
     */
    private fun withNewRoot(newRoot: KClass<*>, block: (SchemaBuilder) -> Unit) {
        val builder = SchemaBuilder(newRoot, ctx)
        block(builder)
    }

    private fun addProperty(prop: KProperty<*>, block: () -> String) {
        ctx.declaredDefs.compute(currentRoot.jsonDef) { _, old ->
            old.orNew.apply {
                add("""
    "${prop.name}": {
        ${block()}
    }
""".trimIndent())
            }
        }
    }

    override fun visitClas(klass: KClass<*>) = withNewRoot(klass) {
        visitSchema(klass, it, detectCustom)
    }

    override fun visitTyped(prop: KProperty<*>, type: KType, types: Collection<KClass<*>>) {
        addProperty(prop) { types.wrapInAnyOf { it.asReferenceTo } }
        super.visitTyped(prop, type, types)
    }

    override fun visitCollectionTyped(prop: KProperty<*>, type: KType, types: Collection<KClass<*>>) {
        addProperty(prop) { buildSchemaCollection { types.wrapInAnyOf { it.asReferenceTo } } }
        super.visitCollectionTyped(prop, type, types)
    }

    override fun visitMapTyped(prop: KProperty<*>, type: KType, types: Collection<KClass<*>>, modifierAware: Boolean) {
        addProperty(prop) { buildSchemaCollection { types.wrapInAnyOf { it.asReferenceTo } } }
        super.visitMapTyped(prop, type, types, modifierAware)
    }

    override fun visitCommon(prop: KProperty<*>, type: KType, default: Any?) =
        addProperty(prop) { buildForScalarBased(type) }

    override fun visitCustom(prop: KProperty<*>, custom: SchemaBuilderCustom) {
        val schemaKClass = prop.unwrapSchemaTypeOrNull ?: return
        when (custom) {
            // Skip root switching, so entries will be added to current root.
            is EmbeddedSchema -> visitSchema(schemaKClass, this, detectCustom)
        }
    }

    private fun buildForScalarBased(type: KType): String = when {
        type.isScalar -> buildScalar(type)
        type.isCollection -> buildSchemaCollection { buildForScalarBased(type.collectionType) }
        type.isMap -> buildSchemaKeyBasedCollection { buildForScalarBased(type.mapValueType) }
        else -> error("Unsupported type $type") // TODO Report
    }

    private fun buildScalar(type: KType) = when {
        type.isEnum -> type.enumSchema
        type.isString -> stringSchema
        type.isBoolean -> booleanSchema
        type.isPath -> stringSchema
        type.isInt -> TODO()
        else -> error("Unsupported type") // TODO reporting
    }

    private val MutableList<String>?.orNew get() = this ?: mutableListOf()
}