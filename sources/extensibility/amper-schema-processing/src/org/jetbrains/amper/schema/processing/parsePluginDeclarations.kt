/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import com.intellij.psi.util.elementType
import kotlinx.serialization.json.Json
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.plugins.schema.model.PluginDataResponse
import org.jetbrains.amper.plugins.schema.model.PluginDataResponse.DiagnosticKind.ErrorGeneric
import org.jetbrains.amper.plugins.schema.model.plus
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

internal interface SymbolsCollector {
    fun onEnumReferenced(symbol: KaClassSymbol)
    fun onClassReferenced(symbol: KaClassSymbol)
}

class ParsingOptions(
    val isParsingAmperApi: Boolean,
)

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
    moduleExtensionSchemaName: String? = null,
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

    val classes = mutableListOf<PluginData.ClassData>()
    val sealedClasses = mutableListOf<PluginData.VariantData>()

    // We need to introduce a queue because this routine may be called on the subset of files.
    // and some declarations may reference things from other unmentioned files.
    val classesQueue = arrayListOf<KtClassOrObject>()
    files.forEach { file ->
        classesQueue.addAll(discoverAnnotatedClassesFrom(file, SCHEMA_ANNOTATION_CLASS))
    }

    val symbolsCollector = object : SymbolsCollector {
        val referencedEnumSymbols = mutableSetOf<KaClassSymbol>()
        override fun onEnumReferenced(symbol: KaClassSymbol) {
            require(symbol.classKind == KaClassKind.ENUM_CLASS)
            if (symbol.origin != KaSymbolOrigin.SOURCE) return  // skip builtin enums
            referencedEnumSymbols += symbol
        }
        override fun onClassReferenced(symbol: KaClassSymbol) {
            require(symbol.isAnnotatedWith(SCHEMA_ANNOTATION_CLASS))
            if (symbol.origin != KaSymbolOrigin.SOURCE) return  // skip builtin declarations
            classesQueue += symbol.psi<KtClassOrObject>()
        }
    }

    val seenDeclarations = hashSetOf<KtClassOrObject>()
    while (classesQueue.isNotEmpty()) {
        val declaration = classesQueue.removeLast()
        if (!seenDeclarations.add(declaration))
            continue

        context(symbolsCollector, diagnosticCollector, options) {
            val modalityType = declaration.modalityModifier()
            if (modalityType != null && modalityType.elementType == KtTokens.SEALED_KEYWORD) {
                if (options.isParsingAmperApi) {
                    sealedClasses += parseVariantDeclaration(declaration)
                } else {
                    reportError(modalityType, "schema.forbidden.sealed")
                }
            } else {
                parseSchemaDeclaration(declaration, primarySchemaFqnString = moduleExtensionSchemaName)
                    ?.let { classes += it }
            }
        }
    }

    val parsedClassesResolver = DeclarationsResolver(
        declarations = PluginData.Declarations(
            classes = classes,
            variants = sealedClasses,
        ) + builtinDeclarations,
    )

    val tasks = context(diagnosticCollector, symbolsCollector, parsedClassesResolver, options) {
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

    val enums = symbolsCollector.referencedEnumSymbols.map {
        parseEnum(it)
    }

    return PluginData.Declarations(
        enums = enums,
        classes = classes,
        tasks = tasks,
        variants = sealedClasses,
    )
}
