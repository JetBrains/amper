/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.builders.schema

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.amper.frontend.builders.schema.SingleATypeSchemaBuilder.withExtras
import org.jetbrains.amper.frontend.meta.ATypesVisitor
import org.jetbrains.amper.frontend.meta.collectReferencedObjects
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.isValueRequired
import org.jetbrains.amper.frontend.types.simpleName
import org.jetbrains.amper.frontend.types.toType

fun jsonSchemaString(root: SchemaObjectDeclaration) = JsonSchema(root).jsonString

/**
 * Creates a [JsonElement] that represents a JSON schema for the specified [root].
 */
fun JsonSchema(root: SchemaObjectDeclaration) = root.simpleName().let { simpleName ->
    val objects = collectReferencedObjects(root)
    JsonSchema(
        "https://json-schema.org/draft/2020-12/schema",
        "${simpleName}.json",
        "$simpleName schema",
        "object",
        listOf(RefElement(root.simpleName())),
        objects
            .map { it.declaration }
            .associate { it.simpleName() to SingleATypeSchemaBuilder.asSchemaObject(it) },
    )
}

/**
 * A visitor that is building the definition of a single [SchemaTypingContext.AmperType] as a [JsonElement] in JSON schema.
 */
private object SingleATypeSchemaBuilder : ATypesVisitor<JsonElement> {

    /**
     * Generate a corresponding JSON schema [JsonElement] for the specified [SchemaTypingContext.Object].
     */
    // TODO Add ad-hock check for aliases field.
    fun asSchemaObject(root: SchemaObjectDeclaration): JsonElement {
        // Create a JSON element describing the selected property.
        fun SchemaObjectDeclaration.Property.propDesc() = type.accept().withExtras(this).wrapKnownValues(this)

        val notCtorArgs = root.properties.filterNot { it.isCtorArg }
        val modifierAware = notCtorArgs.filter { it.isModifierAware }

        val patternProperties = modifierAware.flatMap {
            val desc = it.propDesc()
            listOf("^${it.name}(@.+)?$" to desc, "^test-${it.name}(@.+)?$" to desc)
        }.toMap().takeIf { it.isNotEmpty() }

        // We intentionally include `modifierAware` twice (both in `patternProperties` and `properties`).
        val properties = notCtorArgs.flatMap {
            val desc = it.propDesc()
            // Also, test modifier is set as a prefix in YAML.
            if (it.isModifierAware) listOf("test-${it.name}" to desc, it.name to desc)
            else listOf(it.name to desc)
        }.toMap().takeIf { it.isNotEmpty() }

        val withoutConstructor = ObjectElement(properties, patternProperties).wrapShorthands(root)
        val withConstructor = ObjectElement(patternProperties = mapOf("^.*$" to withoutConstructor))

        // Wrap possible constructor argument.
        val ctorArg = root.properties.singleOrNull { it.isCtorArg }
        val otherNotOptional = notCtorArgs.any { it.isValueRequired() }
        
        // No ctor args.
        return if (ctorArg == null) withoutConstructor
        // Must provide some of the other parameters of the object.
        else if (otherNotOptional) withConstructor
        // Can instantiate an object just by specifying ctor arg (e.g. dependency just by GAV).
        else AnyOfElement(withConstructor, ScalarElement("string"))
    }

    /**
     * Copy [JsonObject] with extra fields, like `x-intellij-html-description` or `x-intellij-metadata`.
     */
    private fun JsonElement.withExtras(prop: SchemaObjectDeclaration.Property): JsonElement {
        if (this !is JsonObject) return this
        val doc = prop.documentation ?: return this
        return this
            .`x-intellij-html-description`(doc)
            .title(docShortForm(doc))
            .withIntellijMetadata(prop)
    }

