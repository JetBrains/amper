/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.intrumentation

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import org.jetbrains.amper.Input
import org.jetbrains.amper.Output
import org.jetbrains.amper.Schema
import org.jetbrains.amper.plugins.schema.model.PluginData

@OptIn(KspExperimental::class)
class SchemaBuilder(
    val resolver: Resolver,
    val logger: KSPLogger,
    val moduleExtensionSchemaName: String?,
) {
    private val enums = mutableMapOf<String, PluginData.EnumData>()
    private val classes = mutableMapOf<String, PluginData.ClassData?>()

    fun addSchemaClass(declaration: KSClassDeclaration): PluginData.Type.ObjectType? {
        require(declaration.isAnnotationPresent(Schema::class))
        val name = checkNotNull(declaration.qualifiedName).asString()
        return classes.getOrPut(name) { doBuildSchema(declaration) }?.let {
            PluginData.Type.ObjectType(schemaName = it.name, isNullable = false)
        }
    }

    fun addEnum(declaration: KSClassDeclaration): PluginData.Type.EnumType {
        require(declaration.classKind == ClassKind.ENUM_CLASS)
        val qualifiedName = checkNotNull(declaration.qualifiedName).asString()
        return enums.getOrPut(qualifiedName) {
            PluginData.EnumData(
                schemaName = PluginData.SchemaName(qualifiedName),
                entries = declaration.declarations
                    .filterIsInstance<KSClassDeclaration>()
                    .filter { it.classKind == ClassKind.ENUM_ENTRY }
                    .map {
                        PluginData.EnumData.Entry(
                            name = it.simpleName.asString(),
                            schemaName = it.simpleName.asString(),
                            doc = it.docString?.trim(),
                        )
                    }.toList()
            )
        }.let { PluginData.Type.EnumType(schemaName = it.schemaName, isNullable = false) }
    }

    fun addTask(declaration: KSFunctionDeclaration): PluginData.TaskInfo? {
        if (declaration.parentDeclaration != null) {
            return null.also { logger.error("Task action must be a top-level function", declaration) }
        }
        if (declaration.extensionReceiver != null) {
            return null.also { logger.error("Task actions can't be extension functions", declaration) }
        }
        if (Modifier.SUSPEND in declaration.modifiers) {
            return null.also { logger.error("Task actions can't be suspend functions", declaration) }
        }
        if (Modifier.INLINE in declaration.modifiers) {
            return null.also { logger.error("Task actions can't be inline functions", declaration) }
        }
        val qualifiedName = declaration.qualifiedName?.asString() ?: return null.also {
            logger.error("Task action must have a name", declaration)
        }

        val docLines = declaration.docString?.lines()
        val inputNames = mutableListOf<String>()
        val outputNames = mutableListOf<String>()
        val properties = mutableListOf<PluginData.ClassData.Property>()

        for (parameter in declaration.parameters) {
            val name = parameter.name?.asString()
            if (name == null) {
                logger.error("Malformed task parameter", parameter); continue
            }
            val type = parameter.type.resolveToSchemaType() ?: continue
            val isPath = type is PluginData.Type.PathType
            val hasInput = parameter.isAnnotationPresent(Input::class)
            val hasOutput = parameter.isAnnotationPresent(Output::class)
            if (!isPath && (hasInput || hasOutput)) {
                logger.error("@Input/@Output annotations are only applicable to `Path` parameters", parameter)
            }
            if (hasInput && hasOutput) {
                logger.error(
                    "Conflicting @Input + @Output annotations. " +
                            "File updates in-place are not supported. Use separate input/output instead.", parameter
                )
            }

            if (isPath && hasInput && !hasOutput) inputNames += name
            if (isPath && hasOutput && !hasInput) outputNames += name

            properties += PluginData.ClassData.Property(
                name = name,
                type = type,
                doc = docLines?.find { it.startsWith("@param ${parameter.name}") },
            )
        }

        return PluginData.TaskInfo(
            syntheticType = PluginData.ClassData(
                name = PluginData.SchemaName(qualifiedName),
                properties = properties,
                doc = declaration.docString,
            ),
            jvmFunctionClassName = checkNotNull(resolver.getOwnerJvmClassName(declaration)),
            jvmFunctionName = checkNotNull(resolver.getJvmName(declaration)),
            inputPropertyNames = inputNames,
            outputPropertyNames = outputNames,
        )
    }

    fun allSchemas(): List<PluginData.ClassData> = classes.values.filterNotNull()

    fun allEnums(): List<PluginData.EnumData> = enums.values.toList()

    private fun doBuildSchema(
        schemaSource: KSClassDeclaration,
    ): PluginData.ClassData? {
        val qualifiedName = checkNotNull(schemaSource.qualifiedName).asString()
        val isPrimarySchema = qualifiedName == moduleExtensionSchemaName

        if (schemaSource.classKind != ClassKind.INTERFACE) {
            return null.also { logger.error("Schema declaration must be an interface", schemaSource) }
        }

        if (schemaSource.typeParameters.isNotEmpty()) {
            return null.also { logger.error("Generic schema is not supported", schemaSource) }
        }

        val mixins = schemaSource.superTypes
            .filter { it.origin != Origin.SYNTHETIC }  // do not include implicit `kotlin.Any` class
            .mapNotNull { it.resolveToSchemaType() }  // nulls will be reported within
            .mapNotNull {
                (it as? PluginData.Type.ObjectType)?.resolve() ?: null.also {
                    logger.error("Schema can only have another schemas as superinterfaces", schemaSource)
                }
            }
            .toList()

        schemaSource.getDeclaredFunctions().forEach { forbidden ->
            logger.error("Function declarations are not permitted inside the schema", forbidden)
        }

        val inheritedProperties = mixins.flatMap { it.properties }

        fun filterAndReportProperty(property: KSPropertyDeclaration): Boolean {
            val name = property.simpleName.asString()
            if (inheritedProperties.any { it.name == name }) {
                logger.error("Overriding properties is not supported inside @SchemaExtension", property)
                return false
            }
            if (property.isMutable) {
                logger.error("Mutable properties are not supported inside @SchemaExtension", property)
                return false
            }
            if (property.extensionReceiver != null) {
                logger.error("Extension properties are not supported inside @SchemaExtension", property)
                return false
            }
            if (!property.isAbstract()) {
                logger.error(
                    "Default property implementations are not supported inside @SchemaExtension",
                    property
                )
                return false
            }
            if (isPrimarySchema && name == "enabled") {
                logger.error(
                    "`enabled` property name is reserved for the implicit property that is used to enable " +
                            "the plugin in a module. Use a different name if there is a need to enable some " +
                            "additional plugin functionality.", property
                )
                return false
            }

            return true
        }

        val properties = schemaSource.getDeclaredProperties().filter(::filterAndReportProperty).mapNotNull {
            PluginData.ClassData.Property(
                name = it.simpleName.asString(),
                type = it.type.resolveToSchemaType() ?: return@mapNotNull null,
                doc = it.docString?.trim(),
            )
        }

        return PluginData.ClassData(
            name = PluginData.SchemaName(qualifiedName),
            properties = inheritedProperties + properties,
            doc = schemaSource.docString,
        )
    }

    private fun PluginData.Type.ObjectType.resolve(): PluginData.ClassData {
        return checkNotNull(classes.values.find { it?.name == schemaName })
    }

    private fun KSTypeReference.resolveToSchemaType(): PluginData.Type? {
        val type = resolve()
        if (type.isError) {
            logger.error(
                "${parseErrorType(type)} is unresolved. " +
                        "Make sure it doesn't come from a dependency, because that is not currently supported. " +
                        "Schema types can only be declared inside the plugin code.", this
            )
            return null
        }

        val declaration = type.declarationTypeAliasAware()
        val declarationName = checkNotNull(declaration.qualifiedName).asString()  // if resolved, then must be available
        return when (declaration.classKind) {
            ClassKind.CLASS -> when (declarationName) {
                BOOLEAN_CLASS_NAME -> PluginData.Type.BooleanType(type.isMarkedNullable)
                STRING_CLASS_NAME -> PluginData.Type.StringType(type.isMarkedNullable)
                INT_CLASS_NAME -> PluginData.Type.IntType(type.isMarkedNullable)
                else -> null.also { logger.error("Unexpected type in the schema: `$declarationName`", this) }
            }
            ClassKind.INTERFACE -> when (declarationName) {
                PATH_CLASS_NAME -> PluginData.Type.PathType(type.isMarkedNullable)
                LIST_CLASS_NAME -> PluginData.Type.ListType(
                    elementType = type.arguments.firstOrNull()?.type?.resolveToSchemaType()
                        ?: return null, // TODO: report no argument
                    isNullable = type.isMarkedNullable,
                )
                MAP_CLASS_NAME -> PluginData.Type.MapType(
                    valueType = type.arguments.getOrNull(1)?.type?.resolveToSchemaType()
                        ?: return null,  // TODO: report no argument
                    isNullable = type.isMarkedNullable,
                )
                else -> if (declaration.isAnnotationPresent(Schema::class)) {
                    addSchemaClass(declaration)?.copy(isNullable = type.isMarkedNullable)
                } else null
            }
            ClassKind.ENUM_CLASS -> addEnum(declaration)
            else -> null.also { logger.error("Unexpected type in the schema: `$declarationName`", this) }
        }
    }
}

private const val BOOLEAN_CLASS_NAME = "kotlin.Boolean"
private const val STRING_CLASS_NAME = "kotlin.String"
private const val INT_CLASS_NAME = "kotlin.Int"
private const val PATH_CLASS_NAME = "java.nio.file.Path"
private const val LIST_CLASS_NAME = "kotlin.collections.List"
private const val MAP_CLASS_NAME = "kotlin.collections.Map"
