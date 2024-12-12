/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.jar.ZipInput
import org.jetbrains.amper.jar.writeZip
import org.jetbrains.amper.tasks.BuildTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.jvm.JvmClassesJarTask
import org.jetbrains.amper.tasks.jvm.RuntimeClasspathElementProvider
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.math.absoluteValue

/**
 * Creates an AAR (Android Archive) to be consumed later by the AGP in the Gradle build.
 * Can contain various things from classes, java resources, android assets, android resources, native libraries, etc.
 * See [the AAR spec](https://developer.android.com/studio/projects/android-library#aar-contents) for extensive info.
 *
 * We use AAR only as an implementation detail to supply compiled classes and prepared assets to the AGP for the final
 * application build stage.
 *
 * AAR is generally the way to go for library publishing; however, currently we do not support that.
 *
 * **Inputs**:
 * - [JvmClassesJarTask.Result]
 * - [AdditionalAndroidAssetsProvider]*
 *
 * **Output**: [RuntimeClasspathElementProvider]
 */
class AndroidAarTask(
    override val taskName: TaskName,
    override val module: AmperModule,
    override val buildType: BuildType,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    private val taskOutputRoot: TaskOutputRoot,
    private val tempRoot: AmperProjectTempRoot,
) : BuildTask {
    override val isTest: Boolean
        get() = false

    override val platform: Platform
        get() = Platform.ANDROID

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val jarResult = dependenciesResult.filterIsInstance<JvmClassesJarTask.Result>().singleOrNull()
            ?: error("No input classes jar")

        // TODO: Implement picking up actual user-provided assets?
        // TODO: Implement overriding (refining) assets?
        val additionalAssets = dependenciesResult.filterIsInstance<AdditionalAndroidAssetsProvider>()

        val outputAarPath = taskOutputRoot.path / jarResult.jarPath.nameWithoutExtension.plus(".aar")

        val inputs = buildList {
            additionalAssets.forEach { result -> result.assetsRoots.forEach { add(it.path) } }
            add(jarResult.jarPath)
        }
        val configuration = mapOf(
            "outputPath" to outputAarPath.pathString,
            "requiredPackagingDirs" to Json.encodeToString(
                additionalAssets.map { result -> result.assetsRoots.map { it.path.pathString } }
            ),
        )
        executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            outputAarPath.deleteIfExists()
            outputAarPath.createParentDirectories()

            val tempDir = createTempDirectory(tempRoot.path)
            try {
                val manifestStubFile = tempDir / "AndroidManifest.xml"
                // TODO: Use a user-provided Manifest if any? Require it? Require `namespace` to be set for libraries?
                manifestStubFile.writeText(
                    """<?xml version="1.0" encoding="utf-8"?>
                        |<manifest package="${internalPackageNameFor(module)}" />""".trimMargin()
                )
                val assetsDir = Path("assets")
                outputAarPath.writeZip(
                    inputs = buildList {
                        this += ZipInput(
                            path = jarResult.jarPath,
                            destPathInArchive = Path("libs"),
                        )
                        this += ZipInput(
                            path = manifestStubFile,
                            destPathInArchive = Path("."),
                        )
                        additionalAssets.forEach { asset ->
                            asset.assetsRoots.forEach { assetsRoot ->
                                this += ZipInput(
                                    path = assetsRoot.path,
                                    destPathInArchive = assetsDir / assetsRoot.relativePackagingPath,
                                )
                            }
                        }
                    }
                )
            } finally {
                tempDir.deleteRecursively()
            }
            ExecuteOnChangedInputs.ExecutionResult(
                outputs = listOf(outputAarPath),
            )
        }

        return Result(
            aarPath = outputAarPath,
        )
    }

    class Result(
        val aarPath: Path,
    ) : TaskResult, RuntimeClasspathElementProvider {
        override val paths: List<Path>
            get() = listOf(aarPath)
    }
}

// Meh, but does the trick, as AARs are purely implementation detail for now.
private fun internalPackageNameFor(module: AmperModule): String {
    // The main purpose is to be unique for the module and respect android package name rules.
    return "org.jetbrains.amper.internal.p${module.userReadableName.hashCode().absoluteValue}"
}