    /**
     * See [withExtras].
     */
    private fun JsonElement.withIntellijMetadata(prop: SchemaObjectDeclaration.Property): JsonElement = with(prop) {
        if (this@withIntellijMetadata !is JsonObject) return this@withIntellijMetadata
        val extras = buildMap {
            if (specificToPlatforms.isNotEmpty()) put("platforms", specificToPlatforms.jsonArray { it.schemaValue })
            if (specificToProducts.isNotEmpty()) put("productTypes",specificToProducts.jsonArray { it.value })
            if (specificToGradleMessage != null) put("gradleSpecific", JsonPrimitive(true))
            // TODO: standalone specific?
            if (aliases.isNotEmpty()) put("aliases", aliases.jsonArray { it })
        }
        if (extras.isEmpty()) return this@withIntellijMetadata
        else {
            val extrasObj = JsonObject(extras)
            val extrasProperty = "x-intellij-metadata" to extrasObj
            JsonObject(this@withIntellijMetadata + extrasProperty)
        }
    }

    /**
     * Wrap an element with [EnumElement] if it has some known values to provide them in autocompletion.
     */
    private fun JsonElement.wrapKnownValues(prop: SchemaObjectDeclaration.Property): JsonElement {
        val knownValues = prop.knownStringValues
        return if (knownValues.isNotEmpty()) AnyOfElement(
            this, EnumElement(knownValues).`x-intellij-enum-order-sensitive`(true)
        ) else this
    }

    /**
     * Add known type shorthands to the schema element in form of `anyOf` wrapper.
     */
    private fun JsonElement.wrapShorthands(type: SchemaObjectDeclaration): JsonElement {
        val shorthands = type.properties.filter { it.hasShorthand }
        return if (shorthands.isNotEmpty()) {
            val booleanShorthands = shorthands
                .filter { it.type is SchemaType.BooleanType }
                .map { it.name }
            val enumShorthands = shorthands
                .mapNotNull { (it.type as? SchemaType.EnumType)?.declaration?.entries }
                .flatten()
                .map { it.schemaValue }
            val stringShorthands = shorthands
                .filter { it.type is SchemaType.StringType }
                .flatMap { it.knownStringValues }
            AnyOfElement(
                this,
                EnumElement(booleanShorthands.toSet() + enumShorthands + stringShorthands),
            )
        } else this
    }

    override fun visitEnum(type: SchemaType.EnumType) =
        EnumElement(
            enumValues = type.declaration.entries
                .filterNot { it.isOutdated }
                .filter { it.isIncludedIntoJsonSchema }
                .map { it.schemaValue },
            metadata = type.declaration.entries
                .mapNotNull { it.schemaValue toNotNull it.documentation }
                .toMap()
        ).`x-intellij-enum-order-sensitive`(type.declaration.isOrderSensitive)

    override fun visitScalar(type: SchemaType.ScalarType) = when(type) {
        is SchemaType.BooleanType -> ScalarElement("boolean")
        is SchemaType.IntType -> ScalarElement("integer")
        is SchemaType.PathType, is SchemaType.StringType -> ScalarElement("string")
        is SchemaType.EnumType -> error("Handled in visitEnum")
    }

    override fun visitMap(type: SchemaType.MapType) =
        ArrayElement(
            items = ObjectElement(patternProperties = mapOf("^[^@+:]+$" to type.valueType.accept())),
            uniqueItems = true,
        )

    override fun visitList(type: SchemaType.ListType) =
        ArrayElement(type.elementType.accept())

    override fun visitPolymorphic(type: SchemaType.VariantType) =
        AnyOfElement(type.declaration.variants.map { it.toType().accept() })

    override fun visitObject(type: SchemaType.ObjectType): JsonObject =
        RefElement(type.declaration.simpleName())
}

// TODO Introduce a short form of the schema doc in the annotation instead.
private fun docShortForm(documentation: String) = documentation
    .replace("[Read more]", "")
    .replace("""\([^)]*\)""".toRegex(), "")
    .replace("""Read more about \[[^]]*]""".toRegex(), "")
    .replace("""\[([^]]*)]""".toRegex(), { r -> r.groupValues.getOrNull(1) ?: "" })
    .trimStart { it.isWhitespace() || it == ')' || it == '.' }
    .trimEnd { it.isWhitespace() || it == '.' }