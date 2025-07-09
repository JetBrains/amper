package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.Aliases
import org.jetbrains.amper.frontend.api.Default
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
import org.jetbrains.amper.frontend.plugins.ExtensionSchemaNode
import org.jetbrains.amper.frontend.plugins.TaskAction
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.plugins.schema.model.SchemaName
import org.jetbrains.amper.plugins.schema.model.TaskInfo
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
 * Container for [SchemaType]s.
 */
interface SchemaTypingContext {
    fun getType(type: KType): SchemaType

    fun getPluginContext(pluginId: PluginData.Id): SchemaTypingContext
}

/**
 * Discovering all schema-defined properties and types.
 */
fun SchemaTypingContext(
    pluginData: List<PluginData> = emptyList(),
): SchemaTypingContext {
    return DefaultSchemaTypingContext(PluginAwareSchemaTypingDeclarations(pluginData))
}

private class PluginAwareSchemaTypingDeclarations(
    val pluginData: List<PluginData>,
) {
    val enums = ConcurrentHashMap<DeclarationKey, SchemaEnumDeclaration>()
    val classes = ConcurrentHashMap<DeclarationKey, SchemaObjectDeclaration>()
    val variants = ConcurrentHashMap<DeclarationKey, SchemaVariantDeclaration>()

    init {
        pluginData.forEach {
            for (declaration in it.classTypes) {
                classes[it.id / declaration.name] = ExternalObjectDeclaration(
                    pluginId = it.id,
                    data = declaration,
                    instantiationStrategy = {
                        ExtensionSchemaNode(declaration.name.qualifiedName)
                    },
                    isRootSchema = declaration.name == it.moduleExtensionSchemaName,
                )
            }
            for (declaration in it.enumTypes) {
                enums[it.id / declaration.schemaName] = ExternalEnumDeclaration(declaration)
            }
            if (it.moduleExtensionSchemaName == null) {
                // Add a stub one just for the `enabled` sake.
                classes[it.id / StubSchemaName] = ExternalObjectDeclaration(
                    pluginId = it.id,
                    data = PluginData.ClassData(name = StubSchemaName),
                    instantiationStrategy = { ExtensionSchemaNode(null) },
                    isRootSchema = true,
                )
            }
            variants[it.id / TaskActionSchemaName] =
                SyntheticVariantDeclaration(
                    qualifiedName = TaskActionSchemaName.qualifiedName,
                    variants = it.tasks.map<TaskInfo, ExternalObjectDeclaration> { taskInfo ->
                        ExternalObjectDeclaration(
                            pluginId = it.id,
                            data = taskInfo.syntheticType,
                            instantiationStrategy = {
                                TaskAction(
                                    jvmFunctionName = taskInfo.jvmFunctionName,
                                    jvmOwnerClassName = taskInfo.jvmFunctionClassName,
                                    inputPropertyNames = taskInfo.inputPropertyNames,
                                    outputPropertyNames = taskInfo.outputPropertyNames,
                                )
                            },
                            isRootSchema = false,
                        )
                    }
                )
        }
    }

    private fun toSchemaType(
        pluginId: PluginData.Id,
        type: PluginData.Type,
    ): SchemaType = when (type) {
        is PluginData.Type.BooleanType -> SchemaType.BooleanType(isMarkedNullable = type.isNullable)
        is PluginData.Type.IntType -> SchemaType.IntType(isMarkedNullable = type.isNullable)
        is PluginData.Type.StringType -> SchemaType.StringType(isMarkedNullable = type.isNullable)
        is PluginData.Type.PathType -> SchemaType.PathType(isMarkedNullable = type.isNullable)
        is PluginData.Type.ListType -> SchemaType.ListType(
            elementType = toSchemaType(pluginId, type.elementType),
            isMarkedNullable = type.isNullable,
        )
        is PluginData.Type.MapType -> SchemaType.MapType(
            keyType = SchemaType.StringType(),
            valueType = toSchemaType(pluginId, type.valueType),
            isMarkedNullable = type.isNullable,
        )
        is PluginData.Type.EnumType -> SchemaType.EnumType(
            declaration = checkNotNull(enums[pluginId / type.schemaName]),
            isMarkedNullable = type.isNullable,
        )
        is PluginData.Type.ObjectType -> SchemaType.ObjectType(
            declaration = checkNotNull(classes[pluginId / type.schemaName]),
            isMarkedNullable = type.isNullable,
        )
    }

    private inner class ExternalObjectDeclaration(
        private val pluginId: PluginData.Id,
        private val data: PluginData.ClassData,
        private val instantiationStrategy: () -> SchemaNode,
        private val isRootSchema: Boolean,
    ) : SchemaObjectDeclaration {
        override val properties: List<SchemaObjectDeclaration.Property> by lazy {
            buildList {
                for (property in data.properties) {
                    this += SchemaObjectDeclaration.Property(
                        name = property.name,
                        type = toSchemaType(pluginId, property.type),
                        documentation = property.doc,
                    )
                }
                if (isRootSchema) {
                    // Add a synthetic `enabled` property if this is a plugin schema extension
                    this += SchemaObjectDeclaration.Property(
                        name = "enabled",
                        type = SchemaType.BooleanType(),
                        default = Default.Static(false),
                        documentation = "Whether to enable the `${pluginId.value}` plugin",
                        hasShorthand = true,
                    )
                }
            }
        }

        override fun createInstance(): SchemaNode = instantiationStrategy()
        override val qualifiedName get() = data.name.qualifiedName
        override fun toString() = qualifiedName
    }

    private class ExternalEnumDeclaration(
        private val data: PluginData.EnumData,
    ) : SchemaEnumDeclaration {
        override val entries: List<SchemaEnumDeclaration.EnumEntry> by lazy {
            data.entries.map { entry ->
                SchemaEnumDeclaration.EnumEntry(
                    name = entry.name,
                    schemaValue = entry.schemaName,
                    documentation = entry.doc,
                )
            }
        }
        override val isOrderSensitive get() = false
        override fun toEnumConstant(name: String) = name
        override val qualifiedName get() = data.schemaName.qualifiedName
        override fun toString() = qualifiedName
    }

    private class SyntheticVariantDeclaration(
        override val qualifiedName: String,
        override val variants: List<SchemaObjectDeclaration>,
    ) : SchemaVariantDeclaration {
        override fun toString() = qualifiedName
    }
}

