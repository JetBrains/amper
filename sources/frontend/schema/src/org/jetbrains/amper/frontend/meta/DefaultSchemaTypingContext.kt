/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.meta

import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.Aliases
import org.jetbrains.amper.frontend.api.DependencyKey
import org.jetbrains.amper.frontend.api.EnumOrderSensitive
import org.jetbrains.amper.frontend.api.EnumValueFilter
import org.jetbrains.amper.frontend.api.GradleSpecific
import org.jetbrains.amper.frontend.api.IgnoreForSchema
import org.jetbrains.amper.frontend.api.KnownStringValues
import org.jetbrains.amper.frontend.api.ModifierAware
import org.jetbrains.amper.frontend.api.PlatformAgnostic
import org.jetbrains.amper.frontend.api.PlatformSpecific
import org.jetbrains.amper.frontend.api.ProductTypeSpecific
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Shorthand
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaEnumDeclaration
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.SchemaTypeDeclaration
import org.jetbrains.amper.frontend.types.SchemaVariantDeclaration
import java.lang.reflect.Field
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Discovering all schema-defined properties and types.
 */
open class DefaultSchemaTypingContext : SchemaTypingContext {
    private val enums = ConcurrentHashMap<String, SchemaEnumDeclaration>()
    private val classes = ConcurrentHashMap<String, SchemaObjectDeclaration>()
    private val variants = ConcurrentHashMap<String, SchemaVariantDeclaration>()

    /**
     * Load [SchemaType] for specified [KType].
     */
    override fun getType(type: KType): SchemaType {
        return when (val classifier = type.classifier) {
            Int::class -> SchemaType.IntType(isMarkedNullable = type.isMarkedNullable)
            Boolean::class -> SchemaType.BooleanType(isMarkedNullable = type.isMarkedNullable)
            Path::class -> SchemaType.PathType(
                isMarkedNullable = type.isMarkedNullable,
                isTraceableWrapped = false,
            )
            TraceablePath::class -> SchemaType.PathType(
                isMarkedNullable = type.isMarkedNullable,
                isTraceableWrapped = true,
            )
            String::class -> SchemaType.StringType(
                isMarkedNullable = type.isMarkedNullable,
                isTraceableWrapped = false,
            )
            TraceableString::class -> SchemaType.StringType(
                isMarkedNullable = type.isMarkedNullable,
                isTraceableWrapped = true,
            )
            TraceableEnum::class -> @Suppress("UNCHECKED_CAST") SchemaType.EnumType(
                isMarkedNullable = type.isMarkedNullable,
                isTraceableWrapped = true,
                declaration = enumDeclaration(type.arguments.first().type!!.classifier as KClass<out Enum<*>>),
            )
            Map::class -> SchemaType.MapType(
                isMarkedNullable = type.isMarkedNullable,
                keyType = getType(checkNotNull(type.arguments[0].type)) as SchemaType.StringType,
                valueType = getType(checkNotNull(type.arguments[1].type)),
            )
            List::class, Set::class, Collection::class -> SchemaType.ListType(
                isMarkedNullable = type.isMarkedNullable,
                elementType = getType(checkNotNull(type.arguments[0].type)),
            )
            is KClass<*> -> {
                val javaClass = classifier.java
                when {
                    javaClass.isEnum -> @Suppress("UNCHECKED_CAST") SchemaType.EnumType(
                        isMarkedNullable = type.isMarkedNullable,
                        isTraceableWrapped = false,
                        declaration = enumDeclaration(classifier as KClass<out Enum<*>>),
                    )
                    SchemaNode::class.java.isAssignableFrom(javaClass) -> if (classifier.isSealed) {
                        @Suppress("UNCHECKED_CAST") SchemaType.VariantType(
                            isMarkedNullable = type.isMarkedNullable,
                            declaration = variantDeclaration(classifier as KClass<out SchemaNode>),
                        )
                    } else {
                        @Suppress("UNCHECKED_CAST") SchemaType.ObjectType(
                            isMarkedNullable = type.isMarkedNullable,
                            declaration = classDeclaration(classifier as KClass<out SchemaNode>),
                        )
                    }
                    else -> error("Unsupported type: $type")
                }
            }
            else -> error("Unexpected type: $type")
        }
    }

    private fun <T : Enum<T>> enumDeclaration(enumClass: KClass<T>) =
        enums.computeIfAbsent(checkNotNull(enumClass.qualifiedName)) {
            BuiltinEnumDeclaration(enumClass)
        }

    private fun <T : SchemaNode> variantDeclaration(sealedClass: KClass<T>) =
        variants.computeIfAbsent(checkNotNull(sealedClass.qualifiedName)) {
            BuiltinVariantDeclaration(sealedClass)
        }

