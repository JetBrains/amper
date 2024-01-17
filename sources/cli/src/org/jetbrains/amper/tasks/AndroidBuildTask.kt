package org.jetbrains.amper.tasks

import AndroidBuildRequest
import AndroidModuleData
import ApkPathAndroidBuildResult
import ResolvedDependency
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.toAndroidRequestBuildType
import org.slf4j.LoggerFactory
import runAndroidBuild
import java.nio.file.Path

class AndroidBuildTask(
    private val module: PotatoModule,
    private val buildType: BuildType,
    override val taskName: TaskName
) : Task {
    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): org.jetbrains.amper.tasks.TaskResult {
        val rootPath =
            (module.source as? PotatoModuleFileSource)?.buildFile?.parent ?: error("No build file ${module.source}")
        val classes = dependenciesResult.filterIsInstance<JvmCompileTask.TaskResult>()
            .firstNotNullOfOrNull { it.classesOutputRoot }?.parent ?: error("No build classes")
        val resolvedAndroidRuntimeDependencies =
            dependenciesResult.filterIsInstance<ResolveExternalDependenciesTask.TaskResult>().flatMap { it.classpath }
        val androidModuleData = AndroidModuleData(":", classes, resolvedAndroidRuntimeDependencies.map {
            ResolvedDependency("group", "artifact", "version", it)
        })
        val request = AndroidBuildRequest(
            rootPath,
            AndroidBuildRequest.Phase.Build,
            setOf(androidModuleData),
            setOf(buildType.toAndroidRequestBuildType)
        )
        val result = runAndroidBuild<ApkPathAndroidBuildResult>(
            request, sourcesPath = Path.of("../../").toAbsolutePath().normalize()
        )

        logger.info("ANDROID ARTIFACTS: ${result.paths}")
        return TaskResult(dependenciesResult, result.paths.map { Path.of(it) })
    }

    class TaskResult(
        override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
        val artifacts: List<Path>,
    ) : org.jetbrains.amper.tasks.TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}