/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.types.instrumentation

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.withIndent
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.CanBeReferenced
import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.api.FromKeyAndTheRestIsNested
import org.jetbrains.amper.frontend.api.GradleSpecific
import org.jetbrains.amper.frontend.api.HiddenFromCompletion
import org.jetbrains.amper.frontend.api.IgnoreForSchema
import org.jetbrains.amper.frontend.api.Misnomers
import org.jetbrains.amper.frontend.api.ModifierAware
import org.jetbrains.amper.frontend.api.PathMark
import org.jetbrains.amper.frontend.api.PlatformAgnostic
import org.jetbrains.amper.frontend.api.PlatformSpecific
import org.jetbrains.amper.frontend.api.ProductTypeSpecific
import org.jetbrains.amper.frontend.api.ReadOnly
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.SchemaValueDelegate
import org.jetbrains.amper.frontend.api.Shorthand
import org.jetbrains.amper.frontend.plugins.generated.ShadowMaps
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.tree.DefaultsReferenceTransform
import org.jetbrains.amper.frontend.tree.TypeDescriptor
import org.jetbrains.amper.frontend.tree.ReferenceNode
import org.jetbrains.amper.frontend.tree.ValueSinkPoint
import org.jetbrains.amper.frontend.types.BuiltinSchemaObjectDeclarationBase
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.plugins.schema.model.InputOutputMark
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Parses a [SchemaNode] class, outputs [BuiltinSchemaObjectDeclarationBase] implementation.
 */
