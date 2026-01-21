/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import com.squareup.kotlinpoet.Annotatable
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.Documentable
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import org.jetbrains.amper.plugins.schema.model.Defaults
import org.jetbrains.amper.plugins.schema.model.PluginData
import kotlin.reflect.KClass

internal fun generateShadowSources(
    declarations: PluginData.Declarations,
): FileSpec {
    val classToVariant = buildMap {
        for (variantData in declarations.variants) {
            for (objectType in variantData.variants) {
                put(objectType.schemaName, variantData.name)
            }
        }
    }
    return FileSpec.builder(
        packageName = SHADOW_PACKAGE,
        fileName = "extensibilityApiGenerated",
    ).addFileComment(
        """
         AUTO-MANAGED SOURCE FILE! DO NOT EDIT MANUALLY!
         --------------------------------------------
         Run ExtensibilityApiDeclarationsTest to see if the source needs updating.
         
         ${'@'}formatter:off
         
    """.trimIndent()
    )
        .indent("    ")
        .addAnnotation(
            AnnotationSpec.builder(Suppress::class)
                .addMember("%S", "REDUNDANT_VISIBILITY_MODIFIER")
                .addMember("%S", "CanConvertToMultiDollarString")
                .build()
        )
        .addTypes(declarations.variants.map { generateShadowSealedClass(it) })
        .addTypes(declarations.classes.map {
            generateShadowSchemaNode(it, superVariant = classToVariant[it.name])
        })
        .addTypes(declarations.enums.map { generateShadowEnum(it) })
        .addType(generateShadowMaps(declarations))
        .build()
}

private fun generateShadowSealedClass(
    data: PluginData.VariantData,
) = TypeSpec.classBuilder(data.name.toShadowClassName()).apply {
    addKDocForShadow(data.name)
    addModifiers(KModifier.SEALED)
    superclass(SCHEMA_NODE)
}.build()

private fun generateShadowSchemaNode(
    data: PluginData.ClassData,
    superVariant: PluginData.SchemaName?,
) = TypeSpec.classBuilder(data.name.toShadowClassName()).apply {
    addKDocForShadow(data.name)
    addSchemaDoc(data.doc)
    superclass(superVariant?.toShadowClassName() ?: SCHEMA_NODE)
    addProperties(data.properties.map { property ->
        PropertySpec.builder(property.name, property.type.toShadowTypeName()).apply {
            val internal = property.internalAttributes
            if (internal != null) {
                if (internal.isShorthand) {
                    addAnnotation(SHORTHAND)
                }
                if (internal.isDependencyNotation) {
                    if (property.type is PluginData.Type.StringType && property.name == "coordinates") {
                        // We know that we need maven coordinates semantics for a string that is dependency notation
                        addAnnotation(AnnotationSpec.builder(STRING_SEMANTICS)
                            .addMember("%T.%N", STRING_SEMANTICS_KIND, "MavenCoordinates").build())
                    }
                    addAnnotation(FROM_KEY_AND_THE_REST_NESTED)
                }
                if (internal.isProvided) {
                    addModifiers(KModifier.LATEINIT)
                        .mutable(true)
                        .addAnnotation(IGNORE_FOR_SCHEMA)
                }

                if (!internal.isProvided && !internal.isDependencyNotation) {
                    addAnnotation(CAN_BE_REFERENCED)
                }
            }

            val default = property.default
            val type = property.type
            when {
                default != null -> delegate("value(default = %L)", default.toCode(type))
                type is PluginData.Type.ObjectType && !type.isNullable -> delegate("nested()")
                else -> if (internal?.isProvided != true) delegate("value()")
            }

            property.inputOutputMark?.let { mark ->
                addAnnotation(
                    AnnotationSpec.builder(PATH_MARK)
                        .addMember("%T.%N", PATH_MARK_TYPE, mark.name)
                        .build()
                )
            }
            addSchemaDoc(property.doc)
        }.build()
    })
}.build()

private fun generateShadowEnum(
    data: PluginData.EnumData,
) = TypeSpec.enumBuilder(data.schemaName.toShadowClassName()).apply {
    addKDocForShadow(data.schemaName)
    addSuperinterface(SCHEMA_ENUM)
    addProperty(PropertySpec.builder("schemaValue", STRING, KModifier.OVERRIDE).initializer("schemaValue").build())
    primaryConstructor(FunSpec.constructorBuilder().addParameter("schemaValue", STRING).build())
    data.entries.forEach { entry ->
        addEnumConstant(
            name = entry.name,
            typeSpec = TypeSpec.anonymousClassBuilder()
                .addSchemaDoc(entry.doc)
                .addSuperclassConstructorParameter("%S", entry.schemaName)
                .build()
        )
    }
}.build()


