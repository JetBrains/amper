package org.jetbrains.amper.tasks

import AndroidBuildRequest
import AndroidModuleData
import ApkPathAndroidBuildResult
import ResolvedDependency
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.repr
import org.jetbrains.amper.util.toAndroidRequestBuildType
import org.slf4j.LoggerFactory
import runAndroidBuild
import java.nio.file.Path

class AndroidBuildTask(
    private val module: PotatoModule,
    private val buildType: BuildType,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    private val fragments: List<Fragment>,
    override val taskName: TaskName
) : Task {
    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): org.jetbrains.amper.tasks.TaskResult {
        val rootPath =
            (module.source as? PotatoModuleFileSource)?.buildFile?.parent ?: error("No build file ${module.source}")
        val classes = dependenciesResult.filterIsInstance<JvmCompileTask.TaskResult>()
            .firstNotNullOfOrNull { it.classesOutputRoot } ?: error("No build classes")
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
        val inputs = listOf(classes) + resolvedAndroidRuntimeDependencies
        val androidConfig = fragments.joinToString { it.settings.android.repr }
        val configuration = mapOf("androidConfig" to androidConfig)
        val executionResult = executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            val result = runAndroidBuild<ApkPathAndroidBuildResult>(
                request, sourcesPath = Path.of("../../").toAbsolutePath().normalize()
            )
            ExecuteOnChangedInputs.ExecutionResult(result.paths.map { Path.of(it) }, mapOf())
        }
        logger.info("ANDROID ARTIFACTS: ${executionResult.outputs}")
        return TaskResult(dependenciesResult, executionResult.outputs)
    }

    class TaskResult(
        override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
        val artifacts: List<Path>,
    ) : org.jetbrains.amper.tasks.TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}