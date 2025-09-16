/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.jetbrains.amper.plugins.schema.model.PluginDataResponse
import org.jetbrains.amper.plugins.schema.model.PluginDeclarationsRequest
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtLibraryModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSdkModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.system.exitProcess

@OptIn(ExperimentalSerializationApi::class)
fun main() {
    val request = Json.decodeFromStream<PluginDeclarationsRequest>(System.`in`)

    val disposable = Disposer.newDisposable()
    try {
        val results = runSchemaProcessor(disposable, request)
        Json.encodeToStream(PluginDataResponse(results = results), System.out)
    } catch (e: Throwable) {
        e.printStackTrace()
        exitProcess(1)
    } finally {
        Disposer.dispose(disposable)

        // exitProcess is necessary because of lingering IDEA-related non-daemon threads.
        exitProcess(0)
    }
}

fun runSchemaProcessor(
    request: PluginDeclarationsRequest,
): List<PluginDataResponse.PluginDataWithDiagnostics> {
    val disposable = Disposer.newDisposable()
    return try {
        runSchemaProcessor(disposable, request)
    } finally {
        Disposer.dispose(disposable)
    }
}

internal fun runSchemaProcessor(
    disposable: Disposable,
    request: PluginDeclarationsRequest,
): List<PluginDataResponse.PluginDataWithDiagnostics> {
    val modules = mutableMapOf<Path, KaSourceModule>()
    val session = buildStandaloneAnalysisAPISession(disposable) {
        val myPlatform = JvmPlatforms.defaultJvmPlatform
        buildKtModuleProvider {
            platform = myPlatform
            val jdkModule = buildKtSdkModule {
                platform = myPlatform
                libraryName = "[JDK]"
                addBinaryRootsFromJdkHome(request.jdkPath, isJre = false)
            }
            val libraryModules = request.librariesPaths.map { libraryPath ->
                buildKtLibraryModule {
                    platform = myPlatform
                    libraryName = libraryPath.nameWithoutExtension
                    addBinaryRoot(libraryPath)
                }
            }
            request.requests.forEach { pluginHeader ->
                val module = buildKtSourceModule {
                    platform = myPlatform
                    moduleName = pluginHeader.moduleName
                    addSourceRoot(pluginHeader.sourceDir)
                    addRegularDependency(jdkModule)
                    libraryModules.forEach(::addRegularDependency)
                }
                modules[pluginHeader.sourceDir] = module
                addModule(module)
            }
        }
    }

    val results = modules.map { (sourceDir, module) ->
        analyze(module) {
            val request = request.requests.first { it.sourceDir == sourceDir }
            val diagnostics = mutableListOf<PluginDataResponse.Diagnostic>()
            val declarations = parsePluginDeclarations(
                files = session.modulesWithFiles[module]?.filterIsInstance<KtFile>().orEmpty(),
                diagnostics = diagnostics,
                isParsingAmperApi = request.isParsingAmperApi,
                moduleExtensionSchemaName = request.moduleExtensionSchemaName,
            )
            PluginDataResponse.PluginDataWithDiagnostics(
                sourcePath = request.sourceDir,
                declarations = declarations,
                diagnostics = diagnostics,
            )
        }
    }
    return results
}
