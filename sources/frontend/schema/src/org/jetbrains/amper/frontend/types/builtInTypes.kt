/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.EnumOrderSensitive
import org.jetbrains.amper.frontend.api.EnumValueFilter
import org.jetbrains.amper.frontend.api.FromKeyAndTheRestIsNested
import org.jetbrains.amper.frontend.api.GradleSpecific
import org.jetbrains.amper.frontend.api.HiddenFromCompletion
import org.jetbrains.amper.frontend.api.IgnoreForSchema
import org.jetbrains.amper.frontend.api.KnownStringValues
import org.jetbrains.amper.frontend.api.Misnomers
import org.jetbrains.amper.frontend.api.ModifierAware
import org.jetbrains.amper.frontend.api.PathMark
import org.jetbrains.amper.frontend.api.PlatformAgnostic
import org.jetbrains.amper.frontend.api.PlatformSpecific
import org.jetbrains.amper.frontend.api.ProductTypeSpecific
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Shorthand
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.schemaDelegate
import org.jetbrains.amper.frontend.schema.PluginSettings
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.types.SchemaType.TypeWithDeclaration
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType

data class BuiltInKey(private val kClass: KClass<*>) : DeclarationKey

val pluginSettingsTypeKey = BuiltInKey(PluginSettings::class)

val KClass<*>.builtInKey get() = BuiltInKey(this)

/**
 * Context that allows to inspect [KClass<*>] to create schema types.
 * Mostly is used for built-in types discovering.
 */
