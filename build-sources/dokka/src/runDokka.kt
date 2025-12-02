/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.dokka

import kotlinx.serialization.json.Json
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.ModuleSources
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.pathString
import kotlin.io.path.writeText

@OptIn(ExperimentalPathApi::class)
@TaskAction
fun runDokka(
    moduleName: String,
    @Input settings: DokkaSettings,
    @Input moduleCompileClasspath: Classpath,
    @Input runtimeClasspath: Classpath,
    @Input builtinPluginsClasspath: Classpath,
    @Input sources: ModuleSources,
    @Output outputDir: Path,
) {
    val html = settings.html
    val siteDir = outputDir / "site"

    val configuration = DokkaConfiguration(
        moduleName = moduleName,
        outputDir = siteDir.pathString,
        sourceSets = listOf(
            DokkaSourceSet(
                sourceSetID = SourceSetID(
                    scopeId = moduleName,
                    sourceSetName = "main",
                ),
                displayName = moduleName,
                classpath = moduleCompileClasspath.resolvedFiles
                    .map { it.pathString },
                sourceRoots = sources.sourceDirectories.map { it.pathString },
                jdkVersion = 25, // TODO: Allow passing when module data can be referenced.
                documentedVisibilities = settings.documentedVisibilities,
                analysisPlatform = "jvm",
            )
        ),
        pluginsClasspath = builtinPluginsClasspath.resolvedFiles.map { it.pathString },
        pluginsConfiguration = listOf(
            PluginConfiguration(
                fqPluginName = "org.jetbrains.dokka.base.DokkaBase",
                serializationFormat = "JSON",
                values = Json.encodeToString(DokkaBase(
                    customAssets = html.customAssets?.map { it.pathString },
                    customStyleSheets = html.customStyleSheets?.map { it.pathString },
                    templatesDir = html.templatesDir?.pathString,
                    footerMessage = html.footerMessage,
                ))
            )
        )
    )

    outputDir.deleteRecursively()
    outputDir.createDirectories()

    val configurationFile = outputDir / "dokka-configuration.json"
    configurationFile.writeText(Json.encodeToString(configuration))

    val commandLine = listOf(
        ProcessHandle.current().info().command().orElse("java"),
        "-jar",
        runtimeClasspath.resolvedFiles.single().pathString,
        configurationFile.pathString,
    )

    // FIXME: AMPER-4912 Runtime API: Process launching facility for tasks
    val process = ProcessBuilder(commandLine).start()
    val capture = listOf(
        thread { process.inputStream.copyTo(System.out) },
        thread { process.errorStream.copyTo(System.err) },
    )
    val exitCode = process.waitFor()
    for (thread in capture) thread.join()

    check(exitCode == 0) {
        "Dokka terminated with code = $exitCode. See the log above for details."
    }
}