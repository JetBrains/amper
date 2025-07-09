/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.compilation.CompilationUserSettings
import org.jetbrains.amper.compilation.KotlinUserSettings
import org.jetbrains.amper.compilation.SCompilerPluginConfig
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.dependency.resolution.MavenRepository
import org.jetbrains.amper.frontend.api.UnstableSchemaApi
import org.jetbrains.amper.frontend.api.toStringRepresentation
import org.jetbrains.amper.frontend.schema.JavaVersion
import org.jetbrains.amper.frontend.schema.KotlinVersion
import org.jetbrains.amper.frontend.plugins.PluginDeclarationSchema
import org.jetbrains.amper.incrementalcache.executeForFiles
import org.jetbrains.amper.jdk.provisioning.JdkDownloader
import org.jetbrains.amper.ksp.Ksp
import org.jetbrains.amper.ksp.KspCompilationType
import org.jetbrains.amper.ksp.KspJvmConfig
import org.jetbrains.amper.ksp.KspOutputPaths
import org.jetbrains.amper.ksp.downloadKspJars
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.walk

internal class PluginPreparer(
    private val scope: CoroutineScope,
    private val userCacheRoot: AmperUserCacheRoot,
    private val projectRoot: AmperProjectRoot,
    private val tempRoot: AmperProjectTempRoot,
    private val projectLocalCacheDir: Path,
    private val schemaDir: Path,
) {
    // To make it build-directory agnostic
    val executeOnChangedInputs = ExecuteOnChangedInputs(AmperBuildOutputRoot(projectLocalCacheDir.createDirectories()))

    private val distributionRoot = Path(checkNotNull(System.getProperty("amper.dist.path")) {
        "Missing `amper.dist.path` system property. Ensure your wrapper script integrity."
    })

    private val outputs = mutableSetOf<Path>()

    private val jdk by lazy {
        scope.async(start = CoroutineStart.LAZY) {
            JdkDownloader.getJdk(userCacheRoot)
        }
    }

    private val ksp by lazy {
        scope.async(start = CoroutineStart.LAZY) {
            val kspVersion = UsedVersions.kspVersion
            Ksp(
                kspVersion = kspVersion,
                jdk = jdk.await(),
                kspImplJars = executeOnChangedInputs.executeForFiles(
                    id = "download-ksp-cli-for-schema-gen",
                    configuration = mapOf("version" to kspVersion),
                    inputs = emptyList(),
                ) {
                    MavenResolver(userCacheRoot).downloadKspJars(
                        kspVersion,
                        listOf(MavenRepository.Companion.MavenCentral)
                    )
                },
            )
        }
    }

    suspend fun prepareLocalPlugin(
        pluginModuleDir: Path,
        pluginInfo: PluginDeclarationSchema,
    ) {
        val sourceRoots = listOf(pluginModuleDir / "src")
        val output = schemaDir / "${pluginInfo.id}.json"
        executeOnChangedInputs.executeForFiles(
            id = "ksp-cli-for-schema-gen/${pluginInfo.id}",
            configuration = mapOf(
                "pluginInfo" to (@OptIn(UnstableSchemaApi::class) pluginInfo.toStringRepresentation()),
                "output" to output.pathString,
            ),
            inputs = sourceRoots,
        ) {
            output.deleteIfExists()
            val jdkHomeDir = jdk.await().homeDir
            logger.info("Processing local plugin schema for `${pluginInfo.id}`...")
            ksp.await().run(
                compilationType = KspCompilationType.JVM,
                processorClasspath = distributionRoot
                    .resolve("plugins-processor").listDirectoryEntries("*.jar"),
                config = KspJvmConfig(
                    kspOutputPaths = KspOutputPaths(
                        pluginModuleDir,
                        (projectLocalCacheDir / "ksp").createDirectories(),
                        resourcesDir = schemaDir.createDirectories(),
                        kotlinSourcesDir = schemaDir,
                        javaSourcesDir = schemaDir,
                        classesDir = schemaDir,
                    ),
                    compilationSettings = CompilationUserSettings(
                        kotlin = KotlinUserSettings(
                            languageVersion = KotlinVersion.Kotlin21,
                            apiVersion = KotlinVersion.Kotlin21,
                            allWarningsAsErrors = false,
                            suppressWarnings = false,
                            debug = null,
                            optimization = null,
                            verbose = true,
                            progressiveMode = false,
                            languageFeatures = emptyList<String>(),
                            optIns = emptyList<String>(),
                            storeJavaParameterNames = false,
                            freeCompilerArgs = emptyList<String>(),
                            compilerPlugins = emptyList<SCompilerPluginConfig>(),
                        ),
                        jvmRelease = JavaVersion.VERSION_17,
                    )
                ) {
                    moduleName = "schema"
                    this.sourceRoots = sourceRoots
                    // This is hardcoded here and is not part of the separate component, because
                    // it is expected to have no external dependencies.
                    libraries = distributionRoot
                        .resolve("extensibility-api").listDirectoryEntries("*.jar")
                    incremental = false
                    processorOptions = buildMap {
                        put("plugin.id", pluginInfo.id)
                        put("plugin.module.root", projectRoot.path.relativize(pluginModuleDir).pathString)
                        pluginInfo.description?.let {
                            put("plugin.description", it)
                        }
                        pluginInfo.schemaExtensionClassName?.let { name ->
                            put("plugin.schemaExtensionClassName", name)
                        }
                    }
                    jdkHome = jdkHomeDir
                },
                tempRoot = tempRoot,
            )

            listOf(output)
        }
        outputs.add(output)
    }

    fun cleanUnknownFiles() {
        for (path in schemaDir.walk()) {
            if (path !in outputs) {
                logger.debug("Deleting unknown/stale file {}", path)
                path.deleteExisting()
            }
        }
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}