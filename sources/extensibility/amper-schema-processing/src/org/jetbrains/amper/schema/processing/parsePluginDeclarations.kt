/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import com.intellij.psi.util.elementType
import kotlinx.serialization.json.Json
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.plugins.schema.model.PluginDataResponse
import org.jetbrains.amper.plugins.schema.model.PluginDataResponse.DiagnosticKind.ErrorGeneric
import org.jetbrains.amper.stdlib.collections.distinctBy
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.psi
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.modalityModifier
import kotlin.context

internal fun loadSerializedBuiltinDeclarations(): String = ParsingOptions::class.java.classLoader
    .getResourceAsStream("META-INF/amper/extensibility-api-declarations.json")
    .use {
        checkNotNull(it) { "Failed to load extensibility-api-declarations.json" }
        it.bufferedReader().readText()
    }

internal fun loadBuiltinDeclarations(): PluginData.Declarations =
    Json.decodeFromString(loadSerializedBuiltinDeclarations())

/**
 * The entry point to the `amper-schema-processing` library.
 */
fun KaSession.parsePluginDeclarations(
    files: Collection<KtFile>,
    diagnostics: MutableList<in PluginDataResponse.Diagnostic>,
    pluginSettingsClassName: String? = null,
    isParsingAmperApi: Boolean = false,
): PluginData.Declarations {
    val options = ParsingOptions(
        isParsingAmperApi = isParsingAmperApi,
    )
    val builtinDeclarations = if (!options.isParsingAmperApi) {
        loadBuiltinDeclarations()
    } else PluginData.Declarations()
    val diagnosticCollector = object : DiagnosticsReporter {
        override fun report(diagnostic: PluginDataResponse.Diagnostic) {
            diagnostics += diagnostic
        }
    }

    val resolver = object : SymbolsCollector, DeclarationsProvider {
        // Need a separate hash-set for tracking seen name to prevent infinite recursive resolution
        private val seenNames = hashSetOf<PluginData.SchemaName>()

        private val enums = mutableMapOf<PluginData.SchemaName, PluginData.EnumData>()
        private val classes = mutableMapOf<PluginData.SchemaName, PluginData.ClassData>()
        private val variants = mutableMapOf<PluginData.SchemaName, PluginData.VariantData>()

        fun resolvedEnums() = enums.values.sortedBy { it.schemaName }
        fun resolvedClasses() = classes.values.sortedBy { it.name }
        fun resolvedVariants() = variants.values.sortedBy { it.name }

        init {
            builtinDeclarations.enums.forEach { seenNames += it.schemaName }
            builtinDeclarations.classes.forEach { seenNames += it.name }
            builtinDeclarations.variants.forEach { seenNames += it.name }
        }

        override fun onEnumReferenced(symbol: KaClassSymbol, name: PluginData.SchemaName) {
            require(symbol.classKind == KaClassKind.ENUM_CLASS)
            if (!seenNames.add(name)) return
            check(symbol.origin == KaSymbolOrigin.SOURCE) { "Non-source declarations must've been preprocessed: $name" }

            enums[name] = parseEnum(symbol)
        }

        override fun onClassReferenced(symbol: KaClassSymbol, name: PluginData.SchemaName) {
            require(symbol.isAnnotatedWith(CONFIGURABLE_ANNOTATION_CLASS))
            if (!seenNames.add(name)) return
            check(symbol.origin == KaSymbolOrigin.SOURCE) { "Non-source declarations must've been preprocessed: $name" }

            val declaration = symbol.psi<KtClassOrObject>()
            val modalityType = declaration.modalityModifier()
            context(diagnosticCollector, options) {
                if (modalityType?.elementType == KtTokens.SEALED_KEYWORD) {
                    if (name in variants) return
                    check(symbol.origin == KaSymbolOrigin.SOURCE)
                    if (options.isParsingAmperApi) {
                        variants[name] = parseVariantDeclaration(declaration)
                    } else {
                        reportError(modalityType, "schema.forbidden.sealed")
                    }
                } else {
                    if (name in classes) return
                    check(symbol.origin == KaSymbolOrigin.SOURCE)
                    classes[name] = parseSchemaDeclaration(
                        schemaDeclaration = declaration,
                        name = name,
                        primaryConfigurableFqnString = pluginSettingsClassName,
                    ) ?: PluginData.ClassData(name) // Empty stub for invalid
                }
            }
        }

        override fun declarationFor(type: PluginData.Type.ObjectType): PluginData.ClassData {
            return classes[type.schemaName]
                ?: builtinDeclarations.classes.find { it.name == type.schemaName }
                ?: error("not reached: $type is created, but was not resolved")
        }

        override fun declarationFor(type: PluginData.Type.VariantType): PluginData.VariantData {
            return variants[type.schemaName]
                ?: builtinDeclarations.variants.find { it.name == type.schemaName }
                ?: error("not reached: $type is created, but was not resolved")
        }
    }

    for (file in files) {
        for (declaration in discoverAnnotatedClassesFrom(file, CONFIGURABLE_ANNOTATION_CLASS)) {
            val symbol = declaration.classSymbol
            val name = symbol?.classId?.toSchemaName() ?: continue  // invalid Kotlin
            resolver.onClassReferenced(symbol, name)
        }
    }

    val tasks = context(diagnosticCollector, resolver, options) {
        files.flatMap {
            discoverAnnotatedFunctionsFrom(it, TASK_ACTION_ANNOTATION_CLASS)
        }.mapNotNull {
            parseTaskAction(it)
        }.distinctBy(
            selector = { it.syntheticType.name.qualifiedName },
            onDuplicates = { name, taskInfos ->
                taskInfos.forEach {
                    report(
                        it.syntheticType.origin!!, "schema.forbidden.task.action.overloads", name,
                        kind = ErrorGeneric,
                    )
                }
            }
        )
    }

    return PluginData.Declarations(
        enums = resolver.resolvedEnums(),
        classes = resolver.resolvedClasses(),
        variants = resolver.resolvedVariants(),
        tasks = tasks.sortedBy { it.syntheticType.name },
    )
}
