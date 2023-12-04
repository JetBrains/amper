/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.builders

import org.jetbrains.amper.frontend.api.Embedded
import org.jetbrains.amper.frontend.api.SchemaDoc
import java.io.Writer
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

/**
 * Marker for special treated fields.
 */
sealed interface DocBuilderCustom

/**
 * Mark, that current property is "embedded" and should not be treated like self-sufficient
 * in terms of doc generating.
 */
data object EmbeddedDoc : DocBuilderCustom

/**
 * Builder that traverses schema to build a reference documentation.
 */
class DocBuilder private constructor(
    private val currentRoot: KClass<*>
) : RecurringVisitor<DocBuilderCustom>(detectCustom) {

    companion object {
        private val detectCustom: (KProperty<*>) -> DocBuilderCustom? = { prop ->
            if (prop.unwrapSchemaTypeOrNull?.hasAnnotation<Embedded>() == true) EmbeddedDoc else null
        }

        fun buildDoc(root: KClass<*>, w: Writer) = DocBuilder(root)
            .apply { visitClas(root) }
            .buildDoc(w)
    }

    /**
     * Visited classes.
     */
    private val visited = mutableSetOf<KClass<*>>()

    /**
     * Saved by-class properties documentation entries.
     */
    private val propertyDesc = mutableMapOf<String, MutableList<String>>()

    /**
     * Saved classes' documentation entries.
     */
    private val schemaNodesDesc = mutableMapOf<String, String>()

    /**
     * Create new builder with a new root and perform operation,
     * then merge docs.
     */
    private fun withNewRoot(newRoot: KClass<*>, block: (DocBuilder) -> Unit) {
        val builder = DocBuilder(newRoot)
        block(builder)
        builder.propertyDesc.forEach { (k, v) ->
            propertyDesc.compute(k) { _, old -> (old ?: mutableListOf()).apply { addAll(v) } }
        }
        schemaNodesDesc.putAll(builder.schemaNodesDesc)
    }

    /**
     * Build whole doc.
     */
    fun buildDoc(w: Writer) = with(w) {
        schemaNodesDesc.keys.forEach { fqn ->
            val nodeDesc = schemaNodesDesc[fqn] ?: return@forEach
            appendLine(nodeDesc)
            appendLine("-".repeat(nodeDesc.length))
            propertyDesc[fqn]?.forEach {
                appendLine("\t$it")
            }
            appendLine()
        }
    }

    override fun visitClas(klass: KClass<*>) = if (visited.add(klass)) {
        withNewRoot(klass) {
            addNodeDesc(klass)
            visitSchema(klass, it, detectCustom)
        }
    } else Unit

    override fun visitCommon(prop: KProperty<*>, type: KType, default: Any?) {
        addPropDesc(prop, type, default = default)
    }

    override fun visitTyped(prop: KProperty<*>, type: KType, types: Collection<KClass<*>>) {
        addPropDesc(prop, type, types)
        super.visitTyped(prop, type, types)
    }

    override fun visitCollectionTyped(prop: KProperty<*>, type: KType, types: Collection<KClass<*>>) {
        addPropDesc(prop, type, types)
        super.visitCollectionTyped(prop, type, types)
    }

    override fun visitMapTyped(
        prop: KProperty<*>,
        type: KType,
        types: Collection<KClass<*>>,
        modifierAware: Boolean
    ) {
        addPropDesc(prop, type, types, isModifierAware = modifierAware)
        super.visitMapTyped(prop, type, types, modifierAware)
    }

    override fun visitCustom(prop: KProperty<*>, custom: DocBuilderCustom) {
        val schemaKClass = prop.unwrapSchemaTypeOrNull ?: return
        when (custom) {
            // Skip root switching, so entries will be added to current root.
            is EmbeddedDoc -> visitSchema(schemaKClass, this, detectCustom)
        }
    }

    private fun addNodeDesc(
        klass: KClass<*>,
    ) {
        val desc = buildString {
            append("${klass.simpleName}")

            val doc = klass.findAnnotation<SchemaDoc>()
            if (doc != null) append(": ${doc.doc}")
        }

        schemaNodesDesc[klass.qualifiedName!!] = desc
    }

    private fun addPropDesc(
        prop: KProperty<*>,
        type: KType,
        subtypes: Collection<KClass<*>>? = null,
        default: Any? = null,
        isModifierAware: Boolean = false,
    ) {
        val desc = buildString {
            val doc = prop.findAnnotation<SchemaDoc>()

            // property
            append(prop.name)

            // property[@<modifier>]
            if (isModifierAware) append("[@modifier]")

            // property[@<modifier>]:
            append(": ")

            // property[@<modifier>]: Type
            if (isModifierAware) append(type.mapValueType.simpleView)
            else append(type.simpleView)

            // property[@<modifier>]: Type, default: value
            if (default != null) append(", default: $default")

            // property[@<modifier>]: Type, default: value - some desc
            if (doc != null) append(" - ${doc.doc}")
            else if (subtypes?.singleOrNull() == type.unwrapKClassOrNull) Unit
            else if (subtypes != null) append(" - See: ${subtypes.joinToString { it.simpleName!! }}")
        }

        propertyDesc.compute(currentRoot.qualifiedName!!) { _, existing ->
            (existing ?: mutableListOf()).apply { add(desc) }
        }
    }

    private val KType.simpleView: String
        get() = buildString {
            append("${unwrapKClass.simpleName}")
            if (arguments.isNotEmpty())
                append("<${arguments.joinToString { it.type?.simpleView ?: "" }}>")
        }
}