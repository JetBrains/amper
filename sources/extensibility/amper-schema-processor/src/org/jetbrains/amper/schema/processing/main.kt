/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import com.intellij.openapi.util.Disposer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.plugins.schema.model.PluginDataRequest
import org.jetbrains.amper.plugins.schema.model.PluginDataResponse
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtLibraryModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSdkModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import kotlin.io.path.nameWithoutExtension
import kotlin.system.exitProcess

fun main() {
    @OptIn(ExperimentalSerializationApi::class)
    val request = Json.decodeFromStream<PluginDataRequest>(System.`in`)

    val disposable = Disposer.newDisposable()
    val results = try {
        val modules = mutableMapOf<PluginData.Id, KaSourceModule>()
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
                request.plugins.forEach { pluginHeader ->
                    val module = buildKtSourceModule {
                        platform = myPlatform
                        moduleName = pluginHeader.pluginId.value
                        addSourceRoot(pluginHeader.sourceDir)
                        addRegularDependency(jdkModule)
                        libraryModules.forEach(::addRegularDependency)
                    }
                    modules[pluginHeader.pluginId] = module
                    addModule(module)
                }
            }
        }

        modules.map { (id, module) ->
            analyze(module) {
                parsePluginData(
                    files = session.modulesWithFiles[module]?.filterIsInstance<KtFile>().orEmpty(),
                    header = request.plugins.first { it.pluginId == id }
                )
            }
        }
    } finally {
        Disposer.dispose(disposable)
    }

    @OptIn(ExperimentalSerializationApi::class)
    Json.encodeToStream(PluginDataResponse(results = results), System.out)

    // exitProcess is necessary because of lingering IDEA-related non-daemon threads.
    exitProcess(0)
}