internal abstract class BuiltInTypingContext protected constructor(
    val parent: SchemaTypingContext?,
    private val roots: List<KClass<*>>,
) : SchemaTypingContext(parent) {

    internal val registeredDeclarations = ConcurrentHashMap<DeclarationKey, SchemaTypeDeclaration>()

    /**
     * True if we are discovering, false if we are in search-only mode.
     */
    private var discoverMode = true

    internal open fun discoverTypes() {
        // TODO Add "run once" check.
        roots.map { getType(it.starProjectedType) }.forEach { collectReferencedObjects(it) }
        discoverMode = false
    }

    final override fun findTypeWithDeclarationSelfOnly(type: KType) = 
        findOrRegisterTypeWithDeclaration(type)

    override fun findDeclarationSelfOnly(key: DeclarationKey) = registeredDeclarations[key]

    internal inline fun <reified T : SchemaTypeDeclaration> findOrRegister(
        key: DeclarationKey,
        crossinline create: () -> T,
    ): T? = if (discoverMode) registeredDeclarations.computeIfAbsent(key) { create() } as? T
    else registeredDeclarations[key] as? T

    protected open fun findOrRegisterTypeWithDeclaration(
        type: KType,
    ): TypeWithDeclaration? {
        val classifier = type.classifier
        val traceableEnumArg = type.arguments.firstOrNull()?.type?.classifier as? KClass<*>
        val isSchemaNode = classifier is KClass<*> && classifier.isSubclassOf(SchemaNode::class)

        return when {
            classifier is KClass<*> && classifier.isSubclassOf(Enum::class) -> @Suppress("UNCHECKED_CAST") SchemaType.EnumType(
                isMarkedNullable = type.isMarkedNullable,
                isTraceableWrapped = false,
                declaration = findOrRegister<SchemaEnumDeclaration>(classifier.builtInKey) {
                    BuiltinEnumDeclaration(classifier as KClass<out Enum<*>>)
                } ?: return null,
            )

            classifier == TraceableEnum::class -> @Suppress("UNCHECKED_CAST") SchemaType.EnumType(
                isMarkedNullable = type.isMarkedNullable,
                isTraceableWrapped = true,
                // We can safely assume that `traceableEnumArg` is not null here, because `TraceableEnum` type argument is always a enum class.
                declaration = findOrRegister<SchemaEnumDeclaration>(traceableEnumArg!!.builtInKey) {
                    BuiltinEnumDeclaration(traceableEnumArg as KClass<out Enum<*>>)
                } ?: return null,
            )

            isSchemaNode && classifier.isVariant() -> @Suppress("UNCHECKED_CAST") SchemaType.VariantType(
                isMarkedNullable = type.isMarkedNullable,
                declaration = findOrRegister<SchemaVariantDeclaration>(classifier.builtInKey) {
                    BuiltinVariantDeclaration(classifier as KClass<out SchemaNode>)
                } ?: return null,
            )

            isSchemaNode -> @Suppress("UNCHECKED_CAST") SchemaType.ObjectType(
                isMarkedNullable = type.isMarkedNullable,
                declaration = findOrRegister<SchemaObjectDeclaration>(classifier.builtInKey) {
                    BuiltinClassDeclaration(classifier as KClass<out SchemaNode>)
                } ?: return null,
            )

            else -> error("Unsupported type: $type")
        }
    }

    /**
     * Check if passed [KClass<*>] is a [SchemaType.VariantType] declaration.
     */
    protected open fun KClass<*>.isVariant(): Boolean = isSealed

    protected inner class BuiltinVariantDeclaration<T : SchemaNode>(
        override val backingReflectionClass: KClass<T>,
    ) : SchemaVariantDeclaration, ReflectionBasedTypeDeclaration {
        init {
            require(backingReflectionClass.isSealed)
        }

        override val variantTree: List<SchemaVariantDeclaration.Variant> by lazy {
            backingReflectionClass.sealedSubclasses.map {
                when(val type = getType(it.createType())) {
                    is SchemaType.ObjectType -> SchemaVariantDeclaration.Variant.LeafVariant(type.declaration)
                    is SchemaType.VariantType -> SchemaVariantDeclaration.Variant.SubVariant(type.declaration)
                    else -> error("Unexpected sealed subclass! class: $it, sealed: $backingReflectionClass")
                }
            }
        }

        override val variants by lazy {
            // TODO Do we need any checks here?
            backingReflectionClass.deepSealedSubclasses()
                .mapNotNull { getType(it.starProjectedType) as? SchemaType.ObjectType }
                .map { it.declaration }
        }

        private fun KClass<out SchemaNode>.deepSealedSubclasses(): List<KClass<out SchemaNode>> =
            if (isSealed) sealedSubclasses.fold(emptyList()) { a, c -> a + c.deepSealedSubclasses() } else listOf(this)

        override fun toString() = "variant declaration `${qualifiedName}`"
    }

    private class BuiltinEnumDeclaration<T : Enum<T>>(
        override val backingReflectionClass: KClass<T>,
    ) : SchemaEnumDeclaration, ReflectionBasedTypeDeclaration, SchemaEnumDeclarationBase() {
        private val enumOrderSensitive = backingReflectionClass.findAnnotation<EnumOrderSensitive>()
        private val enumConstants = backingReflectionClass.java.enumConstants

        override val isOrderSensitive = enumOrderSensitive != null

        override val entries by lazy {
            val annotationsByEntryName: Map<String, Field> = backingReflectionClass.java.fields
                .filter { it.type.isEnum }.associateBy { it.name }
            val filter = backingReflectionClass.findAnnotation<EnumValueFilter>()?.let { valueFilter ->
                val filterProperty = backingReflectionClass.memberProperties
                    .first { it.name == valueFilter.filterPropertyName }
                filterProperty to !valueFilter.isNegated
            }
            val entries = enumConstants.map { entry ->
                entry as SchemaEnum
                val annotated = annotationsByEntryName[entry.name]
                SchemaEnumDeclaration.EnumEntry(
                    name = entry.name,
                    schemaValue = entry.schemaValue,
                    isOutdated = entry.outdated,
                    isIncludedIntoJsonSchema = filter?.first?.get(entry) == filter?.second,
                    documentation = annotated?.getDeclaredAnnotation(SchemaDoc::class.java)?.doc,
                    origin = SchemaOrigin.Builtin,
                )
            }
            if (enumOrderSensitive?.reverse == true) entries.asReversed() else entries
        }

        override fun toEnumConstant(name: String): Any = enumConstants.first { it.name == name }

        override fun toString() = "enum declaration `${qualifiedName}`"
    }

    protected open inner class BuiltinClassDeclaration(
        override val backingReflectionClass: KClass<out SchemaNode>,
    ) : SchemaObjectDeclaration, ReflectionBasedTypeDeclaration, SchemaObjectDeclarationBase() {

        override val properties by lazy { parseBuiltInProperties() }

        protected fun parseBuiltInProperties(): List<SchemaObjectDeclaration.Property> {
            // This is needed to extract default values
            val exampleInstance = backingReflectionClass.createInstance()
            return backingReflectionClass.memberProperties.filterNot { it.hasAnnotation<IgnoreForSchema>() }
                .map { prop ->
                    SchemaObjectDeclaration.Property(
                        name = prop.name,
                        type = getType(prop.returnType).addKnownStringValuesIfAny(prop),
                        documentation = prop.findAnnotation<SchemaDoc>()?.doc,
                        misnomers = prop.findAnnotation<Misnomers>()?.values?.toSet().orEmpty(),
                        default = prop.schemaDelegate(exampleInstance)?.default,
                        isModifierAware = prop.hasAnnotation<ModifierAware>(),
                        isFromKeyAndTheRestNested = prop.hasAnnotation<FromKeyAndTheRestIsNested>(),
                        specificToPlatforms = prop.findAnnotation<PlatformSpecific>()?.platforms?.toSet().orEmpty(),
                        specificToProducts = prop.findAnnotation<ProductTypeSpecific>()?.productTypes?.toSet()
                            .orEmpty(),
                        isPlatformAgnostic = prop.hasAnnotation<PlatformAgnostic>(),
                        specificToGradleMessage = prop.findAnnotation<GradleSpecific>()?.message,
                        hasShorthand = prop.hasAnnotation<Shorthand>(),
                        inputOutputMark = prop.findAnnotation<PathMark>()?.type,
                        isHiddenFromCompletion = prop.hasAnnotation<HiddenFromCompletion>(),
                        origin = SchemaOrigin.Builtin,
                    )
                }
        }

        private fun SchemaType.addKnownStringValuesIfAny(property: KProperty<*>): SchemaType {
            val stringValues = property.findAnnotation<KnownStringValues>()
            if (stringValues != null) {
                check(this is SchemaType.StringType) { "@KnownStringValues is only supported for String properties" }
                return copy(knownStringValues = stringValues.values.toSet())
            }
            return this
        }

        override fun createInstance(): SchemaNode =
            backingReflectionClass.createInstance().apply {
                schemaType = this@BuiltinClassDeclaration
            }

        override fun toString() = "class declaration `${qualifiedName}`"
    }
}