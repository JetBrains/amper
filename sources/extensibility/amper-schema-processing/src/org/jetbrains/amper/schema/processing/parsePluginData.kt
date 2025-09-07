/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import com.intellij.psi.PsiElement
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.plugins.schema.model.PluginDataRequest
import org.jetbrains.amper.plugins.schema.model.PluginDataResponse
import org.jetbrains.amper.plugins.schema.model.PluginDataResponse.DiagnosticKind.ErrorGeneric
import org.jetbrains.amper.stdlib.collections.distinctBy
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.psi
import org.jetbrains.kotlin.analysis.api.symbols.psiSafe
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile

internal interface SymbolsCollector {
    fun onEnumReferenced(symbol: KaClassSymbol)
}

/**
 * The entry point to the `amper-schema-processing` library.
 */
fun KaSession.parsePluginData(
    files: Collection<KtFile>,
    header: PluginDataRequest.PluginHeader,
): PluginDataResponse.PluginDataWithDiagnostics {
    val symbolsCollector = object : SymbolsCollector {
        val referencedEnumSymbols = mutableSetOf<KaClassSymbol>()
        override fun onEnumReferenced(symbol: KaClassSymbol) {
            require(symbol.classKind == KaClassKind.ENUM_CLASS)
            referencedEnumSymbols += symbol
        }
    }
    val diagnosticCollector = object : DiagnosticsReporter {
        val diagnostics = mutableListOf<PluginDataResponse.Diagnostic>()
        override fun report(
            diagnostic: PluginDataResponse.Diagnostic,
        ) {
            diagnostics += diagnostic
        }
    }

    val classes = files.flatMap {
        discoverAnnotatedClassesFrom(it, SCHEMA_ANNOTATION_CLASS)
    }.mapNotNull {
        context(symbolsCollector, diagnosticCollector) {
            parseSchemaDeclaration(it, primarySchemaFqnString = header.moduleExtensionSchemaName)
        }
    }

    val tasks = context(diagnosticCollector, symbolsCollector) {
        files.flatMap {
            discoverAnnotatedFunctionsFrom(it, TASK_ACTION_ANNOTATION_CLASS)
        }.mapNotNull {
            parseTaskAction(it)
        }.distinctBy(
            selector = { it.syntheticType.name.qualifiedName },
            onDuplicates = { name, taskInfos ->
                taskInfos.forEach {
                    report(
                        it.syntheticType.origin, "schema.forbidden.task.action.overloads", name,
                        kind = ErrorGeneric,
                    )
                }
            }
        )
    }

    val enums = symbolsCollector.referencedEnumSymbols.filter {
        // Otherwise it's a library (API) enum, we don't touch those here.
        it.origin == KaSymbolOrigin.SOURCE
    }.map { symbol ->
        PluginData.EnumData(
            schemaName = checkNotNull(symbol.classId) { "not reachable: enum" }.toSchemaName(),
            entries = symbol.staticDeclaredMemberScope.callables.filterIsInstance<KaEnumEntrySymbol>().map {
                PluginData.EnumData.Entry(
                    name = it.name.asString(),
                    schemaName = it.name.asString(),
                    doc = it.psiSafe<KtDeclaration>()?.getDefaultDocString(),
                    origin = it.psi<PsiElement>().getSourceLocation(),
                )
            }.toList(),
            origin = symbol.psi<PsiElement>().getSourceLocation(),
        )
    }

    return PluginDataResponse.PluginDataWithDiagnostics(
        pluginData = PluginData(
            id = header.pluginId,
            moduleExtensionSchemaName = header.moduleExtensionSchemaName,
            description = header.description,
            enumTypes = enums,
            classTypes = classes,
            tasks = tasks,
        ),
        diagnostics = diagnosticCollector.diagnostics,
    )
}