context(generator: Generator)
internal fun <T : SchemaNode> parseAndGenerateSchemaNode(clazz: KClass<T>): ParsedDeclaration.SchemaNode {
    val name = ClassName(
        packageName = TARGET_PACKAGE,
        clazz.asClassName().simpleNames.joinToString("_", prefix = "DeclarationOf"),
    )
    val declarationParameters = mutableListOf<ParsedDeclaration.SchemaNode.Parameter>()

    val propertyDescriptors = mutableMapOf<String, ParsedTypeDescriptor>()
    val schemaProperties = clazz.memberProperties.filterNot {
        it.hasAnnotation<IgnoreForSchema>()
    }
    val propertiesListCode = CodeBlock.builder().apply {
        add("\n")

        // This is needed to extract default values
        val exampleInstance = clazz.createInstance()

        schemaProperties.forEach { prop ->
            add("%T(\n", SchemaObjectDeclaration.Property::class)
            withIndent {
                add("name = %S,\n", prop.name)
                val parsedType = schemaTypeExpression(prop.returnType, annotated = prop)
                propertyDescriptors[prop.name] = parsedType.descriptor
                declarationParameters += parsedType.declarationArguments

                add("type = %L,\n", parsedType.schemaTypeCreationExpression)
                prop.findAnnotation<SchemaDoc>()?.let {
                    add("documentation = %S,\n", it.doc)
                }
                prop.findAnnotation<Misnomers>()?.let {
                    val list = CodeBlock.builder().apply {
                        it.values.forEach { misnomer -> add("%S, ", misnomer) }
                    }.build()
                    add("misnomers = setOf(%L),\n", list)
                }
                prop.schemaDelegate(exampleInstance).default?.let {
                    val expression = when (it) {
                        Default.NestedObject -> CodeBlock.of("%T", Default.NestedObject::class)
                        is Default.Reference -> {
                            val list = CodeBlock.builder().apply {
                                it.referencedPath.forEach { part -> add("%S, ", part) }
                            }.build()
                            it.transform?.let { transform ->
                                CodeBlock.of(
                                    "%T(listOf(%L), %T(%S, %T()))",
                                    Default.Reference::class, list,
                                    ReferenceNode.Transform::class,
                                    transform.description,
                                    transform.function::class.asClassName(),
                                )
                            } ?: run {
                                CodeBlock.of("%T(listOf(%L))", Default.Reference::class, list)
                            }
                        }
                        is Default.Static -> CodeBlock.of("%T(%L)", Default.Static::class, defaultToCode(it.value))
                    }
                    add("default = %L,\n", expression)
                }
                if (prop.hasAnnotation<ModifierAware>()) add("isModifierAware = true,\n")
                if (prop.hasAnnotation<FromKeyAndTheRestIsNested>()) add("isFromKeyAndTheRestNested = true,\n")
                if (prop.hasAnnotation<PlatformAgnostic>()) add("isPlatformAgnostic = true,\n")
                if (prop.hasAnnotation<Shorthand>()) add("hasShorthand = true,\n")
                if (prop.hasAnnotation<HiddenFromCompletion>()) add("isHiddenFromCompletion = true,\n")
                if (prop.hasAnnotation<CanBeReferenced>() || parsedType.descriptor.canBeReferenced()) {
                    add("canBeReferenced = true,\n")
                }
                if (prop.hasAnnotation<ReadOnly>()) add("isUserSettable = false,\n")
                prop.findAnnotation<PlatformSpecific>()?.let {
                    val list = CodeBlock.builder().apply {
                        it.platforms.forEach { platform -> add("%T.%N, ", Platform::class, platform.name) }
                    }.build()
                    add("specificToPlatforms = setOf(%L),\n", list)
                }
                prop.findAnnotation<ProductTypeSpecific>()?.let {
                    val list = CodeBlock.builder().apply {
                        it.productTypes.forEach { product -> add("%T.%N, ", ProductType::class, product.name) }
                    }.build()
                    add("specificToProducts = setOf(%L),\n", list)
                }
                prop.findAnnotation<GradleSpecific>()?.let {
                    add("specificToGradleMessage = %S,\n", it.message)
                }
                prop.findAnnotation<PathMark>()?.let {
                    add("inputOutputMark = %T.%N,\n", InputOutputMark::class, it.type.name)
                }
            }
            add("),\n")
        }
    }.build()

    val spec = if (declarationParameters.isEmpty()) {
        TypeSpec.objectBuilder(name)
    } else {
        TypeSpec.classBuilder(name)
    }.apply {
        superclass(BuiltinSchemaObjectDeclarationBase::class.asClassName().parameterizedBy(clazz.asTypeName()))
        if (declarationParameters.isNotEmpty()) {
            primaryConstructor(FunSpec.constructorBuilder().apply {
                declarationParameters.forEach {
                    addParameter(it.parameterName, it.parameterType)
                }
            }.build())
        }
        declarationParameters.forEach {
            addProperty(
                PropertySpec.builder(it.parameterName, it.parameterType)
                .initializer("%N", it.parameterName)
                .build())
        }
        addKdoc("Declaration for [%T]", clazz)
        addFunction(
            FunSpec.builder("createInstance")
                .addModifiers(KModifier.OVERRIDE)
                .returns(clazz)
                .addCode("return %T()", clazz.asClassName())
                .build()
        )
        addProperty(
            PropertySpec.builder("qualifiedName", STRING, KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder().addCode("return %S", clazz.qualifiedName!!).build()
                ).build()
        )
        ShadowMaps.ShadowNodeClassToPublicReflectionName[clazz]?.let {
            addProperty(
                PropertySpec.builder("publicInterfaceReflectionName", STRING, KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder().addCode("return %S", it).build())
                    .build()
            )
            val displayName = it.substringAfterLast('.').replace('$', '.')
            addProperty(
                PropertySpec.builder("displayName", STRING, KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder().addCode("return %S", displayName).build())
                    .build()
            )
        }
        addProperty(
            PropertySpec.builder(
                "properties",
                LIST.parameterizedBy(SchemaObjectDeclaration.Property::class.asTypeName()),
                KModifier.OVERRIDE,
            ).initializer(
                CodeBlock.builder()
                .add("listOf(\n")
                .withIndent {
                    add(propertiesListCode)
                }
                .add(")")
                .build()
            ).build()
        )
    }.build()

    generator.writeFile(
        FileSpec.builder(name)
            .addAnnotation(AnnotationSpec.builder(Suppress::class)
                .addMember("%S", "UNCHECKED_CAST")
                .addMember("%S", "REDUNDANT_VISIBILITY_MODIFIER")
                .build())
            .addAnnotation(AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
                .addMember("%T::class", DefaultsReferenceTransform::class)
                .build())
            .addGeneratedComment()
            .addType(spec)
            .apply {
                // Generate named `SchemaValueDelegate` accessors.
                schemaProperties.forEach { property ->
                    val nodeType = SchemaValueDelegate::class.asClassName()
                        .parameterizedBy(property.returnType.asTypeName())
                    addProperty(PropertySpec.builder("${property.name}Delegate", nodeType)
                        .receiver(clazz)
                        .getter(FunSpec.getterBuilder()
                            .addCode("return getDelegate(%S) as %T", property.name, nodeType).build()
                        )
                        .build())
                }

                // Generate builder helpers (named `ValueSinkPoint` accessors)
                propertyDescriptors.entries.forEachIndexed { i, (propertyName, parsedType) ->
                    val descriptorTypeName = parsedType.toBuilderDescriptorTypeName()
                    val sinkPointType = ValueSinkPoint::class.asTypeName()
                        .parameterizedBy(descriptorTypeName, SchemaObjectDeclaration.Property::class.asTypeName())
                    addProperty(
                        PropertySpec.builder(propertyName, sinkPointType)
                            .receiver(ObjectBuilderContext.parameterizedBy(name))
                            .getter(
                                FunSpec.getterBuilder().addCode(
                                    "return ValueSinkPoint(%L, builder.properties[%L])",
                                    parsedType.toBuilderDescriptorCreationExpression(declarationParameters),
                                    i,
                                ).build()
                            ).build()
                    )
                }
            }
            .build()
    )

    return ParsedDeclaration.SchemaNode(
        name = clazz.asClassName(),
        declarationName = name,
        declarationParameters = declarationParameters,
    )
}

