/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.builders

import org.jetbrains.amper.core.forEachEndAware
import org.jetbrains.amper.frontend.api.CustomSchemaDef
import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.api.KnownStringValues
import org.jetbrains.amper.frontend.api.Shorthand
import java.io.Writer
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.typeOf


private const val enabledSettingsShortForm = """
  {
    "enum": [
      "enabled"
    ]
  }
"""

/**
 * The place where schema is actually built.
 */
class JsonSchemaBuilderCtx {
    val visited = mutableSetOf<KClass<*>>()
    val customSchemaDef = mutableMapOf<String, String>()
    val declaredPatternProperties: MutableMap<String, MutableList<String>> = mutableMapOf()
    val declaredProperties: MutableMap<String, MutableMap<String, PropertyInfo>> = mutableMapOf()
}

class PropertyInfo(
    val fullJsonDef: String,
    val bodyDef: String,
    val shorthand: Boolean = false
)

/**
 * A visitor, that traverses the tree and put all schema info into [JsonSchemaBuilderCtx].
 */
class JsonSchemaBuilder(
    private val currentRoot: KClass<*>,
    private val ctx: JsonSchemaBuilderCtx,
) : RecurringVisitor() {
    companion object {
        fun writeSchema(
            root: KClass<*>,
            w: Writer,
            schemaId: String = "${root.simpleName}.json",
            title: String = "${root.simpleName} schema",
        ) = JsonSchemaBuilder(root, JsonSchemaBuilderCtx())
            .apply { visitClas(root) }
            .ctx.run {
                w.apply {
                    appendLine("{")
                    appendLine("  \"\$schema\": \"https://json-schema.org/draft/2020-12/schema\",")
                    appendLine("  \"\$id\": \"$schemaId\",")
                    appendLine("  \"title\": \"$title\",")
                    appendLine("  \"type\": \"object\",")

                    appendLine("  \"allOf\": [")
                    appendLine("    {")
                    appendLine("      ${root.asReferenceTo}")
                    appendLine("    }")
                    appendLine("  ],")

                    appendLine("  \"\$defs\": {")


                    visited.forEachEndAware { isEnd, it ->
                        val key = it.jsonDef
                        val propertyInfos = declaredProperties[key]
                        val patternProperties = declaredPatternProperties[key]
                        appendLine("    \"$key\": {")

                        val customSchema = customSchemaDef[key]
                        if (customSchema != null) {
                            appendLine(customSchema.prependIndent("      "))
                        } else {
                            val enabledShorthandSchema = enabledValueSchema(propertyInfos)
                            val extraShorthandSchema = shorthandValueSchema(propertyInfos)
                            if (enabledShorthandSchema != null || extraShorthandSchema != null) {
                                appendLine("      \"anyOf\": [")
                                appendLine("        {")
                            }
                            val identPrefix = (enabledShorthandSchema ?: extraShorthandSchema)?.let { "    " } ?: ""
                            appendLine("$identPrefix      \"type\": \"object\",")

                            // pattern properties section.
                            if (patternProperties != null) {
                                appendLine("$identPrefix      \"patternProperties\": {")
                                patternProperties.forEachEndAware { isEnd2, it ->
                                    append(it.replaceIndent("$identPrefix        "))
                                    if (!isEnd2) appendLine(",") else appendLine()
                                }
                                if (propertyInfos != null) appendLine("$identPrefix      },")
                                else appendLine("$identPrefix      }")
                            }

                            // properties section.
                            if (propertyInfos != null) {
                                appendLine("$identPrefix      \"properties\": {")
                                propertyInfos.values.forEachEndAware { isEnd2, it ->
                                    append(it.fullJsonDef.replaceIndent("$identPrefix        "))
                                    if (!isEnd2) appendLine(",") else appendLine()
                                }
                                appendLine("$identPrefix      },")
                                appendLine("$identPrefix      \"additionalProperties\": false")
                            }

                            if (enabledShorthandSchema != null || extraShorthandSchema != null) {
                                appendLine("        },")
                                if (enabledShorthandSchema != null) {
                                    val suffix = if (extraShorthandSchema != null) "," else ""
                                    appendLine(enabledShorthandSchema.prependIndent("        ") + suffix)
                                }
                                if (extraShorthandSchema != null) {
                                    appendLine(extraShorthandSchema.prependIndent("        "))
                                }
                                appendLine("      ]")
                            }
                        }

                        if (!isEnd) appendLine("    },")
                        else appendLine("    }")
                    }
                    appendLine("  }")
                    appendLine("}")
                }
            }

        private fun shorthandValueSchema(propertyInfos: MutableMap<String, PropertyInfo>?): String? = propertyInfos.orEmpty().values
                .singleOrNull { it.shorthand }?.bodyDef?.let {
                    "{\n${it.prependIndent("  ")}\n}"
            }?.trimIndent()

        private fun enabledValueSchema(propertyValues: MutableMap<String, PropertyInfo>?): String? =
            if (propertyValues?.containsKey("enabled") == true) {
                enabledSettingsShortForm.trimIndent()
            } else null
    }

    private fun addPatternProperty(prop: KProperty<*>, block: () -> String) {
        ctx.declaredPatternProperties.compute(currentRoot.jsonDef) { _, old ->
            old.orNew.apply { add(block()) }
        }
    }

    private fun addProperty(prop: KProperty<*>, block: () -> String) {
        ctx.declaredProperties.compute(currentRoot.jsonDef) { _, old ->
            old.orNew().apply { put(prop.name, PropertyInfo(buildProperty(prop, block),
                block(),
                prop.hasAnnotation<Shorthand>())) }
        }
    }

    override fun visitClas(klass: KClass<*>) = if (ctx.visited.add(klass)) {
        when {
            klass.hasAnnotation<CustomSchemaDef>() ->
                ctx.customSchemaDef[klass.jsonDef] = klass.findAnnotation<CustomSchemaDef>()!!.json.trimIndent()
            else -> {
                visitSchema(klass, JsonSchemaBuilder(klass, ctx))
            }
        }
    } else Unit

    override fun visitTyped(
        prop: KProperty<*>,
        type: KType,
        schemaNodeType: KType,
        types: Collection<KClass<*>>,
        modifierAware: Boolean
    ) {
        fun buildForTyped(type: KType): String = when {
            type.isSchemaNode -> types.wrapInAnyOf { it.asReferenceTo }
            type.isCollection -> buildSchemaCollection { buildForTyped(type.collectionType) }
            type.isMap -> buildObjectWithDynamicKeys { buildForTyped(type.mapValueType) }
            else -> error("Unsupported type $type") // TODO Report
        }

        // Modifier-aware properties produce a regular property (for completion), and a pattern-property
        // producing a regular property is crucial for having a working chained completion
        if (modifierAware) {
            check(type.isMap) { "Modifier-aware properties must be of type Map<Modifier, *>" }
            addProperty(prop) { buildForTyped(type.mapValueType) }
            addPatternProperty(prop) {
                buildModifierBasedCollection(prop.name) { buildForTyped(type.mapValueType) }
            }
        } else {
            addProperty(prop) { buildForTyped(type) }
        }
        super.visitTyped(prop, type, schemaNodeType, types, modifierAware)
    }

    override fun visitCommon(prop: KProperty<*>, type: KType, default: Default<Any>?) =
        addProperty(prop) {
            if (prop.name == "aliases") {
                // Not all platforms could be used in aliases, but only those declared in module file in product definition
                buildAliasesMapAsList {
                    buildSchemaCollection(uniqueElements = true, minItems = 1) {
                        typeOf<String>().jsonSchema()
                    }
                }
            } else {
                val knownStringValues = if (type.isString || type.isTraceableString) {
                    prop.findAnnotation<KnownStringValues>()?.values.orEmpty()
                } else emptyArray<String>()
                if (knownStringValues.isNotEmpty()) {
                    """
                        "anyOf": [
                            {
                                "type": "string",
                                "enum": [${knownStringValues.joinToString(",") { "\"$it\"" }}],
                                "x-intellij-enum-order-sensitive": true
                            },
                            {
                                "type": "string"
                            }
                        ]
                    """.trimIndent()
                }
                else type.jsonSchema()
            }
        }

    private fun KType.jsonSchema(): String {
        val customSchemaDef = unwrapKClassOrNull?.findAnnotation<CustomSchemaDef>()
        return when {
            // Bypass other checks if we already know the schema for it.
            // This allows arbitrary classes to be used as long as they provide a CustomSchemaDef.
            customSchemaDef != null -> customSchemaDef.json.trimIndent()
            isEnum -> enumSchema
            isTraceableEnum -> arguments.single().type!!.enumSchema
            isString || isTraceableString -> stringSchema
            isBoolean -> booleanSchema
            isPath || isTraceablePath -> stringSchema
            isInt -> integerSchema
            isCollection -> buildSchemaCollection { collectionType.jsonSchema() }
            isMap -> buildObjectWithDynamicKeys { mapValueType.jsonSchema() }
            else -> error("Unsupported type ${this}, consider specifying a JSON schema explicitly for it using @CustomSchemaDef")
        }
    }

    private val MutableList<String>?.orNew get() = this ?: mutableListOf()
    private fun <T1, T2> MutableMap<T1, T2>?.orNew() = this ?: mutableMapOf()
}