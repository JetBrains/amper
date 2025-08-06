/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

import com.intellij.openapi.vfs.findDocument
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.amper.cli.CliProblemReporter
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.api.UnstableSchemaApi
import org.jetbrains.amper.frontend.api.toStringRepresentation
import org.jetbrains.amper.frontend.getLineAndColumnRangeInDocument
import org.jetbrains.amper.frontend.plugins.PluginDeclarationSchema
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.incrementalcache.executeForFiles
import org.jetbrains.amper.jdk.provisioning.JdkDownloader
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.plugins.schema.model.PluginDataRequest
import org.jetbrains.amper.plugins.schema.model.PluginDataResponse
import org.jetbrains.amper.problems.reporting.BuildProblem
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import org.jetbrains.amper.problems.reporting.FileWithRangesBuildProblemSource
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.LineAndColumn
import org.jetbrains.amper.problems.reporting.LineAndColumnRange
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
import kotlin.io.path.writeText

internal suspend fun doPreparePlugins(
    userCacheRoot: AmperUserCacheRoot,
    frontendPathResolver: FrontendPathResolver,
    executeOnChangedInputs: ExecuteOnChangedInputs,
    schemaDir: Path,
    plugins: Map<Path, PluginDeclarationSchema>,
) {
    require(plugins.isNotEmpty())
    val distributionRoot = Path(checkNotNull(System.getProperty("amper.dist.path")) {
        "Missing `amper.dist.path` system property. Ensure your wrapper script integrity."
    })

    executeOnChangedInputs.executeForFiles(
        id = "prepare-plugins",
        configuration = mapOf(
            "plugins" to plugins.values.joinToString { @OptIn(UnstableSchemaApi::class) it.toStringRepresentation() }
        ),
        inputs = plugins.keys.toList(),
    ) {
        val jdk = JdkDownloader.getJdk(userCacheRoot)
        val toolClasspath = distributionRoot.resolve("plugins-processor").listDirectoryEntries("*.jar")
        val apiClasspath = distributionRoot.resolve("extensibility-api").listDirectoryEntries("*.jar")
        val outputCaptor = ProcessOutputListener.InMemoryCapture()
        val request = PluginDataRequest(
            jdkPath = jdk.homeDir,
            librariesPaths = apiClasspath,
            plugins = plugins.map { (pluginRootPath, pluginInfo) ->
                PluginDataRequest.PluginHeader(
                    pluginId = PluginData.Id(pluginInfo.id),
                    sourceDir = pluginRootPath / "src",
                    moduleExtensionSchemaName = pluginInfo.schemaExtensionClassName?.let(PluginData::SchemaName),
                    description = pluginInfo.description,
                )
            }
        )
        logger.info("Processing local plugin schema for [${plugins.values.joinToString { it.id }}]...")
        cleanDirectory(schemaDir)
        jdk.runJava(
            workingDir = Path("."),
            mainClass = "org.jetbrains.amper.schema.processing.MainKt",
            programArgs = emptyList(),
            argsMode = ArgsMode.CommandLine,
            classpath = toolClasspath,
            outputListener = outputCaptor,
            // Input request is passed via STDIN
            input = ProcessInput.Text(Json.encodeToString(request))
        )
        // Results are parsed from the process' STDOUT
        val results = try {
            Json.decodeFromString<PluginDataResponse>(outputCaptor.stdout).results
        } catch (e: SerializationException) {
            logger.error(outputCaptor.stderr)
            throw e
        }

        val reporter = CollectingProblemReporter()
        results.flatMap { it.errors }.forEach { error ->
            val document = frontendPathResolver.loadVirtualFileOrNull(error.filePath)?.findDocument()
            reporter.reportMessage(
                SchemaError(
                    error = error,
                    range = document?.let { getLineAndColumnRangeInDocument(it, error.textRange) },
                )
            )
        }

        results.forEach { result ->
            val pluginDataPath = schemaDir / "${result.pluginData.id.value}.json"
            pluginDataPath.writeText(Json.encodeToString(result.pluginData))
        }

        reporter.replayProblemsTo(CliProblemReporter)
        if (reporter.problems.isNotEmpty()) {
            userReadableError("Local plugins pre-processing failed, see the errors above.")
        }

        listOf(schemaDir)
    }
}

private class SchemaError(
    error: PluginDataResponse.Error,
    range: LineAndColumnRange?,
) : BuildProblem {
    override val buildProblemId = error.diagnosticId
    override val source = object : FileWithRangesBuildProblemSource {
        override val range = range ?: LineAndColumnRange(LineAndColumn.NONE, LineAndColumn.NONE)
        override val offsetRange = error.textRange
        override val file = error.filePath
    }
    override val message = error.message
    override val level = Level.Error
    override val type = BuildProblemType.Generic
}

private val logger = LoggerFactory.getLogger("preparePlugins")