private fun generateShadowMaps(declarations: PluginData.Declarations): TypeSpec {
    val names = buildList {
        addAll(declarations.classes.map { it.name })
        addAll(declarations.variants.map { it.name })
        addAll(declarations.enums.map { it.schemaName })
    }
    val shadowMap = CodeBlock.builder().add("mapOf(\n")
    for (name in names) shadowMap.add("%S to %T::class,\n", name.qualifiedName, name.toShadowClassName())
    shadowMap.add(")")

    val unShadowMap = CodeBlock.builder().add("mapOf(\n")
    for (name in names) unShadowMap.add("%T::class to %S,\n", name.toShadowClassName(), name.reflectionName())
    unShadowMap.add(")")
    return TypeSpec.objectBuilder("ShadowMaps")
        .addProperty(
            PropertySpec.builder("PublicInterfaceToShadowNodeClass", MAP.parameterizedBy(STRING, KCLASS))
                .initializer(shadowMap.build())
                .build()
        )
        .addProperty(
            PropertySpec.builder("ShadowNodeClassToPublicReflectionName", MAP.parameterizedBy(KCLASS, STRING))
                .initializer(unShadowMap.build())
                .build()
        )
        .build()
}

private fun <B : Documentable.Builder<B>> B.addKDocForShadow(name: PluginData.SchemaName): B = apply {
    addKdoc("Generated!\nShadow for `${name.qualifiedName}`")
}

private fun <B : Annotatable.Builder<B>> B.addSchemaDoc(doc: String?): B = apply {
    if (doc != null) {
        addAnnotation(AnnotationSpec.builder(SCHEMA_DOC).addMember("doc = %S", doc).build())
    }
}

private fun PluginData.SchemaName.toShadowClassName() =
    ClassName(SHADOW_PACKAGE, "Shadow" + simpleNames.joinToString(""))

private fun PluginData.Type.toShadowTypeName(): TypeName = when (this) {
    is PluginData.Type.BooleanType -> BOOLEAN
    is PluginData.Type.IntType -> INT
    is PluginData.Type.StringType -> STRING
    is PluginData.Type.PathType -> PATH
    is PluginData.Type.EnumType -> schemaName.toShadowClassName()
    is PluginData.Type.ListType -> LIST.parameterizedBy(elementType.toShadowTypeName())
    is PluginData.Type.MapType -> MAP.parameterizedBy(STRING, valueType.toShadowTypeName())
    is PluginData.Type.ObjectType -> schemaName.toShadowClassName()
    is PluginData.Type.VariantType -> schemaName.toShadowClassName()
}.copy(nullable = isNullable)

private fun Defaults.toCode(type: PluginData.Type): CodeBlock = when (this) {
    Defaults.Null -> CodeBlock.of("null")
    is Defaults.BooleanDefault -> CodeBlock.of("%L", value)
    is Defaults.IntDefault -> CodeBlock.of("%L", value)
    is Defaults.EnumDefault -> CodeBlock.of("%T.%N", type.toShadowTypeName(), value)
    is Defaults.ListDefault -> if (value.isEmpty()) CodeBlock.of("emptyList()") else {
        type as PluginData.Type.ListType
        val list = CodeBlock.builder()
        value.map { list.add("%L, ", it.toCode(type.elementType)) }
        CodeBlock.of("listOf(%L)", list.build())
    }
    is Defaults.MapDefault -> CodeBlock.of("emptyMap()") // NOTE: support non-trivial maps when ready
    is Defaults.StringDefault -> CodeBlock.of("%S", value)
}

private val PATH = ClassName("java.nio.file", "Path")
private val KCLASS = KClass::class.asClassName().parameterizedBy(STAR)

private val IGNORE_FOR_SCHEMA = ClassName("org.jetbrains.amper.frontend.api", "IgnoreForSchema")
private val CAN_BE_REFERENCED = ClassName("org.jetbrains.amper.frontend.api", "CanBeReferenced")
private val SCHEMA_DOC = ClassName("org.jetbrains.amper.frontend.api", "SchemaDoc")
private val SHORTHAND = ClassName("org.jetbrains.amper.frontend.api", "Shorthand")
private val FROM_KEY_AND_THE_REST_NESTED = ClassName("org.jetbrains.amper.frontend.api", "FromKeyAndTheRestIsNested")
private val STRING_SEMANTICS = ClassName("org.jetbrains.amper.frontend.api", "StringSemantics")
private val STRING_SEMANTICS_KIND = ClassName("org.jetbrains.amper.frontend.types", "SchemaType", "StringType", "Semantics")
private val PATH_MARK = ClassName("org.jetbrains.amper.frontend.api", "PathMark")
private val PATH_MARK_TYPE = ClassName("org.jetbrains.amper.plugins.schema.model", "InputOutputMark")
private val SCHEMA_ENUM = ClassName("org.jetbrains.amper.frontend", "SchemaEnum")
private val SCHEMA_NODE = ClassName("org.jetbrains.amper.frontend.api", "SchemaNode")

private const val SHADOW_PACKAGE = "org.jetbrains.amper.frontend.plugins.generated"