@JvmInline
private value class DeclarationKey private constructor(private val key: String) {
    constructor(kClass: KClass<*>) : this(checkNotNull(kClass.qualifiedName))
    constructor(pluginId: PluginData.Id, qualifiedName: SchemaName)
            : this("${pluginId.value}/${qualifiedName.qualifiedName}")
    constructor(pluginId: PluginData.Id, kClass: KClass<*>)
            : this("${pluginId.value}/${kClass.qualifiedName}")
}

private operator fun PluginData.Id.div(schemaName: SchemaName) = DeclarationKey(this, schemaName)

private class PluginSpecificTypingContext(
    val pluginId: PluginData.Id,
    declarations: PluginAwareSchemaTypingDeclarations,
) : DefaultSchemaTypingContext(declarations) {
    override fun getType(type: KType): SchemaType = when (type.classifier) {
        TaskAction::class -> SchemaType.VariantType(declarations.variants[pluginId / TaskActionSchemaName]!!)
        else -> super.getType(type)
    }

    override fun keyForClass(clazz: KClass<*>) = DeclarationKey(pluginId, clazz)
}

private open class DefaultSchemaTypingContext(
    protected val declarations: PluginAwareSchemaTypingDeclarations,
) : SchemaTypingContext {

    override fun getPluginContext(pluginId: PluginData.Id): SchemaTypingContext {
        return PluginSpecificTypingContext(pluginId, declarations)
    }

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
                    SchemaNode::class.java.isAssignableFrom(javaClass) -> when {
                        classifier.isSealed -> @Suppress("UNCHECKED_CAST") SchemaType.VariantType(
                            isMarkedNullable = type.isMarkedNullable,
                            declaration = variantDeclaration(classifier as KClass<out SchemaNode>),
                        )
                        else -> @Suppress("UNCHECKED_CAST") SchemaType.ObjectType(
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
        declarations.enums.computeIfAbsent(keyForClass(enumClass)) {
            BuiltinEnumDeclaration(enumClass)
        }

    private fun <T : SchemaNode> variantDeclaration(sealedClass: KClass<T>) =
        declarations.variants.computeIfAbsent(keyForClass(sealedClass)) {
            BuiltinVariantDeclaration(sealedClass)
        }

    private fun <T : SchemaNode> classDeclaration(clazz: KClass<T>) =
        declarations.classes.computeIfAbsent(keyForClass(clazz)) {
            BuiltinClassDeclaration(clazz)
        }

    protected open fun keyForClass(clazz: KClass<*>): DeclarationKey = DeclarationKey(clazz)

    private abstract class BuiltinTypeDeclarationBase(
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

        override fun toString() = "variant declaration `${qualifiedName}`"
    }

    private class BuiltinEnumDeclaration<T : Enum<T>>(
        private val backingReflectionClass: KClass<T>,
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

        override fun toEnumConstant(name: String): Any {
            return backingReflectionClass.java.enumConstants.first { it.name == name }
        }

        override fun toString() = "enum declaration `${qualifiedName}`"
    }

    private inner class BuiltinClassDeclaration<T : SchemaNode>(
        private val backingReflectionClass: KClass<T>,
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

        override fun createInstance(): SchemaNode = backingReflectionClass.createInstance()

        override fun toString() = "class declaration `${qualifiedName}`"
    }

    private fun customProperties(type: KClass<out SchemaNode>): List<SchemaObjectDeclaration.Property> = when (type) {
        Settings::class -> declarations.pluginData
            .map { plugin ->
                SchemaObjectDeclaration.Property(
                    name = plugin.id.value,
                    type = SchemaType.ObjectType(
                        declaration = checkNotNull(
                            declarations.classes[plugin.id / (plugin.moduleExtensionSchemaName ?: StubSchemaName)]
                        ),
                        isMarkedNullable = true,
                    ),
                    documentation = plugin.description,
                    isPlatformAgnostic = true,
                )
            }
        else -> emptyList()
    }
}

private val TaskActionSchemaName = SchemaName(TaskAction::class.qualifiedName!!)
private val StubSchemaName = SchemaName("Stub")