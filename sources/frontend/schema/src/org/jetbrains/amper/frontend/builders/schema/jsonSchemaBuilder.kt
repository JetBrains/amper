/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.builders.schema

import com.intellij.util.applyIf
import com.intellij.util.asSafely
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.meta.ATypesVisitor
import org.jetbrains.amper.frontend.meta.collectReferencedObjects
import org.jetbrains.amper.frontend.types.AmperTypes
import org.jetbrains.amper.frontend.types.isBoolean
import org.jetbrains.amper.frontend.types.isInt
import org.jetbrains.amper.frontend.types.isPath
import org.jetbrains.amper.frontend.types.isString
import org.jetbrains.amper.frontend.types.isTraceablePath
import org.jetbrains.amper.frontend.types.isTraceableString
import org.jetbrains.amper.frontend.types.kClass


fun jsonSchemaString(root: AmperTypes.AmperType) = JsonSchema(root).jsonString

/**
 * Creates a [JsonElement] that represents a JSON schema for the specified [root].
 */
fun JsonSchema(root: AmperTypes.AmperType) = root.kType.kClass.simpleName!!.let { simpleName ->
    val objects = collectReferencedObjects(root)
    JsonSchema(
        "https://json-schema.org/draft/2020-12/schema",
        "${simpleName}.json",
        "$simpleName schema",
        "object",
        listOf(RefElement(root.kType.kClass.simpleName!!)),
        objects
            .associate { it.kType.kClass.simpleName!! to SingleATypeSchemaBuilder.asSchemaObject(it) },
    )
}

/**
 * A visitor that is building the definition of a single [AmperTypes.AmperType] as a [JsonElement] in JSON schema.
 */
private object SingleATypeSchemaBuilder : ATypesVisitor<JsonElement> {

    /**
     * Generate a corresponding JSON schema [JsonElement] for the specified [AmperTypes.Object].
     */
    fun asSchemaObject(root: AmperTypes.Object): JsonElement {
        // Create a JSON element describing the selected property.
        fun AmperTypes.Property.propDesc() = type.accept().withExtras(this).wrapKnownValues(this)

        val notCtorArgs = root.properties.filterNot { it.meta.isCtorArg }
        val modifierAware = notCtorArgs.filter { it.meta.modifierAware != null }

        val patternProperties = modifierAware.flatMap {
            val desc = it.propDesc()
            listOf("^${it.meta.name}(@.+)?$" to desc, "^test-${it.meta.name}(@.+)?$" to desc)
        }.toMap().takeIf { it.isNotEmpty() }

        // We intentionally include `modifierAware` twice (both in `patternProperties` and `properties`).
        val properties = notCtorArgs.flatMap {
            val desc = it.propDesc()
            // Also, test modifier is set as a prefix in YAML.
            if (it.meta.modifierAware != null) listOf("test-${it.meta.name}" to desc, it.meta.name to desc)
            else listOf(it.meta.name to desc)
        }.toMap().takeIf { it.isNotEmpty() }

        val withoutConstructor = ObjectElement(properties, patternProperties).wrapShorthands(root)
        val withConstructor = ObjectElement(patternProperties = mapOf("^.*$" to withoutConstructor))

        // Wrap possible constructor argument.
        val ctorArg = root.properties.singleOrNull { it.meta.isCtorArg }
        val otherNotOptional = notCtorArgs.any { it.meta.isValueRequired }
        
        // No ctor args.
        return if (ctorArg == null) withoutConstructor
        // Must provide some of the other parameters of the object.
        else if (otherNotOptional) withConstructor
        // Can instantiate an object just by specifying ctor arg (e.g. dependency just by GAV).
        else AnyOfElement(ScalarElement("string"), withConstructor)
    }

    /**
     * Copy [JsonObject] with extra fields, like `x-intellij-html-description` or `x-intellij-metadata`.
     */
    private fun JsonElement.withExtras(prop: AmperTypes.Property): JsonElement {
        if (this !is JsonObject) return this
        val doc = prop.meta.schemaDoc?.doc ?: return this
        return this
            .`x-intellij-html-description`(doc)
            .title(docShortForm(doc))
            .withIntellijMetdata(prop)
    }