    private fun <T : SchemaNode> classDeclaration(clazz: KClass<T>) =
        classes.computeIfAbsent(checkNotNull(clazz.qualifiedName)) {
            BuiltinClassDeclaration(clazz)
        }

    abstract class BuiltinTypeDeclarationBase(
        clazz: KClass<*>,
    ) : SchemaTypeDeclaration {
        override val qualifiedName = checkNotNull(clazz.qualifiedName)
    }

    private inner class BuiltinVariantDeclaration<T : SchemaNode>(
        sealedClass: KClass<T>,
    ) : SchemaVariantDeclaration, BuiltinTypeDeclarationBase(sealedClass) {
        init {
            require(sealedClass.isSealed)
        }

        override val variants by lazy {
            sealedClass.deepSealedSubclasses().map { classDeclaration(it) }
        }

        private fun KClass<out SchemaNode>.deepSealedSubclasses(): List<KClass<out SchemaNode>> =
            if (isSealed) sealedSubclasses.fold(emptyList()) { a, c -> a + c.deepSealedSubclasses() } else listOf(this)
    }

    private class BuiltinEnumDeclaration<T : Enum<T>>(
        override val backingReflectionClass: KClass<T>,
    ) : SchemaEnumDeclaration, BuiltinTypeDeclarationBase(backingReflectionClass) {
        private val enumOrderSensitive = backingReflectionClass.findAnnotation<EnumOrderSensitive>()
        override val isOrderSensitive = enumOrderSensitive != null
        override val entries by lazy {
            val annotationsByEntryName: Map<String, Field> = backingReflectionClass.java.fields
                .filter { it.type.isEnum }.associateBy { it.name }
            val filter = backingReflectionClass.findAnnotation<EnumValueFilter>()?.let { valueFilter ->
                val filterProperty = backingReflectionClass.memberProperties
                    .first { it.name == valueFilter.filterPropertyName }
                filterProperty to !valueFilter.isNegated
            }
            val entries = backingReflectionClass.java.enumConstants.map { entry ->
                entry as SchemaEnum
                val annotated = annotationsByEntryName[entry.name]
                SchemaEnumDeclaration.EnumEntry(
                    name = entry.name,
                    schemaValue = entry.schemaValue,
                    isOutdated = entry.outdated,
                    isIncludedIntoJsonSchema = filter?.first?.get(entry) == filter?.second,
                    documentation = annotated?.getDeclaredAnnotation(SchemaDoc::class.java)?.doc,
                )
            }
            if (enumOrderSensitive?.reverse == true) entries.asReversed() else entries
        }
    }

    private inner class BuiltinClassDeclaration<T : SchemaNode>(
        override val backingReflectionClass: KClass<T>,
    ) : SchemaObjectDeclaration, BuiltinTypeDeclarationBase(backingReflectionClass) {
        override val properties by lazy {
            parseProperties() + customProperties(backingReflectionClass)
        }

        private fun parseProperties(): List<SchemaObjectDeclaration.Property> {
            // This is needed to extract default values
            val exampleInstance = backingReflectionClass.createInstance()
            return backingReflectionClass.memberProperties.filterNot { it.hasAnnotation<IgnoreForSchema>() }.map {
                SchemaObjectDeclaration.Property(
                    name = it.name,
                    type = getType(it.returnType),
                    documentation = it.findAnnotation<SchemaDoc>()?.doc,
                    aliases = it.findAnnotation<Aliases>()?.values?.toSet().orEmpty(),
                    default = it.valueBase(exampleInstance)?.default,
                    isModifierAware = it.hasAnnotation<ModifierAware>(),
                    // FIXME Maybe introduce new annotation with meaningful name, or change this one.
                    isCtorArg = it.hasAnnotation<DependencyKey>(),
                    specificToPlatforms = it.findAnnotation<PlatformSpecific>()?.platforms?.toSet().orEmpty(),
                    specificToProducts = it.findAnnotation<ProductTypeSpecific>()?.productTypes?.toSet().orEmpty(),
                    isPlatformAgnostic = it.hasAnnotation<PlatformAgnostic>(),
                    specificToGradleMessage = it.findAnnotation<GradleSpecific>()?.message,
                    knownStringValues = it.findAnnotation<KnownStringValues>()?.values?.toSet().orEmpty(),
                    hasShorthand = it.hasAnnotation<Shorthand>(),
                )
            }
        }
    }

    /**
     * Load custom (plugin) properties for type.
     *
     * Note: [type] is not a receiver to have a possibility to call `super.customProperties()`.
     */
    protected open fun customProperties(type: KClass<out SchemaNode>): List<SchemaObjectDeclaration.Property> =
        if (type != Settings::class) emptyList()
        else emptyList()  // TODO: Implement this properly for extensibility prototype

    companion object : DefaultSchemaTypingContext()
}
