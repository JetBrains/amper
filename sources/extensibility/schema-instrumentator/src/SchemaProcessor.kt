/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.intrumentation

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.jetbrains.amper.Schema
import org.jetbrains.amper.TaskAction
import org.jetbrains.amper.plugins.schema.model.PluginData

class SchemaProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {
    private var singleRound = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (singleRound) return emptyList()  // We do only a single round
        singleRound = true

        val pluginId = checkNotNull(environment.options["plugin.id"])
        val moduleExtensionSchemaName = environment.options["plugin.schemaExtensionClassName"]
        val description = environment.options["plugin.description"]
        val pluginModuleRoot = checkNotNull(environment.options["plugin.module.root"])

        val builder = SchemaBuilder(
            resolver = resolver,
            logger = environment.logger,
            moduleExtensionSchemaName = moduleExtensionSchemaName,
        )
        resolver.getSymbolsWithAnnotation(Schema::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()  // Enforced by annotation target
            .forEach { builder.addSchemaClass(it) }

        val tasks = resolver.getSymbolsWithAnnotation(TaskAction::class.qualifiedName!!)
            .filterIsInstance<KSFunctionDeclaration>()  // Enforced by annotation target
            .map { builder.addTask(it) }
            .filterNotNull()
            .toList()

        val plugin = PluginData(
            id = PluginData.Id(pluginId),
            enumTypes = builder.allEnums(),
            classTypes = builder.allSchemas(),
            tasks = tasks,
            moduleExtensionSchemaName = moduleExtensionSchemaName?.let(PluginData::SchemaName),
            description = description,
            pluginModuleRoot = pluginModuleRoot,
        )

        environment.codeGenerator.createNewFile(
            Dependencies.ALL_FILES,
            packageName = "",
            fileName = pluginId,
            extensionName = "json",
        ).buffered().use {
            @Suppress("JSON_FORMAT_REDUNDANT")
            @OptIn(ExperimentalSerializationApi::class)
            Json {
                prettyPrint = true
                encodeDefaults = false
            }.encodeToStream(plugin, it)
        }

        return emptyList()
    }
}