    /**
     * See [withExtras].
     */
    private fun JsonElement.withIntellijMetdata(prop: AmperTypes.Property): JsonElement = with(prop.meta) {
        if (this@withIntellijMetdata !is JsonObject) return this@withIntellijMetdata
        val extras = listOfNotNull(
            "platforms" toNotNull platformSpecific?.platforms?.jsonArray { it.schemaValue },
            "productTypes" toNotNull productTypeSpecific?.productTypes?.jsonArray { it.value },
            "gradleSpecific" toNotNull gradleSpecific?.let { JsonPrimitive(true) },
            "standaloneSpecific" toNotNull standaloneSpecific?.let { JsonPrimitive(true) },
            "aliases" toNotNull aliases?.jsonArray { it },
        ).toMap()
        if (extras.isEmpty()) return this@withIntellijMetdata
        else {
            val extrasObj = JsonObject(extras)
            val extrasProperty = "x-intellij-metadata" to extrasObj
            JsonObject(this@withIntellijMetdata + extrasProperty)
        }
    }

    /**
     * Wrap an element with [EnumElement] if it has some known values to provide them in autocompletion.
     */
    private fun JsonElement.wrapKnownValues(prop: AmperTypes.Property): JsonElement {
        val knownValues = prop.meta.knownStringValues
        return if (knownValues?.isNotEmpty() == true) AnyOfElement(
            EnumElement(knownValues).`x-intellij-enum-order-sensitive`(true), this
        ) else this
    }

    /**
     * Add known type shorthands to the schema element in form of `anyOf` wrapper.
     */
    private fun JsonElement.wrapShorthands(type: AmperTypes.AmperType): JsonElement {
        if (type !is AmperTypes.Object) return this
        val shorthands = type.properties.filter { it.meta.hasShorthand }
        return if (shorthands.isNotEmpty()) {
            val booleanShorthands = shorthands
                .filter { it.meta.type.isBoolean }
                .map { it.meta.name }
            val enumShorthands = shorthands
                .mapNotNull { it.type.asSafely<AmperTypes.Enum>()?.enumValues }
                .flatten()
                .map { it.schemaValue }
            val stringShorthands = shorthands
                .filter { it.meta.type.isString || it.meta.type.isTraceableString }
                .flatMap { it.meta.knownStringValues.orEmpty() }
            AnyOfElement(
                EnumElement(booleanShorthands.toSet() + enumShorthands + stringShorthands),
                this,
            )
        } else this
    }

    override fun visitEnum(type: AmperTypes.Enum) =
        EnumElement(
            enumValues = type.enumValues
                .filterNot { it.outdated }
                .filter(type.propertyFilter)
                .applyIf(type.orderSensitive?.reverse == true) { asReversed() }
                .map { it.schemaValue },
            metadata = type.annotatedEnumValues
                .mapNotNull { it.first.schemaValue toNotNull it.second.firstNotNullOfOrNull { it.asSafely<SchemaDoc>()?.doc } }
                .toMap()
        ).`x-intellij-enum-order-sensitive`(type.orderSensitive != null)

    override fun visitScalar(type: AmperTypes.Scalar) = when {
        type.kType.isString || type.kType.isTraceableString -> ScalarElement("string")
        type.kType.isPath || type.kType.isTraceablePath -> ScalarElement("string")
        type.kType.isBoolean -> ScalarElement("boolean")
        type.kType.isInt -> ScalarElement("integer")
        else -> error("Unreachable code. Type: ${type.kType}")
    }

    override fun visitMap(type: AmperTypes.Map) =
        ArrayElement(
            items = ObjectElement(patternProperties = mapOf("^[^@+:]+$" to type.valueType.accept())),
            uniqueItems = true,
        )

    override fun visitList(type: AmperTypes.List) =
        ArrayElement(type.valueType.accept())

    override fun visitPolymorphic(type: AmperTypes.Polymorphic) =
        AnyOfElement(type.inheritors.map { it.accept() })

    override fun visitObject(type: AmperTypes.Object): JsonObject =
        RefElement(type.kType.kClass.simpleName!!)
}

// TODO Introduce a short form of the schema doc in the annotation instead.
private fun docShortForm(documentation: String) = documentation
    .replace("[Read more]", "")
    .replace("""\([^)]*\)""".toRegex(), "")
    .replace("""Read more about \[[^]]*]""".toRegex(), "")
    .replace("""\[([^]]*)]""".toRegex(), { r -> r.groupValues.getOrNull(1) ?: "" })
    .trimStart { it.isWhitespace() || it == ')' || it == '.' }
    .trimEnd { it.isWhitespace() || it == '.' }