private fun ParsedTypeDescriptor.toBuilderDescriptorTypeName(): TypeName = when (this) {
    is ParsedTypeDescriptor.Boolean -> TypeDescriptor.Boolean::class.asTypeName()
    is ParsedTypeDescriptor.Enum -> TypeDescriptor.Enum::class.asTypeName()
        .parameterizedBy(enumName)
    is ParsedTypeDescriptor.Int -> TypeDescriptor.Int::class.asTypeName()
    is ParsedTypeDescriptor.List -> TypeDescriptor.List::class.asTypeName()
        .parameterizedBy(element.toBuilderDescriptorTypeName())
    is ParsedTypeDescriptor.Map -> TypeDescriptor.Map::class.asTypeName()
        .parameterizedBy(element.toBuilderDescriptorTypeName())
    is ParsedTypeDescriptor.Object -> TypeDescriptor.Object::class.asTypeName()
        .parameterizedBy(declaration)
    is ParsedTypeDescriptor.Variant -> TypeDescriptor.Variant::class.asTypeName()
        .parameterizedBy(schemaNodeName, declaration)
    is ParsedTypeDescriptor.Path -> TypeDescriptor.Path::class.asTypeName()
    is ParsedTypeDescriptor.String -> TypeDescriptor.String::class.asTypeName()
    is ParsedTypeDescriptor.CustomObject -> TypeDescriptor.CustomObject::class.asTypeName()
}

private fun ParsedTypeDescriptor.toBuilderDescriptorCreationExpression(
    parameterizedBy: List<ParsedDeclaration.SchemaNode.Parameter>,
): CodeBlock = when (this) {
    is ParsedTypeDescriptor.Boolean, is ParsedTypeDescriptor.Int, is ParsedTypeDescriptor.Path,
    is ParsedTypeDescriptor.String, is ParsedTypeDescriptor.CustomObject,
        ->
        CodeBlock.of("%T", toBuilderDescriptorTypeName())
    is ParsedTypeDescriptor.Enum -> CodeBlock.of("%T(%T)", TypeDescriptor.Enum::class, declaration)
    is ParsedTypeDescriptor.List -> CodeBlock.of(
        "%T(%L)",
        TypeDescriptor.List::class,
        element.toBuilderDescriptorCreationExpression(parameterizedBy),
    )
    is ParsedTypeDescriptor.Map -> CodeBlock.of(
        "%T(%L)",
        TypeDescriptor.Map::class,
        element.toBuilderDescriptorCreationExpression(parameterizedBy),
    )
    is ParsedTypeDescriptor.Object -> {
        val parameter = parameterizedBy.find { it.parameterType == declaration }
        if (parameter != null) {
            CodeBlock.of("%T(builder.%N)", TypeDescriptor.Object::class, parameter.parameterName)
        } else {
            CodeBlock.of("%T(%T)", TypeDescriptor.Object::class, declaration)
        }
    }
    is ParsedTypeDescriptor.Variant -> CodeBlock.of("%T(%T)", TypeDescriptor.Variant::class, declaration)
}

private fun ParsedTypeDescriptor.canBeReferenced(): Boolean = when (this) {
    // Scalars can be referenced because they can be easily denoted in plugins.
    ParsedTypeDescriptor.Boolean,
    ParsedTypeDescriptor.Path,
    ParsedTypeDescriptor.Int,
    ParsedTypeDescriptor.String,
        -> true
    // Builtin enum types can't be denoted directly, but they can be assigned to a string.
    is ParsedTypeDescriptor.Enum -> true

    // Collections are referenceable if their element type is referenceable.
    is ParsedTypeDescriptor.List -> element.canBeReferenced()
    is ParsedTypeDescriptor.Map -> element.canBeReferenced()

    // Object types are not referenceable.
    is ParsedTypeDescriptor.Object,
    is ParsedTypeDescriptor.Variant,
    ParsedTypeDescriptor.CustomObject -> false
}

// Can't use class reference because it's a typealias.
private val ObjectBuilderContext = ClassName("org.jetbrains.amper.frontend.tree", "ObjectBuilderContext")