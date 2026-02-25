package build.kargo.tasks.native

import org.jetbrains.amper.ProcessRunner
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.jdk.provisioning.JdkProvider
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.native.NativeLinkTask
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories

internal class NativeLinkTask(
    override val module: AmperModule,
    override val platform: Platform,
    private val userCacheRoot: AmperUserCacheRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val incrementalCache: IncrementalCache,
    override val taskName: TaskName,
    private val tempRoot: AmperProjectTempRoot,
    override val isTest: Boolean,
    override val buildType: BuildType,
    val compilationType: KotlinCompilationType,
    val compileKLibTaskName: TaskName,
    val exportedKLibTaskNames: Set<TaskName>,
    private val jdkProvider: JdkProvider,
    private val processRunner: ProcessRunner,
) : BuildTask {

    val delegate = NativeLinkTask(
        module = module,
        platform = platform,
        userCacheRoot = userCacheRoot,
        taskOutputRoot = taskOutputRoot,
        incrementalCache = incrementalCache,
        tempRoot = tempRoot,
        isTest = isTest,
        buildType = buildType,
        compilationType = compilationType,
        compileKLibTaskName = compileKLibTaskName,
        exportedKLibTaskNames = exportedKLibTaskNames,
        jdkProvider = jdkProvider,
        processRunner = processRunner,
        taskName = taskName
    )

    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): TaskResult {
        // Run original Amper native linking
        val result = delegate.run(dependenciesResult, executionContext) as NativeLinkTask.Result
        val artifactPath = result.linkedBinary ?: return result

        // In Kargo, we support an 'output' configuration for native products
        val outputSetting = delegate.module.fragments.firstNotNullOfOrNull { it.settings.native?.output } ?: "bin/"
        val moduleRoot = delegate.module.source.moduleDir

        val destination = if (outputSetting.endsWith("/")) {
            moduleRoot.resolve(outputSetting).resolve(artifactPath.fileName)
        } else {
            val baseName = outputSetting.substringAfterLast("/")
            val dir = outputSetting.substringBeforeLast("/", "")
            val ext = artifactPath.fileName.toString().substringAfter(".")
            val resolvedDir = if (dir.isEmpty()) moduleRoot else moduleRoot.resolve(dir)
            resolvedDir.resolve("$baseName.$ext")
        }

        destination.parent.createDirectories()
        artifactPath.copyTo(destination, overwrite = true)

        logger.info("Copied native distributable to $destination")

        // Return the new destination so subsequent tasks use it
        return NativeLinkTask.Result(linkedBinary = destination)
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}
