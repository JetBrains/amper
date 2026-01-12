/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.amper.ProcessRunner
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.CliProblemReporter
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.frontend.messages.FileWithRangesBuildProblemSource
import org.jetbrains.amper.frontend.plugins.PluginManifest
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.jdk.provisioning.JdkProvider
import org.jetbrains.amper.jvm.getDefaultJdk
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.plugins.schema.model.PluginDataResponse
import org.jetbrains.amper.plugins.schema.model.PluginDataResponse.DiagnosticKind
import org.jetbrains.amper.plugins.schema.model.PluginDeclarationsRequest
import org.jetbrains.amper.problems.reporting.BuildProblem
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.replayProblemsTo
import org.jetbrains.amper.processes.ArgsMode
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.ProcessOutputListener
import org.jetbrains.amper.processes.runJava
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.relativeTo

internal suspend fun doPreparePlugins(
    projectRoot: AmperProjectRoot,
    jdkProvider: JdkProvider,
    incrementalCache: IncrementalCache,
    plugins: Map<Path, PluginManifest>,
    processRunner: ProcessRunner,
): List<PluginData> {
    require(plugins.isNotEmpty())
    val distributionRoot = Path(checkNotNull(System.getProperty("amper.dist.path")) {
        "Missing `amper.dist.path` system property. Ensure your wrapper script integrity."
    })

    val result = incrementalCache.execute(
        key = "prepare-plugins",
        inputValues = mapOf(
            "plugins" to plugins.values.joinToString()
        ),
        inputFiles = plugins.keys.toList(),
    ) {
        // TODO maybe force plugin modules to use a JDK aligned with Amper's runtime instead, and use it here
        val jdk = jdkProvider.getDefaultJdk()
        val toolClasspath = distributionRoot.resolve("plugins-processor").listDirectoryEntries("*.jar")
        val apiClasspath = distributionRoot.resolve("extensibility-api").listDirectoryEntries("*.jar")
        val outputCaptor = ProcessOutputListener.InMemoryCapture()
        val request = PluginDeclarationsRequest(
            jdkPath = jdk.homeDir,
            librariesPaths = apiClasspath,
            requests = plugins.map { (pluginRootPath, pluginInfo) ->
                PluginDeclarationsRequest.Request(
                    moduleName = pluginRootPath.relativeTo(projectRoot.path).joinToString(":"),
                    sourceDir = pluginRootPath / "src",
                    pluginSettingsClassName = pluginInfo.settingsClass,
                )
            }
        )
        logger.info("Processing local plugin schema for [${plugins.values.joinToString { it.id }}]...")
        val result = processRunner.runJava(
            jdk = jdk,
            workingDir = Path("."),
            mainClass = "org.jetbrains.amper.schema.processing.MainKt",
            programArgs = emptyList(),
            argsMode = ArgsMode.CommandLine,
            classpath = toolClasspath,
            outputListener = outputCaptor,
            // Input request is passed via STDIN
            input = ProcessInput.Text(Json.encodeToString(request))
        )
        if (result.exitCode != 0) {
            logger.error(outputCaptor.stderr)
            error("Failed to process local plugin schema")
        }
        // Results are parsed from the process' STDOUT
        val results = try {
            Json.decodeFromString<PluginDataResponse>(outputCaptor.stdout).results
        } catch (e: SerializationException) {
            logger.error(outputCaptor.stderr)
            throw e
        }

        val reporter = CollectingProblemReporter()
        results.flatMap { it.diagnostics }.forEach { diagnostic ->
            reporter.reportMessage(
                SchemaDiagnostic(diagnostic = diagnostic)
            )
        }

        val allPluginData = results.map { result ->
            val plugin = checkNotNull(plugins[result.sourcePath.parent]) {
                "Processing of ${result.sourcePath} requested, but no corresponding result is found"
            }
            PluginData(
                id = PluginData.Id(plugin.id),
                pluginSettingsSchemaName = result.declarations.classes.map { it.name }.find {
                    it.qualifiedName == plugin.settingsClass
                },
                description = plugin.description,
                declarations = result.declarations,
            )
        }

        reporter.replayProblemsTo(CliProblemReporter)
        if (reporter.problems.any { it.level.atLeastAsSevereAs(Level.Error) }) {
            userReadableError("Local plugins pre-processing failed, see the errors above.")
        }

        IncrementalCache.ExecutionResult(
            outputFiles = emptyList(),
            outputValues = mapOf(CACHE_OUTPUT_KEY to Json.encodeToString(allPluginData))
        )
    }
    return Json.decodeFromString(result.outputValues[CACHE_OUTPUT_KEY]!!)
}

private const val CACHE_OUTPUT_KEY = "pluginData"

private class SchemaDiagnostic(
    diagnostic: PluginDataResponse.Diagnostic,
) : BuildProblem {
    override val buildProblemId = diagnostic.diagnosticId
    override val source = FileWithRangesBuildProblemSource(diagnostic.location.path, diagnostic.location.textRange)
    override val message = diagnostic.message
    override val level = when(diagnostic.kind) {
        DiagnosticKind.ErrorGeneric,
        DiagnosticKind.ErrorUnresolvedLikeConstruct -> Level.Error
        DiagnosticKind.WarningRedundant -> Level.WeakWarning
    }
    override val type = when(diagnostic.kind) {
        DiagnosticKind.ErrorGeneric -> BuildProblemType.Generic
        DiagnosticKind.ErrorUnresolvedLikeConstruct -> BuildProblemType.UnknownSymbol
        DiagnosticKind.WarningRedundant -> BuildProblemType.RedundantDeclaration
    }
}

private val logger = LoggerFactory.getLogger("preparePlugins")