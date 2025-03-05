/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.jar.JarConfig
import org.jetbrains.amper.jar.ZipInput
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists

/**
 * Creates a jar file containing all the main sources of the given [module] that are relevant to the given [platform].
 */
class SourcesJarTask(
    override val taskName: TaskName,
    private val module: AmperModule,
    private val platform: Platform,
    private val taskOutputRoot: TaskOutputRoot,
    executeOnChangedInputs: ExecuteOnChangedInputs,
) : AbstractJarTask(taskName, executeOnChangedInputs) {

    override suspend fun getInputDirs(dependenciesResult: List<TaskResult>): List<ZipInput> =
        module.fragments
            .asSequence()
            .filter { !it.isTest && platform in it.platforms }
            .sortedBy { it.name }
            // To match current KMP publications, sources for common should be in "/commonMain", jvm in "/jvmMain" etc.
            // TODO check whether this is necessary, or if using the src directory name would be understood by IDEs
            // TODO: Migrate to KotlinJavaSourcesDirArtifact
            .map { ZipInput(path = it.src, destPathInArchive = Path("${it.name}Main")) }
            .filter { it.path.exists() }
            .toList()

    // Matches the current format of KMP jar publications from the KMP Gradle plugin (except for the version)
    // TODO add version here?
    override fun outputJarPath(): Path = taskOutputRoot.path / "${module.userReadableName}-${platform.schemaValue.lowercase()}-sources.jar"

    override fun jarConfig(): JarConfig = JarConfig()

    override fun createResult(jarPath: Path): AbstractJarTask.Result =
        Result(jarPath)

    class Result(jarPath: Path) : AbstractJarTask.Result(jarPath)
}
