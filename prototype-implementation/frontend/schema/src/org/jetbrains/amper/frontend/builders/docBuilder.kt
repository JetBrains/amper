/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.builders

import org.jetbrains.amper.frontend.api.Embedded
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.hasAnnotation

sealed interface DocBuilderCustom

data object EmbeddedCustom : DocBuilderCustom

class TestDocBuilder private constructor(
    private val currentRoot: KClass<*>
) : RecurringVisitor<DocBuilderCustom>(detectDocCustom) {

    companion object {
        val detectDocCustom: (KProperty<*>) -> DocBuilderCustom? = { prop ->
            if(prop.hasAnnotation<Embedded>()) EmbeddedCustom else null
        }

        fun buildDoc(root: KClass<*>): String {
            val builder = TestDocBuilder(root)
            visitSchema(root, builder, detectDocCustom)
            return builder.buildDoc()
        }
    }

    private val docEntries = mutableMapOf<String, MutableList<String>>()

    fun withNewRoot(newRoot: KClass<*>, block: (TestDocBuilder) -> Unit) {
        val builder = TestDocBuilder(newRoot)
        block(builder)
        builder.docEntries.forEach { k, v ->
            docEntries.compute(k) { _, old -> (old ?: mutableListOf()).apply { addAll(v) } }
        }
    }

    private fun addDocEntry(desc: String) = docEntries.compute(currentRoot.simpleName!!) { _, existing ->
        (existing ?: mutableListOf()).apply { add(desc) }
    }

    fun buildDoc() = buildString {
        docEntries.forEach { (className, desc) ->
            appendLine(className)
            desc.forEach { appendLine("  $it") }
            appendLine()
        }
    }

    override fun visitCommon(parent: KClass<*>, name: String, type: KType, default: Any?) {
        addDocEntry("field $name of type ${type.buildDescription()}")
    }

    override fun visitTyped(parent: KClass<*>, name: String, types: Collection<KClass<*>>) {
        addDocEntry("field $name of types ${types.joinToString { it.simpleName!! }}")
        types.forEach { root -> withNewRoot(root) { visitSchema(root, it, detectDocCustom) } }
    }

    override fun visitCollectionTyped(parent: KClass<*>, name: String, type: KType, types: Collection<KClass<*>>) {
        addDocEntry("collection field $name of types ${types.joinToString { it.simpleName!! }}")
        types.forEach { root -> withNewRoot(root) { visitSchema(root, it, detectDocCustom) } }
    }

    override fun visitMapTyped(
        parent: KClass<*>,
        name: String,
        type: KType,
        types: Collection<KClass<*>>,
        modifierAware: Boolean
    ) {
        if (modifierAware)
            addDocEntry("modifier aware field $name of types ${types.joinToString { it.simpleName!! }}")
        else
            addDocEntry("key based field $name of types ${types.joinToString { it.simpleName!! }}")
        types.forEach { root -> withNewRoot(root) { visitSchema(root, it, detectDocCustom) } }
    }

    override fun visitCustom(
        parent: KClass<*>,
        prop: KProperty<*>,
        custom: DocBuilderCustom
    ) {
        val schemaKClass = prop.unwrapSchemaTypeOrNull ?: return
        when (custom) {
            is EmbeddedCustom -> visitSchema(schemaKClass, this, detectDocCustom)
        }
    }

    private fun KType.buildDescription(): String = buildString {
        append("${unwrapKClass.simpleName}")
        if (arguments.isNotEmpty())
            append("<${arguments.joinToString { it.type?.buildDescription() ?: "" }}>")
    }
}